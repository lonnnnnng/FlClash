package com.lonnnnnng.flclash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeDiagnosticsTest {
    @Before
    fun resetHistory() {
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
}
