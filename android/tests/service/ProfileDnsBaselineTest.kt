package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ProfileDnsBaselineTest {
    @Test fun missingDnsGetsFlutterBaselineWhileProxyIndentationIsPreserved() {
        val source = "mixed-port: 7890\nproxies:\n  - name: node-a\n    type: direct\n"
        val result = ProfileDnsBaselineApplier.apply(source)
        assertTrue(result.startsWith(source))
        val dns = json(result).getAsJsonObject("dns")
        assertTrue(dns["enable"].asBoolean)
        assertEquals("fake-ip", dns["enhanced-mode"].asString)
        assertEquals(listOf("https://doh.pub/dns-query", "https://dns.alidns.com/dns-query"),
            dns.getAsJsonArray("nameserver").map { it.asString })
    }

    @Test fun enabledDnsKeepsOriginalNameserversAndComments() {
        val source = "dns:\n  enable: true\n  nameserver: [system] # retain network policy\n"
        assertEquals(source, ProfileDnsBaselineApplier.apply(source))
    }

    @Test fun disabledInlineDnsGetsBaselineWithoutDroppingOtherFieldsOrSections() {
        val source = "mixed-port: 7890\ndns: {enable: false, use-hosts: false}\nproxies:\n  - name: node-a\n    type: direct\n"
        val result = ProfileDnsBaselineApplier.apply(source)
        assertTrue(json(result).getAsJsonObject("dns")["enable"].asBoolean)
        assertFalse(json(result).getAsJsonObject("dns")["use-hosts"].asBoolean)
        assertTrue(result.endsWith("proxies:\n  - name: node-a\n    type: direct\n"))
    }

    @Test fun quotedDnsKeyRetainsOtherFieldsWithoutCreatingDuplicateSection() {
        val result = ProfileDnsBaselineApplier.apply("'dns':\n  enable: false\n  use-hosts: false\nrules: [MATCH,DIRECT]\n")
        assertTrue(json(result).getAsJsonObject("dns")["enable"].asBoolean)
        assertFalse(json(result).getAsJsonObject("dns")["use-hosts"].asBoolean)
        assertTrue(result.endsWith("rules: [MATCH,DIRECT]\n"))
    }

    @Test fun explicitProfileDnsOverridesStillWinAfterBaseline() {
        val result = ProfileProviderOverrideApplier.apply(ProfileDnsBaselineApplier.apply("mixed-port: 7890\n"),
            ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false, nameserver = listOf("system"))))
        val dns = json(result).getAsJsonObject("dns")
        assertFalse(dns["enable"].asBoolean)
        assertEquals("system", dns.getAsJsonArray("nameserver")[0].asString)
    }

    private fun json(yaml: String) = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
}
