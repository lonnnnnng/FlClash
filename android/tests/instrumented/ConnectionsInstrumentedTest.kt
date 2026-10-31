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
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        composeRule.onNodeWithText("连接详情").assertIsDisplayed()
        composeRule.onNodeWithText("基本信息").assertIsDisplayed()
        assertDetailVisibleAfterScroll("MATCH")
        assertDetailVisibleAfterScroll("api.example.test")
        assertDetailVisibleAfterScroll("10.0.0.2:54321")
        assertDetailVisibleAfterScroll("192.0.2.10:443")
        assertDetailVisibleAfterScroll("/data/app/client")
        assertDetailVisibleAfterScroll("10000")
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")

        composeRule.onNodeWithText("关闭").performClick()
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
        val topHash = captureHash("detail_default_top", dialog = true)
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        val bottomHash = captureHash("detail_default_bottom", dialog = true)
        assertEquals(
            "top=3240ee1967f94301eea675e680d9156861ae72f1234cd31c342afa2a30edbe75\n" +
                "bottom=6dc102a1a7c09539b7a3b6cf7f05028abed36719569b8fb27a37835364d71e5f",
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
            assertLongDetailFields()
            assertEquals(
                "5de677f0544262b5709003e43c549b6dc658ac84fdd16366be0d5c759ec15a0a",
                captureHash("detail_long_font20_portrait", dialog = true),
            )

            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(5_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            assertLongDetailFields()
            assertEquals(
                "39ff9ffe3ed57e73c2b0fa698c0cf0af9cda85299c62d13fc30944571c43c59f",
                captureHash("detail_long_font20_landscape", dialog = true),
            )
            composeRule.onNodeWithText("关闭").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("connections_detail_dialog").assertDoesNotExist()
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

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    @Test
    fun detailSnapshotSurvivesConnectionRemovalCoreStopAndActivityRecreation() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.showRunningScenario()
        }
        composeRule.onNodeWithTag("connections_row_0").performClick()
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
        composeRule.onNodeWithText("启动核心后显示当前活动连接。").assertExists()
        assertDetailVisibleAfterScroll("2026-10-28T00:00:00Z")
        assertDetailVisibleAfterScroll("com.example.client")
        assertDetailVisibleAfterScroll("MATCH")
        assertDetailVisibleAfterScroll("api.example.test")
        assertDetailVisibleAfterScroll("10.0.0.2:54321")
        assertDetailVisibleAfterScroll("192.0.2.10:443")
        assertDetailVisibleAfterScroll("api.example.test:443")
        assertDetailVisibleAfterScroll("/data/app/client")
        assertDetailVisibleAfterScroll("10000")
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("connections_detail_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("启动核心后显示当前活动连接。").assertIsDisplayed()
    }

    private fun assertLongDetailFields() {
        assertDetailVisibleAfterScroll("MATCH(${ConnectionsTestActivity.LONG_HOST})")
        assertDetailVisibleAfterScroll(ConnectionsTestActivity.LONG_HOST)
        assertDetailVisibleAfterScroll(ConnectionsTestActivity.LONG_PROCESS_PATH)
        assertDetailVisibleAfterScroll("DIRECT → GLOBAL")
        assertDetailVisibleAfterScroll("GLOBAL")
        assertDetailVisibleAfterScroll("special-rule")
        composeRule.onNodeWithText("关闭").assertIsDisplayed()
    }

    private fun assertDetailVisibleAfterScroll(value: String) {
        // 作者 long：限定详情子树并主动滚动，避免背景摘要或屏外语义节点让可达性检查误通过。
        composeRule.onNode(
            hasText(value) and hasAnyAncestor(hasTestTag("connections_detail_dialog")),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
    }

    private fun captureHash(name: String, dialog: Boolean = false): String {
        composeRule.waitForIdle()
        val node = if (dialog) composeRule.onNode(isDialog()) else composeRule.onNodeWithTag("connections_page")
        val bitmap = node.captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        // 作者 long：保留同一断言对应的实际图片，视觉变化必须人工看图后才允许更新哈希基线。
        recordVisualPng("connections-$name", png)
        return MessageDigest.getInstance("SHA-256").digest(png)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
