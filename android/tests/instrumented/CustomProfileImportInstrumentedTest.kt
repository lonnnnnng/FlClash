package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomProfileImportInstrumentedTest {
    @Test fun customReplacementCommitsAtomicallyAndRejectsStaleOrRunningWrite() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("只使用授权 1.yaml", args.getString("kclashLocalFile") == "true")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
        val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("kclashLocalFileSha256"), hash)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "custom-import-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("本地验收不访问订阅")
        })
        try {
            val created = repository.addCustomProfile("保留名称")
            val original = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = true), ntp = ProfileNtpOverrides(enable = true))
            val expected = repository.updateProfileOverrides(created.id, original.toJsonString())
            val file = repository.profileFile(created.id)
            val originalBytes = file.readBytes()
            val before = file.lastModified()
            val content = repository.decodeCustomImport(bytes, "").content
            assertTrue(content.isNotBlank())
            assertTrue(file.readBytes().contentEquals(originalBytes))
            // 作者 long：确认前只解析；运行中和过期元数据必须在文件/Room 提交前一起拒绝。
            assertTrue(runCatching { repository.replaceCustomProfile(created.id, content, expected, "") { false } }.isFailure)
            assertTrue(file.readBytes().contentEquals(originalBytes))
            assertEquals(before, file.lastModified())
            val renamed = repository.updateProfileLabel(created.id, "新的名称")
            assertTrue(runCatching { repository.replaceCustomProfile(created.id, content, expected, "") { true } }.isFailure)
            assertEquals(renamed, database.profiles().getById(created.id)?.toDomain())
            val updated = repository.replaceCustomProfile(created.id, content, renamed, "") { true }
            assertEquals("新的名称", updated.label)
            assertEquals(ProfileProviderOverrides(), ProfileProviderOverrides.fromJsonString(updated.overridesJson))
            assertEquals(content, file.readText())
            assertEquals(updated, database.profiles().getById(created.id)?.toDomain())
            assertEquals(1, database.profiles().getAll().size)
            assertEquals(created.id, repository.currentProfileId.value)
        } finally {
            database.close()
            // 作者 long：只回收本测试在 cacheDir 创建的独占目录，正式 Profile、1.yaml 和系统文件不修改。
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("custom-import-"))
            directory.deleteRecursively()
        }
    }
}
