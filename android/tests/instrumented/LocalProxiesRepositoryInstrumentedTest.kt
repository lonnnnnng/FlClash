package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：节点来自授权 1.yaml 原字节，文件与 Room 均在独占目录；不修改正式配置或启用 VPN。 */
class LocalProxiesRepositoryInstrumentedTest {
    @Test fun importRenameRollbackProjectionAndBackupPreserveGlobalReferences() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val bytes = readAuthorizedProxyProfile()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "local-proxies-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = root }
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(isolated, db, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("禁止下载")
        })
        try {
            val source = bytes.toString(Charsets.UTF_8)
            val first = repository.addCustomProfile("全局节点引用一")
            val second = repository.addCustomProfile("全局节点引用二")
            for (profile in listOf(first, second)) {
                val expected = db.profiles().getById(profile.id)!!.toDomain()
                repository.replaceCustomProfile(profile.id, source, expected, repository.readLocalProfileContent(profile.id)) { true }
            }
            val rows = repository.localProxies()
            assertEquals(ProxyGroupDrafts.proxyNames(source).size, rows.size)
            assertTrue(rows.isNotEmpty())
            val original = repository.readLocalProfileContent(first.id)
            assertFalse(ProxyGroupDrafts.root(original).has("proxies"))
            val names = LocalProxies.referenced(original)
            val chosen = rows.first { it.name in names }
            val oldFile = repository.profileFile(first.id)
            val oldMtime = oldFile.lastModified()
            val renamed = LocalProxies.withValue(chosen, "name", com.google.gson.JsonPrimitive("全局验收改名节点"))
            val next = rows.map { if (it.id == chosen.id) renamed else it }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_local_proxy_rename BEFORE UPDATE ON profiles BEGIN SELECT RAISE(ABORT, 'controlled failure'); END")
            assertTrue(runCatching { repository.saveLocalProxies(rows, next, chosen.name to renamed.name) }.isFailure)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_local_proxy_rename")
            assertEquals(rows, repository.localProxies()); assertEquals(original, oldFile.readText()); assertEquals(oldMtime, oldFile.lastModified())
            repository.saveLocalProxies(rows, next, chosen.name to renamed.name)
            assertEquals(2, repository.localProxyUsers(setOf(renamed.name)).size)
            for (profile in listOf(first, second)) {
                val saved = repository.readLocalProfileContent(profile.id)
                assertTrue(renamed.name in LocalProxies.referenced(saved)); assertFalse(chosen.name in LocalProxies.referenced(saved))
                val active = repository.profileById(profile.id)!!
                assertEquals(LocalProxies.referenced(saved).intersect(next.map { it.name }.toSet()), ProxyGroupDrafts.proxyNames(File(active.filePath).readText()).toSet())
                assertFalse(saved == File(active.filePath).readText())
            }
            assertTrue(runCatching { repository.saveLocalProxies(rows, rows) }.isFailure)
            val archive = BackupRepository(db, root).createArchive(BackupRestoreScope.PROFILES_ONLY)
            repository.saveLocalProxies(next, emptyList())
            BackupRepository(db, root).restore(archive, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.COMPATIBLE)
            assertTrue(repository.localProxies().any { it.id == chosen.id && it.name == renamed.name })
            assertNotNull(repository.profileById(first.id))
            val restored = repository.localProxies()
            val retained = repository.addCustomProfile("备份外保留配置")
            repository.replaceCustomProfile(retained.id, repository.readLocalProfileContent(first.id), retained,
                repository.readLocalProfileContent(retained.id)) { true }
            val currentName = "备份后改名节点"
            val changed = restored.map { if (it.id == chosen.id) LocalProxies.withValue(it, "name", com.google.gson.JsonPrimitive(currentName)) else it }
            repository.saveLocalProxies(restored, changed, renamed.name to currentName)
            val conflicting = renamed.copy(id = java.util.UUID.randomUUID().toString())
            repository.saveLocalProxies(changed, changed + conflicting)
            BackupRepository(db, root).restore(archive, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.COMPATIBLE)
            val merged = repository.localProxies()
            assertTrue(merged.any { it.id == chosen.id && it.name == currentName })
            assertTrue(merged.any { it.id == conflicting.id && it.name == renamed.name })
            val restoredName = "${renamed.name} (2)"
            assertTrue(merged.any { it.id !in setOf(chosen.id, conflicting.id) && it.name == restoredName })
            assertTrue(currentName in LocalProxies.referenced(repository.readLocalProfileContent(retained.id)))
            assertTrue(restoredName in LocalProxies.referenced(repository.readLocalProfileContent(first.id)))
            assertNotNull(repository.profileById(retained.id))
            repository.saveLocalProxies(merged, merged.filterNot { it.name == restoredName })
            assertTrue(restoredName in LocalProxies.referenced(repository.readLocalProfileContent(first.id)))
            assertFalse(restoredName in ProxyGroupDrafts.proxyNames(File(repository.profileById(first.id)!!.filePath).readText()))
        } catch (error: Exception) {
            // 作者 long：错误可能带节点密钥，测试输出只保留失败位置供定位。
            throw IllegalStateException("全局节点验收失败：${error.javaClass.simpleName}\n" + error.stackTrace.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
        } finally {
            db.close(); check(root.parentFile == context.cacheDir && root.name.startsWith("local-proxies-")); root.deleteRecursively()
        }
    }
    @Test fun nativeProxyValidationAndShareLinksUseAuthorizedDefinitions() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val root = ProxyGroupDrafts.root(readAuthorizedProxyProfile().toString(Charsets.UTF_8))
        val definitions = root.getAsJsonArray("proxies").map { it.asJsonObject }
        val bridge = CoreBridge()
        assertTrue(bridge.validateProxies(definitions).all { it.isEmpty() })
        val links = bridge.encodeShareLinks(definitions)
        assertEquals(definitions.size, links.size)
        val converted = links.filter { it.isNotBlank() }.take(5)
        assertTrue(converted.isNotEmpty())
        val decoded = bridge.decodeShareLinks(converted)
        assertTrue(decoded.all { it.size == 1 })
        assertTrue(bridge.validateProxies(decoded.flatten()).all { it.isEmpty() })
        assertTrue(bridge.decodeShareLinks(listOf("not-a-link")).single().isEmpty())
        val invalid = definitions.first().deepCopy().apply { addProperty("type", "__invalid_type__") }
        assertTrue(bridge.validateProxies(listOf(invalid)).single().isNotEmpty())
    }
}
