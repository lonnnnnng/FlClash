package com.lonnnnnng.flclash.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
        val topHash = captureHash("detail_default_top", dialog = true)
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        val bottomHash = captureHash("detail_default_bottom", dialog = true)
        assertEquals(
            "top=544f945f3c6495d2ad36cef3f3d734d7db457d34fecf4c0840fb831b80df6254\n" +
                "bottom=a2268884f7cfb9a898d3041d763b5928ea251d30c6f6fd2db47f744b81e00e2a",
            "top=$topHash\nbottom=$bottomHash",
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
                "portrait=8ba154066394417aa37fff115f58b25664a72ee2efbb6f1306cca5778204dea5\n" +
                    "landscape=6c9282ac26020493995b3fc56232fb227ff81d403ff4735a06ab66ad31aa819f",
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
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
        val actions = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        if (actions.fetchSemanticsNodes().isNotEmpty()) {
            actions[0].performSemanticsAction(SemanticsActions.Expand) { it() }
        }
        composeRule.waitForIdle()
    }

    private fun captureHash(name: String, dialog: Boolean = false): String {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(if (dialog) "connections_detail_content" else "connections_page")
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
