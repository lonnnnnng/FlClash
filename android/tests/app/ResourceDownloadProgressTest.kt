package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceDownloadProgressTest {
    @Test fun byteReportsAreThrottledAndKeepUnknownOrInvalidTotalsIndeterminate() {
        var now = 0L
        val updates = mutableListOf<ResourceDownloadProgress>()
        val reporter = ResourceProgressReporter(100, updates::add) { now }
        reporter.read(10)
        assertEquals(1, updates.size)
        now += 200_000_000L
        reporter.read(15)
        assertEquals(2, updates.size)
        assertEquals(25L, updates.last().downloadedBytes)
        assertEquals(.25f, updates.last().fraction!!, .001f)
        reporter.finish()
        assertEquals(25L, updates.last().downloadedBytes)

        val unknown = ResourceProgressReporter(-1, updates::add) { now }
        unknown.read(9)
        unknown.finish()
        assertNull(updates.last().totalBytes)
        assertNull(updates.last().fraction)

        val overrun = ResourceProgressReporter(5, updates::add) { now }
        overrun.read(6)
        overrun.finish()
        assertNull(updates.last().totalBytes)
        assertTrue(updates.last().downloadedBytes == 6L)
    }
}
