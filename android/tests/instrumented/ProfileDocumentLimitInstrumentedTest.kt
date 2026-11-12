package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.MAX_PROFILE_FILE_BYTES
import com.lonnnnnng.flclash.data.readAuthorizedProxyProfile
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：只在内存给授权原文追加注释达到上限，不另造节点或把机密正文发到日志/截图。 */
class ProfileDocumentLimitInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun matchAcrossWindowAndLongSelectionReplaceWholeMatchWithoutLosingSurroundingText() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("kclashDocumentLimit") == "true")
        val original = eightMiBAuthorizedDocument()
        var value by mutableStateOf(TextFieldValue(original, TextRange(4090, 4110)))
        compose.setContent { KClashTheme { ProfileCodeField(value, { value = it }, true, Modifier.fillMaxSize()) } }
        compose.waitForIdle()
        compose.onNodeWithTag("profile_editor_content").performSemanticsAction(SemanticsActions.InsertTextAtCursor) { it(AnnotatedString("Z")) }
        compose.waitForIdle()
        assertTrue(value.text == original.replaceRange(4090, 4110, "Z"))
        compose.runOnIdle { value = TextFieldValue(original, TextRange(9000, 0)) }
        compose.waitForIdle()
        // 作者 long：Compose的InsertTextAtCursor语义动作直接用反向start/end执行replaceRange；此处走输入法提交命令验证真实编辑路径。
        compose.onNodeWithTag("profile_editor_content").performTextInput("Z")
        compose.waitForIdle()
        assertTrue(value.text == original.replaceRange(0, 9000, "Z"))
    }

    @Test fun eightMiBDocumentLoadsFindsEditsUndoesAndKeepsExitDraft() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("kclashDocumentLimit") == "true")
        assertPortraitDefaultFontConfiguration()
        val marker = "# kclash-limit-end-X"
        val original = eightMiBAuthorizedDocument()
        assertEquals(MAX_PROFILE_FILE_BYTES, original.toByteArray().size)
        var state by mutableStateOf(ProfileDocumentEditorState(1, 1, original = original, content = original))
        var saved = 0
        var dismissed = 0
        fun checkpoint(name: String, since: Long) {
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_limit_step", name)
                putLong("elapsed_ms", SystemClock.elapsedRealtime() - since)
                putLong("java_used_bytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
            })
        }
        var since = SystemClock.elapsedRealtime()
        compose.setContent { KClashTheme { ProfileDocumentEditor(state, { state = state.copy(content = it) }, { saved++ }, { dismissed++ }) } }
        compose.waitForIdle(); checkpoint("load", since)
        compose.onNodeWithTag("profile_code_last").performClick()
        compose.waitForIdle()
        // 作者 long：仅截图内存追加的注释尾段，原始节点/订阅正文不进入图片或测试输出。
        val tail = compose.onNodeWithTag("profile_editor_content").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertTrue(tail.endsWith(marker) && tail.length < 8192 && tail.all { it in "# x\nkclash-limit-end-X" })
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val image = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, image)
        recordVisualPng("profile_limit_tail", image.toByteArray())
        compose.onNodeWithTag("profile_code_previous").performClick()
        compose.onNodeWithTag("profile_code_next").performClick()
        compose.onNodeWithTag("profile_code_first").performClick()
        compose.waitForIdle()
        assertTrue(state.content == original)
        since = SystemClock.elapsedRealtime()
        compose.onNodeWithTag("profile_document_find").performClick()
        compose.onNodeWithTag("profile_document_query").performTextInput(marker)
        compose.onNodeWithTag("profile_document_next").performClick()
        compose.waitForIdle(); checkpoint("find_tail", since)
        since = SystemClock.elapsedRealtime()
        val visibleLength = compose.onNodeWithTag("profile_editor_content").fetchSemanticsNode().config[SemanticsProperties.EditableText].length
        compose.onNodeWithTag("profile_editor_content").performSemanticsAction(SemanticsActions.SetSelection) { it(visibleLength - 1, visibleLength, false) }
        compose.onNodeWithTag("profile_editor_content").performSemanticsAction(SemanticsActions.InsertTextAtCursor) { it(AnnotatedString("Y")) }
        compose.waitForIdle(); checkpoint("edit", since)
        assertTrue(state.content.endsWith("end-Y"))
        since = SystemClock.elapsedRealtime()
        compose.onNodeWithTag("profile_document_history").performClick()
        compose.onNodeWithTag("profile_document_undo").assertIsEnabled().performClick()
        compose.waitForIdle(); checkpoint("undo", since)
        assertTrue("撤销必须逐字恢复上限正文", state.content == original)
        compose.onNodeWithTag("profile_document_history").performClick()
        compose.onNodeWithTag("profile_document_redo").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue(state.content.endsWith("end-Y"))
        compose.onNodeWithTag("profile_document_back").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        assertEquals(0, dismissed); assertEquals(0, saved)
        assertTrue(state.content.endsWith("end-Y"))
        compose.onNodeWithTag("profile_document_save").performClick()
        assertEquals(1, saved)
    }
}

internal fun eightMiBAuthorizedDocument(): String {
    val source = readAuthorizedProxyProfile()
    val marker = "# kclash-limit-end-X"
    val padding = MAX_PROFILE_FILE_BYTES - source.size - marker.length - 1
    require(padding > 0)
    return buildString {
        append(source.toString(Charsets.UTF_8)); append('\n')
        repeat(padding / 256) { append("# "); append("x".repeat(253)); append('\n') }
        val tail = padding % 256
        if (tail > 0) { append('#'); repeat(tail - 1) { append(' ') } }
        append(marker)
    }
}
