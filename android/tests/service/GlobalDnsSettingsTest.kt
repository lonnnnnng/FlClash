package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GlobalDnsSettingsTest {
    @Test fun explicitEmptyAndFalseRoundTripWithoutBecomingInheritance() {
        val value = ProfileDnsOverrides(listen = "", ipv6 = false, nameserver = emptyList(), fakeIpFilterMode = "rule")
        assertEquals(value, ProfileDnsOverrides.fromJson(value.toJson()))
        assertFalse(value.isEmpty)
        GlobalDnsSettings.validate(value)
        assertEquals(15, GlobalDnsSettings.keys.size)
    }

    @Test fun additionalFlagsAndCacheRoundTripAndApplyWithoutDroppingFalse() {
        val value = ProfileDnsOverrides(useHosts = false, useSystemHosts = false, respectRules = false,
            preferH3 = true, cacheAlgorithm = "arc")
        assertFalse(value.isEmpty)
        assertEquals(value, ProfileDnsOverrides.fromJson(value.toJson()))
        val applied = GlobalDnsSettings.apply("dns: {use-hosts: true, cache-algorithm: lru, cache-max-size: 9}\nrules: ['MATCH,DIRECT']\n", value)
        val dns = JsonParser.parseString(YamlConfigCodec.toJson(applied)).asJsonObject.getAsJsonObject("dns")
        assertFalse(dns["use-hosts"].asBoolean); assertFalse(dns["use-system-hosts"].asBoolean)
        assertFalse(dns["respect-rules"].asBoolean); assertTrue(dns["prefer-h3"].asBoolean)
        assertEquals("arc", dns["cache-algorithm"].asString); assertEquals(9, dns["cache-max-size"].asInt)
        assertThrows(IllegalArgumentException::class.java) { GlobalDnsSettings.validate(value.copy(cacheAlgorithm = "unknown")) }
        assertTrue(ProfileDnsOverrides.fromJson(com.google.gson.JsonObject()).isEmpty)
    }

    @Test fun flowAndQuotedDnsPreserveUnknownFieldsAndOtherSections() {
        for (key in listOf("dns", "'dns'", "\"dns\"")) {
            val source = "# comment\n$key: {enable: true, nameserver: [1.1.1.1], cache-max-size: 123}\nproxies: []\nrules: ['MATCH,DIRECT']\n"
            val result = GlobalDnsSettings.apply(source, ProfileDnsOverrides(nameserver = emptyList(), ipv6 = false, fakeIpFilterMode = "whitelist"))
            val dns = JsonParser.parseString(YamlConfigCodec.toJson(result)).asJsonObject.getAsJsonObject("dns")
            assertEquals(123, dns.get("cache-max-size").asInt)
            assertEquals(0, dns.getAsJsonArray("nameserver").size())
            assertFalse(dns.get("ipv6").asBoolean)
            assertTrue(result.contains("rules: ['MATCH,DIRECT']"))
            assertTrue(result.startsWith("# comment"))
        }
    }

    @Test fun removingOverrideRestoresSourceAndBaselineStaysEnabled() {
        val source = "proxies: []\nrules: ['MATCH,DIRECT']\n"
        val base = ProfileDnsBaselineApplier.apply(source)
        val applied = GlobalDnsSettings.apply(base, ProfileDnsOverrides(enhancedMode = "redir-host", fakeIpFilter = emptyList()))
        val dns = JsonParser.parseString(YamlConfigCodec.toJson(applied)).asJsonObject.getAsJsonObject("dns")
        assertTrue(dns.get("enable").asBoolean)
        assertEquals("redir-host", dns.get("enhanced-mode").asString)
        assertEquals(base, GlobalDnsSettings.apply(base, ProfileDnsOverrides()))
    }

    @Test fun globalScopeRejectsProfileOnlyAndUnsupportedModes() {
        assertThrows(IllegalArgumentException::class.java) { GlobalDnsSettings.validate(ProfileDnsOverrides(enable = false)) }
        assertThrows(IllegalArgumentException::class.java) { GlobalDnsSettings.validate(ProfileDnsOverrides(enhancedMode = "hosts")) }
        assertThrows(IllegalArgumentException::class.java) { GlobalDnsSettings.validate(ProfileDnsOverrides(fakeIpFilterMode = "bad")) }
        assertThrows(IllegalArgumentException::class.java) { GlobalDnsSettings.validate(ProfileDnsOverrides(nameserver = listOf("a\nb"))) }
    }
}
