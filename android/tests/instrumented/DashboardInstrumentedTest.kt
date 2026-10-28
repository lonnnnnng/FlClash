package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：Dashboard 视觉回归只锁定固定状态渲染，启动/停止核心、系统设置和真实 Profile 流程继续由宿主 E2E 测试负责。
 */
@RunWith(AndroidJUnit4::class)
class DashboardInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DashboardTestActivity>()

    @Test
    fun dashboardScreenshotMatrixMatchesBaseline() {
        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("dashboard_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            "default" to "557961817f608d1eb34033928b354f515f24cc546320acbc4b2771d9640e724e",
            "system_ready" to "08dad777abcfe55e86aba952ef355a01a0f757feb45802942f0c3c255fd58607",
        )
        val actual = buildMap {
            put("default", captureHash())
            composeRule.activity.runOnUiThread {
                composeRule.activity.showSystemReadyScenario()
            }
            put("system_ready", captureHash())
        }
        val mismatches = expected.mapNotNull { (state, expectedHash) ->
            val actualHash = actual.getValue(state)
            if (actualHash == expectedHash) null else "$state expected=$expectedHash actual=$actualHash"
        }
        if (mismatches.isNotEmpty()) {
            error("dashboard screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }

    @Test
    fun dashboardDefaultStateFontScaleMatrixMatchesBaseline() {
        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("dashboard_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.setFontScale(1.3f)
        }
        val font13Hash = captureHash()

        composeRule.activity.runOnUiThread {
            composeRule.activity.setFontScale(2.0f)
        }
        val font20Hash = captureHash()

        assertEquals(
            "fontScale=1.3 sha256=d5ab1377baf43e6fb32e85712e70e45421fc66451ffaadbf20067f59fd2e0ab5\n" +
                "fontScale=2.0 sha256=6c826a5ab6f325b3b089664815746980ef2ba30062f29ca9e3e5f067bf128018",
            "fontScale=1.3 sha256=$font13Hash\nfontScale=2.0 sha256=$font20Hash",
        )
    }
}
