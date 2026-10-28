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
 * 作者 long：Profiles 视觉回归固定空态和单个选中自定义配置，编辑器、Room 和真实 ViewModel 由既有页面 E2E 覆盖。
 */
@RunWith(AndroidJUnit4::class)
class ProfilesVisualInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProfilesTestActivity>()

    @Test
    fun profilesScreenshotMatrixMatchesBaseline() {
        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("profiles_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            "empty" to "7be078c77904b1b361743899e9618d75429d9a9650a205667e77e59b640af0d6",
            "custom_selected" to "793a665b414afa6898783a8298ea631da41a79350a34a707880a8ed3355ba55c",
        )
        val actual = buildMap {
            put("empty", captureHash())
            composeRule.activity.runOnUiThread {
                composeRule.activity.showCustomSelectedScenario()
            }
            put("custom_selected", captureHash())
        }
        val mismatches = expected.mapNotNull { (state, expectedHash) ->
            val actualHash = actual.getValue(state)
            if (actualHash == expectedHash) null else "$state expected=$expectedHash actual=$actualHash"
        }
        if (mismatches.isNotEmpty()) {
            error("profiles screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }

    @Test
    fun profilesSelectedStateOrientationMatrixMatchesBaseline() {
        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("profiles_page")
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
            composeRule.onNodeWithTag("profiles_page").assertExists()
        }

        try {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            waitForOrientation(Configuration.ORIENTATION_PORTRAIT)
            composeRule.activity.showCustomSelectedScenario()
            composeRule.onNodeWithTag("profile_card_601").assertExists()
            val portraitHash = captureHash()

            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitForOrientation(Configuration.ORIENTATION_LANDSCAPE)
            composeRule.activity.showCustomSelectedScenario()
            composeRule.onNodeWithTag("profile_card_601").assertExists()
            val landscapeHash = captureHash()

            assertEquals(
                "portrait=793a665b414afa6898783a8298ea631da41a79350a34a707880a8ed3355ba55c landscape=df2c3e85c4568f0bbca23a968ed5b4f26986209f63d4bf1bc41213bc31a424f6",
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
