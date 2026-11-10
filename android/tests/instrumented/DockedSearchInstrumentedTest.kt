package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：直接覆盖生产绑定入口，避免旧绑定组件绿测掩盖正式选择页仍在顶部搜索。 */
class DockedSearchInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun proxyBindingBottomSearchCancelKeepsLayerSelectionAndImeBoundary() = binding(true, false)
    @Test fun ruleBindingBottomSearchCancelKeepsLayerSelectionAndImeBoundary() = binding(false, true)

    private fun binding(proxy: Boolean, dark: Boolean) {
        assertPortraitDefaultFontConfiguration()
        val profiles = (0..63).map { Profile.remote(9000L + it, "", "Resource %02d".format(it)).copy(type = ProfileType.FILE) }
        val rules = (0..63).map { RuleResource("rule-$it", "Resource %02d".format(it), "", "$it.rules", "domain", "yaml", 0) }
        var saves = 0
        var dismissals = 0
        var busy by mutableStateOf(false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KClashTheme(darkTheme = dark) {
            ProviderBindingPicker(proxy, profiles, rules, busy, null, { dismissals++ },
                selectedKey = if (proxy) "9045" else "rule-45", onProxy = { saves++ }, onRule = { saves++ })
        } }
        val selectedTag = if (proxy) "provider_binding_proxy_9045" else "provider_binding_rule_rule-45"
        val lastTag = if (proxy) "provider_binding_proxy_9063" else "provider_binding_rule_rule-63"
        compose.onNodeWithTag(selectedTag).assertIsDisplayed()
        val field = compose.onNodeWithTag("provider_binding_search")
        assertTrue(field.fetchSemanticsNode().boundsInWindow.top > compose.onNodeWithTag(selectedTag).fetchSemanticsNode().boundsInWindow.bottom)
        field.performClick().performTextReplacement("Resource 63")
        compose.waitUntil(5_000) { imeTop() != null }
        val dock = compose.onNodeWithTag("provider_binding_search_dock").fetchSemanticsNode().boundsInWindow
        assertTrue(dock.bottom <= checkNotNull(imeTop()) + 1)
        assertTrue(compose.onNodeWithTag(lastTag).fetchSemanticsNode().boundsInWindow.bottom <= dock.top)
        capture("dock_${proxy}_ime")
        compose.onNodeWithTag("provider_binding_search_clear").performClick()
        assertEquals("", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertNotNull(imeTop())
        field.performTextReplacement("Resource 63")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("provider_binding_search").assertTextContains("Resource 63")
        compose.onNodeWithTag("provider_binding_search_cancel").performClick()
        compose.waitUntil(5_000) { imeHidden() }
        compose.onNodeWithTag("provider_binding_back").assertIsDisplayed()
        assertEquals("", compose.onNodeWithTag("provider_binding_search").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(0, saves); assertEquals(0, dismissals)
        capture("dock_${proxy}_cancel")
        compose.onNodeWithTag("provider_binding_search").performTextReplacement("Resource 63")
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("provider_binding_search_cancel").assertIsNotEnabled()
        compose.onNodeWithTag(lastTag).assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag(lastTag).performClick()
        assertEquals(1, saves)
    }

    private fun imeTop(): Int? {
        var result: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(ViewCompat::getRootWindowInsets)
            if (view != null && insets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                result = view.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
        return result
    }

    private fun imeHidden(): Boolean {
        var hidden = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            hidden = view?.let(ViewCompat::getRootWindowInsets)?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        return hidden
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
