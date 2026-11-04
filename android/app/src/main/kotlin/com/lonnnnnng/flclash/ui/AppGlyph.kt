package com.lonnnnnng.flclash.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal enum class GlyphRole { body, detail, line }
internal data class GlyphShape(val path: Path, val role: GlyphRole = GlyphRole.body, val solid: Boolean = false)
internal data class AppGlyph(val shapes: List<GlyphShape>, val matchTextDirection: Boolean = false)
internal fun GlyphPath(data: String, role: GlyphRole = GlyphRole.body, solid: Boolean = false) =
    GlyphShape(androidx.core.graphics.PathParser.createPathFromPathData(data).asComposePath(), role, solid)
internal data class GlyphVertex(val x: Float, val y: Float, val radius: Float = 0f) {
    val point get() = Offset(x, y)
}

internal fun GlyphBox(left: Float, top: Float, right: Float, bottom: Float, radius: Float,
    role: GlyphRole = GlyphRole.body) = GlyphShape(
    superellipsePath(Size(right - left, bottom - top), List(4) { radius }).apply { translate(Offset(left, top)) }, role)

internal fun GlyphCircle(x: Float, y: Float, radius: Float, role: GlyphRole = GlyphRole.body, solid: Boolean = false) =
    GlyphShape(Path().apply { addOval(Rect(x - radius, y - radius, x + radius, y + radius)) }, role, solid)

internal fun GlyphOval(left: Float, top: Float, right: Float, bottom: Float, role: GlyphRole = GlyphRole.body) =
    GlyphShape(Path().apply { addOval(Rect(left, top, right, bottom)) }, role)

internal fun GlyphArc(x: Float, y: Float, radius: Float, start: Float, sweep: Float, role: GlyphRole = GlyphRole.body) =
    GlyphShape(Path().apply { arcTo(Rect(x - radius, y - radius, x + radius, y + radius), start * 180f / kotlin.math.PI.toFloat(),
        sweep * 180f / kotlin.math.PI.toFloat(), true) }, role)

/** 作者 long：沿用 Flutter glyph.dart 的入角/出角二次曲线，圆角不能用普通折线或统一描边连接替代。 */
internal fun GlyphPolyline(vertices: List<GlyphVertex>, closed: Boolean = false, role: GlyphRole = GlyphRole.body): GlyphShape {
    fun corner(index: Int): Pair<Offset, Offset> {
        val vertex = vertices[index]; val p = vertex.point; val count = vertices.size
        fun inset(other: Offset): Offset {
            val delta = other - p; val length = delta.getDistance()
            return if (length == 0f) p else p + delta / length * min(vertex.radius, length / 2f)
        }
        return inset(vertices[(index - 1 + count) % count].point) to inset(vertices[(index + 1) % count].point)
    }
    return GlyphShape(Path().apply {
        val first = if (closed) corner(0).second else vertices.first().point
        moveTo(first.x, first.y)
        val last = if (closed) vertices.size else vertices.lastIndex
        for (index in 1..last) {
            val vertex = vertices[index % vertices.size]
            if (!closed && index == vertices.lastIndex) lineTo(vertex.x, vertex.y)
            else {
                val (enter, exit) = corner(index % vertices.size)
                lineTo(enter.x, enter.y); quadraticTo(vertex.x, vertex.y, exit.x, exit.y)
            }
        }
        if (closed) close()
    }, role)
}

/** 作者 long：实心 Dock 图标的细节是透明镂空；独立图层使其透出滑块底色，并避免半透明描边交点加深。 */
private class AppGlyphPainter(private val glyph: AppGlyph, private val fill: Float) : Painter() {
    override val intrinsicSize = Size(24f, 24f)
    private var alpha = 1f
    private var filter: ColorFilter? = null
    override fun applyAlpha(alpha: Float): Boolean { this.alpha = alpha; return true }
    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean { filter = colorFilter; return true }
    override fun DrawScope.onDraw() {
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = this@AppGlyphPainter.alpha; colorFilter = filter })
        withTransform({
            val unit = size.minDimension / 24f
            translate((size.width - 24f * unit) / 2f, (size.height - 24f * unit) / 2f)
            scale(unit, unit, Offset.Zero)
            if (glyph.matchTextDirection && this@onDraw.layoutDirection == LayoutDirection.Rtl) { translate(24f, 0f); scale(-1f, 1f, Offset.Zero) }
        }) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (fill > 0f) glyph.shapes.filter { it.role == GlyphRole.body }.forEach {
                if (fill >= 1f) drawPath(it.path, Color.White)
                else clipPath(it.path) {
                    val reach = .9f + (it.path.getBounds().size.minDimension / 2f - .9f) * fill
                    drawPath(it.path, Color.White, style = Stroke(reach * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            val cut = fill > .5f
            val detailScale = if (cut) (fill - .5f) / .5f else 1f - fill / .5f
            if (detailScale > 0f) glyph.shapes.filter { it.role == GlyphRole.detail }.forEach {
                val blend = if (cut) BlendMode.Clear else BlendMode.SrcOver
                if (it.solid) withTransform({ scale(detailScale, detailScale, it.path.getBounds().center) }) { drawPath(it.path, Color.White, blendMode = blend) }
                else drawPath(it.path, Color.White, style = Stroke(1.8f * detailScale, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
            }
            glyph.shapes.filter { it.role != GlyphRole.detail }.forEach {
                val width = if (it.role == GlyphRole.line) 1.8f + .4f * fill.coerceIn(0f, 1f) else 1.8f
                if (it.solid) withTransform({ scale(width / 1.8f, width / 1.8f, it.path.getBounds().center) }) { drawPath(it.path, Color.White) }
                else drawPath(it.path, Color.White, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        canvas.restore()
    }
}

@Composable
internal fun AppGlyphIcon(glyph: AppGlyph, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current, filled: Boolean = false, fillProgress: Float = if (filled) 1f else 0f) {
    // 作者 long：Painter的intrinsicSize是像素，不能当作dp；外部显式尺寸和菜单容器约束仍优先。
    Icon(remember(glyph, fillProgress) { AppGlyphPainter(glyph, fillProgress) }, null, modifier.size(24.dp), tint)
}
