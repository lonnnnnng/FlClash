package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFileImportControllerTest {
    @Test fun previewFreezesBytesAndDoesNotWriteUntilConfirmed() = runTest {
        val source = "rules: []".toByteArray()
        val store = ImportStore()
        val model = controller(store, reader = ProfileFileDocumentReader { ProfileFileDocument("source.yaml", source) })
        model.acceptFile(model.beginPick(), "content://fixture")
        advanceUntilIdle()
        assertTrue(model.state.value.validYaml); assertEquals(0, store.saved.size)
        source.fill(0)
        model.confirm(); model.confirm()
        advanceUntilIdle()
        assertEquals(1, store.saved.size)
        assertTrue(store.saved.single().contentEquals("rules: []".toByteArray()))
        assertNull(model.state.value.fileName)
    }

    @Test fun cancelledOrOlderPickerAndLateReaderCannotRestorePreview() = runTest {
        val gate = CompletableDeferred<Unit>()
        val model = controller(ImportStore(), reader = ProfileFileDocumentReader {
            withContext(NonCancellable) { gate.await(); ProfileFileDocument("late.yaml", "rules: []".toByteArray()) }
        })
        val first = model.beginPick()
        assertNull(model.beginPick())
        model.acceptFile(first, null)
        val next = model.beginPick()
        model.acceptFile(first, "content://stale")
        assertTrue(model.state.value.picking)
        model.acceptFile(next, "content://late"); runCurrent()
        model.dismiss(); gate.complete(Unit); advanceUntilIdle()
        assertEquals(ProfileFileImportUiState(), model.state.value)
    }

    @Test fun invalidAndOversizedFilesNeverReachStore() = runTest {
        val store = ImportStore()
        var bytes = "rules: [".toByteArray()
        val model = controller(store, reader = ProfileFileDocumentReader { ProfileFileDocument("bad.yaml", bytes) })
        model.acceptFile(model.beginPick(), "content://fixture"); advanceUntilIdle()
        assertFalse(model.state.value.validYaml); model.confirm(); advanceUntilIdle()
        assertTrue(store.saved.isEmpty())
        bytes = ByteArray(8 * 1024 * 1024 + 1)
        model.acceptFile(model.beginPick(), "content://large"); advanceUntilIdle()
        assertNotNull(model.state.value.error); assertNull(model.state.value.fileName)
        assertTrue(store.saved.isEmpty())
    }

    @Test fun encryptedWrongKeyCanRetrySameSnapshotWithoutReadingAgain() = runTest {
        val store = ImportStore().apply { requiredKey = "correct" }
        var reads = 0
        val model = controller(store, reader = ProfileFileDocumentReader {
            reads++; ProfileFileDocument("source.age", "-----BEGIN AGE ENCRYPTED FILE-----\nfixture".toByteArray())
        })
        model.acceptFile(model.beginPick(), "content://fixture"); advanceUntilIdle()
        model.confirm(); assertTrue(model.state.value.needsKey)
        model.confirm("wrong"); advanceUntilIdle()
        assertTrue(model.state.value.needsKey); assertNotNull(model.state.value.error); assertTrue(store.saved.isEmpty())
        model.confirm(" correct "); advanceUntilIdle()
        assertEquals(1, reads); assertEquals(listOf("correct"), store.keys)
        assertNull(model.state.value.fileName)
    }

    @Test fun backgroundRetainsOnlySystemPickerAndClearsKeyOrReadingSessions() = runTest {
        val store = ImportStore()
        val model = controller(store, reader = ProfileFileDocumentReader { ProfileFileDocument("encrypted.age", "-----BEGIN AGE ENCRYPTED FILE-----\nfixture".toByteArray()) })
        val token = model.beginPick(); model.onBackground(); assertTrue(model.state.value.picking)
        model.acceptFile(token, "content://fixture"); advanceUntilIdle(); model.confirm()
        assertTrue(model.state.value.needsKey); model.onBackground()
        assertEquals(ProfileFileImportUiState(), model.state.value)
        model.confirm("unused"); advanceUntilIdle(); assertTrue(store.saved.isEmpty())
    }

    @Test fun coreStartingDuringSaveRejectsWriteAndAllowsLaterRetry() = runTest {
        val runtime = object : CoreRuntimeStatusProvider { override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED) }
        val gate = CompletableDeferred<Unit>()
        val store = ImportStore().apply { beforeSave = { gate.await() } }
        val model = controller(store, runtime, ProfileFileDocumentReader { ProfileFileDocument("source.yaml", "rules: []".toByteArray()) })
        model.acceptFile(model.beginPick(), "content://fixture"); advanceUntilIdle()
        model.confirm(); runCurrent()
        runtime.state.value = CoreRuntimeStatus.STARTING; gate.complete(Unit); advanceUntilIdle()
        assertTrue(store.saved.isEmpty()); assertNotNull(model.state.value.error)
        runtime.state.value = CoreRuntimeStatus.STOPPED
        model.confirm(); advanceUntilIdle(); assertEquals(1, store.saved.size)
    }

    private fun TestScope.controller(store: ImportStore, runtime: CoreRuntimeStatusProvider = object : CoreRuntimeStatusProvider {
        override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    }, reader: ProfileFileDocumentReader) = ProfileFileImportController(store, runtime, reader, YamlProfileConfigValidator(), this, StandardTestDispatcher(testScheduler))
}

private class ImportStore : ProfileStore {
    override val profiles = MutableStateFlow(emptyList<Profile>())
    override val currentProfileId = MutableStateFlow<Long?>(null)
    val saved = mutableListOf<ByteArray>()
    val keys = mutableListOf<String>()
    var requiredKey: String? = null
    var beforeSave: suspend () -> Unit = {}
    override suspend fun importFileProfile(label: String, bytes: ByteArray, ageSecretKey: String, canWrite: () -> Boolean): Profile {
        beforeSave(); check(canWrite()); requiredKey?.let { check(it == ageSecretKey) }
        keys += ageSecretKey; saved += bytes.copyOf(); return Profile.custom(1, label).copy(type = ProfileType.FILE)
    }
    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile = error("unused")
    override suspend fun selectProfile(id: Long?) = Unit
    override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile = error("unused")
    override suspend fun addCustomProfile(label: String): Profile = error("unused")
    override suspend fun readLocalProfileContent(id: Long): String = error("unused")
    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile = error("unused")
    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile = error("unused")
    override suspend fun updateRemoteProfile(id: Long): Profile = error("unused")
    override suspend fun deleteProfile(id: Long) = Unit
    override suspend fun reorder(ids: List<Long>) = Unit
}
