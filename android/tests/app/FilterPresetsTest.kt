package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class FilterPresetsTest {
    @Test fun presetPartsAppendWithoutRemovingFreeTextAndStopOfferingFullyAppliedPreset() {
        val preset = FilterPreset("地区", "HK`JP`HK")
        assertFalse(FilterPresets.isApplied(preset, "free`HK"))
        assertEquals("free`HK`JP", FilterPresets.apply(preset, "free``HK"))
        assertTrue(FilterPresets.isApplied(preset, "JP`free`HK"))
        assertFalse(FilterPresets.isApplied(FilterPreset("空", "``"), ""))
    }
    @Test fun catalogKeepsOrderAndRejectsDuplicateLabelsAndMalformedArchiveFields() {
        val rows = listOf(FilterPreset("回溯", "(?<=foo)bar"), FilterPreset("地区", "HK`JP"))
        assertEquals(rows, FilterPresets.decode(FilterPresets.encode(rows)))
        listOf("null", "{}", "[{\"label\":\"A\",\"regex\":null}]", "[{\"label\":\"A\",\"regex\":\"``\"}]").forEach {
            assertTrue(runCatching { FilterPresets.decode(it) }.isFailure)
        }
        assertTrue(runCatching { FilterPresets.encode(rows + rows.first()) }.isFailure)
    }
    @Test fun urlBatchValidatesAllInputsAndNamesOnlySingleDistinctUrl() {
        val single = NamedResourceUrls.parse("https://example.com/a.yaml\nhttps://example.com/a.yaml", " 我的规则 ")
        assertEquals(listOf(NamedResourceUrl("https://example.com/a.yaml", "我的规则")), single)
        val multi = NamedResourceUrls.parse("https://example.com/a.yaml\n https://example.com/b.json?secret=redacted", "ignored")
        assertTrue(multi.all { it.label.isEmpty() })
        assertEquals(listOf("a", "b"), multi.map { it.suggestedName("资源") })
        listOf("https://example.com/good\nfile:///bad", "https://user:password@example.com/a", "https://example.com/a#b", "", (1..129).joinToString("\n") { "https://example.com/$it" }).forEach {
            assertTrue(runCatching { NamedResourceUrls.parse(it) }.isFailure)
        }
    }
    @Test fun resourcePreferenceRoundTripAndMergePreserveExistingIconsAndOverrideMatchingFilters() {
        val icon = ResourceIconSet("one", "图库", icons = listOf(ResourceIcon("A", "https://example.com/a.svg")), updatedAt = 1)
        val old = ResourcePreferences(listOf(icon), listOf(FilterPreset("地区", "HK"), FilterPreset("自定义", "custom")), com.lonnnnnng.flclash.service.GeoResourceSettings())
        val incoming = ResourcePreferences(listOf(icon.copy(id = "two")), listOf(FilterPreset("地区", "JP")), old.geo.copy(intervalHours = 72))
        assertEquals(incoming, ResourcePreferencesCodec.decode(ResourcePreferencesCodec.encode(incoming)))
        val merged = ResourcePreferencesCodec.merge(old, incoming)
        assertEquals(listOf("图库", "图库 (2)"), merged.icons.map { it.name })
        assertEquals(listOf(FilterPreset("自定义", "custom"), FilterPreset("地区", "JP")), merged.filters)
        assertEquals(72, merged.geo.intervalHours)
        assertTrue(runCatching { ResourcePreferencesCodec.decode("{\"icons\":[],\"filters\":[]}") }.isFailure)
        assertTrue(runCatching { IconSetCodec.decode("[{\"id\":null}]") }.isFailure)
    }
}
