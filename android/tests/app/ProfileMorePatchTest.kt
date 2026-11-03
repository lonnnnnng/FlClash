package com.lonnnnnng.flclash.data

import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.*
import org.junit.Assert.*
import org.junit.Test

class ProfileMorePatchTest {
    @Test fun portOnlyPatchKeepsLatestDestinationAndUnknownProtocolFields() {
        val before = ProfileSnifferOverrides(sniff = mapOf("HTTP" to ProfileSniffProtocolOverrides(listOf("80"), false)))
        val after = before.copy(sniff = mapOf("HTTP" to ProfileSniffProtocolOverrides(listOf("8080"), false)))
        val json = JsonParser.parseString(ProfileMorePatch(ProfileNtpOverrides(), ProfileNtpOverrides(), before, after)
            .applyTo("""{"sniffer":{"sniff":{"HTTP":{"ports":["80"],"override-destination":true,"future":"keep"}}}}""")).asJsonObject
        val http = json.getAsJsonObject("sniffer").getAsJsonObject("sniff").getAsJsonObject("HTTP")
        assertTrue(http["override-destination"].asBoolean)
        assertEquals("keep", http["future"].asString)
        assertEquals("8080", http.getAsJsonArray("ports")[0].asString)
    }

    @Test fun fieldPatchKeepsLatestOtherSectionMetadataAndUnknownKeys() {
        val before = ProfileNtpOverrides(enable = true, port = 123)
        val current = """{"dns":{"enable":false},"ntp":{"enable":true,"port":124,"server":"latest.test","future":1},"sniffer":{"enable":true},"future-root":{"x":1}}"""
        val updated = JsonParser.parseString(ProfileMorePatch(before, before.copy(enable = false), ProfileSnifferOverrides(), ProfileSnifferOverrides()).applyTo(current)).asJsonObject
        assertFalse(updated.getAsJsonObject("ntp")["enable"].asBoolean)
        assertEquals(124, updated.getAsJsonObject("ntp")["port"].asInt)
        assertEquals("latest.test", updated.getAsJsonObject("ntp")["server"].asString)
        assertEquals(1, updated.getAsJsonObject("ntp")["future"].asInt)
        assertTrue(updated.getAsJsonObject("sniffer")["enable"].asBoolean)
        assertFalse(updated.getAsJsonObject("dns")["enable"].asBoolean)
        assertTrue(updated.has("future-root"))
    }

    @Test fun removingOnlyKnownProtocolLeavesConcurrentProtocolAndUnknownValues() {
        val before = ProfileSnifferOverrides(sniff = mapOf("HTTP" to ProfileSniffProtocolOverrides(listOf("80"))))
        val current = """{"sniffer":{"sniff":{"HTTP":{"ports":["80"]},"TLS":{"ports":["443"]}},"future":true},"ntp":{"port":124}}"""
        val updated = JsonParser.parseString(ProfileMorePatch(ProfileNtpOverrides(), ProfileNtpOverrides(), before, ProfileSnifferOverrides()).applyTo(current)).asJsonObject
        val sniffer = updated.getAsJsonObject("sniffer")
        assertEquals(setOf("TLS"), sniffer.getAsJsonObject("sniff").keySet())
        assertTrue(sniffer["future"].asBoolean); assertEquals(124, updated.getAsJsonObject("ntp")["port"].asInt)
        val empty = ProfileMorePatch(ProfileNtpOverrides(port = 123), ProfileNtpOverrides(), ProfileSnifferOverrides(), ProfileSnifferOverrides()).applyTo("""{"ntp":{"port":123}}""")
        assertFalse(JsonParser.parseString(empty).asJsonObject.has("ntp"))
    }
}
