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
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("proxy_group_yaml_error").assertIsDisplayed(); true }.getOrDefault(false) }
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

    @Test fun wholeGroupYamlRestoresLocatesLongErrorsAndReturnsOnlyToParentDraft() {
        val source = "proxy-groups:\n- {name: Main, type: select, proxies: [DIRECT]}\nrules: ['MATCH,Main']\n"
        var saved: ProxyGroupsSave? = null
        var writes = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(946, "整组草稿"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, onSaveDraft = { saved = it; writes++ })
        } }
        compose.onNodeWithTag("proxy_groups_yaml").performScrollTo().performClick()
        val valid = "- name: Main\n  type: select\n  proxies: [DIRECT]\n  future: preserved\n"
        compose.onNodeWithTag("proxy_groups_yaml_content").performTextReplacement(valid)
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("proxy_groups_yaml_content").assertTextContains("future: preserved", substring = true)
        compose.onNodeWithTag("proxy_groups_yaml_back").performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithTag("proxy_groups_yaml").performScrollTo().performClick()
        val invalid = (1..90).joinToString("\n") { "# 长注释 😀 $it" } + "\n- name: ["
        compose.onNodeWithTag("proxy_groups_yaml_content").performTextReplacement(invalid)
        repeat(2) {
            compose.onNodeWithTag("proxy_groups_yaml_confirm").performClick()
            compose.onNodeWithTag("proxy_groups_yaml_error").assertIsDisplayed().assertTextContains("第 91 行", substring = true)
            compose.onNodeWithTag("proxy_groups_yaml_content").assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, androidx.compose.ui.text.TextRange(invalid.length)))
            compose.onNodeWithTag("proxy_groups_yaml_locate_error").performClick()
        }
        captureSubpage("whole_groups_yaml_error_located")
        closeSoftKeyboard()
        compose.onNodeWithTag("proxy_groups_yaml_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("proxy_groups_yaml_content").assertTextContains(invalid, substring = true)
        compose.onNodeWithTag("proxy_groups_yaml_back").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("proxy_group_list_save").performClick()
        compose.runOnIdle { assertEquals(1, writes); assertEquals("preserved", checkNotNull(saved).groups.single().text("future")) }
    }

    @Test fun singleGroupIconAndOptionSubpagesRestoreTheirDraftAndBackLevel() {
        val source = "proxy-groups:\n- {name: Main, type: select, proxies: [DIRECT]}\n"
        var writes = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(947, "子页恢复"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, initialGroupIndex = 0, onSaveDraft = { writes++ })
        } }
        compose.onNodeWithTag("proxy_group_icon").performScrollTo().performClick()
        compose.onNodeWithTag("group_icon_search").performTextReplacement("empty-search")
        compose.onNodeWithTag("group_icon_link").performClick()
        compose.onNodeWithTag("group_icon_url").performTextReplacement("invalid-draft")
        compose.onNodeWithTag("group_icon_url_confirm").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("group_icon_url").assertTextContains("invalid-draft")
        compose.onNodeWithText("请输入有效的 HTTP 或 HTTPS 图标地址").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("group_icon_search").assertTextContains("empty-search")
        captureSubpage("group_icon_restored_empty")
        compose.onNodeWithTag("group_icon_back").performClick()
        compose.onNodeWithTag("proxy_group_add_option").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("group_add_interval").performScrollTo().performClick()
        compose.onNodeWithTag("proxy_group_add_option_back").performClick()
        compose.onNodeWithTag("group_option_interval").performScrollTo().performClick()
        compose.onNodeWithTag("group_value_input").performTextReplacement("-2")
        compose.onNodeWithTag("group_value_confirm").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("group_value_input").assertTextContains("-2")
        compose.onNodeWithText("请输入非负整数").assertIsDisplayed()
        compose.onNodeWithTag("group_value_input").performTextReplacement("77")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.onNodeWithTag("group_option_interval").assertTextContains("77", substring = true)
        compose.runOnIdle { assertEquals(0, writes) }
        captureSubpage("group_option_restored_value")
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

    private fun beginManagement(dark: Boolean = true) {
        compose.activityRule.scenario.onActivity { it.showServices(dark) }
        compose.onNodeWithTag("dashboard_service_card").performClick()
        compose.onNodeWithTag("service_manage").performClick()
        compose.waitForIdle()
    }

    private fun dragHeader(fraction: Float, cancel: Boolean = false, secondPointer: Boolean = false) {
        val sheet = compose.onNodeWithTag("service_detail_sheet")
        val bounds = sheet.fetchSemanticsNode().boundsInRoot
        val y = compose.onNodeWithTag("dashboard_sheet_title").fetchSemanticsNode().boundsInRoot.center.y - bounds.top
        sheet.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .12f, y))
            moveBy(androidx.compose.ui.geometry.Offset(width * fraction, 0f), delayMillis = 400)
            if (secondPointer) down(1, androidx.compose.ui.geometry.Offset(width * .3f, y + 25f))
            advanceEventTime(200)
            if (cancel || secondPointer) cancel() else up()
        }
        compose.waitForIdle()
    }

    @Test fun dragBackCommitsOrCancelsAndNextOrdinaryBackStillAnimatesAcrossThemes() {
        for (dark in listOf(false, true)) {
            beginManagement(dark)
            dragHeader(.25f)
            compose.onNodeWithTag("service_order_list").assertIsDisplayed()
            compose.onNodeWithTag("service_order_list").performScrollToIndex(8)
            val before = compose.onNodeWithTag("service_order_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            dragHeader(.7f)
            compose.onNodeWithTag("service_check_all").assertIsDisplayed()
            compose.onNodeWithTag("service_manage").performClick()
            assertEquals(before, compose.onNodeWithTag("service_order_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .01f)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("service_manage_back").performClick()
            compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(96)
            val pageLeft = compose.onNodeWithTag("service_detail_sheet_page_detail").fetchSemanticsNode().positionInRoot.x
            val sheetLeft = compose.onNodeWithTag("service_detail_sheet").fetchSemanticsNode().boundsInRoot.left
            captureSubpage("service_drag_then_normal_back_$dark")
            assertTrue("拖回后的普通返回仍须播放过渡：page=$pageLeft sheet=$sheetLeft", pageLeft < sheetLeft - 1f)
            compose.mainClock.advanceTimeBy(700); compose.mainClock.autoAdvance = true
            assertEquals(0, compose.activity.serviceOrderSaveCount)
            compose.onNodeWithTag("service_detail_close").performClick()
        }
    }

    @Test fun interruptedDragNeverNavigatesAfterBackgroundAndNextDragWorks() {
        beginManagement()
        dragHeader(.7f, cancel = true)
        compose.onNodeWithTag("service_order_list").assertIsDisplayed()
        dragHeader(.7f, secondPointer = true)
        compose.onNodeWithTag("service_order_list").assertIsDisplayed()
        compose.onNodeWithTag("service_order_list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("service_order_list").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            val sheet = compose.onNodeWithTag("service_detail_sheet")
            val bounds = sheet.fetchSemanticsNode().boundsInRoot
            val y = compose.onNodeWithTag("dashboard_sheet_title").fetchSemanticsNode().boundsInRoot.center.y - bounds.top
            sheet.performTouchInput { down(androidx.compose.ui.geometry.Offset(width * .12f, y)); moveBy(androidx.compose.ui.geometry.Offset(width * .7f, 0f), delayMillis = 400); up() }
            compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(64)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.mainClock.advanceTimeBy(700)
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithTag("service_order_list").assertIsDisplayed()
        dragHeader(.7f)
        compose.onNodeWithTag("service_check_all").assertIsDisplayed()
        assertEquals(0, compose.activity.serviceOrderSaveCount)
    }

    @Test fun fastFlingCommitsBeforeHalfAndReverseFlingCancelsAfterHalf() {
        beginManagement()
        fun headerY(): Float {
            val sheet = compose.onNodeWithTag("service_detail_sheet").fetchSemanticsNode().boundsInRoot
            return compose.onNodeWithTag("dashboard_sheet_title").fetchSemanticsNode().boundsInRoot.center.y - sheet.top
        }
        var y = headerY()
        compose.onNodeWithTag("service_detail_sheet").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .12f, y), androidx.compose.ui.geometry.Offset(width * .42f, y), 60)
        }
        compose.onNodeWithTag("service_check_all").assertIsDisplayed()
        compose.onNodeWithTag("service_manage").performClick()
        y = headerY()
        val sheet = compose.onNodeWithTag("service_detail_sheet")
        sheet.performTouchInput { down(androidx.compose.ui.geometry.Offset(width * .1f, y)); moveTo(androidx.compose.ui.geometry.Offset(width * .8f, y), delayMillis = 400) }
        val left = compose.onNodeWithTag("service_detail_sheet_page_manage").fetchSemanticsNode().positionInRoot.x
        assertTrue(left > sheet.fetchSemanticsNode().boundsInRoot.width * .5f)
        captureSubpage("service_drag_following_finger")
        sheet.performTouchInput {
            moveTo(androidx.compose.ui.geometry.Offset(width * .77f, y), delayMillis = 16)
            moveTo(androidx.compose.ui.geometry.Offset(width * .74f, y), delayMillis = 16)
            moveTo(androidx.compose.ui.geometry.Offset(width * .71f, y), delayMillis = 16)
            up()
        }
        compose.onNodeWithTag("service_order_list").assertIsDisplayed()
        dragHeader(.7f)
        compose.onNodeWithTag("service_check_all").assertIsDisplayed()
    }

    @Test fun qualityReturnCapturesOutgoingBodyBeforeFadeCompletes() {
        for (dark in listOf(false, true)) {
            compose.activityRule.scenario.onActivity { it.showServiceExit("ready", dark = dark) }
            compose.onNodeWithTag("dashboard_service_card").performClick()
            compose.onNodeWithTag("service_exit_google").performScrollTo().performClick()
            compose.onNodeWithTag("service_quality_details").assertIsDisplayed()
            captureSubpage("quality_before_return_$dark")
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithTag("service_manage_back").performClick()
                compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(32)
                // 作者 long：离场层按产品行为隐藏语义，保留真实Window截图作正文目视验收，不能用终态节点断言代替中间帧。
                captureSubpage("quality_return_outgoing_$dark")
                val bounds = compose.onNodeWithTag("service_detail_sheet").fetchSemanticsNode().boundsInRoot
                val pixels = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                try {
                    val density = compose.activity.resources.displayMetrics.density
                    val top = (bounds.top + 80 * density).toInt()
                    val bottom = minOf((bounds.bottom - 64 * density).toInt(), top + (300 * density).toInt())
                    val background = pixels.getPixel((bounds.left + 8 * density).toInt(), top + 10)
                    var visible = 0
                    for (y in top until bottom step 4) for (x in (bounds.left + 24 * density).toInt() until (bounds.right - 24 * density).toInt() step 4) {
                        val color = pixels.getPixel(x, y)
                        val distance = kotlin.math.abs(android.graphics.Color.red(color) - android.graphics.Color.red(background)) +
                            kotlin.math.abs(android.graphics.Color.green(color) - android.graphics.Color.green(background)) +
                            kotlin.math.abs(android.graphics.Color.blue(color) - android.graphics.Color.blue(background))
                        if (distance > 36) visible++
                    }
                    assertTrue("返回中间帧必须保留实际正文绘制，不能闪成空白：$visible", visible > 80)
                } finally { pixels.recycle() }
                compose.mainClock.advanceTimeBy(700)
            } finally { compose.mainClock.autoAdvance = true }
            compose.onNodeWithTag("service_check_all").assertIsDisplayed()
            compose.onNodeWithTag("service_detail_close").performClick()
        }
    }

    @Test fun qualityPageDragBackKeepsServiceDetailsAndDoesNotRefresh() {
        compose.activityRule.scenario.onActivity { it.showServiceExit("ready") }
        compose.onNodeWithTag("dashboard_service_card").performClick()
        compose.onNodeWithTag("service_exit_google").performScrollTo().performClick()
        compose.onNodeWithTag("service_quality_details").assertExists()
        dragHeader(.25f)
        compose.onNodeWithTag("service_quality_details").assertExists()
        captureSubpage("service_quality_drag_cancelled")
        dragHeader(.7f)
        compose.onNodeWithTag("service_check_all").assertIsDisplayed()
        compose.onNodeWithTag("service_exit_google").assertExists()
        assertEquals(0, compose.activity.serviceCheckCount)
    }

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
