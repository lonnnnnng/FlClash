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
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("resource_setting_dialog").fetchSemanticsNodes().isEmpty() }
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
        compose.onNodeWithTag("icon_set_preview_search").performClick()
        compose.onNodeWithTag("icon_set_preview_search").performTextInput("没有")
        compose.onNodeWithTag("icon_set_preview_empty").assertTextEquals("没有匹配的图标")
        capture("icon_set_preview_no_results_$theme")
        compose.onNodeWithTag("icon_set_search_clear").performClick()
        compose.onNodeWithText("图库节点图标").assertIsDisplayed()
        compose.onNodeWithTag("icon_set_preview_search_cancel").performClick()
        compose.onNodeWithTag("icon_set_preview_back").performClick()
        compose.runOnIdle { showPicker = true }
        compose.onNodeWithTag("group_icon_search").performTextInput("图库节点")
        compose.onNodeWithText("图库节点图标").assertIsDisplayed()
        capture("icon_set_group_picker_$theme")
        compose.onNodeWithTag("group_icon_choice_0").performClick()
        compose.runOnIdle { assertEquals(icon.url, selected) }
        compose.onNodeWithTag("group_icon_search").performImeAction()
        }
    }

    @Test fun iconSetDockedSearchBackRestoresNonEmptyGridPosition() {
        assertPortraitDefaultFontConfiguration()
        // 作者 long：八十项确保目标行超出 POCO 当前可视高度，避免所有条目同屏时把“仍可见”误判为滚动恢复。
        val icons = (0..79).map { ResourceIcon("图标 $it", "") }
        val set = ResourceIconSet("gallery", "本地图标集", icons = icons, updatedAt = 1)
        compose.setContent { KClashTheme {
            IconSetsPage(listOf(set), false, null, 0, {}, { _, _ -> }, {}, { _, _, _ -> }, {}, {}, { _, _ -> })
        } }
        compose.onNodeWithTag("icon_set_gallery").performClick()
        compose.onNodeWithTag("icon_set_grid").performScrollToNode(hasTestTag("icon_set_icon_图标 60"))
        compose.waitForIdle()
        compose.onNodeWithTag("icon_set_icon_图标 60").assertIsDisplayed()
        compose.onNodeWithTag("icon_set_icon_图标 0").assertDoesNotExist()
        val anchorBounds = compose.onNodeWithTag("icon_set_icon_图标 60").fetchSemanticsNode().boundsInRoot
        val anchorTop = anchorBounds.top
        val firstRow = compose.onNodeWithTag("icon_set_icon_图标 60").fetchSemanticsNode().boundsInRoot.top
        val fifthColumn = compose.onNodeWithTag("icon_set_icon_图标 64").fetchSemanticsNode().boundsInRoot.top
        val nextRow = compose.onNodeWithTag("icon_set_icon_图标 65").fetchSemanticsNode().boundsInRoot.top
        assertEquals(anchorTop, firstRow, 1f)
        assertEquals(firstRow, fifthColumn, 1f)
        assertTrue(nextRow > firstRow)
        capture("icon_set_preview_scrolled")

        compose.onNodeWithTag("icon_set_preview_search").performClick()
        compose.onNodeWithTag("icon_set_preview_search").performTextInput("37")
        compose.onNodeWithTag("icon_set_preview_search").performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag("icon_set_icon_图标 37").assertIsDisplayed()
        compose.onNodeWithTag("icon_set_icon_图标 38").assertDoesNotExist()
        compose.onNodeWithTag("icon_set_icon_图标 60").assertDoesNotExist()
        val resultBounds = compose.onNodeWithTag("icon_set_icon_图标 37").fetchSemanticsNode().boundsInRoot
        val dockBounds = compose.onNodeWithTag("icon_set_preview_search_dock").fetchSemanticsNode().boundsInRoot
        assertEquals("过滤结果应回到网格首列", anchorBounds.left, resultBounds.left, 1f)
        assertTrue("过滤结果应显示在停靠搜索框上方", resultBounds.bottom <= dockBounds.top)
        capture("icon_set_preview_docked_search")

        compose.onNodeWithTag("icon_set_preview_search_cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("icon_set_icon_图标 60").assertIsDisplayed()
        compose.onNodeWithTag("icon_set_icon_图标 0").assertDoesNotExist()
        val restoredTop = compose.onNodeWithTag("icon_set_icon_图标 60").fetchSemanticsNode().boundsInRoot.top
        assertEquals("清除搜索后应恢复查询前的网格位置", anchorTop, restoredTop, 1f)
        capture("icon_set_preview_position_restored")
        compose.onNodeWithTag("icon_set_preview_back").performClick()
        compose.onNodeWithTag("icon_set_gallery").assertIsDisplayed()
    }

    @Test fun iconSetNonEmptyGalleryMatchesFlutterFiveColumnGrid() {
        assertPortraitDefaultFontConfiguration()
        val icons = (0..39).map { ResourceIcon("图标 $it", "") }
        val set = ResourceIconSet("gallery", "本地图标集", icons = icons, updatedAt = 1)
        compose.setContent { KClashTheme(darkTheme = true) {
            IconSetsPage(listOf(set), false, null, 0, {}, { _, _ -> }, {}, { _, _, _ -> }, {}, {}, { _, _ -> })
        } }
        compose.onNodeWithTag("icon_set_gallery").performClick()
        compose.onNodeWithTag("icon_set_icon_图标 0").assertIsDisplayed()
        compose.onNodeWithTag("icon_set_icon_图标 39").assertIsDisplayed()
        val firstRow = compose.onNodeWithTag("icon_set_icon_图标 0").fetchSemanticsNode().boundsInRoot.top
        val fifthColumn = compose.onNodeWithTag("icon_set_icon_图标 4").fetchSemanticsNode().boundsInRoot.top
        val nextRow = compose.onNodeWithTag("icon_set_icon_图标 5").fetchSemanticsNode().boundsInRoot.top
        assertEquals(firstRow, fifthColumn, 1f)
        assertTrue(nextRow > firstRow)
        capture("icon_set_preview_nonempty_grid")
        compose.onNodeWithTag("icon_set_preview_back").performClick()
    }

    @Test fun emptyIconSetPageUsesFlutterIllustrationAndCenteredLabel() {
        assertPortraitDefaultFontConfiguration()
        compose.setContent { KClashTheme(darkTheme = true) {
            IconSetsPage(emptyList(), false, null, 0, {}, { _, _ -> }, {}, { _, _, _ -> }, {}, {}, { _, _ -> })
        } }
        compose.onNodeWithTag("icon_sets_empty_illustration").assertIsDisplayed()
        compose.onNodeWithTag("icon_sets_empty_label").assertTextEquals("暂无图标集")
        compose.waitForIdle()
        android.os.SystemClock.sleep(1_050)
        capture("icon_sets_empty_dark_flutter_reference")
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
