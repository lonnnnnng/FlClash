package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.service.ProfileProviderDocument
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileProviderManagementInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun providerMenusBatchRetryAndSubscriptionAreReachableInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var updated = 0; var retries = 0; var edited = ""; var uploaded = ""
        val rows = listOf(CoreExternalProvider("远程节点", "Proxy", "HTTP", 3, null,
            CoreProviderSubscription(100, 200, 1024, null)), CoreExternalProvider("本地规则", "Rule", "File", 2, null))
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileProvidersPanel(ProfileProvidersUiState(1, rows, failedNames = setOf("远程节点")), {}, {},
                { updated++ }, { retries++ }, {}, { edited = it }, { uploaded = it })
        } }
        compose.onNodeWithTag("profile_providers_update_all").performClick()
        compose.onNodeWithTag("profile_providers_retry_failed").performClick()
        compose.onNodeWithTag("profile_provider_menu_0").performClick()
        compose.onNodeWithTag("profile_provider_edit_0").performClick()
        compose.onNodeWithTag("profile_provider_menu_1").performClick()
        compose.onNodeWithTag("profile_provider_upload_1").performClick()
        compose.runOnIdle { assertEquals(1, updated); assertEquals(1, retries); assertEquals("远程节点", edited); assertEquals("本地规则", uploaded) }
        compose.onNodeWithTag("profile_provider_menu_0").performClick()
        capture("provider_menu")
        compose.onNodeWithTag("profile_provider_subscription_0").performClick()
        compose.onNodeWithText("不限到期时间").assertIsDisplayed()
        capture("provider_subscription")
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { dark = true }
        compose.onNodeWithTag("profile_providers_update_all").assertIsEnabled()
    }

    @Test fun providerEditorProtectsDraftShowsFailureAndMrsIsReadOnly() {
        assertPortraitDefaultFontConfiguration()
        val document = ProfileProviderDocument(1, CoreProviderDocument("规则资源", "text", "payload: []", "missing"))
        var state by mutableStateOf(ProviderDocumentUiState("规则资源", false, document, "payload: [example.com]", true, error = "保存失败，请检查内容后重试"))
        var closed = 0; var saved = 0
        compose.setContent { KClashTheme {
            ProfileProviderDocumentPage(state, { closed++ }, {}, { saved++ }, {})
        } }
        compose.onNodeWithTag("profile_provider_document_content").assertTextContains("example.com", substring = true)
        capture("provider_editor_failure")
        compose.onNodeWithTag("profile_provider_document_back").performClick()
        capture("provider_editor_exit")
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { assertEquals(0, closed) }
        compose.onNodeWithTag("profile_provider_document_save").performClick()
        compose.runOnIdle {
            assertEquals(1, saved)
            state = ProviderDocumentUiState("规则资源", false, ProfileProviderDocument(1, CoreProviderDocument("规则资源", "mrs", "example.com", "missing")), "example.com")
        }
        compose.onNodeWithTag("profile_provider_document_save").assertDoesNotExist()
        compose.onNodeWithText("MRS 规则只读预览").assertIsDisplayed()
        capture("provider_mrs_preview")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng(name, bytes)
    }
}
