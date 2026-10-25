package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    @Test
    fun `subscription userinfo is case insensitive and truncates decimals`() {
        val info = SubscriptionInfo.parseHeader(
            "Upload=12.9; DOWNLOAD=2e2; total=1024; expire=1700000000; ignored",
        )

        assertEquals(12, info.upload)
        assertEquals(200, info.download)
        assertEquals(1024, info.total)
        assertEquals(1_700_000_000, info.expire)
    }

    @Test
    fun `empty subscription info has no usage`() {
        val info = SubscriptionInfo.parseHeader(null)

        assertNull(info.usage)
        assertNull(info.expireAtMillis)
    }

    @Test
    fun `usage is capped at one`() {
        val info = SubscriptionInfo(upload = 80, download = 40, total = 100)

        assertEquals(1f, info.usage)
    }

    @Test
    fun `remote profile uses url host as fallback label`() {
        val profile = Profile.remote(1, "https://example.test/subscription")

        assertEquals("example.test", profile.realLabel)
        assertEquals("1.yaml", profile.fileName)
        assertTrue(profile.autoUpdate)
    }
}
