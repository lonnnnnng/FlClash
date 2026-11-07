package com.lonnnnnng.flclash.ui

import com.google.gson.Gson
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileExtensionColdControllerTest {
    private val original = ExtensionRule("old", "DOMAIN,original.test,DIRECT")
    private val edited = original.copy(value = "DOMAIN,private-extension-cold.test,REJECT")
    private val tree = EditorSaveableState(mapOf("form" to listOf(EditorSavedValue.capture("private-extension-cold"))))

    @Test fun localAndGlobalCommitWindowsRecoverWithoutReplayingAndRetryOnlyOnce() = runTest {
        for (global in listOf(false, true)) for (window in listOf(EditorCommitWindow.EXTENSION_INTENT, EditorCommitWindow.EXTENSION_COMMITTED)) {
            val repo = Store(); val disk = Disk(); val runtime = Runtime()
            val oldScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
            val model = ProfileExtensionController(repo, runtime, oldScope, StandardTestDispatcher(testScheduler), disk) {
                if (it == window) awaitCancellation()
            }
            runCurrent(); model.open(1); runCurrent()
            model.ui(checkNotNull(model.state.value.draft).session, tree); runCurrent()
            if (global) model.globalRules(listOf(edited)) else model.rules(listOf(edited))
            runCurrent()
            val pending = Gson().fromJson(checkNotNull(disk.bytes).toString(Charsets.UTF_8), ProfileExtensionDraft::class.java)
            assertNotNull(pending.pending)
            assertEquals(window == EditorCommitWindow.EXTENSION_COMMITTED, pending.pending?.committed)
            assertEquals(if (window == EditorCommitWindow.EXTENSION_COMMITTED) 1 else 0, repo.writes)
            oldScope.cancel(); runCurrent()
            val restored = ProfileExtensionController(repo, runtime, backgroundScope, StandardTestDispatcher(testScheduler), disk)
            runCurrent()
            assertEquals(tree, restored.state.value.draft?.uiDraft)
            assertTrue(restored.state.value.interrupted)
            val before = repo.writes
            restored.retryPending(); restored.retryPending(); runCurrent()
            assertNull(restored.state.value.draft?.pending)
            assertEquals(if (window == EditorCommitWindow.EXTENSION_INTENT) before + 1 else before, repo.writes)
            assertEquals(listOf(edited), if (global) repo.global else ProfileExtensionValues.from(repo.profiles.value.single()).settings.rules)
            assertEquals(0, runtime.applies)
            val again = ProfileExtensionController(repo, runtime, backgroundScope, StandardTestDispatcher(testScheduler), disk)
            runCurrent(); assertNull(again.state.value.draft?.pending)
            assertEquals(1, repo.writes)
        }
    }

    @Test fun coldFailureRetainsIntentAndDuplicateConfirmDoesNotQueueAnotherWrite() = runTest {
        for (global in listOf(false, true)) {
            val repo = Store().apply { failSave = true }; val disk = Disk()
            val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
            runCurrent(); model.open(1); runCurrent()
            repeat(2) { if (global) model.globalRules(listOf(edited)) else model.rules(listOf(edited)) }
            runCurrent()
            assertEquals(1, repo.attempts); assertEquals(0, repo.writes)
            assertNotNull(model.state.value.draft?.pending)
            val restored = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
            runCurrent(); assertTrue(restored.state.value.interrupted)
            repo.failSave = false
            restored.retryPending(); runCurrent()
            assertEquals(1, repo.writes); assertNull(restored.state.value.error)
        }
    }

    @Test fun externalRuleDeletionOrChangeBlocksColdRetryForLocalAndGlobalRules() = runTest {
        for (global in listOf(false, true)) for (deleted in listOf(false, true)) {
            val repo = Store(); val disk = Disk()
            val oldScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
            val model = ProfileExtensionController(repo, Runtime(), oldScope, StandardTestDispatcher(testScheduler), disk) { awaitCancellation() }
            runCurrent(); model.open(1); runCurrent()
            model.ui(checkNotNull(model.state.value.draft).session, tree); runCurrent()
            if (global) model.globalRules(listOf(edited)) else model.rules(listOf(edited))
            runCurrent(); oldScope.cancel(); runCurrent()
            val changed = if (deleted) emptyList() else listOf(original.copy(value = "DOMAIN,external.test,DIRECT"))
            if (global) repo.global = changed else repo.externalRules(changed)
            val restored = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
            runCurrent()
            assertTrue(restored.state.value.blocked); assertEquals(tree, restored.state.value.draft?.uiDraft)
            restored.retryPending(); runCurrent()
            assertEquals(0, repo.writes)
            assertEquals(changed, if (global) repo.global else ProfileExtensionValues.from(repo.profiles.value.single()).settings.rules)
        }
    }

    @Test fun missingHostCannotRecreateProfileOrWriteGlobalDirectory() = runTest {
        val repo = Store(); val disk = Disk()
        val oldScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val model = ProfileExtensionController(repo, Runtime(), oldScope, StandardTestDispatcher(testScheduler), disk) { awaitCancellation() }
        runCurrent(); model.open(1); runCurrent(); model.globalRules(listOf(edited)); runCurrent()
        oldScope.cancel(); repo.profiles.value = emptyList(); runCurrent()
        val restored = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent(); assertTrue(restored.state.value.blocked)
        restored.retryPending(); runCurrent()
        assertTrue(repo.profiles.value.isEmpty()); assertEquals(listOf(original), repo.global); assertEquals(0, repo.writes)
    }

    @Test fun writeBeforeMissingAcknowledgementIsReconciledWithoutSecondCommit() = runTest {
        val repo = Store().apply { failAfterWrite = true }; val disk = Disk()
        val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent(); model.open(1); runCurrent(); model.rules(listOf(edited)); runCurrent()
        assertEquals(1, repo.writes)
        assertTrue(model.state.value.draft?.pending?.committed == true)
        repo.failAfterWrite = false
        model.retryPending(); runCurrent()
        assertEquals(1, repo.writes); assertNull(model.state.value.draft?.pending)
    }

    @Test fun staleVisibleRulesCannotOverwriteMoreRecentLocalOrGlobalList() = runTest {
        val repo = Store(); val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler))
        model.open(1); runCurrent()
        val updated = listOf(original.copy(value = "DOMAIN,other.test,DIRECT"))
        repo.externalRules(updated); repo.global = updated; runCurrent()
        model.rules(listOf(original), listOf(edited)); runCurrent()
        model.globalRules(listOf(original), listOf(edited)); runCurrent()
        assertEquals(0, repo.writes)
        assertEquals(updated, ProfileExtensionValues.from(repo.profiles.value.single()).settings.rules)
        assertEquals(updated, repo.global)
    }

    @Test fun failedCipherReadNeverOverwritesOriginalAndExplicitRetryRestoresIt() = runTest {
        val draft = ProfileExtensionDraft(1, uiDraft = tree)
        val disk = Disk().apply { bytes = Gson().toJson(draft).toByteArray(); failRead = true }
        val originalBytes = disk.bytes!!.copyOf()
        val model = ProfileExtensionController(Store(), Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent(); model.open(2); runCurrent()
        assertTrue(model.state.value.recoveryFailed); assertArrayEquals(originalBytes, disk.bytes)
        disk.failRead = false; model.retryRecovery(); runCurrent()
        assertEquals(draft.session, model.state.value.draft?.session); assertEquals(tree, model.state.value.draft?.uiDraft)
    }

    @Test fun failedIntentPersistenceDoesNotWriteMetadataAndExplicitRetryCanRecover() = runTest {
        val disk = Disk(); val repo = Store()
        val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent(); model.open(1); runCurrent(); disk.failWrite = true
        model.rules(listOf(edited)); runCurrent()
        assertEquals(0, repo.writes)
        disk.failWrite = false; model.retryPending(); runCurrent()
        assertEquals(1, repo.writes)
    }

    @Test fun failedPendingClearKeepsCommittedBarrierAndRetryNeverReplays() = runTest {
        val disk = Disk(); val repo = Store()
        val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk) {
            if (it == EditorCommitWindow.EXTENSION_COMMITTED) disk.failWrite = true
        }
        runCurrent(); model.open(1); runCurrent(); model.rules(listOf(edited)); runCurrent()
        assertEquals(1, repo.writes); assertTrue(model.state.value.draft?.pending?.committed == true)
        disk.failWrite = false; model.retryPending(); runCurrent()
        assertEquals(1, repo.writes); assertNull(model.state.value.draft?.pending)
    }

    @Test fun discardedSessionCannotBeResurrectedByLateUiAndFailedClearKeepsWindow() = runTest {
        val disk = Disk(); val model = ProfileExtensionController(Store(), Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent(); model.open(1); runCurrent()
        val session = checkNotNull(model.state.value.draft).session
        disk.failClear = true
        var closed = false
        model.closeWithoutApplying { closed = true }; runCurrent()
        assertFalse(closed); assertEquals(session, model.state.value.draft?.session)
        disk.failClear = false; model.closeWithoutApplying { closed = true }; runCurrent()
        assertTrue(closed); model.ui(session, tree); runCurrent()
        assertNull(disk.bytes); assertNull(model.state.value.draft)
    }

    @Test fun committedThenExternallyRevertedValuesNeverReplayAcrossReceiptGap() = runTest {
        for (global in listOf(false, true)) {
            val repo = Store().apply { holdAfterCommit = true }; val before = repo.profiles.value
            val disk = Disk()
            val oldScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
            val model = ProfileExtensionController(repo, Runtime(), oldScope, StandardTestDispatcher(testScheduler), disk)
            runCurrent(); model.open(1); runCurrent()
            if (global) model.globalRules(listOf(original, edited.copy(id = "new"))) else model.rules(listOf(original, edited.copy(id = "new")))
            runCurrent()
            assertEquals(1, repo.writes)
            assertFalse(Gson().fromJson(checkNotNull(disk.bytes).toString(Charsets.UTF_8), ProfileExtensionDraft::class.java).pending!!.committed)
            oldScope.cancel(); runCurrent()
            repo.profiles.value = before; repo.global = listOf(original); repo.holdAfterCommit = false
            val restored = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
            runCurrent(); assertTrue(restored.state.value.draft?.pending?.committed == true)
            restored.retryPending(); runCurrent()
            assertEquals(1, repo.writes)
            assertEquals(before, repo.profiles.value); assertEquals(listOf(original), repo.global)
            assertTrue(repo.receipts.isEmpty())
        }
    }

    @Test fun differentSettingCannotReplaceFailedPendingIntent() = runTest {
        val repo = Store().apply { failSave = true }; val disk = Disk()
        val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent(); model.open(1); runCurrent(); model.mode("script"); runCurrent()
        val originalIntent = model.state.value.draft?.pending
        assertTrue(model.state.value.interrupted)
        repo.failSave = false; model.target("Main"); runCurrent()
        assertEquals(originalIntent, model.state.value.draft?.pending); assertEquals(0, repo.writes)
        model.retryPending(); runCurrent()
        assertEquals("script", model.state.value.mode); assertNull(model.state.value.profile?.matchTarget)
    }

    @Test fun restoredSessionIsNotReadyUntilReceiptReconciliationAndPersistenceFinish() = runTest {
        val repo = Store()
        val before = ProfileExtensionValues.from(repo.profiles.value.single())
        val pending = ProfileExtensionCommit(before = before, after = before.copy(settings = before.settings.copy(rules = listOf(edited))))
        val disk = Disk().apply { bytes = Gson().toJson(ProfileExtensionDraft(1, uiDraft = tree, pending = pending)).toByteArray() }
        repo.receipts += pending.operationId
        repo.receiptReadBarrier = CompletableDeferred()
        disk.writeBarrier = CompletableDeferred()
        val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent()
        // 作者 long：正式值已回到旧值时必须先查事务回执；页面提前就绪会短暂开放尚未核对的编辑会话。
        assertFalse(model.state.value.ready)
        repo.receiptReadBarrier!!.complete(Unit); runCurrent()
        assertFalse(model.state.value.ready)
        disk.writeBarrier!!.complete(Unit); runCurrent()
        assertTrue(model.state.value.ready)
        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.draft?.pending?.committed == true)
        assertTrue(Gson().fromJson(disk.bytes!!.decodeToString(), ProfileExtensionDraft::class.java).pending!!.committed)
        assertEquals(0, repo.writes)
    }

    @Test fun repeatedReceiptReadPreservesUiChangesMadeWhileDatabaseIsSuspended() = runTest {
        val repo = Store()
        val before = ProfileExtensionValues.from(repo.profiles.value.single())
        val pending = ProfileExtensionCommit(before = before, after = before.copy(settings = before.settings.copy(rules = listOf(edited))))
        val draft = ProfileExtensionDraft(1, uiDraft = tree, pending = pending)
        val disk = Disk().apply { bytes = Gson().toJson(draft).toByteArray() }
        val model = ProfileExtensionController(repo, Runtime(), backgroundScope, StandardTestDispatcher(testScheduler), disk)
        runCurrent(); assertTrue(model.state.value.ready)
        repo.receiptReadBarrier = CompletableDeferred()
        model.refreshApplied(); runCurrent()
        val latestTree = EditorSaveableState(mapOf("query" to listOf(EditorSavedValue.capture("latest-search"))))
        model.ui(draft.session, latestTree); runCurrent()
        repo.receiptReadBarrier!!.complete(Unit); runCurrent()
        assertEquals(latestTree, model.state.value.draft?.uiDraft)
        assertEquals(latestTree, Gson().fromJson(disk.bytes!!.decodeToString(), ProfileExtensionDraft::class.java).uiDraft)
        assertEquals(0, repo.writes)
    }

    private class Disk : ProfileDraftStore {
        var bytes: ByteArray? = null
        var failRead = false; var failWrite = false; var failClear = false
        var writeBarrier: CompletableDeferred<Unit>? = null
        override suspend fun read(): ByteArray? { check(!failRead); return bytes?.copyOf() }
        override suspend fun write(content: ByteArray?) { writeBarrier?.await(); check(!failWrite && !(content == null && failClear)); bytes = content?.copyOf() }
    }
    private class Runtime : CoreRuntimeStatusProvider {
        override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
        var applies = 0
        override suspend fun profileInputsApplied(id: Long) = true
        override suspend fun applyCurrentProfile(id: Long) { applies++ }
    }
    private inner class Store : ProfileStore {
        val receipts = mutableSetOf<String>()
        var receiptReadBarrier: CompletableDeferred<Unit>? = null
        override val profiles = MutableStateFlow(listOf(Profile.remote(1, "https://example.test/sub", "test").copy(
            overridesJson = ProfileProviderOverrides(extension = ProfileExtensionOverrides(rules = listOf(original))).toJsonString())))
        override val currentProfileId = MutableStateFlow<Long?>(1)
        var global = listOf(original)
        var writes = 0; var attempts = 0; var failSave = false; var failAfterWrite = false; var holdAfterCommit = false
        override suspend fun readLocalProfileContent(id: Long) = "proxy-groups: [{name: Main, type: select}]\nrules: ['MATCH,Main']\n"
        override suspend fun globalExtensionRules() = global
        override suspend fun extensionCommitApplied(commitId: String): Boolean {
            receiptReadBarrier?.await()
            return commitId in receipts
        }
        override suspend fun forgetExtensionCommit(commitId: String) { receipts.remove(commitId) }
        override suspend fun compareAndSaveGlobalExtensionRulesForProfile(id: Long, expected: List<ExtensionRule>, rules: List<ExtensionRule>, commitId: String?) {
            check(profiles.value.any { it.id == id }); check(global == expected)
            saveGlobalExtensionRules(rules)
            commitId?.let(receipts::add)
            if (holdAfterCommit) awaitCancellation()
            check(!failAfterWrite)
        }
        override suspend fun compareAndSaveExtension(id: Long, expected: ProfileExtensionValues, next: ProfileExtensionValues, commitId: String?): Profile {
            check(extensionProfile(id)?.let(ProfileExtensionValues::from) == expected)
            return saveExtension(id, next.mode, next.scriptId, next.matchTarget, next.settings).also {
                commitId?.let(receipts::add)
                if (holdAfterCommit) awaitCancellation()
                check(!failAfterWrite)
            }
        }
        override suspend fun saveGlobalExtensionRules(rules: List<ExtensionRule>) {
            attempts++; check(!failSave); global = rules; writes++
        }
        fun externalRules(rules: List<ExtensionRule>) {
            profiles.value = profiles.value.map { it.copy(overridesJson = ProfileProviderOverrides.fromJsonString(it.overridesJson)
                .let { o -> o.copy(extension = o.extension.copy(rules = rules)) }.toJsonString()) }
        }
        override suspend fun saveExtension(id: Long, mode: String, scriptId: Long?, matchTarget: String?, extension: ProfileExtensionOverrides): Profile {
            attempts++; check(!failSave)
            val profile = profiles.value.single { it.id == id }
            val saved = profile.copy(extendType = mode, scriptId = scriptId, matchTarget = matchTarget,
                overridesJson = ProfileProviderOverrides.fromJsonString(profile.overridesJson).copy(extension = extension.copy(configured = true)).toJsonString())
            profiles.value = profiles.value.map { if (it.id == id) saved else it }; writes++
            return saved
        }
        override suspend fun selectProfile(id: Long?) = error("不能改变选择")
        override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile = error("不能改变选择")
        override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
        override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
        override suspend fun addCustomProfile(label: String): Profile = error("unused")
        override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("不能改正文")
        override suspend fun updateRemoteProfile(id: Long): Profile = error("不能下载")
        override suspend fun deleteProfile(id: Long) = error("unused")
        override suspend fun reorder(ids: List<Long>) = error("unused")
    }
}
