package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GeoResourceSettingsTest {
    @Test fun globalGeoPatchPreservesDnsNodesAndUnknownFieldsWithoutChangingSource() {
        val source = "dns: {enable: true}\nproxies: [{name: 节点, type: direct}]\ncustom-flag: preserve\ngeo-auto-update: false\n"
        val next = GeoResourceSettings(autoUpdate = true, intervalHours = 48)
        val result = JsonParser.parseString(YamlConfigCodec.toJson(GeoResourceSettingsApplier.apply(source, next))).asJsonObject
        assertTrue(result["geo-auto-update"].asBoolean)
        assertEquals(48, result["geo-update-interval"].asInt)
        assertEquals("preserve", result["custom-flag"].asString)
        assertTrue(result.getAsJsonObject("dns")["enable"].asBoolean)
        assertEquals("节点", result.getAsJsonArray("proxies")[0].asJsonObject["name"].asString)
        assertEquals(4, result.getAsJsonObject("geox-url").size())
        assertTrue(source.contains("geo-auto-update: false"))
    }
    @Test fun invalidIntervalAndCredentialsAreRejectedAndMissingUrlsUseFlutterDefaults() {
        assertEquals(4, GeoResourceSettings(urls = emptyMap()).validated().urls.size)
        listOf(0, -1, 87601).forEach { assertTrue(runCatching { GeoResourceSettings(intervalHours = it).validated() }.isFailure) }
        listOf("file:///tmp/file", "https://user:pass@example.com/file", "http://", "https://example.com/file#secret").forEach {
            assertTrue(runCatching { GeoResourceSettings(urls = mapOf(GeoResourceType.MMDB to it)).validated() }.isFailure)
        }
    }
}
