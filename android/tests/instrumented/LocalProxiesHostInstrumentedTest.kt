package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.google.gson.JsonPrimitive
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实核心加载授权原文件的临时 CUSTOM，结束恢复选择和全局目录，仅回收核对过的本次配置。 */
class LocalProxiesHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun stoppedMihomoProjectsGlobalNodesAndRefreshesRenamedReferences() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val bytes = readAuthorizedProxyProfile()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        val runtime = entry.runtimeManager()
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        val selected = checkNotNull(repository.currentProfileId.value)
        val before = entry.database().profiles().getAll().map { it.id }.toSet()
        val originalNodes = repository.localProxies()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val custom = repository.addCustomProfile("本地代理核心验收配置")
        var stage = "登记节点"
        try {
            // 作者 long：本方法只验全局节点投影；沿用原文件的资源定义，避免完整导入的远程下载混入停止态加载证据。
            val plan = planLocalProxyImport(bytes.toString(Charsets.UTF_8), originalNodes, emptySet())
            repository.saveLocalProxies(originalNodes, plan.after)
            repository.updateLocalProfileContent(custom.id, plan.source)
            val source = repository.readLocalProfileContent(custom.id)
            assertFalse(ProxyGroupDrafts.root(source).has("proxies"))
            val nodes = repository.localProxies()
            val referenced = LocalProxies.referenced(source)
            val expected = nodes.filter { it.name in referenced }.map { it.name }.toSet()
            assertTrue(expected.isNotEmpty())
            stage = "加载配置"
            runtime.selectProfile(custom.id, repository::selectProfile)
            suspend fun members(): Set<String> {
                var all = emptySet<String>()
                repeat(40) {
                    all = runtime.getProxyGroups().getOrThrow().groups.flatMap { it.all }.toSet()
                    if (all.containsAll(expected)) return all
                    delay(150)
                }
                return all
            }
            assertTrue("真实核心应展开全局节点引用", members().containsAll(expected))
            stage = "改名刷新"
            val chosen = nodes.first { it.name in expected && originalNodes.none { old -> old.id == it.id } }
            val changedName = "本地代理核心验收改名节点"
            val next = nodes.map { if (it.id == chosen.id) LocalProxies.withValue(it, "name", JsonPrimitive(changedName)) else it }
            repository.saveLocalProxies(nodes, next, chosen.name to changedName)
            var refreshed = emptySet<String>()
            repeat(40) {
                refreshed = runtime.getProxyGroups().getOrThrow().groups.flatMap { it.all }.toSet()
                if (changedName !in refreshed) delay(150)
            }
            assertTrue(changedName in refreshed)
            assertFalse(chosen.name in refreshed)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        } catch (error: Exception) {
            throw IllegalStateException("本地代理核心验收失败：$stage/${error.javaClass.simpleName}\n" + error.stackTrace.filter { it.className.startsWith("com.lonnnnnng") }.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
        } finally {
            runtime.selectProfile(selected, repository::selectProfile)
            val row = entry.database().profiles().getById(custom.id)
            if (row != null) {
                check(custom.id !in before && row.label == "本地代理核心验收配置")
                repository.deleteProfile(custom.id)
            }
            repository.saveLocalProxies(repository.localProxies(), originalNodes)
            assertEquals(before, entry.database().profiles().getAll().map { it.id }.toSet())
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        }
    }
}
