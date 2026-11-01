package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUrlImportTest {
    @Test
    fun parsesYamlLinesQuotedUrlsAndSkipsExistingAndRepeatedEntries() {
        val existing = "https://existing.invalid/sub"
        val first = "https://first.invalid/sub"
        val second = "https://second.invalid/sub"

        val preview = parseProfileUrlImport(
            "- '$first'\n\"$second\" $existing\n$first",
            setOf(existing),
        )

        assertEquals(listOf(first, second), preview.urls)
        assertEquals(1, preview.skippedExisting)
        assertEquals(0, preview.invalidCount)
    }

    @Test
    fun reportsInvalidItemsWithoutDiscardingValidPreview() {
        val preview = parseProfileUrlImport(
            "https://valid.invalid/sub\nfile:///tmp/config.yaml\nnot-a-url",
            emptySet(),
        )

        assertEquals(listOf("https://valid.invalid/sub"), preview.urls)
        assertEquals(2, preview.invalidCount)
        assertEquals(0, preview.skippedExisting)
    }

    @Test
    fun parsesMultipleIndividuallyQuotedUrlsOnOneLine() {
        val first = "https://first.invalid/sub"
        val second = "https://second.invalid/sub"

        val preview = parseProfileUrlImport("\"$first\" '$second'", emptySet())

        assertEquals(listOf(first, second), preview.urls)
        assertEquals(0, preview.invalidCount)
    }
}
