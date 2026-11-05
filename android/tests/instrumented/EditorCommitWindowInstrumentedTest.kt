package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：准备测试绝不返回；外部收到ready后杀同一存活进程，另一次instrumentation只读取真实恢复结果。 */
class EditorCommitWindowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val gson = Gson()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "editor-commit-window.json")
    private fun case(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProfileClosureAcceptance"))
        return requireNotNull(InstrumentationRegistry.getArguments().getString("kclashCommitCase")).also { require(it in CASES) }
    }
    private fun window(case: String) = when (case) {
        "rules-conflict" -> EditorCommitWindow.RULES_INTENT
        "profile-close-before" -> EditorCommitWindow.PROFILE_CLOSE_BEFORE_CLEAR
        "profile-close-after" -> EditorCommitWindow.PROFILE_CLOSE_AFTER_CLEAR
        "local-close-before" -> EditorCommitWindow.LOCAL_CLOSE_INTENT
        "local-close-after" -> EditorCommitWindow.LOCAL_CLOSE_APPLIED
        else -> EditorCommitWindow.valueOf(case.replace("-before", "_INTENT").replace("-after", "_COMMITTED").replace('-', '_').uppercase())
    }

    @Test fun prepareAndHoldActualSaveWindowUntilExternalProcessDeath(): Unit = runBlocking {
        val case = case(); val e = entry(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertFalse("不能覆盖未收尾的保存窗口", marker().exists())
        assertNull("不能覆盖现有父草稿", e.profileDraftStore().read())
        assertNull("不能覆盖现有节点草稿", e.localProxyDraftStore().read())
        val profiles = e.database().profiles().getAll().sortedBy { it.id }
        val selectedBefore = requireNotNull(e.database().appPreferences().get("current_profile_id")?.toLongOrNull())
        assertTrue("本组需要有效原选择，不能主动修复后再记录保护基线", profiles.any { it.id == selectedBefore })
        compose.waitUntil(15_000) { repo.currentProfileId.value == selectedBefore }
        val local = repo.localProxies()
        val id = System.nanoTime().and(Long.MAX_VALUE)
        val profile = Profile.custom(id, "commit-window").copy(orderIndex = profiles.size)
        val record = JsonObject().apply {
            addProperty("case", case); addProperty("window", window(case).name); addProperty("id", id)
            addProperty("preparedPid", android.os.Process.myPid()); addProperty("selected", repo.currentProfileId.value)
            addProperty("profiles", sha(gson.toJson(profiles))); addProperty("files", profileFiles(profiles.map { it.id }))
            addProperty("local", sha(gson.toJson(local)))
        }
        // 作者 long：先记录独有ID再创建临时项；即使准备失败也能定位遗留物，marker不另存任何节点正文。
        marker().writeText(record.toString())
        e.database().profiles().insert(profile.toEntity())
        repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(readAuthorizedProxyProfile()) }
        if (case.startsWith("local")) {
            val nodeId = "commit-node-$id"
            val fields = ProxyGroupDrafts.root(repo.profileFile(id).readText()).getAsJsonArray("proxies").first().asJsonObject.deepCopy()
                .apply { addProperty("name", nodeId) }
            val row = LocalProxy(nodeId, fields.toString())
            record.addProperty("nodeId", nodeId); marker().writeText(record.toString())
            repo.saveLocalProxies(local, local + row)
            if (case.startsWith("local-close")) {
                val source = repo.profileFile(id).readText()
                val groups = ProxyGroupDrafts.read(source).toMutableList()
                groups[0] = ProxyGroupDrafts.withMembers(groups[0], ProxyGroupDrafts.members(groups[0]) + ProxyGroupMember(row.name))
                repo.saveCustomProxyGroups(id, ProxyGroupsSave(groups), source) { true }
                repo.selectProfile(id)
                assertTrue(LocalProxies.referenced(repo.profileFile(id).readText()).contains(row.name))
            }
            record.addProperty("bodyBefore", body(id)); record.addProperty("rowBefore", rowDigest(id))
            lateinit var model: LocalProxiesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[LocalProxiesViewModel::class.java] }
            compose.waitUntil(20_000) { !model.state.value.loading && model.state.value.rows.any { it.id == nodeId } }
            compose.runOnIdle { model.open(); model.edit(model.state.value.rows.single { it.id == nodeId }) }
            val edited = LocalProxies.withValue(row, "udp", JsonPrimitive(!(fields.get("udp")?.asBoolean ?: false)))
            compose.runOnIdle { model.updateEditor(requireNotNull(model.state.value.editor).copy(value = edited)) }
            val gate = if (case.startsWith("local-close")) null else EditorCommitProbe.arm(window(case))
            compose.onNodeWithTag("local_proxy_save").performClick()
            if (gate != null) withTimeout(20_000) { gate.awaitReached() }
            else {
                compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.editor == null }
                record.addProperty("directoryCommitted", sha(gson.toJson(repo.localProxies())))
                val closeGate = EditorCommitProbe.arm(window(case))
                compose.onNodeWithTag("local_proxies_back").performClick()
                withTimeout(30_000) { closeGate.awaitReached() }
            }
            val saved = localDraft()
            if (case.startsWith("local-close")) {
                assertTrue(saved.pendingClose)
                assertNull(saved.pending)
                assertTrue(model.state.value.isOpen && model.state.value.busy)
                val applied = e.runtimeManager().profileInputsApplied(id)
                val calls = if (case.endsWith("after")) 1 else 0
                assertEquals(calls, EditorCommitProbe.visits(EditorCommitWindow.LOCAL_CLOSE_APPLYING))
                assertEquals(calls, EditorCommitProbe.visits(EditorCommitWindow.LOCAL_CLOSE_APPLIED))
                if (case.endsWith("after")) assertTrue(applied)
                record.addProperty("actualReferencedApplyCompleted", calls == 1)
                record.addProperty("coreInputsLoadedAtWindow", applied)
            } else {
                assertEquals("node", saved.pending?.kind)
                assertEquals(edited, saved.pending!!.next.single { it.id == nodeId })
                assertEquals(case.endsWith("after"), repo.localProxies().single { it.id == nodeId } == edited)
            }
            assertEquals(record["bodyBefore"].asString, body(id))
            assertEquals(record["rowBefore"].asString, rowDigest(id))
            record.addProperty("expectedDirectory", sha(gson.toJson(local + edited)))
            record.addProperty("directoryAtWindow", sha(gson.toJson(repo.localProxies())))
            assertEncrypted("local-proxy-draft.v1")
        } else {
            lateinit var model: ProfilesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]; model.openEditor(profile) }
            compose.waitUntil(20_000) { model.editorState.value.overview != null }
            val source = repo.profileFile(id).readText()
            record.addProperty("bodyBefore", body(id)); record.addProperty("rowBefore", rowDigest(id))
            if (case.startsWith("profile-close")) {
                awaitParentProfile(id)
                val gate = EditorCommitProbe.arm(window(case))
                compose.runOnIdle { model.closeEditor() }
                withTimeout(20_000) { gate.awaitReached() }
                if (case.endsWith("before")) assertNotNull(e.profileDraftStore().read())
                else assertEmpty(e.profileDraftStore(), "profile-editor-draft.v1")
                assertNotNull(model.editorState.value.profile)
            } else {
                compose.runOnIdle {
                    when {
                        case.startsWith("rules") -> model.openRulesEditor(profile)
                        case.startsWith("proxies") -> model.openProxiesEditor(profile)
                        else -> model.openProxyGroupsEditor(profile)
                    }
                }
                compose.waitUntil(20_000) { when {
                    case.startsWith("rules") -> model.rulesEditorState.value.profile?.id == id && !model.rulesEditorState.value.isLoading
                    case.startsWith("proxies") -> model.proxiesEditorState.value.profile?.id == id && !model.proxiesEditorState.value.isLoading
                    else -> model.proxyGroupsEditorState.value.profile?.id == id && !model.proxyGroupsEditorState.value.isLoading
                } }
                val gate = EditorCommitProbe.arm(window(case))
                compose.runOnIdle { saveStructured(model, case, source) }
                withTimeout(25_000) { gate.awaitReached() }
                val saved = profileDraft()
                val pending = when {
                    case.startsWith("rules") -> saved.rules!!.also { assertTrue(it.isSaving); assertEquals(sha(source), sha(it.sourceContent)) }.pendingContent
                    case.startsWith("proxies") -> saved.proxies!!.also { assertTrue(it.isSaving); assertEquals(sha(source), sha(it.sourceContent)) }.pendingContent
                    else -> saved.groups!!.also { assertTrue(it.isSaving); assertEquals(sha(source), sha(it.sourceContent)) }.pendingContent
                }
                assertNotNull(pending); assertTrue(source != pending)
                record.addProperty("pendingSha256", sha(requireNotNull(pending)))
                if (case.endsWith("after")) {
                    assertEquals(sha(requireNotNull(pending)), sha(repo.profileFile(id).readText()))
                    assertNotEquals(record["rowBefore"].asString, rowDigest(id))
                } else {
                    assertEquals(record["bodyBefore"].asString, body(id))
                    assertEquals(record["rowBefore"].asString, rowDigest(id))
                }
                if (case == "rules-conflict") repo.profileFile(id).appendText("\n# external-commit-conflict\n")
                assertEncrypted("profile-editor-draft.v1")
            }
        }
        record.addProperty("bodyAtWindow", body(id)); record.addProperty("rowAtWindow", rowDigest(id))
        record.addProperty("ready", true); marker().writeText(record.toString())
        status("kclash_commit_ready", record)
        // 作者 long：不进入测试规则的Activity teardown；只有外部看到ready并核对同PID存活后才可结束进程。
        awaitCancellation()
    }

    @Test fun verifyColdRecoveryWithoutAutomaticReplay(): Unit = runBlocking {
        val case = case(); val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case, record["case"].asString); assertTrue(record["ready"].asBoolean)
        assertNotEquals(record["preparedPid"].asInt, android.os.Process.myPid())
        record["verifiedPid"]?.let { assertNotEquals(it.asInt, android.os.Process.myPid()) }
        val id = record["id"].asLong; val e = entry(); val repo = e.profileRepository()
        lateinit var profiles: ProfilesViewModel
        lateinit var local: LocalProxiesViewModel
        compose.runOnIdle {
            profiles = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]
            local = ViewModelProvider(compose.activity)[LocalProxiesViewModel::class.java]
        }
        withTimeout(20_000) { profiles.awaitEditorDraftRestoration() }
        if (case.startsWith("local")) {
            compose.waitUntil(20_000) { local.state.value.isOpen && !local.state.value.loading }
            if (case.startsWith("local-close")) {
                assertTrue(local.state.value.error.orEmpty().contains("退出已中断"))
                assertFalse(local.state.value.busy)
                // 作者 long：首页读取代理组也会加载核心输入；只有退出链路的调用计数才能区分正常读取与重放退出动作。
                assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.LOCAL_CLOSE_APPLYING))
                assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.LOCAL_CLOSE_APPLIED))
                record.addProperty("closeApplyCallsBeforeExplicitRetry", 0)
            } else if (case.endsWith("after")) {
                assertNull(local.state.value.editor)
                assertTrue(local.state.value.error.orEmpty().contains("已确认上次节点保存"))
            } else {
                assertNotNull(local.state.value.editor)
                assertTrue(local.state.value.error.orEmpty().contains("上次操作已中断"))
            }
            assertEquals(record["directoryAtWindow"].asString, sha(gson.toJson(repo.localProxies())))
        } else if (case == "profile-close-after") {
            awaitNoParentDraft()
            assertNull(profiles.editorState.value.profile)
            assertNull(profiles.rulesEditorState.value.profile)
            assertNull(profiles.proxiesEditorState.value.profile)
            assertNull(profiles.proxyGroupsEditorState.value.profile)
        } else {
            compose.waitUntil(20_000) { profiles.editorState.value.profile?.id == id && profiles.editorState.value.overview != null }
            when {
                case.startsWith("rules") -> {
                    compose.waitUntil(20_000) { profiles.rulesEditorState.value.profile?.id == id && !profiles.rulesEditorState.value.isLoading }
                    val restored = profiles.rulesEditorState.value
                    assertFalse(restored.isSaving); assertNull(restored.pendingContent)
                    if (case.endsWith("after")) {
                        assertNull(restored.interruptedRules); assertNull(restored.error)
                        assertEquals(record["pendingSha256"].asString, sha(restored.sourceContent))
                    } else {
                        assertNotNull(restored.interruptedRules)
                        assertTrue(restored.error.orEmpty().contains(if (case == "rules-conflict") "配置正文已变化" else "上次保存已中断"))
                    }
                }
                case.startsWith("proxies") -> if (case.endsWith("after")) assertNull(profiles.proxiesEditorState.value.profile)
                    else { compose.waitUntil(20_000) { profiles.proxiesEditorState.value.profile?.id == id }; assertFalse(profiles.proxiesEditorState.value.isSaving); assertTrue(profiles.proxiesEditorState.value.error.orEmpty().contains("上次保存已中断")) }
                case.startsWith("groups") -> if (case.endsWith("after")) assertNull(profiles.proxyGroupsEditorState.value.profile)
                    else { compose.waitUntil(20_000) { profiles.proxyGroupsEditorState.value.profile?.id == id }; assertFalse(profiles.proxyGroupsEditorState.value.isSaving); assertTrue(profiles.proxyGroupsEditorState.value.error.orEmpty().contains("上次保存已中断")) }
            }
        }
        // 作者 long：mtime、正文和整行元数据均不变，排除恢复时重复提交后恰好得到相同正文的假通过。
        assertEquals(record["bodyAtWindow"].asString, body(id)); assertEquals(record["rowAtWindow"].asString, rowDigest(id))
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        if (InstrumentationRegistry.getArguments().getString("kclashCommitKeep") == "true") {
            record.addProperty("verifiedPid", android.os.Process.myPid()); marker().writeText(record.toString())
            status("kclash_commit_verified_once", record)
            return@runBlocking
        }
        if (case == "rules-conflict") {
            val old = profiles.rulesEditorState.value.sourceContent
            compose.runOnIdle { profiles.saveRules(requireNotNull(profiles.rulesEditorState.value.interruptedRules)) }
            compose.waitUntil(20_000) { !profiles.rulesEditorState.value.isSaving }
            assertTrue(profiles.rulesEditorState.value.error.orEmpty().contains("配置正文已变化"))
            assertEquals(sha(old), sha(profiles.rulesEditorState.value.sourceContent))
            assertEquals(record["bodyAtWindow"].asString, body(id)); assertEquals(record["rowAtWindow"].asString, rowDigest(id))
        } else if (!case.endsWith("after") && !case.startsWith("profile-close") && !case.startsWith("local-close")) {
            if (case.startsWith("local")) {
                val value = requireNotNull(local.state.value.editor).value
                compose.runOnIdle { local.save(value) }
                compose.waitUntil(20_000) { !local.state.value.busy && local.state.value.editor == null }
                assertEquals(record["expectedDirectory"].asString, sha(gson.toJson(repo.localProxies())))
            } else {
                val source = repo.profileFile(id).readText()
                compose.runOnIdle { saveStructured(profiles, case, source) }
                compose.waitUntil(25_000) { sha(repo.profileFile(id).readText()) == record["pendingSha256"].asString }
                compose.waitUntil(20_000) { when {
                    case.startsWith("rules") -> !profiles.rulesEditorState.value.isSaving
                    case.startsWith("proxies") -> profiles.proxiesEditorState.value.profile == null
                    else -> profiles.proxyGroupsEditorState.value.profile == null
                } }
            }
        }
        if (case.startsWith("local")) {
            compose.runOnIdle { if (local.state.value.editor != null) local.dismissEditor() }
            compose.waitUntil(15_000) { !local.state.value.busy }
            compose.runOnIdle { local.close() }
            compose.waitUntil(30_000) { !local.state.value.isOpen }
            assertEmpty(e.localProxyDraftStore(), "local-proxy-draft.v1")
            if (case.startsWith("local-close")) {
                assertTrue("显式重试必须完成实际引用应用", e.runtimeManager().profileInputsApplied(id))
                assertEquals(1, EditorCommitProbe.visits(EditorCommitWindow.LOCAL_CLOSE_APPLYING))
                assertEquals(1, EditorCommitProbe.visits(EditorCommitWindow.LOCAL_CLOSE_APPLIED))
                record.addProperty("closeApplyCallsAfterExplicitRetry", 1)
            }
            val rows = repo.localProxies()
            repo.saveLocalProxies(rows, rows.filterNot { it.id == record["nodeId"].asString })
        }
        compose.runOnIdle { profiles.closeEditor() }
        compose.waitUntil(20_000) { profiles.editorState.value.profile == null }
        assertEmpty(e.profileDraftStore(), "profile-editor-draft.v1")
        val selected = record["selected"].takeUnless { it.isJsonNull }?.asLong
        repo.selectProfile(selected)
        repo.deleteProfile(id)
        assertNull(e.database().profiles().getById(id))
        assertFalse("临时正文清理失败时保留marker供继续收尾", repo.profileFile(id).exists())
        val original = e.database().profiles().getAll().sortedBy { it.id }
        assertEquals(record["profiles"].asString, sha(gson.toJson(original)))
        assertEquals(record["files"].asString, profileFiles(original.map { it.id }))
        assertEquals(selected, repo.currentProfileId.value)
        assertEquals(record["local"].asString, sha(gson.toJson(repo.localProxies())))
        record.addProperty("restoredPid", android.os.Process.myPid()); record.addProperty("preserved", true)
        status("kclash_commit_verified", record)
        assertTrue(marker().delete())
    }

    @Test fun discardOnlyUnreadyParentCloseFixtureAndPreserveFormalData(): Unit = runBlocking {
        assertEquals("profile-close-before", case())
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals("profile-close-before", record["case"].asString)
        assertFalse(record.has("ready"))
        val e = entry(); val repo = e.profileRepository(); val id = record["id"].asLong
        val original = e.database().profiles().getAll().filterNot { it.id == id }.sortedBy { it.id }
        assertEquals(record["profiles"].asString, sha(gson.toJson(original)))
        assertEquals(record["files"].asString, profileFiles(original.map { it.id }))
        assertEquals(record["local"].asString, sha(gson.toJson(repo.localProxies())))
        val temp = requireNotNull(e.database().profiles().getById(id))
        assertEquals("commit-window", temp.label)
        assertEquals(sha(readAuthorizedProxyProfile()), sha(repo.profileFile(id).readBytes()))
        assertNull("不能丢弃后来出现的用户本地代理草稿", e.localProxyDraftStore().read())
        // 作者 long：后来打开的草稿可能属于远程父页或独立子页，只看本地editor为空会误删其他配置的未确认输入。
        e.profileDraftStore().read()?.let { bytes ->
            val saved = gson.fromJson(bytes.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java)
            val owners = listOfNotNull(saved.editor.profile?.id, saved.remote.profile?.id,
                saved.rules?.profile?.id, saved.groups?.profile?.id, saved.proxies?.profile?.id)
            assertTrue("只能清理marker所属临时配置的草稿", owners.all { it == id })
        }
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
        withTimeout(20_000) { model.awaitEditorDraftRestoration() }
        model.editorState.value.profile?.let { assertEquals(id, it.id) }
        model.remoteEditorState.value.profile?.let { assertEquals(id, it.id) }
        model.rulesEditorState.value.profile?.let { assertEquals(id, it.id) }
        model.proxyGroupsEditorState.value.profile?.let { assertEquals(id, it.id) }
        model.proxiesEditorState.value.profile?.let { assertEquals(id, it.id) }
        compose.runOnIdle { model.closeEditor() }
        compose.waitUntil(20_000) { model.editorState.value.profile == null }
        assertEmpty(e.profileDraftStore(), "profile-editor-draft.v1")
        repo.selectProfile(record["selected"].takeUnless { it.isJsonNull }?.asLong)
        repo.deleteProfile(id)
        assertFalse(repo.profileFile(id).exists())
        assertEquals(record["profiles"].asString, sha(gson.toJson(e.database().profiles().getAll().sortedBy { it.id })))
        assertEquals(record["files"].asString, profileFiles(original.map { it.id }))
        assertTrue(marker().delete())
    }

    private fun saveStructured(model: ProfilesViewModel, case: String, source: String) {
        when {
            case.startsWith("rules") -> model.saveRules(ProfileRulesEditor.extractRules(source) + "\nDOMAIN,commit-window.invalid,DIRECT")
            case.startsWith("proxies") -> model.saveProxies(ProfileProxiesEditor.extractProxies(source) + "\n# commit-window-private")
            else -> {
                val groups = ProxyGroupDrafts.read(source).toMutableList()
                groups[0] = groups[0].edit { it.addProperty("hidden", !groups[0].flag("hidden")) }
                val save = ProxyGroupsSave(groups)
                if (case.startsWith("groups-yaml")) model.saveProxyGroups(ProfileProxyGroupsEditor.extractGroups(ProxyGroupDrafts.apply(source, save)))
                else model.saveProxyGroupsDraft(save)
            }
        }
    }
    private suspend fun profileDraft() = requireNotNull(entry().profileDraftStore().read()).let { gson.fromJson(it.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java) }
    private suspend fun localDraft() = requireNotNull(entry().localProxyDraftStore().read()).let { gson.fromJson(it.toString(Charsets.UTF_8), LocalProxyDraftSnapshot::class.java) }
    private suspend fun awaitParentProfile(id: Long) = withTimeout(15_000) { while (entry().profileDraftStore().read()?.let { gson.fromJson(it.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java).editor.profile?.id } != id) delay(100) }
    private suspend fun awaitNoParentDraft() { assertEmpty(entry().profileDraftStore(), "profile-editor-draft.v1") }
    private suspend fun assertEmpty(store: ProfileDraftStore, name: String) {
        withTimeout(20_000) { while (store.read() != null) delay(100) }
        for (suffix in listOf("", ".bak", ".new")) assertFalse(File(compose.activity.noBackupFilesDir, name + suffix).exists())
    }
    private fun assertEncrypted(name: String) {
        val bytes = File(compose.activity.noBackupFilesDir, name).readBytes()
        assertTrue(bytes.size >= 29 && bytes[0] == 1.toByte())
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("commit-window-private"))
    }
    private fun sha(text: String) = sha(text.toByteArray())
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun body(id: Long) = entry().profileRepository().profileFile(id).let { "${it.length()}:${it.lastModified()}:${sha(it.readBytes())}" }
    private suspend fun rowDigest(id: Long) = sha(gson.toJson(entry().database().profiles().getById(id)))
    private fun profileFiles(ids: List<Long>) = ids.joinToString("|") { "$it:${body(it)}" }
    private fun status(key: String, record: JsonObject) = instrumentation.sendStatus(2, Bundle().apply { putString(key, record.toString()) })
    private companion object {
        val CASES = setOf("rules-before", "rules-after", "rules-conflict", "proxies-before", "proxies-after",
            "groups-before", "groups-after", "groups-yaml-before", "groups-yaml-after", "local-before", "local-after",
            "local-close-before", "local-close-after", "profile-close-before", "profile-close-after")
    }
}
