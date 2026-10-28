package com.lonnnnnng.flclash.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：横竖屏截图矩阵使用 Debug 固定宿主，验证窗口方向变化不会改变 Access 卡片的结构性布局；
 * 它不访问网络、Room、VPN 或 Mihomo 核心，避免把运行态差异混入视觉基线。
 */
@RunWith(AndroidJUnit4::class)
class AccessControlOrientationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<AccessControlTestActivity>()

    @Test
    fun accessControlCardOrientationScreenshotMatrixMatchesBaseline() {
        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("access_control_card")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        fun waitForOrientation(orientation: Int) {
            composeRule.waitUntil(5_000) {
                var current = Configuration.ORIENTATION_UNDEFINED
                composeRule.activityRule.scenario.onActivity { activity ->
                    current = activity.resources.configuration.orientation
                }
                current == orientation
            }
            composeRule.onNodeWithTag("access_control_card").assertExists()
        }

        try {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            waitForOrientation(Configuration.ORIENTATION_PORTRAIT)
            val portraitHash = captureHash()

            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitForOrientation(Configuration.ORIENTATION_LANDSCAPE)
            val landscapeHash = captureHash()

            assertEquals(
                "portrait=74d0dc07b35597f6e6f19905ae4047108290ae8bb6c8a5275c7976237e8236e3 landscape=72e489740b2ed9e114257b0adbd9732a83acbff0348a801fe7dc8147a52c8318",
                "portrait=$portraitHash landscape=$landscapeHash",
            )
        } finally {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            waitForOrientation(Configuration.ORIENTATION_PORTRAIT)
        }
    }
}
