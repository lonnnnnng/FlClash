package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentGroupIconsTest {
    @Test fun matchingRecentImagesLeadWithoutDuplicatingOtherRecentChoices() {
        val recent = listOf(
            "https://example.org/YouTube.svg",
            "https://example.org/Other.svg",
            "https://example.org/youtube-premium.png",
            "data:image/png;base64,AAAA",
            "https://example.org/YouTube.svg",
        )

        val suggested = RecentGroupIcons.suggested(recent, "YouTube")

        assertEquals(listOf(
            "YouTube.svg",
            "youtube-premium.png",
            "Other.svg",
            "",
        ), suggested.map { it.name })
        assertEquals(4, suggested.map { it.url }.distinct().size)
    }

    @Test fun unnamedGroupKeepsRecentImageOrderAndLabelsEmbeddedPictures() {
        val recent = listOf("https://example.org/one.png", "https://example.org/two.svg")

        val suggested = RecentGroupIcons.suggested(recent, "")

        assertEquals(listOf("one.png", "two.svg"), suggested.map { it.name })
        assertEquals("本地图片", RecentGroupIcons.label(""))
    }
}
