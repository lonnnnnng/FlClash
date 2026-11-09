package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：长目录必须直接呈现已有选择，并在真实键盘上方完成搜索/清空/末项选择，防止只验短夹具漏掉遮挡。 */
@SdkSuppress(minSdkVersion = 29)
class ResourcePickerLongListInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun proxyPickerRevealsCurrentChoiceAndHandlesLongDirectoryAboveIme() {
        assertPortraitDefaultFontConfiguration()
        val rows = (0 until 64).map { Profile.remote(9000L + it, "", "目录配置%02d".format(it)).copy(type = ProfileType.FILE) }
        val initial = ProfileProxyResources.bind("", rows[45], "保留别名")
        var section by mutableStateOf(initial)
        var changes = 0
        compose.setContent { KClashTheme { ProfileEditorPage({}, { Text("资源入口") }, { Column {
            ProfileProxyResourceBindings(section, "", rows, true, { section = it; changes++ })
        } }) } }
        compose.onNodeWithText("选择").performClick()
        compose.onNodeWithTag("proxy_resource_pick_9045").assertIsDisplayed()
        capture("long_proxy_selected")
        compose.onNodeWithTag("proxy_resource_search").performClick().performTextReplacement("missing-resource")
        awaitIme()
        aboveIme(compose.onNodeWithTag("proxy_resource_clear_search"))
        capture("long_proxy_empty_ime")
        compose.onNodeWithTag("proxy_resource_clear_search").performClick()
        // 作者 long：清空只检查真实输入，搜索占位文字仍应可见，不能把占位文案误判为残留查询。
        assertEquals("", compose.onNodeWithTag("proxy_resource_search").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(63)
        aboveIme(compose.onNodeWithTag("proxy_resource_pick_9063"))
        capture("long_proxy_last_ime")
        compose.runOnIdle { assertEquals(initial, section); assertEquals(0, changes) }
        compose.onNodeWithTag("proxy_resource_pick_9063").performClick()
        compose.onNodeWithTag("proxy_resource_picker_back").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, changes)
            assertEquals(rows.last().id, ProfileProxyResources.idOf(ProfileProxyResources.definitions(section)["保留别名"]))
        }
        compose.onNodeWithText("选择").performClick()
        compose.onNodeWithTag("proxy_resource_pick_9063").assertIsDisplayed()
        compose.onNodeWithTag("proxy_resource_picker_back").performClick()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun rulePickerRevealsCurrentChoiceAndHandlesLongDirectoryAboveIme() {
        assertPortraitDefaultFontConfiguration()
        val rows = (0 until 64).map { RuleResource("long-rule-$it", "目录规则%02d".format(it), "", "long-$it.rules", "domain", "yaml", 1) }
        val initial = RuleResources.bind("", rows[45], "保留规则别名")
        var section by mutableStateOf(initial)
        var changes = 0
        compose.setContent { KClashTheme { ProfileEditorPage({}, { Text("资源入口") }, { Column {
            RuleResourceBindings(section, "", rows, true, { section = it; changes++ }, {})
        } }) } }
        compose.onNodeWithText("选择").performClick()
        compose.onNodeWithText(rows[45].label).assertIsDisplayed()
        capture("long_rule_selected")
        compose.onNodeWithTag("rule_resource_binding_search").performClick().performTextReplacement("missing-resource")
        awaitIme()
        aboveIme(compose.onNodeWithTag("rule_resource_binding_clear"))
        capture("long_rule_empty_ime")
        compose.onNodeWithTag("rule_resource_binding_clear").performClick()
        assertEquals("", compose.onNodeWithTag("rule_resource_binding_search").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(63)
        aboveIme(compose.onNodeWithText(rows.last().label))
        capture("long_rule_last_ime")
        compose.runOnIdle { assertEquals(initial, section); assertEquals(0, changes) }
        compose.onNodeWithText(rows.last().label).performClick()
        compose.onNodeWithTag("resource_picker_back").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, changes); assertTrue(section.contains("kclash-resource:long-rule-63")); assertTrue(section.contains("保留规则别名")) }
        compose.onNodeWithText("选择").performClick()
        compose.onNodeWithText(rows.last().label).assertIsDisplayed()
        compose.onNodeWithTag("resource_picker_back").performClick()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    private fun imeTop(): Int? {
        var top: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(ViewCompat::getRootWindowInsets)
            if (view != null && insets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                top = view.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
        return top
    }

    private fun awaitIme() = compose.waitUntil(5_000) { imeTop() != null }

    private fun aboveIme(node: SemanticsNodeInteraction) {
        compose.waitForIdle()
        val top = checkNotNull(imeTop()) { "必须实际显示键盘后验收遮挡" }
        val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue("条目或恢复操作必须完整位于键盘上方：$bounds / $top", bounds.bottom <= top)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(650)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
