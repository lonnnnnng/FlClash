package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.common.AppText
import com.lonnnnnng.flclash.service.GeoResourceSettings
import com.lonnnnnng.flclash.service.GeoResourceType
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：工具资源总览只展示核心更新、规则资源和脚本；管理入口分别位于高级配置。 */
class ResourcesPageInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overviewSettingsMenusAndFailureSummaryWorkInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        AppText.useEnglish = false
        UiLanguage.english = false
        var dark by mutableStateOf(false)
        var state by mutableStateOf(ResourceUpdatesState(loading = false, rows = GeoResourceType.entries.map {
            ResourceUpdateRow(ResourceKind.GEO, it.name, it.name, it.defaultUrl, size = 1234567)
        } + listOf(ResourceUpdateRow(ResourceKind.RULE, "rules", "远程规则", "https://example.com/rules.txt", size = 24680),
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
                state = state.copy(failures = listOf(ResourceUpdateFailure("MMDB", "更新失败，请检查网络后重试", "GEO:MMDB")))
            }
            compose.onNodeWithTag("resource_error_GEO:MMDB").assertIsDisplayed()
            compose.onNodeWithTag("resources_failures").assertDoesNotExist()
            capture("resources_failure_$theme")
            compose.runOnIdle { state = state.copy(failures = emptyList()) }
            for ((kind, id) in listOf(ResourceKind.RULE to "rules", ResourceKind.SCRIPT to "script")) {
                val tag = "resource_${kind.name}:$id"
                compose.onNodeWithTag("resources_page").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performTouchInput { longClick() }
                compose.onNodeWithText("同步").performClick()
                compose.runOnIdle { assertEquals(id, updated) }
            }
            capture("resources_remote_sections_$theme")
        }
    }

    @Test fun resourceMenuDefersEditUntilExitAndLoadingKeepsRowHeight() {
        var state by mutableStateOf(ResourceUpdatesState(loading = false, rows = GeoResourceType.entries.map {
            ResourceUpdateRow(ResourceKind.GEO, it.name, it.name, it.defaultUrl, size = 1234567)
        }))
        compose.setContent { KClashTheme { ResourcesPage(state, {}, {}, {}, {}, {}, {}) } }
        val row = compose.onNodeWithTag("resource_GEO:MMDB")
        val baseline = row.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(updating = setOf("GEO:MMDB")) }
        assertEquals(baseline.height, row.fetchSemanticsNode().boundsInRoot.height, 1f)
        row.performTouchInput { longClick() }
        compose.onNodeWithText("同步").assertDoesNotExist()
        compose.onNodeWithText("编辑").assertDoesNotExist()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil { compose.onAllNodesWithTag("app_popup_window").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { state = state.copy(updating = emptySet()) }
        row.performTouchInput { longClick() }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("编辑").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("app_popup_window").assertExists()
        compose.onNodeWithTag("resource_setting_dialog").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitUntil { compose.onAllNodesWithTag("resource_setting_dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app_popup_window").assertDoesNotExist()
        compose.onNodeWithTag("resource_setting_cancel").performClick()
    }

    @Test fun resourceTransferShowsByteProgressAndKeepsFailureOnItsOwnRow() {
        val rows = GeoResourceType.entries.map { ResourceUpdateRow(ResourceKind.GEO, it.name, it.name, it.defaultUrl, size = 1234567) }
        var state by mutableStateOf(ResourceUpdatesState(loading = false, rows = rows,
            updating = setOf("GEO:MMDB"), transfers = mapOf("GEO:MMDB" to ResourceDownloadProgress(512 * 1024, 2 * 1024 * 1024)),
            failures = listOf(ResourceUpdateFailure("ASN", "该行的失败原因", "GEO:ASN"))))
        compose.setContent { KClashTheme { ResourcesPage(state, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("已下载", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("resource_progress_GEO:MMDB").assertExists()
        compose.onNodeWithTag("resource_error_GEO:ASN").assertIsDisplayed()
        compose.onNodeWithTag("resource_error_GEO:MMDB").assertDoesNotExist()
        compose.onNodeWithTag("resources_failures").assertDoesNotExist()
        capture("resource_download_progress")
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
