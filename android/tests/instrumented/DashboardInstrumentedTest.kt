package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun providerDetailsSupportReadOnlyUpdatesLoadingFailureRetryAndEmpty() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showProfileProviders("loading") }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_providers_loading").performScrollTo().assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.showProfileProviders("error") }
        composeRule.onNodeWithTag("profile_providers_retry").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.providerRetryCount) }
        composeRule.activityRule.scenario.onActivity { it.showProfileProviders("empty") }
        composeRule.onNodeWithTag("profile_providers_empty").performScrollTo().assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.showProfileProviders("ready") }
        composeRule.onNodeWithTag("profile_provider_update_1").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_provider_update_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_provider_updating_0").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.providerUpdateCount) }
        composeRule.activityRule.scenario.onActivity { it.showProfileProviders("failed") }
        composeRule.onNodeWithTag("profile_provider_update_message").performScrollTo().assertTextEquals("更新服务返回异常，请稍后重试")
        composeRule.onNodeWithTag("profile_provider_update_0").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(2, composeRule.activity.providerUpdateCount) }
        composeRule.activityRule.scenario.onActivity { it.showProfileProviders("updated") }
        composeRule.onNodeWithTag("profile_provider_update_message").performScrollTo().assertTextEquals("Provider 已更新")
    }

    @Test fun providerDetailsCaptureThemesWithActionsReachable() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showProfileProviders("ready") }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        listOf(true, false).forEach { dark ->
            composeRule.activityRule.scenario.onActivity { it.showProfileProviders("ready", dark) }
            composeRule.onNodeWithTag("dashboard_profile_detail_open_profiles").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("profile_provider_update_0").assertIsDisplayed()
            recordWindowPng(if (dark) "profile_providers_dark" else "profile_providers_light")
        }
    }

    @Test fun profileStatisticsShowsLoadingFailureRetryAndRealZeroCounts() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showProfileStatistics("loading") }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_statistics_value_0", useUnmergedTree = true).assertTextEquals("…")
        composeRule.activityRule.scenario.onActivity { it.showProfileStatistics("error") }
        composeRule.onNodeWithTag("profile_statistics_value_0", useUnmergedTree = true).assertTextEquals("—")
        composeRule.onNodeWithTag("profile_statistics_retry").performClick()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.statisticsRetries) }
        composeRule.activityRule.scenario.onActivity { it.showProfileStatistics("empty") }
        (0..3).forEach { index -> composeRule.onNodeWithTag("profile_statistics_value_" + index, useUnmergedTree = true).assertTextEquals("0") }
        composeRule.onNodeWithTag("dashboard_profile_detail_preview").performClick()
        composeRule.onNodeWithTag("runtime_config_preview_loading").assertIsDisplayed()
    }

    @Test fun profileStatisticsCapturesBothThemesAndKeepsProfileActionsReachable() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showProfileStatistics("ready") }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        listOf("12", "10024", "70000", "9").forEachIndexed { index, value ->
            composeRule.onNodeWithTag("profile_statistics_value_" + index, useUnmergedTree = true).assertTextEquals(value)
        }
        composeRule.onNodeWithTag("dashboard_profile_detail_open_profiles").assertIsDisplayed()
        recordWindowPng("profile_statistics_dark")
        composeRule.activityRule.scenario.onActivity { it.showProfileStatistics("ready", dark = false) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_profile_detail_open_profiles").assertIsDisplayed()
        recordWindowPng("profile_statistics_light")
        composeRule.onNodeWithTag("dashboard_profile_detail_open_profiles").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.profilesNavigationCount) }
    }

    @Test
    fun dashboardScreenshotMatrixMatchesBaseline() {
        composeRule.onNodeWithTag("nav_proxy").assertDoesNotExist()
        val actual = linkedMapOf("default" to captureHash("default"))
        composeRule.activityRule.scenario.onActivity { it.showSystemReadyScenario() }
        actual["system_ready"] = captureHash("system_ready")
        assertBaselines(actual)
    }

    @Test
    fun dashboardRunningStateRendersMainCardsWithoutDiagnosticExtras() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_runtime_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_outbound_ip").assertTextEquals("203.0.113.7")
        composeRule.onNodeWithTag("dashboard_runtime_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_stats_card").assertIsDisplayed()
        composeRule.onNodeWithText("3 个连接").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_profile_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_sync").assertIsEnabled()
        composeRule.onNodeWithText("固定诊断样例").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_probe_card").assertDoesNotExist()
        composeRule.onNodeWithTag("always_on_vpn_card").assertDoesNotExist()
        composeRule.onNodeWithTag("access_control_card").assertDoesNotExist()
        composeRule.onNodeWithTag("main_navigation_dock").assertIsDisplayed()
    }

    @Test
    fun dashboardShowsSessionDurationAndSampledHistoryAcrossRecreation() {
        assertPortraitDefaultFontConfiguration()
        composeRule.onNodeWithTag("dashboard_runtime_duration").assertTextEquals("—")
        composeRule.onNodeWithTag("dashboard_upload_history").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_runtime_duration").assertTextEquals("01:01:01")
        composeRule.onNodeWithTag("dashboard_upload_history").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_download_history").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("dashboard_runtime_duration").assertTextEquals("01:01:01")
        composeRule.onNodeWithTag("dashboard_upload_history").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.showSystemReadyScenario() }
        composeRule.onNodeWithTag("dashboard_runtime_duration").assertTextEquals("—")
        composeRule.onNodeWithTag("dashboard_download_history").assertIsDisplayed()
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
    fun dashboardIpDetailsHideCopyRefreshAndStop() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_outbound_ip").performClick()
        composeRule.onNodeWithTag("ip_quality_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("数据中心").assertIsDisplayed()
        composeRule.onNodeWithText("AS64512").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ip_quality_hide").performScrollTo().performClick()
        composeRule.onNodeWithTag("ip_quality_address").performScrollTo().performClick()
        composeRule.runOnIdle {
            val clipboard = composeRule.activity.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("203.0.113.7", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        composeRule.onNodeWithTag("ip_quality_refresh").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("ip_quality_loading").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, composeRule.activity.qualityRefreshCount)
            assertEquals(0, composeRule.activity.ipRefreshCount)
            composeRule.activity.failQualityRefresh()
        }
        composeRule.onNodeWithText("查询超时").performScrollTo().assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.showSystemReadyScenario() }
        composeRule.onNodeWithTag("ip_quality_sheet").assertDoesNotExist()
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("ip_quality_sheet").assertDoesNotExist()
    }

    @Test
    fun dashboardIpRefreshKeepsAddressAndFailureCanRetry() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showInitialIpFailure() }
        composeRule.onNodeWithTag("dashboard_outbound_ip").assertTextEquals("查询失败")
        composeRule.onNodeWithTag("dashboard_ip_refresh").performClick().assertIsNotEnabled()
        composeRule.activityRule.scenario.onActivity { it.finishIpRefresh(true) }
        composeRule.onNodeWithTag("dashboard_outbound_ip").assertTextEquals("203.0.113.7")
        composeRule.onNodeWithTag("dashboard_ip_refresh").performClick()
        composeRule.onNodeWithTag("dashboard_outbound_ip").assertTextEquals("203.0.113.7")
        composeRule.activityRule.scenario.onActivity { it.finishIpRefresh(false) }
        composeRule.onNodeWithText("点击图标重试").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_ip_refresh").performClick()
        composeRule.runOnIdle { assertEquals(3, composeRule.activity.ipRefreshCount) }
        composeRule.activityRule.scenario.onActivity { it.finishIpRefresh(true) }
    }

    @Test
    fun dashboardIpDetailsCaptureDarkAndLight() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_outbound_ip").performClick()
        composeRule.waitForIdle()
        recordNodePng("ip_quality_dark", "ip_quality_sheet")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(150)
        recordWindowPng("ip_quality_screen_dark")
        composeRule.activityRule.scenario.onActivity { it.setDarkTheme(false) }
        composeRule.waitForIdle()
        recordNodePng("ip_quality_light", "ip_quality_sheet")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(150)
        recordWindowPng("ip_quality_screen_light")
    }

    @Test
    fun dashboardRunningProfilePickerSwitchesWithoutStopping() {
        composeRule.activityRule.scenario.onActivity { it.showRunningProfilePickerScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_switch").performClick()
        composeRule.onNodeWithTag("dashboard_profile_picker").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_option_102").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_option_102").performClick()
        composeRule.onNodeWithText("固定自定义配置").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, composeRule.activity.runtimeStopCount) }
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
    fun dashboardProfilePreviewLoadsRetriesAndClosesInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showProfilePickerScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_preview").performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("runtime_config_preview_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("runtime_config_preview_retry").assertIsNotEnabled()
        composeRule.activityRule.scenario.onActivity { it.finishConfigPreview(false) }
        composeRule.onNodeWithTag("runtime_config_preview_error").assertIsDisplayed()
        composeRule.onNodeWithTag("runtime_config_preview_retry").performClick()
        composeRule.activityRule.scenario.onActivity { it.finishConfigPreview(true) }
        composeRule.onNodeWithText("mixed-port: 7890").assertIsDisplayed()
        recordNodePng("runtime_config_preview_dark", "runtime_config_preview_sheet")
        composeRule.activityRule.scenario.onActivity { it.setDarkTheme(false); it.finishConfigPreview(true, applied = true) }
        composeRule.onNodeWithTag("runtime_config_preview_origin").assertTextEquals("当前核心加载的配置，只读。模式和节点以当前选择为准。")
        recordNodePng("runtime_config_preview_light", "runtime_config_preview_sheet")
        composeRule.onNodeWithTag("runtime_config_preview_find").performClick()
        composeRule.onNodeWithTag("runtime_config_preview_query").performTextInput("mixed-port")
        composeRule.onNodeWithTag("runtime_config_preview_next").performClick()
        composeRule.onNodeWithText("1 行匹配").assertExists()
        composeRule.onNodeWithTag("runtime_config_preview_close").performClick()
        composeRule.onNodeWithTag("runtime_config_preview_sheet").assertDoesNotExist()
    }

    @Test
    fun dashboardRunningProfileDetailRemainsReadableAndAllowsSync() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_name").assertTextEquals("固定诊断样例")
        composeRule.onNodeWithTag("dashboard_profile_detail_sync").assertIsEnabled()
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
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performScrollTo().assertHasClickAction()
        composeRule.onNodeWithTag("dashboard_proxy_groups_count", useUnmergedTree = true).assertTextEquals("5")
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
    fun dashboardProxyPickerSystemBackKeepsSelectionAndReopenClearsSearch() = withProxyOptions(ProxyPageOptions()) {
        prepareTouchVisualCapture()
        openProxyPicker()
        composeRule.onNodeWithTag("proxy_node_机场选择_日本节点").performClick()
        composeRule.onNodeWithTag("proxy_group_search").performTextInput("日本")
        composeRule.onNodeWithTag("proxy_node_机场选择_香港节点").assertDoesNotExist()
        // 作者 long：先收起输入法，随后只发一次平台返回，明确验收 Dialog 退出而不是 IME 消费返回。
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        recordWindowPng("dashboard_proxy_picker_before_system_back")
        androidx.test.espresso.Espresso.pressBack()
        composeRule.onNodeWithTag("dashboard_proxy_picker").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, composeRule.activity.lifecycle.currentState)
            assertEquals(1, composeRule.activity.proxySelectionCount)
        }
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performClick()
        // 作者 long：占位文字也合并到 Text 语义中；重开是否清空应只核对用户可编辑的查询内容。
        composeRule.onNodeWithTag("proxy_group_search").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")),
        )
        composeRule.onNodeWithTag("proxy_group_tab_机场选择").assertIsSelected()
        composeRule.onNodeWithTag("proxy_node_机场选择_香港节点").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_机场选择_日本节点_in_use", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.proxySelectionCount) }
        recordWindowPng("dashboard_proxy_picker_after_system_back_reopen")
        composeRule.onNodeWithTag("dashboard_proxy_picker_close").performClick()
    }

    @Test
    fun dashboardProxyPickerRecreationKeepsOpenQueryGroupAndSelection() = withProxyOptions(ProxyPageOptions()) {
        prepareTouchVisualCapture()
        openProxyPicker()
        composeRule.onNodeWithTag("proxy_group_tab_流媒体").performClick()
        composeRule.onNodeWithTag("proxy_node_流媒体_香港节点").performClick()
        composeRule.onNodeWithTag("proxy_group_search").performTextInput("香港")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("proxy_group_tab_流媒体").assertIsSelected()
        composeRule.onNodeWithTag("proxy_node_流媒体_日本节点").assertDoesNotExist()
        recordWindowPng("dashboard_proxy_picker_before_recreate")
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("dashboard_proxy_picker").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_group_search").assertTextEquals("香港")
        composeRule.onNodeWithTag("proxy_group_tab_流媒体").assertIsSelected()
        composeRule.onNodeWithTag("proxy_node_流媒体_香港节点_in_use", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_流媒体_日本节点").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.proxySelectionCount) }
        recordWindowPng("dashboard_proxy_picker_after_recreate")
        // 作者 long：重建保留未退出面板的查询，真正退出后新一轮面板必须清空查询；两种生命周期不能混用。
        composeRule.onNodeWithTag("dashboard_proxy_picker_close").performClick()
        composeRule.onNodeWithTag("dashboard_proxy_picker").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_proxy_groups_card").performClick()
        composeRule.onNodeWithTag("proxy_group_search").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")),
        )
        composeRule.onNodeWithTag("proxy_group_tab_流媒体").assertIsSelected()
        composeRule.onNodeWithTag("proxy_node_流媒体_日本节点").assertIsDisplayed()
        composeRule.onNodeWithTag("proxy_node_流媒体_香港节点_in_use", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.proxySelectionCount) }
        recordWindowPng("dashboard_proxy_picker_after_recreate_reopen")
        composeRule.onNodeWithTag("dashboard_proxy_picker_close").performClick()
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
        val actual = linkedMapOf<String, String>()
        for (dark in listOf(true, false)) {
            composeRule.activityRule.scenario.onActivity { it.setDarkTheme(dark) }
            val theme = if (dark) "dark" else "light"
            composeRule.onNodeWithTag("dashboard_proxy_groups_card").performScrollTo()
            actual["summary_$theme"] = recordNodePng("dashboard_proxy_summary_$theme", "dashboard_proxy_groups_card")
            composeRule.onNodeWithTag("dashboard_proxy_groups_card").performClick()
            actual["picker_$theme"] = recordNodePng("dashboard_proxy_picker_$theme", "connections_detail_sheet")
            composeRule.onNodeWithTag("dashboard_proxy_picker_close").performClick()
        }
        // 作者 long：一次采齐深浅色的卡片和面板，基线变化时仍保留完整画面供同批视觉复核。
        assertBaselines(actual, proxyPickerBaselines)
    }

    @Test
    fun memoryDetailsKeepSampleDuringReleaseAndAllowRetryAcrossRecreation() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showMemoryScenario() }
        composeRule.onNodeWithTag("dashboard_memory_card").performClick()
        composeRule.onNodeWithTag("memory_detail_total").assertTextEquals(formatBytes(128L * 1024 * 1024))
        composeRule.onNodeWithTag("memory_release").assertIsDisplayed().performClick().assertIsNotEnabled()
        composeRule.onNodeWithTag("memory_detail_total").performScrollTo().assertTextEquals(formatBytes(128L * 1024 * 1024))
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.memoryReleaseCount) }
        composeRule.activityRule.scenario.onActivity { it.finishMemoryRelease(false) }
        composeRule.onNodeWithTag("memory_detail_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("memory_release").assertIsDisplayed().performClick()
        composeRule.activityRule.scenario.onActivity { it.finishMemoryRelease(true) }
        composeRule.onNodeWithTag("memory_detail_feedback").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("memory_detail_total").performScrollTo().assertTextEquals(formatBytes(96L * 1024 * 1024))
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("memory_detail_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("memory_detail_close").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("memory_detail_sheet").assertDoesNotExist()
    }

    @Test
    fun memoryCardAndDetailsCaptureDarkAndLight() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showMemoryScenario() }
        for (dark in listOf(true, false)) {
            composeRule.activityRule.scenario.onActivity { it.setDarkTheme(dark) }
            val theme = if (dark) "dark" else "light"
            recordNodePng("memory_card_" + theme, "dashboard_memory_card")
            composeRule.onNodeWithTag("dashboard_memory_card").performClick()
            composeRule.onNodeWithText("堆保留空闲").performScrollTo().assertIsDisplayed()
            recordNodePng("memory_detail_" + theme, "memory_detail_sheet")
            // 作者 long：底部面板位于独立窗口，保留屏幕截图核对完整详情，避免节点截图偏移裁切。
            val screen = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val png = ByteArrayOutputStream().also { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            recordVisualPng("memory_detail_screen_" + theme, png.toByteArray())
            screen.recycle()
            composeRule.onNodeWithTag("memory_detail_close").performClick()
        }
    }

    @Test
    fun vpnCardShowsSavedChoicePendingRestartAndDisabledTransitions() {
        composeRule.activityRule.scenario.onActivity { it.showVpn(true, true) }
        composeRule.onNodeWithTag("vpn_enabled").assertIsOn().performClick()
        composeRule.onNodeWithTag("vpn_enabled").assertIsOff()
        composeRule.onNodeWithTag("vpn_restart_hint").assertExists()
        composeRule.onNodeWithTag("vpn_restart").performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(1, composeRule.activity.vpnRestartCount) }
        composeRule.onNodeWithTag("vpn_enabled").performClick()
        composeRule.onNodeWithTag("vpn_restart_hint").assertDoesNotExist()
        composeRule.activityRule.scenario.onActivity { it.showVpn(false, null, busy = true) }
        composeRule.onNodeWithTag("vpn_enabled").assertIsNotEnabled()
        composeRule.onNodeWithTag("vpn_restart").assertDoesNotExist()
    }

    @Test
    fun vpnCardCapturesBothThemesAndPendingModes() {
        composeRule.activityRule.scenario.onActivity { it.showVpn(false, true) }
        recordNodePng("vpn_dark", "dashboard_vpn_card")
        composeRule.activityRule.scenario.onActivity { it.showVpn(true, false, dark = false) }
        recordNodePng("vpn_light", "dashboard_vpn_card")
    }

    @Test
    fun intranetCardHandlesLoadingOfflineSaveFailureAndRetry() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showIntranetScenario(loading = true) }
        composeRule.onNodeWithTag("intranet_copy").assertIsNotEnabled()
        composeRule.onNodeWithTag("intranet_address").assertTextEquals("读取中…")
        composeRule.activityRule.scenario.onActivity { it.showIntranetScenario(address = null) }
        composeRule.onNodeWithTag("intranet_address").assertTextEquals("无网络")
        composeRule.onNodeWithTag("intranet_copy").assertIsNotEnabled()
        composeRule.activityRule.scenario.onActivity { it.showIntranetScenario() }
        composeRule.onNodeWithTag("intranet_copy").performClick()
        composeRule.onNodeWithTag("intranet_allow_lan").performClick().assertIsNotEnabled()
        composeRule.activityRule.scenario.onActivity { it.finishIntranetSave(false) }
        composeRule.onNodeWithTag("intranet_error").assertIsDisplayed()
        composeRule.onNodeWithTag("intranet_allow_lan").performClick()
        composeRule.activityRule.scenario.onActivity { it.finishIntranetSave(true) }
        composeRule.onNodeWithTag("intranet_port", useUnmergedTree = true).assertTextEquals("端口 7890")
        composeRule.runOnIdle {
            assertEquals(2, composeRule.activity.intranetChangeCount)
            assertEquals(1, composeRule.activity.intranetCopyCount)
        }
        composeRule.activityRule.scenario.onActivity { it.showIntranetScenario(address = null, error = true) }
        composeRule.onNodeWithTag("intranet_retry").performClick()
        composeRule.onNodeWithTag("intranet_address").assertTextEquals("读取中…")
    }

    @Test
    fun intranetCardCapturesBothThemesAndLongIpv6() {
        assertPortraitDefaultFontConfiguration()
        for (dark in listOf(true, false)) {
            composeRule.activityRule.scenario.onActivity { it.setDarkTheme(dark); it.showIntranetScenario() }
            recordNodePng("intranet_card_" + if (dark) "dark" else "light", "dashboard_intranet_card")
            composeRule.activityRule.scenario.onActivity { it.showIntranetScenario("2001:db8:1234:5678:90ab:cdef:1234:5678"); it.finishIntranetSave(true) }
            composeRule.onNodeWithTag("intranet_copy").assertHasClickAction()
            recordNodePng("intranet_ipv6_" + if (dark) "dark" else "light", "dashboard_intranet_card")
        }
    }

    @Test fun serviceOrderDragPreviewsThenSavesOnceAndCancelKeepsOrder() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showServices() }
        composeRule.onNodeWithTag("dashboard_service_card").performClick()
        composeRule.onNodeWithTag("service_manage").performClick()
        val original = composeRule.activity.serviceSettings
        val list = composeRule.onNodeWithTag("service_order_list")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val first = composeRule.onNodeWithTag("service_order_google").fetchSemanticsNode().boundsInRoot
        val next = composeRule.onNodeWithTag("service_order_github").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        try {
            list.performTouchInput { down(Offset(12f, first.center.y - bounds.top)) }
            composeRule.mainClock.advanceTimeBy(800)
            composeRule.onNodeWithTag("service_order_google").assertIsDisplayed()
            list.performTouchInput { moveBy(Offset(0f, next.center.y - first.center.y)) }
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.runOnIdle { assertEquals(0, composeRule.activity.serviceOrderSaveCount); assertEquals(original, composeRule.activity.serviceSettings) }
            composeRule.onNodeWithTag("service_order_github").assertIsDisplayed()
            list.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(100)
            val changed = composeRule.activity.serviceSettings
            assertEquals(com.lonnnnnng.flclash.core.ServiceTarget.GITHUB, changed.order.first())
            assertEquals(original.disabled, changed.disabled)
            assertEquals(1, composeRule.activity.serviceOrderSaveCount)
            val firstNow = composeRule.onNodeWithTag("service_order_github").fetchSemanticsNode().boundsInRoot
            list.performTouchInput { down(Offset(12f, firstNow.center.y - bounds.top)) }
            composeRule.mainClock.advanceTimeBy(800)
            composeRule.onNodeWithTag("service_order_github").assertIsDisplayed()
            list.performTouchInput { moveBy(Offset(0f, next.height * 2)) }
            composeRule.mainClock.advanceTimeBy(64)
            list.performTouchInput { cancel() }
            composeRule.mainClock.advanceTimeBy(100)
            assertEquals(changed, composeRule.activity.serviceSettings)
            assertEquals(1, composeRule.activity.serviceOrderSaveCount)
            list.performScrollToIndex(0)
            composeRule.mainClock.advanceTimeBy(100)
            composeRule.onNodeWithTag("service_order_github").assertIsDisplayed()
        } finally { composeRule.mainClock.autoAdvance = true }
        composeRule.onNodeWithTag("service_manage_back").performClick()
        composeRule.onNodeWithTag("service_card_github", useUnmergedTree = true).assertExists()
    }

    @Test fun serviceOrderEdgeScrollContinuesWithStationaryFingerAndStopsOnRelease() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showServices() }
        composeRule.onNodeWithTag("dashboard_service_card").performClick()
        composeRule.onNodeWithTag("service_manage").performClick()
        val list = composeRule.onNodeWithTag("service_order_list")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag("service_order_google").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        try {
            list.performTouchInput { down(Offset(12f, row.center.y - bounds.top)) }
            composeRule.mainClock.advanceTimeBy(800)
            composeRule.onNodeWithTag("service_order_google").assertIsDisplayed()
            list.performTouchInput { moveTo(Offset(12f, bounds.height - 8f)) }
            composeRule.mainClock.advanceTimeBy(200)
            val first = serviceOrderScroll()
            composeRule.mainClock.advanceTimeBy(200)
            val second = serviceOrderScroll()
            assertTrue("手指停在下边缘后继续滚动 first=" + first + " second=" + second, second > first + 1f)
            assertEquals(0, composeRule.activity.serviceOrderSaveCount)
            list.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(100)
            val released = serviceOrderScroll()
            composeRule.mainClock.advanceTimeBy(500)
            assertEquals(released, serviceOrderScroll(), 0.1f)
            assertEquals(1, composeRule.activity.serviceOrderSaveCount)
            assertTrue(composeRule.activity.serviceSettings.order.indexOf(com.lonnnnnng.flclash.core.ServiceTarget.GOOGLE) > 1)
        } finally { composeRule.mainClock.autoAdvance = true }
    }

    @Test fun serviceOrderBackgroundCancelsPreviewAndDoesNotResumeScrolling() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showServices() }
        composeRule.onNodeWithTag("dashboard_service_card").performClick()
        composeRule.onNodeWithTag("service_manage").performClick()
        val original = composeRule.activity.serviceSettings
        val list = composeRule.onNodeWithTag("service_order_list")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag("service_order_google").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        try {
            list.performTouchInput { down(Offset(12f, row.center.y - bounds.top)) }
            composeRule.mainClock.advanceTimeBy(800)
            composeRule.onNodeWithTag("service_order_google").assertIsDisplayed()
            list.performTouchInput { moveTo(Offset(12f, bounds.height - 8f)) }
            composeRule.mainClock.advanceTimeBy(200)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.mainClock.advanceTimeBy(100)
            val stopped = serviceOrderScroll()
            composeRule.mainClock.advanceTimeBy(500)
            assertEquals(stopped, serviceOrderScroll(), 0.1f)
            list.performTouchInput { cancel() }
            assertEquals(original, composeRule.activity.serviceSettings)
            assertEquals(0, composeRule.activity.serviceOrderSaveCount)
        } finally { composeRule.mainClock.autoAdvance = true }
    }

    private fun serviceOrderScroll(): Float = composeRule.onNodeWithTag("service_order_list").fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun serviceStatusDetailsSupportSingleBatchChecksAndTargetManagement() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showServices() }
        composeRule.onNodeWithTag("dashboard_service_card").performClick()
        composeRule.onNodeWithTag("service_result_google").assertTextEquals("28 ms")
        composeRule.onNodeWithTag("service_check_google").performClick().assertIsNotEnabled()
        composeRule.onNodeWithTag("service_result_google").assertTextEquals("检测中…")
        composeRule.runOnIdle { assertEquals(1, composeRule.activity.serviceCheckCount) }
        composeRule.activityRule.scenario.onActivity { it.showServices() }
        composeRule.onNodeWithTag("service_check_all").performClick().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(2, composeRule.activity.serviceCheckCount) }
        composeRule.activityRule.scenario.onActivity { it.showServices() }
        composeRule.onNodeWithTag("service_manage").performClick()
        composeRule.onNodeWithTag("service_toggle_google").performClick().assertIsOff()
        composeRule.onNodeWithTag("service_up_github").performClick()
        composeRule.onNodeWithTag("service_manage_back").performClick()
        composeRule.onNodeWithTag("service_result_google").assertDoesNotExist()
        composeRule.onNodeWithTag("service_detail_close").performClick()
        composeRule.onNodeWithTag("service_card_github", useUnmergedTree = true).assertExists()
    }

    @Test fun serviceStatusCardAndDetailsCaptureBothThemes() {
        assertPortraitDefaultFontConfiguration()
        for (dark in listOf(true, false)) {
            composeRule.activityRule.scenario.onActivity { it.showServices(dark) }
            val suffix = if (dark) "dark" else "light"
            recordNodePng("services_card_$suffix", "dashboard_service_card")
            composeRule.onNodeWithTag("dashboard_service_card").performClick()
            recordNodePng("services_details_$suffix", "service_detail_sheet")
            composeRule.onNodeWithTag("service_manage").performClick()
            composeRule.onNodeWithTag("service_order_list").assertIsDisplayed().captureToImage()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(150)
            recordWindowPng("services_manage_$suffix")
            composeRule.onNodeWithTag("service_detail_close").performClick()
        }
    }

    @Test fun serviceExitHandlesLoadingFailureQualityRetryHideAndReturn() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showServiceExit("loading") }
        composeRule.onNodeWithTag("dashboard_service_card").performClick()
        composeRule.onNodeWithTag("service_exit_google").assertTextEquals("US · 正在查询出口…")
        composeRule.activityRule.scenario.onActivity { it.showServiceExit("failed") }
        composeRule.onNodeWithTag("service_exit_google").assertTextEquals("US · DIRECT")
        composeRule.onNodeWithTag("service_exit_retry_google").performClick()
        composeRule.onNodeWithTag("service_exit_google").assertTextEquals("US · 正在查询出口…")
        composeRule.activityRule.scenario.onActivity { it.showServiceExit() }
        composeRule.onNodeWithTag("service_exit_google").performClick()
        composeRule.onNodeWithTag("ip_quality_loading").assertExists()
        composeRule.activityRule.scenario.onActivity { it.finishServiceQuality(false) }
        composeRule.onNodeWithText("请求受限").assertExists()
        composeRule.onNodeWithTag("service_quality_refresh").performClick().assertIsNotEnabled()
        composeRule.activityRule.scenario.onActivity { it.finishServiceQuality(true) }
        composeRule.onNodeWithText("数据中心").assertExists()
        composeRule.onNodeWithTag("ip_quality_hide").performScrollTo().assertIsDisplayed().performClick().assertIsOn()
        composeRule.onNodeWithTag("ip_quality_address").performScrollTo().assertTextContains("*****")
        composeRule.onNodeWithTag("service_manage_back").performClick()
        composeRule.onNodeWithTag("service_exit_google").assertTextEquals("US · *****")
        composeRule.onNodeWithTag("service_detail_close").performClick()
    }

    @Test fun serviceExitAndQualityCaptureThemesAndLongIpv6() {
        assertPortraitDefaultFontConfiguration()
        for (dark in listOf(true, false)) {
            val suffix = if (dark) "dark" else "light"
            composeRule.activityRule.scenario.onActivity { it.showServiceExit(ip = "2001:db8:1234:5678:90ab:cdef:1234:5678", dark = dark) }
            composeRule.onNodeWithTag("dashboard_service_card").performClick()
            recordNodePng("service_exit_ipv6_$suffix", "service_detail_sheet")
            composeRule.onNodeWithTag("service_exit_google").performClick()
            composeRule.activityRule.scenario.onActivity { it.finishServiceQuality(true) }
            composeRule.onNodeWithTag("ip_quality_hide").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("service_manage_back").assertIsDisplayed()
            recordWindowPng("service_exit_quality_$suffix")
            composeRule.onNodeWithTag("service_detail_close").performClick()
        }
    }

    private fun recordWindowPng(name: String) {
        composeRule.waitForIdle()
        // 作者 long：系统Window的主题过渡不受Compose测试时钟控制，等绘制稳定后再采图，避免将半透明中间帧当成深色页面。
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val png = ByteArrayOutputStream().also { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        recordVisualPng(name, png)
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
        prepareTouchVisualCapture()
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag("dashboard_page").captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        recordVisualPng("dashboard_$name", png)
        return MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
    }

    private fun assertBaselines(actual: Map<String, String>, expected: Map<String, String> = baselines) {
        val mismatches = actual.mapNotNull { (name, hash) ->
            val expectedHash = expected[name]
            if (expectedHash == hash) null else "$name expected=$expectedHash actual=$hash"
        }
        check(mismatches.isEmpty()) { "dashboard screenshot baseline mismatch: ${mismatches.joinToString("; ")}" }
    }
}

private val baselines = mapOf(
    "default" to "9e15f92b825a7380cd7279e4b80a14825a845fc13351a3b12f1071db8d3d2280",
    "system_ready" to "d44421dd7257b6520abc6d5c377180718040d38ece1bb20990c899783d89cd3f",
    "running" to "f2b020de1527a30dbc896f66f9bf70db2169d3e909e9010d970881f2f5b2d20e",
    "light_default" to "049197a800eb107905ca031932e7d000ff447a08f7b3a5c5e7844cceee91e5df",
    "light_running" to "d0ab3b47109a0cecf6f4f55d4607734bfdba3002ebd75d1d79f6e1f952c14434",
)

private val proxyPickerBaselines = mapOf(
    "summary_dark" to "d63207b70f8623523c463d364c729647322bfba90734d69ac5d42134d45689dc",
    "summary_light" to "ad089617dba26698ce5433619e32bc1dd827124224382cc143dacfad3b1752f9",
    "picker_dark" to "6a9a8d9214240887e02fffc9032d6956e2b9daa81b8a7d3961e2a9a8339eb927",
    "picker_light" to "62ace29b467263dca1ad4b9a777774516f4961ca2f2ec63d2820b8c97f394754",
)
