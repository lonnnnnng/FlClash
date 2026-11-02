package com.lonnnnnng.flclash.ui

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

internal const val DASHBOARD_TRAFFIC_CAPACITY = 30

internal fun formatRuntimeDuration(uptimeMillis: Long?): String {
    if (uptimeMillis == null) return "—"
    // 作者 long：与 Flutter 保持超过一天继续累计小时、超过 999 小时封顶的可读格式。
    val seconds = (uptimeMillis.coerceAtLeast(0L) / 1_000).coerceAtMost(999 * 3_600L + 59 * 60 + 59)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
}

internal fun dashboardTrafficHeights(samples: List<Long>): List<Double> {
    val recent = samples.takeLast(DASHBOARD_TRAFFIC_CAPACITY)
    val values = List(DASHBOARD_TRAFFIC_CAPACITY - recent.size) { 0.0 } + recent.map { it.coerceAtLeast(0L).toDouble() }
    // 作者 long：沿用 Flutter 的高斯平滑，左侧空位仅参与绘制，不作为真实采样保存或计数。
    return values.indices.map { index ->
        var sum = 0.0
        var weight = 0.0
        for (neighbor in (index - 4).coerceAtLeast(0)..(index + 4).coerceAtMost(values.lastIndex)) {
            val distance = abs(neighbor - index)
            val factor = exp(-distance * distance / (2 * 1.5 * 1.5))
            sum += values[neighbor] * factor
            weight += factor
        }
        sum / weight
    }
}

internal fun dashboardTrafficSlopes(heights: List<Double>): List<Double> = heights.indices.map { index ->
    // 作者 long：控制点限制在非负范围，稀疏突发流量平滑后不能画成负速度。
    if (index == 0 || index == heights.lastIndex) 0.0
    else ((heights[index + 1] - heights[index - 1]) / 2).coerceIn(-3 * heights[index], 3 * heights[index])
}
