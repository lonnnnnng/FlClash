// Copyright 2013 The Flutter Authors. All rights reserved.
// BSD license and attribution: assets/licenses/flutter-superellipse.txt.
package com.lonnnnnng.flclash.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** 作者 long：主页面直接沿用 Flutter 引擎的八分弧 Bézier 参数，避免普通圆角或固定四次曲线改变角部轮廓。 */
internal data class AppSuperellipseShape(
    val topStart: Dp = 0.dp,
    val topEnd: Dp = topStart,
    val bottomEnd: Dp = topEnd,
    val bottomStart: Dp = topStart,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.minDimension <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
        val logical = with(density) { listOf(topStart, topEnd, bottomEnd, bottomStart).map { it.toPx().coerceAtLeast(0f) } }
        val corners = if (layoutDirection == LayoutDirection.Ltr) logical else listOf(logical[1], logical[0], logical[3], logical[2])
        return Outline.Generic(superellipsePath(size, corners))
    }
}

private data class SuperellipseOctant(val offset: Offset, val a: Float, val n: Float,
    val start: Offset = Offset.Zero, val center: Offset = Offset.Zero, val angle: Float = 0f)

private val superellipseRatioTable = arrayOf(
    2.00000000f to 1.13276676f, 2.18349805f to 1.20311921f, 2.33888662f to 1.28698796f,
    2.48660575f to 1.36351941f, 2.62226596f to 1.44717976f, 2.75148990f to 1.53385819f,
    3.36298265f to 1.98288283f, 4.08649929f to 2.23811846f, 4.85481134f to 2.47563463f,
    5.62945551f to 2.72948597f, 6.43023796f to 2.98020421f,
)
private val superellipseBezierTable = arrayOf(
    .01339448f to .05994973f, .13664115f to .13592082f, .24545546f to .14099516f,
    .32353151f to .12808021f, .39093068f to .11726264f, .44847800f to .10808278f,
    .49817452f to .10026175f, .54105583f to .09344429f, .57812578f to .08748984f,
    .61050961f to .08224722f, .63903989f to .07759639f, .66416338f to .07346530f,
    .68675338f to .06974996f, .70678034f to .06529512f,
)

private fun interpolate(table: Array<Pair<Float, Float>>, steps: Float): Pair<Float, Float> {
    val left = floor(steps).toInt().coerceIn(0, table.size - 2)
    val f = steps - left
    return (table[left].first * (1 - f) + table[left + 1].first * f) to
        (table[left].second * (1 - f) + table[left + 1].second * f)
}

private fun normalized(vector: Offset): Offset = vector / vector.getDistance().coerceAtLeast(1e-12f)

private fun superellipseOctant(offset: Offset, a: Float, radius: Float): SuperellipseOctant {
    if (radius <= 1e-6f) return SuperellipseOctant(offset, a, 0f)
    val ratio = max(2f, a * 2f / radius)
    val (n, k) = if (ratio > 5f) {
        (1.559599389f * (ratio - 5f) + superellipseRatioTable.last().first) to
            (.522807185f * (ratio - 5f) + superellipseRatioTable.last().second)
    } else interpolate(superellipseRatioTable, if (ratio < 2.5f) (ratio - 2f) * 10f else (ratio - 2.5f) * 2f + 5f)
    val fraction = 1f - 1f / k
    val point = Offset(fraction * a, (1f - fraction.pow(n)).coerceAtLeast(0f).pow(1f / n) * a)
    val slope = (point.x / point.y).pow(n - 1f)
    val d = (point.x - slope * point.y) / (1f - slope)
    val gap = .29289321881f * radius
    val arcRadius = (a - d - gap) * sqrt(2f)
    val middle = Offset(a - gap, a - gap)
    val delta = middle - point
    val arcCenter = (point + middle) / 2f - normalized(Offset(-delta.y, delta.x)) *
        sqrt(max(0f, arcRadius * arcRadius - delta.getDistanceSquared() / 4f))
    val from = middle - arcCenter
    val to = point - arcCenter
    val angle = atan2(from.x * to.y - from.y * to.x, from.x * to.x + from.y * to.y)
    return SuperellipseOctant(offset, a, n, point, arcCenter, angle)
}

