package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：首屏截图复用正式 Scaffold 与卡片；固定数据只证明视觉排版，核心和系统入口由真实宿主 E2E 验证。
 */
@RunWith(AndroidJUnit4::class)
class DashboardInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DashboardTestActivity>()

    @Test
    fun dashboardScreenshotMatrixMatchesBaseline() {
        val actual = linkedMapOf("default" to captureHash("default"))
        composeRule.activityRule.scenario.onActivity { it.showSystemReadyScenario() }
        actual["system_ready"] = captureHash("system_ready")
        assertBaselines(actual)
    }

    @Test
    fun dashboardRunningStateRendersStatsAndDiagnosticEvidence() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_runtime_card").assertIsDisplayed()
        composeRule.onNodeWithText("核心运行中").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("停止核心").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_stats_card").assertIsDisplayed()
        composeRule.onNodeWithText("3 个连接").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_sync").assertIsNotEnabled()
        composeRule.onNodeWithText("固定诊断样例").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_probe_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("main_navigation_dock").assertIsDisplayed()
    }

    @Test
    fun dashboardProfilePickerChangesCurrentProfile() {
        composeRule.activityRule.scenario.onActivity { it.showProfilePickerScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().assertIsDisplayed()
        // 作者 long：整卡详情入口会合并无独立操作的子节点，仍需检查实际绘制的用量条。
        composeRule.onNodeWithTag("dashboard_profile_usage_track", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_switch").performClick()
        composeRule.onNodeWithTag("dashboard_profile_picker").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_option_102").performClick()
        composeRule.onNodeWithText("固定自定义配置").assertIsDisplayed()
    }

    @Test
    fun dashboardRunningProfilePickerGuidesUserToStopBeforeSwitching() {
        composeRule.activityRule.scenario.onActivity { it.showRunningProfilePickerScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_switch").performClick()
        composeRule.onNodeWithTag("dashboard_profile_picker").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_runtime_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_option_102").assertIsNotEnabled()

        composeRule.onNodeWithTag("dashboard_profile_stop_runtime").performClick()
        composeRule.onNodeWithTag("dashboard_profile_runtime_notice").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_profile_option_102").performClick()
        composeRule.onNodeWithText("固定自定义配置").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.runtimeStopCount) }
    }

    @Test
    fun dashboardProfileCardOpensProfileDetailSheet() {
        composeRule.activityRule.scenario.onActivity { it.showProfilePickerScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_detail_name").assertTextEquals("固定远程订阅")
        composeRule.onNodeWithTag("dashboard_profile_detail_source").assertTextEquals("远程订阅")
        composeRule.onNodeWithTag("dashboard_profile_detail_open_profiles").performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_sheet").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.profilesNavigationCount) }
    }

    @Test
    fun dashboardRunningProfileDetailRemainsReadableAndDisablesSync() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_name").assertTextEquals("固定诊断样例")
        composeRule.onNodeWithTag("dashboard_profile_detail_sync").assertIsNotEnabled()
        composeRule.onNodeWithTag("dashboard_profile_detail_open_profiles").performClick()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.profilesNavigationCount) }
    }

    @Test
    fun dashboardProfileDetailSyncShowsProgressAndPreventsRepeatedSync() {
        composeRule.activityRule.scenario.onActivity { it.showProfilePickerScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_sync").performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_sync_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_detail_sync").assertIsNotEnabled()
    }

    @Test
    fun dashboardOutboundModeControlChangesSelectedMode() {
        composeRule.onNodeWithTag("dashboard_outbound_mode_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_mode_rule").assertIsSelected()
        composeRule.onNodeWithTag("dashboard_mode_global").performClick()
        composeRule.onNodeWithTag("dashboard_mode_global").assertIsSelected()
        composeRule.onNodeWithTag("dashboard_mode_rule").assertIsNotSelected()
        composeRule.onNodeWithTag("dashboard_mode_direct").performClick()
        composeRule.onNodeWithTag("dashboard_mode_direct").assertIsSelected()
        composeRule.onNodeWithTag("dashboard_mode_global").assertIsNotSelected()
        // 作者 long：重复点击当前模式必须保持当前选择，Flutter setup action 对相同模式直接返回。
        composeRule.onNodeWithTag("dashboard_mode_direct").performClick()
        composeRule.onNodeWithTag("dashboard_mode_direct").assertIsSelected()
    }

    @Test
    fun dashboardRemoteProfileSyncShowsBusyState() {
        composeRule.activityRule.scenario.onActivity { it.showProfilePickerScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_sync").performClick()
        composeRule.onNodeWithTag("dashboard_profile_sync_loading").assertIsDisplayed()
    }

    @Test
    fun dashboardRunningStateScreenshotMatrixMatchesBaseline() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        assertBaselines(mapOf("running" to captureHash("running")))
    }

    @Test
    fun dashboardLightThemeScreenshotMatrixMatchesBaseline() {
        val actual = linkedMapOf<String, String>()
        composeRule.activityRule.scenario.onActivity { it.setDarkTheme(false) }
        actual["light_default"] = captureHash("light_default")
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        actual["light_running"] = captureHash("light_running")
        composeRule.onNodeWithTag("main_navigation_dock").assertIsDisplayed()
        assertBaselines(actual)
    }

    @Test
    fun dashboardProxySummaryFollowsOutboundMode() {
        composeRule.activityRule.scenario.onActivity { it.showProxyGroupsScenario() }
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performScrollTo()
        composeRule.onNodeWithTag("dashboard_proxy_groups_count", useUnmergedTree = true).assertTextEquals("5")
        composeRule.onNodeWithTag("dashboard_mode_global").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performScrollTo()
        composeRule.onNodeWithTag("dashboard_proxy_groups_count", useUnmergedTree = true).assertTextEquals("6")
        composeRule.onNodeWithTag("dashboard_mode_direct").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performScrollTo().assertHasNoClickAction()
        composeRule.onNodeWithText("没有代理组").assertIsDisplayed()
    }

    @Test
    fun dashboardProxyPickerKeepsOpenForRepeatedSelectionAndResetsSearch() = withProxyOptions(ProxyPageOptions()) {
        openProxyPicker()
        composeRule.onNodeWithTag("proxy_tools").assertDoesNotExist()
        composeRule.onNodeWithTag("proxy_scroll_to_selected").assertDoesNotExist()
        composeRule.onNodeWithTag("proxy_groups_refresh").assertDoesNotExist()
        composeRule.onNodeWithTag("proxy_group_batch_机场选择").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_机场选择_日本节点").performClick()
        composeRule.onNodeWithTag("proxy_node_机场选择_香港节点").performClick()
        composeRule.onNodeWithTag("dashboard_proxy_picker").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, composeRule.activity.proxySelectionCount) }
        composeRule.onNodeWithTag("proxy_group_search").performTextInput("日本")
        composeRule.onNodeWithTag("proxy_node_机场选择_香港节点").assertDoesNotExist()
        composeRule.onNodeWithTag("proxy_node_机场选择_日本节点").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_group_search").assertIsDisplayed()
        recordNodePng("dashboard_proxy_picker_search_keyboard_dark", "connections_detail_sheet")
        composeRule.onNodeWithTag("dashboard_proxy_picker_close").performClick()
        composeRule.onNodeWithTag("dashboard_proxy_picker").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performClick()
        composeRule.onNodeWithTag("proxy_node_机场选择_香港节点").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_机场选择_日本节点").assertIsDisplayed()
    }

    @Test
    fun dashboardProxyPickerInheritsListLayoutAndMiddleDragKeepsSheetOpen() =
        withProxyOptions(ProxyPageOptions(displayMode = ProxyDisplayMode.LIST)) {
            openProxyPicker()
            composeRule.onNodeWithTag("proxy_group_tabs").assertDoesNotExist()
            composeRule.onNodeWithTag("proxy_groups_list").assertIsDisplayed()
            composeRule.onNodeWithTag("proxy_list_group_header_机场选择").performClick()
            composeRule.onNodeWithTag("proxy_list_node_机场选择_日本节点").assertIsDisplayed()
            composeRule.onNodeWithTag("proxy_groups_list").performScrollToIndex(2)
            composeRule.onNodeWithTag("proxy_groups_list").performTouchInput { swipeDown() }
            composeRule.onNodeWithTag("dashboard_proxy_picker").assertIsDisplayed()
            composeRule.onNodeWithTag("dashboard_proxy_picker_close").performClick()
            composeRule.onNodeWithTag("dashboard_proxy_picker").assertDoesNotExist()
        }

    @Test
    fun dashboardProxySummaryAndPickerCaptureDarkAndLight() = withProxyOptions(ProxyPageOptions()) {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showProxyGroupsScenario() }
        for (dark in listOf(true, false)) {
            composeRule.activityRule.scenario.onActivity { it.setDarkTheme(dark) }
            val theme = if (dark) "dark" else "light"
            composeRule.onNodeWithTag("dashboard_proxy_groups_card").performScrollTo()
            assertEquals(proxyPickerBaselines["summary_$theme"], recordNodePng("dashboard_proxy_summary_$theme", "dashboard_proxy_groups_card"))
            composeRule.onNodeWithTag("dashboard_proxy_groups_card").performClick()
            assertEquals(proxyPickerBaselines["picker_$theme"], recordNodePng("dashboard_proxy_picker_$theme", "connections_detail_sheet"))
            composeRule.onNodeWithTag("dashboard_proxy_picker_close").performClick()
        }
    }

    private fun openProxyPicker() {
        composeRule.activityRule.scenario.onActivity { it.showProxyGroupsScenario() }
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_proxy_picker").assertIsDisplayed()
    }

    private fun withProxyOptions(options: ProxyPageOptions, block: () -> Unit) {
        val context = composeRule.activity.applicationContext
        val original = ProxyPageOptionsStore.load(context)
        try {
            // 作者 long：弹层继承真实应用偏好，测试后恢复原设置，避免改变后续真机使用的布局。
            composeRule.runOnIdle { ProxyPageOptionsStore.save(context, options) }
            block()
        } finally {
            composeRule.runOnIdle { ProxyPageOptionsStore.save(context, original) }
        }
    }

    private fun recordNodePng(name: String, tag: String): String {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        recordVisualPng(name, png)
        return MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
    }

    private fun captureHash(name: String): String {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag("dashboard_page").captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        recordVisualPng("dashboard_$name", png)
        return MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
    }

    private fun assertBaselines(actual: Map<String, String>) {
        val mismatches = actual.mapNotNull { (name, hash) ->
            val expected = baselines[name]
            if (expected == hash) null else "$name expected=$expected actual=$hash"
        }
        check(mismatches.isEmpty()) { "dashboard screenshot baseline mismatch: ${mismatches.joinToString("; ")}" }
    }
}

