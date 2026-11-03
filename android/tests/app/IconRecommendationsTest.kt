package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class IconRecommendationsTest {
    private fun set(id: String, vararg names: String) = ResourceIconSet(id, id, icons = names.map { ResourceIcon(it, "https://example.org/$it") }, updatedAt = 0)

    @Test fun `recommendations follow upstream exact contained and prefix ranking with stable ties`() {
        val library = set("A", "YouTube_Premium.png", "You.png", "YouTube.svg", "Tu.png", "YouTube_Music.svg")
        val sources = IconRecommendations.sources(listOf(library), listOf("https://example.org/recent.png"), "YouTube")
        assertEquals(listOf("YouTube.svg", "You.png", "YouTube_Music.svg", "YouTube_Premium.png", "recent.png"), sources.first().icons.map { it.name })
        assertEquals("YouTube Premium", IconRecommendations.label(library.icons.first()))
        assertEquals(library.icons, sources.last().icons)
    }

    @Test fun `non latin names do not invent matches and recent images remain deduplicated`() {
        val library = set("A", "香港.png", "HK.png")
        val local = "data:image/png;base64,AAAA"
        val sources = IconRecommendations.sources(listOf(library), listOf(local, local), "香港")
        assertEquals(listOf(local), sources.first().icons.map { it.url })
        assertEquals("本地图片", IconRecommendations.label(sources.first().icons.single()))
        assertEquals("set:A", IconRecommendations.initialKey(sources, library.icons.last().url))
        assertEquals(IconRecommendations.RECOMMENDED, IconRecommendations.initialKey(sources, ""))
        assertEquals("set:A", IconRecommendations.sources(listOf(library), emptyList(), "香港").single().key)
    }

    @Test fun `recommendation limit does not truncate galleries or recent tail and first matching url wins`() {
        val library = set("A", *(0..30).map { "YouTube_$it.png" }.toTypedArray())
        val duplicate = library.copy(id = "B", icons = listOf(ResourceIcon("YouTube.svg", library.icons.first().url)))
        val recent = "https://example.org/old.png"
        val sources = IconRecommendations.sources(listOf(library, duplicate), listOf(recent), "YouTube")
        assertEquals(25, sources.first().icons.size)
        assertEquals(recent, sources.first().icons.last().url)
        assertEquals(library.icons.first(), sources.first().icons.first())
        assertEquals(31, sources[1].icons.size)
        assertTrue(IconRecommendations.sources(emptyList(), emptyList(), "YouTube").isEmpty())
    }
}
