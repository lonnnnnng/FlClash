package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class IconSetCodecTest {
    @Test fun quantumultGalleryFiltersInvalidItemsDeduplicatesAndKeepsNames() {
        val (name, icons) = IconSetCodec.parse("""{"name":"  图标集  ","icons":[null,{},
            {"name":"  A  ","url":"https://example.com/a.svg"},
            {"name":"重复","url":"https://example.com/a.svg"},
            {"url":"https://example.com/b.png"},
            {"url":"file:///tmp/a.png"},{"url":"https://user:pass@example.com/a.png"}]}""")
        assertEquals("图标集", name)
        assertEquals(listOf("A", "b.png"), icons.map { it.name })
        assertEquals(2, icons.size)
    }
    @Test fun emptyOrWrongFormatIsRejectedBeforeReplacingExistingGallery() {
        listOf("[]", "{}", """{"icons":[{"url":"javascript:bad"}]}""").forEach {
            assertTrue(runCatching { IconSetCodec.parse(it) }.isFailure)
        }
    }
    @Test fun catalogRoundTripPreservesStableIdsOrderAndRemoteUrl() {
        val row = ResourceIconSet("stable", "Gallery", "https://example.com/gallery.json", listOf(ResourceIcon("A", "https://example.com/a.svg")), 24L)
        assertEquals(listOf(row), IconSetCodec.decode(IconSetCodec.encode(listOf(row))))
    }
}
