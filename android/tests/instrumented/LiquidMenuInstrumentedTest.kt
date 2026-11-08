package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：使用真实Popup与按钮绘制层验收中断帧和操作时序，不能用几何单测代替实际回源。 */
class LiquidMenuInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun sourceReturnsBeforeActionAndInterruptedOpeningRestoresInBothMaterialsAndThemes() {
        var open by mutableStateOf(false); var dark by mutableStateOf(false); var glass by mutableStateOf(false)
        lateinit var source: AppMenuSource
        var actions = 0
        compose.setContent { KClashTheme(darkTheme = dark) { CompositionLocalProvider(LocalAppMenuGlass provides glass) {
            source = rememberAppMenuSource()
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                Text("配置与代理菜单", Modifier.align(Alignment.Center))
                AppMenuSourceContent(source) {
                    Button({ open = true }, Modifier.appMenuSource(source).align(Alignment.TopEnd).testTag("liquid_open")) { Text("菜单") }
                }
                AppDropdownMenu(open, { open = false }, source = source) {
                    AppDropdownMenuItem({ Text("打开子页") }, { actions++; open = false }, Modifier.testTag("liquid_action"))
                    AppDropdownMenuItem({ Text("禁用项") }, { actions += 100 }, enabled = false)
                }
            } }
        } } }
        for (theme in listOf(false, true)) for (material in listOf(false, true)) {
            compose.runOnIdle { dark = theme; glass = material }
            compose.mainClock.autoAdvance = false
            // 作者 long：真实触点会启动按钮涟漪，覆盖隐藏期间取消动画与回源后的再次点击，语义点击无法验证这条路径。
            compose.onNodeWithTag("liquid_open").performTouchInput { click() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(5_000) { compose.mainClock.advanceTimeByFrame(); source.hidden }
            if (material) {
                // 作者 long：PixelCopy在真实Window帧完成后异步回调；手动时钟须先让出这帧，再采样开场，避免把等待背景的源按钮误记为液滴。
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                android.os.SystemClock.sleep(150)
                compose.mainClock.advanceTimeByFrame()
            }
            compose.mainClock.advanceTimeBy(160)
            capture("open_${theme}_$material")
            compose.mainClock.advanceTimeBy(500)
            val before = actions
            compose.onNodeWithTag("liquid_action").performClick()
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle { assertEquals(before, actions); assertTrue(source.hidden) }
            capture("return_${theme}_$material")
            compose.mainClock.advanceTimeBy(550)
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { assertEquals(before + 1, actions); assertFalse(source.hidden) }
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()

            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("liquid_open").performTouchInput { click() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(5_000) { compose.mainClock.advanceTimeByFrame(); source.hidden }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f, centerY)) }
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { assertFalse(source.hidden); assertEquals(before + 1, actions) }
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        }
    }

    @Test fun carriedDragSelectsOnlyReleaseRowAndBackgroundRevokesPendingAction() {
        var open by mutableStateOf(false); var result = 0
        lateinit var source: AppMenuSource
        val pointer = AppMenuCarry()
        lateinit var stack: AppMenuStackState
        lateinit var registry: AppMenuCarryRegistry
        compose.setContent { KClashTheme {
            source = rememberAppMenuSource()
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                AppMenuSourceContent(source) {
                    Button({ open = true }, Modifier.appMenuSource(source).align(Alignment.TopEnd).testTag("liquid_open")) { Text("菜单") }
                }
                AppDropdownMenu(open, { open = false }, source = source, carriedPointer = pointer) {
                    stack = checkNotNull(LocalAppMenuStack.current)
                    registry = checkNotNull(LocalAppMenuCarry.current)
                    repeat(3) { index -> AppDropdownMenuItem({ Text("选项$index") }, { result = index + 1; open = false }, Modifier.testTag("drag_item_$index")) }
                }
            }
        } }
        compose.onNodeWithTag("liquid_open").performClick()
        val a = compose.onNodeWithTag("drag_item_0").fetchSemanticsNode().boundsInRoot.center
        val b = compose.onNodeWithTag("drag_item_1").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("app_popup_window").performTouchInput { down(a); moveTo(b, delayMillis = 120) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("移动后必须仍持有原指针 armed=${pointer.armed} dragging=${pointer.dragging} position=${pointer.position}", pointer.armed && pointer.dragging) }
        compose.onNodeWithTag("app_popup_window").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals("release=${pointer.release} point=${pointer.releasedPosition} clip=${stack.hitBounds} rows=${registry.items.values.map { it.bounds to it.enabled }}", 2, result)
        }
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        compose.onNodeWithTag("liquid_open").performClick()
        compose.onNodeWithTag("drag_item_0").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drag_item_0").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(2, result); assertFalse(source.hidden) }
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
    }

    @Test fun nestedSelectionReturnsOnlyTopCardBeforeRunningAction() {
        var open by mutableStateOf(false)
        var page by mutableStateOf("root")
        var actions = 0
        lateinit var source: AppMenuSource
        compose.setContent { KClashTheme {
            source = rememberAppMenuSource()
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                AppMenuSourceContent(source) {
                    Button({ open = true; page = "root" }, Modifier.appMenuSource(source).align(Alignment.TopEnd).testTag("nested_open")) { Text("菜单") }
                }
                AppDropdownMenu(open, { open = false }, source = source, pageKey = page,
                    onNavigateBack = if (page == "sub") ({ page = "root" }) else null) { key ->
                    if (key == "root") {
                        AppDropdownMenuItem({ Text("进入子菜单") }, { page = "sub" }, Modifier.testTag("nested_enter"),
                            dismissOnClick = false, opensSubmenu = true)
                        repeat(5) { AppDropdownMenuItem({ Text("后层项目$it") }, {}, enabled = false) }
                    } else AppDropdownMenuItem({ Text("确认选择") }, { actions++; open = false }, Modifier.testTag("nested_select"))
                }
            } }
        } }
        compose.onNodeWithTag("nested_open").performTouchInput { click() }
        compose.onNodeWithTag("nested_enter").performClick()
        compose.onNodeWithTag("nested_select").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nested_select").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals(0, actions); assertTrue(source.hidden) }
        capture("return_nested_top")
        compose.mainClock.advanceTimeBy(550)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(1, actions); assertFalse(source.hidden) }
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        compose.onNodeWithTag("nested_open").performTouchInput { click() }
        compose.onNodeWithTag("nested_enter").assertIsDisplayed()
    }

    @Test fun longMenuFingerScrollDoesNotExecuteHoveredRows() {
        var open by mutableStateOf(false); var calls = 0
        compose.setContent { KClashTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Button({ open = true }, Modifier.align(Alignment.TopEnd).testTag("liquid_open")) { Text("菜单") }
                AppDropdownMenu(open, { open = false }) {
                    repeat(30) { i -> AppDropdownMenuItem({ Text("选项$i") }, { calls++; open = false }, Modifier.testTag("scroll_item_$i")) }
                }
            }
        } }
        compose.onNodeWithTag("liquid_open").performClick()
        compose.onNodeWithTag("app_popup_menu").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, calls); assertTrue(open) }
        val scrolled = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().any {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) && it.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0
        }
        assertTrue("长菜单应由正文滚动接管竖向滑动", scrolled)
        compose.onNodeWithTag("scroll_item_29").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun longPressCarriesOriginalPointerIntoNodeMenuAndReleasesOnce() {
        var selections = 0
        compose.setContent { KClashTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                ProxyNodeCard("节点", "Vless", inUse = false, preferred = false, enabled = true, delay = null,
                    selectionTag = "carried_source", delayTag = "delay", onSelect = { selections++ }, onTestDelay = {})
            }
        } }
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val point = compose.onNodeWithTag("carried_source").fetchSemanticsNode().boundsInWindow.center
        val started = android.os.SystemClock.uptimeMillis()
        fun send(action: Int, at: Offset) {
            val event = android.view.MotionEvent.obtain(started, android.os.SystemClock.uptimeMillis(), action, at.x, at.y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { val injected = instrumentation.uiAutomation.injectInputEvent(event, true); if (action != android.view.MotionEvent.ACTION_CANCEL) assertTrue(injected) } finally { event.recycle() }
        }
        try {
            send(android.view.MotionEvent.ACTION_DOWN, point)
            compose.mainClock.advanceTimeBy(800)
            compose.waitForIdle()
            val target = compose.onNodeWithText("选择").fetchSemanticsNode().boundsInWindow.center
            send(android.view.MotionEvent.ACTION_MOVE, target)
            compose.waitForIdle()
            send(android.view.MotionEvent.ACTION_UP, target)
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(1, selections) }
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        } finally { send(android.view.MotionEvent.ACTION_CANCEL, point) }
    }

    private fun capture(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(100)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        if (name.startsWith("return_")) {
            // 作者 long：只验证回源形状仍在绘制，防止“计时正确但图层已消失”的假通过；不把文字标签当作液滴证据。
            val background = image.getPixel(10, image.height / 3)
            var ink = 0
            for (y in 100 until image.height / 3 step 3) for (x in image.width / 2 until image.width - 12 step 3) {
                val pixel = image.getPixel(x, y)
                val delta = kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
                    kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
                    kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
                if (delta > 35) ink++
            }
            assertTrue("回源中间帧不能提前清空液滴图层", ink > 20)
        }
        recordVisualPng("liquid_$name", java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
