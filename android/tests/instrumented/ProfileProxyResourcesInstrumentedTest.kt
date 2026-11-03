package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：只读取授权原配置，在独占 Room/cache 中验证资源绑定、投影、备份与导入事务。 */
class ProfileProxyResourcesInstrumentedTest {
    @Test fun bindingProjectionImportRollbackAndBackupKeepSourceProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val bytes = readAuthorizedProxyProfile()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "proxy-resources-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        var downloads = 0
        val client = object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload { downloads++; return SubscriptionDownload(bytes, null, SubscriptionInfo()) }
        }
        val repository = ProfileRepository(isolated, database, client)
        try {
            val resource = repository.addFileProfile("已有资源", bytes)
            val custom = repository.addCustomProfile("引用配置")
            repository.updateLocalProfileContent(custom.id, bytes.toString(Charsets.UTF_8))
            val source = repository.readLocalProfileContent(custom.id)
            val groups = ProxyGroupDrafts.read(source)
            val save = ProxyGroupsSave(groups.mapIndexed { index, group -> if (index == 0) ProxyGroupDrafts.withMembers(group,
                ProxyGroupDrafts.members(group) + ProxyGroupMember("资源别名", true)) else group }, mapOf("资源别名" to resource.id))
            repository.saveCustomProxyGroups(custom.id, save, source) { true }
            val bound = repository.readLocalProfileContent(custom.id)
            assertTrue(bound.contains(PROFILE_RESOURCE_PREFIX + resource.id))
            assertEquals(listOf(custom.realLabel), repository.proxyResourceUsers(resource.id))
            val first = repository.profileById(custom.id)!!
            val projection = ProxyGroupDrafts.root(File(first.filePath).readText())
            val path = projection.getAsJsonObject("proxy-providers").getAsJsonObject("资源别名")["path"].asString
            assertTrue(File(path).readBytes().contentEquals(bytes))
            assertEquals(first.filePath, repository.profileById(custom.id)!!.filePath)
            repository.updateProfileLabel(resource.id, "改名资源")
            assertTrue(repository.readLocalProfileContent(custom.id) == bound)
            repository.updateFileProfile(resource.id, "", bytes + "\n# resource revision\n".toByteArray())
            assertNotEquals(first.filePath, repository.profileById(custom.id)!!.filePath)
            assertTrue(File(path).readBytes().contentEquals(bytes))
            val archive = BackupRepository(database, directory).createArchive(BackupRestoreScope.PROFILES_ONLY)
            repository.deleteProfile(resource.id)
            assertTrue(runCatching { repository.profileById(custom.id) }.isFailure)
            BackupRepository(database, directory).restore(archive, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.COMPATIBLE)
            assertNotNull(repository.profileById(custom.id))

            val remote = repository.addRemoteProfile("https://example.org/proxy-resource", "同源资源", "")
            val imported = ProxyGroupDrafts.root(source)
            val payload = imported.getAsJsonArray("proxies").deepCopy()
            imported.add("proxy-providers", JsonObject().apply {
                add("旧订阅别名", JsonObject().apply { addProperty("type", "http"); addProperty("url", remote.url) })
                add("改名资源", JsonObject().apply { addProperty("type", "inline"); add("payload", payload) })
            })
            imported.getAsJsonArray("proxy-groups")[0].asJsonObject.apply {
                add("use", ProxyGroupDrafts.array(listOf("旧订阅别名", "改名资源")))
                add("use-position", JsonObject().apply { addProperty("旧订阅别名", 0) })
            }
            val incoming = YamlConfigCodec.toYaml(imported.toString())
            val expected = database.profiles().getById(custom.id)!!.toDomain()
            val beforeRows = database.profiles().getAll()
            val beforeFiles = File(directory, "profiles").listFiles()!!.map { it.name }.toSet()
            var checks = 0
            assertTrue(runCatching { repository.replaceCustomProfile(custom.id, incoming, expected, bound) { ++checks < 4 } }.isFailure)
            assertEquals(beforeRows, database.profiles().getAll())
            assertEquals(beforeFiles, File(directory, "profiles").listFiles()!!.map { it.name }.toSet())
            assertTrue(bound == repository.readLocalProfileContent(custom.id))
            repository.replaceCustomProfile(custom.id, incoming, expected, bound) { true }
            assertEquals(1, downloads)
            assertEquals(beforeRows.size + 1, database.profiles().getAll().size)
            val mapped = ProxyGroupDrafts.root(repository.readLocalProfileContent(custom.id))
            val providers = mapped.getAsJsonObject("proxy-providers")
            assertEquals(remote.id, ProfileProxyResources.idOf(providers["同源资源"]))
            assertTrue(providers.has("改名资源 (2)"))
            val group = mapped.getAsJsonArray("proxy-groups")[0].asJsonObject
            assertEquals(listOf("同源资源", "改名资源 (2)"), group.getAsJsonArray("use").map { it.asString })
            assertEquals(0, group.getAsJsonObject("use-position")["同源资源"].asInt)
            assertFalse(mapped.has("proxies"))
            assertEquals(imported.getAsJsonArray("proxies").map { it.asJsonObject["name"].asString }.toSet(), repository.localProxies().map { it.name }.toSet())
            assertNotNull(repository.profileById(custom.id))
        } finally {
            database.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("proxy-resources-"))
            directory.deleteRecursively()
        }
    }
}

internal fun readAuthorizedProxyProfile(): ByteArray {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
    val bytes = descriptor.use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    assertEquals(34814, bytes.size)
    assertEquals("bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    return bytes
}
