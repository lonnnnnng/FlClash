package com.lonnnnnng.flclash.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.*

internal data class AppMenuMorphFrame(val platter: Rect, val radius: Float, val progress: Float,
    val droplet: Rect? = null, val blend: Float = 0f, val blur: Float = 0f, val sourceAlpha: Float = 0f) {
    fun path() = superellipsePath(platter.size, List(4) { radius }).apply { translate(platter.topLeft) }
    fun outline() = droplet?.let { appMenuGooPath(platter, radius, it, blend) } ?: path()
}

private fun menuSpring(stiffness: Float, damping: Float, time: Float): Float {
    val omega = sqrt(stiffness); val damped = omega * sqrt(1f - damping * damping)
    return 1f - exp(-damping * omega * time) * (cos(damped * time) + damping * omega / damped * sin(damped * time))
}

private fun riseAndFall(time: Float, peak: Float, shape: Float): Float {
    val x = (time / peak).coerceAtLeast(0f)
    return x.pow(shape) * exp(shape * (1f - x))
}

/** 作者 long：参数来自Flutter popup.dart，位置、等比内容缩放和延迟70ms的角部弹簧分别计算，文字不会随卡片高度被拉扁。 */
internal fun appMenuMorphFrame(anchor: Rect, card: Size, seconds: Float, unit: Float, source: Boolean = false): AppMenuMorphFrame {
    val progress = if (seconds >= .45f) 1f else menuSpring(400f, .754f, seconds.coerceAtLeast(0f))
    val scale = (.41f * anchor.size.minDimension / card.width).let { it + (1f - it) * progress }.coerceAtLeast(0f)
    val height = card.width + (card.height - card.width) * progress
    val distance = card.height / 2f - anchor.center.y
    val dip = if (source) sign(distance) * (min(1f, abs(distance) / (30f * unit)) * 23f * unit + .124f * abs(distance)) * riseAndFall(seconds, .098f, 3.3f) else 0f
    val center = anchor.center + (Offset(card.width / 2f, card.height / 2f) - anchor.center) * progress + Offset(0f, dip)
    val size = Size(card.width * scale, (height * scale).coerceAtLeast(0f))
    val corners = if (seconds >= .45f) 0f else 1f - menuSpring(324f, .75f, (seconds - .07f).coerceAtLeast(0f))
    val radius = (min(height / 2f, 24f * unit + card.width * .48f * corners) * scale).coerceAtLeast(0f)
    val droplet = if (!source) null else {
        val extent = anchor.size * (1f - .75f * progress)
        val inner = Rect(Offset.Zero, card).deflate(anchor.size.maxDimension * .125f + 36f * unit)
        val sunk = anchor.center + (Offset(card.width / 2f, card.height / 2f) - anchor.center) * .25f
        val target = if (inner.isEmpty) Offset(card.width / 2f, card.height / 2f) else Offset(sunk.x.coerceIn(inner.left, inner.right), sunk.y.coerceIn(inner.top, inner.bottom))
        val at = anchor.center + (target - anchor.center) * progress
        Rect(at - Offset(extent.width / 2, extent.height / 2), extent)
    }
    val settled = progress.coerceIn(0f, 1f)
    return AppMenuMorphFrame(Rect(center - Offset(size.width / 2f, size.height / 2f), size), radius, settled,
        droplet, 28f * unit, sqrt((4f * (1f - settled)).pow(2) + (1.9f * riseAndFall(seconds, .069f, 2.1f)).pow(2)) * unit,
        1f - min(1f, settled / .25f))
}

