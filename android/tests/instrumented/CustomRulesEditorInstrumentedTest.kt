package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomRulesEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var content by mutableStateOf("DOMAIN,alpha.test,DIRECT\nMATCH,DIRECT")
    private var dark by mutableStateOf(false)
    private var failure by mutableStateOf<String?>(null)
    private var failSave = false
    private var saves = 0
    private val catalog = CustomRuleCatalog(listOf("Main"), listOf("Node"), listOf("Local"))

    @Test fun addingDuplicatingSearchingAndBatchDeletingSaveImmediately() {
        render(); addDomain("beta.test")
        assertEquals(3, values().size)
        compose.onNodeWithTag("custom_rule_menu_2").performClick(); compose.onNodeWithTag("custom_rule_duplicate_2").performClick()
        compose.waitForIdle(); assertEquals(2, values().count { it == "DOMAIN,beta.test,DIRECT" })
        compose.onNodeWithTag("custom_rules_search_toggle").performClick(); compose.onNodeWithTag("custom_rules_search").performTextInput("beta")
        menu("select"); compose.onNodeWithTag("custom_rules_select_all").performClick()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        compose.onNodeWithTag("custom_rules_delete_selected").performClick(); compose.onNodeWithText("取消").performClick()
        assertEquals(4, values().size)
        compose.onNodeWithTag("custom_rules_delete_selected").performClick(); compose.onNodeWithTag("custom_rules_delete_confirm").performClick()
        compose.waitForIdle(); assertEquals(listOf("DOMAIN,alpha.test,DIRECT", "MATCH,DIRECT"), values()); assertEquals(3, saves)
    }

    @Test fun formSelectsTypesTargetsProvidersAndKeepsSourceIpNoResolveConsistent() {
        content = ""; render()
        compose.onNodeWithTag("custom_rules_add").performClick(); pick("type", "IP-CIDR")
        compose.onNodeWithTag("custom_rule_payload").performTextInput("192.0.2.0/24")
        compose.onNodeWithTag("custom_rule_src").performClick()
        compose.onNodeWithTag("custom_rule_no_resolve").assertIsOn().assertIsNotEnabled()
        pick("target", "Main"); confirmForm()
        assertEquals("IP-CIDR,192.0.2.0/24,Main,src", values().single())
        compose.onNodeWithTag("custom_rule_edit_0").performClick(); compose.onNodeWithTag("custom_rule_src").performClick()
        compose.onNodeWithTag("custom_rule_no_resolve").assertIsOff().assertIsEnabled().performClick(); confirmForm()
        assertEquals("IP-CIDR,192.0.2.0/24,Main,no-resolve", values().single())
        compose.onNodeWithTag("custom_rules_add").performClick(); pick("type", "RULE-SET"); pick("provider", "Local"); confirmForm()
        assertEquals("RULE-SET,Local,DIRECT", values().last())
    }

    @Test fun yamlRejectsInvalidPayloadAndKeepsMissingReferencesVisibleForRepair() {
        render(); menu("yaml")
        compose.onNodeWithTag("profile_rules_yaml").performTextReplacement("- NETWORK,any,DIRECT")
        compose.onNodeWithTag("custom_rules_yaml_confirm").performClick(); compose.onNodeWithTag("custom_rules_yaml_error").assertTextContains("TCP", substring = true)
        compose.onNodeWithTag("profile_rules_yaml").performTextReplacement("rules:\n- SUB-RULE,(NETWORK,TCP),nested\n- RULE-SET,Missing,Main\n- MATCH,DIRECT")
        compose.onNodeWithTag("custom_rules_yaml_confirm").performClick(); waitGone("custom_rules_yaml_confirm")
        assertEquals(2, values().size); compose.onNodeWithTag("custom_rules_issues").assertTextContains("1 条", substring = true)
        compose.onNodeWithTag("custom_rule_edit_0").performClick(); compose.onNodeWithTag("custom_rule_reference_issue").assertTextContains("资源不存在", substring = true)
        pick("provider", "Local"); confirmForm(); compose.onNodeWithTag("custom_rules_issues").assertDoesNotExist()
    }

    @Test fun sortingButtonsAndWholeRowLongDragPersistTheDisplayedOrder() {
        content = "DOMAIN,a.test,DIRECT\nDOMAIN,b.test,DIRECT\nMATCH,DIRECT"; render(); menu("sort")
        compose.onNodeWithTag("custom_rule_down_0").performClick(); compose.waitForIdle()
        assertEquals("DOMAIN,b.test,DIRECT", values().first())
        val rowHeight = compose.onNodeWithTag("custom_rule_row_0").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("custom_rule_row_0").performTouchInput {
            down(center); advanceEventTime(700); moveBy(Offset(0f, rowHeight * 1.5f)); advanceEventTime(200); up()
        }
        compose.waitForIdle(); assertEquals("DOMAIN,a.test,DIRECT", values().first()); assertEquals(2, saves)
    }

    @Test fun failedSaveAndReturningFromFormAndYamlRetainEditableDrafts() {
        render(); failSave = true
        compose.onNodeWithTag("custom_rules_add").performClick(); compose.onNodeWithTag("custom_rule_payload").performTextInput("retry.test")
        compose.onNodeWithTag("custom_rule_confirm").performClick(); compose.onNodeWithTag("custom_rule_form_error").assertTextContains("原配置已保留", substring = true)
        compose.onNodeWithTag("custom_rule_payload").assertTextContains("retry.test")
        failSave = false; confirmForm(); assertEquals("DOMAIN,retry.test,DIRECT", values().last())
        compose.onNodeWithTag("custom_rule_edit_0").performClick(); compose.onNodeWithTag("custom_rule_payload").performTextReplacement("discard.test")
        compose.onNodeWithTag("custom_rule_form_back").performClick(); compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("custom_rule_payload").assertTextContains("discard.test")
        compose.onNodeWithTag("custom_rule_form_back").performClick(); compose.onNodeWithText("放弃").performClick()
        menu("yaml"); compose.onNodeWithTag("profile_rules_yaml").performTextReplacement("bad")
        compose.onNodeWithTag("custom_rules_yaml_back").performClick(); compose.onNodeWithText("放弃").performClick()
        assertEquals("DOMAIN,alpha.test,DIRECT", values().first())
    }

    @Test fun failedReorderRestoresSavedOrderAndSearchingEndsSorting() {
        render(); failSave = true; menu("sort")
        compose.onNodeWithTag("custom_rule_down_0").performClick()
        compose.onNodeWithTag("custom_rules_error").assertTextContains("原配置已保留", substring = true)
        compose.onNodeWithTag("custom_rule_edit_0").assertTextContains("alpha.test", substring = true)
        assertEquals("DOMAIN,alpha.test,DIRECT", values().first()); assertEquals(0, saves)
        compose.onNodeWithTag("custom_rules_search_toggle").performClick()
        compose.onNodeWithTag("custom_rules_search").performTextInput("MATCH")
        compose.onNodeWithTag("custom_rule_down_0").assertDoesNotExist()
        compose.onNodeWithTag("custom_rule_edit_0").assertTextContains("MATCH", substring = true)
    }

    @Test fun existingSubRuleDoesNotBlockOtherRulesOrPresetAdditions() {
        content = "SUB-RULE,(NETWORK,TCP),nested\nMATCH,DIRECT"; render()
        addDomain("kept.test")
        assertEquals("SUB-RULE,(NETWORK,TCP),nested", values().first())
        assertEquals("DOMAIN,kept.test,DIRECT", values().last())
        menu("presets")
        compose.onNodeWithText("阻止 QUIC").performClick()
        compose.onNodeWithTag("extension_preset_confirm").performClick()
        compose.waitForIdle()
        assertTrue(values().any { it.startsWith("AND,") }); assertEquals(2, saves)
    }

    @Test fun entireRulesPageRendersListsFormsPickersYamlAndBatchAcrossThemes() {
        content = ""; render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; content = "" }
            capture("custom_rules_empty_$theme")
            addDomain("example.test")
            capture("custom_rules_list_$theme")
            compose.onNodeWithTag("custom_rule_edit_0").performClick(); capture("custom_rules_form_$theme")
            compose.onNodeWithTag("custom_rule_type").performClick(); capture("custom_rules_types_$theme")
            compose.onNodeWithTag("custom_rule_picker_back").performClick(); compose.onNodeWithTag("custom_rule_form_back").performClick()
            menu("yaml"); capture("custom_rules_yaml_$theme"); compose.onNodeWithTag("custom_rules_yaml_back").performClick()
            menu("select"); compose.onNodeWithTag("custom_rules_select_all").performClick(); capture("custom_rules_batch_$theme")
            compose.onNodeWithTag("custom_rules_back").performClick()
        }
    }

    private fun render() {
        assertPortraitDefaultFontConfiguration()
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileRulesEditorDialog(Profile.custom(940, "规则配置"), content, false, {}, { next ->
                if (failSave) failure = "保存失败，原配置已保留" else { failure = null; content = next; saves++ }
            }, catalog, failure)
        } }
    }
    private fun menu(action: String) { compose.onNodeWithTag("custom_rules_menu").performClick(); compose.onNodeWithTag("custom_rules_$action").performClick() }
    private fun values() = CustomRules.decode(content, validate = false).map { it.value }
    private fun addDomain(payload: String) { compose.onNodeWithTag("custom_rules_add").performClick(); compose.onNodeWithTag("custom_rule_payload").performTextInput(payload); confirmForm() }
    private fun confirmForm() { compose.onNodeWithTag("custom_rule_confirm").performClick(); waitGone("custom_rule_confirm") }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun pick(mode: String, value: String) {
        compose.onNodeWithTag("custom_rule_$mode").performScrollTo().performClick()
        compose.onNodeWithTag("custom_rule_picker_list").performScrollToNode(hasTestTag("custom_rule_pick_$value"))
        compose.onNodeWithTag("custom_rule_pick_$value").performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray(); recordVisualPng(name, png)
    }
}
