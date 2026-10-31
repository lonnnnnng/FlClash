package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ConnectionDetailFormattingTest {
    @Test
    fun `UTC and offset timestamps display the same local second`() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        assertEquals("2026-10-28 08:00:00", formatConnectionStart("2026-10-28T00:00:00Z", zone))
        assertEquals("2026-10-28 08:00:00", formatConnectionStart("2026-10-28T08:00:00+08:00", zone))
        assertEquals("2026-10-28 08:00:00", formatConnectionStart("2026-10-27T17:00:00-07:00", zone))
    }

    @Test
    fun `nanosecond precision does not overflow local seconds`() {
        val zone = TimeZone.getTimeZone("UTC")
        for (fraction in listOf("1", "123", "999999999")) {
            assertEquals("2026-10-28 00:00:00", formatConnectionStart("2026-10-28T00:00:00.${fraction}Z", zone))
        }
    }

    @Test
    fun `invalid timestamps remain readable without fabricated dates`() {
        for (raw in listOf("", "not-a-date", "2026-02-30T12:00:00Z", "2026-10-28T00:00:00Ztrailing")) {
            assertEquals(raw, formatConnectionStart(raw))
        }
    }

    @Test
    fun `process shows only a nonzero UID and handles missing process name`() {
        assertEquals("app(10000)", connectionProcessText("app", 10000))
        assertEquals("app", connectionProcessText(" app ", 0))
        assertEquals("(10000)", connectionProcessText("", 10000))
        assertEquals("", connectionProcessText("", 0))
    }
}