/** 作者 long：回源从实际中断帧起算，500ms内消除弹簧余振；100ms隐去文字，470ms重建按钮图像。 */
internal fun appMenuReturnFrame(anchor: Rect, from: AppMenuMorphFrame, seconds: Float, unit: Float): AppMenuMorphFrame {
    if (seconds >= .5f) return AppMenuMorphFrame(anchor, anchor.size.minDimension / 2f, 0f, anchor, 0f, 0f, 1f)
    val ringing = 1f - androidx.compose.animation.core.FastOutLinearInEasing.transform(((seconds - .4f) / .1f).coerceIn(0f, 1f))
    val p = 1f + (menuSpring(165f, .8f, seconds) - 1f) * ringing
    val distance = from.platter.center.y - anchor.center.y
    val dip = min(.21f * abs(distance), 22f * unit) * riseAndFall(seconds, .072f, 2.4f)
    val lift = min(.25f * abs(distance), 19f * unit) * riseAndFall(seconds, .176f, 4.9f)
    val scale = 1f + (.41f * anchor.size.minDimension / from.platter.size.maxDimension.coerceAtLeast(1f) - 1f) * p
    val size = Size(max(0f, from.platter.width * scale), max(0f, (from.platter.height + (from.platter.width - from.platter.height) * p) * scale))
    val center = from.platter.center + (anchor.center - from.platter.center) * p + Offset(0f, sign(distance) * dip * ringing)
    val origin = from.droplet ?: Rect(anchor.center + (from.platter.center - anchor.center) * .25f - Offset(anchor.width / 8, anchor.height / 8), anchor.size * .25f)
    val dropSize = Size(max(0f, origin.width + (anchor.width - origin.width) * p), max(0f, origin.height + (anchor.height - origin.height) * p))
    val dropCenter = origin.center + (anchor.center - origin.center) * p - Offset(0f, sign(distance) * lift * ringing)
    val absorbed = androidx.compose.animation.core.FastOutSlowInEasing.transform(((seconds - .3f) / .15f).coerceIn(0f, 1f))
    val reveal = from.progress * (1f - androidx.compose.animation.core.LinearOutSlowInEasing.transform((seconds / .1f).coerceIn(0f, 1f)))
    return AppMenuMorphFrame(Rect(center - Offset(size.width / 2, size.height / 2), size),
        max(0f, (from.radius + (from.platter.width / 2 - from.radius) * p) * scale), reveal,
        Rect(dropCenter - Offset(dropSize.width / 2, dropSize.height / 2), dropSize), 28f * unit * min(1f, seconds / .05f) * (1f - absorbed),
        8f * unit * (1f - reveal), .2f + .8f * androidx.compose.animation.core.CubicBezierEasing(.25f, .1f, .25f, 1f).transform((seconds / .47f).coerceIn(0f, 1f)))
}

internal class AppMenuMorphShape(private val frame: (Size) -> AppMenuMorphFrame) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Generic(frame(size).outline())
}

internal fun Modifier.appMenuMorph(surface: Color, frame: (Size) -> AppMenuMorphFrame, source: ImageBitmap? = null,
    backdrop: ImageBitmap? = null, backdropOffset: Offset = Offset.Zero, frost: GraphicsLayer? = null) = drawWithContent {
    val motion = frame(size)
    val scale = motion.platter.width / size.width.coerceAtLeast(1f)
    val path = motion.path()
    val outline = motion.outline()
    if (backdrop != null && frost != null) {
        frost.record { drawImage(backdrop, topLeft = backdropOffset) }
        if (android.os.Build.VERSION.SDK_INT >= 31) frost.renderEffect = BlurEffect(18f * density, 18f * density)
        clipPath(outline) { drawLayer(frost) }
    }
    drawPath(outline, surface.copy(alpha = if (backdrop != null) .72f else 1f))
    if (backdrop != null) drawPath(outline, Brush.linearGradient(listOf(Color.White.copy(alpha = .4f), Color.White.copy(alpha = .05f))), style = Stroke(1f * density))
    motion.droplet?.let { drop -> if (source != null && drop.width > 0 && drop.height > 0) {
        drawImage(source, dstOffset = androidx.compose.ui.unit.IntOffset(drop.left.roundToInt(), drop.top.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(drop.width.roundToInt().coerceAtLeast(1), drop.height.roundToInt().coerceAtLeast(1)), alpha = motion.sourceAlpha)
    } }
    clipPath(path) {
        withTransform({
            translate(motion.platter.center.x, motion.platter.center.y)
            scale(scale, scale, Offset.Zero)
            translate(-size.width / 2f, -size.height / 2f)
        }) {
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply {
                alpha = motion.progress.coerceIn(0f, 1f)
            })
            this@drawWithContent.drawContent()
            drawContext.canvas.restore()
        }
    }
}
