package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FilterAndImportPagesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun filterManagementAndPartPresetsWorkInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var state by mutableStateOf(FilterPresetsUiState(listOf(FilterPreset("地区", "HK`JP"), FilterPreset("低延迟", "fast")), loading = false))
        var choosing by mutableStateOf(false)
        var value by mutableStateOf("free`HK")
        compose.setContent { KClashTheme(darkTheme = dark) {
            if (choosing) CompositionLocalProvider(LocalFilterPresets provides state.rows) { FilterPresetChoices(value) { value = it } }
            else FilterPresetsPage(state, {}, { state = state.copy(editing = it, creating = it == null) }, { state = state.copy(editing = null, creating = false) },
                { name, regex -> state = state.copy(rows = state.rows.filterNot { it.label == state.editing?.label } + FilterPreset(name.trim(), regex.trim()), creating = false, editing = null) },
                { rows -> state = state.copy(rows = state.rows - rows.toSet()) }, { labels -> state = state.copy(rows = labels.map { label -> state.rows.first { it.label == label } }) })
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; choosing = false; value = "free`HK"; state = FilterPresetsUiState(listOf(FilterPreset("地区", "HK`JP"), FilterPreset("低延迟", "fast")), loading = false) }
            capture("filter_presets_list_$theme")
            compose.onNodeWithTag("filter_presets_search").performTextInput("JP")
            compose.onNodeWithText("低延迟").assertDoesNotExist()
            compose.onNodeWithText("地区").performClick()
            compose.onNodeWithTag("filter_preset_regex").performTextReplacement("HK`JP`US")
            capture("filter_preset_editor_$theme")
            compose.onNodeWithTag("filter_preset_save").performClick()
            compose.runOnIdle { assertEquals("HK`JP`US", state.rows.last().regex) }
            compose.onNodeWithTag("filter_presets_search").performTextReplacement("")
            compose.onNodeWithTag("filter_presets_sort").performClick()
            compose.onNodeWithTag("group_move_up_1").performClick()
            compose.runOnIdle { assertEquals("地区", state.rows.first().label) }
            compose.onNodeWithTag("filter_presets_select").performClick()
            compose.onNodeWithText("低延迟").performClick()
            compose.onNodeWithContentDescription("删除所选预设").performClick()
            compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
            compose.runOnIdle { assertEquals(1, state.rows.size); choosing = true }
            compose.onNodeWithTag("filter_preset_choice_地区").performClick()
            compose.runOnIdle { assertEquals("free`HK`JP`US", value) }
            compose.onNodeWithTag("filter_preset_choice_地区").assertDoesNotExist()
        }
    }
    @Test fun batchPageShowsPartialResultsRetriesAndValidatesBeforeDownloadInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        val calls = java.util.concurrent.ConcurrentHashMap<String, Int>()
        lateinit var controller: ResourceUrlImportController
        compose.setContent { KClashTheme(darkTheme = dark) {
            val scope = rememberCoroutineScope()
            val imports = remember { ResourceUrlImportController(scope) { input ->
                val call = calls.merge(input.url, 1, Int::plus)!!
                if (input.url.endsWith("b.json") && call == 1) error("download failed")
                if (input.url.endsWith("a.json")) ResourceImportResult.SKIPPED else ResourceImportResult.IMPORTED
            } }
            controller = imports
            val state by imports.state.collectAsState()
            ResourceUrlImportPage("导入图标集", state, {}, imports::start, imports::cancel, imports::retry, imports::reset)
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; controller.reset(); calls.clear() }
            compose.onNodeWithTag("resource_urls_input").performTextReplacement("file:///bad")
            compose.onNodeWithTag("resource_urls_start").performClick()
            compose.runOnIdle { assertTrue(calls.isEmpty()); assertNotNull(controller.state.value.error) }
            compose.onNodeWithTag("resource_urls_input").performTextReplacement("https://example.com/a.json\nhttps://example.com/b.json\nhttps://example.com/c.json")
            capture("resource_urls_form_$theme")
            compose.onNodeWithTag("resource_urls_start").performClick()
            compose.waitUntil(10000) { !controller.state.value.running && controller.state.value.rows.size == 3 }
            capture("resource_urls_partial_$theme")
            compose.onNodeWithTag("resource_urls_retry").performClick()
            compose.waitUntil(10000) { controller.state.value.rows.count { it.status == UrlImportStatus.IMPORTED } == 2 }
            compose.runOnIdle { assertEquals(1, calls["https://example.com/c.json"]); assertEquals(2, calls["https://example.com/b.json"]) }
            capture("resource_urls_complete_$theme")
        }
    }
    @Test fun providerFilterPresetsKeepExplicitClearAndUnsetDistinct() {
        assertPortraitDefaultFontConfiguration()
        var saved: com.lonnnnnng.flclash.service.ProxyProviderOverride? = null
        compose.setContent { KClashTheme {
            CompositionLocalProvider(LocalFilterPresets provides listOf(FilterPreset("地区", "HK`JP"))) {
                ProfileProxyProviderOverrideDialog("cloud", com.lonnnnnng.flclash.service.ProxyProviderOverride(filter = "free`HK", excludeFilter = ""), false, {}, { saved = it })
            }
        } }
        compose.onNodeWithTag("provider_filters_add").performScrollTo().performClick()
        compose.onNodeWithTag("provider_filter_category_FILTER").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").performClick()
        compose.onNodeWithTag("provider_filter_picker_done").performClick()
        compose.onNodeWithTag("provider_filter_row_2").assertTextContains("JP")
        capture("provider_filter_presets")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("free`HK`JP", saved?.filter); assertEquals("", saved?.excludeFilter) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(300)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
