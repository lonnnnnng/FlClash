package com.lonnnnnng.flclash.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.*

/** 作者 long：移植上游goo.dart的距离场与射线桥接，只补两轮廓接近处，远端仍保留真实连续圆角。 */
internal fun appMenuGooPath(a: Rect, radius: Float, b: Rect, blend: Float, tolerance: Float = .05f): Path {
    fun contour(rect: Rect, r: Float) = superellipsePath(rect.size, List(4) { r }).apply { translate(rect.topLeft) }
    fun distance(rect: Rect, r: Float, point: Offset): Float {
        val round = min(r, rect.size.minDimension / 2)
        val x = abs(point.x - rect.center.x) - rect.width / 2 + round
        val y = abs(point.y - rect.center.y) - rect.height / 2 + round
        return hypot(max(x, 0f), max(y, 0f)) + min(max(x, y), 0f) - round
    }
    fun depth(rect: Rect, r: Float, other: Rect) = -listOf(other.topLeft, other.topRight, other.bottomLeft, other.bottomRight).maxOf { distance(rect, r, it) } - blend
    val platter = contour(a, radius)
    if (b.isEmpty || depth(a, radius, b) >= 0) return platter
    val pill = contour(b, b.size.minDimension / 2)
    if (depth(b, b.size.minDimension / 2, a) >= 0) return pill
    val both = Path.combine(PathOperation.Union, platter, pill)
    if (blend <= 0f) return both
    fun smooth(x: Float, y: Float): Float {
        val h = (.5f + .5f * (y - x) / blend).coerceIn(0f, 1f)
        return y + (x - y) * h - blend * h * (1f - h)
    }
    fun field(point: Offset) = smooth(distance(a, radius, point), distance(b, b.size.minDimension / 2, point))
    val ends = arrayOfNulls<Offset>(128)
    val depths = FloatArray(128)
    fun cast(turn: Int) {
        val angle = turn * 2f * PI.toFloat() / 128
        val direction = Offset(cos(angle), sin(angle))
        val limit = b.size.maxDimension / 2 + a.size.maxDimension + blend
        var inside = 0f; var at = 0f
        while (at < limit) {
            val point = b.center + direction * at
            val toA = distance(a, radius, point)
            if (toA < -blend) { inside = at; break }
            val d = smooth(toA, distance(b, b.size.minDimension / 2, point))
            if (d >= 0) {
                var outside = at
                repeat(4) {
                    val middle = (inside + outside) / 2
                    if (field(b.center + direction * middle) < 0) inside = middle else outside = middle
                }
                break
            }
            inside = at
            at += max(.5f, min(-d, toA + blend))
        }
        val end = b.center + direction * inside
        ends[turn] = end
        depths[turn] = distance(a, radius, end)
    }
    fun reach(turn: Int) = when {
        depths[turn % 128] < -blend -> -1
        depths[turn % 128] < blend -> 0
        else -> 1
    }
    fun skip(from: Int, to: Int) {
        for (turn in from + 1 until to) if (ends[turn] == null) depths[turn] = depths[from]
    }
    fun refine(from: Int, to: Int) {
        if (to - from < 2) return
        val alike = reach(from) == reach(to)
        if (alike && reach(from) != 0) { skip(from, to); return }
        val middle = (from + to) / 2
        cast(middle)
        if (alike && reach(middle) == reach(from) &&
            menuGooOffChord(checkNotNull(ends[from]), checkNotNull(ends[to % 128]), checkNotNull(ends[middle])) <= tolerance) {
            skip(from, to); return
        }
        refine(from, middle)
        refine(middle, to)
    }
    // 作者 long：沿固定Flutter goo.dart先每四条射线取一个种子；仅在交界或弦误差超限处补点，避免每帧算满128条。
    for (turn in 0 until 128 step 4) cast(turn)
    for (turn in 0 until 128 step 4) refine(turn, turn + 4)
    val flows = BooleanArray(128) { depths[it] < blend }
    if (flows.none { it }) return both
    val bridge = Path()
    val fan = mutableListOf<Int>()
    fun closeFan() {
        if (fan.size > 1) {
            val rim = fan.mapIndexedNotNull { index, turn ->
                ends[turn]?.takeIf { index == 0 || index == fan.lastIndex ||
                    depths[fan[index - 1]] >= -blend || depths[turn] >= -blend || depths[fan[index + 1]] >= -blend }
            }
            bridge.moveTo(b.center.x, b.center.y)
            menuGooSimplify(rim, tolerance).forEach { bridge.lineTo(it.x, it.y) }
            bridge.close()
        }
        fan.clear()
    }
    val first = flows.indexOfFirst { !it }.coerceAtLeast(0)
    repeat(129) { index ->
        val turn = (first + index) % 128
        if (flows[turn]) fan.add(turn) else closeFan()
    }
    closeFan()
    return Path.combine(PathOperation.Union, both, bridge)
}

private fun menuGooOffChord(from: Offset, to: Offset, point: Offset): Float {
    val chord = to - from
    val offset = point - from
    val length = chord.getDistance()
    return if (length == 0f) offset.getDistance() else abs(offset.x * chord.y - offset.y * chord.x) / length
}

/** 作者 long：桥接内侧冗余点按同一弦误差移除，减少Path合并和GPU三角化的输入，不改变主体连续圆角。 */
private fun menuGooSimplify(points: List<Offset>, tolerance: Float): List<Offset> {
    if (points.size < 3) return points
    val kept = BooleanArray(points.size).apply { this[0] = true; this[lastIndex] = true }
    fun split(from: Int, to: Int) {
        var farthest = 0f
        var at = -1
        for (index in from + 1 until to) {
            val off = menuGooOffChord(points[from], points[to], points[index])
            if (off > farthest) { farthest = off; at = index }
        }
        if (at >= 0 && farthest > tolerance) { kept[at] = true; split(from, at); split(at, to) }
    }
    split(0, points.lastIndex)
    return points.filterIndexed { index, _ -> kept[index] }
}
