package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ProfileMoreOverridesTest {
    @Test fun overridingOneProtocolRetainsDeprecatedProtocolsAndTheirPorts() {
        val source = "sniffer: {sniffing: [http, tls], port-whitelist: [80, 443]}\n"
        val updated = ProfileMoreOverrideApplier.apply(source, ProfileNtpOverrides(), ProfileSnifferOverrides(
            sniff = mapOf("HTTP" to ProfileSniffProtocolOverrides(listOf("8080")))))
        val protocols = JsonParser.parseString(YamlConfigCodec.toJson(updated)).asJsonObject
            .getAsJsonObject("sniffer").getAsJsonObject("sniff")
        assertEquals(setOf("HTTP", "TLS"), protocols.keySet())
        assertEquals(listOf("80", "443"), protocols.getAsJsonObject("TLS").getAsJsonArray("ports").map { it.asString })
        assertEquals(listOf("8080"), protocols.getAsJsonObject("HTTP").getAsJsonArray("ports").map { it.asString })
    }

    @Test fun protocolOverrideReplacesCaseAliasesAndKeepsOtherProtocols() {
        val source = "sniffer: {sniff: {http: {ports: [80], custom: keep}, HTTP: {ports: [81]}, tls: {ports: [443]}}}\n"
        val yaml = ProfileMoreOverrideApplier.apply(source, ProfileNtpOverrides(), ProfileSnifferOverrides(
            sniff = mapOf("HTTP" to ProfileSniffProtocolOverrides(listOf("8080"), false))))
        val protocols = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject.getAsJsonObject("sniffer").getAsJsonObject("sniff")
        assertEquals(setOf("HTTP", "tls"), protocols.keySet())
        assertEquals("8080", protocols.getAsJsonObject("HTTP").getAsJsonArray("ports")[0].asString)
        assertEquals("keep", protocols.getAsJsonObject("HTTP")["custom"].asString)
        assertFalse(protocols.getAsJsonObject("HTTP")["override-destination"].asBoolean)
    }

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
        assertFalse(sniffer.has("skip-domain"))
        assertEquals("old", sniffer.getAsJsonObject("sniff").getAsJsonObject("HTTP").get("custom").asString)
        assertEquals("8443", sniffer.getAsJsonObject("sniff").getAsJsonObject("TLS").getAsJsonArray("ports")[0].asString)
    }

    @Test fun emptyValuesStayListedButInheritSourceAndKeepOriginalBytes() {
        val source = "sniffer: {sniff: {HTTP: {ports: [80]}}, sniffing: [http, tls]}\n"
        assertEquals(source, ProfileProviderOverrideApplier.apply(source, ProfileProviderOverrides()))
        val empty = ProfileProviderOverrides(ntp = ProfileNtpOverrides(dialerProxy = ""),
            sniffer = ProfileSnifferOverrides(skipDomain = emptyList(), sniff = emptyMap()))
        assertEquals(source, ProfileProviderOverrideApplier.apply(source, empty))
        assertTrue(empty.ntp.toJson().has("dialer-proxy")); assertTrue(empty.sniffer.toJson().has("skip-domain"))
        assertTrue(empty.ntp.effectiveJson().size() == 0); assertTrue(empty.sniffer.effectiveJson().size() == 0)
        val changed = ProfileProviderOverrideApplier.apply(source, empty.copy(sniffer = empty.sniffer.copy(enable = false)))
        val section = JsonParser.parseString(YamlConfigCodec.toJson(changed)).asJsonObject.getAsJsonObject("sniffer")
        assertFalse(section["enable"].asBoolean)
        assertTrue(section.getAsJsonObject("sniff").has("HTTP")); assertEquals(2, section.getAsJsonArray("sniffing").size())
    }
}
