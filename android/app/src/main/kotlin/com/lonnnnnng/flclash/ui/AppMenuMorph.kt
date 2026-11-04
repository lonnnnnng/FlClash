package com.lonnnnnng.flclash.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.*

internal data class AppMenuMorphFrame(val platter: Rect, val radius: Float, val progress: Float) {
    fun path() = superellipsePath(platter.size, List(4) { radius }).apply { translate(platter.topLeft) }
}

/** 作者 long：参数来自Flutter popup.dart，位置、等比内容缩放和延迟70ms的角部弹簧分别计算，文字不会随卡片高度被拉扁。 */
internal fun appMenuMorphFrame(anchor: Rect, card: Size, seconds: Float, unit: Float): AppMenuMorphFrame {
    fun spring(stiffness: Float, damping: Float, time: Float): Float {
        val omega = sqrt(stiffness); val damped = omega * sqrt(1f - damping * damping)
        return 1f - exp(-damping * omega * time) * (cos(damped * time) + damping * omega / damped * sin(damped * time))
    }
    val progress = if (seconds >= .45f) 1f else spring(400f, .754f, seconds.coerceAtLeast(0f))
    val scale = (.41f * anchor.size.minDimension / card.width).let { it + (1f - it) * progress }.coerceAtLeast(0f)
    val height = card.width + (card.height - card.width) * progress
    val center = anchor.center + (Offset(card.width / 2f, card.height / 2f) - anchor.center) * progress
    val size = Size(card.width * scale, (height * scale).coerceAtLeast(0f))
    val corners = if (seconds >= .45f) 0f else 1f - spring(324f, .75f, (seconds - .07f).coerceAtLeast(0f))
    val radius = (min(height / 2f, 24f * unit + card.width * .48f * corners) * scale).coerceAtLeast(0f)
    return AppMenuMorphFrame(Rect(center - Offset(size.width / 2f, size.height / 2f), size), radius, progress)
}

internal class AppMenuMorphShape(private val frame: (Size) -> AppMenuMorphFrame) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Generic(frame(size).path())
}

internal fun Modifier.appMenuMorph(surface: Color, frame: (Size) -> AppMenuMorphFrame) = drawWithContent {
    val motion = frame(size)
    val scale = motion.platter.width / size.width.coerceAtLeast(1f)
    val path = motion.path()
    drawPath(path, surface)
    clipPath(path) {
        withTransform({
            translate(motion.platter.center.x, motion.platter.center.y)
            scale(scale, scale, Offset.Zero)
            translate(-size.width / 2f, -size.height / 2f)
        }) {
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = motion.progress.coerceIn(0f, 1f) })
            this@drawWithContent.drawContent()
            drawContext.canvas.restore()
        }
    }
}
