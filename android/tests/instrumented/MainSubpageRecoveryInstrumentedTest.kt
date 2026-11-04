package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：仅使用隔离 UI 草稿，验证重建和失败恢复，不写正式配置或启动核心。 */
class MainSubpageRecoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun duplicateRuleIdentityAndBatchSelectionSurviveRestoration() {
        var text by mutableStateOf("DOMAIN,same.test,DIRECT\nDOMAIN,same.test,DIRECT\nMATCH,DIRECT")
        var writes = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            ProfileRulesEditorDialog(Profile.custom(941, "重复规则"), text, false, {}, { text = it; writes++ })
        } }
        compose.onNodeWithTag("custom_rule_edit_1").performClick()
        compose.onNodeWithTag("custom_rule_payload").performTextReplacement("second.test")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("custom_rule_payload").assertTextContains("second.test")
        captureSubpage("rule_restored_draft")
        compose.onNodeWithTag("custom_rule_confirm").performClick()
        compose.onNodeWithTag("custom_rule_confirm").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("DOMAIN,same.test,DIRECT", "DOMAIN,second.test,DIRECT", "MATCH,DIRECT"), CustomRules.decode(text).map { it.value })
            assertEquals(1, writes)
        }
        compose.onNodeWithTag("custom_rules_menu").performClick()
        compose.onNodeWithTag("custom_rules_select").performClick()
        compose.onNodeWithTag("custom_rule_select_1").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        compose.onNodeWithTag("custom_rule_select_1").assertIsOn()
        compose.onNodeWithTag("custom_rule_select_0").assertIsOff()
        captureSubpage("rule_restored_selection")
        compose.onNodeWithTag("custom_rules_delete_selected").performClick()
        compose.onNodeWithTag("custom_rules_delete_confirm").performClick()
        compose.runOnIdle { assertEquals(listOf("DOMAIN,same.test,DIRECT", "MATCH,DIRECT"), CustomRules.decode(text).map { it.value }) }
    }

    @Test fun pendingRuleSaveRestoresAndCompletesWithoutSubmittingAgain() {
        var text by mutableStateOf("DOMAIN,original.test,DIRECT")
        var busy by mutableStateOf(false)
        var accepted: String? = null
        var writes = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            ProfileRulesEditorDialog(Profile.custom(942, "等待回读"), text, busy, {}, { accepted = it; writes++; busy = true })
        } }
        compose.onNodeWithTag("custom_rule_edit_0").performClick()
        compose.onNodeWithTag("custom_rule_payload").performTextReplacement("saved.test")
        compose.onNodeWithTag("custom_rule_confirm").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("custom_rule_confirm").assertIsNotEnabled()
        compose.runOnIdle { text = checkNotNull(accepted); busy = false }
        compose.onNodeWithTag("custom_rule_confirm").assertDoesNotExist()
        compose.onNodeWithTag("custom_rule_edit_0").assertTextContains("saved.test", substring = true)
        compose.runOnIdle { assertEquals(1, writes); assertEquals(1, CustomRules.decode(text).size) }
    }

    @Test fun changedRuleSourceDoesNotTurnRestoredEditIntoInsertion() {
        var text by mutableStateOf("DOMAIN,original.test,DIRECT")
        var writes = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            ProfileRulesEditorDialog(Profile.custom(943, "源变更"), text, false, {}, { writes++ })
        } }
        compose.onNodeWithTag("custom_rule_edit_0").performClick()
        compose.onNodeWithTag("custom_rule_payload").performTextReplacement("draft.test")
        compose.runOnIdle { text = "MATCH,DIRECT" }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("原规则已变更").assertIsDisplayed()
        compose.onNodeWithText("返回列表").performClick()
        compose.onNodeWithTag("custom_rule_edit_0").assertTextContains("MATCH", substring = true)
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun groupYamlBackKeepsValidDraftAndInvalidDraftCanContinueOrDiscard() {
        val source = "proxy-groups:\n- {name: Main, type: select, proxies: [DIRECT]}\nrules: ['MATCH,Main']\n"
        var saved: ProxyGroupsSave? = null
        var writes = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(944, "策略草稿"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, initialGroupIndex = 0, onSaveDraft = { saved = it; writes++ })
        } }
        compose.onNodeWithTag("proxy_group_quick_yaml").performClick()
        compose.onNodeWithTag("proxy_group_yaml_content").performTextReplacement("name: Renamed\ntype: select\nproxies: [DIRECT]\n")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("proxy_group_yaml_content").assertTextContains("Renamed", substring = true)
        compose.onNodeWithTag("proxy_group_yaml_back").performClick()
        compose.onNodeWithTag("proxy_group_name").assertTextContains("Renamed", substring = true)
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithTag("proxy_group_quick_yaml").performClick()
        compose.onNodeWithTag("proxy_group_yaml_content").performTextReplacement("name: [")
        compose.onNodeWithTag("proxy_group_yaml_confirm").performClick()
        compose.onNodeWithTag("proxy_group_yaml_error").assertIsDisplayed()
        // 作者 long：系统返回先消费 IME；明确等键盘收起后才验证页面返回，避免把系统两级返回混成一次。
        closeSoftKeyboard()
        pressBack()
        compose.onNodeWithText("放弃 YAML 修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("proxy_group_yaml_content").assertTextContains("name: [")
        captureSubpage("group_yaml_failed_draft")
        compose.onNodeWithTag("proxy_group_yaml_back").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("proxy_group_name").assertTextContains("Renamed", substring = true)
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.runOnIdle { assertEquals(1, writes); assertEquals("Renamed", checkNotNull(saved).groups.single().name) }
    }

    @Test fun providerYamlErrorsReturnToTopOnRepeatedLocalAndRepositoryFailures() {
        var failure by mutableStateOf<String?>(null)
        var busy by mutableStateOf(false)
        var writes = 0
        compose.setContent { KClashTheme {
            ProfileProvidersPage(Profile.custom(945, "长资源草稿"), "{}", "{}", "rules: ['MATCH,DIRECT']", emptyMap(), emptyList(), emptyList(),
                busy, 0, failure, {}, { _, _, _ -> writes++; failure = null; busy = true }, {}, { emptyList() })
        } }
        compose.onNodeWithTag("profile_providers_menu").performClick()
        compose.onNodeWithText("快捷 YAML").performClick()
        val comments = (1..70).joinToString("\n") { "# 注释行 $it" }
        compose.onNodeWithTag("providers_yaml_rule").performTextReplacement("$comments\ninvalid: [")
        repeat(2) {
            compose.onNodeWithTag("providers_yaml_scroll").performTouchInput { swipeUp() }
            compose.onNodeWithTag("providers_yaml_save").performClick()
            // 作者 long：这里不滚动到错误节点，否则会掩盖保存后提示仍留在屏外的问题。
            compose.onNodeWithTag("providers_yaml_error").assertIsDisplayed()
        }
        captureSubpage("providers_yaml_local_error")
        compose.onNodeWithTag("providers_yaml_rule").performScrollTo().performTextReplacement("$comments\n{}")
        repeat(2) {
            compose.onNodeWithTag("providers_yaml_scroll").performTouchInput { swipeUp() }
            compose.onNodeWithTag("providers_yaml_save").performClick()
            compose.onNodeWithTag("providers_yaml_save").assertIsNotEnabled()
            compose.runOnIdle { failure = "保存失败，原配置已保留"; busy = false }
            compose.onNodeWithTag("providers_yaml_error").assertIsDisplayed().assertTextContains("原配置已保留", substring = true)
        }
        captureSubpage("providers_yaml_repository_error")
        compose.onNodeWithTag("providers_yaml_rule").assertTextContains(comments, substring = true)
        compose.runOnIdle { assertEquals(2, writes) }
    }

    @Test fun dismissingSheetBlocksBusinessClicksAndReopenedSheetWorks() {
        var open by mutableStateOf(true)
        var calls = 0
        var closed = 0
        compose.setContent { KClashTheme {
            if (open) DashboardModalSheet("退出隔离", "exit_sheet", "exit_close", { open = false; closed++ }) {
                TextButton(onClick = { calls++ }, Modifier.testTag("exit_action")) { Text("执行业务动作") }
            }
        } }
        compose.onNodeWithTag("exit_action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, calls) }
        val sheet = compose.onNodeWithTag("exit_sheet").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("exit_action").fetchSemanticsNode().boundsInRoot.center - sheet.topLeft
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("exit_close").performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("exit_sheet").performTouchInput { click(action) }
        compose.runOnIdle { assertEquals(1, calls) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("exit_sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closed); open = true }
        compose.onNodeWithTag("exit_action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, calls) }
    }
}

class ServiceSubpageMotionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<DashboardTestActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun managementTransitionReversesAndPreservesListPositionAcrossThemes() {
        for (dark in listOf(false, true)) {
            compose.activityRule.scenario.onActivity { it.showServices(dark) }
            compose.onNodeWithTag("dashboard_service_card").performClick()
            compose.onNodeWithTag("service_manage").assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("service_manage").performClick()
            compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(96)
            val entering = compose.onNodeWithTag("service_detail_sheet_page_manage").fetchSemanticsNode().boundsInRoot.left
            val sheetLeft = compose.onNodeWithTag("service_detail_sheet").fetchSemanticsNode().boundsInRoot.left
            assertTrue("管理子页入场中必须仍有横向位移", entering > sheetLeft + 1f)
            // 作者 long：旧页仍绘制，但不能保留业务点击节点；其绘制状态另由中间帧截图核对。
            compose.onNodeWithTag("service_check_all").assertDoesNotExist()
            captureSubpage("service_manage_mid_$dark")
            compose.onNodeWithTag("service_manage_back").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("service_manage").performClick()
            compose.mainClock.advanceTimeBy(700); compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("service_order_list").performScrollToIndex(8)
            val before = compose.onNodeWithTag("service_order_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue(before > 0)
            captureSubpage("service_manage_scrolled_$dark")
            pressBack()
            compose.onNodeWithTag("service_check_all").assertIsDisplayed()
            compose.onNodeWithTag("service_manage").performClick()
            val after = compose.onNodeWithTag("service_order_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals(before, after, 0.01f)
            compose.onNodeWithTag("service_detail_close").performClick()
            compose.onNodeWithTag("service_detail_sheet").assertDoesNotExist()
        }
    }
}

private fun captureSubpage(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    android.os.SystemClock.sleep(150)
    val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    recordVisualPng(name, ByteArrayOutputStream().also { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    screen.recycle()
}
