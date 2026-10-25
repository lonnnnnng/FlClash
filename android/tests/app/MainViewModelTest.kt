package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreDnsQuery
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

    @Test
    fun `dns history keeps the newest queries`() {
        val state = (1..1_005).fold(DnsUiState()) { current, index ->
            current.apply(CoreEvent.Dns(dnsQuery("example-$index.com")))
        }

        assertEquals(1_000, state.queries.size)
        assertEquals("example-6.com", state.queries.first().domain)
        assertEquals("example-1005.com", state.queries.last().domain)
    }

    @Test
    fun `log state stores log events and ignores other event types`() {
        val log = CoreEvent.Log(
            level = "error",
            payload = "连接失败",
            timestampMillis = 1L,
        )

        val state = LogsUiState().apply(log).apply(CoreEvent.Request(connection("request-1")))

        assertEquals(listOf(log), state.entries)
    }

    @Test
    fun `request filter searches host process chain and rule`() {
        val first = connection("request-1")
        val second = connection("request-2").copy(
            metadata = first.metadata.copy(
                host = "api.example.net",
                process = "com.example.browser",
            ),
            chains = listOf("Proxy-A", "DIRECT"),
            rule = "DOMAIN-SUFFIX",
        )

        assertEquals(listOf(second), filterRequests(listOf(first, second), "browser"))
        assertEquals(listOf(second), filterRequests(listOf(first, second), "proxy-a"))
        assertEquals(listOf(second), filterRequests(listOf(first, second), "domain-suffix"))
    }

    @Test
    fun `dns filter searches domain upstream answers and error`() {
        val first = dnsQuery("example.com")
        val second = first.copy(
            domain = "blocked.example.net",
            upstream = "https://dns.example.net",
            answers = emptyList(),
            error = "timeout",
        )

        assertEquals(listOf(second), filterDnsQueries(listOf(first, second), "dns.example.net"))
        assertEquals(listOf(second), filterDnsQueries(listOf(first, second), "timeout"))
        assertEquals(listOf(first, second), filterDnsQueries(listOf(first, second), ""))
    }

    @Test
    fun `log filter combines level and case insensitive text`() {
        val error = CoreEvent.Log("error", "连接失败", 1L)
        val info = CoreEvent.Log("info", "代理已连接", 2L)

        assertEquals(listOf(error), filterLogs(listOf(error, info), "失败", "ERROR"))
        assertEquals(listOf(info), filterLogs(listOf(error, info), "代理", null))
        assertEquals(emptyList<CoreEvent.Log>(), filterLogs(listOf(error, info), "代理", "error"))
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

    private fun dnsQuery(domain: String) = CoreDnsQuery(
        domain = domain,
        type = "A",
        initiator = "com.example.app",
        upstream = "198.18.0.2",
        cached = false,
        answers = listOf("203.0.113.7"),
        rcode = "NOERROR",
        error = "",
        delay = 12L,
        time = "2026-10-25T11:00:00Z",
    )
}
