package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelProvider
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

/** 作者 long：正式宿主的独立密文与真实核心分两进程验证，恢复阶段禁止主动open掩盖导航丢失。 */
class ProviderDocumentColdInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "provider-document-cold.json")
    private val gson = Gson()
    private fun case(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProviderColdAcceptance"))
        return requireNotNull(InstrumentationRegistry.getArguments().getString("kclashProviderColdCase")).also { require(it in CASES) }
    }
    private fun model(): ProfileStatisticsViewModel {
        lateinit var result: ProfileStatisticsViewModel
        compose.runOnIdle { result = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] }
        return result
    }
    private suspend fun draft() = entry().providerDocumentStore().read()?.let {
        gson.fromJson(it.decodeToString(), ProviderDocumentDraft::class.java)
    }
    private suspend fun awaitDraft(predicate: (ProviderDocumentDraft) -> Boolean) {
        withTimeout(20_000) { while (draft()?.let(predicate) != true) delay(100) }
    }

    @Test fun prepareAndHoldProviderDraftForExternalDeath(): Unit = runBlocking {
        val case = case(); val e = entry(); val repo = e.profileRepository()
        repo.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("先收尾上一场Provider冷恢复", marker().exists())
        assertNull("不能覆盖用户正文草稿", e.providerDocumentStore().read())
        assertNull("不能覆盖用户选择器", e.providerPickerStore().read())
        assertNull("不能覆盖用户配置草稿", e.profileDraftStore().read())
        val expected = requireNotNull(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expected))
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        val original = rows.single { it.type == ProfileType.FILE && sha(repo.profileFile(it.id).readBytes()) == expected }
        val selected = requireNotNull(repo.currentProfileId.value)
        val bytes = repo.profileFile(original.id).readBytes()
        val label = "provider-cold-${UUID.randomUUID()}"
        val record = JsonObject().apply {
            addProperty("case", case); addProperty("preparedPid", android.os.Process.myPid()); addProperty("selected", selected)
            addProperty("label", label); addProperty("sha", expected); add("ids", gson.toJsonTree(rows.map { it.id }))
            addProperty("rows", sha(gson.toJson(rows))); addProperty("files", files(rows.map { it.id }))
            addProperty("preferences", preferences()); addProperty("scripts", scripts())
        }
        marker().writeText(record.toString())
        val sourceId = repo.addFileProfile("$label-source", bytes).id
        record.addProperty("sourceId", sourceId); marker().writeText(record.toString())
        val id = repo.addCustomProfile(label).id
        record.addProperty("id", id); marker().writeText(record.toString())
        repo.updateLocalProfileContent(id, bytes.decodeToString())
        val alias = "冷恢复资源-${UUID.randomUUID()}"
        record.addProperty("alias", alias); marker().writeText(record.toString())
        val groups = ProxyGroupDrafts.read(bytes.decodeToString()).mapIndexed { index, group ->
            if (index == 0) ProxyGroupDrafts.withMembers(group, ProxyGroupDrafts.members(group) + ProxyGroupMember(alias, true)) else group
        }
        repo.saveCustomProxyGroups(id, ProxyGroupsSave(groups, mapOf(alias to sourceId)), bytes.decodeToString()) { true }
        e.runtimeManager().selectProfile(id, repo::selectProfile)
        e.runtimeManager().getProxyGroups(id).getOrThrow()
        val cache = cache(record)
        assertTrue(cache.isFile && cache.readBytes().contentEquals(bytes))
        compose.onNodeWithTag("nav_dashboard").performClick()
        compose.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        val m = model()
        compose.waitUntil(20_000) { m.providers.value.let { it.profileId == id && it.providers != null && !it.isLoading } }
        if (case == "mrs-preview") {
            prepareMrsPreview(record)
            awaitCancellation()
        }
        val index = m.providers.value.providers!!.indexOfFirst { it.name == alias }
        assertTrue(index >= 0)
        compose.onNodeWithTag("profile_provider_menu_$index").performScrollTo().performClick()
        compose.onNodeWithTag("profile_provider_edit_$index").performClick()
        compose.waitUntil(20_000) { m.document.value?.draft != null && m.document.value?.loading == false }
        val intended = if (case == "failure") "invalid: [\n# $PRIVATE" else bytes.decodeToString() + "\n# $PRIVATE\n"
        compose.onNodeWithTag("profile_provider_document_content").performTextReplacement(intended)
        compose.onNodeWithTag("profile_provider_document_content").performTextInputSelection(TextRange(intended.length - 9, intended.length - 3))
        closeSoftKeyboard()
        record.addProperty("intended", sha(intended)); record.addProperty("selectionStart", intended.length - 9); record.addProperty("selectionEnd", intended.length - 3)
        marker().writeText(record.toString())
        if (case in setOf("before", "after")) {
            val window = if (case == "before") EditorCommitWindow.PROVIDER_INTENT else EditorCommitWindow.PROVIDER_COMMITTED
            EditorCommitProbe.arm(window)
            compose.onNodeWithTag("profile_provider_document_save").performClick()
            compose.waitUntil(20_000) { EditorCommitProbe.visits(window) > 0 || m.document.value?.let { !it.saving && it.error != null } == true }
            instrumentation.sendStatus(0, Bundle().apply { putString("kclash_provider_save_state",
                "intent=${EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT)};committed=${EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED)};error=${m.document.value?.error}") })
            assertEquals("保存必须到达精确核心边界", 1, EditorCommitProbe.visits(window))
            instrumentation.sendStatus(0, Bundle().apply { putString("kclash_provider_cold_stage", window.name) })
            assertTrue(draft()!!.pending)
            assertTrue(cache.readText() == if (case == "before") bytes.decodeToString() else intended)
        } else if (case == "failure") {
            compose.onNodeWithTag("profile_provider_document_save").performClick()
            compose.waitUntil(20_000) { m.document.value?.let { !it.saving && it.error != null } == true }
            assertTrue(cache.readBytes().contentEquals(bytes))
            // 作者 long：点击保存可能收起选区，冷恢复断言从提交反馈后的真实选区开始。
            compose.onNodeWithTag("profile_provider_document_content").performTextInputSelection(TextRange(intended.length - 9, intended.length - 3))
            closeSoftKeyboard()
        } else if (case == "draft") {
            compose.onNodeWithTag("profile_provider_document_back").performClick()
            compose.onNodeWithText("保存更改？").assertIsDisplayed()
        }
        compose.waitForIdle()
        awaitDraft { it.content == intended && it.uiDraft != null && it.uiDraft == m.document.value?.draft?.uiDraft }
        record.addProperty("ui", sha(gson.toJson(draft()!!.uiDraft)))
        record.addProperty("sourceSignature", signature(repo.profileFile(sourceId)))
        if (case == "conflict") cache.appendText("\n# external-provider-change\n")
        if (case == "deleted-profile") repo.deleteProfile(id)
        if (case == "deleted-source-profile") repo.deleteProfile(sourceId)
        if (case == "deleted-source-file") {
            // 作者 long：只删除本例按原1.yaml创建且已核对摘要的源文件，保留Room行以覆盖文件级缺失。
            assertEquals(record["sourceSignature"].asString, signature(repo.profileFile(sourceId)))
            assertTrue(repo.profileFile(sourceId).delete())
        }
        record.addProperty("cacheAtDeath", signature(cache))
        assertPrivateState()
        record.addProperty("ready", true); marker().writeText(record.toString())
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_provider_cold_ready", record.toString()) })
        // 作者 long：外部必须核对同一存活PID再终止；不能用正常测试退出冒充保存受理中的进程死亡。
        awaitCancellation()
    }

    @Test fun removeCompletedFixtureMarkerAfterKnownQualityCacheChange(): Unit = runBlocking {
        case()
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        val e = entry(); val repo = e.profileRepository()
        repo.profilesLoaded.first { it }
        // 作者 long：只清理已完成正文断言且临时对象全部清除的记录；保留发生变化的质量缓存，不回写历史偏好掩盖失败。
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProviderMarkerRecovery"))
        assertEquals(record["rows"].asString, sha(gson.toJson(e.database().profiles().getAll().sortedBy { it.id })))
        assertEquals(record["files"].asString, files(e.database().profiles().getAll().map { it.id }.sorted()))
        assertEquals(record["selected"].asLong, repo.currentProfileId.value)
        assertNull(e.database().profiles().getById(record["id"].asLong))
        assertNull(e.database().profiles().getById(record["sourceId"].asLong))
        assertFalse(cache(record).exists()); assertNull(e.providerDocumentStore().read())
        assertEquals(record["scripts"].asString, scripts())
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertTrue(marker().delete())
    }

    @Test fun inspectAndRecoverExactInterruptedProviderFixture(): Unit = runBlocking {
        case()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProviderFixtureRecovery"))
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        val e = entry(); val repo = e.profileRepository(); repo.profilesLoaded.first { it }
        val id = record["id"].asLong; val sourceId = record["sourceId"].asLong
        val label = record["label"].asString
        require(label.startsWith("provider-cold-") && UUID.fromString(label.removePrefix("provider-cold-")).toString() == label.removePrefix("provider-cold-"))
        check(e.database().profiles().getById(id)?.label == label)
        val sourceDeleted = record["case"].asString in setOf("deleted-source-profile", "deleted-source-file")
        if (record["case"].asString == "deleted-source-profile") check(e.database().profiles().getById(sourceId) == null)
        else check(e.database().profiles().getById(sourceId)?.label == "$label-source")
        if (sourceDeleted) check(!repo.profileFile(sourceId).exists())
        else check(sha(repo.profileFile(sourceId).readBytes()) == record["sha"].asString)
        val saved = draft()
        // 作者 long：只读预览可能已正常关闭、随后卡在父面板；必须核对本轮源与缓存未变，才能收尾自己的临时对象。
        val closedMrs = record["case"].asString == "mrs-preview" && record["ready"]?.asBoolean == true &&
            record.has("session") && record.has("verifiedPid") &&
            signature(repo.profileFile(sourceId)) == record["sourceSignature"]?.asString &&
            signature(cache(record)) == record["cacheAtDeath"]?.asString
        check(saved?.let { it.profileId == id && it.name == (record["previewName"] ?: record["alias"]).asString } ?: (sourceDeleted || closedMrs))
        val m = model()
        if (saved != null) compose.waitUntil(25_000) { m.document.value?.let { !it.loading && !it.saving } == true }
        else assertNull(m.document.value)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("kclash_provider_fixture_diagnostic", "pending=${saved?.pending};committed=${saved?.committed};bound=${m.document.value?.document != null};blocked=${m.document.value?.recoveryBlocked};cacheMatchesDraft=${cache(record).readText() == saved?.content};error=${m.document.value?.error}")
        })
        reportProviderPanel(record, "recovery")
        cleanup(record)
    }

    @Test fun verifyAutomaticProviderRecoveryAndCleanOwnFixture(): Unit = runBlocking {
        val case = case(); val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case, record["case"].asString); assertTrue(record["ready"].asBoolean)
        assertNotEquals(record["preparedPid"].asInt, android.os.Process.myPid())
        record["verifiedPid"]?.let { assertNotEquals(it.asInt, android.os.Process.myPid()) }
        val m = model(); val e = entry(); val repo = e.profileRepository()
        compose.waitUntil(25_000) { m.document.value?.let { it.draft != null && !it.loading } == true }
        if (case != "deleted-profile") compose.waitUntil(25_000) {
            m.document.value?.let { !it.loading && (it.document != null || it.error != null) } == true
        }
        assertTrue(m.document.value!!.content.let(::sha) == record["intended"].asString)
        assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT))
        assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED))
        compose.onNodeWithTag("profile_provider_document").assertExists()
        if (case == "draft") {
            compose.onNodeWithText("保存更改？").assertIsDisplayed()
            compose.onNodeWithText("继续编辑").performClick()
        }
        if (case in setOf("draft", "failure", "conflict", "deleted-profile", "deleted-provider-cache", "deleted-source-profile", "deleted-source-file")) {
            val field = compose.onNodeWithTag("profile_provider_document_content").fetchSemanticsNode().config
            assertTrue(sha(field[SemanticsProperties.EditableText].text) == record["intended"].asString)
            assertEquals(TextRange(record["selectionStart"].asInt, record["selectionEnd"].asInt), field[SemanticsProperties.TextSelectionRange])
        }
        if (case == "deleted-provider-cache") {
            // 作者 long：外部驱动在旧PID消失后删除派生缓存；恢复允许由未变源重建，mtime不再代表原文件。
            assertTrue(cache(record).isFile)
            assertEquals(record["sha"].asString, sha(cache(record).readBytes()))
            assertNotNull(m.document.value!!.document)
            assertFalse(m.document.value!!.committed)
        } else assertEquals(record["cacheAtDeath"].asString, signature(cache(record)))
        val sourceDeleted = case in setOf("deleted-source-profile", "deleted-source-file")
        assertEquals(if (sourceDeleted) "missing" else record["sourceSignature"].asString,
            signature(repo.profileFile(record["sourceId"].asLong)))
        if (case == "mrs-preview") {
            verifyMrsPreview(record)
            if (InstrumentationRegistry.getArguments().getString("kclashProviderKeep") == "true") {
                record.addProperty("verifiedPid", android.os.Process.myPid()); marker().writeText(record.toString())
                return@runBlocking
            }
        }
        when (case) {
            "after" -> {
                assertTrue(m.document.value!!.committed)
                compose.runOnIdle { m.saveProvider() }
                assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT))
                if (InstrumentationRegistry.getArguments().getString("kclashProviderKeep") == "true") {
                    awaitDraft { it.committed }
                    record.addProperty("verifiedPid", android.os.Process.myPid()); marker().writeText(record.toString())
                    return@runBlocking
                }
            }
            "before" -> {
                assertFalse(m.document.value!!.committed); assertTrue(m.document.value!!.dirty)
                compose.onNodeWithTag("profile_provider_document_save").performClick()
                // 作者 long：恢复自带中断提示，点击事件尚未进入保存时也有error；只认本次意图受理与最终关闭，不能被旧提示提前放行。
                try {
                    compose.waitUntil(20_000) { EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT) == 1 }
                    compose.waitUntil(20_000) { m.document.value == null }
                } finally {
                    val state = m.document.value
                    instrumentation.sendStatus(0, Bundle().apply { putString("kclash_provider_retry_state",
                        "intent=${EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT)};committed=${EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED)};closed=${state == null};saving=${state?.saving};readbackPending=${state?.committed};hasError=${state?.error != null}") })
                }
                assertNull(m.document.value)
                assertEquals(1, EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT))
                assertEquals(1, EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED))
                assertEquals(record["intended"].asString, sha(cache(record).readBytes()))
            }
            "conflict", "deleted-profile", "deleted-source-profile", "deleted-source-file" -> {
                assertTrue(m.document.value!!.recoveryBlocked); assertTrue(m.document.value!!.dirty)
                compose.runOnIdle { m.saveProvider() }
                assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT))
                if (case == "deleted-profile") assertNull(e.database().profiles().getById(record["id"].asLong))
                if (sourceDeleted) {
                    assertEquals(record["id"].asLong, repo.currentProfileId.value)
                    assertNull(m.document.value!!.document)
                    if (case == "deleted-source-profile") assertNull(e.database().profiles().getById(record["sourceId"].asLong))
                    else assertNotNull(e.database().profiles().getById(record["sourceId"].asLong))
                }
            }
            "failure" -> { assertTrue(m.document.value!!.dirty); assertNotNull(m.document.value!!.error) }
        }
        assertPrivateState()
        if (sourceDeleted && InstrumentationRegistry.getArguments().getString("kclashProviderKeep") == "true") {
            awaitDraft { it.content.let(::sha) == record["intended"].asString }
            record.addProperty("verifiedPid", android.os.Process.myPid()); marker().writeText(record.toString())
            return@runBlocking
        }
        cleanup(record)
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_provider_cold_verified", "case=$case;automatic-open=true;no-replay=true;formal-preserved=true;draft-cleared=true") })
    }

    private suspend fun prepareMrsPreview(record: JsonObject) {
        val m = model()
        // 作者 long：沿用原配置中的真实MRS，至少超过一屏才能证明滚动恢复，不生成替代规则。
        val providers = requireNotNull(m.providers.value.providers)
        val name = providers.filter { it.type == "Rule" && it.count in 200..3_000 }.minBy { it.count }.name
        record.addProperty("previewName", name); marker().writeText(record.toString())
        val index = providers.indexOfFirst { it.name == name }
        compose.onNodeWithTag("profile_provider_row_$index").performScrollTo().performClick()
        compose.waitUntil(20_000) { m.document.value?.let { it.draft != null && !it.loading } == true }
        val state = requireNotNull(m.document.value)
        val document = requireNotNull(state.document).document
        assertFalse(document.editable); assertFalse(state.editing); assertNull(state.error)
        compose.onNodeWithText("MRS 规则只读预览").assertIsDisplayed()
        compose.onNodeWithTag("profile_provider_document_save").assertDoesNotExist()
        assertTrue(document.content.count { it == '\n' } >= 100)
        compose.onNodeWithTag("profile_provider_document_content").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val scroll = compose.onNodeWithTag("profile_provider_document_content").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("真实MRS必须发生滚动", scroll > 0f)
        awaitDraft { it.content == document.content && it.uiDraft != null && it.uiDraft == m.document.value?.draft?.uiDraft }
        record.addProperty("intended", sha(document.content)); record.addProperty("digest", document.digest)
        record.addProperty("session", draft()!!.session); record.addProperty("scroll", scroll)
        record.addProperty("ui", sha(gson.toJson(draft()!!.uiDraft)))
        record.addProperty("sourceSignature", signature(entry().profileRepository().profileFile(record["sourceId"].asLong)))
        record.addProperty("cacheAtDeath", signature(cache(record)))
        assertPrivateState(document.content.take(128))
        record.addProperty("ready", true); marker().writeText(record.toString())
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_provider_cold_ready", record.toString()) })
    }

    private suspend fun verifyMrsPreview(record: JsonObject) {
        val m = model(); val state = requireNotNull(m.document.value)
        val document = requireNotNull(state.document).document
        assertEquals(record["previewName"].asString, state.draft!!.name)
        assertEquals(record["session"].asString, state.draft.session)
        assertEquals(record["digest"].asString, document.digest)
        assertEquals(record["intended"].asString, sha(document.content))
        assertFalse(document.editable); assertFalse(state.editing); assertFalse(state.dirty)
        assertFalse(state.saving); assertFalse(state.committed); assertFalse(state.recoveryBlocked); assertNull(state.error)
        compose.onNodeWithText("MRS 规则只读预览").assertIsDisplayed()
        compose.onNodeWithTag("profile_provider_document_save").assertDoesNotExist()
        assertEquals(record["scroll"].asFloat, compose.onNodeWithTag("profile_provider_document_content")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
        awaitDraft { it.session == record["session"].asString && !it.pending && !it.committed }
        if (InstrumentationRegistry.getArguments().getString("kclashProviderKeep") == "true") {
            assertPrivateState(document.content.take(128))
            return
        }
        // 作者 long：从自动恢复的页面逐层返回，确认清除独立会话后重开详情不会复活旧预览。
        compose.onNodeWithTag("profile_provider_document_back").performClick()
        awaitSameProviderPanel(record)
        compose.onNodeWithTag("dashboard_profile_detail_back").performClick()
        compose.onNodeWithTag("page_dashboard").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        awaitSameProviderPanel(record)
        assertNull(entry().providerDocumentStore().read())
        // 作者 long：手动onSaveInstanceState会把ComponentActivity生命周期降到CREATED，必须在最后一次UI操作后做Bundle泄漏检查。
        assertPrivateState(document.content.take(128))
    }

    private fun awaitSameProviderPanel(record: JsonObject) {
        val m = model()
        compose.onNodeWithTag("profile_provider_document").assertDoesNotExist()
        try {
            compose.waitUntil(20_000) { m.providers.value.let {
                it.profileId == record["id"].asLong && !it.isLoading && it.providers != null && it.error == null
            } }
        } finally { reportProviderPanel(record, "returned") }
        val index = m.providers.value.providers!!.indexOfFirst { it.name == record["previewName"].asString }
        assertTrue(index >= 0)
        compose.onNodeWithTag("profile_provider_row_$index").performScrollTo().assertIsDisplayed()
    }

    private fun reportProviderPanel(record: JsonObject, phase: String) {
        val m = model(); val p = m.providers.value; val s = m.state.value
        val id = record["id"].asLong
        val selected = entry().profileRepository().currentProfileId.value
        val detailNodes = compose.onAllNodesWithTag("dashboard_profile_detail_back").fetchSemanticsNodes().size
        // 作者 long：只记录状态和匹配结果，失败时区分导航丢失、读取失败与生命周期暂停，不泄漏资源正文或地址。
        instrumentation.sendStatus(0, Bundle().apply { putString("kclash_provider_panel_state",
            "phase=$phase;profileMatches=${p.profileId == id};profileNull=${p.profileId == null};" +
                "loading=${p.isLoading};count=${p.providers?.size};hasError=${p.error != null};" +
                "statisticsLoading=${s.isLoading};statisticsError=${s.error != null};document=${m.document.value != null};" +
                "selectedMatches=${selected == id};detailNodes=$detailNodes;" +
                "lifecycle=${compose.activity.lifecycle.currentState}") })
    }

    private suspend fun cleanup(record: JsonObject) {
        val e = entry(); val repo = e.profileRepository(); val m = model()
        compose.runOnIdle { m.closeProvider() }
        compose.waitUntil(15_000) { m.document.value == null }
        assertNull(e.providerDocumentStore().read())
        for (suffix in listOf("", ".bak", ".new")) assertFalse(File(compose.activity.noBackupFilesDir, "provider-document.v1$suffix").exists())
        e.runtimeManager().selectProfile(record["selected"].asLong, repo::selectProfile)
        for ((key, label) in listOf("id" to record["label"].asString, "sourceId" to record["label"].asString + "-source")) {
            val id = record[key].asLong
            e.database().profiles().getById(id)?.let { check(it.label == label); repo.deleteProfile(id) }
        }
        val cache = cache(record)
        if (cache.exists()) assertTrue(cache.delete())
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        assertEquals(record["rows"].asString, sha(gson.toJson(rows)))
        assertEquals(record["files"].asString, files(rows.map { it.id }))
        assertEquals(record["preferences"].asString, preferences()); assertEquals(record["scripts"].asString, scripts())
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertTrue(marker().delete())
    }
    private fun cache(record: JsonObject): File {
        val id = record["sourceId"].asLong; val sha = record["sha"].asString
        require(id > 0 && Regex("[a-f0-9]{64}").matches(sha))
        return File(compose.activity.filesDir, "mihomo/providers/profiles/$id-$sha.yaml")
    }
    private suspend fun preferences() = sha(gson.toJson(entry().database().appPreferences().getByPrefix("").associate { it.key to it.value }.toSortedMap()))
    private suspend fun scripts() = sha(gson.toJson(entry().database().scripts().getAll().sortedBy { it.id }))
    private fun files(ids: List<Long>) = ids.joinToString("|") { "$it:${signature(entry().profileRepository().profileFile(it))}" }
    private fun signature(file: File) = if (!file.exists()) "missing" else "${file.length()}:${file.lastModified()}:${sha(file.readBytes())}"
    private fun sha(text: String) = sha(text.toByteArray(Charsets.UTF_8))
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun assertPrivateState(privateText: String = PRIVATE) {
        val state = Bundle()
        compose.runOnIdle { instrumentation.callActivityOnSaveInstanceState(compose.activity, state) }
        val parcel = Parcel.obtain()
        val bytes = try { parcel.writeBundle(state); parcel.marshall() } finally { parcel.recycle() }
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            assertFalse("正文标记不得进入系统Bundle", bytes.toString(charset).contains(privateText))
        }
        val cipher = File(compose.activity.noBackupFilesDir, "provider-document.v1")
        if (cipher.exists()) assertFalse(cipher.readBytes().toString(Charsets.UTF_8).contains(privateText))
        // 作者 long：真实正文含订阅与节点凭据，本组仅记录摘要和控件状态，不截取正文画面。
    }
    private companion object {
        const val PRIVATE = "provider-private-cold-20261107"
        val CASES = setOf("draft", "failure", "before", "after", "conflict", "deleted-profile",
            "deleted-provider-cache", "deleted-source-profile", "deleted-source-file", "mrs-preview")
    }
}
