package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：失败只注入隔离数据库；原1.yaml仅复制原字节或提取内存节点，正式库和Flutter均不写入。 */
@SdkSuppress(minSdkVersion = 29)
class LocalProxyInteractionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<LocalProxyDraftTestActivity>()
    private val model get() = compose.activity.model
    private val fixture get() = compose.activity.fixture
    private val row = LocalProxy("interaction", """{"name":"本地交互验收","type":"direct"}""")

    @Test fun longDeleteReferencesCancelKeepsParentAndFailedSaveRetriesOnce() = runBlocking {
        protected { bytes ->
            fixture.repository.saveLocalProxies(emptyList(), listOf(row))
            val source = fixture.repository.addFileProfile("引用正文原字节", bytes)
            val sourceFile = fixture.repository.profileFile(source.id)
            val original = signature(sourceFile)
            awaitRows()
            compose.runOnIdle { model.open(); model.edit(model.state.value.rows.single()) }
            val refs = (1..40).map { "引用配置-$it-" + "很长的配置名称".repeat(4) }
            // 作者 long：长名称只模拟查询结果；删除和失败回滚实际走隔离Repository与Room，不生成替代配置正文。
            compose.runOnIdle { fixture.referenceUsers = refs; model.askDelete(setOf(row.id)) }
            awaitIdle()
            compose.onNodeWithText(refs.first()).assertIsDisplayed()
            capture("local_delete_first")
            compose.onNodeWithText(refs.last()).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("local_proxy_delete_confirm").assertIsDisplayed()
            capture("local_delete_last")
            compose.onNodeWithTag("local_proxy_delete_cancel").performClick(); awaitIdle()
            assertNotNull(model.state.value.editor)
            assertEquals(listOf(row), fixture.repository.localProxies())
            compose.runOnIdle { model.askDelete(setOf(row.id)) }; awaitIdle()
            failWrites(true)
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            fixture.beforeSave = { entered.complete(Unit); release.await() }
            val beforeCalls = fixture.saveCalls
            compose.onNodeWithTag("local_proxy_delete_confirm").performClick()
            withTimeout(5_000) { entered.await() }
            compose.onNodeWithTag("local_proxy_delete_pending").assertIsDisplayed()
            compose.onNodeWithTag("local_proxy_delete_confirm").assertIsNotEnabled().performClick()
            compose.onNodeWithTag("local_proxy_delete_cancel").assertIsNotEnabled().performClick()
            back(); assertEquals(setOf(row.id), model.state.value.deleting)
            assertEquals(beforeCalls + 1, fixture.saveCalls)
            capture("local_delete_pending")
            release.complete(Unit); awaitIdle()
            compose.onNodeWithTag("local_proxy_delete_error").assertTextContains("删除失败，请重试；配置引用会保留").assertIsDisplayed()
            assertEquals(listOf(row), fixture.repository.localProxies()); assertEquals(original, signature(sourceFile))
            capture("local_delete_failed")
            compose.activityRule.scenario.recreate(); awaitIdle()
            compose.onNodeWithTag("local_proxy_delete_error").assertIsDisplayed()
            capture("local_delete_failed_recreated")
            failWrites(false); fixture.beforeSave = {}
            compose.onNodeWithTag("local_proxy_delete_confirm").performClick(); awaitIdle()
            assertTrue(fixture.repository.localProxies().isEmpty()); assertEquals(original, signature(sourceFile))
            assertNull(model.state.value.editor); assertTrue(model.state.value.deleting.isEmpty())
            assertEquals(beforeCalls + 2, fixture.saveCalls)
            capture("local_delete_saved")
        }
    }

    @Test fun listDeleteCancellationKeepsSelectionAndSecondBackExitsSelectionOnly() = runBlocking {
        protected {
            fixture.repository.saveLocalProxies(emptyList(), listOf(row)); awaitRows()
            compose.runOnIdle { model.open() }
            compose.onNodeWithTag("local_proxy_${row.id}").onChildren().filterToOne(isToggleable()).performClick()
            compose.onActionMarker("删除选中节点").performClick(); awaitIdle()
            compose.onNodeWithTag("local_proxy_delete_cancel").performClick(); awaitIdle()
            compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
            compose.activityRule.scenario.recreate(); awaitIdle()
            compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
            back(); compose.onNodeWithText("本地代理", substring = false).assertIsDisplayed()
            assertEquals(listOf(row), fixture.repository.localProxies())
            capture("local_delete_cancel_selection")
        }
    }

    @Test fun singleLinksImeBackDiscardAndRecreationKeepParentWithoutSaving() = linksBack(single = true)
    @Test fun batchLinksLongInputImeBackAndRecreationKeepDirectoryWithoutSaving() = linksBack(single = false)

    private fun linksBack(single: Boolean) = runBlocking {
        protected {
            fixture.repository.saveLocalProxies(emptyList(), listOf(row)); awaitRows()
            compose.runOnIdle { model.open(); if (single) model.edit(model.state.value.rows.single()) }
            if (single) compose.onActionMarker("从分享链接填写").performClick()
            else compose.onNodeWithTag("local_proxies_links").performClick()
            awaitIdle()
            val text = (1..40).joinToString("\n") { "invalid-link-$it-" + "long-input-".repeat(8) }
            val prefix = if (single) "local_links_single" else "local_links_batch"
            compose.onNodeWithTag("local_proxy_links_content").performTextReplacement(text)
            compose.onNodeWithTag("local_proxy_links_save").performClick(); awaitIdle()
            compose.onNodeWithTag("local_proxy_links_error").assertIsDisplayed()
            compose.onNodeWithTag("local_proxy_links_content").performClick()
            compose.onNodeWithTag("local_proxy_links_content").performTextInputSelection(TextRange(0))
            assertAboveIme("local_proxy_links_save", "local_proxy_links_error", "local_proxy_links_content")
            capture("${prefix}_first_ime")
            compose.onNodeWithTag("local_proxy_links_content").performTextInputSelection(TextRange(text.length))
            capture("${prefix}_last_ime")
            back(); compose.waitUntil(5_000) { focusedImeTop() == null }
            compose.onNodeWithTag("local_proxy_links_content").assertIsDisplayed()
            compose.onNodeWithText("放弃修改？").assertDoesNotExist()
            back(); compose.onNodeWithText("放弃修改？").assertIsDisplayed()
            capture("${prefix}_discard")
            compose.activityRule.scenario.recreate(); awaitIdle()
            compose.onNodeWithText("放弃修改？").assertIsDisplayed()
            compose.onNodeWithText("继续编辑", substring = false).performClick()
            assertEquals(text, value("local_proxy_links_content"))
            compose.onNodeWithTag("local_proxy_links_error").assertIsDisplayed()
            closeSoftKeyboard(); capture("${prefix}_recreated")
            back(); compose.onNodeWithText("放弃修改？").assertIsDisplayed()
            compose.onNodeWithText("放弃修改", substring = false).performClick(); awaitIdle()
            compose.onNodeWithTag("local_proxy_links_content").assertDoesNotExist()
            if (single) {
                compose.onNodeWithTag("local_proxy_save").assertIsDisplayed()
                assertEquals(row, model.state.value.editor!!.value)
                back(); awaitIdle()
            }
            compose.onNodeWithTag("local_proxies_page").assertIsDisplayed()
            assertEquals(listOf(row), fixture.repository.localProxies()); assertEquals(0, fixture.saveCalls)
            capture("${prefix}_returned")
        }
    }

    @Test fun actualCoreValidationAndDatabaseFailureRecoverAndSelectionSwitchRespectsAcceptanceBoundary() = runBlocking {
        protected { bytes ->
            val first = fixture.repository.addFileProfile("隔离原文件A", bytes)
            val second = fixture.repository.addFileProfile("隔离原文件B", bytes)
            fixture.repository.selectProfile(first.id)
            val definition = ProxyGroupDrafts.root(bytes.toString(Charsets.UTF_8)).getAsJsonArray("proxies").first().asJsonObject.deepCopy()
                .apply { addProperty("name", "原文件节点验收") }
            fixture.nodeDefinition = definition.deepCopy().apply { addProperty("type", "__invalid_type__") }
            awaitIdle(); compose.runOnIdle { model.open(); model.addFromRuntime(first.id, "原文件节点验收") }; awaitIdle()
            compose.onNodeWithTag("local_proxies_error").assertTextContains("核心拒绝此节点，请刷新代理列表后重试")
            assertTrue(fixture.repository.localProxies().isEmpty()); assertEquals(0, fixture.saveCalls)
            capture("local_add_core_rejected")
            fixture.nodeDefinition = definition
            failWrites(true)
            compose.runOnIdle { model.addFromRuntime(first.id, "原文件节点验收") }; awaitIdle()
            compose.onNodeWithTag("local_proxies_error").assertTextContains("添加未完成，请重新打开节点菜单后重试")
            assertTrue(fixture.repository.localProxies().isEmpty())
            capture("local_add_database_failed")
            failWrites(false)
            val validating = CompletableDeferred<Unit>(); val validated = CompletableDeferred<Unit>()
            fixture.beforeValidate = { validating.complete(Unit); validated.await() }
            compose.runOnIdle { model.addFromRuntime(first.id, "原文件节点验收") }
            withTimeout(5_000) { validating.await() }
            fixture.repository.selectProfile(second.id); validated.complete(Unit); awaitIdle()
            compose.onNodeWithTag("local_proxies_error").assertTextContains("配置已切换，请重新打开节点菜单")
            assertTrue(fixture.repository.localProxies().isEmpty())
            capture("local_add_profile_changed")
            fixture.beforeValidate = {}; fixture.repository.selectProfile(first.id)
            val saving = CompletableDeferred<Unit>(); val saved = CompletableDeferred<Unit>()
            fixture.beforeSave = { saving.complete(Unit); saved.await() }
            val calls = fixture.saveCalls
            compose.runOnIdle { model.addFromRuntime(first.id, "原文件节点验收") }
            withTimeout(5_000) { saving.await() }
            compose.runOnIdle { model.addFromRuntime(first.id, "原文件节点验收") }
            fixture.repository.selectProfile(second.id); saved.complete(Unit); awaitIdle()
            assertEquals(calls + 1, fixture.saveCalls)
            assertEquals("原文件节点验收", fixture.repository.localProxies().single().name)
            assertEquals(second.id, fixture.repository.currentProfileId.value)
            capture("local_add_accepted_original")
        }
    }

    private suspend fun protected(block: suspend (ByteArray) -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        val args = InstrumentationRegistry.getArguments()
        require(args.getString("kclashLocalInteractionAcceptance") == "true")
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = entry.profileRepository(); repo.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val rows = entry.database().profiles().getAll(); require(rows.isNotEmpty())
        val files = rows.associate { it.id to signature(repo.profileFile(it.id)) }
        val scripts = entry.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) }
        val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        val selected = repo.currentProfileId.value
        val expected = requireNotNull(args.getString("kclashLocalYamlSha256"))
        val bytes = rows.filter { it.type == ProfileType.FILE }.map { repo.profileFile(it.id).readBytes() }.first { sha(it) == expected }
        var failure: Throwable? = null
        try { awaitIdle(); block(bytes) }
        catch (error: Throwable) { failure = error; throw error }
        finally {
            try {
                assertTrue("正式配置行保持", rows == entry.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(repo.profileFile(it.id)) })
                assertTrue("正式脚本行保持", scripts == entry.database().scripts().getAll())
                assertEquals(scriptFiles, scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) })
                assertTrue("正式偏好保持", preferences == entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(selected, repo.currentProfileId.value); assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("kclash_local_interaction_preservation", "profiles-scripts-bodies-preferences-selection-preserved=true;runtime=STOPPED") })
            } catch (protection: Throwable) { if (failure != null) failure.addSuppressed(protection) else throw protection }
        }
    }
    private suspend fun failWrites(enabled: Boolean) = withContext(Dispatchers.IO) {
        val db = fixture.database.openHelper.writableDatabase
        if (enabled) db.execSQL("CREATE TEMP TRIGGER local_interaction_failure BEFORE INSERT ON app_preferences WHEN NEW.key = 'global_local_proxies' BEGIN SELECT RAISE(ABORT, 'fixture-local-save-failure'); END")
        else db.execSQL("DROP TRIGGER IF EXISTS local_interaction_failure")
    }
    private fun awaitIdle() { compose.waitUntil(10_000) { !model.state.value.loading && !model.state.value.busy }; compose.waitForIdle() }
    private fun awaitRows() { compose.waitUntil(10_000) { model.state.value.rows == listOf(row) }; awaitIdle() }
    private fun value(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun focusedImeTop(): Int? {
        var top: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(ViewCompat::getRootWindowInsets)
            if (view != null && insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) top = view.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
        return top
    }
    private fun assertAboveIme(vararg tags: String) {
        compose.waitUntil(5_000) { focusedImeTop() != null }
        val top = requireNotNull(focusedImeTop())
        tags.forEach { assertTrue("$it 位于真实键盘上方", compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInWindow.bottom <= top) }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), sha(file.readBytes()))
    private fun capture(name: String) {
        compose.waitForIdle(); val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(650)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
