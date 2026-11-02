package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：代理选择需要在真实 Activity 生命周期中验证页面事件、ViewModel 状态和 Profile 映射一起收敛；
 * 这里仅替换页面依赖的运行时契约，不启动真实 Mihomo 或 VPN，避免把网络偶发性混入 UI 回归。
 */
@RunWith(AndroidJUnit4::class)
class ProxyGroupsActivityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProxyGroupsTestActivity>()

    @Test
    fun proxySelectionPersistsAndComputedGroupCanReturnToAutomatic() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_node_Auto_node-a").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("proxy_node_Auto_node-b").assertIsDisplayed().performClick()

        composeRule.waitUntil(5_000) {
            composeRule.activity.viewModel.state.value.selectedMap == mapOf("Auto" to "node-b")
        }
        assertEquals("node-b", composeRule.activity.runtime.lastTarget)
        assertEquals(mapOf("Auto" to "node-b"), composeRule.activity.profileStore.selectedMap())

        composeRule.onNodeWithTag("proxy_node_Auto_node-b").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.activity.viewModel.state.value.selectedMap.isEmpty()
        }
        assertEquals("", composeRule.activity.runtime.lastTarget)
        assertEquals(emptyMap<String, String>(), composeRule.activity.profileStore.selectedMap())
    }

    @Test
    fun proxyGroupTabsShowOneGroupAndPersistCurrentGroup() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_group_tab_nested").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("proxy_node_Auto_node-a").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_nested_leaf").assertIsNotDisplayed()

        composeRule.onNodeWithTag("proxy_group_tab_nested").performClick().assertIsSelected()
        composeRule.onNodeWithTag("proxy_node_nested_leaf").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_Auto_node-a").assertIsNotDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.activity.profileStore.profiles.value.single().currentGroupName == "nested"
        }
    }

    @Test
    fun proxyGroupsScreenshotMatrixMatchesBaseline() {
        assertPortraitDefaultFontConfiguration()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_node_Auto_node-a").fetchSemanticsNodes().isNotEmpty()
        }

        fun captureHash(name: String): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("proxy_groups_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordVisualPng("proxy_$name", png)
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            "page_dark" to "167f41b4df3c1602e0be8e11a13e35a5e7c3c07c5c9ef0c4abb2323a75866623",
            "page_light" to "d84a4b882e795cff01901d00c0e34c48f29ea082467ca97d7a12779756e7fbac",
        )
        val actual = buildMap {
            listOf(true, false).forEach { dark ->
                val theme = if (dark) "dark" else "light"
                composeRule.activityRule.scenario.onActivity { it.showResultsScenario(dark) }
                composeRule.onNodeWithTag("proxy_node_Auto_node-a").assertIsSelected()
                composeRule.onNodeWithTag("proxy_node_Auto_node-b").assertIsDisplayed()
                put("page_$theme", captureHash("page_$theme"))
            }
        }
        val mismatches = expected.mapNotNull { (state, expectedHash) ->
            val actualHash = actual.getValue(state)
            if (actualHash == expectedHash) null else "$state expected=$expectedHash actual=$actualHash"
        }
        if (mismatches.isNotEmpty()) {
            error("proxy screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }

    @Test
    fun searchFiltersInlineNodeGridAndCanBeClosed() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_node_Auto_node-a").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("proxy_group_search_toggle").performClick()
        composeRule.onNodeWithTag("proxy_group_search").performTextInput("node-b")
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("proxy_node_Auto_node-a").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("proxy_group_search_toggle").performClick()
        composeRule.onNodeWithTag("proxy_node_Auto_node-a").assertIsDisplayed()
    }

    @Test
    fun delayActionDoesNotSelectNodeAndCurrentChoiceHasSelectedSemantics() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_node_Auto_node-a").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("proxy_node_Auto_node-a").assertIsSelected()
        // 作者 long：直接触摸卡片内独立测速区，确认它不会冒泡成节点选择。
        composeRule.onNodeWithTag("proxy_node_delay_Auto_node-b", useUnmergedTree = true)
            .assertIsDisplayed().performTouchInput { click() }
        composeRule.waitUntil(5_000) {
            composeRule.activity.viewModel.state.value.delayResults.values.any { it.value == 100 }
        }

        composeRule.onNodeWithTag("proxy_node_delay_Auto_node-b", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("node-b", composeRule.activity.runtime.lastDelayTarget)
        assertEquals(null, composeRule.activity.runtime.lastTarget)
        assertEquals(emptyMap<String, String>(), composeRule.activity.profileStore.selectedMap())
    }

    @Test
    fun testingNodeDisablesRepeatDelayWhileOtherPageActionsRemainAvailable() {
        composeRule.activityRule.scenario.onActivity { it.showResultsScenario(dark = true, testing = true) }
        composeRule.onNodeWithTag("proxy_node_delay_Auto_node-b", useUnmergedTree = true).assertIsNotEnabled()
        composeRule.onNodeWithTag("proxy_node_Auto_node-b").assertIsEnabled()
        composeRule.onNodeWithTag("proxy_tools").performClick()
        composeRule.onNodeWithTag("proxy_groups_refresh").assertIsDisplayed()
    }

    @Test
    fun proxyToolsMenuChangesGridDensityAndCurrentNodeCanBeLocated() {
        composeRule.onNodeWithTag("proxy_tools").performClick()
        composeRule.onNodeWithTag("proxy_tools_density").performClick()
        composeRule.onNodeWithTag("proxy_density_option_COMPACT").performClick()

        composeRule.onNodeWithTag("proxy_tools").performClick()
        composeRule.onNodeWithTag("proxy_tools_hide_timeout").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.showLongSelectionScenario() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_node_Auto_node-00").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("proxy_scroll_to_selected").performClick()
        composeRule.onNodeWithTag("proxy_node_Auto_node-19").assertIsDisplayed()
    }

    @Test
    fun proxyListLayoutShowsGroupSectionsAndListIconMenu() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_node_Auto_node-a").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("proxy_tools").performClick()
        composeRule.onNodeWithTag("proxy_tools_display_mode").performClick()
        composeRule.onNodeWithTag("proxy_display_mode_LIST").performClick()

        composeRule.onNodeWithTag("proxy_groups_list").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_list_group_Auto").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_list_group_header_Auto").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("proxy_list_node_Auto_node-a").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("proxy_list_group_header_Auto").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_list_node_Auto_node-a").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("proxy_list_node_Auto_node-a").assertIsDisplayed()
        assertEquals(setOf("Auto"), composeRule.activity.profileStore.unfoldSet())
        composeRule.onNodeWithTag("proxy_list_group_header_Auto").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("proxy_list_node_Auto_node-a").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(composeRule.activity.profileStore.unfoldSet().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("proxy_group_tabs").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("proxy_tools").performClick()
        composeRule.onNodeWithTag("proxy_tools_icon_style").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("proxy_icon_style_HIDDEN").performClick()
        composeRule.onNodeWithTag("proxy_list_group_Auto").assertIsDisplayed()
    }
}
