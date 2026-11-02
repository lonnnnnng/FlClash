package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProxyProtocolScrollInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previousOptions: ProxyPageOptions

    @Before fun setup() {
        assertPortraitDefaultFontConfiguration()
        previousOptions = ProxyPageOptionsStore.load(compose.activity)
        ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
    }

    @After fun restoreOptions() { ProxyPageOptionsStore.save(compose.activity, previousOptions) }

    @Test fun leafProtocolsAndLooseNestedExitRenderInTabAndListBothThemes() {
        val main = ProxyGroupInfo("Main", "Selector", "leaf", listOf("leaf", "DIRECT", "nested", "unknown"), false)
        val nested = ProxyGroupInfo("nested", "Fallback", "leaf", listOf("leaf"), false)
        var dark by mutableStateOf(false)
        val state = ProxyGroupsUiState(groups = listOf(main, nested), unfoldSet = setOf("Main"),
            proxyTypes = mapOf("leaf" to "Trojan", "DIRECT" to "Direct"))
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProxyGroupsCard(state = state, runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 10, currentGroupName = "Main",
                onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } }
        compose.onNodeWithTag("proxy_node_Main_leaf").assert(hasText("Trojan"))
        compose.onNodeWithTag("proxy_node_Main_DIRECT").assert(hasText("Direct"))
        compose.onNodeWithTag("proxy_node_Main_nested").assert(hasText("Fallback"))
        compose.onNodeWithTag("proxy_node_Main_unknown").assert(hasText("unknown"))
        option("card_size", "proxy_card_size_option_LOOSE")
        compose.onNodeWithTag("proxy_node_Main_nested").assert(hasText("Fallback(leaf)"))
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            capture("proxy_protocol_tab_$theme")
        }
        option("display_mode", "proxy_display_mode_LIST")
        compose.onNodeWithTag("proxy_list_node_Main_leaf").assert(hasText("Trojan"))
        compose.onNodeWithTag("proxy_list_node_Main_nested").assert(hasText("Fallback(leaf)"))
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            capture("proxy_protocol_list_$theme")
        }
    }

    @Test fun groupPositionsSurviveDistantTabsLayoutNavigationAndProfileSwitch() {
        val groups = longGroups()
        var id by mutableStateOf(11L)
        var current by mutableStateOf("A")
        var open by mutableStateOf(true)
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            KClashTheme {
                if (open) ProxyGroupsCard(state = ProxyGroupsUiState(groups = groups), runtimeState = CoreRuntimeStatus.STOPPED,
                    currentProfileId = id, currentGroupName = current, scrollStateHolder = holder,
                    onRefresh = {}, onSelectGroup = { current = it }, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
                else Text("另一个页面")
            }
        }
        grid("A").performScrollToIndex(24)
        node("A", 24).assertIsDisplayed()
        tab("C")
        node("C", 0).assertIsDisplayed()
        grid("C").performScrollToIndex(36)
        tab("A")
        node("A", 24).assertIsDisplayed()
        tab("C")
        node("C", 36).assertIsDisplayed()
        option("display_mode", "proxy_display_mode_LIST")
        option("display_mode", "proxy_display_mode_TAB")
        node("C", 36).assertIsDisplayed()
        compose.runOnIdle { open = false }
        compose.onNodeWithText("另一个页面").assertIsDisplayed()
        compose.runOnIdle { open = true }
        node("C", 36).assertIsDisplayed()
        compose.runOnIdle { id = 12 }
        node("C", 0).assertIsDisplayed()
        compose.runOnIdle { id = 11 }
        node("C", 36).assertIsDisplayed()
        compose.onNodeWithTag("proxy_scroll_to_selected").performClick()
        node("C", 0).assertIsDisplayed()
        tab("A")
        // 作者 long：在别的组点击定位，不能覆盖本组上次浏览的独立位置。
        node("A", 24).assertIsDisplayed()
    }

    @Test fun listLocatesInsideGroupAndRestoresScrollAcrossLayoutAndNavigation() {
        val groups = longGroups().take(2).map { it.copy(now = it.all[40]) }
        var open by mutableStateOf(true)
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            KClashTheme {
                if (open) ProxyGroupsCard(state = ProxyGroupsUiState(groups = groups, unfoldSet = setOf("A", "B")),
                    runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 13, currentGroupName = "A", scrollStateHolder = holder,
                    onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
                else Text("另一个页面")
            }
        }
        option("display_mode", "proxy_display_mode_LIST")
        compose.onNodeWithTag("proxy_list_node_A_A-00").assertIsDisplayed()
        compose.onNodeWithTag("proxy_list_group_locate_A").performClick()
        compose.onNodeWithTag("proxy_list_node_A_A-40").assertIsDisplayed()
        option("display_mode", "proxy_display_mode_TAB")
        node("A", 0).assertIsDisplayed()
        option("display_mode", "proxy_display_mode_LIST")
        compose.onNodeWithTag("proxy_list_node_A_A-40").assertIsDisplayed()
        compose.runOnIdle { open = false }
        compose.runOnIdle { open = true }
        compose.onNodeWithTag("proxy_list_node_A_A-40").assertIsDisplayed()
        compose.onNodeWithTag("proxy_groups_list").performScrollToIndex(0)
        compose.onNodeWithTag("proxy_list_group_locate_A").assertIsDisplayed()
    }

    @Test fun gridSemanticAnchorSurvivesSortDensityAndSearchChanges() {
        val members = (0..59).map { index -> "A-${(59 - index).toString().padStart(2, '0')}" }
        val groups = listOf(ProxyGroupInfo("A", "Selector", members.first(), members, false))
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            KClashTheme {
                ProxyGroupsCard(
                    state = ProxyGroupsUiState(groups = groups),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    currentProfileId = 17,
                    currentGroupName = "A",
                    scrollStateHolder = holder,
                    onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {},
                )
            }
        }
        grid("A").performScrollToIndex(40)
        node("A", 19).assertIsDisplayed()

        option("sort", "proxy_sort_option_NAME")
        // 作者 long：排序重排后仍按节点名恢复，不能把旧的第 40 项当作语义位置。
        node("A", 19).assertIsDisplayed()
        option("density", "proxy_density_option_COMPACT")
        node("A", 19).assertIsDisplayed()

        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.onNodeWithTag("proxy_group_search").performTextInput("A-00")
        // 作者 long：锚定节点被搜索过滤后退回同组首个可见节点。
        node("A", 0).assertIsDisplayed()
    }

    private fun longGroups() = listOf("A", "B", "C", "D").map { name ->
        val members = (0..59).map { "$name-${it.toString().padStart(2, '0')}" }
        ProxyGroupInfo(name, "Selector", members.first(), members, false)
    }
    private fun grid(group: String) = compose.onNodeWithTag("proxy_group_members_$group")
    private fun node(group: String, index: Int) = compose.onNodeWithTag("proxy_node_${group}_${group}-${index.toString().padStart(2, '0')}")
    private fun tab(group: String) { compose.onNodeWithTag("proxy_group_tab_$group").performScrollTo().performClick() }
    private fun option(menu: String, choice: String) {
        compose.onNodeWithTag("proxy_tools").performClick()
        compose.onNodeWithTag("proxy_tools_$menu").performClick()
        compose.onNodeWithTag(choice).performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng(name, png)
    }
}
