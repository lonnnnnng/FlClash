package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.service.CoreRuntimeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTelemetryTest {
    @Test
    fun `runtime format keeps hours across days and caps extreme durations`() {
        assertEquals("—", formatRuntimeDuration(null))
        assertEquals("00:00:00", formatRuntimeDuration(0))
        assertEquals("00:00:00", formatRuntimeDuration(-1))
        assertEquals("01:01:01", formatRuntimeDuration(3_661_999))
        assertEquals("25:00:00", formatRuntimeDuration(25 * 3_600_000L))
        assertEquals("999:59:59", formatRuntimeDuration(Long.MAX_VALUE))
    }

    @Test
    fun `history keeps the latest thirty raw successful samples without differencing`() {
        var state = RuntimeStatsUiState(isLoading = true, error = "上次读取失败")
        for (value in 1L..45L) {
            state = state.withSample(CoreRuntimeStats(CoreTraffic(value, value * 2), CoreTraffic(100, 200), 3))
        }
        assertEquals((16L..45L).map { CoreTraffic(it, it * 2) }, state.trafficHistory)
        assertEquals(CoreTraffic(45, 90), state.traffic)
        assertEquals(CoreTraffic(100, 200), state.totalTraffic)
        assertEquals(3, state.connectionCount)
        assertEquals(false, state.isLoading)
        assertEquals(null, state.error)
        // 作者 long：失败只改变读取状态；绘图补点不能进入真实采样缓冲。
        val failed = state.copy(error = "读取失败")
        assertEquals(state.trafficHistory, failed.trafficHistory)
        assertEquals(emptyList<CoreTraffic>(), RuntimeStatsUiState().trafficHistory)
    }

    @Test
    fun `empty and idle charts remain zero and a lone sample is right aligned`() {
        assertEquals(List(30) { 0.0 }, dashboardTrafficHeights(emptyList()))
        assertEquals(List(30) { 0.0 }, dashboardTrafficHeights(listOf(-1, 0)))
        val single = dashboardTrafficHeights(listOf(8_192))
        assertEquals(List(25) { 0.0 }, single.take(25))
        assertTrue(single.last() > single[single.lastIndex - 1])
    }

    @Test
    fun `burst chart control points cannot imply negative speed or nonfinite values`() {
        val heights = dashboardTrafficHeights(listOf(0, Long.MAX_VALUE, 0))
        val slopes = dashboardTrafficSlopes(heights)
        heights.indices.forEach { index ->
            assertTrue(heights[index].isFinite())
            assertTrue(heights[index] - slopes[index] / 3 >= 0)
            assertTrue(heights[index] + slopes[index] / 3 >= 0)
        }
    }
}
