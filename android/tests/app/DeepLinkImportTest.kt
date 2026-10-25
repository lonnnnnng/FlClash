package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkImportTest {
    @Test
    fun `parses encoded subscription url and optional name`() {
        val result = DeepLinkProfileParser.parse(
            "kclash://install-config?url=https%3A%2F%2Fexample.com%2Fsub%3Ftoken%3Dabc%26x%3D1&name=工作配置",
        )

        assertEquals("https://example.com/sub?token=abc&x=1", result?.url)
        assertEquals("工作配置", result?.label)
    }

    @Test
    fun `accepts upstream and KClash protocol schemes`() {
        assertTrue(DeepLinkProfileParser.isSupported("clash://install-config?url=https%3A%2F%2Fexample.com"))
        assertTrue(DeepLinkProfileParser.isSupported("clashmeta://install-config?url=https%3A%2F%2Fexample.com"))
        assertTrue(DeepLinkProfileParser.isSupported("flclash://install-config?url=https%3A%2F%2Fexample.com"))
        assertTrue(DeepLinkProfileParser.isSupported("kclash://install-config?url=https%3A%2F%2Fexample.com"))
    }

    @Test
    fun `rejects unsupported host or non-http target`() {
        assertNull(
            DeepLinkProfileParser.parse(
                "kclash://other?url=https%3A%2F%2Fexample.com",
            ),
        )
        assertNull(
            DeepLinkProfileParser.parse(
                "kclash://install-config?url=file%3A%2F%2F%2Ftmp%2Fprofile.yaml",
            ),
        )
    }

    @Test
    fun `rejects missing or malformed subscription url`() {
        assertNull(DeepLinkProfileParser.parse("kclash://install-config"))
        assertNull(DeepLinkProfileParser.parse("kclash://install-config?url=not-a-url"))
        assertNull(DeepLinkProfileParser.parse("kclash://install-config?url=https%3A%2F%2F"))
    }

    @Test
    fun `accepts direct http profile urls from qr content`() {
        assertEquals(
            "https://example.com/profile.yaml",
            DeepLinkProfileParser.parseProfileUrl("  https://example.com/profile.yaml  "),
        )
    }

    @Test
    fun `normalizes deep link qr content through the same parser`() {
        assertEquals(
            "https://example.com/profile.yaml",
            DeepLinkProfileParser.parseProfileUrl(
                "clash://install-config?url=https%3A%2F%2Fexample.com%2Fprofile.yaml",
            ),
        )
    }
}
