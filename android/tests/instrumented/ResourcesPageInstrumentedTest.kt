package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.GeoResourceSettings
import com.lonnnnnng.flclash.service.GeoResourceType
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ResourcesPageInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun overviewSettingsMenusAndFailureSummaryWorkInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var state by mutableStateOf(ResourceUpdatesState(loading = false, rows = GeoResourceType.entries.map {
            ResourceUpdateRow(ResourceKind.GEO, it.name, it.name, it.defaultUrl, size = 1234567)
        } + listOf(ResourceUpdateRow(ResourceKind.ICONS, "gallery", "远程图标集", "https://example.com/icons.json", count = 128),
            ResourceUpdateRow(ResourceKind.RULE, "rules", "远程规则", "https://example.com/rules.txt", size = 24680),
            ResourceUpdateRow(ResourceKind.SCRIPT, "script", "远程脚本", "https://example.com/script.js", size = 1234))))
        var updated = ""
        compose.setContent { KClashTheme(darkTheme = dark) {
            ResourcesPage(state, {}, {}, { updated = it.id }, {}, { state = state.copy(settings = it) }, { state = state.copy(failures = emptyList()) })
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; state = state.copy(settings = GeoResourceSettings()) }
            compose.onNodeWithTag("resources_page").performScrollToIndex(0)
            capture("resources_overview_$theme")
            compose.onNodeWithTag("resources_auto_update").performClick()
            compose.onNodeWithText("资源更新周期").performClick()
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("0")
            compose.onNodeWithTag("resource_setting_save").performClick()
            compose.onNodeWithText("更新周期须为 1 至 87600 小时").assertIsDisplayed()
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("48")
            compose.onNodeWithTag("resource_setting_save").performClick()
            compose.runOnIdle { assertEquals(48, state.settings.intervalHours) }
            compose.onNodeWithTag("resource_GEO:MMDB").performClick()
            compose.onNodeWithTag("resource_setting_input").performTextReplacement("file:///bad")
            compose.onNodeWithTag("resource_setting_save").performClick()
            capture("resources_geo_validation_$theme")
            compose.onNodeWithTag("resource_setting_reset").performClick()
            compose.onNodeWithTag("resource_setting_save").performClick()
            compose.runOnIdle { assertEquals(GeoResourceType.MMDB.defaultUrl, state.settings.urls[GeoResourceType.MMDB]) }
            compose.onNodeWithTag("resource_GEO:MMDB").performTouchInput { longClick() }
            compose.onNodeWithText("同步").performClick()
            compose.runOnIdle {
                assertEquals("MMDB", updated)
                state = state.copy(failures = listOf(ResourceUpdateFailure("MMDB", "更新失败，请检查网络后重试")))
            }
            capture("resources_failure_$theme")
            compose.onNodeWithText("关闭提示").performClick()
            compose.onNodeWithTag("resources_failures").assertDoesNotExist()
            for ((kind, id) in listOf(ResourceKind.ICONS to "gallery", ResourceKind.RULE to "rules", ResourceKind.SCRIPT to "script")) {
                val tag = "resource_${kind.name}:$id"
                compose.onNodeWithTag("resources_page").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performTouchInput { longClick() }
                compose.onNodeWithText("同步").performClick()
                compose.runOnIdle { assertEquals(id, updated) }
            }
            capture("resources_remote_sections_$theme")
        }
    }

    @Test fun iconSetPreviewAndGroupPickerUseGalleryNamesAndSelection() {
        assertPortraitDefaultFontConfiguration()
        val icon = ResourceIcon("图库节点图标", "http://127.0.0.1:9/preview.png")
        val set = ResourceIconSet("gallery", "本地图标集", icons = listOf(icon), updatedAt = 1)
        var showPicker by mutableStateOf(false)
        var dark by mutableStateOf(false)
        var selected = ""
        compose.setContent { KClashTheme(darkTheme = dark) {
            if (!showPicker) IconSetsPage(listOf(set), false, null, 0, {}, { _, _ -> }, {}, { _, _, _ -> }, {}, {}, { _, _ -> })
            else CompositionLocalProvider(LocalResourceIcons provides listOf(icon)) {
                ProfileGroupIconPicker("", "节点", emptyList(), { _, _ -> }, onDismiss = {}, onSelect = { selected = it })
            }
        } }
        for (theme in listOf(false, true)) {
        compose.runOnIdle { dark = theme; showPicker = false }
        capture("icon_set_list_$theme")
        compose.onNodeWithTag("icon_set_gallery").performClick()
        compose.onNodeWithText("图库节点图标").assertIsDisplayed()
        capture("icon_set_preview_$theme")
        compose.onNodeWithTag("icon_set_search").performTextInput("没有")
        compose.onNodeWithText("没有匹配的图标").assertIsDisplayed()
        compose.onNodeWithTag("icon_set_preview_back").performClick()
        compose.runOnIdle { showPicker = true }
        compose.onNodeWithTag("group_icon_search").performTextInput("图库节点")
        compose.onNodeWithText("图库节点图标").assertIsDisplayed()
        capture("icon_set_group_picker_$theme")
        compose.onNodeWithTag("group_icon_choice_0").performClick()
        compose.runOnIdle { assertEquals(icon.url, selected) }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
