package com.lonnnnnng.flclash.ui

import com.google.gson.Gson
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：每次冷恢复创建新控制器与作用域，只共享磁盘字节和正式数据，避免把同一VM重连误记为进程恢复。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileManagementControllerTest {
    @Test fun rawLabelAndWindowTreeSurviveNewControllerWithoutWritingFormalData() = runTest {
        val repository = Store(); val disk = Disk(); val first = model(repository, disk)
        runCurrent(); first.controller.open("rename", repository.profiles.value.first())
        val session = first.controller.state.value.draft!!.session
        first.controller.label(session, "  新名称  ")
        val tree = EditorSaveableState(mapOf("selection" to listOf(EditorSavedValue("int", "3"))))
        first.controller.ui(session, tree); runCurrent(); first.scope.cancel(); runCurrent()
        val second = model(repository, disk); runCurrent()
        assertEquals("  新名称  ", second.controller.state.value.draft!!.label)
        assertEquals(tree, second.controller.state.value.draft!!.uiDraft)
        assertEquals(0, repository.writes); assertEquals("one", repository.profiles.value.first().label)
        second.controller.close(); runCurrent(); assertNull(disk.bytes)
        second.controller.ui(session, tree); runCurrent(); assertNull(disk.bytes)
        second.scope.cancel()
    }

    @Test fun allIntentAndCommittedWindowsRecoverWithoutAutomaticReplay() = runTest {
        for (kind in listOf("rename", "delete", "order")) for (window in listOf(EditorCommitWindow.MANAGEMENT_INTENT, EditorCommitWindow.MANAGEMENT_COMMITTED)) {
            val repository = Store(); val disk = Disk(); val reached = CompletableDeferred<Unit>()
            val first = model(repository, disk) { if (it == window) { reached.complete(Unit); awaitCancellation() } }
            runCurrent(); prepare(first.controller, repository, kind); first.controller.save(); runCurrent()
            assertTrue(reached.isCompleted)
            val expected = if (window == EditorCommitWindow.MANAGEMENT_INTENT) 0 else 1
            assertEquals(expected, repository.writes)
            first.scope.cancel(); runCurrent()
            val second = model(repository, disk); runCurrent()
            assertEquals(expected, repository.writes)
            assertEquals(expected == 1, second.controller.state.value.draft!!.committed)
            if (expected == 1) {
                second.controller.save(); runCurrent(); assertEquals(1, repository.writes)
                second.scope.cancel(); runCurrent()
                val third = model(repository, disk); runCurrent()
                assertTrue(third.controller.state.value.draft!!.committed)
                third.controller.close(); runCurrent(); assertNull(disk.bytes); third.scope.cancel()
            } else {
                second.controller.save(); second.controller.save(); runCurrent()
                assertEquals(1, repository.writes); assertNull(second.controller.state.value.draft); assertNull(disk.bytes)
                second.scope.cancel()
            }
        }
    }

    @Test fun failureRetainsEachWindowAndExplicitRetryCommitsOnce() = runTest {
        for (kind in listOf("rename", "delete", "order")) {
            val repository = Store(); val disk = Disk(); val model = model(repository, disk)
            runCurrent(); prepare(model.controller, repository, kind)
            repository.fail = true; model.controller.save(); model.controller.save(); runCurrent()
            assertNotNull(model.controller.state.value.draft); assertNotNull(model.controller.state.value.error)
            assertFalse(model.controller.state.value.busy); assertEquals(0, repository.writes)
            repository.fail = false; model.controller.save(); model.controller.save(); runCurrent()
            assertEquals(1, repository.writes); assertNull(model.controller.state.value.draft); model.scope.cancel()
        }
    }

    @Test fun externallyDeletedOrRenamedTargetKeepsDraftAndNeverRecreatesRow() = runTest {
        for (remove in listOf(true, false)) {
            val repository = Store(); val disk = Disk(); val first = model(repository, disk)
            runCurrent(); prepare(first.controller, repository, "rename"); runCurrent(); first.scope.cancel(); runCurrent()
            repository.profiles.value = if (remove) repository.profiles.value.drop(1) else repository.profiles.value.map { it.copy(label = "external") }
            val second = model(repository, disk); runCurrent()
            assertTrue(second.controller.state.value.blocked); assertEquals(" changed ", second.controller.state.value.draft!!.label)
            second.controller.save(); runCurrent(); assertEquals(0, repository.writes)
            second.controller.close(); runCurrent(); assertNull(disk.bytes); second.scope.cancel()
        }
    }

    @Test fun deletionWithoutConfirmationRemainsBlockedAfterTargetDisappears() = runTest {
        val repository = Store(); val disk = Disk(); val model = model(repository, disk)
        runCurrent(); prepare(model.controller, repository, "delete"); runCurrent()
        repository.profiles.value = repository.profiles.value.drop(1); runCurrent()
        assertTrue(model.controller.state.value.blocked); assertFalse(model.controller.state.value.draft!!.committed)
        model.controller.save(); runCurrent(); assertEquals(0, repository.writes); model.scope.cancel()
    }

    @Test fun orderReconcilesAddedAndRemovedMembersButRejectsExternalReorder() = runTest {
        val repository = Store(); val disk = Disk(); val first = model(repository, disk)
        runCurrent(); prepare(first.controller, repository, "order"); runCurrent(); first.scope.cancel(); runCurrent()
        repository.profiles.value = repository.profiles.value.filterNot { it.id == 3L } + Profile.custom(4, "four")
        val second = model(repository, disk); runCurrent()
        assertEquals(listOf(2L, 1L, 4L), second.controller.state.value.draft!!.order)
        assertFalse(second.controller.state.value.blocked)
        // 作者 long：只移动新增项也属于外部已确认排序，不能仅比较旧成员交集而静默把它挪回末尾。
        repository.profiles.value = listOf(repository.profiles.value.last()) + repository.profiles.value.dropLast(1); runCurrent()
        assertTrue(second.controller.state.value.blocked)
        second.controller.save(); runCurrent(); assertEquals(0, repository.writes); second.scope.cancel()
    }

    @Test fun failedReadAndFailedIntentWritePreserveCipherAndPreventCommit() = runTest {
        val repository = Store(); val disk = Disk(); val first = model(repository, disk)
        runCurrent(); prepare(first.controller, repository, "rename"); runCurrent(); first.scope.cancel(); runCurrent()
        val original = disk.bytes!!.clone(); disk.failRead = true
        val second = model(repository, disk); runCurrent()
        assertTrue(second.controller.state.value.recoveryFailed)
        second.controller.open("delete", repository.profiles.value.first()); second.controller.close(); runCurrent()
        assertTrue(original.contentEquals(disk.bytes))
        disk.failRead = false; second.controller.retryRecovery(); runCurrent()
        assertEquals(" changed ", second.controller.state.value.draft!!.label)
        disk.failWrite = true; second.controller.save(); runCurrent(); assertEquals(0, repository.writes)
        second.controller.close(); runCurrent(); assertNotNull(second.controller.state.value.draft)
        disk.failWrite = false; second.controller.close(); runCurrent(); assertNull(disk.bytes); second.scope.cancel()
    }

    @Test fun newlyObservedTailOrderSurvivesColdStartAndRejectsLaterTailSwap() = runTest {
        val repository = Store(); val disk = Disk(); val first = model(repository, disk)
        runCurrent(); prepare(first.controller, repository, "order"); runCurrent()
        repository.profiles.value = repository.profiles.value + Profile.custom(4, "four") + Profile.custom(5, "five")
        runCurrent(); assertEquals(listOf(1L, 2L, 3L, 4L, 5L), disk.snapshot().originalOrder)
        first.scope.cancel(); runCurrent()
        val values = repository.profiles.value
        repository.profiles.value = values.take(3) + values.takeLast(2).reversed()
        val second = model(repository, disk); runCurrent()
        assertTrue(second.controller.state.value.blocked)
        second.controller.save(); runCurrent(); assertEquals(0, repository.writes); second.scope.cancel()
    }

    @Test fun commitBeforeAcknowledgementIsRecognizedFromFormalData() = runTest {
        for (kind in listOf("rename", "delete", "order")) {
            val repository = Store(); val disk = Disk(); val first = model(repository, disk)
            runCurrent(); prepare(first.controller, repository, kind)
            repository.afterWrite = { awaitCancellation() }
            first.controller.save(); runCurrent(); assertEquals(1, repository.writes)
            assertFalse(disk.snapshot().committed)
            first.scope.cancel(); runCurrent(); repository.afterWrite = {}
            val second = model(repository, disk); runCurrent()
            assertTrue(second.controller.state.value.draft!!.committed)
            second.controller.save(); runCurrent(); assertEquals(1, repository.writes); second.scope.cancel()
        }
    }

    @Test fun incompleteFileCleanupNeedsExplicitRetryAndNeverRepeatsRowDeletion() = runTest {
        val repository = Store(); val disk = Disk(); val first = model(repository, disk)
        runCurrent(); prepare(first.controller, repository, "delete")
        repository.orphan = true
        first.controller.save(); runCurrent(); assertEquals(1, repository.writes)
        assertFalse(first.controller.state.value.draft!!.committed)
        first.controller.close(); runCurrent(); assertNotNull(first.controller.state.value.draft); assertNotNull(disk.bytes)
        first.scope.cancel(); runCurrent()
        val second = model(repository, disk); runCurrent()
        assertEquals(1, repository.writes); assertFalse(second.controller.state.value.blocked)
        second.controller.save(); runCurrent(); assertFalse(repository.orphan)
        assertEquals(1, repository.writes); assertEquals(1, repository.cleanups)
        assertNull(second.controller.state.value.draft); second.scope.cancel()
    }

    @Test fun suspendedReconcileCannotOverwriteNewInputOrSaveClaim() = runTest {
        val repository = Store(); val disk = Disk(); val model = model(repository, disk)
        runCurrent(); prepare(model.controller, repository, "rename"); runCurrent()
        val session = model.controller.state.value.draft!!.session
        val gate = CompletableDeferred<Unit>(); repository.readGate = gate
        repository.profiles.value = repository.profiles.value + Profile.custom(4, "four"); runCurrent()
        model.controller.label(session, "new while reading")
        model.controller.save(); assertTrue(model.controller.state.value.busy)
        gate.complete(Unit); runCurrent()
        assertEquals("new while reading", repository.profiles.value.first().label)
        assertEquals(1, repository.writes); assertNull(model.controller.state.value.draft); model.scope.cancel()
    }

    @Test fun longExistingNameCanBeRenamedOrDeletedWithoutCrashingEntry() = runTest {
        for (kind in listOf("rename", "delete")) {
            val repository = Store(); repository.profiles.value = repository.profiles.value.map { it.copy(label = "x".repeat(100)) }
            val disk = Disk(); val model = model(repository, disk); runCurrent()
            model.controller.open(kind, repository.profiles.value.first()); runCurrent()
            assertEquals(100, model.controller.state.value.draft!!.originalLabel.length)
            model.controller.save(); runCurrent(); assertEquals(1, repository.writes); model.scope.cancel()
        }
    }

    private fun prepare(controller: ProfileManagementController, repository: Store, kind: String) {
        controller.open(kind, repository.profiles.value.first())
        val session = controller.state.value.draft!!.session
        if (kind == "rename") controller.label(session, " changed ")
        if (kind == "order") controller.order(session, listOf(2, 1, 3))
    }
    private data class Model(val controller: ProfileManagementController, val scope: CoroutineScope)
    private fun TestScope.model(repository: Store, disk: Disk, checkpoint: suspend (EditorCommitWindow) -> Unit = {}): Model {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val runtime = object : CoreRuntimeStatusProvider { override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED) }
        return Model(ProfileManagementController(repository, runtime, disk, scope, checkpoint), scope)
    }
    private class Disk : ProfileDraftStore {
        var bytes: ByteArray? = null; var failRead = false; var failWrite = false
        override suspend fun read(): ByteArray? { check(!failRead); return bytes?.clone() }
        override suspend fun write(content: ByteArray?) { check(!failWrite); bytes = content?.clone() }
        fun snapshot(): ProfileManagementDraft = Gson().fromJson(bytes!!.decodeToString(), ProfileManagementDraft::class.java)
    }
    private class Store : ProfileStore {
        override val profiles = MutableStateFlow(listOf(Profile.custom(1, "one"), Profile.custom(2, "two"), Profile.custom(3, "three")))
        override val currentProfileId = MutableStateFlow<Long?>(3)
        var writes = 0; var cleanups = 0; var fail = false; var orphan = false
        var afterWrite: suspend () -> Unit = {}
        var readGate: CompletableDeferred<Unit>? = null
        override suspend fun extensionProfile(id: Long): Profile? {
            val result = profiles.value.find { it.id == id }; readGate?.await(); return result
        }
        override suspend fun updateProfileLabel(id: Long, label: String): Profile {
            check(!fail); val next = profiles.value.single { it.id == id }.copy(label = label)
            profiles.value = profiles.value.map { if (it.id == id) next else it }; writes++; afterWrite(); return next
        }
        override suspend fun reorder(ids: List<Long>) {
            check(!fail); profiles.value = ids.mapNotNull { id -> profiles.value.find { it.id == id } }; writes++; afterWrite()
        }
        override suspend fun deleteProfile(id: Long) {
            check(!fail)
            if (profiles.value.any { it.id == id }) { profiles.value = profiles.value.filterNot { it.id == id }; writes++; afterWrite() }
            else { orphan = false; cleanups++ }
        }
        override suspend fun profileDeletionComplete(id: Long) = profiles.value.none { it.id == id } && !orphan
        override suspend fun selectProfile(id: Long?) { currentProfileId.value = id }
        override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
        override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
        override suspend fun addCustomProfile(label: String): Profile = error("unused")
        override suspend fun readLocalProfileContent(id: Long): String = error("must not read source")
        override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("must not write source")
        override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile = error("unused")
        override suspend fun updateRemoteProfile(id: Long): Profile = error("must not download")
    }
}