private fun superellipseCurves(o: SuperellipseOctant): Pair<List<Offset>, List<Offset>> {
    val factors = if (o.n >= 15f) {
        (1.07f - exp(1.307649835f) * o.n.pow(-.8568516731f)) to
            (-.01f + exp(-.9287690322f) * o.n.pow(-.6120901398f))
    } else interpolate(superellipseBezierTable, (o.n - 2f).coerceIn(0f, 13f))
    val vector = o.start - o.center
    val endVector = Offset(vector.x * cos(o.angle) + vector.y * sin(o.angle),
        -vector.x * sin(o.angle) + vector.y * cos(o.angle))
    val end = o.center + endVector
    val arcFactor = tan(o.angle / 4f) * 4f / 3f * vector.getDistance()
    val se = listOf(Offset(0f, o.a), Offset(factors.first * o.a, o.a),
        o.start + normalized(Offset(-vector.y, vector.x)) * factors.second * o.a, o.start)
    val circle = listOf(o.start, o.start + normalized(Offset(vector.y, -vector.x)) * arcFactor,
        end + normalized(Offset(-endVector.y, endVector.x)) * arcFactor, end)
    return se to circle
}

internal fun superellipsePath(size: Size, radii: List<Float>): Path {
    require(radii.size == 4 && radii.all { it.isFinite() && it >= 0f })
    val w = size.width; val h = size.height
    val scale = minOf(1f, w / (radii[0] + radii[1]).coerceAtLeast(1e-12f),
        w / (radii[3] + radii[2]).coerceAtLeast(1e-12f), h / (radii[0] + radii[3]).coerceAtLeast(1e-12f),
        h / (radii[1] + radii[2]).coerceAtLeast(1e-12f))
    val r = radii.map { it * scale }
    fun split(extent: Float, first: Float, second: Float) = if (first + second == 0f) extent / 2f else extent * first / (first + second)
    val top = split(w, r[0], r[1]); val bottom = split(w, r[3], r[2])
    val left = split(h, r[0], r[3]); val right = split(h, r[1], r[2])
    val centers = listOf(Offset(top, right), Offset(bottom, right), Offset(bottom, left), Offset(top, left))
    val corners = listOf(Offset(w, 0f), Offset(w, h), Offset(0f, h), Offset.Zero)
    val signs = listOf(Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f), Offset(-1f, -1f))
    val path = Path().apply { moveTo(top, 0f) }
    for (index in 0..3) {
        val center = centers[index]; val sign = signs[index]
        val half = corners[index] - center
        val radius = r[(index + 1) % 4].coerceAtMost(min(abs(half.x), abs(half.y)))
        val c = abs(half.x) - abs(half.y)
        val a = superellipseOctant(Offset(0f, -c), abs(half.x), radius)
        val b = superellipseOctant(Offset(c, 0f), abs(half.y), radius)
        fun transform(p: Offset) = center + Offset(p.x * sign.x, p.y * sign.y)
        fun line(p: Offset) { val q = transform(p); path.lineTo(q.x, q.y) }
        val reverse = index % 2 == 1
        if (a.n < 2f || b.n < 2f) {
            line(a.offset + Offset(a.a, a.a))
            line(if (reverse) a.offset + Offset(0f, a.a) else b.offset + Offset(b.a, 0f))
        } else {
            fun octant(o: SuperellipseOctant, backwards: Boolean, flip: Boolean) {
                fun map(p: Offset): Offset = transform(o.offset + if (flip) Offset(p.y, p.x) else p)
                val (se, arc) = superellipseCurves(o)
                val curves = if (backwards) listOf(arc.asReversed(), se.asReversed()) else listOf(se, arc)
                curves.forEach { points ->
                    val p1 = map(points[1]); val p2 = map(points[2]); val p3 = map(points[3])
                    path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                }
            }
            if (!reverse) { octant(a, false, false); octant(b, true, true) }
            else { octant(b, false, true); octant(a, true, false) }
        }
    }
    return path.apply { close() }
}
