package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalRulesControllerTest {
    @Test fun reenterDuringSaveKeepsAcceptedOperationVisible() = runTest {
        val store = Store(); val runtime = Runtime()
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        val gate = CompletableDeferred<Unit>(); store.saveGate = gate
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))
        model.save(next); runCurrent()
        model.open()
        // 作者 long：Activity重建再次进入同一页面，不能把已受理保存伪装成一次新的加载。
        assertTrue(model.state.value.saving)
        gate.complete(Unit); runCurrent()
        assertEquals(next, model.state.value.rules)
    }

    @Test fun applyRetryAfterSelectionChangesDoesNotApplyNewProfile() = runTest {
        val store = Store(); val runtime = Runtime { store.currentProfileId.value }.apply { fail = true }
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        model.save(listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))); runCurrent()
        assertEquals(1, runtime.applies)
        store.currentProfileId.value = 2
        runtime.fail = false
        model.retryApply(); runCurrent()
        assertEquals(1, runtime.applies)
    }

    @Test fun globalRulesNeedNoProfileAndRejectConcurrentStaleWrites() = runTest {
        val store = Store().apply { currentProfileId.value = null; profiles.value = emptyList() }
        val runtime = Runtime()
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))
        model.save(next); runCurrent()
        assertEquals(next, store.global); assertEquals(0, runtime.applies)
        store.global = listOf(ExtensionRule("external", "GEOIP,CN,DIRECT"))
        model.save(emptyList()); runCurrent()
        assertEquals("external", store.global.single().id)
        assertNotNull(model.state.value.error)
        assertEquals(store.global, model.state.value.rules)
        assertTrue(model.state.value.blocked)
    }

    @Test fun savedRulesSurviveApplyFailureAndRetryDoesNotWriteAgain() = runTest {
        val store = Store(); val runtime = Runtime().apply { fail = true }
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,MATCH"))
        model.save(next); model.save(emptyList()); runCurrent()
        assertEquals(next, store.global); assertEquals(next, model.state.value.rules)
        assertNotNull(model.state.value.error)
        runtime.fail = false; model.retryApply(); runCurrent()
        assertNull(model.state.value.error); assertEquals(2, runtime.applies)
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
    }

    @Test fun profileSwitchDuringSaveDoesNotApplyToNewSelection() = runTest {
        val store = Store(); val runtime = Runtime { store.currentProfileId.value }
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        val gate = CompletableDeferred<Unit>(); store.saveGate = gate
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))
        model.save(next); runCurrent()
        store.currentProfileId.value = 2
        gate.complete(Unit); runCurrent()
        assertEquals(next, store.global); assertEquals(0, runtime.applies)
    }

    @Test fun encryptedSessionRecoversUiWithoutOpeningOrWriting() = runTest {
        val store = Store(); val disk = MemoryDraft(); val runtime = Runtime()
        val first = GlobalRulesController(store, runtime, backgroundScope, disk)
        runCurrent(); first.open(); runCurrent()
        val tree = EditorSaveableState(mapOf("selection" to listOf(EditorSavedValue("string", "private-draft"))))
        first.ui(first.state.value.draft!!.session, tree); runCurrent()
        val recovered = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
        assertTrue(recovered.state.value.ready)
        assertEquals(tree, recovered.state.value.draft!!.uiDraft)
        assertEquals(0, store.writes); assertEquals(0, runtime.applies)
    }

    @Test fun interruptedIntentWaitsForExplicitRetryAndKeepsSameReceiptId() = runTest {
        val store = Store(); val disk = MemoryDraft(); val runtime = Runtime()
        val first = GlobalRulesController(store, runtime, backgroundScope, disk) {
            if (it == EditorCommitWindow.GLOBAL_RULES_INTENT) throw CancellationException("process death")
        }
        runCurrent(); first.open(); runCurrent()
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))
        first.save(next); runCurrent()
        val id = first.state.value.draft!!.pending!!.operationId
        val recovered = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
        assertTrue(recovered.state.value.interrupted); assertEquals(0, store.writes)
        recovered.retryPending(); recovered.retryPending(); runCurrent()
        assertEquals(next, store.global); assertEquals(1, store.writes)
        assertEquals(listOf(id), store.committedIds); assertNull(recovered.state.value.draft!!.pending)
    }

    @Test fun receiptPreventsReplayEvenWhenExternalWriterRestoresOldValue() = runTest {
        for (window in listOf(EditorCommitWindow.GLOBAL_RULES_APPLIED, EditorCommitWindow.GLOBAL_RULES_COMMITTED)) {
            val store = Store(); val disk = MemoryDraft(); val runtime = Runtime()
            val first = GlobalRulesController(store, runtime, backgroundScope, disk) { if (it == window) throw CancellationException("death") }
            runCurrent(); first.open(); runCurrent()
            first.save(listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))); runCurrent()
            assertEquals(1, store.writes)
            store.global = emptyList()
            repeat(2) {
                val recovered = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
                assertTrue(recovered.state.value.draft!!.pending!!.committed)
                assertEquals(1, store.writes); assertEquals(0, runtime.applies)
            }
            val recovered = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
            recovered.retryPending(); runCurrent()
            assertEquals(1, store.writes); assertTrue(store.global.isEmpty()); assertTrue(store.receipts.isEmpty())
        }
    }

    @Test fun changedOrDeletedRowsBlockRetryAndKeepDraft() = runTest {
        for (external in listOf(emptyList(), listOf(ExtensionRule("r", "DOMAIN,changed.test,REJECT")))) {
            val store = Store().apply { global = listOf(ExtensionRule("r", "DOMAIN,old.test,DIRECT")) }
            val disk = MemoryDraft(); val runtime = Runtime()
            val first = GlobalRulesController(store, runtime, backgroundScope, disk) {
                if (it == EditorCommitWindow.GLOBAL_RULES_INTENT) throw CancellationException("death")
            }
            runCurrent(); first.open(); runCurrent()
            first.save(listOf(ExtensionRule("r", "DOMAIN,new.test,DIRECT"))); runCurrent()
            store.global = external
            val recovered = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
            assertTrue(recovered.state.value.blocked)
            recovered.retryPending(); runCurrent()
            assertEquals(external, store.global); assertEquals(0, store.writes)
            recovered.discardPending(); runCurrent()
            assertNull(recovered.state.value.draft!!.pending); assertFalse(recovered.state.value.blocked)
        }
    }

    @Test fun closeDeathBeforeAndAfterClearNeverReplaysCommit() = runTest {
        for (window in listOf(EditorCommitWindow.GLOBAL_RULES_CLOSE_BEFORE_CLEAR, EditorCommitWindow.GLOBAL_RULES_CLOSE_AFTER_CLEAR)) {
            val store = Store(); val disk = MemoryDraft(); val runtime = Runtime()
            val first = GlobalRulesController(store, runtime, backgroundScope, disk) { if (it == window) throw CancellationException("death") }
            runCurrent(); first.open(); runCurrent()
            first.save(listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))); runCurrent()
            first.close {}; runCurrent()
            val recovered = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
            assertEquals(window == EditorCommitWindow.GLOBAL_RULES_CLOSE_BEFORE_CLEAR, recovered.state.value.draft != null)
            assertEquals(1, store.writes); assertEquals(1, runtime.applies)
        }
    }

    @Test fun unreadableDraftIsRetainedAndLateUiCannotResurrectClosedSession() = runTest {
        val store = Store(); val disk = MemoryDraft().apply { value = "broken".toByteArray() }; val runtime = Runtime()
        val broken = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
        assertTrue(broken.state.value.recoveryFailed); broken.open(); runCurrent()
        assertEquals("broken", disk.value!!.decodeToString())
        disk.value = null; broken.retryRecovery(); runCurrent(); broken.open(); runCurrent()
        val session = broken.state.value.draft!!.session
        broken.close {}; runCurrent()
        broken.ui(session, EditorSaveableState()); runCurrent()
        assertNull(disk.value); assertNull(broken.state.value.draft)
    }

    @Test fun failedWriteKeepsIntentAndRetryWritesOnlyOnce() = runTest {
        val store = Store().apply { failSave = true }; val disk = MemoryDraft()
        val model = GlobalRulesController(store, Runtime(), backgroundScope, disk)
        runCurrent(); model.open(); runCurrent()
        val next = listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))
        model.save(next); model.save(next); runCurrent()
        assertEquals(0, store.writes); assertNotNull(model.state.value.draft!!.pending)
        assertTrue(model.state.value.interrupted)
        store.failSave = false
        model.retryPending(); model.retryPending(); runCurrent()
        assertEquals(1, store.writes); assertEquals(next, store.global)
    }

    @Test fun failedResultReadKeepsExplicitRetryAndCommittedApplyRemainsAvailable() = runTest {
        val store = Store(); val disk = MemoryDraft(); val runtime = Runtime()
        val model = GlobalRulesController(store, runtime, backgroundScope, disk) {
            if (it == EditorCommitWindow.GLOBAL_RULES_APPLIED) { store.failRead = true; error("result unavailable") }
        }
        runCurrent(); model.open(); runCurrent()
        model.save(listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))); runCurrent()
        assertTrue(model.state.value.interrupted); assertEquals(1, store.writes)
        store.failRead = false
        model.retryPending(); runCurrent()
        assertNull(model.state.value.draft!!.pending); assertTrue(model.state.value.draft!!.applyPending)
        model.reload(); runCurrent(); model.retryApply(); runCurrent()
        assertEquals(1, runtime.applies); assertEquals(1, store.writes)
        assertFalse(model.state.value.draft!!.applyPending)
    }

    @Test fun failedIntentPersistenceDoesNotCommitAndCanRetry() = runTest {
        val store = Store(); val disk = MemoryDraft(); val model = GlobalRulesController(store, Runtime(), backgroundScope, disk)
        runCurrent(); model.open(); runCurrent()
        disk.failNext = true
        model.save(listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))); runCurrent()
        assertTrue(model.state.value.interrupted); assertEquals(0, store.writes)
        model.retryPending(); runCurrent(); assertEquals(1, store.writes)
    }

    @Test fun uiArrivingDuringReceiptReadIsNotOverwritten() = runTest {
        val store = Store(); val disk = MemoryDraft(); val runtime = Runtime()
        val first = GlobalRulesController(store, runtime, backgroundScope, disk) {
            if (it == EditorCommitWindow.GLOBAL_RULES_INTENT) throw CancellationException("death")
        }
        runCurrent(); first.open(); runCurrent()
        first.save(listOf(ExtensionRule("r", "DOMAIN,example.test,DIRECT"))); runCurrent()
        val model = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
        val barrier = CompletableDeferred<Unit>(); store.receiptGate = barrier
        model.retryPending(); runCurrent()
        val tree = EditorSaveableState(mapOf("query" to listOf(EditorSavedValue("string", "latest-ui"))))
        model.ui(model.state.value.draft!!.session, tree)
        barrier.complete(Unit); runCurrent()
        assertEquals(tree, model.state.value.draft!!.uiDraft)
        val restored = GlobalRulesController(store, runtime, backgroundScope, disk); runCurrent()
        assertEquals(tree, restored.state.value.draft!!.uiDraft)
        assertEquals(1, store.writes)
    }

    @Test fun failedNewSaveDoesNotRetargetPreviouslyFailedApply() = runTest {
        val store = Store(); val runtime = Runtime { store.currentProfileId.value }.apply { fail = true }
        val model = GlobalRulesController(store, runtime, backgroundScope)
        model.open(); runCurrent()
        model.save(listOf(ExtensionRule("r", "DOMAIN,first.test,DIRECT"))); runCurrent()
        assertEquals(1L, model.state.value.draft!!.applyTargetId)
        store.currentProfileId.value = 2L; store.failSave = true; runtime.fail = false
        model.save(listOf(ExtensionRule("r", "DOMAIN,second.test,DIRECT"))); runCurrent()
        model.discardPending(); runCurrent()
        assertEquals(1L, model.state.value.draft!!.applyTargetId)
        model.retryApply(); runCurrent(); assertEquals(1, runtime.applies)
        assertEquals("DOMAIN,first.test,DIRECT", store.global.single().value)
    }

    private class MemoryDraft : ProfileDraftStore {
        var value: ByteArray? = null
        var failNext = false
        override suspend fun read() = value
        override suspend fun write(content: ByteArray?) { if (failNext) { failNext = false; error("disk") }; value = content }
    }

    private class Runtime(private val selected: () -> Long? = { 1 }) : CoreRuntimeStatusProvider {
        override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
        var applies = 0; var fail = false
        override suspend fun applyCurrentProfile(id: Long) { applies++; if (fail) error("apply failed") }
        override suspend fun <T> withExtensionWrite(targetId: Long?, write: suspend () -> T): T {
            val result = write()
            if (targetId != null && targetId == selected()) {
                try { applyCurrentProfile(targetId) } catch (_: Exception) { throw ProfileApplyAfterSaveException(targetId) }
            }
            return result
        }
    }
    private class Store : ProfileStore {
        override val profiles = MutableStateFlow(listOf(Profile.remote(1, "https://example.test/sub", "test")))
        override val currentProfileId = MutableStateFlow<Long?>(1)
        var global = emptyList<ExtensionRule>()
        var writes = 0; var failSave = false; var failRead = false
        var receiptGate: CompletableDeferred<Unit>? = null
        val receipts = mutableSetOf<String>()
        val committedIds = mutableListOf<String>()
        override suspend fun commitGlobalExtensionRules(expected: List<ExtensionRule>, rules: List<ExtensionRule>, commitId: String) {
            if (commitId in receipts) return
            compareAndSaveGlobalExtensionRules(expected, rules)
            receipts += commitId; committedIds += commitId
        }
        override suspend fun extensionCommitApplied(commitId: String): Boolean { receiptGate?.await(); return commitId in receipts }
        override suspend fun forgetExtensionCommit(commitId: String) { receipts -= commitId }

        var readGate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun readLocalProfileContent(id: Long): String { readGate?.await(); return "proxy-groups: [{name: Main, type: select}]\nrules: ['MATCH,Main']\n" }
        override suspend fun globalExtensionRules(): List<ExtensionRule> { check(!failRead); return global }
        override suspend fun saveGlobalExtensionRules(rules: List<ExtensionRule>) { saveGate?.await(); check(!failSave); global = rules; writes++ }
        override suspend fun saveExtension(id: Long, mode: String, scriptId: Long?, matchTarget: String?, extension: ProfileExtensionOverrides): Profile {
            val profile = profiles.value.single { it.id == id }
            val updated = profile.copy(extendType = mode, scriptId = scriptId, matchTarget = matchTarget,
                overridesJson = ProfileProviderOverrides.fromJsonString(profile.overridesJson).copy(extension = extension.copy(configured = true)).toJsonString())
            profiles.value = profiles.value.map { if (it.id == id) updated else it }
            return updated
        }
        override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile = error("unused")
        override suspend fun selectProfile(id: Long?) { error("不应切换配置") }
        override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
        override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
        override suspend fun addCustomProfile(label: String): Profile = error("unused")
        override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("不应重写正文")
        override suspend fun updateRemoteProfile(id: Long): Profile = error("不应下载订阅")
        override suspend fun deleteProfile(id: Long) { error("unused") }
        override suspend fun reorder(ids: List<Long>) { error("unused") }
    }
}
