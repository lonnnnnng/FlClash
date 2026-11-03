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
        assertEquals(10, GlobalDnsSettings.keys.size)
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
