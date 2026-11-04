package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：整组页面验证排序模式、真实按住跨帧滚动、取消保护和图库来源，不把按钮改序代替拖动验收。 */
class ResourceOrderAndIconsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothResourcePagesSortInBothThemesAndRetainOrderOnSaveFailure() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var gallery by mutableStateOf(false)
        var order by mutableStateOf(listOf("a", "b", "c"))
        var failure by mutableStateOf<String?>(null)
        var reject = false
        var dismissed = 0
        val rules = listOf("a", "b", "c").associateWith { RuleResource(it, "规则$it", "", "$it.rules", "domain", "text", 1) }
        val sets = listOf("a", "b", "c").associateWith { ResourceIconSet(it, "图库$it", icons = listOf(ResourceIcon(it, "http://127.0.0.1:9/$it.png")), updatedAt = 1) }
        val save: (List<String>) -> Unit = { if (reject) failure = "保存失败，请重试" else { order = it; failure = null } }
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark, gallery) {
            if (gallery) IconSetsPage(order.map { sets.getValue(it) }, false, failure, 0, { dismissed++ }, { _, _ -> }, {}, { _, _, _ -> }, {}, {}, { _, _ -> }, onReorder = save)
            else RuleResourcesPage(RuleResourcesUiState(rows = order.map { rules.getValue(it) }, loading = false, error = failure),
                { dismissed++ }, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _ -> }, onReorder = save)
        } } }
        for (theme in listOf(false, true)) for (icons in listOf(false, true)) {
            compose.runOnIdle { dark = theme; gallery = icons; order = listOf("a", "b", "c"); failure = null; reject = true }
            val tag = if (icons) "icon_sets" else "rule_resources"
            compose.onNodeWithTag("${tag}_sort").performClick()
            compose.onNodeWithTag("${tag}_add").assertDoesNotExist()
            compose.onNodeWithTag("${tag}_order_a").performOrderTouchMove(1)
            compose.onNodeWithText("保存失败，请重试").assertIsDisplayed()
            compose.runOnIdle { assertEquals(listOf("a", "b", "c"), order); reject = false }
            compose.onNodeWithTag("${tag}_order_a").performOrderTouchMove(1)
            compose.runOnIdle { assertEquals(listOf("b", "a", "c"), order) }
            compose.onNodeWithTag("${tag}_order_b").assertIsDisplayed()
            capture("${tag}_sort_$theme")
            compose.onNodeWithTag("${tag}_back").performClick()
            compose.onNodeWithTag("${tag}_add").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, dismissed) }
        }
    }

    @Test fun viewportDragScrollsWithoutNewMovesCancelsAndCommitsOnlyOnDrop() {
        assertPortraitDefaultFontConfiguration()
        val original = (0..35).map { ResourceOrderItem("$it", "资源 $it", "本地") }
        var rows by mutableStateOf(original)
        var calls = 0
        compose.setContent { KClashTheme { ProfileEditorPage(onDismissRequest = {}, title = { Text("资源排序") }, text = {
            ResourceOrderList(rows, false, "test_resources", { ids -> calls++; rows = ids.map { id -> original.first { it.id == id } } }, Modifier.fillMaxSize())
        }) } }
        val list = compose.onNodeWithTag("test_resources_order_list")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("test_resources_order_0").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        list.performTouchInput { down(Offset(90f, row.center.y - bounds.top)); advanceEventTime(800); moveTo(Offset(90f, bounds.height - 12f), delayMillis = 200) }
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        val first = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        val second = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("按住底边时应持续滚动", second > first)
        compose.runOnIdle { assertEquals(0, calls) }
        list.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, calls); assertEquals(original, rows) }
        list.performScrollToIndex(0)
        val next = compose.onNodeWithTag("test_resources_order_1").fetchSemanticsNode().boundsInRoot
        list.performTouchInput { down(Offset(90f, row.center.y - bounds.top)); advanceEventTime(800); moveBy(Offset(0f, next.center.y - row.center.y), delayMillis = 200); up() }
        compose.runOnIdle { assertEquals(1, calls); assertEquals("1", rows.first().id); assertEquals("0", rows[1].id) }
        compose.onNodeWithTag("test_resources_order_1").assertIsDisplayed()
        capture("resource_order_drag_saved")
    }

    @Test fun iconSourcesRecommendMatchKeepCurrentGalleryAndRemoveOnlyRecentRecord() {
        assertPortraitDefaultFontConfiguration()
        val first = ResourceIconSet("one", "常用图库", icons = listOf(ResourceIcon("YouTube.png", "http://127.0.0.1:9/youtube.png"), ResourceIcon("Other.svg", "http://127.0.0.1:9/other.svg")), updatedAt = 1)
        val second = first.copy(id = "two", name = "另一图库", icons = listOf(ResourceIcon("Music.png", "http://127.0.0.1:9/music.png")))
        val old = "data:image/png;base64,AAAA"
        var recent by mutableStateOf(listOf(old))
        var dark by mutableStateOf(false)
        var selected: String? = null
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark) { CompositionLocalProvider(LocalResourceIconSets provides listOf(first, second)) {
            ProfileGroupIconPicker(second.icons.single().url, "YouTube", recent,
                { url, remove -> if (remove) recent = recent - url }, onDismiss = {}, onSelect = { selected = it })
        } } } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; recent = listOf(old); selected = null }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("group_icon_source_set:two").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("group_icon_source_set:two").assertIsSelected()
            compose.onNodeWithTag("group_icon_choice_0").assertIsDisplayed()
            capture("group_icon_current_gallery_$theme")
            compose.onNodeWithTag("group_icon_source_recommended").performClick()
            compose.onNodeWithText("YouTube").assertIsDisplayed()
            capture("group_icon_recommended_$theme")
            compose.onNodeWithTag("group_icon_search").performTextReplacement("没有")
            compose.onNodeWithText("没有匹配的图标").assertIsDisplayed()
            compose.onNodeWithTag("group_icon_search").performTextClearance()
            compose.onNodeWithTag("group_icon_choice_1").performTouchInput { longClick() }
            compose.onNodeWithText("删除最近图标记录？").assertIsDisplayed()
            compose.onNodeWithText("删除").performClick()
            compose.runOnIdle { assertTrue(recent.isEmpty()); assertNull(selected) }
            compose.onNodeWithTag("group_icon_source_set:one").performClick()
            compose.onNodeWithTag("group_icon_choice_0").performClick()
            compose.runOnIdle { assertEquals(first.icons.first().url, selected) }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
