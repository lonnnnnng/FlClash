package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：菜单验证采用正式卡片和真实Popup Window，不能用直接调用回调代替长按、返回或后台退出。 */
class MainMenuInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun restoreTouchInput() = prepareTouchVisualCapture()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun profileContextAvoidsRowAndBackClosesOnlySubmenuInBothThemes() {
        var dark by mutableStateOf(false); var selected = 0; var copied = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                ProfileCard(Profile.remote(9, "https://example.test/sub", "配置菜单"), true, true,
                    onSelect = { selected++ }, onEdit = {}, onUpdate = {}, onDelete = {}, onCopyLink = { copied++ })
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithTag("profile_card_9").performTouchInput { longClick() }
            compose.onNodeWithText("编辑").assertIsDisplayed()
            val card = compose.onNodeWithTag("profile_card_9").fetchSemanticsNode().boundsInWindow
            val menu = compose.onNodeWithTag("app_popup_menu").fetchSemanticsNode().boundsInWindow
            assertTrue(menu.bottom <= card.top || menu.top >= card.bottom)
            compose.runOnIdle { assertEquals(0, selected) }
            capture("profile_root_$theme")
            compose.onNodeWithTag("profile_more_9").performClick()
            compose.onNodeWithTag("profile_copy_link_9").assertIsDisplayed()
            capture("profile_more_$theme")
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("编辑").assertIsDisplayed()
            compose.onNodeWithTag("profile_more_9").performClick()
            compose.onNodeWithTag("profile_copy_link_9").performClick()
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals(2, copied) }
    }

    @Test fun keyboardContextMenuAndOutsideDismissDoNotSelectCard() {
        var selected = 0
        lateinit var inputMode: InputModeManager
        compose.setContent { KClashTheme {
            inputMode = LocalInputModeManager.current
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                ProfileCard(Profile.custom(9, "键盘菜单"), true, true, { selected++ }, {}, onUpdate = {}, onDelete = {})
            }
        } }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("profile_card_9").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("profile_card_9").assertIsFocused()
        compose.onNodeWithTag("profile_card_9").performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.F10); keyUp(Key.ShiftLeft) }
        compose.onNodeWithText("重命名").assertIsDisplayed()
        capture("keyboard_context")
        compose.onNodeWithTag("app_popup_window").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f, centerY)) }
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, selected) }
        prepareTouchVisualCapture()
    }

    @Test fun nodeMenuWithdrawsOnRefreshAndOnBackgroundWithoutExecutingAction() {
        var enabled by mutableStateOf(true); var selected = 0
        compose.setContent { KClashTheme {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                ProxyNodeCard("节点", "Vless", inUse = false, preferred = false, enabled = enabled,
                    menuEnabled = enabled, delay = null, selectionTag = "menu_node", delayTag = "delay",
                    onSelect = { selected++ }, onTestDelay = {})
            }
        } }
        compose.onNodeWithTag("menu_node").performTouchInput { longClick() }
        compose.onNodeWithText("选择").assertIsDisplayed()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("menu_node").performTouchInput { longClick() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, selected) }
    }

    @Test fun longMenuScrollsInsideSafeViewportAndDisabledItemCannotSubmit() {
        var opened by mutableStateOf(false); var anchor by mutableStateOf(IntRect.Zero); var submissions = 0
        compose.setContent { KClashTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Button({ opened = true }, Modifier.align(Alignment.BottomEnd).testTag("open_long")
                    .onGloballyPositioned { anchor = it.boundsInWindow().roundToIntRect() }) { Text("菜单") }
                AppDropdownMenu(opened, { opened = false }, anchor = anchor) {
                    AppDropdownMenuItem({ Text("不可用") }, { submissions += 100 }, enabled = false, modifier = Modifier.testTag("disabled_menu"))
                    repeat(25) { i -> AppDropdownMenuItem({ Text("选项 $i") }, { submissions++; opened = false }, Modifier.testTag("long_item_$i")) }
                }
            }
        } }
        compose.onNodeWithTag("open_long").performClick()
        compose.onNodeWithTag("disabled_menu").assertIsNotEnabled().performTouchInput { click() }
        capture("long_menu")
        compose.onNodeWithTag("long_item_24").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
    }

    @Test fun proxyToolsSubmenuBackOutsideAndProfileChangeStayIndependent() {
        val options = ProxyPageOptionsStore.load(compose.activity)
        var profileId by mutableStateOf(9L)
        val group = ProxyGroupInfo("Main", "Selector", "node", listOf("node"), false)
        try {
            compose.setContent { KClashTheme {
                ProxyGroupsCard(state = ProxyGroupsUiState(profileId = profileId, groups = listOf(group)), runtimeState = CoreRuntimeStatus.STOPPED,
                    currentProfileId = profileId, onRefresh = {}, onSelect = { _, _ -> }, onTestDelay = { _, _ -> }, onTestGroupDelay = {})
            } }
            compose.onNodeWithTag("proxy_tools").performClick()
            compose.onNodeWithTag("proxy_tools_sort").performClick()
            capture("proxy_sort")
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("proxy_tools_sort").assertIsDisplayed()
            compose.runOnIdle { profileId = 10 }
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
            compose.onNodeWithTag("proxy_tools").performClick()
            compose.onNodeWithTag("proxy_tools_sort").performClick()
            compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f, centerY)) }
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
            compose.runOnIdle { assertEquals(options, ProxyPageOptionsStore.load(compose.activity)) }
        } finally { ProxyPageOptionsStore.save(compose.activity, options) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        // 作者 long：Popup属于独立Window，语义树就绪后还需等待平台绘制，避免截图落在打开菜单前一帧。
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(150)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng("main_menu_$name", java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
