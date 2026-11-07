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

/** 作者 long：跨真实进程验证正式扩展入口，局部只写原1.yaml副本，全局只改本例规则并恢复原偏好行。 */
class ProfileExtensionColdInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val gson = Gson()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "profile-extension-cold.json")
    private fun fixtureStore() = EncryptedDraftFile(compose.activity, "profile-extension-fixture.v1", "com.lonnnnnng.flclash.extension.fixture.v1")
    private fun case(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashExtensionColdAcceptance"))
        return requireNotNull(InstrumentationRegistry.getArguments().getString("kclashExtensionColdCase")).also {
            require(it.substringBefore('-') in setOf("local", "global"))
            require(it.substringAfter('-') in setOf("draft", "before", "after", "applied", "failure", "deleted", "conflict", "host", "batch"))
        }
    }
    private fun model(): ProfileExtensionController {
        lateinit var value: ProfileExtensionController
        compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java].extension }
        return value
    }
    private suspend fun draft() = entry().profileExtensionStore().read()?.let { gson.fromJson(it.decodeToString(), ProfileExtensionDraft::class.java) }
    private suspend fun awaitDraft(predicate: (ProfileExtensionDraft) -> Boolean) {
        withTimeout(20_000) { while (draft()?.let(predicate) != true) delay(100) }
    }
    private fun own(record: JsonObject, value: String = "DOMAIN-SUFFIX,original.test,DIRECT") = ExtensionRule(record["ruleId"].asString, value)
    private fun pendingWindows() = listOf(EditorCommitWindow.EXTENSION_INTENT, EditorCommitWindow.EXTENSION_APPLIED, EditorCommitWindow.EXTENSION_COMMITTED)

    @Test fun prepareAndHoldExtensionDraftForExternalDeath(): Unit = runBlocking {
        val case = case(); val global = case.startsWith("global-"); val phase = case.substringAfter('-')
        val e = entry(); val repo = e.profileRepository(); repo.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("先收尾上次扩展验收", marker().exists())
        assertNull(fixtureStore().read())
        assertNull(e.profileExtensionStore().read()); assertNull(e.profileManagementStore().read())
        assertNull(e.profileDraftStore().read()); assertNull(e.providerDocumentStore().read())
        val expected = requireNotNull(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expected))
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        val original = rows.single { it.type == ProfileType.FILE && sha(repo.profileFile(it.id).readBytes()) == expected }
        val token = UUID.randomUUID().toString()
        val record = JsonObject().apply {
            addProperty("case", case); addProperty("preparedPid", android.os.Process.myPid()); addProperty("token", token)
            addProperty("ruleId", "extension-cold-$token"); addProperty("selected", repo.currentProfileId.value)
            addProperty("rows", sha(gson.toJson(rows))); addProperty("files", files(rows.map { it.id }))
            addProperty("preferences", preferences()); addProperty("scripts", scripts()); addProperty("sourceSha", expected)
        }
        marker().writeText(record.toString())
        // 作者 long：原全局规则可能包含用户内容，保护副本也必须加密，marker与状态输出只保存摘要和本例ID。
        fixtureStore().write(gson.toJson(e.database().appPreferences().getByPrefix(ExtensionRules.PREFERENCE_KEY)
            .singleOrNull { it.key == ExtensionRules.PREFERENCE_KEY }).toByteArray())
        val profile = repo.addFileProfile("extension-cold-$token", repo.profileFile(original.id).readBytes())
        record.addProperty("profileId", profile.id); record.addProperty("signature", signature(repo.profileFile(profile.id))); marker().writeText(record.toString())
        if (global) repo.saveGlobalExtensionRules(repo.globalExtensionRules() + own(record))
        else repo.saveExtension(profile.id, "standard", null, null, ProfileExtensionOverrides(configured = true, rules = listOf(own(record))))
        record.add("localBefore", gson.toJsonTree(ProfileExtensionValues.from(checkNotNull(repo.extensionProfile(profile.id)))))
        marker().writeText(record.toString())
        compose.onNodeWithTag("nav_profiles").performClick()
        compose.waitUntil(20_000) { repo.profiles.value.any { it.id == profile.id } && compose.onNodeWithTag("profiles_grid").isDisplayed() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("profile_card_${profile.id}"))
        compose.onNodeWithTag("profile_card_${profile.id}").performTouchInput { longClick() }
        compose.onNodeWithTag("profile_extend_${profile.id}").performClick()
        val m = model(); compose.waitUntil(20_000) { m.state.value.profile?.id == profile.id && !m.state.value.loading }
        if (global) {
            compose.onNodeWithTag("extension_global_control").performScrollTo().performClick()
            compose.onNodeWithTag("extension_global_edit").performScrollTo().performClick()
        }
        if (phase == "batch") {
            compose.onNodeWithTag("extension_rule_select_${own(record).id}").performScrollTo().performClick()
            compose.onNodeWithTag("extension_delete_selected").performClick()
        } else {
            compose.onNodeWithTag("extension_rule_${own(record).id}").performScrollTo().performClick()
            compose.onNodeWithTag("extension_rule_content").performTextReplacement(PRIVATE)
            compose.onNodeWithTag("extension_rule_content").performTextInputSelection(TextRange(3, 11)); closeSoftKeyboard()
        }
        compose.waitForIdle()
        awaitDraft { it.uiDraft != null && it.uiDraft == m.state.value.draft?.uiDraft }
        if (phase in setOf("before", "after", "applied", "batch")) {
            val window = when (phase) { "after" -> EditorCommitWindow.EXTENSION_COMMITTED; "applied" -> EditorCommitWindow.EXTENSION_APPLIED; else -> EditorCommitWindow.EXTENSION_INTENT }
            EditorCommitProbe.arm(window)
            compose.onNodeWithTag(if (phase == "batch") "extension_delete_confirm" else "extension_rule_confirm").performClick()
            compose.waitUntil(20_000) { EditorCommitProbe.visits(window) == 1 || m.state.value.let { !it.saving && it.error != null } }
            assertEquals(1, EditorCommitProbe.visits(window))
            val pending = checkNotNull(draft()?.pending)
            record.addProperty("commitId", pending.operationId); marker().writeText(record.toString())
            assertEquals(phase in setOf("after", "applied"), repo.extensionCommitApplied(pending.operationId))
            assertRules(record, if (phase in setOf("after", "applied")) listOf(own(record, "DOMAIN-SUFFIX,$PRIVATE,DIRECT")) else listOf(own(record)))
        } else if (phase == "failure") {
            failureTrigger(record, true)
            compose.onNodeWithTag("extension_rule_confirm").performSemanticsAction(SemanticsActions.OnClick) { click -> click(); click() }
            compose.waitUntil(20_000) { !m.state.value.saving && m.state.value.error != null }
            assertEquals(1, ProfileExtensionProbe.databaseAborts)
            record.addProperty("commitId", checkNotNull(draft()?.pending).operationId); marker().writeText(record.toString())
            failureTrigger(record, false)
            assertRules(record, listOf(own(record)))
        }
        // 作者 long：外部变更在密文已有完整草稿后发生，不通过重新打开窗口构造恢复状态。
        if (phase == "deleted") changeOwnRules(record, emptyList())
        if (phase == "conflict") changeOwnRules(record, listOf(own(record, "DOMAIN-SUFFIX,external.test,REJECT")))
        if (phase in setOf("host", "batch")) repo.deleteProfile(profile.id)
        if (phase == "applied") changeOwnRules(record, listOf(own(record)))
        compose.waitForIdle()
        awaitDraft { it.uiDraft != null && (if (phase in setOf("before", "after", "applied", "batch")) it.pending != null else it.uiDraft == m.state.value.draft?.uiDraft) }
        assertPrivateState()
        record.addProperty("ready", true); marker().writeText(record.toString())
        // 作者 long：原全局规则仅在设备私有密文中精确保护，不随instrumentation状态输出。
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_extension_cold_ready", JsonObject().apply {
            addProperty("case", case); addProperty("preparedPid", android.os.Process.myPid()); addProperty("ready", true)
        }.toString()) })
        awaitCancellation()
    }

    @Test fun verifyAutomaticExtensionRecoveryAndCleanOwnFixture(): Unit = runBlocking {
        val case = case(); val phase = case.substringAfter('-')
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case, record["case"].asString); assertTrue(record["ready"].asBoolean)
        assertNotEquals(record["preparedPid"].asInt, android.os.Process.myPid())
        record["verifiedPid"]?.let { assertNotEquals(it.asInt, android.os.Process.myPid()) }
        val m = model(); val e = entry(); val repo = e.profileRepository()
        compose.waitUntil(25_000) { m.state.value.let { it.ready && !it.loading && it.draft != null } }
        assertEquals(record["profileId"].asLong, m.state.value.draft!!.profileId)
        pendingWindows().forEach { assertEquals(0, EditorCommitProbe.visits(it)) }
        if (phase !in setOf("after", "batch", "applied")) {
            compose.waitUntil(20_000) { compose.onNodeWithTag("extension_rule_content").isDisplayed() }
            val field = compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode().config
            assertEquals(PRIVATE, field[SemanticsProperties.EditableText].text)
            assertEquals(TextRange(3, 11), field[SemanticsProperties.TextSelectionRange])
        }
        if (phase in setOf("before", "failure")) {
            assertRules(record, listOf(own(record)))
            compose.onNodeWithTag("extension_rule_retry").performClick()
            compose.waitUntil(20_000) { !m.state.value.saving && m.state.value.draft?.pending == null }
            pendingWindows().forEach { assertEquals(1, EditorCommitProbe.visits(it)) }
            assertRules(record, listOf(own(record, "DOMAIN-SUFFIX,$PRIVATE,DIRECT")))
        } else if (phase in setOf("after", "applied")) {
            assertTrue(m.state.value.draft?.pending?.committed == true)
            assertRules(record, if (phase == "after") listOf(own(record, "DOMAIN-SUFFIX,$PRIVATE,DIRECT")) else listOf(own(record)))
            if (InstrumentationRegistry.getArguments().getString("kclashExtensionKeep") == "true") {
                assertPrivateState(); record.addProperty("verifiedPid", android.os.Process.myPid()); marker().writeText(record.toString())
                instrumentation.sendStatus(0, Bundle().apply { putString("kclash_extension_cold_verified", "case=$case;committed-no-replay=true;kept=true") })
                return@runBlocking
            }
            compose.runOnIdle { m.retryPending() }; compose.waitUntil(20_000) { !m.state.value.saving && m.state.value.draft?.pending == null }
            pendingWindows().forEach { assertEquals(0, EditorCommitProbe.visits(it)) }
            assertRules(record, if (phase == "after") listOf(own(record, "DOMAIN-SUFFIX,$PRIVATE,DIRECT")) else listOf(own(record)))
        } else if (phase in setOf("deleted", "conflict", "host")) {
            compose.onNodeWithTag("extension_rule_confirm").assertIsNotEnabled()
            if (phase in setOf("deleted", "conflict")) assertRules(record, if (phase == "deleted") emptyList() else listOf(own(record, "DOMAIN-SUFFIX,external.test,REJECT")))
            else assertTrue(repo.profileDeletionComplete(record["profileId"].asLong))
        } else if (phase == "batch") {
            compose.onNodeWithText("删除规则").assertIsDisplayed()
            compose.onNodeWithTag("extension_delete_cancel").assertIsEnabled().performClick()
            compose.waitUntil(20_000) { !m.state.value.saving && m.state.value.draft?.pending == null }
            assertTrue(repo.profileDeletionComplete(record["profileId"].asLong))
        } else assertRules(record, listOf(own(record)))
        assertPrivateState()
        cleanup(record)
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_extension_cold_verified", "case=$case;automatic-open=true;no-replay=true;formal-preserved=true;draft-cleared=true") })
    }

    @Test fun recoverExactOwnInterruptedExtensionFixture(): Unit = runBlocking {
        case(); assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashExtensionFixtureRecovery"))
        cleanup(JsonParser.parseString(marker().readText()).asJsonObject)
    }

    private suspend fun cleanup(record: JsonObject) {
        val e = entry(); val repo = e.profileRepository(); val m = model()
        repo.profilesLoaded.first { it }
        compose.waitUntil(20_000) { m.state.value.ready && !m.state.value.loading && !m.state.value.saving }
        failureTrigger(record, false)
        val id = record["profileId"].asLong
        assertTrue(m.state.value.draft == null || m.state.value.draft?.profileId == id)
        compose.runOnIdle { m.closeWithoutApplying { } }; compose.waitUntil(20_000) { m.state.value.draft == null }
        assertNull(e.profileExtensionStore().read())
        record["commitId"]?.let { assertFalse(repo.extensionCommitApplied(it.asString)) }
        val token = record["token"].asString; UUID.fromString(token)
        e.database().profiles().getById(id)?.let {
            assertEquals("extension-cold-$token", it.label); assertEquals(ProfileType.FILE, it.type)
            assertEquals(record["signature"].asString, signature(repo.profileFile(id))); repo.deleteProfile(id)
        }
        assertTrue(repo.profileDeletionComplete(id))
        if (record["case"].asString.startsWith("global-")) {
            val before = originalGlobal(record)
            assertTrue("非本例全局规则必须保持", repo.globalExtensionRules().filterNot { it.id == own(record).id } == before)
            e.database().withTransaction {
                val saved = originalGlobalRow()
                if (saved == null) e.database().appPreferences().delete(ExtensionRules.PREFERENCE_KEY)
                else e.database().appPreferences().put(saved)
            }
        }
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        assertEquals(record["rows"].asString, sha(gson.toJson(rows))); assertEquals(record["files"].asString, files(rows.map { it.id }))
        assertEquals(record["selected"].asLong, repo.currentProfileId.value)
        assertEquals(record["preferences"].asString, preferences()); assertEquals(record["scripts"].asString, scripts())
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        for (suffix in listOf("", ".bak", ".new")) assertFalse(File(compose.activity.noBackupFilesDir, "profile-extension.v1$suffix").exists())
        fixtureStore().write(null)
        assertTrue(marker().delete())
    }
    private suspend fun originalGlobalRow(): AppPreferenceEntity? = gson.fromJson(checkNotNull(fixtureStore().read()).decodeToString(), AppPreferenceEntity::class.java)
    private suspend fun originalGlobal(record: JsonObject): List<ExtensionRule> = ExtensionRules.decode(originalGlobalRow()?.value)
    private suspend fun changeOwnRules(record: JsonObject, rules: List<ExtensionRule>) {
        val repo = entry().profileRepository()
        if (record["case"].asString.startsWith("global-")) repo.saveGlobalExtensionRules(originalGlobal(record) + rules)
        else {
            val before = gson.fromJson(record["localBefore"], ProfileExtensionValues::class.java)
            repo.saveExtension(record["profileId"].asLong, before.mode, before.scriptId, before.matchTarget, before.settings.copy(rules = rules))
        }
    }
    private suspend fun assertRules(record: JsonObject, expected: List<ExtensionRule>) {
        val repo = entry().profileRepository()
        val actual = if (record["case"].asString.startsWith("global-")) {
            val rules = repo.globalExtensionRules()
            assertTrue(rules.filterNot { it.id == own(record).id } == originalGlobal(record))
            rules.filter { it.id == own(record).id }
        } else ProfileExtensionValues.from(checkNotNull(repo.extensionProfile(record["profileId"].asLong))).settings.rules
        assertEquals(expected, actual)
        assertEquals(record["signature"].asString, signature(repo.profileFile(record["profileId"].asLong)))
    }
    private suspend fun failureTrigger(record: JsonObject, enabled: Boolean) = entry().database().withTransaction {
        val db = entry().database().openHelper.writableDatabase
        db.execSQL("DROP TRIGGER IF EXISTS temp.extension_cold_failure")
        if (enabled) {
            if (record["case"].asString.startsWith("global-")) db.execSQL("CREATE TEMP TRIGGER extension_cold_failure BEFORE INSERT ON app_preferences WHEN NEW.key='global_extension_rules' BEGIN SELECT RAISE(ABORT,'fixture-extension-save-failure'); END")
            else {
                val id = record["profileId"].asLong; require(id > 0)
                db.execSQL("CREATE TEMP TRIGGER extension_cold_failure BEFORE UPDATE ON profiles WHEN OLD.id=$id BEGIN SELECT RAISE(ABORT,'fixture-extension-save-failure'); END")
            }
        }
    }
    private suspend fun preferences() = sha(gson.toJson(entry().database().appPreferences().getByPrefix("").associate { it.key to it.value }.toSortedMap()))
    private suspend fun scripts() = sha(gson.toJson(entry().database().scripts().getAll().sortedBy { it.id }))
    private fun files(ids: List<Long>) = ids.joinToString("|") { "$it:${signature(entry().profileRepository().profileFile(it))}" }
    private fun signature(file: File) = if (!file.exists()) "missing" else "${file.length()}:${file.lastModified()}:${sha(file.readBytes())}"
    private fun sha(text: String) = sha(text.toByteArray(Charsets.UTF_8))
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun assertPrivateState() {
        val bundle = Bundle(); compose.runOnIdle { instrumentation.callActivityOnSaveInstanceState(compose.activity, bundle) }
        val parcel = Parcel.obtain(); val bytes = try { parcel.writeBundle(bundle); parcel.marshall() } finally { parcel.recycle() }
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) assertFalse(bytes.toString(charset).contains(PRIVATE))
        val file = File(compose.activity.noBackupFilesDir, "profile-extension.v1")
        if (file.exists()) assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(PRIVATE))
    }
    private companion object { const val PRIVATE = "private-extension-cold-20261107.test" }
}
