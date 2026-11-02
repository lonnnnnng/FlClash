package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileExtensionRepositoryInstrumentedTest {
    @Test fun extensionPersistsWithoutSourceMutationAndRoundTripsGlobalRulesInBackup() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashLocalFile") == "true")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
        val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
        assertEquals(args.getString("kclashLocalFileSha256"), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "extension-test-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("不访问订阅")
        })
        try {
            val profile = repository.addFileProfile("授权本地配置", bytes)
            val script = ScriptRepository(isolated, database).create("脚本", "function main(config) { return config; }")
            val source = repository.profileFile(profile.id)
            val before = source.readBytes(); val modified = source.lastModified()
            val originalOverrides = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = true), ntp = ProfileNtpOverrides(enable = true))
            repository.updateProfileOverrides(profile.id, originalOverrides.toJsonString())
            val localRules = listOf(ExtensionRule("own", "DOMAIN,own.test,MATCH"))
            val globalRules = listOf(ExtensionRule("global", "DOMAIN,global.test,DIRECT"), ExtensionRule("off", "DOMAIN,disabled.test,REJECT"))
            repository.saveGlobalExtensionRules(globalRules)
            repository.saveExtension(profile.id, "standard", script.id, "DIRECT", ProfileExtensionOverrides(true, localRules, setOf("off")))
            val active = checkNotNull(repository.profileById(profile.id))
            assertNull(active.scriptPath)
            assertEquals(listOf("own", "global"), active.standardExtension?.rules?.map { it.id })
            assertEquals("DIRECT", active.standardExtension?.matchTarget)
            repository.saveExtension(profile.id, "script", script.id, "DIRECT", ProfileExtensionOverrides(true, localRules, setOf("off")))
            val scripted = checkNotNull(repository.profileById(profile.id))
            assertNotNull(scripted.scriptPath); assertNull(scripted.standardExtension)
            val saved = checkNotNull(database.profiles().getById(profile.id)).toDomain()
            assertEquals(originalOverrides.dns, ProfileProviderOverrides.fromJsonString(saved.overridesJson).dns)
            assertEquals(originalOverrides.ntp, ProfileProviderOverrides.fromJsonString(saved.overridesJson).ntp)
            assertTrue(source.readBytes().contentEquals(before)); assertEquals(modified, source.lastModified())
            // 作者 long：绑定缺失脚本必须原子拒绝，不允许模式已改、脚本未保存的半成品状态。
            assertTrue(runCatching { repository.saveExtension(profile.id, "standard", -1, null, ProfileExtensionOverrides()) }.isFailure)
            assertEquals(saved, database.profiles().getById(profile.id)?.toDomain())
            val backup = BackupRepository(database, directory, File(directory, "backup-staging"))
            val archive = backup.createArchive()
            repository.saveGlobalExtensionRules(emptyList())
            backup.restore(archive, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)
            assertEquals(globalRules, repository.globalExtensionRules())
            assertEquals(saved, database.profiles().getById(profile.id)?.toDomain())
            assertTrue(source.readBytes().contentEquals(before))
        } finally {
            database.close()
            // 作者 long：只回收本用例创建的独占目录，不接触正式库和授权 YAML。
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("extension-test-"))
            directory.deleteRecursively()
        }
    }
}
