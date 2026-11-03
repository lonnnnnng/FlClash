package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

/** 作者 long：真实核心只加载授权配置的副本，测试结束恢复当前选择，仅删除本次创建的临时配置。 */
class ProfileProxyResourceHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun stoppedMihomoLoadsFileProviderAndRefreshesItsVersionWithoutVpn() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val bytes = readAuthorizedProxyProfile()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository(); val runtime = entry.runtimeManager()
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        val selected = checkNotNull(repository.currentProfileId.value)
        val before = entry.database().profiles().getAll().map { it.id }.toSet()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val resource = repository.addFileProfile("代理资源核心验收文件", bytes)
        val custom = repository.addCustomProfile("代理资源核心验收配置")
        try {
            val source = bytes.toString(Charsets.UTF_8)
            repository.updateLocalProfileContent(custom.id, source)
            val groups = ProxyGroupDrafts.read(source)
            val extra = ProxyGroupDraft(json = "{\"name\":\"代理资源核心验收组\",\"type\":\"select\",\"use\":[\"验收资源\"]}")
            repository.saveCustomProxyGroups(custom.id, ProxyGroupsSave(groups + extra, mapOf("验收资源" to resource.id)), source) { true }
            val expected = ProxyGroupDrafts.proxyNames(source).toSet()
            assertTrue(expected.size > 1)
            try {
                runtime.selectProfile(custom.id, repository::selectProfile)
                var loaded = emptyList<String>()
                repeat(40) {
                    loaded = runtime.getProxyGroups().getOrThrow().groups.firstOrNull { it.name == extra.name }?.all.orEmpty()
                    if (loaded.toSet() != expected) delay(250)
                }
                assertTrue("真实文件资源必须展开全部授权节点", loaded.toSet() == expected)
                val firstPath = repository.profileById(custom.id)!!.filePath
                repository.updateProfileLabel(resource.id, "代理资源核心验收改名")
                assertEquals(firstPath, repository.profileById(custom.id)!!.filePath)
                val updated = ProxyGroupDrafts.root(source)
                val nodes = updated.getAsJsonArray("proxies")
                nodes.remove(nodes.size() - 1)
                repository.updateFileProfile(resource.id, "", com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(updated.toString()).toByteArray())
                var refreshed = emptyList<String>()
                repeat(40) {
                    refreshed = runtime.getProxyGroups().getOrThrow().groups.firstOrNull { it.name == extra.name }?.all.orEmpty()
                    if (refreshed.size != expected.size - 1) delay(250)
                }
                assertEquals(expected.size - 1, refreshed.size)
                assertNotEquals(firstPath, repository.profileById(custom.id)!!.filePath)
                assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
            } catch (failure: Exception) {
                // 作者 long：核心错误可能含节点凭据，验收只保留类型与代码位置，禁止输出配置正文。
                val types = generateSequence<Throwable>(failure) { it.cause }.take(6).joinToString("/") { it.javaClass.simpleName }
                throw IllegalStateException("代理资源核心验收失败：$types\n" + failure.stackTrace.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
            }
        } finally {
            runtime.selectProfile(selected, repository::selectProfile)
            for (profile in listOf(custom, resource)) {
                val row = entry.database().profiles().getById(profile.id)
                if (row != null) { check(profile.id !in before && row.label.startsWith("代理资源核心验收")); repository.deleteProfile(profile.id) }
            }
            assertEquals(before, entry.database().profiles().getAll().map { it.id }.toSet())
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        }
    }
}
