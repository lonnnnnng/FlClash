package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalProxiesPageInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val alpha = LocalProxy("alpha", """{"name":"Alpha","type":"ss","server":"example.test","port":443,"cipher":"aes-128-gcm","password":"test-only"}""")
    @Test fun pageSearchSelectionAndProtocolEditorRenderInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var state by mutableStateOf(LocalProxiesUiState(loading = false, rows = listOf(alpha)))
        var saved: LocalProxy? = null
        compose.setContent { KClashTheme(darkTheme = dark) {
            LocalProxiesPage(state, {}, { state = state.copy(editor = LocalProxyDraft(it, it ?: LocalProxy())) },
                { saved = it; state = state.copy(editor = null) }, { state = state.copy(editor = null) }, {}, {}, {}, {},
                { state = state.copy(deleting = it, users = listOf("引用配置")) }, { state = state.copy(rows = emptyList(), deleting = emptySet()) }, { state = state.copy(deleting = emptySet()) }, {})
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; state = LocalProxiesUiState(loading = false, rows = listOf(alpha)) }
            capture("local_proxies_list_$theme")
            compose.onNodeWithTag("local_proxies_search").performTextReplacement("没有")
            compose.onNodeWithText("没有匹配的节点").assertIsDisplayed()
            compose.onActionMarker("清空搜索").performClick()
            compose.onNodeWithTag("local_proxy_alpha").performClick()
            compose.onNodeWithTag("local_proxy_field_password").assertIsDisplayed()
            compose.onActionMarker("显示密码").performClick()
            capture("local_proxy_ss_editor_$theme")
            compose.onNodeWithText("代理类型").performClick()
            choose("type", "vless")
            compose.onNodeWithTag("local_proxy_field_uuid").performTextReplacement("11111111-1111-1111-1111-111111111111")
            clickSetting("local_proxy_add_option")
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("local_proxy_choices_option").fetchSemanticsNodes().isNotEmpty() }
            capture("local_proxy_options_$theme")
            choose("option", "network")
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("local_proxy_choices_option").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("传输协议").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            choose("network", "ws")
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("local_proxy_choices_network").fetchSemanticsNodes().isEmpty() }
            clickSetting("local_proxy_add_option")
            choose("option", "wsPath")
            compose.onNodeWithTag("local_proxy_field_ws-opts.path").performScrollTo().performTextReplacement("/transport")
            capture("local_proxy_transport_$theme")
            compose.onNodeWithTag("local_proxy_save").performClick()
            compose.runOnIdle {
                assertEquals("vless", saved!!.type)
                assertEquals("/transport", LocalProxies.value(saved!!.fields, "ws-opts.path")!!.asString)
                assertFalse(saved!!.fields.has("password"))
            }
        }
    }
    @Test fun missingReferencesDeleteAndUnsavedDraftRequireConfirmation() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(LocalProxiesUiState(loading = false, rows = listOf(alpha)))
        var deletes = 0
        compose.setContent { KClashTheme {
            LocalProxiesPage(state, {}, { state = state.copy(editor = LocalProxyDraft(it, it ?: LocalProxy())) }, {}, { state = state.copy(editor = null) }, {}, {}, {}, {},
                { state = state.copy(deleting = it, users = listOf("正在引用的配置")) }, { deletes++; state = state.copy(rows = emptyList(), editor = null, deleting = emptySet()) }, { state = state.copy(deleting = emptySet()) }, {})
        } }
        compose.onNodeWithTag("local_proxy_alpha").performClick()
        compose.onNodeWithTag("local_proxy_field_name").performTextReplacement("Changed")
        compose.onNodeWithTag("local_proxy_editor_back").performClick()
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        capture("local_proxy_discard")
        compose.onNodeWithText("继续编辑").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("继续编辑").fetchSemanticsNodes().isEmpty() }
        // 作者 long：继续编辑会恢复原输入焦点；先收起IME并等视口稳定，再用真实触摸点击屏内删除入口。
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle(); android.os.SystemClock.sleep(350)
        compose.onNodeWithText("删除节点").performScrollTo().assertIsDisplayed().performClick()
        capture("local_proxy_delete_references")
        compose.waitUntil(5_000) { compose.onNodeWithText("以下配置正在引用节点", substring = true).isDisplayed() }
        compose.onNodeWithText("以下配置正在引用节点", substring = true).assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("local_proxy_editor_back").performClick()
        compose.onNodeWithText("放弃修改", substring = false).performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onActionMarker("删除选中节点").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
        compose.onNodeWithText("暂无本地代理").assertIsDisplayed()
        capture("local_proxy_empty")
    }
    @Test fun dialerListFiltersLoopsAndPersistsEachSelectionInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        val source = """
            proxies: [{name: Alpha, type: ss}, {name: Beta, type: direct}]
            proxy-groups:
              - {name: Main, type: select, proxies: [Alpha]}
              - {name: Relay, type: select, proxies: [Beta]}
        """.trimIndent()
        var state by mutableStateOf(ProfileDialersEditorUiState(Profile.custom(1, "链式设置"), source, "Alpha -> missing"))
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileDialersPage(state, {}, { name, target ->
                val mapping = ProfileDialerEditor.parseDialers(state.dialers).toMutableMap()
                if (target == null) mapping.remove(name) else mapping[name] = target
                state = state.copy(dialers = ProfileDialerEditor.renderDialers(mapping))
            }, {})
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; state = state.copy(dialers = "Alpha -> missing") }
            compose.onNodeWithText("目标不存在，请重新选择").assertIsDisplayed()
            capture("profile_dialers_missing_$theme")
            compose.onNodeWithTag("profile_dialer_Alpha").performClick()
            compose.onNodeWithTag("profile_dialer_target_Alpha").assertDoesNotExist()
            compose.onNodeWithTag("profile_dialer_target_Main").assertDoesNotExist()
            compose.onNodeWithTag("profile_dialer_target_Relay").assertIsDisplayed()
            capture("profile_dialers_targets_$theme")
            compose.onNodeWithTag("profile_dialer_target_Beta").performClick()
            compose.runOnIdle { assertEquals(mapOf("Alpha" to "Beta"), ProfileDialerEditor.parseDialers(state.dialers)) }
            compose.onNodeWithTag("profile_dialer_Alpha").performClick()
            compose.onNodeWithTag("profile_dialer_target_Beta").assertIsSelected()
            compose.onNodeWithTag("profile_dialer_none").assertIsNotSelected().performClick()
            compose.runOnIdle { assertTrue(state.dialers.isEmpty()) }
        }
    }

    private fun choose(kind: String, value: String) {
        // 作者 long：独立 Dialog 的 Window 注册晚于父页重组，等待实际选择页可见后才注入触摸，避免点到尚未退场的父页。
        compose.waitUntil(5_000) { compose.onNodeWithTag("local_proxy_choices_$kind").isDisplayed() }
        compose.onNodeWithTag("local_proxy_choice_$value").performScrollTo().performClick()
    }

    private fun clickSetting(tag: String) {
        // 作者 long：字段输入触发的 IME 动画会改变屏幕坐标；使用可访问点击动作验证控件行为，窗口选择仍走真实触摸。
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
