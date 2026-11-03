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
import com.google.gson.JsonParser

class CoreBridgeParsingTest {
    @Test fun providerMembershipOnlyKeepsValidNonemptyStringMetadata() {
        val nodes = JsonParser.parseString("""{"a":{"provider-name":"fixture"},"inline":{"type":"Vless"},"blank":{"provider-name":" "},"number":{"provider-name":4},"object":{"provider-name":{}},"null":null}""").asJsonObject
        assertEquals(setOf("a"), CoreBridge().parseProviderProxies(nodes))
    }
    @Test fun geoEventsDistinguishAcceptedProgressCompletionAndFailure() {
        val events = CoreBridge().parseEvents("""{"method":"message","arguments":[
            {"type":"geoUpdate","data":{"type":"MMDB","updating":true}},
            {"type":"geoUpdate","data":{"type":"MMDB","updating":false}},
            {"type":"geoUpdate","data":{"type":"GEOIP","updating":false,"error":"download failed"}},
            {"type":"geoUpdate","data":{"type":"unknown","updating":true}}]}""".toByteArray())
        assertEquals(listOf(CoreEvent.GeoUpdate("MMDB", true), CoreEvent.GeoUpdate("MMDB", false),
            CoreEvent.GeoUpdate("GEOIP", false, error = "download failed")), events)
    }
    @Test fun proxyTypesKeepLeavesGroupsAndUnknownProtocolsWithoutPrivateMetadata() {
        val types = CoreBridge().parseProxyTypes(JsonParser.parseString("""{
            "Main":{"type":"Selector","all":["a","b"],"now":"a"},
            "a":{"type":"Shadowsocks","provider-name":"fixture-provider","password":"fixture-secret"},
            "b":{"type":"FutureProtocol"},"DIRECT":{"type":"Direct"},"REJECT":{"type":"Reject"}
        }""").asJsonObject)
        assertEquals(mapOf("Main" to "Selector", "a" to "Shadowsocks", "b" to "FutureProtocol", "DIRECT" to "Direct", "REJECT" to "Reject"), types)
        assertFalse(types.toString().contains("secret"))
        assertFalse(types.toString().contains("provider"))
    }

    @Test fun proxyTypesIgnoreMalformedMissingOrBlankFields() {
        val types = CoreBridge().parseProxyTypes(JsonParser.parseString("""{
            "null":null,"array":[],"number":1,"missing":{},"blank":{"type":" "},
            "wrong":{"type":3},"object":{"type":{}},"valid":{"type":"Vless"}
        }""").asJsonObject)
        assertEquals(mapOf("valid" to "Vless"), types)
    }

    @Test fun providerDetailsParseOnlyPublicFieldsAndRfc3339Times() {
        val bridge = CoreBridge()
        val raw = """[{"name":"fixture","type":"Proxy","count":12,"vehicle-type":"HTTP","update-at":"2026-11-02T08:00:00.123456789+08:00","path":"private","subscription-info":{"token":"fixture-secret"}}]"""
        val result = bridge.parseExternalProviders(JsonParser.parseString(raw).asJsonArray).single()
        assertEquals(CoreExternalProvider("fixture", "Proxy", "HTTP", 12, 1793577600123L), result.copy(fileIdentity = null))
        assertEquals(64, result.fileIdentity?.length)
        assertFalse(result.toString().contains("private"))
        assertTrue(result.canUpdate)
        assertFalse(result.toString().contains("secret"))
        assertFalse(result.copy(vehicleType = "File").canUpdate)
        assertEquals(null, bridge.parseProviderTime("0001-01-01T00:00:00Z"))
        assertEquals(1793577600000L, bridge.parseProviderTime("2026-11-02T00:00:00Z"))
    }

