package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 作者 long：首屏截图复用正式 Scaffold 与卡片；固定数据只证明视觉排版，核心和系统入口由真实宿主 E2E 验证。
 */
@RunWith(AndroidJUnit4::class)
class DashboardInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DashboardTestActivity>()

    @Test
    fun dashboardScreenshotMatrixMatchesBaseline() {
        val actual = linkedMapOf("default" to captureHash("default"))
        composeRule.activityRule.scenario.onActivity { it.showSystemReadyScenario() }
        actual["system_ready"] = captureHash("system_ready")
        assertBaselines(actual)
    }

    @Test
    fun dashboardRunningStateRendersStatsAndDiagnosticEvidence() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        composeRule.onNodeWithTag("dashboard_runtime_card").assertIsDisplayed()
        composeRule.onNodeWithText("核心运行中").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("停止核心").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_stats_card").assertIsDisplayed()
        composeRule.onNodeWithText("3 个连接").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_card").assertIsDisplayed()
        composeRule.onNodeWithText("固定诊断样例").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_probe_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("main_navigation_dock").assertIsDisplayed()
    }

    @Test
    fun dashboardRunningStateScreenshotMatrixMatchesBaseline() {
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        assertBaselines(mapOf("running" to captureHash("running")))
    }

    @Test
    fun dashboardLightThemeScreenshotMatrixMatchesBaseline() {
        val actual = linkedMapOf<String, String>()
        composeRule.activityRule.scenario.onActivity { it.setDarkTheme(false) }
        actual["light_default"] = captureHash("light_default")
        composeRule.activityRule.scenario.onActivity { it.showRunningScenario() }
        actual["light_running"] = captureHash("light_running")
        composeRule.onNodeWithTag("main_navigation_dock").assertIsDisplayed()
        assertBaselines(actual)
    }

    private fun captureHash(name: String): String {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag("dashboard_page").captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        recordVisualPng("dashboard_$name", png)
        return MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
    }

    private fun assertBaselines(actual: Map<String, String>) {
        val mismatches = actual.mapNotNull { (name, hash) ->
            val expected = baselines[name]
            if (expected == hash) null else "$name expected=$expected actual=$hash"
        }
        check(mismatches.isEmpty()) { "dashboard screenshot baseline mismatch: ${mismatches.joinToString("; ")}" }
    }
}

private val baselines = mapOf(
    "default" to "464738e30a98f0049c1cb6379559c125d8fbfe90491baee31341bef861c86c2c",
    "system_ready" to "47b0ac48bcf9b38022979f38b1fa4e87dd926494836148ebdcf8eeabc80aa905",
    "running" to "0d7821c9ffb9e543cb50cea53f9eb9e14e97ced739f0b6b814588330b05f64e7",
    "light_default" to "a8282432638a383be3ebec20b220d66ec14da3c299146968f124651ec6fe7755",
    "light_running" to "893e382e6b88b9ae4e314ed7da7f70d8eb3c19497aa350228cfe1d9e683fc139",
)
