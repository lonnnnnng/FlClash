package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileSyncUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun progressPartialFailuresAndApplyRetryRetainDistinctResultsInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var state by mutableStateOf(ProfileSyncUiState(true, listOf(ProfileSyncItem(1, "家庭订阅", ProfileSyncStatus.RUNNING), ProfileSyncItem(2, "备用订阅"))))
        var retry = 0; var dismissed = 0
        compose.setContent { KClashTheme(darkTheme = dark) { ProfileSyncResults(state, { dismissed++ }, { retry++ }) } }
        compose.onNodeWithTag("profile_sync_done").assertIsNotEnabled()
        capture("profile_sync_light_progress")
        compose.runOnIdle { dark = true; state = state.withStatus(1, ProfileSyncStatus.SUCCEEDED).withStatus(2, ProfileSyncStatus.FAILED).copy(running = false) }
        compose.onNodeWithText("成功 1，失败 1").assertExists()
        compose.onNodeWithTag("profile_sync_retry").performClick()
        assertEquals(1, retry)
        capture("profile_sync_dark_failures")
        compose.runOnIdle { state = state.withStatus(2, ProfileSyncStatus.SUCCEEDED).copy(applyError = "配置已保存，当前配置应用失败，请重试应用") }
        compose.onNodeWithText("重试应用").assertExists()
        compose.onNodeWithTag("profile_sync_retry").performClick()
        assertEquals(2, retry)
        compose.onNodeWithTag("profile_sync_done").performClick()
        assertEquals(1, dismissed)
        capture("profile_sync_dark_apply_failure")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
