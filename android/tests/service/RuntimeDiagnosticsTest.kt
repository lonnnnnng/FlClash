package com.lonnnnnng.flclash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RuntimeDiagnosticsTest {
    @Before
    fun resetHistory() {
        RuntimeDiagnosticHistory.configure(null)
        RuntimeDiagnosticHistory.clear()
    }

    @Test
    fun `history keeps newest events within a fixed bound`() {
        repeat(RuntimeDiagnosticHistory.MAX_EVENTS + 3) { index ->
            RuntimeDiagnosticHistory.record(
                RuntimeDiagnosticKind.DNS_UPDATED,
                "DNS 服务器数量：$index",
            )
        }

        val events = RuntimeDiagnosticHistory.events.value
        assertEquals(RuntimeDiagnosticHistory.MAX_EVENTS, events.size)
        assertTrue(events.first().detail.endsWith("3"))
        assertTrue(events.last().detail.endsWith("202"))
    }

    @Test
    fun `clear removes previous lifecycle evidence`() {
        RuntimeDiagnosticHistory.record(RuntimeDiagnosticKind.CORE_STARTED, "核心启动成功")

        RuntimeDiagnosticHistory.clear()

        assertEquals(emptyList<RuntimeDiagnosticEvent>(), RuntimeDiagnosticHistory.events.value)
    }

    @Test
    fun `history persists the newest bounded events and restores chronological order`() = runTest {
        val store = FakeRuntimeDiagnosticStore()
        RuntimeDiagnosticHistory.configure(store)
        repeat(RuntimeDiagnosticHistory.MAX_EVENTS + 3) { index ->
            RuntimeDiagnosticHistory.record(
                RuntimeDiagnosticKind.DNS_UPDATED,
                "DNS 服务器数量：$index",
            )
        }

        RuntimeDiagnosticHistory.flush()
        assertEquals(RuntimeDiagnosticHistory.MAX_EVENTS, store.events.size)
        assertTrue(store.events.first().detail.endsWith("3"))
        assertTrue(store.events.last().detail.endsWith("202"))

        RuntimeDiagnosticHistory.clear()
        RuntimeDiagnosticHistory.flush()
        store.events = listOf(
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.CORE_STARTED, "旧启动", 2L),
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.NETWORK_LOST, "旧网络", 1L),
        )

        val restored = RuntimeDiagnosticHistory.restore()

        assertEquals(listOf(RuntimeDiagnosticKind.NETWORK_LOST, RuntimeDiagnosticKind.CORE_STARTED), restored.map { it.kind })
        assertEquals(restored, RuntimeDiagnosticHistory.events.value)
    }

    @Test
    fun `flush waits for an append that is still writing`() = runTest {
        val store = BlockingRuntimeDiagnosticStore()
        RuntimeDiagnosticHistory.configure(store)
        RuntimeDiagnosticHistory.record(RuntimeDiagnosticKind.NETWORK_AVAILABLE, "底层网络可用")

        store.appendStarted.await()
        val flushJob = launch { RuntimeDiagnosticHistory.flush() }
        assertFalse(flushJob.isCompleted)

        store.releaseAppend.complete(Unit)
        flushJob.join()

        assertEquals(1, store.events.size)
        assertEquals(RuntimeDiagnosticKind.NETWORK_AVAILABLE, store.events.single().kind)
    }

    private class FakeRuntimeDiagnosticStore(
        var events: List<RuntimeDiagnosticEvent> = emptyList(),
    ) : RuntimeDiagnosticStore {
        override suspend fun loadRecent(limit: Int): List<RuntimeDiagnosticEvent> = events.takeLast(limit)

        override suspend fun append(event: RuntimeDiagnosticEvent, limit: Int) {
            events = (events + event).takeLast(limit)
        }

        override suspend fun clear() {
            events = emptyList()
        }
    }

    private class BlockingRuntimeDiagnosticStore : RuntimeDiagnosticStore {
        val appendStarted = CompletableDeferred<Unit>()
        val releaseAppend = CompletableDeferred<Unit>()
        var events: List<RuntimeDiagnosticEvent> = emptyList()

        override suspend fun loadRecent(limit: Int): List<RuntimeDiagnosticEvent> = events.takeLast(limit)

        override suspend fun append(event: RuntimeDiagnosticEvent, limit: Int) {
            appendStarted.complete(Unit)
            releaseAppend.await()
            events = (events + event).takeLast(limit)
        }

        override suspend fun clear() {
            events = emptyList()
        }
    }
}
