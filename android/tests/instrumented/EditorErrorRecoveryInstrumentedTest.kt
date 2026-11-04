package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.CoreProviderDocument
import com.lonnnnnng.flclash.data.FilterPreset
import com.lonnnnnng.flclash.service.ProfileProviderDocument
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：隔离页面回调验收失败草稿、错误可见性和单次选择，避免异常提示被光标移动或重复点击擦掉。 */
class EditorErrorRecoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun scriptSelectionAndLongSearchPreserveFailureUntilTextChanges() {
        val text = (1..400).joinToString("\n") { "// 第 $it 行" } + "\nreturn config;"
        var state by mutableStateOf(ScriptEditorUiState(isOpen = true, label = "脚本", content = text, error = "保存失败，请重试"))
        var changes = 0
        compose.setContent { KClashTheme {
            ScriptEditorDialog(state, {}, { _, _ -> }, { label, content -> changes++; state = state.copy(label = label, content = content, error = null) })
        } }
        val field = compose.onNodeWithTag("script_content")
        field.performTextInputSelection(TextRange(2))
        compose.onNodeWithTag("script_editor_error").assertIsDisplayed()
        field.assertIsDisplayed()
        assertEquals(0, changes)
        compose.onNodeWithTag("script_find").performClick()
        compose.onNodeWithTag("script_query").performTextInput("return config;")
        compose.onNodeWithTag("script_find_next").performClick()
        compose.waitForIdle()
        assertEquals(TextRange(text.length - 14, text.length), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        assertTrue(compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().any {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) && it.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0
        })
        assertEquals(0, changes)
        capture("script_long_match_error")
        compose.onNodeWithTag("script_find").performClick()
        field.performTextReplacement("const main = config => config;")
        compose.onNodeWithTag("script_editor_error").assertDoesNotExist()
        assertEquals(1, changes)
    }

    @Test fun providerSelectionKeepsFailureAndCommittedStateOnlyAllowsReadback() {
        val document = ProfileProviderDocument(1, CoreProviderDocument("规则", "text", "example.com", "missing"))
        var state by mutableStateOf(ProviderDocumentUiState("规则", false, document, "example.net", true, error = "保存失败，请重试"))
        var changes = 0; var saves = 0; var retries = 0
        compose.setContent { KClashTheme {
            ProfileProviderDocumentPage(state, {}, { changes++ }, { saves++ }, { retries++ })
        } }
        val field = compose.onNodeWithTag("profile_provider_document_content")
        field.assertIsDisplayed()
        field.performTextInputSelection(TextRange(3))
        assertEquals(0, changes)
        compose.onNodeWithTag("profile_provider_document_error").assertIsDisplayed()
        compose.runOnIdle { state = ProviderDocumentUiState("规则", false, document, "example.net", true,
            error = "Provider 已提交，但回读确认失败，请重新读取确认", committed = true) }
        field.assertIsNotEnabled()
        compose.onNodeWithTag("profile_provider_document_save").assertDoesNotExist()
        compose.onNodeWithText("重新读取确认").performClick()
        assertEquals(1, retries); assertEquals(0, saves)
        capture("provider_committed_readback")
    }

    @Test fun providerOptionsKeepRadioSelectionAndFilterToggleState() {
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalFilterPresets provides listOf(FilterPreset("地区", "HK"))) {
            ProviderOptionsPage("cloud", ProxyProviderOverride(interval = 300, skipCertVerify = false, ipVersion = "dual"), false, null, {}, {})
        } } }
        compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("provider_option_SKIP_CERT").performScrollTo().assertIsOff()
        compose.onNodeWithTag("provider_option_SKIP_CERT").assertIsDisplayed()
        compose.onNodeWithTag("provider_option_IP_VERSION").performScrollTo().performClick()
        compose.onNodeWithTag("provider_ip_dual").assertIsSelected()
        compose.onNodeWithTag("provider_ip_ipv4").assertIsNotSelected().performClick()
        compose.onNodeWithTag("provider_filters_add").performScrollTo().performClick()
        compose.onNodeWithTag("provider_filter_category_FILTER").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").assertIsOff().performClick().assertIsOn()
        capture("provider_filter_toggle")
    }

    @Test fun filterPresetBatchSelectionTogglesOnceAndKeepsFailureVisible() {
        val state = FilterPresetsUiState(rows = listOf(FilterPreset("地区", "HK")), loading = false, error = "保存失败，请重试")
        compose.setContent { KClashTheme { FilterPresetsPage(state, {}, {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("保存失败，请重试").assertIsDisplayed()
        compose.onNodeWithTag("filter_presets_select").performClick()
        compose.onNodeWithTag("filter_preset_row_地区").assertIsOff().assertTextContains("地区").performClick().assertIsOn()
        capture("filter_batch_selected")
    }

    @Test fun ruleResourceSaveFailureKeepsLocalBodyAndOffersUnsavedRecovery() {
        var state by mutableStateOf(RuleResourcesUiState(loading = false, editor = RuleResourceDraft(label = "规则", content = "example.com")))
        var saves = 0
        compose.setContent { KClashTheme {
            RuleResourcesPage(state, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _ ->
                saves++; state = state.copy(error = "操作失败，请检查资源正文；原资源已保留")
            })
        } }
        compose.onNodeWithTag("rule_resource_content").performScrollTo().performTextReplacement("example.net")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("rule_resource_save").performClick()
        compose.onNodeWithTag("rule_resource_editor_error").assertIsDisplayed()
        compose.onNodeWithTag("rule_resource_content").performScrollTo().assertTextContains("example.net")
        assertEquals(1, saves)
        compose.onNodeWithTag("rule_resource_editor_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("rule_resource_content").performScrollTo().assertTextContains("example.net")
        capture("rule_resource_failure_draft")
    }

    @Test fun intranetAndServiceFailuresRemainVisibleAndRetryOnce() {
        var retried = 0
        compose.setContent { KClashTheme { Column {
            DashboardIntranetIpCard(IntranetIpUiState(settingsLoading = false, error = "共享保存失败", feedback = "地址已复制"), {}, {}, { retried++ })
            DashboardServiceStatusCard(ServiceStatusUiState(settingsLoading = false, error = "服务设置读取失败"), {}, { _, _ -> }, { _, _ -> }, {}, {})
        } } }
        compose.onNodeWithTag("intranet_error").assertIsDisplayed()
        compose.onNodeWithTag("intranet_feedback").assertIsDisplayed()
        compose.onNodeWithTag("intranet_allow_lan").assertIsToggleable()
        compose.onNodeWithTag("intranet_retry").performClick()
        assertEquals(1, retried)
        compose.onNodeWithText("服务设置读取失败").assertIsDisplayed()
        capture("dashboard_panel_visible_errors")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
