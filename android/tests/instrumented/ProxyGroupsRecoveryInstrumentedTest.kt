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
