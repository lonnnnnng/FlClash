package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：Profiles 视觉回归只固定手机竖屏和系统默认字号；横屏、大字号历史矩阵不再执行。
 */
@RunWith(AndroidJUnit4::class)
class ProfilesVisualInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProfilesTestActivity>()

    @Test
    fun profilesScreenshotMatrixMatchesBaseline() {
        val actual = linkedMapOf("empty" to captureHash("empty"))
        composeRule.onAllNodesWithTag("profiles_sync_all").assertCountEquals(0)
        composeRule.activityRule.scenario.onActivity { it.showCustomSelectedScenario() }
        actual["custom_selected"] = captureHash("custom_selected")
        composeRule.onAllNodesWithTag("profiles_sync_all").assertCountEquals(0)
        composeRule.activityRule.scenario.onActivity { it.showRemoteUsageScenario() }
        actual["remote_usage"] = captureHash("remote_usage")
        composeRule.onNodeWithTag("profiles_sync_all").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_expiry_602", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("已用 3.0GB / 10.0GB").assertIsDisplayed()

        val expected = mapOf(
            "empty" to "e8667f3bbd39ea6d54e6c922c4a251a200bdfad7bc111ea624b722f12ad0d87f",
            "custom_selected" to "17606d708901178d81e274683f566a412384967a5d9498eec19c34dce4c7b5f8",
            "remote_usage" to "8cace415c1199ff1f3fceaa167d249c011b1ef85b2aacf8ae14c1698fd631eca",
        )
        val mismatches = actual.mapNotNull { (state, hash) ->
            val baseline = expected.getValue(state)
            if (baseline == hash) null
            else "$state expected=$baseline actual=$hash"
        }
        check(mismatches.isEmpty()) { "profiles screenshot baseline mismatch: ${mismatches.joinToString("; ")}" }
    }

    @Test
    fun profilesLightThemeAndSourceMenusRenderWithoutChangingProfiles() {
        assertPortraitDefaultFontConfiguration()
        composeRule.activityRule.scenario.onActivity { it.showTheme(false) }
        captureHash("empty_light")
        composeRule.activityRule.scenario.onActivity { it.showCustomSelectedScenario() }
        captureHash("custom_selected_light")
        composeRule.activityRule.scenario.onActivity { it.showRemoteUsageScenario() }
        captureHash("remote_usage_light")
        composeRule.onNodeWithTag("profiles_add_fab").performTouchInput { click() }
        listOf("qr", "file", "remote", "custom").forEach {
            composeRule.onNodeWithTag("profile_add_source_$it").assertIsDisplayed()
        }
        captureHash("source_menu_light", "profile_add_source_menu")
        androidx.test.espresso.Espresso.pressBack()
        composeRule.activityRule.scenario.onActivity { it.showTheme(true) }
        composeRule.onNodeWithTag("profiles_add_fab").performTouchInput { click() }
        captureHash("source_menu_dark", "profile_add_source_menu")
        androidx.test.espresso.Espresso.pressBack()
        composeRule.onNodeWithText("机场订阅").assertIsDisplayed()
        prepareTouchVisualCapture()
    }

    private fun captureHash(name: String, tag: String = "profiles_test_screen"): String {
        prepareTouchVisualCapture()
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        recordVisualPng("profiles_$name", png)
        return MessageDigest.getInstance("SHA-256").digest(png)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
