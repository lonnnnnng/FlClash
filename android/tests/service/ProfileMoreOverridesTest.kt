package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ProfileMoreOverridesTest {
    @Test fun roundTripRetainsFalseEmptyAndProtocolInheritance() {
        val values = ProfileProviderOverrides(ntp = ProfileNtpOverrides(enable = false, dialerProxy = "", port = 123),
            sniffer = ProfileSnifferOverrides(enable = true, skipDomain = emptyList(),
                sniff = mapOf("TLS" to ProfileSniffProtocolOverrides(listOf("443", "8000-9000"), null))))
        assertEquals(values, ProfileProviderOverrides.fromJsonString(values.toJsonString()))
        val cleared = values.copy(sniffer = ProfileSnifferOverrides(sniff = emptyMap()))
        assertEquals(cleared, ProfileProviderOverrides.fromJsonString(cleared.toJsonString()))
    }

    @Test fun stagingPreservesUnknownFieldsAndOtherProtocols() {
        val source = """
            ntp: {enable: true, server: time.example, custom: keep}
            sniffer:
              enable: false
              custom: retained
              sniff:
                HTTP: {ports: [80], custom: old}
                TLS: {ports: [443]}
            rules: [MATCH,DIRECT]
        """.trimIndent()
        val updated = ProfileProviderOverrideApplier.apply(source, ProfileProviderOverrides(
            ntp = ProfileNtpOverrides(enable = false, port = 124),
            sniffer = ProfileSnifferOverrides(enable = true, skipDomain = emptyList(), sniff = mapOf("TLS" to ProfileSniffProtocolOverrides(listOf("8443"), false)))))
        val json = JsonParser.parseString(YamlConfigCodec.toJson(updated)).asJsonObject
        assertFalse(json.getAsJsonObject("ntp").get("enable").asBoolean)
        assertEquals("time.example", json.getAsJsonObject("ntp").get("server").asString)
        assertEquals("keep", json.getAsJsonObject("ntp").get("custom").asString)
        val sniffer = json.getAsJsonObject("sniffer")
        assertTrue(sniffer.get("enable").asBoolean)
        assertEquals("retained", sniffer.get("custom").asString)
        assertEquals(0, sniffer.getAsJsonArray("skip-domain").size())
        assertEquals("old", sniffer.getAsJsonObject("sniff").getAsJsonObject("HTTP").get("custom").asString)
        assertEquals("8443", sniffer.getAsJsonObject("sniff").getAsJsonObject("TLS").getAsJsonArray("ports")[0].asString)
    }

    @Test fun emptyOverridesReturnOriginalBytesAndEmptySniffClearsProtocols() {
        val source = "sniffer: {sniff: {HTTP: {ports: [80]}}}\n"
        assertEquals(source, ProfileProviderOverrideApplier.apply(source, ProfileProviderOverrides()))
        val updated = ProfileProviderOverrideApplier.apply(source, ProfileProviderOverrides(sniffer = ProfileSnifferOverrides(sniff = emptyMap())))
        assertEquals(0, JsonParser.parseString(YamlConfigCodec.toJson(updated)).asJsonObject.getAsJsonObject("sniffer").getAsJsonObject("sniff").size())
    }
}
