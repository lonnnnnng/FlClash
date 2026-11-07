package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Parcel
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
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实主页面分两进程验收；原1.yaml仅复制到本例配置，正式配置和选择必须逐值保持。 */
class ProfileManagementColdInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val gson = Gson()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "profile-management-cold.json")
    private fun case(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashManagementColdAcceptance"))
        return requireNotNull(InstrumentationRegistry.getArguments().getString("kclashManagementColdCase")).also {
            require(it.substringBefore('-') in setOf("rename", "delete", "order"))
            require(it.substringAfter('-') in setOf("draft", "before", "after", "failure", "deleted", "conflict"))
        }
    }
    private fun model(): ProfileManagementController {
        lateinit var result: ProfileManagementController
        compose.runOnIdle { result = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java].management }
        return result
    }
    private suspend fun draft() = entry().profileManagementStore().read()?.let { gson.fromJson(it.decodeToString(), ProfileManagementDraft::class.java) }
    private suspend fun awaitDraft(predicate: (ProfileManagementDraft) -> Boolean) {
        withTimeout(20_000) { while (draft()?.let(predicate) != true) delay(100) }
    }

    @Test fun prepareAndHoldManagementDraftForExternalDeath(): Unit = runBlocking {
        val case = case(); val kind = case.substringBefore('-'); val phase = case.substringAfter('-')
        val e = entry(); val repo = e.profileRepository(); repo.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("先收尾上次管理窗口验收", marker().exists())
        assertNull("不覆盖已有管理草稿", e.profileManagementStore().read())
        assertNull(e.profileDraftStore().read()); assertNull(e.providerDocumentStore().read())
        val expected = requireNotNull(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expected))
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        val original = rows.single { it.type == ProfileType.FILE && sha(repo.profileFile(it.id).readBytes()) == expected }
        val bytes = repo.profileFile(original.id).readBytes()
        val token = UUID.randomUUID().toString()
        val record = JsonObject().apply {
            addProperty("case", case); addProperty("preparedPid", android.os.Process.myPid()); addProperty("token", token)
            addProperty("selected", repo.currentProfileId.value); addProperty("rows", sha(gson.toJson(rows)))
            addProperty("files", files(rows.map { it.id })); addProperty("preferences", preferences()); addProperty("scripts", scripts())
            add("formalIds", gson.toJsonTree(rows.map { it.id })); addProperty("sourceSha", expected)
        }
        marker().writeText(record.toString())
        val first = repo.addCustomProfile("management-$token-1")
        record.addProperty("first", first.id); marker().writeText(record.toString())
        repo.updateLocalProfileContent(first.id, bytes.decodeToString())
        val second = repo.addFileProfile("management-$token-2", bytes)
        record.addProperty("second", second.id); marker().writeText(record.toString())
        compose.onNodeWithTag("nav_profiles").performClick()
        // 作者 long：惰性列表不会创建屏幕外副本；先等正式列表包含两项，再滚到实际操作目标，不能拿屏幕内节点作数据就绪条件。
        compose.waitUntil(20_000) { repo.profiles.value.any { it.id == second.id } }
        compose.waitUntil(20_000) { compose.onNodeWithTag("profiles_grid").isDisplayed() }
        val m = model(); compose.waitUntil(20_000) { m.state.value.ready && !m.state.value.busy }
        if (kind == "order") compose.onNodeWithTag("profiles_sort").performClick()
        else {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("profile_card_${first.id}"))
            compose.onNodeWithTag("profile_card_${first.id}").performScrollTo().performTouchInput { longClick() }
            if (kind == "rename") compose.onNodeWithTag("profile_rename_${first.id}").performClick()
            else compose.onNodeWithText("删除").performClick()
        }
        compose.waitUntil(20_000) { m.state.value.draft?.kind == kind }
        if (kind == "rename") {
            compose.onNodeWithTag("profile_rename_label").performTextReplacement("  $PRIVATE  ")
            compose.onNodeWithTag("profile_rename_label").performTextInputSelection(TextRange(3, 10)); closeSoftKeyboard()
        }
        if (kind == "order") {
            compose.onNodeWithTag("profiles_order_${first.id}").performOrderTouchMove(1)
            assertBefore(second.id, first.id)
        }
        val desired = requireNotNull(m.state.value.draft)
        record.addProperty("label", desired.label); record.add("order", gson.toJsonTree(desired.order))
        record.add("originalOrder", gson.toJsonTree(desired.originalOrder))
        record.addProperty("firstSignature", signature(repo.profileFile(first.id))); record.addProperty("secondSignature", signature(repo.profileFile(second.id)))
        marker().writeText(record.toString())
        if (phase in setOf("before", "after")) {
            // 作者 long：提交检查点会持锁等待外部结束进程，必须先持久化完整窗口树；不能在持锁后等新的UI反馈快照落盘。
            compose.waitForIdle()
            awaitDraft { it.uiDraft != null && it.uiDraft == m.state.value.draft?.uiDraft && it.label == desired.label }
            val window = if (phase == "before") EditorCommitWindow.MANAGEMENT_INTENT else EditorCommitWindow.MANAGEMENT_COMMITTED
            EditorCommitProbe.arm(window); submit(kind)
            compose.waitUntil(20_000) { EditorCommitProbe.visits(window) == 1 || m.state.value.let { !it.busy && it.error != null } }
            assertEquals(1, EditorCommitProbe.visits(window)); assertTrue(draft()!!.pending)
            assertResult(record, phase == "after")
        } else if (phase == "failure") {
            failureTrigger(record, true)
            // 作者 long：同一次事件循环连续提交，验证同步认领；两次独立点击可能已跨过第一次失败，不代表重复受理。
            compose.onNodeWithTag(saveTag(kind)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { click -> click(); click() }
            compose.waitUntil(20_000) { !m.state.value.busy && m.state.value.error != null }
            assertEquals(1, ProfileManagementProbe.databaseAborts)
            failureTrigger(record, false)
            assertNotNull(e.database().profiles().getById(first.id))
            assertEquals("management-$token-1", e.database().profiles().getById(first.id)!!.label)
            if (kind == "rename") {
                compose.onNodeWithTag("profile_rename_label").performTextInputSelection(TextRange(3, 10)); closeSoftKeyboard()
            }
        } else if (phase == "draft" && kind == "order") {
            compose.onNodeWithTag("profiles_order_back").performClick(); compose.onNodeWithText("放弃排序修改？").assertIsDisplayed()
        }
        compose.waitForIdle()
        awaitDraft { it.uiDraft != null && it.label == desired.label &&
            (if (phase in setOf("before", "after")) it.pending else it.uiDraft == m.state.value.draft?.uiDraft) }
        if (phase == "deleted") repo.deleteProfile(if (kind == "order") second.id else first.id)
        if (phase == "conflict") {
            if (kind == "rename") repo.updateProfileLabel(first.id, "external-$token")
            else repo.reorder(desired.order)
        }
        assertPrivateState()
        record.addProperty("ready", true); marker().writeText(record.toString())
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_management_cold_ready", record.toString()) })
        awaitCancellation()
    }

    @Test fun verifyAutomaticManagementRecoveryAndCleanOwnFixture(): Unit = runBlocking {
        val case = case(); val kind = case.substringBefore('-'); val phase = case.substringAfter('-')
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case, record["case"].asString); assertTrue(record["ready"].asBoolean)
        assertNotEquals(record["preparedPid"].asInt, android.os.Process.myPid())
        record["verifiedPid"]?.let { assertNotEquals(it.asInt, android.os.Process.myPid()) }
        val m = model(); val e = entry(); val repo = e.profileRepository()
        compose.waitUntil(25_000) { m.state.value.let { it.ready && !it.busy && it.draft != null } }
        assertEquals(kind, m.state.value.draft!!.kind)
        assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.MANAGEMENT_INTENT))
        assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.MANAGEMENT_COMMITTED))
        when (kind) {
            "rename" -> {
                val field = compose.onNodeWithTag("profile_rename_label").fetchSemanticsNode().config
                assertEquals(record["label"].asString, field[SemanticsProperties.EditableText].text)
                if (phase in setOf("draft", "failure", "deleted", "conflict")) assertEquals(TextRange(3, 10), field[SemanticsProperties.TextSelectionRange])
            }
            "delete" -> compose.onNodeWithText("删除配置").assertIsDisplayed()
            "order" -> {
                compose.onNodeWithTag("profiles_order_page").assertIsDisplayed()
                if (phase != "deleted") assertBefore(record["second"].asLong, record["first"].asLong)
            }
        }
        if (phase == "after") {
            assertResult(record, true)
            assertTrue(m.state.value.draft!!.committed)
            compose.runOnIdle { m.save() }; compose.waitForIdle()
            assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.MANAGEMENT_INTENT))
            if (InstrumentationRegistry.getArguments().getString("kclashManagementKeep") == "true") {
                assertPrivateState()
                record.addProperty("verifiedPid", android.os.Process.myPid()); marker().writeText(record.toString())
                instrumentation.sendStatus(0, Bundle().apply { putString("kclash_management_cold_verified", "case=$case;committed-no-replay=true;kept=true") })
                return@runBlocking
            }
        } else if (phase == "deleted" && kind != "order" || phase == "conflict") {
            compose.waitUntil(20_000) { m.state.value.blocked }
            compose.runOnIdle { m.save() }; compose.waitForIdle()
            assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.MANAGEMENT_INTENT))
            if (phase == "deleted") assertTrue(repo.profileDeletionComplete(record["first"].asLong))
            else if (kind == "rename") assertEquals("external-${record["token"].asString}", e.database().profiles().getById(record["first"].asLong)!!.label)
            else assertEquals(record["order"].asJsonArray.map { it.asLong }, repo.managementProfileOrder())
        } else if (phase in setOf("before", "failure")) {
            assertResult(record, false)
            assertFalse(m.state.value.draft!!.committed); assertFalse(m.state.value.blocked)
            submit(kind)
            compose.waitUntil(20_000) { m.state.value.draft == null }
            assertEquals(1, EditorCommitProbe.visits(EditorCommitWindow.MANAGEMENT_INTENT))
            assertEquals(1, EditorCommitProbe.visits(EditorCommitWindow.MANAGEMENT_COMMITTED))
            assertResult(record, true)
        } else if (phase == "deleted" && kind == "order") {
            assertFalse(m.state.value.blocked)
            assertEquals(record["order"].asJsonArray.map { it.asLong }.filterNot { it == record["second"].asLong }, m.state.value.draft!!.order)
            assertTrue(repo.profileDeletionComplete(record["second"].asLong))
            compose.onNodeWithTag("profiles_order_${record["second"].asLong}").assertDoesNotExist()
        } else if (phase == "draft" && kind == "order") {
            assertResult(record, false)
            compose.onNodeWithText("放弃排序修改？").assertIsDisplayed()
            compose.onNodeWithText("继续排序").performClick(); assertBefore(record["second"].asLong, record["first"].asLong)
        }
        // 作者 long：保存实例状态会降低宿主生命周期；所有语义点击完成后再查Bundle，避免测试自己移除可交互Compose树。
        assertPrivateState()
        cleanup(record)
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_management_cold_verified", "case=$case;automatic-open=true;no-replay=true;formal-preserved=true;draft-cleared=true") })
    }

    @Test fun recoverExactOwnInterruptedManagementFixture(): Unit = runBlocking {
        case(); assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashManagementFixtureRecovery"))
        cleanup(JsonParser.parseString(marker().readText()).asJsonObject)
    }

    private suspend fun cleanup(record: JsonObject) {
        val e = entry(); val repo = e.profileRepository(); val m = model()
        repo.profilesLoaded.first { it }
        compose.waitUntil(20_000) { m.state.value.ready && !m.state.value.busy }
        failureTrigger(record, false)
        compose.runOnIdle { m.close() }; compose.waitUntil(20_000) { m.state.value.draft == null }
        assertNull(e.profileManagementStore().read())
        val token = record["token"].asString; UUID.fromString(token)
        for ((key, suffix) in listOf("first" to "1", "second" to "2")) {
            val id = record[key].asLong
            e.database().profiles().getById(id)?.let {
                assertTrue(it.label in setOf("management-$token-$suffix", PRIVATE, "external-$token"))
                assertEquals(record["sourceSha"].asString, sha(repo.profileFile(id).readBytes()))
                repo.deleteProfile(id)
            }
            assertTrue(repo.profileDeletionComplete(id))
        }
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        assertEquals(record["rows"].asString, sha(gson.toJson(rows))); assertEquals(record["files"].asString, files(rows.map { it.id }))
        assertEquals(record["selected"].asLong, repo.currentProfileId.value)
        assertEquals(record["preferences"].asString, preferences()); assertEquals(record["scripts"].asString, scripts())
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        for (suffix in listOf("", ".bak", ".new")) assertFalse(File(compose.activity.noBackupFilesDir, "profile-management.v1$suffix").exists())
        assertTrue(marker().delete())
    }
    private fun saveTag(kind: String) = when (kind) {
        "rename" -> "profile_rename_save"; "delete" -> "profile_delete_save"; else -> "profiles_order_save"
    }
    private fun submit(kind: String) = compose.onNodeWithTag(saveTag(kind)).performClick()
    private suspend fun assertResult(record: JsonObject, committed: Boolean) {
        val e = entry(); val repo = e.profileRepository(); val kind = record["case"].asString.substringBefore('-')
        val first = record["first"].asLong; val second = record["second"].asLong
        // 作者 long：检查点与状态标志不能替代正式结果，提交前后分别回读实际行、顺序和文件元数据。
        if (kind == "delete" && committed) assertTrue(repo.profileDeletionComplete(first))
        else {
            assertEquals(if (kind == "rename" && committed) record["label"].asString.trim() else "management-${record["token"].asString}-1",
                e.database().profiles().getById(first)!!.label)
            assertEquals(record["firstSignature"].asString, signature(repo.profileFile(first)))
        }
        assertEquals(record["secondSignature"].asString, signature(repo.profileFile(second)))
        val expected = record[if (kind == "order" && committed) "order" else "originalOrder"].asJsonArray.map { it.asLong }
            .filterNot { kind == "delete" && committed && it == first }
        assertEquals(expected, repo.managementProfileOrder())
    }
    private fun assertBefore(first: Long, second: Long) {
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("profiles_order_$first").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithTag("profiles_order_$second").fetchSemanticsNode().boundsInRoot.top)
    }
    private suspend fun failureTrigger(record: JsonObject, enabled: Boolean) = entry().database().withTransaction {
        val db = entry().database().openHelper.writableDatabase
        db.execSQL("DROP TRIGGER IF EXISTS temp.management_cold_failure")
        if (enabled) {
            val kind = record["case"].asString.substringBefore('-'); val id = record["first"].asLong; require(id > 0)
            val operation = if (kind == "delete") "DELETE" else "UPDATE"
            db.execSQL("CREATE TEMP TRIGGER management_cold_failure BEFORE $operation ON profiles WHEN OLD.id=$id BEGIN SELECT RAISE(ABORT,'fixture-management-save-failure'); END")
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
        val cipher = File(compose.activity.noBackupFilesDir, "profile-management.v1")
        if (cipher.exists()) assertFalse(cipher.readBytes().toString(Charsets.ISO_8859_1).contains(PRIVATE))
    }
    private companion object { const val PRIVATE = "management-private-cold-20261107" }
}
