package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileDocumentUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longDocumentSearchScrollsToMatchAndCopyExcludesGutter() {
        assertPortraitDefaultFontConfiguration()
        // 作者 long：长正文的屏外匹配必须真实进入视口；行号只用于阅读，复制不能污染返回父草稿的原始字节。
        val original = (1..1200).joinToString("\n") { "# 配置正文第${it}行" } + "\nmode: rule"
        var state by mutableStateOf(ProfileDocumentEditorState(1, 1, original = original, content = original))
        compose.setContent { KClashTheme { ProfileDocumentEditor(state, { state = state.copy(content = it) }, {}, {}) } }
        compose.onNodeWithTag("profile_document_find").performClick()
        compose.onNodeWithTag("profile_document_query").performTextInput("mode: rule")
        compose.onNodeWithTag("profile_document_next").performClick()
        compose.waitForIdle()
        val editor = compose.onNodeWithTag("profile_editor_content")
        assertEquals(TextRange(original.length - 10, original.length), editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        assertTrue("查找必须滚到屏外匹配", compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().any {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) && it.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0
        })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        val before = clipboard.primaryClip
        try {
            editor.performSemanticsAction(SemanticsActions.CopyText) { assertTrue(it()) }
            compose.runOnIdle { assertEquals("mode: rule", clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
            capture("profile_document_long_match")
            assertEquals(original, state.content)
        } finally {
            compose.runOnIdle { if (before == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(before) }
        }
    }

    @Test fun textSearchReplaceUndoAndValidationFailureKeepDraftUntilParentAccepts() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(ProfileDocumentEditorState(1, 1, original = "rules: []\nmode: rule", content = "rules: []\nmode: rule"))
        var dark by mutableStateOf(false)
        var accepted = 0; var dismissed = 0; var reject = true
        compose.setContent { KClashTheme(darkTheme = dark) { ProfileDocumentEditor(state, { state = state.copy(content = it) }, {
            if (reject) state = state.copy(error = "校验失败，输入已保留") else accepted++
        }, { dismissed++ }) } }
        compose.onNodeWithTag("profile_document_find").performClick()
        compose.onNodeWithTag("profile_document_query").performTextInput("rule")
        compose.onNodeWithTag("profile_document_replacement").performTextInput("direct")
        compose.onNodeWithTag("profile_document_replace_all").performClick()
        assertTrue(state.content.contains("direct"))
        compose.onNodeWithTag("profile_document_history").performClick()
        compose.onNodeWithTag("profile_document_undo").performClick()
        compose.waitForIdle()
        assertEquals(state.original, state.content)
        compose.onNodeWithTag("profile_document_history").performClick()
        compose.onNodeWithTag("profile_document_redo").performClick()
        compose.onNodeWithTag("profile_document_save").performClick()
        compose.onNodeWithTag("profile_document_error").assertTextContains("输入已保留", substring = true)
        capture("profile_document_light_failed_search")
        compose.runOnIdle { dark = true }
        compose.onNodeWithTag("profile_document_back").performClick()
        capture("profile_document_dark_exit")
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("profile_editor_content").assertTextContains("direct", substring = true)
        compose.runOnIdle { reject = false }
        compose.onNodeWithTag("profile_document_save").performClick()
        assertEquals(1, accepted); assertEquals(0, dismissed)
    }

    @Test fun remoteBodyWarningCanKeepOrDisableAutoUpdateAndFailureDoesNotDropParentDraft() {
        assertPortraitDefaultFontConfiguration()
        val profile = Profile.remote(2, "https://example.test/sub", "家庭订阅").copy(autoUpdate = true)
        var state by mutableStateOf(RemoteProfileEditorUiState(profile, document = ProfileDocumentSnapshot(profile, "rules: []", "digest", 0), draftContent = "rules: []"))
        var dark by mutableStateOf(false)
        val choices = mutableListOf<Boolean>()
        compose.setContent { KClashTheme(darkTheme = dark) {
            RemoteProfileEditorDialog(profile, false, {}, { _, _, auto, _ -> choices += auto; state = state.copy(error = "保存失败，输入已保留") }, state = state,
                onDraftChange = { state = state.copy(draft = it) })
        } }
        compose.onNodeWithTag("remote_profile_editor_save").performClick()
        capture("remote_document_light_auto_warning")
        compose.onNodeWithTag("remote_profile_keep_auto_save").performClick()
        assertEquals(listOf(true), choices)
        compose.onNodeWithTag("remote_profile_editor_error").performScrollTo().assertTextContains("输入已保留", substring = true)
        compose.runOnIdle { dark = true }
        compose.onNodeWithTag("remote_profile_editor_save").performClick()
        compose.onNodeWithTag("remote_profile_disable_auto_save").performClick()
        assertEquals(listOf(true, false), choices)
        assertFalse(state.draft.autoUpdate)
        capture("remote_document_dark_failed_parent")
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
