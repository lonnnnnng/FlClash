package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.CoreTrafficUpdate
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {
    @Test
    fun `runtime observation only polls while the page is visible and core is running`() {
        assertTrue(shouldPollRuntimeObservation(CoreRuntimeStatus.RUNNING, true))
        assertFalse(shouldPollRuntimeObservation(CoreRuntimeStatus.RUNNING, false))
        assertFalse(shouldPollRuntimeObservation(CoreRuntimeStatus.STOPPED, true))
        assertFalse(shouldPollRuntimeObservation(CoreRuntimeStatus.FAILED, true))
    }

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

    @Test
    fun `log export redacts credentials and keeps diagnostic columns`() {
        val log = CoreEvent.Log(
            level = "warn",
            payload = "request https://example.test/path?token=secret-value&x=1 {\"password\":\"p@ss\"}",
            timestampMillis = 42L,
        )

        val exported = formatLogsForExport(listOf(log))

        assertEquals(true, exported.startsWith("KClash core logs\n"))
        assertEquals(true, exported.contains("42\twarn\t"))
        assertEquals(true, exported.contains("[REDACTED_SECRET]"))
        assertEquals(false, exported.contains("secret-value"))
        assertEquals(false, exported.contains("p@ss"))
        assertEquals(true, exported.contains("x=1"))
    }

    @Test
    fun `diagnostic session captures events only while running`() {
        val running = DiagnosticSessionUiState(
            status = DiagnosticSessionStatus.RUNNING,
            sessionId = 7L,
        )
        val captured = running
            .withEvent(CoreEvent.Request(connection("request-1")))
            .withEvent(CoreEvent.Dns(dnsQuery("probe.example.com")))
            .withEvent(CoreEvent.Log("info", "probe complete", 3L))
        val finished = captured.copy(status = DiagnosticSessionStatus.PASS)

        assertEquals(1, captured.requests.size)
        assertEquals(1, captured.dns.size)
        assertEquals(1, captured.logs.size)
        assertEquals(finished, finished.withEvent(CoreEvent.Log("info", "late", 4L)))
    }

    @Test
    fun `diagnostic session captures runtime history only while running`() {
        val event = RuntimeDiagnosticEvent(
            kind = RuntimeDiagnosticKind.NETWORK_SWITCHED,
            detail = "底层网络已切换",
            timestampMillis = 7L,
        )
        val running = DiagnosticSessionUiState(status = DiagnosticSessionStatus.RUNNING)
        val captured = running.withRuntimeEvents(listOf(event))
        val finished = captured.copy(status = DiagnosticSessionStatus.PASS)

        assertEquals(listOf(event), captured.runtimeEvents)
        assertEquals(finished, finished.withRuntimeEvents(emptyList()))
    }

    @Test
    fun `diagnostic session captures event flow health only while running`() {
        val stats = CoreEventFlowStats(
            parsedBatches = 3,
            parsedEvents = 12,
            emittedEvents = 10,
            droppedEvents = 2,
            truncatedEvents = 1,
            lastEventAtMillis = 7L,
        )
        val running = DiagnosticSessionUiState(status = DiagnosticSessionStatus.RUNNING)
        val captured = running.withEventFlowStats(stats)
        val finished = captured.copy(status = DiagnosticSessionStatus.PASS)

        assertEquals(stats, captured.eventFlowStats)
        assertEquals(finished, finished.withEventFlowStats(CoreEventFlowStats()))
    }

    @Test
    fun `runtime diagnostic history shows newest events first and labels restarts`() {
        val events = listOf(
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.NETWORK_LOST, "断网", 1L),
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.DNS_UPDATED, "DNS 更新", 2L),
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.CORE_STARTED, "启动", 3L),
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.CORE_RESTARTED, "重启", 4L),
        )

        assertEquals(
            listOf(RuntimeDiagnosticKind.CORE_RESTARTED, RuntimeDiagnosticKind.CORE_STARTED, RuntimeDiagnosticKind.DNS_UPDATED),
            runtimeDiagnosticHistoryRows(events).map { it.kind },
        )
        assertEquals("核心重启", runtimeDiagnosticLabel(RuntimeDiagnosticKind.CORE_RESTARTED))
    }

    @Test
    fun `diagnostic assertion requires status and route`() {
        val result = CoreProbeResult(
            statusCode = 403,
            delay = 120L,
            url = "https://example.test",
            chains = listOf("GLOBAL", "DIRECT"),
            rule = "MATCH",
            rulePayload = "",
            error = "",
            message = "",
            coreEpoch = 1L,
            routeVersion = 2L,
        )
        val noRoute = result.copy(chains = emptyList())

        assertEquals(null, diagnosticFailureReason(result))
        assertEquals("核心未返回代理链", diagnosticFailureReason(noRoute))
    }

    @Test
    fun `diagnostic export contains probe and related evidence without token`() {
        val session = DiagnosticSessionUiState(
            status = DiagnosticSessionStatus.PASS,
            sessionId = 9L,
            targetUrl = "https://example.test/config?token=secret-value",
            result = CoreProbeResult(
                statusCode = 200,
                delay = 80L,
                url = "https://example.test/config",
                chains = listOf("GLOBAL", "DIRECT"),
                rule = "MATCH",
                rulePayload = "",
                error = "",
                message = "",
                coreEpoch = 1L,
                routeVersion = 2L,
            ),
            requests = listOf(connection("request-1")),
            dns = listOf(dnsQuery("probe.example.com")),
            logs = listOf(CoreEvent.Log("info", "token=secret-value", 4L)),
            runtimeEvents = listOf(
                RuntimeDiagnosticEvent(
                    RuntimeDiagnosticKind.DNS_UPDATED,
                    "DNS 服务器数量：2",
                    5L,
                ),
            ),
            eventFlowStats = CoreEventFlowStats(
                parsedBatches = 2,
                parsedEvents = 4,
                emittedEvents = 3,
                droppedEvents = 1,
                truncatedEvents = 0,
                lastEventAtMillis = 6L,
            ),
        )

        val exported = formatDiagnosticForExport(session)

        assertEquals(true, exported.contains("KClash diagnostic session"))
        assertEquals(true, exported.contains("HTTP 200"))
        assertEquals(true, exported.contains("[requests]"))
        assertEquals(true, exported.contains("[dns]"))
        assertEquals(true, exported.contains("[logs]"))
        assertEquals(true, exported.contains("[runtime]"))
        assertEquals(true, exported.contains("[event_flow]"))
        assertEquals(true, exported.contains("dropped_events\t1"))
        assertEquals(true, exported.contains("DNS_UPDATED"))
        assertEquals(true, exported.contains("[REDACTED_SECRET]"))
        assertEquals(false, exported.contains("secret-value"))
    }

    @Test
    fun `runtime diagnostic history export keeps timeline and redacts details`() {
        val exported = formatRuntimeDiagnosticHistoryForExport(
            listOf(
                RuntimeDiagnosticEvent(
                    kind = RuntimeDiagnosticKind.CORE_STARTED,
                    detail = "启动 https://example.test/config?token=secret-value",
                    timestampMillis = 10L,
                ),
                RuntimeDiagnosticEvent(
                    kind = RuntimeDiagnosticKind.NETWORK_LOST,
                    detail = "底层网络丢失\npassword=p@ss",
                    timestampMillis = 20L,
                ),
            ),
        )

        assertEquals(true, exported.startsWith("KClash runtime diagnostic history\n"))
        assertEquals(true, exported.contains("event_count\t2"))
        assertEquals(true, exported.contains("10\tCORE_STARTED\t"))
        assertEquals(true, exported.contains("20\tNETWORK_LOST\t"))
        assertEquals(true, exported.contains("[REDACTED_SECRET]"))
        assertEquals(false, exported.contains("secret-value"))
        assertEquals(false, exported.contains("p@ss"))
        assertEquals(false, exported.contains("\npassword="))
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
