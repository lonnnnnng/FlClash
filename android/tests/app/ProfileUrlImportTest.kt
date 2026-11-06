package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileUrlImportTest {
    @Test fun singleInputAcceptsTrimmedHttpButRejectsOtherSchemesAndMultipleUrls() {
        assertNull(profileUrlInputError("  https://example.invalid/sub?token=fixture  "))
        assertNull(profileUrlInputError("http://example.invalid/sub"))
        val upper = "HTTPS://example.invalid/CaseSensitive?Token=AbCd"
        assertNull(profileUrlInputError(upper))
        assertEquals(upper, com.lonnnnnng.flclash.data.normalizeSubscriptionUrl(upper))
        for (value in listOf("file:///private/fixture", "https://", "not-a-url", "https://one.invalid https://two.invalid")) {
            assertEquals("请输入有效的 HTTP/HTTPS 订阅链接", profileUrlInputError(value))
        }
        assertEquals("请输入订阅链接", profileUrlInputError(" \n "))
    }

    @Test fun singleAndBatchShareUrlLengthBoundaryWithoutIncludingInputInError() {
        val prefix = "https://example.invalid/"
        val exact = prefix + "a".repeat(2048 - prefix.length)
        assertNull(profileUrlInputError(exact))
        assertEquals("订阅链接不能超过 2048 个字符", profileUrlInputError(exact + "x"))
        val preview = parseProfileUrlImport("$exact\n${exact}x", emptySet())
        assertEquals(listOf(exact), preview.urls)
        assertEquals(1, preview.invalidCount)
    }
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
