package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        composeRule.onNodeWithText("新建脚本").performClick()
        composeRule.onAllNodesWithText("下移")[0].performClick()

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
}
