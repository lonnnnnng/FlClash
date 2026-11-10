package com.lonnnnnng.flclash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProviderOverridesTest {
    @Test fun `provider filters stay at provider level and explicit clear survives metadata`() {
        val source = ProfileProviderOverrides(proxyProviders = mapOf("cloud" to ProxyProviderOverride(filter = "HK`(?<=a)b", excludeFilter = "")))
        assertEquals(source, ProfileProviderOverrides.fromJsonString(source.toJsonString()))
        val yaml = "proxy-providers:\n  cloud:\n    type: http\n    filter: |\n      old\n    exclude-filter: old\n    override:\n      udp: true\n"
        val root = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(ProfileProviderOverrideApplier.apply(yaml, source))).asJsonObject
        val provider = root.getAsJsonObject("proxy-providers").getAsJsonObject("cloud")
        assertEquals("HK`(?<=a)b", provider["filter"].asString)
        assertEquals("", provider["exclude-filter"].asString)
        assertTrue(provider.getAsJsonObject("override")["udp"].asBoolean)
    }
    @Test
    fun `json round trip keeps dns override fields`() {
        val source = ProfileProviderOverrides(
            dns = ProfileDnsOverrides(
                enable = true,
                listen = "0.0.0.0:1053",
                ipv6 = false,
                enhancedMode = "fake-ip",
                nameserver = listOf("https://doh.example/dns-query"),
                fallback = listOf("tls://1.1.1.1:853"),
                fakeIpFilter = listOf("localhost", "*.lan"),
            ),
        )

        assertEquals(source, ProfileProviderOverrides.fromJsonString(source.toJsonString()))
    }

    @Test
    fun `json round trip keeps dialer overrides including explicit empty map`() {
        val source = ProfileProviderOverrides(dialers = mapOf("node-a" to "Relay"))
        assertEquals(source, ProfileProviderOverrides.fromJsonString(source.toJsonString()))

        val cleared = ProfileProviderOverrides(dialers = emptyMap())
        assertEquals(cleared, ProfileProviderOverrides.fromJsonString(cleared.toJsonString()))
    }

    @Test
    fun `json round trip keeps all supported fields`() {
        val source = ProfileProviderOverrides(
            proxyProviders = mapOf(
                "cloud" to ProxyProviderOverride(
                    url = "https://example.com/health",
                    interval = 300,
                    timeout = 5_000,
                    lazy = true,
                    expectedStatus = "204",
                    additionalPrefix = "HK-",
                    additionalSuffix = "-A",
                    udp = true,
                    skipCertVerify = false,
                    ipVersion = "dual",
                ),
            ),
        )

        val decoded = ProfileProviderOverrides.fromJsonString(source.toJsonString())

        assertEquals(source, decoded)
    }

    @Test
    fun `applies health check and connection overrides to matching provider`() {
        val yaml = """
            mixed-port: 7890
            proxy-providers:
              cloud:
                type: http
                url: https://example.com/cloud
                path: ./cloud.yaml
              local:
                type: file
                path: ./local.yaml

            # keep the following section and its boundary comment
            rules:
              - MATCH,DIRECT
        """.trimIndent() + "\n"
        val overrides = ProfileProviderOverrides(
            mapOf(
                "cloud" to ProxyProviderOverride(
                    url = "https://example.com/ping",
                    interval = 300,
                    timeout = 5_000,
                    lazy = true,
                    expectedStatus = "200",
                    additionalPrefix = "Cloud-",
                    udp = false,
                    skipCertVerify = true,
                    ipVersion = "ipv4",
                ),
            ),
        )

        val actual = ProfileProviderOverrideApplier.apply(yaml, overrides)

        assertEquals(
            """
                mixed-port: 7890
                proxy-providers:
                  cloud:
                    type: http
                    url: https://example.com/cloud
                    path: ./cloud.yaml
                    health-check:
                      enable: true
                      url: "https://example.com/ping"
                      interval: 300
                      timeout: 5000
                      lazy: true
                      expected-status: "200"
                    override:
                      additional-prefix: "Cloud-"
                      udp: false
                      skip-cert-verify: true
                      ip-version: ipv4
                  local:
                    type: file
                    path: ./local.yaml

                # keep the following section and its boundary comment
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n",
            actual,
        )
        assertTrue(actual.contains("local:\n    type: file"))
    }

    @Test
    fun `merges existing nested blocks without dropping unknown fields`() {
        val yaml = """
            proxy-providers:
              cloud:
                type: http
                health-check:
                  enable: false
                  url: https://old.example/ping
                  max-failed-times: 4
                override:
                  udp: true
                  custom-field: keep
        """.trimIndent()

        val actual = ProfileProviderOverrideApplier.apply(
            yaml,
            ProfileProviderOverrides(
                mapOf("cloud" to ProxyProviderOverride(timeout = 2_000, udp = false)),
            ),
        )

        assertTrue(actual.contains("enable: true"))
        assertTrue(actual.contains("url: \"${com.lonnnnnng.flclash.core.CoreBridge.DEFAULT_TEST_URL}\""))
        assertTrue(actual.contains("max-failed-times: 4"))
        assertTrue(actual.contains("timeout: 2000"))
        assertTrue(actual.contains("udp: false"))
        assertTrue(actual.contains("custom-field: keep"))
    }

    @Test fun `mixed layout stays in metadata and never enters staged provider definitions`() {
        val option = ProxyProviderOverride(filter = "HK`JP", excludeFilter = "ads", memberLayout = listOf("excludeFilter", "filter"))
        val overrides = ProfileProviderOverrides(proxyProviders = mapOf("订阅:香港" to option))
        assertEquals(overrides, ProfileProviderOverrides.fromJsonString(overrides.toJsonString()))
        val source = "proxy-providers: {\"订阅:香港\": {type: file, path: cloud.yaml, custom: keep}}\nrules: [MATCH,DIRECT]\n"
        val staged = ProfileProviderOverrideApplier.apply(source, overrides)
        assertTrue(!staged.contains("memberLayout"))
        val provider = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(staged)).asJsonObject.getAsJsonObject("proxy-providers").getAsJsonObject("订阅:香港")
        assertEquals("HK`JP", provider["filter"].asString)
        assertEquals("ads", provider["exclude-filter"].asString)
        assertEquals("keep", provider["custom"].asString)
        assertEquals(source, ProfileProviderOverrideApplier.apply(source, ProfileProviderOverrides(proxyProviders = mapOf("订阅:香港" to ProxyProviderOverride(memberLayout = listOf("filter"))))))
    }

    @Test fun `group member layouts survive unrelated override edits without entering yaml`() {
        val source = "proxy-groups: [{name: Group, type: select, proxies: [DIRECT]}]\nrules: [MATCH,Group]\n"
        val original = ProfileProviderOverrides(groupMemberLayouts = mapOf("Group" to listOf("filter", "proxy")))
        val decoded = ProfileProviderOverrides.fromJsonString(original.toJsonString())
        assertEquals(original, decoded)
        assertEquals(original.groupMemberLayouts, ProfileProviderOverrides.fromJsonString(decoded.copy(dns = ProfileDnsOverrides(ipv6 = false)).toJsonString()).groupMemberLayouts)
        assertEquals(source, ProfileProviderOverrideApplier.apply(source, decoded))
    }

    @Test fun `health checks get default url for unicode quoted flow and canonical providers`() {
        for (source in listOf(
            "proxy-providers:\n  cloud:\n    type: file\n    health-check: {url: https://old.example, custom: keep}\n",
            "{proxy-providers: {cloud: {type: file, health-check: {url: https://old.example, custom: keep}}}}",
            "proxy-providers:\n  cloud:\n    type: file\n    health-check:\n      url: https://old.example\n      custom: keep\n",
            "'proxy-providers':\n  '中文':\n    type: file\n    health-check: {custom: keep}\n",
        )) {
            val root = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(source)).asJsonObject
            val name = root.getAsJsonObject("proxy-providers").keySet().single()
            val staged = ProfileProviderOverrideApplier.apply(source, ProfileProviderOverrides(proxyProviders = mapOf(name to ProxyProviderOverride(interval = 300, lazy = false, additionalPrefix = "true"))))
            val provider = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(staged)).asJsonObject.getAsJsonObject("proxy-providers").getAsJsonObject(name)
            assertEquals(com.lonnnnnng.flclash.core.CoreBridge.DEFAULT_TEST_URL, provider.getAsJsonObject("health-check")["url"].asString)
            assertEquals("keep", provider.getAsJsonObject("health-check")["custom"].asString)
            assertEquals(false, provider.getAsJsonObject("health-check")["lazy"].asBoolean)
            assertTrue(provider.getAsJsonObject("override")["additional-prefix"].asJsonPrimitive.isString)
        }
    }

    @Test
    fun `applies dns overrides to staged yaml and keeps unknown fields`() {
        val source = """
            mixed-port: 7890
            dns:
              enable: false
              nameserver:
                - https://old.example/dns-query
              custom-field: keep
            rules:
              - MATCH,DIRECT
        """.trimIndent() + "\n"

        val actual = ProfileProviderOverrideApplier.apply(
            source,
            ProfileProviderOverrides(
                dns = ProfileDnsOverrides(
                    enable = true,
                    enhancedMode = "fake-ip",
                    nameserver = listOf("https://doh.example/dns-query", "tls://1.1.1.1:853"),
                ),
            ),
        )

        assertTrue(actual.contains("enable: true"))
        assertTrue(actual.contains("enhanced-mode: fake-ip"))
        assertTrue(actual.contains("- https://doh.example/dns-query"))
        assertTrue(actual.contains("custom-field: keep"))
        assertTrue(actual.contains("rules:\n  - MATCH,DIRECT"))
    }

    @Test
    fun `appends dns section when profile has no dns block`() {
        val actual = ProfileProviderOverrideApplier.apply(
            "mixed-port: 7890\n",
            ProfileProviderOverrides(
                dns = ProfileDnsOverrides(nameserver = listOf("https://doh.example/dns-query")),
            ),
        )

        assertTrue(actual.endsWith("dns:\n  nameserver:\n    - https://doh.example/dns-query\n"))
    }

    @Test
    fun `applies dialer overrides to proxies and preserves unrelated yaml`() {
        val source = """
            mixed-port: 7890
            proxies:
              - name: node-a
                type: vmess
                server: old.example
                dialer-proxy: old-target
              - name: node-b
                type: trojan
            proxy-groups:
              - name: Relay
                type: select
                proxies:
                  - node-b
            rules:
              - MATCH,DIRECT
        """.trimIndent() + "\n"

        val actual = ProfileDialerOverrideApplier.apply(
            source,
            mapOf("node-a" to "Relay"),
        )

        assertTrue(actual.contains("server: old.example"))
        assertTrue(actual.contains("dialer-proxy: Relay"))
        assertTrue(!actual.contains("dialer-proxy: old-target"))
        assertTrue(actual.contains("node-b"))
        assertTrue(actual.contains("rules:\n  - MATCH,DIRECT"))
    }

    @Test
    fun `empty dialer override removes existing fields and rejects missing or circular targets`() {
        val source = """
            proxies:
              - name: node-a
                type: vmess
                dialer-proxy: node-b
              - name: node-b
                type: vmess
        """.trimIndent() + "\n"

        val cleared = ProfileDialerOverrideApplier.apply(source, emptyMap())
        assertTrue(!cleared.contains("dialer-proxy:"))

        val missing = runCatching {
            ProfileDialerOverrideApplier.apply(source, mapOf("node-a" to "missing"))
        }.exceptionOrNull()
        assertTrue(missing is IllegalArgumentException)

        val circular = runCatching {
            ProfileDialerOverrideApplier.apply(
                source,
                mapOf("node-a" to "node-b", "node-b" to "node-a"),
            )
        }.exceptionOrNull()
        assertTrue(circular is IllegalArgumentException)
    }

    @Test
    fun `dialer loop detection normalizes quoted inline proxy group members`() {
        val source = """
            proxies:
              - name: node-a
                type: vmess
              - name: node-b
                type: vmess
            proxy-groups:
              - name: Relay Group
                type: select
                proxies: ["node-b"]
        """.trimIndent() + "\n"

        val circular = runCatching {
            ProfileDialerOverrideApplier.apply(
                source,
                mapOf("node-a" to "Relay Group", "node-b" to "node-a"),
            )
        }.exceptionOrNull()

        assertTrue(circular is IllegalArgumentException)
    }
}
