package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import org.junit.Assert.*
import org.junit.Test

class CustomProfileSummaryTest {
    @Test fun referencesAreDeduplicatedAndMissingResourcesStayVisible() {
        val yaml = """
            proxies:
              - {name: local, type: direct, dialer-proxy: DIRECT}
            proxy-groups:
              - {name: main, type: select, proxies: [local], use: [cloud, missing]}
              - {name: auto, type: url-test, use: [cloud]}
            proxy-providers:
              cloud: {type: file, path: cloud.yaml}
              unused: {type: file, path: unused.yaml}
            rule-providers:
              sites: {type: file, path: sites.yaml}
            rules:
              - RULE-SET,sites,main
              - RULE-SET,sites,auto
              - RULE-SET,absent,DIRECT
              - MATCH,main
            dns: {enable: true}
            ntp: {enable: true}
        """.trimIndent()
        val result = customProfileSummary(yaml, Profile.custom(1, "fixture"))
        assertFalse(result.unreadable)
        assertEquals(4, result.rules)
        assertEquals(4, result.referencedProviders)
        assertEquals(2, result.missingProviders)
        assertEquals(1, result.proxies)
        assertEquals(listOf("main", "auto"), result.groups.map { it.name })
        assertEquals(2, result.groups.first().resources)
        assertTrue(result.dnsEnabled)
        assertEquals(2, result.moreInEffect)
    }

    @Test fun explicitOverridesReplaceSourceStatusAndIgnoreMissingDialerKeys() {
        val profile = Profile.custom(2, "fixture").copy(overridesJson = ProfileProviderOverrides(
            dns = ProfileDnsOverrides(enable = false), dialers = emptyMap(),
        ).toJsonString())
        val yaml = "proxies: [{name: local, type: direct, dialer-proxy: DIRECT}]\ndns: {enable: true}\n"
        val cleared = customProfileSummary(yaml, profile)
        assertFalse(cleared.dnsEnabled)
        assertEquals(0, cleared.dialers)
        val updated = customProfileSummary(yaml, profile.copy(overridesJson = ProfileProviderOverrides(
            dialers = mapOf("local" to "DIRECT", "missing" to "DIRECT"),
        ).toJsonString()))
        assertEquals(1, updated.dialers)
    }

    @Test fun emptyDraftAndMalformedConfigHaveDifferentStates() {
        val profile = Profile.custom(3, "fixture")
        assertEquals(CustomProfileSummary(), customProfileSummary("", profile))
        assertTrue(customProfileSummary("rules: [", profile).unreadable)
        assertTrue(customProfileSummary("- not-a-config", profile).unreadable)
    }

    @Test fun globalReferencesCountOnlyProjectedNodesAndKeepDialerOverrides() {
        val node = com.lonnnnnng.flclash.data.LocalProxy(json = """{"name":"A","type":"direct"}""")
        val unused = com.lonnnnnng.flclash.data.LocalProxy(json = """{"name":"B","type":"direct"}""")
        val profile = Profile.custom(4, "global").copy(overridesJson = ProfileProviderOverrides(dialers = mapOf("A" to "DIRECT")).toJsonString())
        val summary = customProfileSummary("proxy-groups: [{name: Main, type: select, proxies: [A]}]", profile, localProxies = listOf(node, unused))
        assertFalse(summary.unreadable)
        assertEquals(1, summary.proxies)
        assertEquals(1, summary.dialers)
    }
}
