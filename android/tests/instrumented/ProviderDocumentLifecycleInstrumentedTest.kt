package com.lonnnnnng.flclash.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：用正式面板、编辑页及ViewModel验证后台/重建边界，固定运行时不冒充实核或冷进程恢复。 */
class ProviderDocumentLifecycleInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ProviderDocumentTestActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    private fun open(edit: Boolean = true) {
        compose.waitUntil(5_000) { compose.activity.model.providers.value.providers != null }
        if (edit) {
            compose.onNodeWithTag("profile_provider_menu_0").performClick()
            compose.onNodeWithTag("profile_provider_edit_0").performClick()
        } else compose.onNodeWithTag("profile_provider_row_0").performClick()
        compose.waitUntil(5_000) { compose.activity.model.document.value?.loading == false }
    }
    private fun editor() = compose.onNodeWithTag("profile_provider_document_content")

    @Test fun longFailedDraftKeepsSelectionAcrossBackgroundAndRecreationWithoutSavingBodyInBundle() {
        open()
        val marker = "provider-private-body-marker-20261106"
        val draft = (1..180).joinToString("\n") { "# 第${it}行测试正文" } + "\n$marker"
        editor().performTextReplacement(draft)
        val range = TextRange(draft.length - marker.length, draft.length)
        editor().performSemanticsAction(SemanticsActions.SetSelection) { it(range.start, range.end, false) }
        compose.runOnIdle { compose.activity.fixture.failSave = true }
        compose.onNodeWithTag("profile_provider_document_save").performClick()
        compose.onNodeWithTag("profile_provider_document_error").assertTextEquals("测试保存失败，输入已保留")
        // 作者 long：保存按钮会改变输入焦点；在后台前重新建立选区，隔离验证Activity保存/恢复而不把焦点折叠误判为重建丢失。
        editor().performSemanticsAction(SemanticsActions.SetSelection) { it(range.start, range.end, false) }
        editor().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, range))
        val old = compose.activity
        val model = old.model
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(draft, model.document.value?.content)
        assertEquals(1, old.fixture.saves)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        editor().assertTextEquals(draft)
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { assertSame(model, compose.activity.model) }
        editor().assertTextEquals(draft)
        editor().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, range))
        compose.onNodeWithTag("profile_provider_document_error").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().any {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) && it.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0
        })
        // 作者 long：实际序列化系统状态，按三种编码检查正文标记，避免仅凭实现方式推断未落Bundle。
        assertTrue(old.savedBytes.isNotEmpty())
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val needle = marker.toByteArray(charset).asList()
            assertFalse(old.savedBytes.asList().windowed(needle.size).any { it == needle })
        }
        capture("provider_document_failed_recreated")
        compose.runOnIdle { compose.activity.fixture.failSave = false }
        compose.onNodeWithTag("profile_provider_document_save").performClick()
        compose.waitUntil(5_000) { model.document.value == null }
        assertEquals(draft, compose.activity.fixture.content); assertEquals(2, compose.activity.fixture.saves)
        compose.onNodeWithTag("profile_providers").assertIsDisplayed()
    }

    @Test fun discardConfirmationSurvivesRecreationAndReturnsToSameProviderPanel() {
        open(); editor().performTextReplacement("example.net")
        closeSoftKeyboard()
        compose.onNodeWithTag("profile_provider_document_back").performClick()
        compose.onNodeWithText("保存更改？").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("保存更改？").assertIsDisplayed()
        capture("provider_document_discard_recreated")
        compose.onNodeWithText("继续编辑").performClick()
        editor().assertTextEquals("example.net")
        compose.onNodeWithTag("profile_provider_document_back").performClick()
        compose.onNodeWithText("放弃").performClick()
        compose.onNodeWithTag("profile_providers").assertIsDisplayed()
        assertNull(compose.activity.model.document.value)
        assertEquals(0, compose.activity.fixture.saves)
        open(); editor().assertTextEquals("example.com")
        compose.onNodeWithTag("profile_provider_document_back").performClick()
        compose.onNodeWithTag("profile_provider_document").assertDoesNotExist()
    }

    @Test fun acceptedSaveCompletesAcrossBackgroundAndRecreationOnlyOnce() {
        open(); editor().performTextReplacement("example.org")
        closeSoftKeyboard()
        val gate = CompletableDeferred<Unit>()
        compose.runOnIdle { compose.activity.fixture.saveGate = gate }
        compose.onNodeWithTag("profile_provider_document_save").performClick()
        compose.onNodeWithTag("profile_provider_document_saving").assertIsDisplayed()
        compose.runOnIdle { repeat(3) { compose.activity.model.saveProvider() }; compose.activity.model.closeProvider() }
        val model = compose.activity.model
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(model.document.value?.saving == true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { assertSame(model, compose.activity.model) }
        editor().assertTextEquals("example.org")
        compose.onNodeWithTag("profile_provider_document_save").assertDoesNotExist()
        compose.onNodeWithTag("profile_provider_document_saving").assertIsDisplayed()
        // 作者 long：本例保存仍挂起，只检查真正完成入场的页面，不能把半透明位移中间帧当作稳定保存态。
        val page = compose.onNode(hasTestTag("profile_editor_page") and hasAnyDescendant(hasTestTag("profile_provider_document_saving")))
        compose.waitUntil(5_000) { kotlin.math.abs(page.fetchSemanticsNode().boundsInWindow.left) < 1f }
        assertEquals(0f, page.fetchSemanticsNode().boundsInWindow.left, 1f)
        assertTrue(model.document.value?.saving == true)
        assertEquals("example.com", compose.activity.fixture.content)
        capture("provider_document_saving_recreated")
        assertEquals(1, compose.activity.fixture.saves)
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitUntil(5_000) { model.document.value == null }
        assertEquals(1, compose.activity.fixture.saves); assertEquals("example.org", compose.activity.fixture.content)
        compose.onNodeWithTag("profile_providers").assertIsDisplayed()
    }

    @Test fun previewReadFinishesInBackgroundAndExplicitCloseRejectsOldRead() {
        val gate = CompletableDeferred<Unit>()
        compose.runOnIdle { compose.activity.fixture.readGate = gate }
        compose.waitUntil(5_000) { compose.activity.model.providers.value.providers != null }
        compose.onNodeWithTag("profile_provider_row_0").performClick()
        val model = compose.activity.model
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { gate.complete(Unit) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { model.document.value?.loading == false }
        editor().assertTextEquals("example.com")
        compose.activityRule.scenario.recreate()
        editor().assertTextEquals("example.com")
        capture("provider_document_preview_recreated")
        compose.onNodeWithTag("profile_provider_document_back").performClick()
        val late = CompletableDeferred<Unit>()
        compose.runOnIdle { compose.activity.fixture.readGate = late }
        compose.onNodeWithTag("profile_provider_row_0").performClick()
        compose.onNodeWithTag("profile_provider_document_back").performClick()
        compose.runOnIdle { late.complete(Unit) }
        compose.onNodeWithTag("profile_provider_document").assertDoesNotExist()
        assertNull(model.document.value); assertEquals(0, compose.activity.fixture.saves)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 作者 long：系统Window退场有450ms且不受Compose时钟驱动，跨过该窗口后才采静态终帧。
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(650)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
