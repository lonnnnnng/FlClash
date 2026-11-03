package com.lonnnnnng.flclash.ui

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class ProfileMoreFieldsTest {
    @Test fun hostAndAddressMatchersFollowFlutterValidationWithoutResolvingDomains() {
        for (host in listOf("time.apple.com", "ntp_server", "2001:db8::1")) {
            assertEquals(host, parseMoreFieldsYaml("server: '$host'", true)["server"].asString)
        }
        for (host in listOf("time..test", "-invalid.test", "host:123", "https://time.test", "host.")) {
            assertTrue(runCatching { parseMoreFieldsYaml("server: '$host'", true) }.isFailure)
        }
        val addresses = parseMoreFieldsYaml("skip-src-address: ['192.0.2.0/24', '2001:db8::/32', 'geoip:cn', 'RULE-SET:local']", false)
        assertEquals(4, addresses["skip-src-address"].asJsonArray.size())
    }

    @Test fun yamlRoundTripKeepsFalseEmptyAndIntegerTypes() {
        val ntp = parseMoreFieldsYaml("enable: false\nport: 124\ninterval: 60\ndialer-proxy: ''", true)
        assertFalse(ntp["enable"].asBoolean)
        assertTrue(ntp["port"].asJsonPrimitive.isNumber)
        assertEquals("", ntp["dialer-proxy"].asString)
        assertEquals(ntp, parseMoreFieldsYaml(moreFieldsYaml(ntp), true))
        val sniffer = parseMoreFieldsYaml("enable: false\nskip-domain: []\nsniff: {}", false)
        assertEquals(0, sniffer["skip-domain"].asJsonArray.size())
        assertEquals(0, sniffer["sniff"].asJsonObject.size())
        assertEquals(sniffer, parseMoreFieldsYaml(moreFieldsYaml(sniffer), false))
    }

    @Test fun yamlNormalizesProtocolsAndNumericPortsAndNullRestoresInheritance() {
        val root = parseMoreFieldsYaml("enable: null\nsniff:\n  http:\n    ports: [80, 8000-9000]\n    override-destination: null", false)
        assertFalse(root.has("enable"))
        val http = root.getAsJsonObject("sniff").getAsJsonObject("HTTP")
        assertEquals(listOf("80", "8000-9000"), http.getAsJsonArray("ports").map { it.asString })
        assertFalse(http.has("override-destination"))
        assertEquals(JsonObject(), parseMoreFieldsYaml("# no overrides", false))
        assertEquals(JsonObject(), parseMoreFieldsYaml("", true))
    }

    @Test fun yamlRejectsUnknownNestedWrongTypesAndInvalidValues() {
        for ((yaml, ntp) in listOf("ntp: {port: 123}" to true, "port: '123'" to true,
            "enable: 'true'" to false, "skip-domain: invalid" to false, "skip-domain: [false]" to false,
            "server: https://example.test" to true, "port: 65536" to true, "interval: 0" to true,
            "sniff: {SMTP: {}}" to false, "sniff: {http: {}, HTTP: {}}" to false,
            "sniff: {HTTP: {ports: [9000-8000]}}" to false, "sniff: {TLS: {future: 1}}" to false,
            "sniff: {HTTP: {ports: [true]}}" to false, "skip-src-address: [example.test/32]" to false,
            "[broken" to false)) {
            assertTrue(yaml, runCatching { parseMoreFieldsYaml(yaml, ntp) }.isFailure)
        }
    }

    @Test fun addingUsesFlutterDefaultsAndRemovingLastProtocolRestoresInheritance() {
        val root = JsonObject()
        for (field in ntpFields) setMoreField(root, field, field.defaultValue())
        assertEquals(123, root["port"].asInt); assertEquals("time.apple.com", root["server"].asString)
        assertFalse(root["enable"].asBoolean)
        val protocol = snifferFields.single { it.key == "sniff.QUIC" }
        val sniff = JsonObject(); setMoreField(sniff, protocol, protocol.defaultValue())
        assertNotNull(moreFieldValue(sniff, protocol))
        setMoreField(sniff, protocol, null); assertFalse(sniff.has("sniff"))
    }
}
