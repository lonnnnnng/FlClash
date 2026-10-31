package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConnectionSheetGeometryTest {
    @Test
    fun `detents follow the available viewport after resize`() {
        for (height in listOf(240f, 640f, 2160f)) {
            val geometry = ConnectionSheetGeometry(height)
            assertEquals(height * 9f / 16f, height - geometry.offset(ConnectionSheetDetent.PARTIAL), 0.001f)
            assertEquals(height * 0.9f, height - geometry.offset(ConnectionSheetDetent.EXPANDED), 0.001f)
        }
    }

    @Test
    fun `release projection distinguishes slow collapse from a closing fling`() {
        val geometry = ConnectionSheetGeometry(1000f)
        assertEquals(ConnectionSheetDetent.PARTIAL, geometry.projectedTarget(450f, 0f))
        assertEquals(ConnectionSheetDetent.EXPANDED, geometry.projectedTarget(450f, -3000f))
        assertEquals(ConnectionSheetDetent.HIDDEN, geometry.projectedTarget(450f, 4000f))
        assertEquals(ConnectionSheetDetent.EXPANDED, geometry.projectedTarget(-100f, 0f))
        assertEquals(ConnectionSheetDetent.HIDDEN, geometry.projectedTarget(1500f, 0f))
    }

    @Test
    fun `invalid viewport does not create undefined drag anchors`() {
        for (height in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { ConnectionSheetGeometry(height) }
        }
    }
}