private val baselines = mapOf(
    "default" to "2e5cfd2f65d73acf1ea6ac7c36634a466a6e7b5465fd4a410cb2c20addac8bd0",
    "system_ready" to "2e5cfd2f65d73acf1ea6ac7c36634a466a6e7b5465fd4a410cb2c20addac8bd0",
    "running" to "2c4fffe57c45d2a04ced846eca0bba6b21018306b629986374f8bb1abd979447",
    "light_default" to "af78e2abc59c3fb15c42d517d1bf90979796948f0772d13dc4cbbd79e36c3eef",
    "light_running" to "d309fe0472fbe453e2a09ec70fc5bdc7f0487ad833d0bf772b9f7b9960390c8f",
)

private val proxyPickerBaselines = mapOf(
    "summary_dark" to "6fd94883371c103cefb6657b5c0181594310139a4cca060036cbbc6c58ce1a2a",
    "summary_light" to "43deeeb5ae0308173edfa2ceabe6b86f8380b6e32c84cbe282db6d5e0f247d63",
    "picker_dark" to "62847a1e78ebf50115e56f5a8a0ba18393251f7d51002cd4fd55f4bc6db05d46",
    "picker_light" to "43e3cb2030ed1f63c927dfb187c964ab3a09bf5e34ffc4c8f275b39c5016ecdc",
)
