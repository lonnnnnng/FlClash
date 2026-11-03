package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CustomRulesRepositoryInstrumentedTest {
    @Test fun rulesWriteRejectsStaleOrRunningChangesAndPreservesLatestMetadata() = runBlocking {
        val args = InstrumentationRegistry.getArguments(); assumeTrue(args.getString("kclashLocalFile") == "true")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
        val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
        assertEquals(args.getString("kclashLocalFileSha256"), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val source = bytes.toString(Charsets.UTF_8)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "custom-rules-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir(): File = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(isolated, database, object : SubscriptionClient { override suspend fun download(url: String): SubscriptionDownload = error("本地规则验收不访问订阅") })
        try {
            val created = repository.addCustomProfile("规则隔离验收")
            repository.updateLocalProfileContent(created.id, source)
            val fields = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false))
            repository.updateProfileOverrides(created.id, fields.toJsonString())
            val file = repository.profileFile(created.id); val mtime = file.lastModified()
            assertTrue(runCatching { repository.saveCustomRules(created.id, "MATCH,DIRECT", source) { false } }.isFailure)
            assertTrue(runCatching { repository.saveCustomRules(created.id, "MATCH,DIRECT", source + "\n") { true } }.isFailure)
            assertTrue(file.readBytes().contentEquals(bytes)); assertEquals(mtime, file.lastModified())
            repository.updateProfileLabel(created.id, "保留最新名称")
            val saved = repository.saveCustomRules(created.id, "MATCH,DIRECT", source) { true }
            assertEquals("保留最新名称", saved.label); assertEquals(fields, ProfileProviderOverrides.fromJsonString(saved.overridesJson))
            assertEquals(listOf("MATCH,DIRECT"), CustomRules.fromSource(file.readText()))
            assertTrue(ProxyGroupDrafts.root(source).apply { remove("rules") } == ProxyGroupDrafts.root(file.readText()).apply { remove("rules") })
            assertEquals(created.id, repository.currentProfileId.value); assertEquals(1, database.profiles().getAll().size)
        } finally {
            database.close()
            // 作者 long：只回收本测试独占的缓存目录，正式数据库、正文和授权文件均不参与写入。
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("custom-rules-")); directory.deleteRecursively()
        }
    }
}
