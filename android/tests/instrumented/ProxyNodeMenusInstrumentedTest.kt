package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：菜单操作与卡片点击共用选择入口，验证长按本身不切节点，图库/排序等前序页面不参与此用例。 */
class ProxyNodeMenusInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun selectorPreferredAndNonselectableMenusKeepSelectionAndDelayIndependentInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var kind by mutableStateOf("Selector")
        var chosen by mutableStateOf(false)
        var blocked by mutableStateOf(false)
        var selections = 0; var delays = 0; var additions = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalAddToLocalProxy provides { id, name -> assertEquals(17L, id); assertEquals("leaf", name); additions++ }) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    ProxyNodeCard("leaf", "Vless", inUse = chosen, preferred = chosen,
                        enabled = !blocked && kind != "LoadBalance", menuEnabled = !blocked, delayEnabled = !blocked,
                        delay = null, selectionTag = "node", delayTag = "delay", groupType = kind, addable = true, profileId = 17,
                        onSelect = { chosen = !chosen; selections++ }, onTestDelay = { delays++ })
                }
            }
        } }
        for (theme in listOf(false, true)) for (group in listOf("Selector", "URLTest", "Fallback", "LoadBalance")) {
            compose.runOnIdle { dark = theme; kind = group; chosen = false }
            val before = selections
            compose.onNodeWithTag("node").performTouchInput { longClick() }
            compose.runOnIdle { assertEquals(before, selections) }
            if (group == "LoadBalance") compose.onNodeWithTag("node_menu_select").assertDoesNotExist()
            else {
                compose.onNodeWithTag("node_menu_select").assert(hasText(if (group == "Selector") "选择" else "优选")).performClick()
                compose.runOnIdle { assertTrue(chosen); assertEquals(before + 1, selections) }
                compose.onNodeWithTag("node").performTouchInput { longClick() }
                compose.onActionMarker("已选中").assertExists()
            }
            capture("node_menu_${group}_$theme")
            compose.onNodeWithTag("node_menu_add_local").performClick()
            compose.onNodeWithTag("delay").performClick()
        }
        compose.runOnIdle { assertEquals(8, additions); assertEquals(8, delays); blocked = true }
        compose.onNodeWithTag("node").performTouchInput { longClick() }
        compose.onNodeWithTag("node_menu_add_local").assertDoesNotExist()
    }

    @Test fun customProviderAndBuiltinPoliciesApplyToBothPageLayouts() {
        assertPortraitDefaultFontConfiguration()
        val previous = ProxyPageOptionsStore.load(compose.activity)
        ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
        var additions = 0
        var dark by mutableStateOf(false)
        val main = ProxyGroupInfo("Main", "Selector", "inline", listOf("inline", "provider", "DIRECT", "nested"), false)
        val nested = ProxyGroupInfo("nested", "Selector", "inline", listOf("inline"), false)
        val state = ProxyGroupsUiState(groups = listOf(main, nested), unfoldSet = setOf("Main"), customProfile = true,
            profileId = 18, providerProxies = setOf("provider"), proxyTypes = mapOf("inline" to "Vless", "provider" to "Trojan", "DIRECT" to "Direct"))
        try {
            compose.setContent { KClashTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAddToLocalProxy provides { id, name -> assertEquals(18L, id); assertEquals("provider", name); additions++ }) {
                    ProxyGroupsCard(state = state, runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 18, currentGroupName = "Main",
                        onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
                }
            } }
            for (layout in listOf("TAB", "LIST")) {
                if (layout == "LIST") {
                    compose.onNodeWithTag("proxy_tools").performClick()
                    compose.onNodeWithTag("proxy_tools_display_mode").performClick()
                    compose.onNodeWithTag("proxy_display_mode_LIST").performClick()
                }
                val prefix = if (layout == "TAB") "proxy_node_Main_" else "proxy_list_node_Main_"
                for (theme in listOf(false, true)) {
                    compose.runOnIdle { dark = theme }
                    for (name in listOf("inline", "DIRECT", "nested")) {
                        compose.onNodeWithTag(prefix + name).performTouchInput { longClick() }
                        compose.onNodeWithTag(prefix + name + "_menu_add_local").assertDoesNotExist()
                        compose.onNodeWithTag(prefix + name + "_menu_select").performClick()
                    }
                    compose.onNodeWithTag(prefix + "provider").performTouchInput { longClick() }
                    capture("node_provider_${layout}_$theme")
                    compose.onNodeWithTag(prefix + "provider_menu_add_local").performClick()
                }
            }
            compose.runOnIdle { assertEquals(4, additions) }
        } finally { ProxyPageOptionsStore.save(compose.activity, previous) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
