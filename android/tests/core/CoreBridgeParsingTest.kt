package com.lonnnnnng.flclash.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class CoreBridgeParsingTest {
    private val bridge = CoreBridge()

    @Test
    fun `proxy group response preserves core ordering and selector members`() {
        val result = bridge.parseProxyGroupsPayload(
            names = listOf("GLOBAL", "媒体"),
            payloads = mapOf(
                "GLOBAL" to ProxyGroupPayload(
                    name = "GLOBAL",
                    type = "Selector",
                    now = "自动",
                    all = listOf("自动", "直连"),
                    hidden = false,
                    testUrl = "https://example.com",
                ),
                "媒体" to ProxyGroupPayload(
                    name = "媒体",
                    type = "URLTest",
                    now = "香港-01",
                    all = listOf("香港-01"),
                    hidden = false,
                ),
            ),
        )

        assertEquals(listOf("GLOBAL", "媒体"), result.groups.map { it.name })
        assertEquals(listOf("自动", "直连"), result.groups.first().all)
        assertTrue(result.groups.first().isSelector)
        assertEquals("https://example.com", result.groups.first().testUrl)
        assertFalse(result.groups[1].isSelector)
        assertTrue(result.groups[1].isComputed)
        assertTrue(result.groups[1].isSelectable)
        assertEquals("香港-01", result.groups[1].now)

        val fallback = result.groups[1].copy(type = "Fallback")
        assertTrue(fallback.isComputed)
        assertTrue(fallback.isSelectable)
    }

    @Test
    fun `change proxy response exposes changed flag`() {
        assertTrue(bridge.parseChangeProxyPayload(message = "", changed = true))
        assertFalse(bridge.parseChangeProxyPayload(message = "", changed = false))
    }

    @Test
    fun `change proxy response turns core message into typed failure`() {
        val error = assertThrows(CoreBridgeException::class.java) {
            bridge.parseChangeProxyPayload(message = "节点不存在", changed = false)
        }

        assertEquals("change_proxy_failed", error.code)
        assertEquals("节点不存在", error.message)
    }

    @Test
    fun `delay payload preserves success timeout and failure values`() {
        assertEquals(
            ProxyDelay(url = "https://example.com", name = "香港-01", value = 123),
            bridge.parseDelayPayload(
                ProxyDelayPayload(
                    url = "https://example.com",
                    name = "香港-01",
                    value = 123,
                ),
            ),
        )
        assertEquals(
            -1,
            bridge.parseDelayPayload(ProxyDelayPayload("", "香港-01", -1))?.value,
        )
        assertEquals(
            -2,
            bridge.parseDelayPayload(ProxyDelayPayload("", "香港-01", -2))?.value,
        )
    }

    @Test
    fun `empty delay result means no concurrent slot result`() {
        assertEquals(null, bridge.parseDelayPayload(null))
    }

    @Test
    fun `probe response preserves status delay and route attribution`() {
        val payload = CoreProbePayload(
            statusCode = 200,
            delay = 145,
            url = "https://example.com/final",
            chains = listOf("GLOBAL", "FR-1-01"),
            rule = "DOMAIN-SUFFIX",
            rulePayload = "example.com",
            error = "",
            message = "",
            coreEpoch = 7,
            routeVersion = 9,
        )
        val parsedPayload = bridge.parseProbePayload(payload)

        assertEquals(200, parsedPayload.statusCode)
        assertEquals(145L, parsedPayload.delay)
        assertEquals(listOf("GLOBAL", "FR-1-01"), parsedPayload.chains)
        assertEquals("DOMAIN-SUFFIX", parsedPayload.rule)
        assertEquals(7L, parsedPayload.coreEpoch)
        assertEquals(9L, parsedPayload.routeVersion)
    }

    @Test
    fun `empty probe result means core returned no slot`() {
        assertEquals(null, bridge.parseProbeResponse(null))
    }

    @Test
    fun `traffic response clamps malformed negative counters`() {
        assertEquals(
            CoreTraffic(up = 0, down = 12),
            bridge.parseTrafficPayload(up = -5, down = 12),
        )
    }

    @Test
    fun `connections response maps tracker metadata and clamps counters`() {
        val parsed = bridge.parseConnectionPayload(
            CoreConnectionPayload(
                id = "connection-1",
                upload = -4,
                download = 128,
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
                    uid = 12345,
                    specialProxy = "",
                    specialRules = "",
                ),
                chains = listOf("GLOBAL", "香港-01"),
                rule = "MATCH",
                rulePayload = "香港",
            ),
        )

        assertEquals("connection-1", parsed.id)
        assertEquals(CoreTraffic(0, 128), CoreTraffic(parsed.upload, parsed.download))
        assertEquals("example.com", parsed.metadata.host)
        assertEquals("443", parsed.metadata.destinationPort)
        assertEquals(listOf("GLOBAL", "香港-01"), parsed.chains)
        assertEquals("MATCH", parsed.rule)
    }

    @Test
    fun `event batch maps request and traffic update messages`() {
        val events = bridge.parseEvents(
            """
            {
              "method":"message",
              "arguments":[
                {
                  "type":"request",
                  "data":{
                    "id":"request-1",
                    "upload":4,
                    "download":8,
                    "start":"2026-10-25T11:00:00Z",
                    "metadata":{
                      "network":"tcp",
                      "host":"example.com",
                      "destinationIP":"203.0.113.7",
                      "destinationPort":"443",
                      "process":"com.example.app"
                    },
                    "chains":["GLOBAL","DIRECT"],
                    "rule":"DOMAIN-SUFFIX",
                    "rulePayload":"example.com"
                  }
                },
                {
                  "type":"requestTraffic",
                  "data":[{"id":"request-1","upload":40,"download":80}]
                }
              ]
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals(2, events.size)
        val request = (events[0] as CoreEvent.Request).connection
        assertEquals("request-1", request.id)
        assertEquals("example.com", request.metadata.host)
        assertEquals("DOMAIN-SUFFIX", request.rule)
        val traffic = (events[1] as CoreEvent.RequestTraffic).updates.single()
        assertEquals(CoreTrafficUpdate("request-1", 40, 80), traffic)
    }

    @Test
    fun `event parser drops malformed and unknown events without failing the batch`() {
        val events = bridge.parseEvents(
            """
            {
              "method":"message",
              "arguments":[
                {"type":"unknown","data":{}},
                {"type":"request","data":"invalid"},
                {"type":"log","data":{"LogLevel":3,"Payload":"failed"}}
              ]
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals(1, events.size)
        val log = events.single() as CoreEvent.Log
        assertEquals("error", log.level)
        assertEquals("failed", log.payload)
    }

    @Test
    fun `event parser maps dns query details`() {
        val events = bridge.parseEvents(
            """
            {
              "method":"message",
              "arguments":[
                {
                  "type":"dns",
                  "data":{
                    "domain":"api.example.com",
                    "type":"A",
                    "initiator":"com.example.app",
                    "upstream":"https://dns.example/query",
                    "cached":true,
                    "answers":["203.0.113.7","203.0.113.8"],
                    "rcode":"NOERROR",
                    "error":"",
                    "delay":18,
                    "time":"2026-10-25T11:00:00Z"
                  }
                }
              ]
            }
            """.trimIndent().toByteArray(),
        )

        val query = (events.single() as CoreEvent.Dns).query
        assertEquals("api.example.com", query.domain)
        assertEquals("A", query.type)
        assertEquals("com.example.app", query.initiator)
        assertEquals("https://dns.example/query", query.upstream)
        assertTrue(query.cached)
        assertEquals(listOf("203.0.113.7", "203.0.113.8"), query.answers)
        assertEquals("NOERROR", query.rcode)
        assertEquals(18L, query.delay)
    }

    @Test
    fun `event parser maps automatic proxy delay messages`() {
        val events = bridge.parseEvents(
            """
            {
              "method":"message",
              "arguments":[
                {"type":"delay","data":{"url":"https://example.com","name":"香港-01","value":123}},
                {"type":"delay","data":{"url":"https://example.com","name":"香港-02","value":-1}}
              ]
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals(
            listOf(
                CoreEvent.Delay(ProxyDelay("https://example.com", "香港-01", 123)),
                CoreEvent.Delay(ProxyDelay("https://example.com", "香港-02", -1)),
            ),
            events,
        )
    }

    @Test
    fun `event dispatch uses a bounded batch and records undelivered events`() {
        val payload = buildString {
            append("{\"method\":\"message\",\"arguments\":[")
            repeat(CoreBridge.MAX_EVENTS_PER_BATCH + 1) { index ->
                if (index > 0) append(',')
                append("{\"type\":\"log\",\"data\":{\"LogLevel\":1,\"Payload\":\"event-$index\"}}")
            }
            append("]}")
        }.toByteArray()

        bridge.dispatchEvents(payload)

        val stats = bridge.eventFlowStats.value
        assertEquals(1L, stats.parsedBatches)
        assertEquals(CoreBridge.MAX_EVENTS_PER_BATCH.toLong(), stats.parsedEvents)
        assertEquals(0L, stats.emittedEvents)
        assertEquals(CoreBridge.MAX_EVENTS_PER_BATCH.toLong(), stats.droppedEvents)
        assertEquals(1L, stats.truncatedEvents)
        assertTrue(stats.lastEventAtMillis > 0L)
        assertEquals(CoreBridge.MAX_EVENTS_PER_BATCH, bridge.parseEvents(payload).size)
    }

    @Test
    fun `event dispatch records events accepted by an active subscriber`() = runBlocking {
        val received = Channel<CoreEvent>(Channel.UNLIMITED)
        val collector = launch {
            bridge.events.collect { received.send(it) }
        }
        yield()

        bridge.dispatchEvents(
            """{"method":"message","arguments":[{"type":"log","data":{"LogLevel":1,"Payload":"ok"}}]}"""
                .toByteArray(),
        )

        assertTrue(received.receive() is CoreEvent.Log)
        assertEquals(1L, bridge.eventFlowStats.value.emittedEvents)
        assertEquals(0L, bridge.eventFlowStats.value.droppedEvents)
        collector.cancel()
    }

    @Test
    fun `event dispatch counts newest events dropped when the subscriber cannot keep up`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val collector = launch {
            bridge.events.collect { gate.await() }
        }
        yield()
        val payload =
            """{"method":"message","arguments":[{"type":"log","data":{"LogLevel":1,"Payload":"busy"}}]}"""
                .toByteArray()

        repeat(CoreBridge.EVENT_BUFFER_SIZE + 32) { bridge.dispatchEvents(payload) }

        val stats = bridge.eventFlowStats.value
        assertEquals((CoreBridge.EVENT_BUFFER_SIZE + 32).toLong(), stats.parsedEvents)
        assertTrue(stats.droppedEvents > 0L)
        assertEquals(stats.parsedEvents, stats.emittedEvents + stats.droppedEvents)
        collector.cancel()
    }
}