    @Test fun providerDocumentsAndBatchesRejectWrongKindsDigestsAndResultOrder() {
        val bridge = CoreBridge()
        val source = """{"name":"rules","kind":"text","content":"fixture-secret","digest":"missing"}"""
        val doc = bridge.parseProviderDocument(JsonParser.parseString(source).asJsonObject)
        assertTrue(doc.editable); assertFalse(doc.toString().contains("fixture-secret"))
        for (bad in listOf(source.replace("text", "unknown"), source.replace("missing", "bad"), source.replace("\"content\":\"fixture-secret\"", "\"content\":2"))) {
            assertTrue(runCatching { bridge.parseProviderDocument(JsonParser.parseString(bad).asJsonObject) }.isFailure)
        }
        val result = JsonParser.parseString("""[{"name":"a","code":""},{"name":"b","code":"request_timeout"}]""").asJsonArray
        assertEquals(listOf(CoreProviderUpdateResult("a", ""), CoreProviderUpdateResult("b", "request_timeout")), bridge.parseProviderUpdateResults(result, listOf("a", "b")))
        assertTrue(runCatching { bridge.parseProviderUpdateResults(result, listOf("b", "a")) }.isFailure)
    }

    @Test fun providerSubscriptionUsesGoFieldsAndSaturatesUsedBytes() {
        val raw = """[{"name":"a","type":"Proxy","count":1,"vehicle-type":"HTTP","update-at":"0001-01-01T00:00:00Z","subscription-info":{"Upload":9223372036854775807,"Download":4,"Total":10,"Expire":100}}]"""
        val info = CoreBridge().parseExternalProviders(JsonParser.parseString(raw).asJsonArray).single().subscriptionInfo!!
        assertEquals(Long.MAX_VALUE, info.used); assertEquals(1f, info.usage); assertEquals(100000L, info.expireAtMillis)
    }

    @Test fun providerDetailsRejectMalformedMetadataAndEmptyIsRealEmpty() {
        val bridge = CoreBridge()
        val good = """{"name":"fixture","type":"Rule","count":0,"vehicle-type":"File","update-at":"0001-01-01T00:00:00Z"}"""
        listOf(good.replace("File", ""), good.replace("0001-01-01T00:00:00Z", "2026-02-30T00:00:00Z"),
            good.replace("0001-01-01T00:00:00Z", "bad"), good.replace("\"vehicle-type\":\"File\",", ""),
            good.replace("\"count\":0", "\"count\":-1")).forEach { value ->
            assertTrue(runCatching { bridge.parseExternalProviders(JsonParser.parseString("[$value]").asJsonArray) }.isFailure)
        }
        assertEquals(emptyList<CoreExternalProvider>(), bridge.parseExternalProviders(JsonParser.parseString("[]").asJsonArray))
    }

    @Test fun providerCountsOnlyAddsProxyItemsAndCountsBothProviderTypes() {
        val raw = JsonParser.parseString("""[{"name":"a","type":"Proxy","count":7},{"name":"b","type":"Rule","count":1000}]""").asJsonArray
        assertEquals(CoreProviderCounts(7, 2), CoreBridge().parseProviderCounts(raw))
        assertEquals(CoreProviderCounts(0, 0), CoreBridge().parseProviderCounts(JsonParser.parseString("[]").asJsonArray))
    }

    @Test fun providerCountsRejectsMissingMalformedDuplicateAndOverflowValues() {
        val good = """{"name":"a","type":"Proxy","count":7}"""
        val cases = listOf("[{}]", "[null]", "[$good,$good]", "[" + good.replace(":7", ":-1") + "]",
            "[" + good.replace(":7", ":1.5") + "]", "[" + good.replace(":7", ":\"7\"") + "]",
            "[" + good.replace("Proxy", "Future") + "]",
            """[{"name":"a","type":"Proxy","count":9223372036854775807},{"name":"b","type":"Proxy","count":1}]""")
        cases.forEach { raw -> assertTrue(raw, runCatching { CoreBridge().parseProviderCounts(JsonParser.parseString(raw).asJsonArray) }.isFailure) }
        assertThrows(IllegalArgumentException::class.java) { CoreBridge().parseProviderCounts(null) }
    }

