package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：授权原1.yaml仅在独占目录内派生资源绑定，正式配置/当前选择不参与写入。 */
class ProviderPersistenceInstrumentedTest {
    @Test fun metadataOnlySavesKeepBytesMtimeAndOtherOverridesAndRollbackLateGuards() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val original = readAuthorizedProxyProfile()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "provider-persistence-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("离线验收不允许下载")
        })
        try {
            val resource = repository.addFileProfile("节点资源", original)
            val custom = repository.addCustomProfile("Provider 持久化")
            repository.updateLocalProfileContent(custom.id, original.toString(Charsets.UTF_8))
            val source = repository.readLocalProfileContent(custom.id)
            val sections = ProfileProvidersEditor.extract(source)
            val bound = sections.copy(proxyProviders = ProfileProxyResources.bind(sections.proxyProviders, resource, "节点别名"))
            repository.saveCustomProviders(custom.id, bound, emptyMap(), source) { true }
            val before = repository.readLocalProfileContent(custom.id)
            val currentSections = ProfileProvidersEditor.extract(before)
            val file = repository.profileFile(custom.id)
            val bytes = file.readBytes(); val modified = file.lastModified()
            val other = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false), dialers = emptyMap())
            repository.updateProfileOverrides(custom.id, other.toJsonString())
            val options = mapOf("节点别名" to ProxyProviderOverride(interval = 300, filter = "HK", memberLayout = listOf("filter")))
            val saved = repository.saveCustomProviders(custom.id, currentSections, options, before) { true }
            assertArrayEquals(bytes, file.readBytes()); assertEquals(modified, file.lastModified())
            assertEquals(other.copy(proxyProviders = options), ProfileProviderOverrides.fromJsonString(saved.overridesJson))
            // 作者 long：仅选项变化时正文完全相同，旧窗口也必须被事务内CAS拒绝；定义变化同样不能绕过覆盖版本。
            assertTrue(runCatching { repository.saveCustomProviders(custom.id, currentSections, emptyMap(), before, emptyMap()) { true } }.isFailure)
            assertArrayEquals(bytes, file.readBytes()); assertEquals(modified, file.lastModified())
            assertEquals(options, ProfileProviderOverrides.fromJsonString(database.profiles().getById(custom.id)!!.overridesJson).proxyProviders)

            val row = database.profiles().getById(custom.id)
            var checks = 0
            assertTrue(runCatching {
                repository.saveCustomProviders(custom.id, currentSections, emptyMap(), before) { ++checks < 3 }
            }.isFailure)
            assertEquals(3, checks); assertEquals(row, database.profiles().getById(custom.id))
            assertArrayEquals(bytes, file.readBytes()); assertEquals(modified, file.lastModified())
            assertTrue(runCatching { repository.saveCustomProviders(custom.id, currentSections, options, before + "\n# stale") { true } }.isFailure)

            val second = repository.addFileProfile("另一个节点资源", original)
            val rebound = currentSections.copy(proxyProviders = ProfileProxyResources.bind(currentSections.proxyProviders, second, "节点别名"))
            checks = 0
            assertTrue(runCatching { repository.saveCustomProviders(custom.id, rebound, emptyMap(), before) { ++checks < 3 } }.isFailure)
            assertEquals(row, database.profiles().getById(custom.id))
            assertArrayEquals(bytes, file.readBytes()); assertEquals(modified, file.lastModified())

            val resourceFile = repository.profileFile(resource.id)
            val resourceTime = resourceFile.lastModified()
            repository.updateProfileOverrides(resource.id, other.toJsonString())
            val resourceSaved = repository.saveProviderOptions(resource.id, options) { true }
            assertEquals(other.copy(proxyProviders = options), ProfileProviderOverrides.fromJsonString(resourceSaved.overridesJson))
            assertTrue(runCatching { repository.saveProviderOptions(resource.id, emptyMap(), emptyMap()) { true } }.isFailure)
            val resourceRow = database.profiles().getById(resource.id)
            checks = 0
            assertTrue(runCatching { repository.saveProviderOptions(resource.id, emptyMap()) { ++checks < 2 } }.isFailure)
            assertEquals(resourceRow, database.profiles().getById(resource.id))
            assertArrayEquals(original, resourceFile.readBytes()); assertEquals(resourceTime, resourceFile.lastModified())

            // 作者 long：分区提交必须基于事务内最新值；后置守卫失败时连同DNS/链式代理一起回滚。
            val dns = ProfileDnsOverrides(enable = true)
            val dnsSaved = repository.patchProfileOverrides(custom.id, { true }) { current -> current.copy(dns = dns) }
            assertEquals(options, ProfileProviderOverrides.fromJsonString(dnsSaved.overridesJson).proxyProviders)
            val dialerSaved = repository.patchProfileOverrides(custom.id, { true }) { current -> current.copy(dialers = mapOf("A" to "B")) }
            assertEquals(dns, ProfileProviderOverrides.fromJsonString(dialerSaved.overridesJson).dns)
            val patchedRow = database.profiles().getById(custom.id)
            checks = 0
            assertTrue(runCatching {
                repository.patchProfileOverrides(custom.id, { ++checks < 2 }) { current -> current.copy(dns = ProfileDnsOverrides()) }
            }.isFailure)
            assertEquals(patchedRow, database.profiles().getById(custom.id))
            assertArrayEquals(bytes, file.readBytes()); assertEquals(modified, file.lastModified())
        } finally {
            database.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("provider-persistence-"))
            directory.deleteRecursively()
        }
    }
}
