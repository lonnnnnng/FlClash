package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实菜单只加载授权原1.yaml副本，不改源正文；按新增ID回收本次本地节点并恢复原配置选择。 */
class ProxyNodeMenuHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualNodeMenuAddsOncePersistsAfterRecreationAndShowsDuplicateFeedback() = exercise(false)
    @Test fun runningNodeMenuAddsOnceAndKeepsCoreSessionAcrossRecreation() = exercise(true)

    private fun exercise(running: Boolean) = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository(); val runtime = entry.runtimeManager()
        val beforeProfiles = entry.database().profiles().getAll()
        val originalIds = beforeProfiles.map { it.id }.toSet()
        if (originalIds.isNotEmpty()) {
            compose.waitUntil(30_000) { repository.currentProfileId.value?.let(originalIds::contains) == true }
        }
        val selected = repository.currentProfileId.value
        check(selected == null || selected in originalIds)
        val originalFiles = beforeProfiles.associate { it.id to signature(repository.profileFile(it.id)) }
        val originalVpn = entry.vpnSettingsStore().read()
        val originalNodes = repository.localProxies()
        val previousOptions = ProxyPageOptionsStore.load(compose.activity)
        val token = "节点菜单验收-${System.nanoTime()}"
        var temporary: Profile? = null
        var addedId: String? = null
        var importedFields: com.google.gson.JsonObject? = null
        var stage = "加载原配置副本"
        try {
            ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
            // 作者 long：真实配置恢复可能超过一次短启动窗口；先等核心停止，避免把STARTING当作菜单结果。
            if (runtime.state.value != CoreRuntimeStatus.STOPPED) runtime.stop().getOrThrow()
            compose.waitUntil(30_000) { runtime.state.value == CoreRuntimeStatus.STOPPED }
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
            temporary = repository.addFileProfile(token, readAuthorizedProxyProfile())
            val profile = checkNotNull(temporary)
            runtime.selectProfile(profile.id, repository::selectProfile)
            if (running) {
                runtime.setVpnEnabled(false).getOrThrow()
                runtime.startProxy().getOrThrow()
            }
            val since = runtime.runningSinceElapsedMillis.value
            val snapshot = runtime.getProxyGroups(profile.id).getOrThrow()
            val groupNames = repository.profiles.value.filter { it.type == ProfileType.CUSTOM }.flatMap {
                ProxyGroupDrafts.read(repository.readLocalProfileContent(it.id)).map { group -> group.name }
            }.toSet()
            val candidates = visibleProxyGroups(snapshot.groups, runtime.outboundMode.value).flatMap { group ->
                group.all.filter { it !in LocalProxies.reserved && snapshot.groups.none { nested -> nested.name == it } }.map { group to it }
            }
            var chosen: Pair<com.lonnnnnng.flclash.core.ProxyGroupInfo, String>? = null
            var expected: LocalProxy? = null
            for (candidate in candidates) {
                val definition = runtime.getProxyDefinition(profile.id, candidate.second)
                val plan = planLocalProxyAddition(definition, originalNodes, groupNames)
                if (plan.added) { chosen = candidate; expected = plan.row; break }
            }
            val (group, name) = checkNotNull(chosen) { "原文件没有尚未登记的节点，不能把重复提示当作新增验收" }
            val expectedFields = checkNotNull(expected).fields
            importedFields = expectedFields
            stage = "正式菜单新增"
            compose.onNodeWithTag("nav_proxy").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("proxy_group_tab_${group.name}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("proxy_group_tab_${group.name}").performScrollTo().performClick()
            val card = "proxy_node_${group.name}_$name"
            compose.onNodeWithTag("proxy_group_members_${group.name}").performScrollToNode(hasTestTag(card))
            compose.onNodeWithTag(card).performTouchInput { longClick() }
            compose.onNodeWithTag("${card}_menu_add_local").performClick()
            compose.waitUntil(10_000) { runBlocking { repository.localProxies().size == originalNodes.size + 1 } }
            val added = repository.localProxies().single { row -> originalNodes.none { it.id == row.id } }
            addedId = added.id
            assertTrue("保存定义必须与原核心定义一致", added.fields == expectedFields)
            assertFalse(added.fields.has("dialer-proxy"))
            compose.onNodeWithText("${added.name} 已添加到本地代理").assertIsDisplayed()
            stage = "重复反馈"
            compose.onNodeWithTag(card).performTouchInput { longClick() }
            compose.onNodeWithTag("${card}_menu_add_local").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("${added.name} 已在本地代理中").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(originalNodes.size + 1, repository.localProxies().size)
            stage = "重建与本地管理页回读"
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("tools_item_resources_settings").performScrollTo().performClick()
            compose.onNodeWithText("本地代理").performClick()
            compose.onNodeWithTag("local_proxy_$addedId").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("local_proxies_back").performClick()
            compose.onNodeWithTag("profile_editor_back").performClick()
            assertTrue("原Profile行不得被节点导入修改", entry.database().profiles().getAll().filter { it.id != temporary.id } == beforeProfiles)
            assertEquals(if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED, runtime.state.value)
            assertEquals(since, runtime.runningSinceElapsedMillis.value)
            if (running) assertTrue(runtime.profileInputsApplied(profile.id))
        } catch (failure: Exception) {
            // 作者 long：核心异常可能含真实订阅凭据，只记录阶段、异常类型和本项目代码位置。
            throw IllegalStateException("节点菜单验收失败：$stage/${failure.javaClass.simpleName}\n" + failure.stackTrace.filter { it.className.startsWith("com.lonnnnnng") }.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
        } finally {
            // 作者 long：宿主断言失败时也必须继续回收自己创建的夹具，导航异常不能阻断正式配置和偏好的恢复。
            runCatching { compose.onNodeWithTag("nav_dashboard").performClick(); compose.waitForIdle() }
            runtime.stop().getOrThrow()
            if (selected == null) repository.selectProfile(null) else runtime.selectProfile(selected, repository::selectProfile)
            val current = repository.localProxies()
            val owned = current.filter { (it.id == addedId || importedFields != null && it.fields == importedFields) && originalNodes.none { old -> old.id == it.id } }
            repository.saveLocalProxies(current, current - owned.toSet())
            val row = temporary?.let { entry.database().profiles().getById(it.id) }
            if (row != null) { check(row.label == token && beforeProfiles.none { it.id == row.id }); repository.deleteProfile(row.id) }
            if (selected == null) repository.selectProfile(null)
            ProxyPageOptionsStore.save(compose.activity, previousOptions)
            runtime.setVpnEnabled(originalVpn).getOrThrow()
            assertTrue("原目录必须保持", repository.localProxies() == originalNodes)
            assertTrue("原Profile必须保持", entry.database().profiles().getAll() == beforeProfiles)
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(originalFiles, beforeProfiles.associate { it.id to signature(repository.profileFile(it.id)) })
        }
    }

    private fun signature(file: java.io.File) = file.lastModified() to
        java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
}
