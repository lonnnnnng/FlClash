package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals

/** 作者 long：失败不能伪装为空配置，首页和完整代理页都应原位提供可达的恢复入口。 */
class ProxyGroupsRecoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val group = ProxyGroupInfo("Shared", "Selector", "a", listOf("a", "b"), false)
    private lateinit var originalOptions: ProxyPageOptions
    private val context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun preserveOptions() {
        originalOptions = ProxyPageOptionsStore.load(context)
        ProxyPageOptionsStore.save(context, ProxyPageOptions())
    }
    @After fun restoreOptions() { ProxyPageOptionsStore.save(context, originalOptions) }

    @Test fun proxyReadFailureCanRetryWithoutShowingMissingConfiguration() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(ProxyGroupsUiState(profileId = 7, error = "无法读取代理组，请重试"))
        compose.setContent { KClashTheme {
            ProxyGroupsCard(state = state, runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 7,
                onRefresh = { state = state.copy(groups = listOf(group), error = null) },
                onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } }
        compose.onNodeWithTag("proxy_groups_error").assertIsDisplayed()
        capture("proxy_read_failure")
        compose.onNodeWithText("添加有效配置后显示代理组。").assertDoesNotExist()
        compose.onNodeWithTag("proxy_groups_retry").performClick()
        compose.onNodeWithTag("proxy_node_Shared_a").assertIsDisplayed()
        compose.onNodeWithTag("proxy_groups_error").assertDoesNotExist()
    }

    @Test fun errorRecoveryTransitionDisablesTheRetiringRetryAction() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(ProxyGroupsUiState(profileId = 7, error = "无法读取代理组，请重试"))
        var retries = 0
        var selections = 0
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { KClashTheme {
                ProxyGroupsCard(state = state, runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 7,
                    onRefresh = { retries++; state = state.copy(groups = listOf(group), error = null) },
                    onSelect = { _, _ -> selections++ }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
            } }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("proxy_groups_retry").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)

            // 作者 long：错误按钮在淡出帧中仍可见，但已失效；只有新代理节点能接收后续操作。
            compose.onNodeWithTag("proxy_groups_retry").assertIsNotEnabled()
            compose.onNodeWithTag("proxy_node_Shared_a").assertIsDisplayed().performClick()
            assertEquals(1, retries)
            assertEquals(1, selections)

            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("proxy_groups_empty").assertDoesNotExist()
            compose.onNodeWithTag("proxy_node_Shared_a").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun emptyRecoveryTransitionDisablesTheRetiringRefreshAction() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(ProxyGroupsUiState(profileId = 7))
        var refreshes = 0
        var selections = 0
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { KClashTheme {
                ProxyGroupsCard(state = state, runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 7,
                    onRefresh = { refreshes++; state = state.copy(groups = listOf(group)) },
                    onSelect = { _, _ -> selections++ }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
            } }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("proxy_groups_empty_action").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)

            // 作者 long：空态的刷新入口在离场动画期间禁用，防止一次过渡重复发起恢复请求。
            compose.onNodeWithTag("proxy_groups_empty_action").assertIsNotEnabled()
            compose.onNodeWithTag("proxy_node_Shared_a").assertIsDisplayed().performClick()
            assertEquals(1, refreshes)
            assertEquals(1, selections)

            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("proxy_groups_empty").assertDoesNotExist()
            compose.onNodeWithTag("proxy_node_Shared_a").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun contentFailureTransitionKeepsTheLastPageVisibleButInactive() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(ProxyGroupsUiState(profileId = 7, groups = listOf(group)))
        var retries = 0
        var selections = 0
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { KClashTheme {
                ProxyGroupsCard(state = state, runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 7,
                    onRefresh = { retries++; state = state.copy(groups = listOf(group), error = null) },
                    onSelect = { _, _ -> selections++ }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
            } }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnUiThread { state = state.copy(groups = emptyList(), error = "无法读取代理组，请重试") }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)

            // 作者 long：页面内容作为退场快照继续淡出，但不能在错误态覆盖期间切换节点。
            compose.onNodeWithTag("proxy_node_Shared_a").assertIsNotEnabled()
            compose.onNodeWithTag("proxy_groups_retry").assertIsEnabled().performClick()
            assertEquals(1, retries)
            assertEquals(0, selections)

            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("proxy_groups_empty").assertDoesNotExist()
            compose.onNodeWithTag("proxy_node_Shared_a").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun dashboardReadFailureHasIndependentRetryAndRecoversSummary() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(ProxyGroupsUiState(profileId = 7, error = "读取代理组超时，请重试"))
        compose.setContent { KClashTheme {
            DashboardProxyGroupsCard(state, CoreRuntimeStatus.STOPPED, CoreOutboundMode.RULE, 7, "Shared",
                onRefresh = { state = state.copy(groups = listOf(group), error = null) },
                onSelectGroup = {}, onToggleGroup = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } }
        compose.onNodeWithTag("dashboard_proxy_groups_error").assertIsDisplayed()
        capture("home_proxy_read_failure")
        compose.onNodeWithTag("dashboard_proxy_groups_retry").performClick()
        compose.onNodeWithTag("dashboard_proxy_groups_error").assertDoesNotExist()
        compose.onNodeWithText("代理组加载失败").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
