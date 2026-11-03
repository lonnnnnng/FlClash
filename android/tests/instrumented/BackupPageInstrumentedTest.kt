package com.lonnnnnng.flclash.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupPageInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun remoteAccountFileScopeStrategyAndBothThemesUseIndependentPages() {
        assertPortraitDefaultFontConfiguration()
        var credentials by mutableStateOf<WebDavCredentials?>(null)
        var strategy by mutableStateOf(BackupRestoreStrategy.COMPATIBLE)
        var dark by mutableStateOf(false)
        var upload: BackupRestoreScope? = null
        var restore: Pair<BackupRestoreScope, BackupRestoreStrategy>? = null
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileMaintenancePage(true, false, credentials, {}, {}, { scope, value -> restore = scope to value },
                { credentials = it }, { credentials = null }, {}, { _, scope -> upload = scope }, { _, scope, value -> restore = scope to value },
                strategy, { strategy = it }, WebDavConnectionState.CONNECTED)
        } }
        capture("backup_light_unbound")
        compose.onNodeWithTag("webdav_open").performClick()
        compose.onNodeWithTag("webdav_save").performClick()
        compose.onNodeWithTag("webdav_account_error").assertExists()
        compose.onNodeWithTag("webdav_base_url").performTextInput("https://dav.example.test/kclash")
        compose.onNodeWithTag("webdav_username").performTextInput("alice")
        compose.onNodeWithTag("webdav_password").performTextInput("fixture-password")
        capture("backup_account_light_keyboard")
        compose.onNodeWithTag("webdav_save").assertIsDisplayed().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("webdav_account_page").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("webdav_connection").assertTextContains("连接成功", substring = true)
        compose.onNodeWithTag("webdav_file").performClick()
        compose.onNodeWithTag("webdav_file_name").performTextReplacement("snapshot.zip")
        compose.onNodeWithTag("webdav_file_save").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("webdav_file_name").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals("snapshot.zip", credentials?.fileName); dark = true }
        capture("backup_dark_bound")
        compose.onNodeWithTag("tools_backup_page").performScrollToNode(hasTestTag("backup_strategy"))
        compose.onNodeWithTag("backup_strategy").performClick()
        compose.onNodeWithTag("backup_strategy_OVERRIDE").performClick()
        compose.onNodeWithTag("tools_backup_page").performScrollToNode(hasTestTag("backup_remote_export"))
        compose.onNodeWithTag("backup_remote_export").performClick()
        compose.runOnIdle { assertNull(upload) }
        compose.onNodeWithTag("webdav_upload_confirm").performClick()
        compose.runOnIdle { assertEquals(BackupRestoreScope.ALL_DATA, upload) }
        compose.onNodeWithTag("backup_remote_restore").performClick()
        compose.onNodeWithTag("backup_scope_PROFILES_ONLY").performClick()
        capture("backup_restore_dark_scope")
        compose.onNodeWithTag("backup_restore_confirm").performClick()
        compose.runOnIdle { assertEquals(BackupRestoreScope.PROFILES_ONLY to BackupRestoreStrategy.OVERRIDE, restore) }
    }

    @Test fun busyAndRunningGuardsKeepRestoreAndRemoteActionsDisabled() {
        assertPortraitDefaultFontConfiguration()
        var busy by mutableStateOf(false)
        var exports = 0
        compose.setContent { KClashTheme { ProfileMaintenancePage(false, busy, null, {}, { exports++ }, { _, _ -> }, {}, {}, {}, { _, _ -> }, { _, _, _ -> }) } }
        compose.onNodeWithTag("backup_restore").assertIsNotEnabled()
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("backup_export").assertIsNotEnabled()
        compose.onNodeWithTag("webdav_open").assertIsNotEnabled()
        compose.onNodeWithTag("backup_busy").assertExists()
        compose.runOnIdle { assertEquals(0, exports) }
    }
    private fun capture(name: String) {
        compose.waitForIdle(); val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
