package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileQrFormsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scannedLinkPrefillsFormAndOnlyImportsAfterConfirmation() {
        assertPortraitDefaultFontConfiguration()
        var submissions = 0
        compose.setContent { KClashTheme {
            AddProfileDialog(false, {}, {}, {}, { label, url ->
                assertEquals("扫描名称", label); assertEquals("https://example.com/profile.yaml", url); submissions++
            }, onAddCustom = {}, initialRemoteUrl = "https://example.com/profile.yaml", initialRemoteLabel = "扫描名称")
        } }
        compose.onNodeWithTag("profile_add_remote_url").assertTextContains("https://example.com/profile.yaml")
        compose.onNodeWithTag("profile_add_remote_label").assertTextContains("扫描名称")
        compose.runOnIdle { assertEquals(0, submissions) }
        compose.onNodeWithTag("profile_add_submit").performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
    }

    @Test fun permissionAndCameraErrorsKeepAlbumAndRecoveryActions() {
        assertPortraitDefaultFontConfiguration()
        var denied by mutableStateOf(true)
        var retry = 0; var album = 0; var settings = 0
        compose.setContent { KClashTheme {
            ProfileQrScannerOverlay(false, denied, !denied, false, null, false, false,
                {}, { album++ }, { retry++ }, { settings++ }, {})
        } }
        compose.onNodeWithTag("profile_qr_torch").assertIsNotEnabled()
        compose.onNodeWithTag("profile_qr_retry").performClick()
        compose.onNodeWithTag("profile_qr_settings").performClick()
        compose.onNodeWithTag("profile_qr_album").performClick()
        compose.runOnIdle { assertEquals(1, retry); assertEquals(1, settings); assertEquals(1, album); denied = false }
        compose.onNodeWithText("相机不可用，请重试或从相册选择").assertIsDisplayed()
        compose.onNodeWithTag("profile_qr_settings").assertDoesNotExist()
        compose.onNodeWithTag("profile_qr_album").assertIsEnabled()
    }
}
