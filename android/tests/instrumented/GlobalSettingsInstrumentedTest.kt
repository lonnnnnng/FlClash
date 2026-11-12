package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GlobalSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dnsSaveShowsProgressUntilCompletionCallback() {
        var saving by mutableStateOf(false)
        var settings by mutableStateOf(ProfileDnsOverrides(nameserver = listOf("1.1.1.1")))
        var completion: ((Boolean) -> Unit)? = null
        compose.setContent { KClashTheme {
            ProfileEditorPage({}, { Text("DNS") }, {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GlobalDnsSettingsPanel(settings, saving, saving = saving) { value, done ->
                        settings = value
                        saving = true
                        completion = done
                    }
                }
            })
        } }
        compose.onNodeWithTag("global_dns_nameserver").performClick()
        compose.onNodeWithTag("global_dns_input").performTextReplacement("9.9.9.9")
        compose.onNodeWithTag("global_dns_save").performClick()
        compose.onNodeWithTag("global_dns_editor_saving").assertIsDisplayed()
        compose.onNodeWithTag("global_dns_editor").assertExists()
        compose.runOnIdle { completion!!.invoke(true); saving = false }
        compose.waitUntil { compose.onAllNodesWithTag("global_dns_editor").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("global_dns_saving").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("9.9.9.9"), settings.nameserver) }
    }

    @Test fun dnsExplicitEmptyFailureRetryRemovalAndBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var settings by mutableStateOf(ProfileDnsOverrides(nameserver = listOf("1.1.1.1"), ipv6 = false))
        var fail = true
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) { ProfileEditorPage({}, { Text("DNS") }, {
            Column(Modifier.verticalScroll(rememberScrollState())) { GlobalDnsSettingsPanel(settings, false) { value, done ->
                if (!fail) settings = value
                done(!fail)
            } }
        }) } }
        capture("global_dns_light")
        compose.onNodeWithTag("global_dns_nameserver").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_input").performTextReplacement("")
        compose.onNodeWithTag("global_dns_save").performClick()
        compose.onNodeWithTag("global_dns_error").assertTextContains("输入已保留", substring = true)
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("global_dns_save").performClick()
        compose.onNodeWithTag("global_dns_editor").assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), settings.nameserver) }
        compose.onNodeWithTag("global_dns_add").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_add_fake-ip-filter-mode").performClick()
        compose.onNodeWithTag("global_dns_option_whitelist").performClick()
        compose.runOnIdle { assertEquals("whitelist", settings.fakeIpFilterMode); dark = true }
        capture("global_dns_dark")
        compose.onNodeWithTag("global_dns_remove_nameserver").performScrollTo().performClick()
        compose.runOnIdle { assertNull(settings.nameserver); assertEquals(false, settings.ipv6) }
        compose.onNodeWithTag("global_dns_add").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_add_listen").performClick()
        compose.onNodeWithTag("global_dns_save").performClick()
        compose.runOnIdle { assertEquals("", settings.listen) }
    }

    @Test fun globalRulesSearchPresetSortSelectDeleteAndBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(GlobalRulesUiState(rules = listOf(ExtensionRule("one", "DOMAIN,one.test,DIRECT"),
            ExtensionRule("two", "DOMAIN,two.test,MATCH")), loaded = true))
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) { GlobalRulesPage(state, {}, {
            // 作者 long：真实保存会返回成功版本；固定宿主也须推进回执，后续拖拽才基于最新排序快照。
            state = state.copy(rules = it, saveResultVersion = state.saveResultVersion + 1)
        }, {}, {}) } }
        capture("global_rules_light")
        compose.onNodeWithTag("global_rules_search").performTextReplacement("one.test")
        compose.onNodeWithTag("global_rule_two").assertDoesNotExist()
        compose.onNodeWithTag("global_rules_sort").assertIsNotEnabled()
        compose.onNodeWithTag("global_rules_search").performTextReplacement("")
        compose.onNodeWithTag("global_rules_sort").performClick()
        dragRule("one", "two")
        compose.runOnIdle { assertEquals("two", state.rules.first().id) }
        capture("global_rules_sort")
        dragRule("two", "one")
        compose.runOnIdle { assertEquals(listOf("one", "two"), state.rules.map { it.id }); assertEquals(2L, state.saveResultVersion) }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("global_rules_add").performClick()
        compose.onNodeWithTag("extension_rule_content").performTextInput("added.test")
        compose.onNodeWithTag("extension_rule_confirm").performClick()
        compose.waitUntil { state.rules.size == 3 }
        compose.onNodeWithTag("global_rules_select").performClick()
        compose.onNodeWithTag("global_rules_all").performClick()
        compose.onNodeWithTag("global_rules_delete").performClick()
        compose.onNodeWithTag("global_rules_delete_confirm").performClick()
        compose.runOnIdle { assertTrue(state.rules.isEmpty()) }
        compose.onNodeWithTag("global_rules_cancel_selection").performClick()
        compose.onNodeWithTag("global_rules_presets").performClick()
        compose.onNodeWithText("阻止 QUIC").performClick()
        compose.onNodeWithTag("extension_preset_confirm").performClick()
        compose.runOnIdle { assertEquals(1, state.rules.size); dark = true }
        capture("global_rules_dark")
    }

    private fun dragRule(from: String, to: String) {
        val list = compose.onNodeWithTag("global_rules_order_list")
        val source = compose.onNodeWithTag("global_rules_order_$from").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("global_rules_order_$to").fetchSemanticsNode().boundsInRoot
        val bounds = list.fetchSemanticsNode().boundsInRoot
        // 作者 long：排序入口已是列表长按拖拽，测试使用真实触点和当前行几何，不恢复已移除的上下移按钮。
        list.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(source.center.x - bounds.left, source.center.y - bounds.top))
            advanceEventTime(650)
            moveTo(androidx.compose.ui.geometry.Offset(target.center.x - bounds.left, target.center.y - bounds.top), 300)
            advanceEventTime(200)
            up()
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
