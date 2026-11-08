package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** 作者 long：真实GPU像素和Popup中间帧分别验证光学位移/层级命中，避免仅有时钟和截图文件就宣称完成。 */
class GlassStackInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun edgeRefractionChangesPixelsWhileCenterKeepsSameFrostInBothThemes() {
        assertTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val pixels = IntArray(800 * 1000) { index ->
            val stripe = (index % 800 / 91) % 3
            when (stripe) { 0 -> 0xffd03050.toInt(); 1 -> 0xff309060.toInt(); else -> 0xff3050c0.toInt() }
        }
        val bitmap = Bitmap.createBitmap(pixels, 800, 1000, Bitmap.Config.ARGB_8888)
        val backdrop = kotlinx.coroutines.runBlocking { prepareAppMenuBackdrop(bitmap, compose.activity.resources.displayMetrics.density) }
        assertTrue(backdrop.raw.width < bitmap.width)
        assertFalse(backdrop.raw.sameAs(backdrop.frosted))
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) {
            Row(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0f, 60f).forEach { depth ->
                    val renderer = remember { AppMenuGlassRenderer() }
                    Canvas(Modifier.width(160.dp).height(220.dp).testTag("glass_$depth")) {
                        assertTrue(renderer.draw(this, AppMenuMorphFrame(Rect(Offset.Zero, size), 24.dp.toPx(), 1f), backdrop,
                            Offset(-17f, -31f), if (dark) Color(0xff343537) else Color(0xfffcf8f9), dark, depth))
                    }
                }
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val a = compose.onNodeWithTag("glass_0.0").captureToImage().toPixelMap()
            val b = compose.onNodeWithTag("glass_60.0").captureToImage().toPixelMap()
            var edgeDifference = 0; var centerDifference = 0
            val density = compose.activity.resources.displayMetrics.density
            for (y in a.height / 3 until a.height * 2 / 3 step 3) for (x in 3 until (18 * density).toInt() step 2) {
                if (delta(a[x,y], b[x,y]) > .05f) edgeDifference++
            }
            for (y in a.height / 3 until a.height * 2 / 3 step 5) for (x in a.width / 3 until a.width * 2 / 3 step 5) {
                if (delta(a[x,y], b[x,y]) > .01f) centerDifference++
            }
            assertTrue("折射边缘必须区别于同背景的纯磨砂", edgeDifference > 40)
            assertEquals("超过bevel后的中心不能额外位移", 0, centerDifference)
            capture("glass_optics_$theme")
        }
    }

    @Test fun threeLevelsKeepParentsAndFoldCanReverseWithoutExecutingHiddenRows() {
        var open by mutableStateOf(false); var page by mutableIntStateOf(0); var calls = 0
        compose.setContent { KClashTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                val source = rememberAppMenuSource()
                Button({ page = 0; open = true }, Modifier.align(Alignment.TopEnd).appMenuSource(source).testTag("stack_open")) { Text("菜单") }
                AppDropdownMenu(open, { open = false }, source = source, pageKey = page,
                    onNavigateBack = if (page > 0) ({ page-- }) else null) { level ->
                    val value = level as Int
                    if (value > 0) AppDropdownMenuItem({ Text("返回") }, { page = value - 1 }, Modifier.testTag("stack_back_$value"), dismissOnClick = false, isBack = true)
                    AppDropdownMenuItem({ Text("第$value 层动作") }, { calls++ }, Modifier.testTag("stack_action_$value"), dismissOnClick = false)
                    if (value < 2) AppDropdownMenuItem({ Text("展开${value + 1}") }, { page = value + 1 }, Modifier.testTag("stack_next_$value"), dismissOnClick = false, opensSubmenu = true,
                        trailingIcon = { AppMenuSubmenuArrow() })
                    repeat(3) { AppDropdownMenuItem({ Text("尾项$value-$it") }, { calls += 100 }, dismissOnClick = false) }
                }
            }
        } }
        compose.onNodeWithTag("stack_open").performClick()
        compose.onNodeWithTag("stack_next_0").performClick()
        compose.onNodeWithTag("stack_next_1").performClick()
        compose.onNodeWithTag("app_menu_level_0", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("app_menu_level_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("stack_action_0").assertDoesNotExist()
        compose.onNodeWithTag("stack_action_1").assertDoesNotExist()
        capture("stack_three")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("stack_back_2").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { assertEquals(2, page) }
        compose.onNodeWithTag("stack_action_2").assertIsNotEnabled()
        capture("stack_fold_middle")
        compose.onNodeWithTag("stack_back_2").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(2, page); assertEquals(0, calls) }
        compose.onNodeWithTag("stack_action_2").assertIsEnabled()
        // 作者 long：宿主直接改到祖先时，返回标题不能把外部已切走的子页重新展开成永久禁用态。
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { page = 0 }
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("stack_back_2").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("stack_next_0").assertIsEnabled().performClick()
        compose.onNodeWithTag("stack_next_1").performClick()
        // 作者 long：露出父卡的顶部只允许回退一级，不能触发被遮住的父层菜单动作。
        val parent = compose.onNodeWithTag("app_menu_level_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("app_popup_window").performTouchInput { click(Offset(parent.center.x, parent.top + 12f)) }
        compose.runOnIdle { assertEquals(1, page); assertEquals(0, calls) }
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("stack_next_0").assertIsDisplayed()
        compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f, centerY)) }
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
    }

    @Test fun glassSubmenuKeepsCapturedHostAndReturnsInBothThemes() {
        var dark by mutableStateOf(false); var open by mutableStateOf(false); var child by mutableStateOf(false)
        var shifted by mutableStateOf(false)
        lateinit var source: AppMenuSource
        lateinit var stack: AppMenuStackState
        compose.setContent { KClashTheme(darkTheme = dark) { CompositionLocalProvider(LocalAppMenuGlass provides true) {
            source = rememberAppMenuSource()
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                Canvas(Modifier.fillMaxSize().testTag("glass_stack_host")) { repeat(16) { i ->
                    drawRect(if ((i % 2 == 0) != shifted) Color(.25f,.5f,.75f) else Color(.8f,.4f,.3f), Offset(0f,i*size.height/16), androidx.compose.ui.geometry.Size(size.width,size.height/16))
                } }
                Button({ child = false; open = true }, Modifier.align(Alignment.TopEnd).appMenuSource(source, MaterialTheme.colorScheme.primary).testTag("glass_stack_open")) { Text("菜单") }
                AppDropdownMenu(open, { open = false }, source = source, pageKey = child,
                    onNavigateBack = if (child) ({ child = false }) else null) { level ->
                    stack = checkNotNull(LocalAppMenuStack.current)
                    if (level as Boolean) {
                        AppDropdownMenuItem({ Text("返回") }, { child = false }, Modifier.testTag("glass_stack_back"), dismissOnClick = false, isBack = true)
                        repeat(4) { AppDropdownMenuItem({ Text("选项$it") }, {}, dismissOnClick = false) }
                    } else {
                        AppDropdownMenuItem({ Text("父卡动作") }, {}, dismissOnClick = false)
                        AppDropdownMenuItem({ Text("更多") }, { child = true }, Modifier.testTag("glass_stack_next"), dismissOnClick = false, opensSubmenu = true)
                        AppDropdownMenuItem({ Text("尾项") }, {}, dismissOnClick = false)
                    }
                }
            }
        } } }
        for (theme in listOf(false,true)) {
            compose.runOnIdle { dark = theme; shifted = false }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("glass_stack_open").performClick()
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                val control = source.bounds
                val initialWidth = .41f * minOf(control.width, control.height) + maxOf(0, control.width - control.height)
                source.hidden && (stack.outerBounds?.width ?: 0f) > initialWidth + 1f
            }
            compose.mainClock.advanceTimeBy(144)
            compose.runOnIdle { assertTrue(checkNotNull(stack.outerBounds).width > source.bounds.width * 1.2f) }
            capture("glass_stack_opening_$theme")
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.autoAdvance = true
            val beforeHost = compose.onNodeWithTag("glass_stack_host").captureToImage().toPixelMap()
            val beforeMenu = compose.onNodeWithTag("app_menu_level_0", useUnmergedTree = true).captureToImage().toPixelMap()
            compose.runOnIdle { shifted = true }
            val afterHost = compose.onNodeWithTag("glass_stack_host").captureToImage().toPixelMap()
            val afterMenu = compose.onNodeWithTag("app_menu_level_0", useUnmergedTree = true).captureToImage().toPixelMap()
            assertTrue("背景必须实际变化，才能验证菜单仍持有捕获帧", delta(beforeHost[10, 10], afterHost[10, 10]) > .5f)
            var changed = 0
            for (y in 20 until beforeMenu.height - 20 step 4) for (x in 20 until beforeMenu.width - 20 step 4) {
                if (delta(beforeMenu[x,y], afterMenu[x,y]) > .01f) changed++
            }
            assertEquals("玻璃菜单展开期间继续使用同一捕获背景", 0, changed)
            capture("glass_stack_frozen_host_$theme")
            compose.onNodeWithTag("glass_stack_next").performClick()
            compose.onNodeWithTag("glass_stack_back").assertIsDisplayed()
            compose.onNodeWithTag("app_menu_level_1", useUnmergedTree = true).assertExists()
            capture("glass_stack_$theme")
            compose.onNodeWithTag("glass_stack_back").performClick()
            compose.onNodeWithTag("glass_stack_next").assertIsDisplayed()
            compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f,centerY)) }
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
            compose.runOnIdle { assertFalse(source.hidden) }

            // 作者 long：重开必须采到已变化的宿主；否则“冻结一致”也可能只是捕获失败后一直显示普通底色。
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("glass_stack_open").performClick()
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                val control = source.bounds
                val initialWidth = .41f * minOf(control.width, control.height) + maxOf(0, control.width - control.height)
                source.hidden && (stack.outerBounds?.width ?: 0f) > initialWidth + 1f
            }
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.autoAdvance = true
            val recaptured = compose.onNodeWithTag("app_menu_level_0", useUnmergedTree = true).captureToImage().toPixelMap()
            var refreshed = 0
            for (y in 20 until beforeMenu.height - 20 step 4) for (x in 20 until beforeMenu.width - 20 step 4) {
                if (delta(beforeMenu[x,y], recaptured[x,y]) > .05f) refreshed++
            }
            assertTrue("重开应换成当前宿主的真实捕获背景", refreshed > 100)
            capture("glass_stack_recaptured_$theme")
            compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f,centerY)) }
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
            compose.runOnIdle { assertFalse(source.hidden) }
        }
    }

    private fun delta(a: Color, b: Color) = abs(a.red-b.red)+abs(a.green-b.green)+abs(a.blue-b.blue)
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(100)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray())
    }
}
