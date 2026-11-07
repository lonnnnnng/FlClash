package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreExternalProvider
import com.lonnnnnng.flclash.core.CoreProviderDocument
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：每次恢复创建新VM，保留真实存储边界而不靠重新打开资源补回草稿；断言不输出正文。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderDocumentRecoveryTest {
    @After fun resetMain() = Dispatchers.resetMain()
    private fun TestScope.model(runtime: Runtime, store: Store): ProfileStatisticsViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return ProfileStatisticsViewModel(runtime, ProfileFileDocumentReader { error("unused") }, null,
            NoopProfileDraftStore, store, dispatcher)
    }
    private fun TestScope.open(model: ProfileStatisticsViewModel) {
        model.setForeground(true); model.show(1); runCurrent()
        model.openProvider("resource", edit = true); runCurrent()
        assertNotNull(model.document.value?.draft)
    }

    @Test fun coldDraftKeepsFailureSelectionAndOriginalBaselineWithoutWrites() = runTest {
        val runtime = Runtime(); val store = Store(); val first = model(runtime, store)
        open(first); first.changeProviderContent(CHANGED)
        runtime.onSave = { error("private-runtime-detail") }
        first.saveProvider(); runCurrent()
        val saved = first.document.value!!
        val ui = EditorSaveableState(mapOf("selection" to listOf(EditorSavedValue.capture(intArrayOf(3, 7))),
            "discard" to listOf(EditorSavedValue.capture(true))))
        first.captureProviderUi(saved.draft!!.session, ui); runCurrent()
        first.viewModelScope.cancel(); runCurrent()
        val second = model(runtime, store)
        try {
            second.show(null); runCurrent()
            assertEquals(1L, second.restoredProviderProfileId.value)
            assertTrue(second.document.value?.content == CHANGED)
            second.resumeProviderDocument(1); runCurrent()
            assertTrue(second.document.value!!.dirty)
            assertEquals(ui, second.document.value!!.draft!!.uiDraft)
            assertEquals(saved.error, second.document.value?.error)
            assertFalse(second.document.value.toString().contains(CHANGED))
            assertEquals(1, runtime.saves)
            assertEquals("base", second.document.value?.document?.document?.digest)
        } finally { second.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun interruptedSaveResumesAsExplicitRetryAndCommittedSaveNeverReplays() = runTest {
        for (committed in listOf(false, true)) {
            val runtime = Runtime(); val store = Store(); val first = model(runtime, store)
            open(first); first.changeProviderContent(CHANGED)
            runtime.onSave = { if (committed) { runtime.content = it; runtime.digest = "next" }; awaitCancellation() }
            first.saveProvider(); runCurrent()
            assertTrue(store.snapshot().pending)
            first.viewModelScope.cancel(); runCurrent()
            val second = model(runtime, store)
            try {
                second.setForeground(true); runCurrent(); second.resumeProviderDocument(1); runCurrent()
                assertEquals(1, runtime.saves)
                assertEquals(committed, second.document.value?.committed)
                assertFalse(second.document.value!!.saving)
                if (committed) {
                    second.saveProvider(); runCurrent(); assertEquals(1, runtime.saves)
                    second.viewModelScope.cancel(); runCurrent()
                    val third = model(runtime, store)
                    runCurrent(); third.resumeProviderDocument(1); runCurrent()
                    assertTrue(third.document.value!!.committed); assertEquals(1, runtime.saves)
                    third.closeProvider(); runCurrent(); assertNull(store.bytes)
                    third.viewModelScope.cancel(); runCurrent()
                } else {
                    runtime.onSave = {}; second.saveProvider(); second.saveProvider(); runCurrent()
                    assertEquals(2, runtime.saves); assertNull(second.document.value); assertNull(store.bytes)
                }
            } finally { second.viewModelScope.cancel(); runCurrent() }
        }
    }

    @Test fun changedInputChangedFileAndRemovedTargetRetainVisibleDraftWithoutRebasing() = runTest {
        for (change in listOf("input", "file", "missing")) {
            val runtime = Runtime(); val store = Store(); val first = model(runtime, store)
            open(first); first.changeProviderContent(CHANGED); runCurrent(); first.viewModelScope.cancel(); runCurrent()
            when (change) {
                "input" -> runtime.key = "new-input"
                "file" -> { runtime.content = "external"; runtime.digest = "external" }
                else -> runtime.missing = true
            }
            val second = model(runtime, store)
            try {
                second.setForeground(true); runCurrent(); second.resumeProviderDocument(1); runCurrent()
                assertTrue(second.document.value!!.recoveryBlocked)
                assertTrue(second.document.value!!.dirty)
                assertTrue(second.document.value!!.content == CHANGED)
                assertNull(second.document.value?.document)
                assertEquals("base", store.snapshot().digest)
                second.saveProvider(); runCurrent(); assertEquals(0, runtime.saves)
                second.closeProvider(); runCurrent(); assertNull(store.bytes)
            } finally { second.viewModelScope.cancel(); runCurrent() }
        }
    }

    @Test fun switchedSelectionCannotLoadOldTargetOrEraseDraft() = runTest {
        val runtime = Runtime(); val store = Store(); val first = model(runtime, store)
        open(first); first.changeProviderContent(CHANGED); runCurrent(); first.viewModelScope.cancel(); runCurrent()
        val second = model(runtime, store)
        try {
            runCurrent(); val reads = runtime.reads
            second.resumeProviderDocument(2); second.retryProviderDocument(); runCurrent()
            assertEquals(reads, runtime.reads); assertTrue(second.document.value!!.dirty)
            second.resumeProviderDocument(1); runCurrent(); assertNotNull(second.document.value?.document)
            second.reconcileProviderSelection(null); runCurrent()
            assertTrue(second.document.value!!.recoveryBlocked); assertNull(second.document.value?.document)
            assertTrue(store.snapshot().content == CHANGED); assertEquals(0, runtime.saves)
        } finally { second.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun failedEncryptedReadKeepsBytesAndRetriesWithoutOpeningAnotherResource() = runTest {
        val runtime = Runtime(); val store = Store(); val first = model(runtime, store)
        open(first); first.changeProviderContent(CHANGED); runCurrent(); first.viewModelScope.cancel(); runCurrent()
        val original = store.bytes!!.clone(); store.failRead = true
        val second = model(runtime, store)
        try {
            second.setForeground(true); second.show(1); runCurrent(); second.openProvider("resource", edit = true); runCurrent()
            assertTrue(original.contentEquals(store.bytes)); assertTrue(second.document.value!!.recoveryBlocked)
            store.failRead = false; second.retryProviderDocument(); runCurrent(); second.resumeProviderDocument(1); runCurrent()
            assertTrue(second.document.value!!.content == CHANGED); assertNotNull(second.document.value?.document)
        } finally { second.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun failedIntentWriteStopsCommitAndFailedClearPreservesExplicitExitRetry() = runTest {
        val runtime = Runtime(); val store = Store(); val model = model(runtime, store)
        try {
            open(model); model.changeProviderContent(CHANGED); runCurrent(); store.failWrite = true
            model.saveProvider(); runCurrent(); assertEquals(0, runtime.saves)
            assertTrue(model.document.value!!.dirty); assertFalse(model.document.value!!.saving)
            model.closeProvider(); runCurrent(); assertNotNull(model.document.value); assertNotNull(store.bytes)
            store.failWrite = false; model.closeProvider(); runCurrent(); assertNull(model.document.value); assertNull(store.bytes)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun initialNullAndLateUiCallbacksCannotEraseOrResurrectDraft() = runTest {
        val runtime = Runtime(); val store = Store(); val first = model(runtime, store)
        open(first); first.changeProviderContent(CHANGED); runCurrent(); val session = first.document.value!!.draft!!.session
        first.viewModelScope.cancel(); runCurrent()
        val gate = CompletableDeferred<Unit>(); store.readGate = gate
        val second = model(runtime, store)
        try {
            second.show(null); second.show(1); runCurrent(); gate.complete(Unit); runCurrent()
            assertTrue(second.document.value!!.content == CHANGED)
            second.closeProvider(); runCurrent()
            second.captureProviderUi(session, EditorSaveableState()); runCurrent()
            assertNull(second.document.value); assertNull(store.bytes)
        } finally { gate.complete(Unit); second.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun lateReadRetryCannotResurrectExplicitlyDiscardedCiphertext() = runTest {
        val runtime = Runtime(); val store = Store(); val first = model(runtime, store)
        open(first); first.changeProviderContent(CHANGED); runCurrent(); first.viewModelScope.cancel(); runCurrent()
        store.failRead = true
        val second = model(runtime, store)
        val gate = CompletableDeferred<Unit>()
        try {
            runCurrent(); assertTrue(second.document.value!!.recoveryBlocked)
            store.failRead = false; store.readGate = gate; store.nonCancellableRead = true
            second.retryProviderDocument(); runCurrent(); second.closeProvider(); runCurrent()
            assertNull(second.document.value); assertNull(store.bytes)
            gate.complete(Unit); runCurrent()
            assertNull(second.document.value); assertNull(second.restoredProviderProfileId.value); assertNull(store.bytes)
        } finally { gate.complete(Unit); second.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun explicitSaveRefreshesBindingButStillRejectsExternallyChangedFile() = runTest {
        for (conflict in listOf(false, true)) {
            val runtime = Runtime(); val store = Store(); val model = model(runtime, store)
            try {
                open(model); model.changeProviderContent(CHANGED); runCurrent()
                runtime.rejectOldBinding = true
                runtime.lastRead = null
                if (conflict) { runtime.content = "external"; runtime.digest = "external" }
                model.saveProvider(); runCurrent()
                if (conflict) {
                    assertEquals(0, runtime.saves); assertTrue(model.document.value!!.dirty)
                    assertEquals("base", store.snapshot().digest)
                } else { assertEquals(1, runtime.saves); assertNull(model.document.value) }
            } finally { model.viewModelScope.cancel(); runCurrent() }
        }
    }

    private class Store : ProfileDraftStore {
        var bytes: ByteArray? = null
        var failRead = false
        var failWrite = false
        var readGate: CompletableDeferred<Unit>? = null
        var nonCancellableRead = false
        override suspend fun read(): ByteArray? {
            val snapshot = bytes
            if (nonCancellableRead) withContext(NonCancellable) { readGate?.await() } else readGate?.await()
            check(!failRead); return snapshot
        }
        override suspend fun write(content: ByteArray?) { check(!failWrite); bytes = content?.clone() }
        fun snapshot(): ProviderDocumentDraft = com.google.gson.Gson().fromJson(checkNotNull(bytes).decodeToString(), ProviderDocumentDraft::class.java)
    }
    private class Runtime : ProfileStatisticsRuntime {
        override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
        override val profileStatisticsRevision = MutableStateFlow(0L)
        var content = "payload: []"
        var digest = "base"
        var key = "input"
        var missing = false
        var reads = 0
        var saves = 0
        var rejectOldBinding = false
        var lastRead: ProfileProviderDocument? = null
        var onSave: suspend (String) -> Unit = {}
        override suspend fun profileStatistics(profileId: Long) = ProfileStatistics(profileId, 0, 0, 0, 1)
        override suspend fun profileProviders(profileId: Long) = listOf(CoreExternalProvider("resource", "Rule", "File", 0, null))
        override suspend fun readProfileProvider(profileId: Long, name: String): ProfileProviderDocument {
            reads++; check(!missing)
            return ProfileProviderDocument(profileId, CoreProviderDocument(name, "text", content, digest), recoveryKey = key).also { lastRead = it }
        }
        override suspend fun saveProfileProvider(document: ProfileProviderDocument, content: String) {
            check(!rejectOldBinding || document === lastRead)
            saves++; check(document.document.digest == digest); onSave(content); this.content = content; digest = "next"
        }
    }
    private companion object { const val CHANGED = "payload: [example.invalid]\n# draft-only" }
}
