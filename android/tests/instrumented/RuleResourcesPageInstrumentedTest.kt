package com.lonnnnnng.flclash.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.RuleResource
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RuleResourcesPageInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val resource = RuleResource("page-rule", "常用规则", "", "page-file.rules", "domain", "yaml", 1)
    @Test fun completeResourcePageSearchMenusDraftAndDeleteWorkInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var state by mutableStateOf(RuleResourcesUiState(rows = listOf(resource), loading = false))
        var saved = ""
        compose.setContent { KClashTheme(darkTheme = dark) {
            RuleResourcesPage(state, {}, { remote -> state = state.copy(editor = RuleResourceDraft(url = if (remote) "https://" else "")) }, {},
                { row, options -> state = state.copy(editor = RuleResourceDraft(row, row.label, row.url, "payload: [example.com]", options = options)) }, {}, {},
                { row -> state = state.copy(deleting = row, users = listOf("引用配置")) }, { state = state.copy(deleting = null) }, { state = state.copy(deleting = null) }, { _, _ -> },
                { state = state.copy(editor = null) }, { label, _, _ -> saved = label; state = state.copy(editor = null) })
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            capture("rule_resources_list_" + theme)
            compose.onNodeWithTag("rule_resources_search").performTextInput("不存在")
            compose.onNodeWithText("没有匹配的结果").assertIsDisplayed()
            capture("rule_resources_no_results_" + theme)
            compose.onNodeWithContentDescription("清除搜索").performClick()
            compose.onNodeWithTag("rule_resources_add").performClick()
            compose.onNodeWithText("从头开始").performClick()
            compose.onNodeWithTag("rule_resource_label").performTextInput("新增规则")
            compose.onNodeWithTag("rule_resource_content").performTextInput("payload: [example.org]")
            capture("rule_resources_editor_" + theme)
            compose.onNodeWithTag("rule_resource_editor_back").performClick()
            compose.onNodeWithText("放弃修改？").assertIsDisplayed()
            compose.onNodeWithText("继续编辑").performClick()
            compose.onNodeWithTag("rule_resource_save").performClick()
            compose.runOnIdle { assertEquals("新增规则", saved) }
            compose.onNodeWithContentDescription("资源操作 · 常用规则").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("以下配置仍引用此资源：引用配置。删除后引用将显示缺失，可重新选择资源。").assertIsDisplayed()
            capture("rule_resources_delete_" + theme)
            compose.onNodeWithText("取消").performClick()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
    @Test fun missingReferenceCanBindAnExistingGlobalResourceWithoutRenamingRules() {
        assertPortraitDefaultFontConfiguration()
        var section by mutableStateOf("")
        compose.setContent { KClashTheme { ProfileEditorPage(onDismissRequest = {}, title = { Text("配置资源") }, text = { androidx.compose.foundation.layout.Column { RuleResourceBindings(section, "rules: ['RULE-SET,缺失规则,DIRECT']", listOf(resource), true, { section = it }, {}) } }) } }
        compose.onNodeWithText("资源缺失，请重新选择").assertIsDisplayed()
        capture("rule_resources_missing_reference")
        compose.onNodeWithText("选择").performClick()
        capture("rule_resources_choose")
        compose.onNodeWithText("常用规则").performClick()
        compose.runOnIdle { assertTrue(section.contains("缺失规则")); assertTrue(section.contains("kclash-resource:page-rule")) }
        compose.onNodeWithText("全局资源 · 常用规则").assertIsDisplayed()
    }
}
