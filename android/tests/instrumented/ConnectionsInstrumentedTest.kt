package com.lonnnnnng.flclash.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：连接页视觉回归固定停止态空连接和运行态单条连接，真实核心请求继续由 MainActivity E2E 验证。
 */
@RunWith(AndroidJUnit4::class)
class ConnectionsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ConnectionsTestActivity>()

    @Test
    fun connectionsScreenshotMatrixMatchesBaseline() {
        val expected = mapOf(
            "stopped_empty" to "07736d6f23751a9f8f9b5caf292562aba4b273eb73e0d78c10beaef487e4087f",
            "running_non_empty" to "e636a875e0244931c81e59e5205eacaac38dbe104fe9dfe8996ee53d14c25860",
        )
        val actual = buildMap {
            put("stopped_empty", captureHash("stopped_empty"))
            composeRule.activity.runOnUiThread {
                composeRule.activity.showRunningScenario()
            }
            put("running_non_empty", captureHash("running_non_empty"))
        }
        val mismatches = expected.mapNotNull { (state, expectedHash) ->
            val actualHash = actual.getValue(state)
            if (actualHash == expectedHash) null else "$state expected=$expectedHash actual=$actualHash"
        }
        if (mismatches.isNotEmpty()) {
            error("connections screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }

    @Test
    fun connectionsRunningStateFontScaleMatrixMatchesBaseline() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.showRunningScenario()
            composeRule.activity.setFontScale(1.3f)
        }
        val font13Hash = captureHash("running_font13")

        composeRule.activity.runOnUiThread {
            composeRule.activity.setFontScale(2.0f)
        }
        val font20Hash = captureHash("running_font20")

        assertEquals(
            "fontScale=1.3 sha256=70b6fd8e8d9f041e0b51d31195b6e0b26a30005e3f197fbfe5677d5f4c9402e9\n" +
                "fontScale=2.0 sha256=0eee362d4265877f4f81d832ae76978d0e00f4cbaae59888966dc2a280c3f502",
            "fontScale=1.3 sha256=$font13Hash\nfontScale=2.0 sha256=$font20Hash",
        )
    }

    @Test
    fun connectionsRowOpensDetailsAndClosesWithoutChangingList() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.showRunningScenario()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connections_row_0").performClick()
        expandDetailSheet()
        composeRule.onNodeWithText("连接详情").assertIsDisplayed()
        composeRule.onNodeWithText("基本信息").assertIsDisplayed()
        assertDetailVisibleAfterScroll("MATCH")
        assertDetailVisibleAfterScroll("api.example.test")
        assertDetailVisibleAfterScroll("10.0.0.2:54321")
        assertDetailVisibleAfterScroll("192.0.2.10:443")
        assertDetailVisibleAfterScroll("com.example.client(10000)")
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")

        composeRule.onNodeWithContentDescription("关闭连接详情").performClick()
        composeRule.onNodeWithText("连接详情").assertDoesNotExist()
        composeRule.onNodeWithTag("connections_row_0").assertIsDisplayed()
        composeRule.onNodeWithText("活动连接（1）").assertIsDisplayed()
    }

    @Test
    fun connectionsDetailScreenshotMatrixMatchesBaseline() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.showRunningScenario()
        }
        composeRule.onNodeWithTag("connections_row_0").performClick()
        expandDetailSheet()
        val shapeHash = captureHash("detail_superellipse_dark", sheet = true)
        val topHash = captureHash("detail_default_top", dialog = true)
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        val bottomHash = captureHash("detail_default_bottom", dialog = true)
        assertEquals(
            "shape=412ccb6a664b13b16713614662b92a8da1891806ef9c412f17366c3e3c1b06c5\n" +
                "top=ab1204b05bce90e6d4431bb9f4ade38043c2f1d257889b57244d5060d57bcea6\n" +
                "bottom=ef928ddde3f1d0ce88cd0e84803327bd91da8b476c5db00ac0901b12287567d3",
            "shape=$shapeHash\ntop=$topHash\nbottom=$bottomHash",
        )
    }

    @Test
    fun longConnectionDetailsRemainReachableWithLargeFontAndLandscape() {
        val originalFontScale = shell("settings get system font_scale").trim().toFloat()
        try {
            setSystemFontScaleAndWait(2f)
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.showLongRunningScenario()
                activity.setFontScale(2f)
            }
            composeRule.onNodeWithTag("connections_row_0").performClick()
            expandDetailSheet()
            assertLongDetailFields()
            val portraitHash = captureHash("detail_long_font20_portrait", dialog = true)

            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(5_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            expandDetailSheet()
            assertLongDetailFields()
            val landscapeHash = captureHash("detail_long_font20_landscape", dialog = true)
            composeRule.onNodeWithContentDescription("关闭连接详情").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("connections_detail_dialog").assertDoesNotExist()
            // 作者 long：两种方向都采样后再报告差异，首次截图变化不能阻断横屏可达性的独立检查。
            assertEquals(
                "portrait=0f0087659342aece563b627f51706fb18d358789bd3e3e6e6bdccc464e896823\n" +
                    "landscape=7f4843c3c31a2899e4b693071ded28f92b10a7a5d3886c1e1fe6b3d7e5cbf0ea",
                "portrait=$portraitHash\nlandscape=$landscapeHash",
            )
        } finally {
            setSystemFontScaleAndWait(originalFontScale)
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            composeRule.waitUntil(5_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
        }
    }

    private fun setSystemFontScaleAndWait(value: Float) {
        // 作者 long：Dialog 有独立窗口，只注入页面 LocalDensity 无法覆盖弹窗；必须验证真实系统字号，并在 finally 恢复。
        shell("settings put system font_scale $value")
        composeRule.waitUntil(10_000) {
            kotlin.math.abs(composeRule.activity.resources.configuration.fontScale - value) < 0.01f
        }
        composeRule.waitForIdle()
    }

    @Test
    fun detailTextCopiesAndMetadataSurvivesRecreation() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("connections_row_0").performClick()
        expandDetailSheet()
        assertDetailVisibleAfterScroll("US Example ISP")
        assertDetailVisibleAfterScroll("AS64496")
        composeRule.onNodeWithTag("connection_detail_目标 ASN").performClick()
        assertEquals("AS64496", composeRule.activity.lastCopiedValue)
        composeRule.onNodeWithText("已复制").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        expandDetailSheet()
        assertDetailVisibleAfterScroll("US Example ISP")
        assertDetailVisibleAfterScroll("AS64496")
        assertDetailVisibleAfterScroll("fake-ip")
        composeRule.onNodeWithTag("connection_detail_DNS 模式").performClick()
        assertEquals("fake-ip", composeRule.activity.lastCopiedValue)
    }

    @Test
    fun missingFieldsStayHiddenAndSheetDismissesOnBackAndSwipe() {
        composeRule.activityRule.scenario.onActivity { it.showMinimalRunningScenario() }
        composeRule.onNodeWithTag("connections_row_0").performClick()
        listOf("进程", "目标 GeoIP", "目标 ASN", "DNS 模式", "UID").forEach {
            composeRule.onNodeWithTag("connection_detail_$it").assertDoesNotExist()
        }
        androidx.test.espresso.Espresso.pressBack()
        composeRule.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("connections_row_0").performClick()
        composeRule.onNodeWithTag("connections_detail_sheet").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("connections_detail_sheet")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
    }

    @Test
    fun sheetDetentsSupportTapDragSemanticsAndScrimDismissal() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("connections_row_0").performClick()
        assertSheetHeight(9f / 16f)
        composeRule.onNodeWithTag("connection_detail_handle").performClick()
        assertSheetHeight(0.9f)
        composeRule.activityRule.scenario.recreate()
        assertSheetHeight(0.9f)
        composeRule.onNodeWithTag("connections_detail_sheet").performSemanticsAction(SemanticsActions.Collapse) { it() }
        assertSheetHeight(9f / 16f)
        val height = composeRule.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot.height
        composeRule.onNodeWithTag("connections_detail_sheet").performTouchInput {
            swipe(Offset(width / 2f, 40f), Offset(width / 2f, 40f - height * 0.4f), 800)
        }
        assertSheetHeight(0.9f)
        composeRule.onNodeWithTag("connections_detail_sheet").performTouchInput {
            swipe(Offset(width / 2f, 40f), Offset(width / 2f, 40f + height * 0.35f), 1000)
        }
        assertSheetHeight(9f / 16f)
        composeRule.onNodeWithTag("connection_detail_scrim").performTouchInput { click(Offset(2f, 2f)) }
        composeRule.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
    }

    @Test
    fun scrollingFromContentMiddleDoesNotCollapseSheetWithRemainingFling() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("connections_row_0").performClick()
        expandDetailSheet()
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        val content = composeRule.onNodeWithTag("connections_detail_dialog")
        assertTrue(content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        // 作者 long：一次快速下滑从正文底部回顶，剩余惯性不能越过正文边界收起或关闭详情。
        content.performTouchInput {
            swipe(Offset(width / 2f, height * 0.15f), Offset(width / 2f, height * 0.95f), 180)
        }
        composeRule.waitUntil(5_000) {
            content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() == 0f
        }
        assertSheetHeight(0.9f)
        composeRule.onNodeWithText("基本信息").assertIsDisplayed()
        // 作者 long：下一次从顶部起手仍可收起，不能靠全面禁用正文拖动来修复误关闭。
        val viewport = composeRule.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
        content.performTouchInput {
            swipe(Offset(width / 2f, 24f), Offset(width / 2f, 24f + viewport.height * 0.35f), 1000)
        }
        assertSheetHeight(9f / 16f)
    }

    @Test
    fun lightDetailPortraitAndLandscapeMatchBaselines() {
        composeRule.activityRule.scenario.onActivity { it.setDarkTheme(false); it.showRunningScenario() }
        composeRule.onNodeWithTag("connections_row_0").performClick()
        expandDetailSheet()
        val actual = linkedMapOf<String, String>()
        try {
            actual["light_portrait_top"] = captureHash("detail_light_portrait_top", dialog = true)
            actual["light_superellipse"] = captureHash("detail_superellipse_light", sheet = true)
            assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
            actual["light_portrait_bottom"] = captureHash("detail_light_portrait_bottom", dialog = true)
            composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            assertSidePanelBounds()
            composeRule.onNodeWithText("基本信息").performScrollTo()
            actual["light_landscape_top"] = captureHash("detail_light_landscape_top", dialog = true)
            assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
            actual["light_landscape_bottom"] = captureHash("detail_light_landscape_bottom", dialog = true)
            composeRule.onNodeWithTag("connection_detail_DNS 模式").performScrollTo().performClick()
            assertEquals("fake-ip", composeRule.activity.lastCopiedValue)
            composeRule.onNodeWithContentDescription("关闭连接详情").performClick()
        } finally {
            restorePortrait()
        }
        assertEquals(mapOf(
            "light_portrait_top" to "79c82f2f62af1d189ec3082ec82406bca4eba90b9f13d362a89a5a3831d08666",
            "light_superellipse" to "7af73735580eefd6ef9ad354d7d668cbef94df290fcfcc11e19220a8e2ea0660",
            "light_portrait_bottom" to "ed2d6c8f8350dc5770a6586ace33751d193dfb695773ef62a56d4c6d29cac67d",
            "light_landscape_top" to "23c1bc4aa5e65997322c179df342e85aa3d171e2318e6f6ba69bc2173ff660eb",
            "light_landscape_bottom" to "3922ea63a1da30ec5637cf51935f0a23b001ddea3f91cbd7e9f5e8187486ff83",
        ), actual)
    }

    @Test
    fun smallWindowUsesActualBoundsAndKeepsLastFieldReachable() {
        val previousWidth = composeRule.activity.window.decorView.width
        val previousOverride = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val density = composeRule.activity.resources.displayMetrics.density
        val widthPx = (360 * density).toInt()
        val heightPx = (640 * density).toInt()
        val actual = linkedMapOf<String, String>()
        try {
            // 作者 long：Dialog 是独立窗口，必须改变真机实际可用显示区域，给背景加 size 无法证明面板适应小窗口。
            shell("wm size ${widthPx}x$heightPx")
            composeRule.waitUntil(10_000) { composeRule.activity.window.decorView.width == widthPx }
            composeRule.activityRule.scenario.onActivity { it.setDarkTheme(false); it.showRunningScenario() }
            composeRule.onNodeWithTag("connections_row_0").performClick()
            assertSheetHeight(9f / 16f)
            expandDetailSheet()
            assertSheetHeight(0.9f)
            actual["small_light_top"] = captureHash("detail_small_light_top", dialog = true)
            assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
            actual["small_light_bottom"] = captureHash("detail_small_light_bottom", dialog = true)
            composeRule.onNodeWithContentDescription("关闭连接详情").assertIsDisplayed().performClick()
        } finally {
            shell("wm size ${previousOverride ?: "reset"}")
            composeRule.waitUntil(10_000) { composeRule.activity.window.decorView.width == previousWidth }
        }
        assertEquals(mapOf(
            "small_light_top" to "0dc934dd4bbe284ae48fd9cb15786d66867081db1f6f78f02240e0be569eb7c2",
            "small_light_bottom" to "f00476b91d2a27816ce09fd487b9febcb325fd166fb242ff02449eb6f6ecf03f",
        ), actual)
    }

    @Test
    fun disabledSystemAnimationsStillAllowExpandCollapseAndClose() {
        val previousScale = shell("settings get global animator_duration_scale").trim()
        try {
            shell("settings put global animator_duration_scale 0")
            composeRule.activityRule.scenario.recreate()
            composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
            composeRule.onNodeWithTag("connections_row_0").performClick()
            assertSheetHeight(9f / 16f)
            composeRule.onNodeWithTag("connection_detail_handle").performClick()
            assertSheetHeight(0.9f)
            composeRule.onNodeWithTag("connection_detail_handle").performClick()
            assertSheetHeight(9f / 16f)
            androidx.test.espresso.Espresso.pressBack()
            composeRule.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
        } finally {
            if (previousScale == "null") shell("settings delete global animator_duration_scale")
            else shell("settings put global animator_duration_scale $previousScale")
        }
    }

    @Test
    fun sheetEntranceAndExitAnimateTogetherWithTheModalBarrier() {
        val wasAutoAdvance = composeRule.mainClock.autoAdvance
        try {
            composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            composeRule.onNodeWithTag("connections_row_0").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals(
                "进入中",
                composeRule.onNodeWithTag("connection_detail_scrim").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription],
            )
            // 作者 long：必须检查真实中间帧的位移与遮罩像素，只有状态标签变化不能证明面板实际在移动。
            composeRule.mainClock.advanceTimeBy(96)
            composeRule.waitForIdle()
            val enteringTop = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot.top
            val enteringScrimAlpha = composeRule.onNodeWithTag("connection_detail_scrim").captureToImage().toPixelMap()[2, 2].alpha
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.waitForIdle()
            val restingTop = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot.top
            val restingScrimAlpha = composeRule.onNodeWithTag("connection_detail_scrim").captureToImage().toPixelMap()[2, 2].alpha
            assertTrue("入场中面板尚未到达停靠点", enteringTop > restingTop + 5f)
            assertTrue("入场中遮罩应逐步变深", enteringScrimAlpha < restingScrimAlpha - 0.01f)
            assertEquals(
                "已打开",
                composeRule.onNodeWithTag("connection_detail_scrim").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription],
            )

            composeRule.onNodeWithContentDescription("关闭连接详情").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            assertEquals(
                "关闭中",
                composeRule.onNodeWithTag("connection_detail_scrim").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription],
            )
            composeRule.mainClock.advanceTimeBy(96)
            composeRule.waitForIdle()
            assertTrue(
                "退出中面板应向底部移动",
                composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot.top > restingTop + 5f,
            )
            assertTrue(
                "退出中遮罩应逐步变浅",
                composeRule.onNodeWithTag("connection_detail_scrim").captureToImage().toPixelMap()[2, 2].alpha < restingScrimAlpha - 0.01f,
            )
            // 作者 long：重复关闭或退出中旋转都不能重新入场，也不能丢掉最终移除 Dialog 的回调。
            composeRule.onNodeWithTag("connections_detail_sheet").performSemanticsAction(SemanticsActions.Dismiss) { it() }
            composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
        } finally {
            composeRule.mainClock.autoAdvance = wasAutoAdvance
            restorePortrait()
        }
    }

    @Test
    fun sideSheetUsesFlutterDeceleratingEntranceAndExit() {
        val wasAutoAdvance = composeRule.mainClock.autoAdvance
        try {
            composeRule.activityRule.scenario.onActivity {
                it.setDarkTheme(false)
                it.showRunningScenario()
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(5_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            composeRule.onNodeWithTag("connections_row_0").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals(
                "进入中",
                composeRule.onNodeWithTag("connection_detail_scrim").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription],
            )
            val viewport = composeRule.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
            composeRule.mainClock.advanceTimeBy(120)
            val enteringRight = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot.right
            assertTrue("侧栏入场应从屏幕右侧滑入", enteringRight > viewport.right + 5f)
            composeRule.mainClock.advanceTimeBy(220)
            composeRule.waitForIdle()
            val resting = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.right, resting.right, 2f)
            assertEquals("已打开", composeRule.onNodeWithTag("connection_detail_scrim").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription])

            composeRule.onNodeWithContentDescription("关闭连接详情").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(80)
            val exitingRight = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot.right
            assertTrue("侧栏退场应向屏幕右侧滑出", exitingRight > resting.right + 5f)
            assertEquals("关闭中", composeRule.onNodeWithTag("connection_detail_scrim").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription])
            composeRule.mainClock.advanceTimeBy(180)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
        } finally {
            composeRule.mainClock.autoAdvance = wasAutoAdvance
            restorePortrait()
        }
    }

    private fun restorePortrait() {
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
    }

    private fun assertSheetHeight(fraction: Float) {
        // 作者 long：手势松手由独立协程完成归档；等待目标几何后仍执行严格像素断言，不用固定延迟掩盖卡住的动画。
        composeRule.waitUntil(5_000) {
            val viewport = composeRule.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
            val panel = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode()
            panel.config[SemanticsProperties.StateDescription] == (if (fraction == 0.9f) "已展开" else "半展开") &&
                kotlin.math.abs(viewport.height * fraction - panel.boundsInRoot.height) <= 2f
        }
        val viewport = composeRule.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot
        assertEquals("面板高度必须基于安全区内实际窗口", viewport.height * fraction, panel.height, 2f)
        assertEquals(viewport.bottom, panel.bottom, 2f)
        assertEquals(viewport.width, panel.width, 2f)
    }

    private fun assertSidePanelBounds() {
        composeRule.waitForIdle()
        val viewport = composeRule.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("connections_detail_sheet").fetchSemanticsNode().boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        val expectedWidth = (viewport.width * 0.4f).coerceIn(360 * density, 560 * density)
        assertEquals(expectedWidth, panel.width, 2f)
        assertEquals(viewport.height, panel.height, 2f)
        assertEquals(viewport.right, panel.right, 2f)
        assertTrue(panel.top >= viewport.top)
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    @Test
    fun detailSnapshotSurvivesConnectionRemovalCoreStopAndActivityRecreation() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.showRunningScenario()
        }
        composeRule.onNodeWithTag("connections_row_0").performClick()
        expandDetailSheet()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.showEmptyRunningScenario()
        }
        composeRule.onNodeWithTag("connections_row_0").assertDoesNotExist()
        composeRule.onNodeWithText("当前没有活动连接。").assertExists()
        assertDetailVisibleAfterScroll("api.example.test")
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.showStoppedScenario()
        }
        composeRule.activityRule.scenario.recreate()
        expandDetailSheet()
        composeRule.onNodeWithText("启动核心后显示当前活动连接。").assertExists()
        assertDetailVisibleAfterScroll(formatConnectionStart("2026-10-28T00:00:00Z"))
        assertDetailVisibleAfterScroll("com.example.client(10000)")
        assertDetailVisibleAfterScroll("MATCH")
        assertDetailVisibleAfterScroll("api.example.test")
        assertDetailVisibleAfterScroll("10.0.0.2:54321")
        assertDetailVisibleAfterScroll("192.0.2.10:443")
        assertDetailVisibleAfterScroll("api.example.test:443")
        assertDetailVisibleAfterScroll("com.example.client(10000)")
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        composeRule.onNodeWithContentDescription("关闭连接详情").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("connections_detail_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("启动核心后显示当前活动连接。").assertIsDisplayed()
    }

    private fun assertLongDetailFields() {
        assertDetailVisibleAfterScroll("MATCH(${ConnectionsTestActivity.LONG_HOST})")
        assertDetailVisibleAfterScroll(ConnectionsTestActivity.LONG_HOST)
        assertDetailVisibleAfterScroll("AS64496")
        assertDetailVisibleAfterScroll("US Example ISP")
        assertDetailVisibleAfterScroll("fake-ip")
        composeRule.onNodeWithTag("connection_detail_特殊代理").performScrollTo().assertIsDisplayed()
        assertDetailVisibleAfterScroll("special-rule")
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        composeRule.onNodeWithContentDescription("关闭连接详情").assertIsDisplayed()
    }

    private fun assertDetailVisibleAfterScroll(value: String) {
        // 作者 long：限定详情子树并主动滚动，避免背景摘要或屏外语义节点让可达性检查误通过。
        if (value == "DIRECT → GLOBAL") {
            composeRule.onNodeWithTag("connection_detail_chains").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithContentDescription(value).assertIsDisplayed()
            return
        }
        composeRule.onNode(
            hasText(value) and hasAnyAncestor(hasTestTag("connections_detail_dialog")),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
    }

    private fun expandDetailSheet() {
        // 作者 long：半展开面板的底部尚在屏外；先使用真实展开语义，再验证字段滚动，不能把屏外存在当成可见。
        val actions = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand) and hasTestTag("connections_detail_sheet"))
        if (actions.fetchSemanticsNodes().isNotEmpty()) {
            actions[0].performSemanticsAction(SemanticsActions.Expand) { it() }
            assertSheetHeight(0.9f)
        }
        composeRule.waitForIdle()
    }

    private fun captureHash(name: String, dialog: Boolean = false, sheet: Boolean = false): String {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(
            when {
                sheet -> "connections_detail_sheet"
                dialog -> "connections_detail_content"
                else -> "connections_page"
            },
        )
        val bitmap = node.captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        // 作者 long：保留同一断言对应的实际图片，视觉变化必须人工看图后才允许更新哈希基线。
        recordVisualPng("connections-$name", png)
        if (dialog && InstrumentationRegistry.getArguments().getString("kclashCapturePng") == "true") {
            val screen = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val screenPng = ByteArrayOutputStream().also { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            recordVisualPng("connections-screen-$name", screenPng.toByteArray())
            screen.recycle()
        }
        return MessageDigest.getInstance("SHA-256").digest(png)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
