package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

private fun captureResourceOptions(name: String) {
    android.os.SystemClock.sleep(450)
    val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    recordVisualPng("vis073_$name", ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray())
}

/** 作者 long：比较真实文字布局及提交原文；面板测试覆盖等待回执、失败重试与退出，而不是只断言标签存在。 */
class ResourceOptionsTransitionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun uriCaretTraversesEveryDotBoundaryAndDeletesOnlyRawCharacters() {
        prepareTouchVisualCapture()
        val raw = "https://a.b/c.d?x=e.f"
        var value by mutableStateOf(TextFieldValue(raw))
        compose.setContent { KClashTheme {
            FloatingInputDialog("URL", "uri_caret", false, true, {}, {}, "保存") { enabled, submit ->
                FloatingInputField(value, { value = it }, "uri_caret_input", enabled, submit, keyboardType = KeyboardType.Uri)
            }
        } }
        val field = compose.onNodeWithTag("uri_caret_input")
        field.performClick()
        for (position in 1..raw.length) {
            compose.runOnIdle { value = TextFieldValue(raw, androidx.compose.ui.text.TextRange(position)) }
            field.performKeyInput { pressKey(Key.DirectionLeft) }
            compose.runOnIdle { assertEquals("left from $position", position - 1, value.selection.start); assertEquals(raw, value.text) }
        }
        for (position in 0 until raw.length) {
            compose.runOnIdle { value = TextFieldValue(raw, androidx.compose.ui.text.TextRange(position)) }
            field.performKeyInput { pressKey(Key.DirectionRight) }
            compose.runOnIdle { assertEquals("right from $position", position + 1, value.selection.start); assertEquals(raw, value.text) }
        }
        val afterDot = raw.lastIndexOf('.') + 1
        compose.runOnIdle { value = TextFieldValue(raw, androidx.compose.ui.text.TextRange(afterDot)) }
        field.performKeyInput { pressKey(Key.Backspace) }
        compose.runOnIdle { assertEquals(raw.removeRange(afterDot - 1, afterDot), value.text) }
        field.assertRawInputEquals(raw.removeRange(afterDot - 1, afterDot))
    }

    @Test fun newScriptImportKeepsLongUrlErrorAndBusyUntilSuccessfulExit() {
        prepareTouchVisualCapture()
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        var closing by mutableStateOf(false); var open by mutableStateOf(true)
        var writes = 0; var saved = ""; var label = "unexpected"
        compose.setContent { KClashTheme {
            if (open) ScriptRemoteDialog(busy, { open = false }, { name, url -> label = name; saved = url; writes++; busy = true },
                error = error, closing = closing, onClosed = { open = false })
        } }
        val url = "https://example.invalid/" + "long-directory/".repeat(10) + "script.js?raw=a%2Fb"
        compose.onNodeWithTag("script_remote_submit").assertIsNotEnabled()
        compose.onNodeWithTag("script_remote_url").performTextReplacement(url)
        captureResourceOptions("new_script_long")
        compose.onNodeWithTag("script_remote_url").performImeAction()
        compose.onNodeWithTag("script_remote_submit").assertIsNotEnabled()
        compose.onNodeWithTag("script_remote_back").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, writes); busy = false; error = "下载失败，请重试" }
        compose.onNodeWithTag("script_remote_url").assertRawInputEquals(url)
        captureResourceOptions("new_script_failure")
        compose.onNodeWithTag("script_remote_submit").performClick()
        compose.runOnIdle { assertEquals(2, writes); assertEquals("", label); assertEquals(url, saved); busy = false; error = null; closing = true }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("script_remote_page").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun uriWrapKeepsFlutterFilenameTogetherAndPreservesRawInput() {
        prepareTouchVisualCapture()
        val initial="https://example.invalid/country.mmdb"
        var value by mutableStateOf(TextFieldValue(initial));var saved=""
        compose.setContent { KClashTheme {
            FloatingInputDialog("MMDB","url_wrap",false,true,{}, { saved=value.text },"保存") { enabled,submit ->
                FloatingInputField(value,{value=it},"url_wrap_input",enabled,submit,keyboardType=KeyboardType.Uri)
            }
        } }
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("url_wrap_input").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout=layouts.single()
        assertEquals(2,layout.lineCount)
        assertEquals("https://example.invalid/",layout.layoutInput.text.text.substring(0,layout.getLineEnd(0)).replace("\u2060",""))
        captureResourceOptions("url_wrap_initial")
        val longUrl="https://example.invalid/"+"directory-name/".repeat(18)+"country.mmdb?name=%E4%B8%AD%E6%96%87&x=a%2Fb&empty="
        compose.onNodeWithTag("url_wrap_input").performTextReplacement(longUrl)
        val clipboard=compose.activity.getSystemService(android.content.ClipboardManager::class.java)
        var previous: android.content.ClipData? = null
        compose.runOnIdle { previous=clipboard.primaryClip }
        try {
            val displayLength=UriDisplayTransformation.filter(androidx.compose.ui.text.AnnotatedString(longUrl)).text.length
            compose.onNodeWithTag("url_wrap_input").performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(0,displayLength,false)) }
            compose.onNodeWithTag("url_wrap_input").performSemanticsAction(SemanticsActions.CopyText) { assertTrue(it()) }
            compose.runOnIdle { assertEquals(longUrl,clipboard.primaryClip!!.getItemAt(0).text.toString()) }
        } finally { compose.runOnIdle { previous?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip() } }
        compose.onNodeWithTag("url_wrap_input").performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(8,8,false)) }
        compose.onNodeWithTag("url_wrap_input").performTextInput("z")
        compose.onNodeWithTag("url_wrap_input").performImeAction()
        assertEquals(longUrl.substring(0,8)+"z"+longUrl.substring(8),saved)
        captureResourceOptions("url_wrap_long")
    }

    @Test fun urlBreakStrategyDiagnostic() {
        prepareTouchVisualCapture()
        val value="https://example.invalid/country.mmdb"
        val candidates=listOf("simple" to LineBreak.Simple,"paragraph" to LineBreak.Paragraph,"heading" to LineBreak.Heading,
            "balanced-strict" to LineBreak(LineBreak.Strategy.Balanced,LineBreak.Strictness.Strict,LineBreak.WordBreak.Default))
        compose.setContent { KClashTheme {
            Column { candidates.forEach { (name,style) -> BasicTextField(value,{},Modifier.width(288.dp).testTag("wrap_$name"),
                textStyle=MaterialTheme.typography.bodyLarge.copy(lineBreak=style)) } }
        } }
        candidates.forEach { (name,_) ->
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("wrap_$name").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                putString("uri_break_strategy",name);putInt("uri_first_line_end",layouts.single().getLineEnd(0))
            })
        }
    }

    @Test fun scriptUrlDialogKeepsLongDraftDuringFailureAndWaitsForSuccessfulExit() {
        prepareTouchVisualCapture()
        var open by mutableStateOf(true);var busy by mutableStateOf(false);var error by mutableStateOf<String?>(null)
        var closing by mutableStateOf(false);var dark by mutableStateOf(false);var writes=0;var raw=""
        val script=Script(731,"远程脚本","https://example.invalid/scripts/long-script.js")
        compose.setContent { KClashTheme(darkTheme=dark) {
            if(open) ScriptRemoteDialog(busy,{open=false},{_,url->writes++;raw=url;busy=true},script,error,
                closing=closing,onClosed={open=false})
        } }
        captureResourceOptions("script_light_initial")
        compose.runOnIdle { dark=true }
        val url="https://example.invalid/"+"long-directory/".repeat(15)+"script.js?raw=a%2Fb&query=%E4%B8%AD"
        compose.onNodeWithTag("script_remote_url").performTextReplacement(url)
        captureResourceOptions("script_dark_long_ime")
        compose.onNodeWithTag("script_remote_url").performImeAction()
        compose.onNodeWithTag("script_remote_submit").assertIsNotEnabled()
        compose.onNodeWithTag("script_remote_back").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1,writes);busy=false;error="下载失败，请重试" }
        compose.onNodeWithTag("script_remote_url").assertRawInputEquals(url)
        captureResourceOptions("script_dark_error")
        compose.onNodeWithTag("script_remote_submit").performClick()
        compose.runOnIdle { assertEquals(2,writes);assertEquals(url,raw);busy=false;error=null;closing=true }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("script_remote_page").fetchSemanticsNodes().isEmpty() }
    }
}
