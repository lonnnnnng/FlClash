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
        var settings by mutableStateOf(ProfileDnsOverrides(listen = "127.0.0.1:1053"))
        var pending: ProfileDnsOverrides? = null
        var completion: ((Boolean) -> Unit)? = null
        var writes = 0
        compose.setContent { KClashTheme { GlobalDnsSettingsPage(settings, saving, saving, {}, { value, done ->
            writes++; pending = value; saving = true; completion = done
        }) } }
        compose.onNodeWithTag("global_dns_listen").performClick()
        compose.onNodeWithTag("global_dns_input").performTextReplacement("127.0.0.1:5353")
        compose.onNodeWithTag("global_dns_save").performClick()
        compose.onNodeWithTag("global_dns_editor_busy").assertIsDisplayed()
        compose.onNodeWithTag("global_dns_save").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, writes); completion!!.invoke(false); saving = false }
        compose.onNodeWithTag("global_dns_error").assertTextContains("输入已保留", substring = true)
        assertEquals("127.0.0.1:5353", compose.onNodeWithTag("global_dns_input").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.InputText].text)
        compose.onNodeWithTag("global_dns_save").performClick()
        compose.runOnIdle { settings = pending!!; completion!!.invoke(true); saving = false }
        compose.waitUntil { compose.onAllNodesWithTag("global_dns_editor").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals(2, writes); assertEquals("127.0.0.1:5353", settings.listen) }
    }

    @Test fun dnsExplicitEmptyFailureRetryRemovalAndBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var settings by mutableStateOf(ProfileDnsOverrides(nameserver = listOf("1.1.1.1"), ipv6 = false))
        var fail = true
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) { GlobalDnsSettingsPage(settings, false, false, {}, { value, done ->
            if (!fail) settings = value
            done(!fail)
        }) } }
        capture("vis095_dns_light")
        compose.onNodeWithTag("global_dns_nameserver").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_list_select_0").performClick()
        compose.onNodeWithTag("global_dns_list_delete").performClick()
        compose.onNodeWithTag("global_dns_list_delete_confirm").performClick()
        compose.onNodeWithTag("global_dns_list_save").performClick()
        compose.onNodeWithTag("global_dns_list_error").assertTextContains("输入已保留", substring = true)
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("global_dns_list_save").performClick()
        compose.onNodeWithTag("global_dns_list_page").assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), settings.nameserver) }
        compose.onNodeWithTag("global_dns_add").performClick()
        compose.onNodeWithTag("global_dns_add_fake-ip-filter-mode").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_add_sheet").assertIsDisplayed()
        compose.onNodeWithTag("global_dns_add_listen").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_add_close").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("global_dns_add_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("global_dns_fake-ip-filter-mode").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_option_whitelist").performClick()
        compose.runOnIdle { assertEquals("whitelist", settings.fakeIpFilterMode); dark = true }
        capture("vis095_dns_dark")
        compose.onNodeWithTag("global_dns_remove_nameserver").performScrollTo().performClick()
        compose.runOnIdle { assertNull(settings.nameserver); assertEquals(false, settings.ipv6); assertEquals("", settings.listen) }
    }

    @Test fun dnsListDraftReorderQuickEditAndDiscard() {
        var settings by mutableStateOf(ProfileDnsOverrides(nameserver = listOf("https://doh.pub/dns-query", "tls://223.5.5.5:853")))
        compose.setContent { KClashTheme(darkTheme = false) { GlobalDnsSettingsPage(settings, false, false, {}, { value, done -> settings = value; done(true) }) } }
        compose.onNodeWithTag("global_dns_nameserver").performClick()
        capture("vis095_list_light")
        val first = compose.onNodeWithTag("global_dns_list_row_0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("global_dns_list_row_1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("global_dns_list_row_0").performTouchInput {
            down(center); advanceEventTime(650); moveBy(androidx.compose.ui.geometry.Offset(0f, second.center.y - first.center.y), 400); advanceEventTime(150); up()
        }
        compose.onNodeWithTag("global_dns_list_row_0").assertTextContains("tls://223.5.5.5:853")
        compose.onNodeWithTag("global_dns_list_add_menu").performClick()
        compose.onNodeWithTag("global_dns_list_add").performClick()
        compose.onNodeWithTag("more_list_entry_input").performTextInput("1.1.1.1")
        capture("vis095_list_add")
        compose.onNodeWithTag("more_list_entry_confirm").performClick()
        compose.onNodeWithTag("global_dns_list_row_2").performClick()
        compose.onNodeWithTag("more_list_entry_input").performTextReplacement("tls://223.5.5.5:853")
        compose.onNodeWithTag("more_list_entry_confirm").performClick()
        compose.onNodeWithTag("more_list_input_error").assertTextContains("已存在相同的项")
        compose.onNodeWithTag("more_list_entry_input").performTextReplacement("9.9.9.9")
        compose.onNodeWithTag("more_list_entry_confirm").performClick()
        compose.onNodeWithTag("global_dns_list_add_menu").performClick()
        compose.onNodeWithTag("global_dns_list_quick").performClick()
        compose.onNodeWithTag("global_dns_list_quick_input").performTextReplacement("9.9.9.9\n1.1.1.1")
        compose.onNodeWithTag("more_list_quick_back").performClick()
        compose.onNodeWithTag("global_dns_list_row_0").assertTextContains("9.9.9.9")
        compose.onNodeWithTag("global_dns_list_save").performClick()
        compose.runOnIdle { assertEquals(listOf("9.9.9.9", "1.1.1.1"), settings.nameserver) }
        compose.onNodeWithTag("global_dns_nameserver").performClick()
        compose.onNodeWithTag("global_dns_list_select_0").performClick()
        compose.onNodeWithTag("global_dns_list_all").performClick()
        compose.onNodeWithTag("global_dns_list_delete").performClick()
        compose.onNodeWithTag("global_dns_list_delete_confirm").performClick()
        compose.onNodeWithTag("global_dns_list_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("global_dns_list_back").performClick()
        compose.onNodeWithText("放弃").performClick()
        compose.runOnIdle { assertEquals(listOf("9.9.9.9", "1.1.1.1"), settings.nameserver) }
    }

    @Test fun dnsDefaultsRetrySwitchEnumAndLastCandidateClose() {
        var settings by mutableStateOf(ProfileDnsOverrides())
        var fail = true
        var writes = 0
        compose.setContent { KClashTheme { GlobalDnsSettingsPage(settings, false, false, {}, { value, done ->
            writes++; if (!fail) settings = value; done(!fail)
        }) } }
        compose.onNodeWithTag("global_dns_add").performClick()
        compose.onNodeWithTag("global_dns_add_use-hosts").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_add_error").assertIsDisplayed()
        compose.onNodeWithTag("global_dns_add_use-hosts").assertIsDisplayed()
        compose.runOnIdle { assertTrue(settings.isEmpty); fail = false }
        for (key in com.lonnnnnng.flclash.service.GlobalDnsSettings.keys) {
            compose.onNodeWithTag("global_dns_add_search").performTextReplacement(key)
            compose.onNodeWithTag("global_dns_add_$key").performScrollTo().performClick()
        }
        compose.waitUntil { compose.onAllNodesWithTag("global_dns_add_sheet").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals(16, writes); assertEquals(15, settings.toJson().size()); assertEquals(true, settings.useHosts); assertEquals(false, settings.preferH3) }
        compose.onNodeWithTag("global_dns_use-hosts").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, settings.useHosts) }
        compose.onNodeWithTag("global_dns_cache-algorithm").performScrollTo().performClick()
        compose.onNodeWithTag("global_dns_option_arc").performClick()
        compose.runOnIdle { assertEquals("arc", settings.cacheAlgorithm) }
        capture("vis095_all_fields")
    }

    @Test fun dnsListExternalRemovalKeepsExplicitEmptyConflict() {
        var settings by mutableStateOf(ProfileDnsOverrides(nameserver = listOf("1.1.1.1")))
        var writes = 0
        compose.setContent { KClashTheme { GlobalDnsSettingsPage(settings, false, false, {}, { value, done ->
            writes++; settings = value; done(true)
        }) } }
        compose.onNodeWithTag("global_dns_nameserver").performClick()
        compose.onNodeWithTag("global_dns_list_select_0").performClick()
        compose.onNodeWithTag("global_dns_list_delete").performClick()
        compose.onNodeWithTag("global_dns_list_delete_confirm").performClick()
        compose.runOnIdle { settings = settings.copy(nameserver = null) }
        compose.onNodeWithTag("global_dns_list_save").performClick()
        compose.onNodeWithTag("global_dns_list_error").assertTextContains("DNS 设置已变化", substring = true)
        compose.runOnIdle { assertEquals(0, writes); assertNull(settings.nameserver) }
        compose.onNodeWithTag("global_dns_list_reset").performClick()
        compose.onNodeWithTag("global_dns_list_reset_confirm").performClick()
        compose.onNodeWithTag("global_dns_list_row_0").assertTextContains("1.1.1.1")
    }

    @Test fun dnsListPendingSaveRecreationDoesNotReplay() {
        var settings by mutableStateOf(listOf("1.1.1.1"))
        var saving by mutableStateOf(false)
        var visible by mutableStateOf(true)
        var pending: List<String>? = null
        var writes = 0
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { KClashTheme {
            if (visible) GlobalDnsListEditor("nameserver", "Nameserver", settings, saving, { visible = false }) { value, _ ->
                writes++; pending = value.asJsonArray.map { it.asString }; saving = true
            }
        } }
        compose.onNodeWithTag("global_dns_list_select_0").performClick()
        compose.onNodeWithTag("global_dns_list_delete").performClick()
        compose.onNodeWithTag("global_dns_list_delete_confirm").performClick()
        compose.onNodeWithTag("global_dns_list_save").performClick()
        compose.onNodeWithTag("global_dns_list_saving").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("global_dns_list_saving").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, writes); settings = pending!!; saving = false }
        compose.waitUntil { compose.onAllNodesWithTag("global_dns_list_page").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals(1, writes); assertEquals(emptyList<String>(), settings) }
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
        compose.onNodeWithTag("global_rule_select_one").performClick()
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

    @Test fun dnsFullPageUsesViewportEmptyStateAndLightDarkSelector() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) {
            GlobalDnsSettingsPage(ProfileDnsOverrides(), false, false, {}, { _, _ -> error("只读视觉不应保存") })
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.waitForIdle()
            val viewport = compose.onNodeWithTag("global_dns_empty").fetchSemanticsNode().boundsInRoot
            val content = compose.onNodeWithTag("global_dns_empty_content").fetchSemanticsNode().boundsInRoot
            val art = compose.onNodeWithTag("global_dns_illustration").fetchSemanticsNode().boundsInRoot
            val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            assertEquals(160f * density, art.height, 1f)
            assertEquals(viewport.top + (viewport.height - content.height) * .4f, content.top, 1f)
            compose.onNodeWithTag("global_dns_add").assertIsDisplayed()
            capture("vis094_dns_empty_$theme")
            compose.onNodeWithTag("global_dns_add").performClick()
            compose.onNodeWithTag("global_dns_add_search").performTextReplacement("fake")
            compose.onNodeWithTag("global_dns_add_fake-ip-filter").assertIsDisplayed()
            compose.onNodeWithTag("global_dns_add_listen").assertDoesNotExist()
            capture("vis094_dns_filtered_$theme")
            compose.onNodeWithTag("global_dns_add_close").performClick()
            compose.waitUntil { compose.onAllNodesWithTag("global_dns_add_sheet").fetchSemanticsNodes().isEmpty() }
        }
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
