package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreTrafficUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelTest {
    @Test
    fun `request traffic updates the matching history item`() {
        val request = connection("request-1")
        val initial = RequestsUiState().apply(CoreEvent.Request(request))

        val updated = initial.apply(
            CoreEvent.RequestTraffic(
                listOf(CoreTrafficUpdate(id = "request-1", upload = 40, download = 80)),
            ),
        )

        assertEquals(1, updated.requests.size)
        assertEquals(40, updated.requests.single().upload)
        assertEquals(80, updated.requests.single().download)
    }

    @Test
    fun `request history keeps only the newest entries`() {
        val state = (1..2_005).fold(RequestsUiState()) { current, index ->
            current.apply(CoreEvent.Request(connection("request-$index")))
        }

        assertEquals(2_000, state.requests.size)
        assertEquals("request-6", state.requests.first().id)
        assertEquals("request-2005", state.requests.last().id)
    }

    private fun connection(id: String) = CoreConnection(
        id = id,
        upload = 0,
        download = 0,
        start = "2026-10-25T11:00:00Z",
        metadata = CoreConnectionMetadata(
            network = "tcp",
            sourceIp = "",
            sourcePort = "",
            destinationIp = "203.0.113.7",
            destinationPort = "443",
            host = "example.com",
            process = "com.example.app",
            processPath = "",
            remoteDestination = "",
            uid = 0,
            specialProxy = "",
            specialRules = "",
        ),
        chains = listOf("DIRECT"),
        rule = "MATCH",
        rulePayload = "",
    )
}
