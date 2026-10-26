package com.lonnnnnng.flclash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProviderOverridesTest {
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
                      url: https://example.com/ping
                      interval: 300
                      timeout: 5000
                      lazy: true
                      expected-status: 200
                    override:
                      additional-prefix: Cloud-
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
        assertTrue(actual.contains("url: https://old.example/ping"))
        assertTrue(actual.contains("max-failed-times: 4"))
        assertTrue(actual.contains("timeout: 2000"))
        assertTrue(actual.contains("udp: false"))
        assertTrue(actual.contains("custom-field: keep"))
    }
}
