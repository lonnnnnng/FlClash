package com.lonnnnnng.flclash.ui

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
 * 作者 long：连接页视觉回归固定停止态空连接和运行态单条连接，真实核心请求继续由 MainActivity E2E 验证。
 */
@RunWith(AndroidJUnit4::class)
class ConnectionsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ConnectionsTestActivity>()

    @Test
    fun connectionsScreenshotMatrixMatchesBaseline() {
        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("connections_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            "stopped_empty" to "07736d6f23751a9f8f9b5caf292562aba4b273eb73e0d78c10beaef487e4087f",
            "running_non_empty" to "52ec482ca5537a64a2091d563f4fb95ed9fea2d61562cca86d4499e0a88e2920",
        )
        val actual = buildMap {
            put("stopped_empty", captureHash())
            composeRule.activity.runOnUiThread {
                composeRule.activity.showRunningScenario()
            }
            put("running_non_empty", captureHash())
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
        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("connections_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        composeRule.activity.runOnUiThread {
            composeRule.activity.showRunningScenario()
            composeRule.activity.setFontScale(1.3f)
        }
        val font13Hash = captureHash()

        composeRule.activity.runOnUiThread {
            composeRule.activity.setFontScale(2.0f)
        }
        val font20Hash = captureHash()

        assertEquals(
            "fontScale=1.3 sha256=978584c42dd91dcf17266075137c0f759630207c1faeae1e4776da8f01379c66\n" +
                "fontScale=2.0 sha256=74f7977c07aa092c18e1a61db68232bdc0b9934f1d6b9b5486e147da5ac0659e",
            "fontScale=1.3 sha256=$font13Hash\nfontScale=2.0 sha256=$font20Hash",
        )
    }
}
