package com.lonnnnnng.flclash.ui

import org.junit.Assert.*
import org.junit.Test

class DashboardDragTest {
    @Test fun edgeScrollStopsInMiddleAndClampsOutsideViewport() {
        assertEquals(0f, dashboardDragScrollDelta(300f, 0, 600, 1f, 16f), 0f)
        val top = dashboardDragScrollDelta(0f, 0, 600, 1f, 16f)
        val bottom = dashboardDragScrollDelta(600f, 0, 600, 1f, 16f)
        assertTrue(top < 0f && bottom > 0f)
        assertEquals(-top, bottom, 0.001f)
        assertEquals(bottom, dashboardDragScrollDelta(900f, 0, 600, 1f, 16f), 0.001f)
        assertTrue(dashboardDragScrollDelta(560f, 0, 600, 1f, 16f) < bottom)
    }

    @Test fun elapsedTimeAndDensityDoNotChangeLogicalSpeedAndLongFramesAreBounded() {
        val normal = dashboardDragScrollDelta(600f, 0, 600, 1f, 16f)
        assertEquals(normal, dashboardDragScrollDelta(1_800f, 0, 1_800, 3f, 16f) / 3f, 0.001f)
        assertEquals(normal, dashboardDragScrollDelta(600f, 0, 600, 1f, 8f) * 2, 0.001f)
        assertEquals(normal * 2, dashboardDragScrollDelta(600f, 0, 600, 1f, 5_000f), 0.001f)
        assertEquals(0f, dashboardDragScrollDelta(Float.NaN, 0, 600, 1f, 16f), 0f)
        assertEquals(0f, dashboardDragScrollDelta(600f, 600, 600, 1f, 16f), 0f)
    }

    @Test fun staleListCoordinatesCannotReverseAStoredReorder() {
        val status = DashboardSection.CURRENT_OUTBOUND
        val speed = DashboardSection.NETWORK_STATS
        val mode = DashboardSection.OUTBOUND_MODE
        val old = listOf(DashboardDragItem(status, 0, 200), DashboardDragItem(speed, 200, 100))
        assertEquals(speed, dashboardDragTarget(old, listOf(status, speed, mode), 250f))
        assertNull(dashboardDragTarget(old, listOf(speed, status, mode), 250f))
        val applied = listOf(DashboardDragItem(speed, 0, 100), DashboardDragItem(status, 100, 200), DashboardDragItem(mode, 300, 100))
        assertEquals(mode, dashboardDragTarget(applied, listOf(speed, status, mode), 350f))
        assertNull(dashboardDragTarget(emptyList(), listOf(status), 50f))
    }
}
