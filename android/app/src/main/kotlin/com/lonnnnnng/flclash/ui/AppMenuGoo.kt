package com.lonnnnnng.flclash.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.*

/** 作者 long：移植上游goo.dart的距离场与射线桥接，只补两轮廓接近处，远端仍保留真实连续圆角。 */
internal fun appMenuGooPath(a: Rect, radius: Float, b: Rect, blend: Float): Path {
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
    fun field(point: Offset): Float {
        val x = distance(a, radius, point); val y = distance(b, b.size.minDimension / 2, point)
        val h = (.5f + .5f * (y - x) / blend).coerceIn(0f, 1f)
        return y + (x - y) * h - blend * h * (1f - h)
    }
    val bridge = Path()
    var fan = mutableListOf<Offset>()
    fun closeFan() {
        if (fan.size > 1) {
            bridge.moveTo(b.center.x, b.center.y)
            fan.forEach { bridge.lineTo(it.x, it.y) }
            bridge.close()
        }
        fan = mutableListOf()
    }
    val ends = ArrayList<Pair<Offset, Boolean>>(128)
    repeat(128) { turn ->
        val angle = turn * 2f * PI.toFloat() / 128
        val direction = Offset(cos(angle), sin(angle))
        val limit = b.size.maxDimension / 2 + a.size.maxDimension + blend
        var inside = 0f; var at = 0f
        while (at < limit) {
            val point = b.center + direction * at
            val toA = distance(a, radius, point)
            if (toA < -blend) { inside = at; break }
            val d = field(point)
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
        ends += end to (distance(a, radius, end) < blend)
    }
    val first = ends.indexOfFirst { !it.second }.coerceAtLeast(0)
    repeat(129) { index ->
        val (point, flowing) = ends[(first + index) % 128]
        if (flowing) fan.add(point) else closeFan()
    }
    closeFan()
    return Path.combine(PathOperation.Union, both, bridge)
}
