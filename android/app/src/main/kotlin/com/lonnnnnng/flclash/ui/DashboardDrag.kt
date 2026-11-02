package com.lonnnnnng.flclash.ui

import kotlin.math.abs

internal data class DashboardDragItem(val section: DashboardSection, val top: Int, val height: Int)

internal fun dashboardDragTarget(
    items: List<DashboardDragItem>,
    currentOrder: List<DashboardSection>,
    centerY: Float,
): DashboardSection? {
    val shown = items.map { it.section }
    // 作者 long：排序写入后 LazyColumn 要到下一次布局才更新位置，旧坐标不能再次触发反向交换。
    if (shown != currentOrder.filter(shown::contains)) return null
    return items.minByOrNull { abs(it.top + it.height / 2f - centerY) }?.section
}

internal fun dashboardDragScrollDelta(
    pointerY: Float,
    viewportStart: Int,
    viewportEnd: Int,
    density: Float,
    elapsedMillis: Float,
): Float {
    if (!pointerY.isFinite() || !density.isFinite() || density <= 0f ||
        !elapsedMillis.isFinite() || elapsedMillis <= 0f || viewportEnd <= viewportStart
    ) return 0f
    val edge = minOf(56f * density, (viewportEnd - viewportStart) / 2f)
    val penetration = when {
        pointerY < viewportStart + edge -> -((viewportStart + edge - pointerY) / edge).coerceAtMost(1f)
        pointerY > viewportEnd - edge -> ((pointerY - viewportEnd + edge) / edge).coerceAtMost(1f)
        else -> 0f
    }
    // 作者 long：按时间和密度计算速度，停住手指仍持续滚动；长帧只推进最多 32ms，避免恢复时突然跳屏。
    return penetration * 640f * density * elapsedMillis.coerceAtMost(32f) / 1_000f
}
