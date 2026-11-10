package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSheetGeometryTest {
    @Test fun `floating form keeps minimum detent but caps expansion at measured content`() {
        val short = ConnectionSheetGeometry(1000f, 280f, minimumPartial = true)
        assertEquals(437.5f, short.offset(ConnectionSheetDetent.PARTIAL), .001f)
        assertEquals(short.offset(ConnectionSheetDetent.PARTIAL), short.offset(ConnectionSheetDetent.EXPANDED), .001f)
        val medium = ConnectionSheetGeometry(1000f, 700f, minimumPartial = true)
        assertEquals(300f, medium.offset(ConnectionSheetDetent.EXPANDED), .001f)
    }
    @Test fun `short content has one visible anchor and can still be flung closed`() {
        val geometry = ConnectionSheetGeometry(1000f, 280f)
        assertEquals(720f, geometry.offset(ConnectionSheetDetent.PARTIAL), 0.001f)
        assertEquals(listOf(ConnectionSheetDetent.PARTIAL, ConnectionSheetDetent.HIDDEN), geometry.detents)
        assertEquals(ConnectionSheetDetent.PARTIAL, geometry.projectedTarget(720f, -4000f))
        assertEquals(ConnectionSheetDetent.HIDDEN, geometry.projectedTarget(720f, 4000f))
    }

    @Test fun `content between two normal detents expands only to its natural height`() {
        val geometry = ConnectionSheetGeometry(1000f, 700f)
        assertTrue(geometry.hasExpandedDetent)
        assertEquals(437.5f, geometry.offset(ConnectionSheetDetent.PARTIAL), 0.001f)
        assertEquals(300f, geometry.offset(ConnectionSheetDetent.EXPANDED), 0.001f)
        assertEquals(ConnectionSheetDetent.EXPANDED, geometry.projectedTarget(400f, -3000f))
        assertEquals(100f, ConnectionSheetGeometry(1000f, 5000f).offset(ConnectionSheetDetent.EXPANDED), 0.001f)
    }

    @Test fun `invalid measured content cannot poison drag anchors`() {
        for (height in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { ConnectionSheetGeometry(1000f, height) }
        }
    }

    @Test
    fun `superellipse corner starts on the side and ends on the top`() {
        val points = superellipseQuarterPoints(radius = 28f, exponent = 4f, samples = 20)
        assertEquals(0f, points.first().x, 0.001f)
        assertEquals(28f, points.first().y, 0.001f)
        assertEquals(28f, points.last().x, 0.001f)
        assertEquals(0f, points.last().y, 0.001f)
        points.zipWithNext().forEach { (previous, next) ->
            assertTrue("x must progress toward the top edge", next.x >= previous.x)
            assertTrue("y must progress toward the side edge", next.y <= previous.y)
            assertTrue(next.x in 0f..28f && next.y in 0f..28f)
        }
    }

    @Test
    fun `sheet motion keeps the Flutter projection and critical spring constants`() {
        assertEquals(0.099f, ConnectionSheetMotion.projectionTimeSeconds, 0.0001f)
        assertEquals(333.33334f, ConnectionSheetMotion.springStiffness, 0.001f)
        assertEquals(28f, ConnectionSheetMotion.cornerRadiusDp, 0.001f)
        assertTrue(ConnectionSheetMotion.settleSpec().dampingRatio >= 0.999f)
        assertEquals(300, ConnectionSheetMotion.sideEnterDurationMillis)
        assertEquals(200, ConnectionSheetMotion.sideExitDurationMillis)
    }

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
