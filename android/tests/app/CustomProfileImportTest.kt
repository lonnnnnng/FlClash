package com.lonnnnnng.flclash.data

import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.ui.validateMoreFields
import org.junit.Assert.*
import org.junit.Test

class CustomProfileImportTest {
    @Test fun importRetainsCustomSectionsAndOmitsGlobalListenerSettings() {
        val content = customProfileImportContent("mixed-port: 7890\nntp: {enable: true}\nsniffer: {enable: false, custom: keep}\nrules: []\n".toByteArray())
        val json = JsonParser.parseString(YamlConfigCodec.toJson(content.content)).asJsonObject
        assertFalse(json.has("mixed-port"))
        assertTrue(json.getAsJsonObject("ntp").get("enable").asBoolean)
        assertEquals("keep", json.getAsJsonObject("sniffer").get("custom").asString)
    }
    @Test fun unrelatedOrWronglyTypedConfigIsRejected() {
        for (content in listOf("foo: bar", "rules: invalid", "ntp: 1", "[broken")) {
            assertTrue(runCatching { customProfileImportContent(content.toByteArray()) }.isFailure)
        }
    }
    @Test fun subRuleReferencesAreDroppedWithReportedCount() {
        val imported = customProfileImportContent("rules: ['SUB-RULE,(NETWORK,TCP),nested', 'MATCH,DIRECT']".toByteArray())
        assertEquals(1, imported.droppedSubRules)
        assertFalse(imported.content.contains("SUB-RULE"))
        assertTrue(imported.content.contains("MATCH,DIRECT"))
    }
    @Test fun moreValidationRejectsInvalidPortsAndCidrsAndCoercesIntegerText() {
        val ntp = JsonParser.parseString("""{"port":"123","interval":"30"}""").asJsonObject
        validateMoreFields(ntp, true)
        assertTrue(ntp.get("port").asJsonPrimitive.isNumber)
        for ((json, isNtp) in listOf("""{"port":"65536"}""" to true,
            """{"interval":"0"}""" to true, """{"skip-src-address":["example.com/32"]}""" to false,
            """{"sniff":{"HTTP":{"ports":["9000-8000"]}}}""" to false)) {
            assertTrue(runCatching { validateMoreFields(JsonParser.parseString(json).asJsonObject, isNtp) }.isFailure)
        }
    }
}
