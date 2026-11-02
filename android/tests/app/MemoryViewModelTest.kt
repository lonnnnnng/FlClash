package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreMemoryStats
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun sharedProcessTotalDoesNotAddCoreTwice() {
        val memory = CoreMemoryStats(100, 20, 10, 5, 5)
        assertEquals(60L, memory.appResidentBytes())
        assertEquals(0L, memory.copy(rss = 10).appResidentBytes())
    }

    @Test fun pollRequiresForegroundAndVisibleCardAndResumesImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeMemoryRuntime()
        val model = MemoryViewModel(runtime)
        try {
            model.setForeground(true); runCurrent(); assertEquals(0, runtime.reads)
            model.setVisible(true); runCurrent(); assertEquals(1, runtime.reads)
            advanceTimeBy(2_000); runCurrent(); assertEquals(2, runtime.reads)
            model.setForeground(false); runCurrent()
            advanceTimeBy(10_000); runCurrent(); assertEquals(2, runtime.reads)
            model.setForeground(true); runCurrent(); assertEquals(3, runtime.reads)
            model.setVisible(false); runCurrent()
            advanceTimeBy(10_000); runCurrent(); assertEquals(3, runtime.reads)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun cancelledSlowReadCannotReplaceSnapshotInBackground() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeMemoryRuntime()
        val answer = CompletableDeferred<CoreMemoryStats>()
        runtime.read = { withContext(NonCancellable) { answer.await() } }
        val model = MemoryViewModel(runtime)
        try {
            model.setVisible(true); model.setForeground(true); runCurrent()
            model.setForeground(false); runCurrent()
            answer.complete(memory); runCurrent()
            assertNull(model.state.value.snapshot)
            assertFalse(model.state.value.isLoading)
        } finally { answer.complete(memory); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun cancelledSlowFailureCannotPublishAnErrorInBackground() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeMemoryRuntime()
        val answer = CompletableDeferred<CoreMemoryStats>()
        runtime.read = { withContext(NonCancellable) { answer.await() } }
        val model = MemoryViewModel(runtime)
        try {
            model.setVisible(true); model.setForeground(true); runCurrent()
            model.setForeground(false); runCurrent()
            answer.completeExceptionally(IllegalStateException("延迟失败")); runCurrent()
            assertNull(model.state.value.error)
            assertFalse(model.state.value.isLoading)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun readFailureKeepsLastSampleAndRecoversOnNextPoll() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeMemoryRuntime()
        val model = MemoryViewModel(runtime)
        try {
            model.setVisible(true); model.setForeground(true); runCurrent()
            runtime.read = { error("失败") }
            advanceTimeBy(2_000); runCurrent()
            assertEquals(memory, model.state.value.snapshot)
            assertNotNull(model.state.value.error)
            runtime.read = { memory.copy(rss = 90) }
            advanceTimeBy(2_000); runCurrent()
            assertEquals(90L, model.state.value.snapshot?.rss)
            assertNull(model.state.value.error)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun backgroundCancellationRejectsLateReleaseAndForegroundCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeMemoryRuntime()
        val answer = CompletableDeferred<CoreMemoryStats>()
        val model = MemoryViewModel(runtime)
        try {
            model.setVisible(true); model.setForeground(true); runCurrent()
            runtime.release = { withContext(NonCancellable) { answer.await() } }
            model.release(); runCurrent()
            model.setForeground(false); runCurrent()
            answer.complete(memory.copy(rss = 50)); runCurrent()
            assertEquals(memory, model.state.value.snapshot)
            assertNull(model.state.value.feedback)
            assertFalse(model.state.value.isReleasing)
            model.setForeground(true); runCurrent()
            runtime.release = { memory.copy(rss = 75) }
            model.release(); runCurrent()
            assertEquals(75L, model.state.value.snapshot?.rss)
        } finally { answer.complete(memory); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun releaseDeduplicatesReportsFailureAndPublishesActualReread() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeMemoryRuntime()
        val model = MemoryViewModel(runtime)
        try {
            model.setVisible(true); model.setForeground(true); runCurrent()
            val answer = CompletableDeferred<CoreMemoryStats>()
            runtime.release = { answer.await() }
            model.release(); model.release(); runCurrent()
            assertEquals(1, runtime.releases)
            assertTrue(model.state.value.isReleasing)
            answer.completeExceptionally(IllegalStateException("失败")); runCurrent()
            assertNotNull(model.state.value.error)
            assertEquals(memory, model.state.value.snapshot)
            runtime.release = { memory.copy(rss = 80) }
            model.release(); runCurrent()
            assertEquals(80L, model.state.value.snapshot?.rss)
            assertNotNull(model.state.value.feedback)
            assertFalse(model.state.value.isReleasing)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }
}

private val memory = CoreMemoryStats(100, 20, 10, 5, 5)
private class FakeMemoryRuntime : MemoryRuntime {
    override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    var reads = 0
    var releases = 0
    var read: suspend () -> CoreMemoryStats = { memory }
    var release: suspend () -> CoreMemoryStats = { memory }
    override suspend fun memoryStats(): CoreMemoryStats { reads++; return read() }
    override suspend fun releaseMemory(): CoreMemoryStats { releases++; return release() }
}
