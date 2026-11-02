package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：同一轮覆盖代理完整页面的布局和操作，固定状态不访问订阅或启动真实核心。 */
class ProxyPageParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previousOptions: ProxyPageOptions

    @Before fun setup() {
        assertPortraitDefaultFontConfiguration()
        previousOptions = ProxyPageOptionsStore.load(compose.activity)
        ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
    }

    @After fun restore() { ProxyPageOptionsStore.save(compose.activity, previousOptions) }

    @Test fun tabAndListAllCardSizesRenderBothThemesAndKeepDelayIndependent() {
        var dark by mutableStateOf(false)
        var expanded by mutableStateOf(setOf("节点选择"))
        var selected by mutableStateOf("香港节点")
        var tested by mutableStateOf(0)
        val group = ProxyGroupInfo("节点选择", "Selector", selected,
            listOf("香港节点", "日本节点", "这是一个很长的节点名称用来检查两行省略", "自动选择"), false)
        val nested = ProxyGroupInfo("自动选择", "URLTest", "香港节点", listOf("香港节点"), false)
        compose.setContent {
            KClashTheme(darkTheme = dark) {
                val state = ProxyGroupsUiState(groups = listOf(group.copy(now = selected), nested),
                    unfoldSet = expanded, proxyTypes = mapOf("香港节点" to "Trojan", "日本节点" to "Vless"),
                    delayResults = mapOf(
                        ProxyDelayKey(group.name, "香港节点", CoreBridge.DEFAULT_TEST_URL) to ProxyDelayUiState(value = 100),
                        ProxyDelayKey(group.name, "日本节点", CoreBridge.DEFAULT_TEST_URL) to ProxyDelayUiState(value = 600),
                    ))
                ProxyGroupsCard(modifier = Modifier.fillMaxSize(), state = state,
                    runtimeState = CoreRuntimeStatus.STOPPED, currentGroupName = group.name,
                    onRefresh = {}, onToggleGroup = { name -> expanded = if (name in expanded) expanded - name else expanded + name },
                    onSelect = { _, node -> selected = node }, onTestDelay = { _, _ -> tested++ }, onTestGroupDelay = {})
            }
        }
        // 作者 long：对可见测速目标执行真实触摸，确保压缩高度后仍不会触发父卡片选择。
        compose.onNodeWithTag("proxy_node_delay_节点选择_日本节点", true).performTouchInput { click() }
        compose.runOnIdle { check(tested == 1 && selected == "香港节点") }
        for (layout in listOf("TAB", "LIST")) {
            if (layout == "LIST") option("display_mode", "proxy_display_mode_LIST")
            for (size in ProxyNodeCardSize.entries) {
                option("card_size", "proxy_card_size_option_" + size.name)
                for (theme in listOf("light", "dark")) {
                    compose.runOnIdle { dark = theme == "dark" }
                    val tag = if (layout == "TAB") "proxy_node_节点选择_日本节点" else "proxy_list_node_节点选择_日本节点"
                    compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(when (size) {
                        ProxyNodeCardSize.COMPACT -> 66.dp
                        ProxyNodeCardSize.STANDARD -> 86.dp
                        ProxyNodeCardSize.LOOSE -> 105.dp
                    })
                    val longName = "这是一个很长的节点名称用来检查两行省略"
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose.onNode(hasText(longName) and hasAnyAncestor(hasTestTag(
                        (if (layout == "TAB") "proxy_node_节点选择_" else "proxy_list_node_节点选择_") + longName)), true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    check(layouts.single().lineCount == if (size == ProxyNodeCardSize.COMPACT) 1 else 2) {
                        "layout=" + layout + " size=" + size + " lineCount=" + layouts.single().lineCount + " bounds=" + layouts.single().size
                    }
                    capture("proxy_page_" + layout.lowercase() + "_" + size.name.lowercase() + "_" + theme)
                }
            }
        }
        compose.onNodeWithTag("proxy_list_group_header_节点选择").performClick()
        compose.onNodeWithTag("proxy_list_group_locate_节点选择").assertDoesNotExist()
        compose.onNodeWithTag("proxy_list_group_batch_节点选择").assertDoesNotExist()
        capture("proxy_page_list_collapsed_dark")
        compose.onNodeWithTag("proxy_list_group_header_节点选择").performClick()
        compose.onNodeWithTag("proxy_list_group_locate_节点选择").assertIsDisplayed()
        compose.onNodeWithTag("proxy_list_group_batch_节点选择").assertIsDisplayed()
    }

    @Test fun overflowingGroupsCanBePickedAndSearchClearsWithoutLosingSelection() {
        val names = (0..12).map { "分组" + it }
        var current by mutableStateOf(names.first())
        val groups = names.map { ProxyGroupInfo(it, "Selector", "node-a", listOf("node-a", "node-b"), false) }
        compose.setContent { KClashTheme {
            ProxyGroupsCard(state = ProxyGroupsUiState(groups = groups), runtimeState = CoreRuntimeStatus.STOPPED,
                currentGroupName = current, onSelectGroup = { current = it },
                onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } }
        compose.onNodeWithTag("proxy_group_more").performClick()
        compose.onNodeWithTag("proxy_group_menu_分组12").performScrollTo().performClick()
        compose.onNodeWithTag("proxy_group_tab_分组12").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.onNodeWithTag("proxy_group_search").performTextInput("node-b")
        compose.onNodeWithTag("proxy_node_分组12_node-a").assertDoesNotExist()
        compose.onNodeWithTag("proxy_tools").assertDoesNotExist()
        compose.onNodeWithTag("proxy_group_batch_分组12").assertIsDisplayed()
        compose.onNodeWithTag("proxy_search_clear").performClick()
        compose.onNodeWithTag("proxy_node_分组12_node-a").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.onNodeWithTag("proxy_group_search").assertDoesNotExist()
        compose.onNodeWithTag("proxy_group_tab_分组12").assertIsSelected()
    }

    private fun option(menu: String, value: String) {
        compose.onNodeWithTag("proxy_tools").performClick()
        compose.onNodeWithTag("proxy_tools_" + menu).performClick()
        compose.onNodeWithTag(value).performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("proxy_groups_page").captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        recordVisualPng(name, png)
    }
}
