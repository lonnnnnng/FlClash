package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.runtime.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：脚本 UI 测试只验证本地资源管理入口和回调边界，不启动 JavaScript 引擎或核心。
 */
@RunWith(AndroidJUnit4::class)
class ScriptsUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun remoteMenusFileEntryProgressAndBothThemesUseWholePages() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        val remote = Script(2L, "远程脚本", "https://example.test/a.js", lastUpdateAt = System.currentTimeMillis())
        var file = 0; var source = 0; var sync = 0
        var busy by mutableStateOf(false)
        composeRule.setContent { KClashTheme(darkTheme = dark) {
            ScriptsLibraryDialog(ScriptsUiState(listOf(Script(1L, "本地脚本"), remote), busy,
                if (busy) setOf(2L) else emptySet()), {}, {}, {}, {}, {}, {}, { sync++ }, { file++ }, { source++ })
        } }
        capture("scripts_light_list")
        composeRule.onNodeWithTag("script_menu_2").performClick()
        composeRule.onNodeWithTag("script_url_2").performClick()
        assertEquals(1, source)
        composeRule.onNodeWithTag("script_menu_2").performClick()
        composeRule.onNodeWithTag("script_sync_2").performClick()
        assertEquals(1, sync)
        composeRule.onNodeWithTag("scripts_add").performClick()
        composeRule.onNodeWithTag("scripts_import_file").performClick()
        assertEquals(1, file)
        composeRule.runOnIdle { dark = true; busy = true }
        composeRule.onNodeWithTag("scripts_add").assertIsNotEnabled()
        capture("scripts_dark_syncing")
        composeRule.runOnIdle { busy = false }
        composeRule.onNodeWithTag("scripts_sort").performClick()
        capture("scripts_dark_order")
    }

    @Test
    fun editorSearchReplaceUndoAndUnsavedExitKeepFailedDraft() {
        assertPortraitDefaultFontConfiguration()
        var dismissed = 0
        var state by mutableStateOf(ScriptEditorUiState(isOpen = true, label = "示例", content = "const main = config => config;"))
        composeRule.setContent { KClashTheme(darkTheme = false) {
            ScriptEditorDialog(state, { dismissed++ }, { label, text -> state = state.copy(label = label, content = text, error = "保存失败，请重试") },
                { label, text -> state = state.copy(label = label, content = text) })
        } }
        composeRule.onNodeWithTag("script_find").performClick()
        composeRule.onNodeWithTag("script_query").performTextInput("config")
        composeRule.onNodeWithTag("script_replacement").performTextInput("value")
        composeRule.onNodeWithTag("script_replace_all").performClick()
        composeRule.onNodeWithTag("script_content").assertTextContains("const main = value => value;", substring = true)
        composeRule.onNodeWithTag("script_undo").performClick()
        composeRule.onNodeWithTag("script_content").assertTextContains("const main = config => config;", substring = true)
        composeRule.onNodeWithTag("script_redo").performClick()
        capture("script_editor_light_search")
        composeRule.onNodeWithTag("script_find").performClick()
        composeRule.onNodeWithTag("script_editor_back").performClick()
        composeRule.onNodeWithText("继续编辑").performClick()
        assertEquals(0, dismissed)
        composeRule.onNodeWithTag("script_save").performClick()
        composeRule.onNodeWithTag("script_editor_error").assertExists()
        capture("script_editor_light_failed")
        composeRule.onNodeWithTag("script_editor_back").performClick()
        composeRule.onNodeWithTag("script_discard").performClick()
        assertEquals(1, dismissed)
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }

    @Test
    fun libraryShowsScriptsAndForwardsReorderAndCreate() {
        val scripts = listOf(
            Script(1L, "第一个"),
            Script(2L, "第二个"),
        )
        var createClicked = false
        var order: List<Long>? = null
        composeRule.setContent {
            KClashTheme {
                ScriptsLibraryDialog(
                    state = ScriptsUiState(scripts = scripts),
                    onDismiss = {},
                    onCreate = { createClicked = true },
                    onEdit = {},
                    onDelete = {},
                    onReorder = { order = it },
                )
            }
        }

        composeRule.onNodeWithText("脚本库").assertIsDisplayed()
        composeRule.onNodeWithTag("scripts_add").performClick()
        composeRule.onNodeWithText("新建脚本").performClick()
        composeRule.onNodeWithTag("scripts_sort").performClick()
        composeRule.onNodeWithTag("scripts_down_1").performClick()

        assertTrue(createClicked)
        assertEquals(listOf(2L, 1L), order)
    }

    @Test
    fun editorForwardsLabelAndJavascriptContent() {
        var saved: Pair<String, String>? = null
        composeRule.setContent {
            KClashTheme {
                ScriptEditorDialog(
                    state = ScriptEditorUiState(
                        isOpen = true,
                        label = "初始脚本",
                        content = "const main = (config) => config;",
                    ),
                    onDismiss = {},
                    onSave = { label, content -> saved = label to content },
                )
            }
        }

        composeRule.onNodeWithText("脚本名称").performTextClearance()
        composeRule.onNodeWithText("脚本名称").performTextInput("更新脚本")
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("更新脚本", saved?.first)
        assertEquals("const main = (config) => config;", saved?.second)
    }

    @Test
    fun remoteDialogForwardsUrlAndOptionalLabel() {
        var imported: Pair<String, String>? = null
        composeRule.setContent {
            KClashTheme {
                ScriptRemoteDialog(
                    busy = false,
                    onDismiss = {},
                    onImport = { label, url -> imported = label to url },
                )
            }
        }

        composeRule.onNodeWithText("脚本名称（可选）").performTextInput("远程脚本")
        composeRule.onNodeWithText("HTTP(S) 脚本地址").performTextInput("http://127.0.0.1/script.js")
        composeRule.onNodeWithText("导入").performClick()

        assertEquals("远程脚本" to "http://127.0.0.1/script.js", imported)
    }
}
