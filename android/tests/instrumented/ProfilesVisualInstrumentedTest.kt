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
        composeRule.onNodeWithTag("profiles_sync_all").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.showRemoteUsageScenario() }
        actual["remote_usage"] = captureHash("remote_usage")
        composeRule.onNodeWithTag("profiles_sync_all").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_expiry_602", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("3.0GB / 10.0GB").assertIsDisplayed()

        val expected = mapOf(
            "empty" to "80902cd421286571e3fb11e68a8ee1f818d371eb77d2ed47921f20feac430e0c",
            "custom_selected" to "2225a95a722601cc43737fb20c485c2391c389f33a74365ce41e69ef7fcfa14f",
            "remote_usage" to "eeb5d0ed2077d39bfb06bb777878e74a0c11518f65cd88d5cbce7bea877ab9dc",
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