    @Test fun serviceHitsMustAllBeCompleteBeforeTheyCanBeReused() {
        val bridge = CoreBridge()
        fun parse(hits: String) = bridge.parseServiceChecks(JsonParser.parseString(
            """[{"name":"github","status":"available","checked-at":1,"core-epoch":2,"route-version":3,"hits":$hits}]""").asJsonArray,
            listOf(ServiceTarget.GITHUB)).single()
        val valid = """{"host":"example.com:443","exit":"fixture-exit"}"""
        assertEquals(listOf(CoreRouteHit("example.com:443", "", "fixture-exit")), parse("[$valid]").hits)
        listOf("null", "{}", "[]", "[null]", "[$valid,{}]", "[$valid,1]",
            """[{"host":"","exit":"fixture-exit"}]""", """[{"host":"example.com:443","exit":""}]""",
            """[{"host":1,"exit":"fixture-exit"}]""").forEach { raw ->
            val result = parse(raw)
            assertEquals(ServiceStatus.AVAILABLE, result.status)
            assertTrue(raw, result.hits.isEmpty())
        }
    }

    @Test fun routeCheckRejectsMissingMisalignedAndCoercedFields() {
        val bridge = CoreBridge()
        val good = """{"core-epoch":2,"route-version":3,"held":[true,false]}"""
        assertEquals(CoreRouteCheck(CoreRouteStamp(2, 3), listOf(true, false)),
            bridge.parseRouteCheck(JsonParser.parseString(good).asJsonObject, 2))
        val invalid = listOf("{}", good.replace("[true,false]", "[true]"), good.replace("[true,false]", "[true,1]"),
            good.replace("[true,false]", "[true,null]"), good.replace(":2", ":-2"), good.replace(":2", ":2.5"),
            good.replace(":3", ":9223372036854775808"), good.replace("true", "\"true\""))
        invalid.forEach { raw -> assertTrue(raw, runCatching { bridge.parseRouteCheck(JsonParser.parseString(raw).asJsonObject, 2) }.isFailure) }
        assertThrows(IllegalArgumentException::class.java) { bridge.parseRouteCheck(null, 2) }
    }

    @Test fun serviceChecksPreserveStatusRouteAndRequestedOrder() {
        val rows = ServiceStatus.entries.map { status ->
            """{"name":"google","status":"${status.wire}","region":"US","delay":12,"chains":["DIRECT"],"checked-at":100,"core-epoch":2,"route-version":3}"""
        }
        rows.zip(ServiceStatus.entries).forEach { (row, status) ->
            val parsed = CoreBridge().parseServiceChecks(JsonParser.parseString("[$row]").asJsonArray, listOf(ServiceTarget.GOOGLE)).single()
            assertEquals(status, parsed.status)
            assertEquals(CoreRouteStamp(2, 3), parsed.stamp)
            assertEquals(12L, parsed.delayMillis)
            assertEquals(listOf("DIRECT"), parsed.chains)
        }
        val row = rows.first()
        val ordered = CoreBridge().parseServiceChecks(JsonParser.parseString("[$row,${row.replace("google", "github")}]").asJsonArray, listOf(ServiceTarget.GITHUB, ServiceTarget.GOOGLE))
        assertEquals(listOf(ServiceTarget.GITHUB, ServiceTarget.GOOGLE), ordered.map { it.target })
        assertEquals(ServiceStatus.FAILED, CoreBridge().parseServiceChecks(JsonParser.parseString("[${row.replace("available", "future-value")}]").asJsonArray, listOf(ServiceTarget.GOOGLE)).single().status)
    }

