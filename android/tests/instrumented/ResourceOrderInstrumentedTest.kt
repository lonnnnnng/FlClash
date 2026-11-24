package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：规则资源排序覆盖保存失败后的草稿保持、跨帧拖动、取消和松手提交。 */
class ResourceOrderInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ruleResourcesSortInBothThemesAndRetainOrderOnSaveFailure() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var order by mutableStateOf(listOf("a", "b", "c"))
        var failure by mutableStateOf<String?>(null)
        var reject = false
        var dismissed = 0
        val rules = listOf("a", "b", "c").associateWith { RuleResource(it, "规则$it", "", "$it.rules", "domain", "text", 1) }
        val save: (List<String>) -> Unit = { if (reject) failure = "保存失败，请重试" else { order = it; failure = null } }
        compose.setContent { KClashTheme(darkTheme = dark) {
            RuleResourcesPage(RuleResourcesUiState(rows = order.map { rules.getValue(it) }, loading = false, error = failure),
                { dismissed++ }, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _ -> }, onReorder = save)
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; order = listOf("a", "b", "c"); failure = null; reject = true }
            compose.onNodeWithTag("rule_resources_sort").performClick()
            compose.onNodeWithTag("rule_resources_add").assertDoesNotExist()
            compose.onNodeWithTag("rule_resources_order_a").performOrderTouchMove(1)
            compose.onNodeWithText("保存失败，请重试").assertIsDisplayed()
            compose.runOnIdle { assertEquals(listOf("a", "b", "c"), order); reject = false }
            compose.onNodeWithTag("rule_resources_order_a").performOrderTouchMove(1)
            compose.runOnIdle { assertEquals(listOf("b", "a", "c"), order) }
            compose.onNodeWithTag("rule_resources_order_b").assertIsDisplayed()
            capture("rule_resources_sort_$theme")
            compose.onNodeWithTag("rule_resources_back").performClick()
            compose.onNodeWithTag("rule_resources_add").assertIsDisplayed()
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

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
