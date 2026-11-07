package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：只增删本例全局规则，原偏好行独立加密保护；正式配置、文件和选择逐场保持。 */
class GlobalRulesRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val gson = Gson()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "global-rules-acceptance.json")
    private fun fixtureStore() = EncryptedDraftFile(compose.activity, "global-rules-fixture.v1", "com.lonnnnnng.flclash.global.rules.fixture.v1")
    private fun model(): GlobalRulesController {
        lateinit var value: GlobalRulesController
        compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java].globalRules }
        return value
    }
    private fun gate(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashGlobalRulesAcceptance"))
        return InstrumentationRegistry.getArguments().getString("kclashGlobalRulesCase") ?: "activity"
    }
    private fun own(r: JsonObject, index: Int = 0, value: String? = null) = ExtensionRule("gr-${r["token"].asString}-$index",
        value ?: "DOMAIN-SUFFIX,${if (index < 2) "visible" else "hidden"}-$index.test,DIRECT")
    private suspend fun storedDraft() = entry().globalRulesStore().read()?.let { gson.fromJson(it.decodeToString(), GlobalRulesDraft::class.java) }
    private suspend fun awaitDraft() {
        val m = model()
        withTimeout(20_000) { while (storedDraft()?.let { it.uiDraft != null && it.uiDraft == m.state.value.draft?.uiDraft } != true) delay(100) }
    }
    private fun windows() = listOf(EditorCommitWindow.GLOBAL_RULES_INTENT, EditorCommitWindow.GLOBAL_RULES_APPLIED, EditorCommitWindow.GLOBAL_RULES_COMMITTED)
    private suspend fun prepare(case: String): JsonObject {
        gate(); val e = entry(); val repo = e.profileRepository(); repo.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("先恢复已有夹具", marker().exists()); assertNull(fixtureStore().read())
        assertNull(e.globalRulesStore().read()); assertNull(e.profileExtensionStore().read()); assertNull(e.profileManagementStore().read())
        assertNull(e.profileDraftStore().read()); assertNull(e.providerDocumentStore().read())
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        assertTrue(rows.isNotEmpty())
        val expected = requireNotNull(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"))
        assertTrue(rows.any { it.type == ProfileType.FILE && sha(repo.profileFile(it.id).readBytes()) == expected })
        val r = JsonObject().apply {
            addProperty("case", case); addProperty("token", UUID.randomUUID().toString()); addProperty("preparedPid", android.os.Process.myPid())
            addProperty("rows", sha(gson.toJson(rows))); addProperty("files", files(rows.map { it.id }))
            addProperty("preferences", preferences()); addProperty("scripts", scripts()); addProperty("selected", repo.currentProfileId.value)
        }
        fixtureStore().write(gson.toJson(e.database().appPreferences().getByPrefix(ExtensionRules.PREFERENCE_KEY)
            .singleOrNull { it.key == ExtensionRules.PREFERENCE_KEY }).toByteArray())
        marker().writeText(r.toString())
        repo.saveGlobalExtensionRules(original() + (0..2).map { own(r, it) })
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_advanced"))
        compose.onNodeWithTag("tools_item_advanced").performClick()
        compose.onNodeWithTag("advanced_item_rules").performClick()
        compose.waitUntil(20_000) { model().state.value.let { it.ready && it.loaded && !it.loading } && exists("global_rules_add") }
        r.addProperty("session", model().state.value.draft!!.session); marker().writeText(r.toString())
        return r
    }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun openRule(r: JsonObject) {
        compose.onNodeWithTag("global_rules_list").performScrollToNode(hasTestTag("global_rule_${own(r).id}"))
        compose.onNodeWithTag("global_rule_${own(r).id}").performClick()
        compose.onNodeWithTag("extension_rule_content").performTextReplacement(PRIVATE)
        compose.onNodeWithTag("extension_rule_content").performTextInputSelection(TextRange(3, 11))
        closeSoftKeyboard()
    }
    private fun batch() {
        compose.onNodeWithTag("global_rules_search").performTextReplacement("visible"); closeSoftKeyboard()
        compose.onNodeWithTag("global_rules_select").performClick()
        compose.onNodeWithTag("global_rules_all").performClick()
        compose.onNodeWithTag("global_rules_delete").performClick()
    }
    private fun recreate() { compose.activityRule.scenario.recreate(); compose.waitForIdle() }
    private suspend fun fixture(block: suspend (JsonObject) -> Unit) {
        val r = prepare("activity")
        try { block(r) } finally { cleanup(r) }
    }

    @Test fun draftErrorSelectionAndDiscardSurviveRecreation(): Unit = runBlocking { fixture { r ->
        openRule(r); recreate()
        assertEquals(PRIVATE, compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(3, 11), compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        capture("global_rules_long_draft_recreated")
        compose.onNodeWithTag("extension_rule_raw_mode").performScrollTo().performClick()
        compose.onNodeWithTag("extension_rule_raw").performTextReplacement("DOMAIN-SUFFIX,,DIRECT"); closeSoftKeyboard()
        compose.onNodeWithTag("extension_rule_confirm").performClick()
        compose.onNodeWithTag("extension_rule_error").performScrollTo().assertIsDisplayed()
        recreate(); compose.onNodeWithTag("extension_rule_error").performScrollTo().assertIsDisplayed()
        capture("global_rules_validation_recreated")
        ins.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); compose.waitForIdle()
        compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed(); recreate()
        compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed(); capture("global_rules_discard_recreated")
        compose.onNodeWithText("继续编辑").performClick()
        assertRules(r, (0..2).map { own(r,it) }); assertPrivate()
    } }

    @Test fun visibleBatchFailureCancelAndRetryPreserveSelection(): Unit = runBlocking { fixture { r ->
        batch(); recreate(); compose.onNodeWithText("删除 2 条规则？").assertIsDisplayed()
        val before = GlobalRulesProbe.databaseAborts
        trigger(true)
        compose.onNodeWithTag("global_rules_delete_confirm").performSemanticsAction(SemanticsActions.OnClick) { it(); it() }
        compose.waitUntil(20_000) { !model().state.value.saving && model().state.value.interrupted }
        assertEquals(before + 1, GlobalRulesProbe.databaseAborts)
        val commit = model().state.value.draft!!.pending!!.operationId
        assertFalse(entry().profileRepository().extensionCommitApplied(commit))
        assertRules(r,(0..2).map { own(r,it) })
        recreate(); compose.onNodeWithTag("global_rules_delete_error").assertIsDisplayed(); capture("global_rules_delete_failure_recreated")
        compose.onNodeWithTag("global_rules_delete_cancel").performClick()
        compose.waitUntil(20_000) { !model().state.value.saving && !model().state.value.interrupted }
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        trigger(false)
        compose.onNodeWithTag("global_rules_delete").performClick()
        compose.onNodeWithTag("global_rules_delete_confirm").performClick()
        compose.waitUntil(20_000) { !model().state.value.saving && !exists("global_rules_delete_confirm") }
        assertRules(r,listOf(own(r,2)))
        compose.onNodeWithTag("global_rules_cancel_selection").performClick()
        compose.onNodeWithTag("global_rules_search").performTextReplacement(""); closeSoftKeyboard()
        compose.onNodeWithTag("global_rules_list").performScrollToNode(hasTestTag("global_rule_${own(r,2).id}"))
        capture("global_rules_batch_saved_hidden_preserved")
    } }

    @Test fun externalEditAndDeletionCannotOverwriteOrReinsertOriginal(): Unit = runBlocking { fixture { r ->
        for (deleted in listOf(false, true)) {
            openRule(r)
            val external = if (deleted) emptyList() else listOf(own(r,value="DOMAIN-SUFFIX,external.test,REJECT"))
            change(r,external + listOf(own(r,1),own(r,2)))
            recreate(); compose.onNodeWithTag("extension_rule_confirm").performClick()
            compose.waitUntil(20_000) { !model().state.value.saving && model().state.value.blocked }
            compose.onNodeWithTag("extension_rule_confirm").assertIsNotEnabled()
            assertEquals(PRIVATE, compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertRules(r,external + listOf(own(r,1),own(r,2)))
            capture(if(deleted) "global_rules_deleted_conflict" else "global_rules_modified_conflict")
            compose.onNodeWithTag("extension_rule_discard_pending").performClick()
            compose.waitUntil(20_000) { !model().state.value.saving && !model().state.value.interrupted }
            ins.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); compose.waitForIdle()
            compose.onNodeWithText("放弃").performClick()
            change(r,(0..2).map { own(r,it) }); val controller = model(); compose.runOnIdle { controller.reload() }
            compose.waitUntil(20_000) { !model().state.value.loading }
        }
    } }

    @Test fun sortingPersistsAcrossRecreationAndRejectsExternalDirectory(): Unit = runBlocking { fixture { r ->
        compose.onNodeWithTag("global_rules_sort").performClick(); recreate()
        compose.onNodeWithTag("global_rules_order_list").assertIsDisplayed()
        drag(r,0,1)
        compose.waitUntil(20_000) { !model().state.value.saving && model().state.value.saveResultVersion > 0 }
        assertRules(r,listOf(own(r,1),own(r),own(r,2)))
        capture("global_rules_sort_saved")
        drag(r,1,0)
        compose.waitUntil(20_000) { !model().state.value.saving && model().state.value.saveResultVersion > 1 }
        assertRules(r,(0..2).map { own(r,it) })
        change(r,listOf(own(r,value="DOMAIN-SUFFIX,external.test,REJECT"),own(r,1),own(r,2)))
        drag(r,0,1)
        compose.waitUntil(20_000) { !model().state.value.saving && model().state.value.interrupted }
        assertTrue(model().state.value.blocked)
        capture("global_rules_sort_conflict")
    } }

    @Test fun realKeyboardLeavesSaveReachableAndMatchDraftRequiresDiscard(): Unit = runBlocking { fixture { r ->
        compose.onNodeWithTag("global_rules_add").performClick()
        val field = compose.onNodeWithTag("extension_rule_content")
        field.performClick(); field.performTextReplacement(PRIVATE)
        compose.waitUntil(5_000) { imeTop() != null }
        val save = compose.onNodeWithTag("extension_rule_confirm").fetchSemanticsNode().boundsInWindow
        assertTrue(save.bottom <= requireNotNull(imeTop()))
        capture("global_rules_real_keyboard")
        closeSoftKeyboard()
        ins.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); compose.waitForIdle()
        compose.onNodeWithText("放弃").performClick()
        compose.onNodeWithTag("global_rules_add").performClick()
        compose.onNodeWithText("类型：DOMAIN-SUFFIX").performClick()
        compose.onNodeWithText("MATCH", useUnmergedTree = true).performScrollTo().performClick()
        // 作者 long：类型菜单是另一个Window，等其退场完成后才记录表单令牌。
        compose.waitForIdle(); android.os.SystemClock.sleep(650)
        val formWindow = focusedWindow()
        assertNotNull(formWindow)
        compose.onNodeWithTag("extension_rule_target").performClick()
        compose.onNodeWithTag("extension_target_REJECT").performScrollTo().performClick()
        // 作者 long：子窗退场时系统焦点交接独立于Compose时钟；返回必须由恢复焦点后的原表单处理。
        compose.waitUntil(5_000) { focusedWindow() == formWindow }
        compose.onNodeWithText("MATCH,REJECT").assertExists()
        ins.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("放弃未保存修改？").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
        recreate(); compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
        capture("global_rules_match_discard_recreated")
        compose.onNodeWithText("放弃").performClick()
        assertRules(r,(0..2).map { own(r,it) })
    } }

    private fun focusedWindow(): android.os.IBinder? {
        var token: android.os.IBinder? = null
        ins.runOnMainSync { token = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }?.windowToken }
        return token
    }

    private fun imeTop(): Int? {
        var top: Int? = null
        ins.runOnMainSync {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(androidx.core.view.ViewCompat::getRootWindowInsets)
            if (view != null && insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
                top = view.height - insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
        }
        return top
    }

    private fun drag(r: JsonObject, from: Int, to: Int) {
        val list = compose.onNodeWithTag("global_rules_order_list")
        list.performScrollToNode(hasTestTag("global_rules_order_${own(r,from).id}"))
        val a = compose.onNodeWithTag("global_rules_order_${own(r,from).id}").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("global_rules_order_${own(r,to).id}").fetchSemanticsNode().boundsInRoot
        val bounds = list.fetchSemanticsNode().boundsInRoot
        list.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(a.center.x-bounds.left,a.center.y-bounds.top)); advanceEventTime(650)
            moveTo(androidx.compose.ui.geometry.Offset(b.center.x-bounds.left,b.center.y-bounds.top),300); advanceEventTime(200); up()
        }
        compose.waitForIdle()
    }

    @Test fun prepareAndHoldForExternalDeath(): Unit = runBlocking {
        val case = gate()
        require(case in setOf("draft","error","discard","batch","sorting","conflict","deleted","before","applied","after","delete-before","failure","close-before","close-after"))
        val r = prepare(case); val m = model(); val repo = entry().profileRepository()
        if (case in setOf("batch","delete-before")) batch()
        else if (case == "sorting") compose.onNodeWithTag("global_rules_sort").performClick()
        else if (!case.startsWith("close-")) openRule(r)
        if (case == "error") {
            compose.onNodeWithTag("extension_rule_raw_mode").performScrollTo().performClick()
            compose.onNodeWithTag("extension_rule_raw").performTextReplacement("DOMAIN-SUFFIX,,DIRECT"); closeSoftKeyboard()
            compose.onNodeWithTag("extension_rule_confirm").performClick()
        }
        if (case == "discard") {
            ins.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); compose.waitForIdle()
            compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
        }
        awaitDraft()
        val window = when(case) {
            "before", "delete-before" -> EditorCommitWindow.GLOBAL_RULES_INTENT
            "applied" -> EditorCommitWindow.GLOBAL_RULES_APPLIED
            "after" -> EditorCommitWindow.GLOBAL_RULES_COMMITTED
            "close-before" -> EditorCommitWindow.GLOBAL_RULES_CLOSE_BEFORE_CLEAR
            "close-after" -> EditorCommitWindow.GLOBAL_RULES_CLOSE_AFTER_CLEAR
            else -> null
        }
        if (window != null) {
            EditorCommitProbe.arm(window)
            compose.onNodeWithTag(when { case.startsWith("close-") -> "global_rules_back"; case=="delete-before" -> "global_rules_delete_confirm"; else -> "extension_rule_confirm" }).performClick()
            compose.waitUntil(20_000) { EditorCommitProbe.visits(window) == 1 }
            storedDraft()?.pending?.let { r.addProperty("commitId",it.operationId) }
        }
        if (case == "failure") {
            trigger(true); compose.onNodeWithTag("extension_rule_confirm").performClick()
            compose.waitUntil(20_000) { !m.state.value.saving && m.state.value.interrupted }
            assertEquals(1,GlobalRulesProbe.databaseAborts); trigger(false)
            r.addProperty("commitId",checkNotNull(storedDraft()?.pending).operationId)
        }
        if (case in setOf("conflict","deleted","applied")) {
            change(r,when(case) {
                "deleted" -> listOf(own(r,1),own(r,2))
                "conflict" -> listOf(own(r,value="DOMAIN-SUFFIX,external.test,REJECT"),own(r,1),own(r,2))
                else -> (0..2).map { own(r,it) }
            })
        }
        if (exists("extension_rule_content")) {
            val range = compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
            r.addProperty("selectionStart", range.start); r.addProperty("selectionEnd", range.end)
            ins.sendStatus(2, Bundle().apply { putString("kclash_global_rules_selection", "case=$case;before-death=${range.start},${range.end}") })
        }
        assertPrivate()
        r.addProperty("ready",true); marker().writeText(r.toString())
        ins.sendStatus(0,Bundle().apply { putString("kclash_global_rules_ready",JsonObject().apply {
            addProperty("case",case);addProperty("preparedPid",android.os.Process.myPid());addProperty("ready",true)
        }.toString()) })
        awaitCancellation()
    }

    @Test fun verifyAutomaticRecoveryAndClean(): Unit = runBlocking {
        val case=gate();val r=JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case,r["case"].asString);assertTrue(r["ready"].asBoolean)
        assertNotEquals(r["preparedPid"].asInt,android.os.Process.myPid())
        r["verifiedPid"]?.let { assertNotEquals(it.asInt,android.os.Process.myPid()) }
        val m=model();compose.waitUntil(25_000) { m.state.value.ready && !m.state.value.loading }
        windows().forEach { assertEquals(0,EditorCommitProbe.visits(it)) }
        if(case=="close-after") {
            assertNull(m.state.value.draft);compose.onNodeWithTag("page_global_rules").assertDoesNotExist()
        } else {
            assertEquals(r["session"].asString,m.state.value.draft?.session)
            if(case in setOf("batch","delete-before")) compose.onNodeWithText("删除 2 条规则？").assertIsDisplayed()
            else if(case=="sorting") compose.onNodeWithTag("global_rules_order_list").assertIsDisplayed()
            else if(case=="close-before") compose.onNodeWithTag("page_global_rules").assertIsDisplayed()
            else if(case=="error") compose.onNodeWithTag("extension_rule_error").performScrollTo().assertIsDisplayed()
            else if(case=="discard") compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
            else {
                assertEquals(PRIVATE, compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
                val expectedSelection = TextRange(r["selectionStart"].asInt, r["selectionEnd"].asInt)
                if (case in setOf("draft", "before", "conflict", "deleted")) assertEquals(TextRange(3,11), expectedSelection)
                assertEquals(expectedSelection,compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            }
        }
        if(case in setOf("before","failure","delete-before")) {
            assertRules(r,(0..2).map { own(r,it) })
            compose.onNodeWithTag(if(case=="delete-before") "global_rules_delete_retry" else "extension_rule_retry").performClick()
            compose.waitUntil(20_000) { !m.state.value.saving && m.state.value.draft?.pending==null }
            windows().forEach { assertEquals(1,EditorCommitProbe.visits(it)) }
            assertRules(r,if(case=="delete-before") listOf(own(r,2)) else listOf(own(r,value="DOMAIN-SUFFIX,$PRIVATE,DIRECT"),own(r,1),own(r,2)))
        } else if(case in setOf("after","applied")) {
            assertTrue(m.state.value.draft?.pending?.committed==true)
            val expected=if(case=="after") listOf(own(r,value="DOMAIN-SUFFIX,$PRIVATE,DIRECT"),own(r,1),own(r,2)) else (0..2).map { own(r,it) }
            assertRules(r,expected)
            if(InstrumentationRegistry.getArguments().getString("kclashGlobalRulesKeep")=="true") {
                assertPrivate();r.addProperty("verifiedPid",android.os.Process.myPid());marker().writeText(r.toString())
                ins.sendStatus(0,Bundle().apply { putString("kclash_global_rules_verified","case=$case;kept=true;no-replay=true") });return@runBlocking
            }
            compose.runOnIdle { m.retryPending() };compose.waitUntil(20_000) { !m.state.value.saving && m.state.value.draft?.pending==null }
            assertRules(r,expected);windows().forEach { assertEquals(0,EditorCommitProbe.visits(it)) }
        } else if(case in setOf("conflict","deleted")) {
            compose.onNodeWithTag("extension_rule_confirm").assertIsNotEnabled()
            assertRules(r,if(case=="deleted") listOf(own(r,1),own(r,2)) else listOf(own(r,value="DOMAIN-SUFFIX,external.test,REJECT"),own(r,1),own(r,2)))
        } else assertRules(r,(0..2).map { own(r,it) })
        assertPrivate();cleanup(r)
        ins.sendStatus(0,Bundle().apply { putString("kclash_global_rules_verified","case=$case;automatic-open=true;no-replay=true;formal-preserved=true;draft-cleared=true") })
    }

    @Test fun recoverExactOwnInterruptedFixture(): Unit = runBlocking {
        gate();assertEquals("true",InstrumentationRegistry.getArguments().getString("kclashGlobalRulesFixtureRecovery"))
        cleanup(JsonParser.parseString(marker().readText()).asJsonObject)
    }
    private suspend fun cleanup(r: JsonObject) {
        val e=entry();val repo=e.profileRepository();val m=model()
        repo.profilesLoaded.first { it }
        compose.waitUntil(20_000) { m.state.value.ready && !m.state.value.loading && !m.state.value.saving }
        trigger(false)
        assertTrue(m.state.value.draft==null || m.state.value.draft?.session==r["session"]?.asString)
        if(m.state.value.draft!=null) { compose.runOnIdle { m.close {} };compose.waitUntil(20_000) { m.state.value.draft==null } }
        assertNull(e.globalRulesStore().read());r["commitId"]?.let { assertFalse(repo.extensionCommitApplied(it.asString)) }
        UUID.fromString(r["token"].asString)
        assertEquals(original(),repo.globalExtensionRules().filterNot { it.id.startsWith("gr-${r["token"].asString}-") })
        e.database().withTransaction {
            val row=originalRow();if(row==null)e.database().appPreferences().delete(ExtensionRules.PREFERENCE_KEY) else e.database().appPreferences().put(row)
        }
        val rows=e.database().profiles().getAll().sortedBy { it.id }
        assertEquals(r["rows"].asString,sha(gson.toJson(rows)));assertEquals(r["files"].asString,files(rows.map { it.id }))
        assertEquals(r["selected"].asLong,repo.currentProfileId.value);assertEquals(r["preferences"].asString,preferences());assertEquals(r["scripts"].asString,scripts())
        assertEquals(CoreRuntimeStatus.STOPPED,e.runtimeManager().state.value)
        for(suffix in listOf("",".bak",".new")) assertFalse(File(compose.activity.noBackupFilesDir,"global-rules.v1$suffix").exists())
        fixtureStore().write(null);assertTrue(marker().delete())
        ins.sendStatus(2,Bundle().apply { putString("kclash_global_rules_protection","formal-count=${rows.size};rows-files-scripts-preferences-selection-preserved=true;own-rules-draft-receipts-removed=true;runtime=STOPPED") })
    }
    private suspend fun originalRow(): AppPreferenceEntity?=gson.fromJson(checkNotNull(fixtureStore().read()).decodeToString(),AppPreferenceEntity::class.java)
    private suspend fun original()=ExtensionRules.decode(originalRow()?.value)
    private suspend fun change(r:JsonObject, own:List<ExtensionRule>) { entry().profileRepository().saveGlobalExtensionRules(original()+own) }
    private suspend fun assertRules(r:JsonObject, expected:List<ExtensionRule>) { assertEquals(original()+expected,entry().profileRepository().globalExtensionRules()) }
    private suspend fun trigger(enabled:Boolean)=entry().database().withTransaction {
        val db=entry().database().openHelper.writableDatabase
        db.execSQL("DROP TRIGGER IF EXISTS temp.global_rules_failure")
        if(enabled) db.execSQL("CREATE TEMP TRIGGER global_rules_failure BEFORE INSERT ON app_preferences WHEN NEW.key='global_extension_rules' BEGIN SELECT RAISE(ABORT,'fixture-global-rules-failure'); END")
    }
    private suspend fun preferences()=sha(gson.toJson(entry().database().appPreferences().getByPrefix("").associate { it.key to it.value }.toSortedMap()))
    private suspend fun scripts()=sha(gson.toJson(entry().database().scripts().getAll().sortedBy { it.id }))
    private fun files(ids:List<Long>)=ids.joinToString("|") { "$it:${signature(entry().profileRepository().profileFile(it))}" }
    private fun signature(file:File)=if(!file.exists())"missing" else "${file.length()}:${file.lastModified()}:${sha(file.readBytes())}"
    private fun sha(text:String)=sha(text.toByteArray())
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun assertPrivate() {
        val bundle=Bundle();compose.runOnIdle { ins.callActivityOnSaveInstanceState(compose.activity,bundle) }
        val parcel=Parcel.obtain();val bytes=try { parcel.writeBundle(bundle);parcel.marshall() }finally{parcel.recycle()}
        for(cs in listOf(Charsets.UTF_8,Charsets.UTF_16LE,Charsets.UTF_16BE))assertFalse(bytes.toString(cs).contains(PRIVATE))
        val file=File(compose.activity.noBackupFilesDir,"global-rules.v1")
        if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(PRIVATE))
    }
    private fun capture(name:String) {
        compose.waitForIdle();ins.waitForIdleSync();android.os.SystemClock.sleep(650)
        val image=checkNotNull(ins.uiAutomation.takeScreenshot())
        recordVisualPng(name,java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray())
    }
    private companion object { val PRIVATE="private-global-rules-"+"long-content-".repeat(20)+".test" }
}
