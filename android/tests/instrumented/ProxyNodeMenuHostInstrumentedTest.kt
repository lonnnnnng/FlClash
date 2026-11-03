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
    @Test fun actualNodeMenuAddsOncePersistsAfterRecreationAndShowsDuplicateFeedback() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository(); val runtime = entry.runtimeManager()
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        val selected = checkNotNull(repository.currentProfileId.value)
        val beforeProfiles = entry.database().profiles().getAll()
        val originalNodes = repository.localProxies()
        val previousOptions = ProxyPageOptionsStore.load(compose.activity)
        ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val token = "节点菜单验收-${System.nanoTime()}"
        val temporary = repository.addFileProfile(token, readAuthorizedProxyProfile())
        var addedId: String? = null
        var importedFields: com.google.gson.JsonObject? = null
        var stage = "加载原配置副本"
        try {
            runtime.selectProfile(temporary.id, repository::selectProfile)
            val snapshot = runtime.getProxyGroups().getOrThrow()
            val groupNames = repository.profiles.value.filter { it.type == ProfileType.CUSTOM }.flatMap {
                ProxyGroupDrafts.read(repository.readLocalProfileContent(it.id)).map { group -> group.name }
            }.toSet()
            val candidates = visibleProxyGroups(snapshot.groups, runtime.outboundMode.value).flatMap { group ->
                group.all.filter { it !in LocalProxies.reserved && snapshot.groups.none { nested -> nested.name == it } }.map { group to it }
            }
            var chosen: Pair<com.lonnnnnng.flclash.core.ProxyGroupInfo, String>? = null
            var expected: LocalProxy? = null
            for (candidate in candidates) {
                val definition = runtime.getProxyDefinition(temporary.id, candidate.second)
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
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        } catch (failure: Exception) {
            // 作者 long：核心异常可能含真实订阅凭据，只记录阶段、异常类型和本项目代码位置。
            throw IllegalStateException("节点菜单验收失败：$stage/${failure.javaClass.simpleName}\n" + failure.stackTrace.filter { it.className.startsWith("com.lonnnnnng") }.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
        } finally {
            runtime.selectProfile(selected, repository::selectProfile)
            val current = repository.localProxies()
            val owned = current.filter { (it.id == addedId || importedFields != null && it.fields == importedFields) && originalNodes.none { old -> old.id == it.id } }
            repository.saveLocalProxies(current, current - owned.toSet())
            val row = entry.database().profiles().getById(temporary.id)
            if (row != null) { check(row.label == token && beforeProfiles.none { it.id == row.id }); repository.deleteProfile(row.id) }
            ProxyPageOptionsStore.save(compose.activity, previousOptions)
            assertTrue("原目录必须保持", repository.localProxies() == originalNodes)
            assertTrue("原Profile必须保持", entry.database().profiles().getAll() == beforeProfiles)
            assertEquals(selected, repository.currentProfileId.value)
        }
    }
}
