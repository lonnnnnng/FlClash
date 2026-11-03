package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.core.RuleSetInfo
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：资源写入回归使用独占内存 Room/cache；配置取授权原字节，不访问真实订阅或用户数据库。 */
class RuleResourcesRepositoryInstrumentedTest {
    @Test fun resourceImportRollbackRestoreAndStaleSyncPreserveStableReferences() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "rule-resource-test-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var delayDownload = false
        val client = object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload {
                if (delayDownload) { started.complete(Unit); release.await() }
                return SubscriptionDownload("+.example.com\n".toByteArray(), null, SubscriptionInfo())
            }
        }
        val compiler = object : RuleResourceCompiler {
            override suspend fun compile(name: String): RuleSetInfo {
                val text = File(directory, "mihomo/providers/rules/$name").readText()
                require(!text.contains("invalid-marker"))
                File(directory, "mihomo/providers/rules/$name.mrs").writeText("fake-compiled")
                return RuleSetInfo("domain", "yaml")
            }
            override suspend fun preview(path: String): String = error("此回归不使用 MRS")
        }
        val resources = RuleResourceRepository(isolated, database, client, compiler)
        val profiles = ProfileRepository(isolated, database, client, resources = resources)
        try {
            val resource = resources.save("全局规则", "https://example.org/rules", null)
            val local = resources.save("本地规则", "", "payload: [example.net]\n".toByteArray())
            assertEquals(2, resources.all().size)
            val failed = runCatching { resources.save(local.label, "", "invalid-marker".toByteArray(), local) }
            assertTrue(failed.isFailure); assertEquals(local, resources.all().first { it.id == local.id })
            delayDownload = true
            val syncing = async { runCatching { resources.sync(resource) } }
            started.await()
            val renamed = resources.updateOptions(resource, "重命名规则", resource.url)
            release.complete(Unit)
            assertTrue(syncing.await().isFailure)
            assertEquals(renamed, resources.all().first { it.id == resource.id })
            delayDownload = false
            val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
            val bytes = java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
            assertEquals("bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            assertEquals(34814, bytes.size)
            val profile = profiles.addCustomProfile("资源隔离验收")
            profiles.updateLocalProfileContent(profile.id, bytes.toString(Charsets.UTF_8))
            val source = profiles.readLocalProfileContent(profile.id)
            val sections = ProfileProvidersEditor.extract(source)
            assertTrue(runCatching { profiles.saveCustomProviders(profile.id, sections, emptyMap(), source + "\n") { true } }.isFailure)
            assertTrue(runCatching { profiles.saveCustomProviders(profile.id, sections, emptyMap(), source) { false } }.isFailure)
            assertEquals(source, profiles.readLocalProfileContent(profile.id))
            val before = database.profiles().getById(profile.id)!!.toDomain()
            // 作者 long：只给原配置加资源绑定；不替换授权配置为自行生成的 YAML。
            val document = RuleResources.document(source)
            document.add("rule-providers", com.google.gson.JsonObject().apply { add("全局引用", RuleResources.reference(renamed)) })
            val bound = com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(document.toString())
            profiles.replaceCustomProfile(profile.id, bound, before, source) { true }
            val projection = resources.resolve(profiles.readLocalProfileContent(profile.id))
            assertTrue(projection.contains(renamed.fileName)); assertFalse(projection.contains(RESOURCE_PATH_PREFIX))
            val active = profiles.profileById(profile.id)!!
            assertNotEquals(profiles.profileFile(profile.id).path, active.filePath)
            assertEquals(projection, File(active.filePath).readText())
            assertEquals(active.filePath, profiles.profileById(profile.id)!!.filePath)
            assertTrue(resources.profilesUsing(renamed).contains(profile.realLabel))
            val backup = BackupRepository(database, directory).createArchive(BackupRestoreScope.PROFILES_ONLY)
            resources.delete(renamed)
            check(resources.file(renamed).parentFile!!.canonicalPath.startsWith(directory.canonicalPath))
            resources.file(renamed).delete()
            File(resources.file(renamed).parentFile, renamed.coreFileName).delete()
            assertTrue(runCatching { resources.resolve(bound) }.isFailure)
            BackupRepository(database, directory).restore(backup, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.COMPATIBLE)
            assertTrue(resources.resolve(bound).contains(renamed.fileName))
            val afterBind = profiles.readLocalProfileContent(profile.id)
            val current = database.profiles().getById(profile.id)!!.toDomain()
            val imported = RuleResources.document(afterBind).apply {
                add("rule-providers", com.google.gson.JsonObject().apply {
                    add("本地规则", com.google.gson.JsonObject().apply { addProperty("type", "inline"); add("payload", com.google.gson.JsonArray().apply { add("+.example.com") }) })
                })
            }
            val importContent = com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(imported.toString())
            val count = resources.all().size
            var checks = 0
            assertTrue(runCatching { profiles.replaceCustomProfile(profile.id, importContent, current, afterBind) { ++checks < 4 } }.isFailure)
            assertEquals(count, resources.all().size); assertEquals(afterBind, profiles.readLocalProfileContent(profile.id))
            profiles.replaceCustomProfile(profile.id, importContent, current, afterBind) { true }
            assertEquals(count + 1, resources.all().size)
            val result = RuleResources.document(profiles.readLocalProfileContent(profile.id)).getAsJsonObject("rule-providers")
            assertTrue(result.has("本地规则 (2)")); assertEquals(local, resources.all().first { it.id == local.id })
        } finally {
            database.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("rule-resource-test-"))
            directory.deleteRecursively()
        }
    }
}
