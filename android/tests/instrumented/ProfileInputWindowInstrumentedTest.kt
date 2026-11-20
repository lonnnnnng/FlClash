package com.lonnnnnng.flclash.ui

import android.os.IBinder
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：输入前先检查自然焦点与真实Dialog的IME，不能让performTextInput主动建输入会话掩盖交接失败。 */
class ProfileInputWindowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var showing by mutableStateOf(false)
    private var feedback by mutableStateOf(ProfileAddFeedback())
    private var submissions = 0
    private fun render(initial: String? = null) {
        assertPortraitDefaultFontConfiguration()
        showing = initial != null
        compose.setContent { KClashTheme {
            val source = rememberAppMenuSource()
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                AppMenuSourceContent(source) {
                    Button({ showing = true }, Modifier.appMenuSource(source, MaterialTheme.colorScheme.primary).testTag("input_open")) { Text("添加配置") }
                }
                if (showing) AddProfileDialog(false, { showing = false }, { showing = false }, { showing = false },
                    { _, _ -> submissions++ }, onAddCustom = {}, menuSource = source, initialRemoteUrl = initial, feedback = feedback)
            }
        } }
    }
    private fun open() {
        compose.onNodeWithTag("input_open").performTouchInput { click() }
        compose.onNodeWithTag("profile_add_source_remote").performTouchInput { click() }
    }
    private fun ime(): Pair<IBinder, Boolean>? {
        var value: Pair<IBinder, Boolean>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(ViewCompat::getRootWindowInsets)
            if (view != null && insets != null) value = view.windowToken to insets.isVisible(WindowInsetsCompat.Type.ime())
        }
        return value
    }
    private fun focusedIme(tag: String): IBinder {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.getOrElse(SemanticsProperties.Focused) { false } == true && ime()?.second == true
        }
        compose.onNodeWithTag(tag).assertIsFocused()
        return checkNotNull(ime()).first
    }
    private fun hiddenIme(token: IBinder) {
        compose.waitUntil(5_000) { ime()?.let { it.first == token && !it.second } == true }
    }
    @Test fun sourceSnapshotKeepsMenuContentIdentityUntilWindowCloses() {
        var open by mutableStateOf(false)
        var entered = 0
        var disposed = 0
        lateinit var source: AppMenuSource
        compose.setContent { KClashTheme {
            source = rememberAppMenuSource()
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                AppMenuSourceContent(source) {
                    Button({ open = true }, Modifier.appMenuSource(source).testTag("identity_source")) { Text("菜单") }
                }
                AppDropdownMenu(open, { open = false }, source = source) {
                    // 作者 long：统计实际挂载/释放而非组合调用次数，避免把允许的状态重组误判为正文被重建。
                    DisposableEffect(Unit) { entered++; onDispose { disposed++ } }
                    AppDropdownMenuItem({ Text("选择") }, { open = false }, Modifier.testTag("identity_action"))
                }
            }
        } }
        compose.onNodeWithTag("identity_source").performClick()
        compose.onNodeWithTag("identity_action").assertIsDisplayed()
        compose.runOnIdle { assertTrue(source.hidden); assertEquals(1, entered); assertEquals(0, disposed) }
        compose.onNodeWithTag("identity_action").performClick()
        compose.runOnIdle { assertEquals(1, disposed) }
    }

    @Test fun reopeningAfterExternalRevocationDoesNotReplayPreviousSelection() {
        var open by mutableStateOf(false)
        var actions = 0
        var dismissals = 0
        compose.setContent { KClashTheme {
            val source = rememberAppMenuSource()
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                AppMenuSourceContent(source) {
                    Button({ open = true }, Modifier.appMenuSource(source).testTag("reopen_source")) { Text("菜单") }
                }
                AppDropdownMenu(open, { dismissals++; open = false }, source = source) {
                    AppDropdownMenuItem({ Text("选择") }, { actions++; open = false }, Modifier.testTag("reopen_action"))
                }
            }
        } }
        compose.onNodeWithTag("reopen_source").performClick()
        compose.onNodeWithTag("reopen_action").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("reopen_action").performClick()
        compose.mainClock.advanceTimeBy(80)
        // 作者 long：外部撤销和新一轮展开之间必须提交一帧；最终同为true不能让旧选择获得新窗口的授权。
        compose.runOnIdle { open = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { open = true }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, actions); assertEquals(0, dismissals); assertTrue(open) }
        compose.onNodeWithTag("reopen_action").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertFalse(open) }
    }

    @Test fun menuActionRunsAfterPopupViewHasDetached() {
        var open by mutableStateOf(false)
        var actions = 0
        var detachedAtAction = false
        var popupView: android.view.View? = null
        compose.setContent { KClashTheme {
            val source = rememberAppMenuSource()
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                AppMenuSourceContent(source) {
                    Button({ open = true }, Modifier.appMenuSource(source).testTag("handoff_open")) { Text("菜单") }
                }
                AppDropdownMenu(open, { open = false }, source = source) {
                    val view = androidx.compose.ui.platform.LocalView.current
                    SideEffect { popupView = view }
                    AppDropdownMenuItem({ Text("进入表单") }, {
                        detachedAtAction = popupView?.isAttachedToWindow == false
                        actions++; open = false
                    }, Modifier.testTag("handoff_action"))
                }
            }
        } }
        compose.onNodeWithTag("handoff_open").performTouchInput { click() }
        compose.onNodeWithTag("handoff_action").performClick()
        compose.waitUntil(5_000) { actions == 1 }
        compose.runOnIdle { assertTrue("旧窗口必须先移除再交接表单", detachedAtAction) }
    }

    @Test fun sourceToSingleBatchAndBackKeepsNaturalInputAndWindowOwnership() {
        render(); open()
        val token = focusedIme("profile_add_remote_url")
        compose.onNodeWithTag("profile_add_remote_url").performTextInput("https://example.invalid/sub")
        compose.onNodeWithTag("profile_add_mode_batch").performClick()
        assertEquals(token, focusedIme("profile_add_remote_batch"))
        compose.onNodeWithTag("profile_add_mode_single").performClick()
        assertEquals(token, focusedIme("profile_add_remote_url"))
        compose.onNodeWithTag("profile_add_back").performClick()
        compose.onNodeWithTag("profile_add_source_remote").assertIsDisplayed()
        compose.waitUntil(5_000) { ime()?.let { it.first != token && !it.second } == true }
        compose.onNodeWithTag("profile_add_source_remote").performTouchInput { click() }
        val reopened = focusedIme("profile_add_remote_url")
        compose.onNodeWithTag("profile_add_remote_url").assertTextContains("https://example.invalid/sub")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        hiddenIme(reopened)
        compose.onNodeWithTag("profile_add_remote_url").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !showing }
        compose.onNodeWithTag("profile_add_remote_url").assertDoesNotExist()
    }
    @Test fun backgroundResumeAndReturnRestoreSourceWithoutKeyboard() {
        render()
        compose.onNodeWithTag("input_open").performTouchInput { click() }
        compose.onNodeWithTag("profile_add_source_remote").performTouchInput { click() }
        focusedIme("profile_add_remote_url")
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        // 作者 long：经过真实前后台切换再返回来源页，旧输入会话必须收起，重开仍能自然输入。
        compose.waitForIdle()
        compose.onNodeWithTag("profile_add_back").performClick()
        compose.onNodeWithTag("profile_add_source_remote").assertIsDisplayed()
        compose.waitUntil(5_000) { ime()?.second == false }
        compose.onNodeWithTag("profile_add_source_remote").performTouchInput { click() }
        focusedIme("profile_add_remote_url")
    }

    @Test fun scannedDirectFormGetsNaturalImeAndSubmissionHidesItsOwnWindow() {
        render("https://example.invalid/sub")
        val token = focusedIme("profile_add_remote_url")
        compose.onNodeWithTag("profile_add_submit").performTouchInput { click(); advanceEventTime(40); click() }
        hiddenIme(token)
        compose.runOnIdle { assertEquals(1, submissions) }
        compose.onNodeWithTag("profile_add_submit").assertIsNotEnabled()
    }
    @Test fun failureHidesDialogImeAndAllowsRetryWithoutLosingDraft() {
        render("https://example.invalid/sub")
        val token = focusedIme("profile_add_remote_url")
        compose.runOnIdle { feedback = ProfileAddFeedback(phase = ProfileAddPhase.FAILED, message = "导入失败，请重试") }
        hiddenIme(token)
        compose.onNodeWithTag("profile_add_remote_url").assertTextContains("https://example.invalid/sub")
        compose.onNodeWithTag("profile_add_submit").performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
    }
}
