package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

/** 作者 long：只验证主页面渲染和操作契约；不读正式配置、不启动核心，页面偏好在宿主外保存/恢复。 */
class MainPageStatesInstrumentedTest {
    @get:Rule(order = 0) val preserveOptions = object : ExternalResource() {
        private lateinit var original: ProxyPageOptions
        override fun before() { original = ProxyPageOptionsStore.load(InstrumentationRegistry.getInstrumentation().targetContext) }
        override fun after() { ProxyPageOptionsStore.save(InstrumentationRegistry.getInstrumentation().targetContext, original) }
    }
    @get:Rule(order = 1) val compose = createComposeRule()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun profilesEmptyAddUsesSameBusyAndTransitionGateAsToolbar() {
        var busy by mutableStateOf(true)
        var runtime by mutableStateOf(CoreRuntimeStatus.STOPPED)
        var dark by mutableStateOf(false)
        var added = 0
        compose.setContent { KClashTheme(darkTheme = dark) { androidx.compose.material3.Surface(color = dashboardPageColor()) {
            ProfilesPanel(state = ProfilesUiState(isBusy = busy), editorState = ProfileEditorUiState(), runtimeState = runtime,
                showAddDialog = false, onShowAddDialog = { added++ }, onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {},
                onAddRemote = { _, _ -> }, onAddCustom = {}, onSelect = {}, onEdit = {}, onUpdate = {}, onCloseEditor = {}, onSaveEditor = {}, onDelete = {})
        } } }
        compose.onNodeWithTag("profiles_empty").assertIsDisplayed()
        compose.onNodeWithTag("profiles_empty_add").assertIsNotEnabled()
        compose.runOnIdle { busy = false; runtime = CoreRuntimeStatus.STARTING }
        compose.onNodeWithTag("profiles_empty_add").assertIsNotEnabled()
        compose.runOnIdle { runtime = CoreRuntimeStatus.STOPPING }
        compose.onNodeWithTag("profiles_empty_add").assertIsNotEnabled()
        compose.runOnIdle { runtime = CoreRuntimeStatus.RUNNING }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithText("暂无配置").assertIsDisplayed()
            capture("profiles_empty_$theme")
        }
        compose.onNodeWithTag("profiles_empty_add").assertIsEnabled().performClick()
        assertEquals(1, added)
    }

    @Test fun proxyLoadingErrorRetryAndEmptyRemainReadableInBothThemes() {
        var state by mutableStateOf(ProxyGroupsUiState(isLoading = true))
        var dark by mutableStateOf(false)
        var runtime by mutableStateOf(CoreRuntimeStatus.STOPPED)
        var retries = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProxyGroupsCard(state = state, runtimeState = runtime,
                onRefresh = { retries++ }, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } }
        compose.onNodeWithText("正在加载代理组").assertIsDisplayed()
        compose.onNodeWithTag("proxy_groups_empty_action").assertDoesNotExist()
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; state = ProxyGroupsUiState(error = "读取失败，请重试") }
            compose.onNodeWithText("代理组加载失败").assertIsDisplayed()
            compose.onNodeWithText("代理组加载失败").assertIsDisplayed()
            compose.onNodeWithText("代理组加载失败").assertIsDisplayed()
            for (transition in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)) {
                compose.runOnIdle { runtime = transition }
                compose.onNodeWithTag("proxy_groups_retry").assertIsNotEnabled()
            }
            compose.runOnIdle { runtime = CoreRuntimeStatus.STOPPED }
            compose.onNodeWithTag("proxy_groups_retry").assertIsEnabled().performClick()
            capture("proxy_empty_failed_$theme")
            compose.runOnIdle { state = ProxyGroupsUiState() }
            compose.onNodeWithText("没有代理组").assertIsDisplayed()
            compose.onNodeWithTag("proxy_groups_empty_action").assertIsEnabled()
            capture("proxy_empty_plain_$theme")
        }
        assertEquals(2, retries)
    }

    @Test fun tabAndListSearchEmptyCanClearWithoutChangingSelectedNode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ProxyPageOptionsStore.save(context, ProxyPageOptions(displayMode = ProxyDisplayMode.TAB))
        val group = ProxyGroupInfo("测试组", "Selector", "alpha", listOf("alpha"), false)
        val state = ProxyGroupsUiState(groups = listOf(group), selectedMap = mapOf(group.name to "alpha"), unfoldSet = setOf(group.name))
        var epoch by mutableIntStateOf(0)
        var hostView: android.view.View? = null
        var selected = 0
        compose.setContent { KClashTheme { key(epoch) {
            hostView = LocalView.current
            ProxyGroupsCard(state = state, runtimeState = CoreRuntimeStatus.STOPPED, currentProfileId = 1,
                onRefresh = {}, onSelect = { _, _ -> selected++ }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
        } } }
        for (mode in listOf(ProxyDisplayMode.TAB, ProxyDisplayMode.LIST)) {
            if (mode == ProxyDisplayMode.LIST) compose.runOnIdle {
                ProxyPageOptionsStore.save(context, ProxyPageOptions(displayMode = mode)); epoch++
            }
            compose.onNodeWithTag("proxy_group_search_toggle").performClick()
            compose.onNodeWithTag("proxy_group_search").performTextInput("没有这个节点")
            // 作者 long：语义上的 displayed 不识别系统键盘遮挡，必须等真实 IME 并核对操作的屏幕边界。
            compose.waitUntil(5_000) { ViewCompat.getRootWindowInsets(checkNotNull(hostView))?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            compose.waitForIdle()
            compose.onNodeWithText("没有匹配的节点").assertIsDisplayed()
            val action = compose.onNodeWithTag("proxy_group_empty_${group.name}_action")
            action.assertIsDisplayed()
            val imeTop = checkNotNull(hostView).height - checkNotNull(ViewCompat.getRootWindowInsets(checkNotNull(hostView)))
                .getInsets(WindowInsetsCompat.Type.ime()).bottom
            for (node in listOf(compose.onNodeWithText("没有匹配的节点"), action)) {
                val bounds = node.fetchSemanticsNode().boundsInWindow
                assertTrue("${mode.name} 空态/操作必须完整位于键盘上方：$bounds, imeTop=$imeTop", bounds.bottom <= imeTop)
            }
            capture("proxy_search_empty_${mode.name}")
            action.performClick()
            compose.onNodeWithText("没有匹配的节点").assertDoesNotExist()
            val prefix = if (mode == ProxyDisplayMode.TAB) "proxy_node" else "proxy_list_node"
            compose.onNodeWithTag("${prefix}_${group.name}_alpha").assertIsDisplayed()
            capture("proxy_search_cleared_${mode.name}")
        }
        assertEquals(0, selected); assertEquals(mapOf(group.name to "alpha"), state.selectedMap)
    }

    @Test fun dashboardEmptyStatusAndRetryRespectRuntimeGate() {
        var state by mutableStateOf(ProxyGroupsUiState(isLoading = true))
        var runtime by mutableStateOf(CoreRuntimeStatus.STOPPED)
        compose.setContent { KClashTheme { Column(Modifier.fillMaxSize()) {
            DashboardProxyGroupsCard(state, runtime, CoreOutboundMode.RULE, null, null,
                {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } } }
        compose.onNodeWithText("策略组").assertIsDisplayed()
        compose.onNodeWithText("正在加载代理组").assertIsDisplayed()
        compose.runOnIdle { state = ProxyGroupsUiState(error = "读取失败") }
        compose.onNodeWithTag("dashboard_proxy_groups_retry").assertIsEnabled()
        compose.onNodeWithText("代理组加载失败").assertIsDisplayed()
        for (transition in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)) {
            compose.runOnIdle { runtime = transition }
            compose.onNodeWithTag("dashboard_proxy_groups_retry").assertIsNotEnabled()
        }
        compose.runOnIdle { runtime = CoreRuntimeStatus.RUNNING; state = state.copy(switchingGroup = "group") }
        compose.onNodeWithTag("dashboard_proxy_groups_retry").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(switchingGroup = null, batchTestingGroup = "group") }
        compose.onNodeWithTag("dashboard_proxy_groups_retry").assertIsNotEnabled()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