    @Test fun serviceChecksRejectMissingDuplicateUnknownAndMalformedFields() {
        val good = """{"name":"google","status":"available","checked-at":100,"core-epoch":2,"route-version":3}"""
        val invalid = listOf("[]", "[$good,$good]", "[${good.replace("google", "unknown")}]", "[null]",
            "[${good.replace("100", "-1")}]", "[${good.replace("100", "1.5")}]",
            "[${good.replace("100", "9223372036854775808")}]", "[${good.replace("\"checked-at\":100,", "")}]",
            "[${good.dropLast(1)},\"chains\":[1]}]", "[${good.dropLast(1)},\"delay\":\"12\"}]")
        invalid.forEach { raw -> assertTrue(raw, runCatching { CoreBridge().parseServiceChecks(JsonParser.parseString(raw).asJsonArray, listOf(ServiceTarget.GOOGLE)) }.isFailure) }
        assertThrows(IllegalArgumentException::class.java) { CoreBridge().parseServiceChecks(null, listOf(ServiceTarget.GOOGLE)) }
    }

    @Test fun lanFieldsRejectMalformedBooleansAndPorts() {
        val bridge = CoreBridge()
        val valid = com.google.gson.JsonParser.parseString("""{"allow-lan":true,"mixed-port":7890}""").asJsonObject
        assertEquals(CoreLanConfig(true, 7890), bridge.parseLanConfig(valid))
        for (bad in listOf(
            """{"allow-lan":"true","mixed-port":7890}""",
            """{"allow-lan":true,"mixed-port":-1}""",
            """{"allow-lan":true,"mixed-port":65536}""",
            """{"allow-lan":true,"mixed-port":1.5}""",
            """{"mixed-port":7890}""",
        )) assertTrue(runCatching { bridge.parseLanConfig(com.google.gson.JsonParser.parseString(bad).asJsonObject) }.isFailure)
    }

    @Test
    fun ipInformationPreservesBodyStatusAndRouteButDoesNotLogBody() {
        val parsed = CoreBridge().parseIpJson(JsonParser.parseString(
            """{"url":"https://fixture.invalid","body":"sensitive-body","status-code":429,"core-epoch":3,"route-version":7}""",
        ).asJsonObject)
        assertEquals(429, parsed.statusCode)
        assertEquals(CoreRouteStamp(3, 7), parsed.stamp)
        assertEquals("sensitive-body", parsed.body)
        assertFalse(parsed.toString().contains("sensitive-body"))
        assertFalse(parsed.toString().contains("fixture.invalid"))
        assertEquals(200, CoreBridge().parseIpJson(JsonParser.parseString("{}").asJsonObject).statusCode)
        assertThrows(IllegalArgumentException::class.java) { CoreBridge().parseIpJson(null) }
    }
    @Test fun memoryFieldsAreExactNonNegativeAndTotalsDoNotOverflow() {
        val bridge = CoreBridge()
        val good = JsonParser.parseString("""{"rss":100,"heapInuse":20,"heapIdle":10,"stackInuse":5,"runtimeOther":5}""").asJsonObject
        val memory = bridge.parseMemoryJson(good)
        assertEquals(100L, memory.rss)
        assertEquals(40L, memory.runtimeTotal)
        for (bad in listOf("{}", "null", "-1", "1.5", "9223372036854775808")) {
            if (bad == "{}") {
                assertThrows(IllegalArgumentException::class.java) { bridge.parseMemoryJson(JsonParser.parseString(bad).asJsonObject) }
            } else {
                val invalid = good.deepCopy().apply { add("rss", JsonParser.parseString(bad)) }
                assertTrue(runCatching { bridge.parseMemoryJson(invalid) }.isFailure)
            }
        }
        assertEquals(Long.MAX_VALUE, CoreMemoryStats(0L, Long.MAX_VALUE, 1L, 1L, 1L).runtimeTotal)
    }

    private val bridge = CoreBridge()

    @Test
    fun `core outbound mode accepts only supported values`() {
        assertEquals(CoreOutboundMode.RULE, CoreOutboundMode.fromWire("rule"))
        assertEquals(CoreOutboundMode.GLOBAL, CoreOutboundMode.fromWire("GLOBAL"))
        assertEquals(CoreOutboundMode.DIRECT, CoreOutboundMode.fromWire("direct"))
        assertThrows(CoreBridgeException::class.java) {
            CoreOutboundMode.fromWire("bypass")
        }
    }

    @Test
    fun `polling and request event preserve the same connection metadata`() {
        val connection = """{
          "id":"metadata-1","start":"2026-10-31T00:00:00.123456789Z","upload":-1,"download":20,
          "metadata":{"network":"tcp","sourcePort":12345,"destinationPort":"443",
          "sourceGeoIP":["LAN"],"destinationGeoIP":["US","Example ISP"],
          "sourceIPASN":"AS64512","destinationIPASN":"AS64496","dnsMode":"fake-ip"},
          "chains":["GLOBAL","DIRECT"],"rule":"MATCH"
        }"""
        val polled = bridge.parseConnectionsJson(JsonParser.parseString("""{"connections":[$connection]}""").asJsonObject).single()
        val event = bridge.parseEvents("""{"method":"message","arguments":[{"type":"request","data":$connection}]}""".toByteArray()).single() as CoreEvent.Request
        assertEquals(polled, event.connection)
        assertEquals(listOf("LAN"), polled.metadata.sourceGeoIp)
        assertEquals(listOf("US", "Example ISP"), polled.metadata.destinationGeoIp)
        assertEquals("AS64512", polled.metadata.sourceIpAsn)
        assertEquals("AS64496", polled.metadata.destinationIpAsn)
        assertEquals("fake-ip", polled.metadata.dnsMode)
        assertEquals("12345", polled.metadata.sourcePort)
        assertEquals(0L, polled.upload)
    }

    @Test
    fun `missing and null metadata do not become visible null text`() {
        for (raw in listOf("{}", """{"destinationGeoIP":null,"destinationIPASN":null,"dnsMode":null}""")) {
            val parsed = bridge.parseConnectionsJson(JsonParser.parseString("""{"connections":[{"metadata":$raw}]}""").asJsonObject).single()
            assertEquals(emptyList<String>(), parsed.metadata.destinationGeoIp)
            assertEquals("", parsed.metadata.destinationIpAsn)
            assertEquals("", parsed.metadata.dnsMode)
        }
    }

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
                    fixed = "香港-01",
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
        assertEquals("香港-01", result.groups[1].fixed)

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
    fun `message response keeps raw core validation text`() {
        val error = assertThrows(CoreBridgeException::class.java) {
            bridge.parseMessageResult(
                message = "line 7: unknown field token=secret",
                code = "invalid_config",
            )
        }

        assertEquals("invalid_config", error.code)
        assertEquals("line 7: unknown field token=secret", error.message)
    }

    @Test
    fun `error response preserves structured and scalar bridge errors`() {
        val structured = bridge.parseErrorPayload(
            JsonParser.parseString("{\"code\":\"apply_failed\",\"message\":\"bad field\"}"),
        )
        assertEquals("apply_failed", structured?.code)
        assertEquals("bad field", structured?.message)

        val scalar = bridge.parseErrorPayload(JsonParser.parseString("\"mihomo failed\""))
        assertEquals("core_error", scalar?.code)
        assertEquals("mihomo failed", scalar?.message)
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
                    sourceGeoIp = listOf("10.0.0.0/8"),
                    destinationGeoIp = listOf("US", "Example ISP"),
                    sourceIpAsn = "AS64512",
                    destinationIpAsn = "AS64496",
                    dnsMode = "fake-ip",
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
        assertEquals(listOf("US", "Example ISP"), parsed.metadata.destinationGeoIp)
        assertEquals("AS64496", parsed.metadata.destinationIpAsn)
        assertEquals("fake-ip", parsed.metadata.dnsMode)
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
                      "process":"com.example.app",
                      "sourceGeoIP":["LAN"],
                      "destinationGeoIP":["US","Example ISP"],
                      "sourceIPASN":"AS64512",
                      "destinationIPASN":"AS64496",
                      "dnsMode":"redir-host"
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
        assertEquals(listOf("US", "Example ISP"), request.metadata.destinationGeoIp)
        assertEquals("AS64496", request.metadata.destinationIpAsn)
        assertEquals("redir-host", request.metadata.dnsMode)
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
