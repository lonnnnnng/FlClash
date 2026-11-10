package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：整条编辑路径在隔离 UI 中验证，不创建正式 Profile，不启动核心或访问 Provider。 */
class ProxyGroupEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val source = "proxies:\n  - {name: Local, type: direct}\nproxy-providers:\n  cloud: {type: file, path: cloud.yaml}\nproxy-groups:\n  - {name: Main, type: select, proxies: [DIRECT], future: keep}\n  - {name: Other, type: fallback, proxies: [Main]}\nrules: ['MATCH,Main']\n"

    @Test fun categorizedMemberPicksRestoreAndUndoWholeOriginalDraft() {
        assertPortraitDefaultFontConfiguration()
        val original = ProxyGroupDrafts.read(source).first().edit { it.addProperty("filter", "HK") }
            .copy(memberLayout = listOf("filter"))
        var current by mutableStateOf(original)
        val restore = StateRestorationTester(compose)
        restore.setContent { KClashTheme {
            CompositionLocalProvider(LocalFilterPresets provides listOf(FilterPreset("区域", "HK`JP"))) {
                ProxyGroupMemberPicker(current, ProxyGroupDrafts.read(source), source, {}, { current = it })
            }
        } }
        compose.onNodeWithTag("group_member_choice_false_Local").assertDoesNotExist()
        compose.onNodeWithTag("group_member_category_local").performClick()
        compose.onNodeWithTag("group_member_choice_false_Local").performClick()
        compose.onNodeWithTag("group_member_category_auto").performClick()
        compose.onNodeWithTag("group_member_choice_ALL_PROXIES_*").performClick()
        compose.onNodeWithTag("group_member_search").performTextReplacement("区域")
        compose.onNodeWithTag("group_member_choice_FILTER_HK`JP").performClick()
        compose.runOnIdle {
            assertEquals(listOf("DIRECT", "Local"), current.strings("proxies"))
            assertTrue(current.flag("include-all-proxies"))
            assertEquals("HK`JP", current.text("filter"))
            assertEquals(original.memberLayout, current.memberLayout)
        }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("group_member_search").assertTextContains("区域")
        capture("member_categories_restored")
        compose.onNodeWithTag("group_member_choice_FILTER_HK`JP").performClick()
        compose.onNodeWithTag("group_member_search").performTextReplacement("zzzz-no-results")
        compose.onNodeWithTag("group_member_empty").assertTextEquals("无搜索结果")
        // 作者 long：本用例只验重建后的选择状态；取消的IME触点和动画由独立memberDock用例验，避免平台键盘位移干扰撤回断言。
        compose.onNodeWithTag("group_member_search_cancel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("group_member_search").assertTextContains("")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("group_member_choice_false_Local").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("group_member_choice_false_Local").performScrollTo().performClick()
        compose.onNodeWithTag("group_member_choice_ALL_PROXIES_*").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, current) }
        capture("member_categories_undone")
    }

    @Test fun memberDockCancelPreservesDraftAndStaysAboveRealIme() {
        assertPortraitDefaultFontConfiguration()
        var writes = 0
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(214, "成员搜索"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, initialGroupIndex = 0, onSaveDraft = { writes++ })
        } }

        compose.onNodeWithTag("group_members_add").performScrollTo().performClick()
        compose.onNodeWithTag("group_member_search").performClick().performTextReplacement("Local")
        fun imeTop(): Int? {
            var result: Int? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                val insets = view?.let(androidx.core.view.ViewCompat::getRootWindowInsets)
                if (view != null && insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
                    result = view.height - insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            }
            return result
        }
        compose.waitUntil(5_000) { imeTop() != null }
        val dock = compose.onNodeWithTag("group_member_search_dock").fetchSemanticsNode().boundsInWindow
        assertTrue(dock.bottom <= checkNotNull(imeTop()) + 1)
        assertTrue(compose.onNodeWithTag("group_member_choice_false_Local").fetchSemanticsNode().boundsInWindow.bottom <= dock.top)
        capture("member_dock_ime")
        compose.onNodeWithTag("group_member_search_cancel").performClick()
        compose.waitUntil(5_000) { imeTop() == null }
        compose.onNodeWithTag("group_member_picker_back").assertIsDisplayed()
        assertEquals("", compose.onNodeWithTag("group_member_search").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("group_member_picker_back").performClick()
        compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("group_member_label_0"))
        compose.onNodeWithTag("group_member_label_0").assertTextEquals("DIRECT")
        compose.onAllNodesWithTag("group_member_label_1").assertCountEquals(0)
        assertEquals(0, writes)
        capture("member_dock_returned")
    }

    @Test fun overviewSingleGroupConfirmWritesOnceAndReturnsWithoutExtraSave() {
        assertPortraitDefaultFontConfiguration()
        var writes = 0
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(210, "直接编辑"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, initialGroupIndex = 0, onSaveDraft = { writes++; saved = it })
        } }
        compose.onNodeWithTag("proxy_group_name").performClick()
        compose.onNodeWithTag("group_value_input").performTextReplacement("New")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.runOnIdle {
            assertEquals(1, writes)
            assertEquals(listOf("New", "Other"), checkNotNull(saved).groups.map { it.name })
            assertEquals(listOf("New"), checkNotNull(saved).groups.last().strings("proxies"))
        }
    }

    @Test fun listCopyDeleteAndLongPressReorderKeepIndependentIdentity() {
        assertPortraitDefaultFontConfiguration()
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(211, "管理策略"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, {}, sourceContent = source, onSaveDraft = { saved = it })
        } }
        compose.onNodeWithTag("proxy_group_menu_0").performClick()
        compose.onNodeWithTag("proxy_group_copy_0").assertIsDisplayed()
        compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f, centerY)) }
        compose.onNodeWithTag("proxy_group_copy_0").assertDoesNotExist()
        compose.onNodeWithTag("proxy_group_menu_0").performClick()
        compose.onNodeWithText("复制").performClick()
        compose.onNodeWithText("Main 副本").assertIsDisplayed()
        compose.onNodeWithTag("proxy_group_menu_1").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("Main 副本").assertDoesNotExist()
        compose.onNodeWithTag("proxy_group_sort").performClick()
        compose.onNodeWithTag("proxy_group_row_0").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveTo(center + Offset(0f, height * 1.2f), delayMillis = 350)
            up()
        }
        compose.onNodeWithTag("proxy_group_list_save").performClick()
        compose.runOnIdle { assertEquals(listOf("Other", "Main"), checkNotNull(saved).groups.map { it.name }) }
    }

    @Test fun groupFormMembersOptionsAndQuickYamlWorkAcrossThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark) {
            ProfileProxyGroupsEditorDialog(Profile.custom(if (dark) 202 else 201, "家庭策略"),
                ProfileProxyGroupsEditor.extractGroups(source), false, {}, {}, sourceContent = source, onSaveDraft = { saved = it })
        } } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; saved = null }
            capture("groups_list_$theme")
            compose.onNodeWithTag("proxy_group_row_0").performClick()
            compose.onNodeWithTag("proxy_group_name").performClick()
            compose.onNodeWithTag("group_value_input").performTextReplacement("Primary")
            compose.onNodeWithTag("group_value_confirm").performClick()

            compose.onNodeWithTag("group_members_add").performScrollTo().performClick()
            compose.onNodeWithTag("group_member_category_local").performClick()
            compose.onNodeWithText("Local").performClick()
            compose.onNodeWithTag("group_member_category_providers").performScrollTo().performClick()
            compose.onNodeWithText("cloud").performScrollTo().performClick()
            capture("groups_member_picker_$theme")
            compose.onNodeWithTag("group_member_picker_back").performClick()
            val sheetHeight = compose.onNodeWithTag("proxy_group_editor_sheet").fetchSemanticsNode().boundsInRoot.height
            val viewportHeight = compose.onNodeWithTag("connection_sheet_viewport").fetchSemanticsNode().boundsInRoot.height
            if (sheetHeight < viewportHeight * .8f) compose.onNodeWithTag("connection_detail_handle").performClick()
            compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("group_member_label_0"))
            compose.onNodeWithTag("group_member_row_PROXY:DIRECT").performTouchInput {
                down(center); advanceEventTime(650); moveTo(center + Offset(0f, height * 1.2f), delayMillis = 350); up()
            }
            capture("groups_members_$theme")

            compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("proxy_group_add_option"))
            compose.onNodeWithTag("proxy_group_add_option").performClick()
            compose.onNodeWithTag("group_add_timeout").performScrollTo().performClick()
            compose.onNodeWithTag("group_add_timeout").assertDoesNotExist()
            capture("groups_add_options_$theme")
            compose.onNodeWithTag("proxy_group_add_option_back").performClick()
            compose.onNodeWithTag("group_option_timeout").performScrollTo().performClick()
            compose.onNodeWithTag("group_value_input").performTextReplacement("-1")
            compose.onNodeWithTag("group_value_confirm").performClick()
            compose.onNodeWithText("请输入非负整数").assertIsDisplayed()
            compose.onNodeWithTag("group_value_input").performTextReplacement("8000")
            compose.onNodeWithTag("group_value_confirm").performClick()
            capture("groups_options_$theme")
            compose.onNodeWithTag("proxy_group_quick_yaml").performClick()
            compose.onNodeWithTag("proxy_group_yaml_content").assertTextContains("future: keep", substring = true)
            capture("groups_quick_yaml_$theme")
            compose.onNodeWithTag("proxy_group_yaml_confirm").performClick()
            compose.onNodeWithTag("proxy_group_form_confirm").performClick()
            compose.onNodeWithTag("proxy_group_sort").performClick()
            compose.onNodeWithTag("group_move_down_0").performClick()
            compose.onNodeWithTag("proxy_group_list_save").performClick()
            compose.runOnIdle {
                val values = checkNotNull(saved).groups
                assertEquals(listOf("Other", "Primary"), values.map { it.name })
                assertEquals(listOf("Primary"), values.first().strings("proxies"))
                assertEquals("8000", values.last().text("timeout"))
                assertEquals(listOf(ProxyGroupMember("Local"), ProxyGroupMember("DIRECT"), ProxyGroupMember("cloud", true)), ProxyGroupDrafts.members(values.last()))
                assertTrue(values.last().fields.has("future"))
            }
        }
    }

    @Test fun addingAllOptionsReturnsToDraftWithoutSavingParent() {
        assertPortraitDefaultFontConfiguration()
        var writes = 0
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(212, "连续设置"), ProfileProxyGroupsEditor.extractGroups(source), false,
                {}, { writes++ }, sourceContent = source, onSaveDraft = { writes++ })
        } }
        compose.onNodeWithTag("proxy_group_row_0").performClick()
        compose.onNodeWithTag("group_form_list").performScrollToNode(hasTestTag("proxy_group_add_option"))
            compose.onNodeWithTag("proxy_group_add_option").performClick()
        val options = listOf("empty-fallback", "url", "interval", "timeout", "max-failed-times", "lazy", "expected-status", "default-selected", "hidden", "disable-udp")
        options.forEachIndexed { index, option ->
            compose.onNodeWithTag("group_add_$option").performScrollTo().performClick()
            compose.onNodeWithTag("group_add_$option").assertDoesNotExist()
            if (index < options.lastIndex) compose.onNodeWithTag("proxy_group_add_option_back").assertIsDisplayed()
        }
        compose.onNodeWithTag("proxy_group_add_option_back").assertDoesNotExist()
        compose.onNodeWithTag("proxy_group_form_confirm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, writes) }
        capture("groups_all_options_returned_to_draft")
        compose.onNodeWithTag("proxy_group_form_back").performClick()
        compose.onNodeWithText("放弃此策略组修改？").assertIsDisplayed()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("proxy_group_row_0").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun emptyGroupBlocksConfirmAndCancelDoesNotWrite() {
        assertPortraitDefaultFontConfiguration()
        var dismissed = false
        var writes = 0
        compose.setContent { KClashTheme {
            ProfileProxyGroupsEditorDialog(Profile.custom(203, "新建策略"), "", false,
                { dismissed = true }, { writes++ }, initialGroupIndex = -1, onSaveDraft = { writes++ })
        } }
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.onNodeWithText("策略组名称不能为空").assertIsDisplayed()
        compose.onNodeWithTag("proxy_group_name").performClick()
        compose.onNodeWithTag("group_value_input").performTextInput("Empty")
        compose.onNodeWithTag("group_value_confirm").performClick()
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.onNodeWithText("策略组没有节点来源：Empty").assertIsDisplayed()
        compose.onNodeWithTag("proxy_group_form_back").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("proxy_group_list_back").performClick()
        assertTrue(dismissed)
        assertEquals(0, writes)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
