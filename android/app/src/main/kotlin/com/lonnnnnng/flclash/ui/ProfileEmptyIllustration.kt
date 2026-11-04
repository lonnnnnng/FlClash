package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** 作者 long：采用Flutter空态spin编排的900ms时间窗，各层最后落在统一轮廓；只播放进入动画，不在空页面持续消耗绘制。 */
@Composable
internal fun ProfileEmptyIllustration() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, easing = LinearEasing)) }
    val back = remember { CubicBezierEasing(.175f, .885f, .32f, 1.275f) }
    val cubic = remember { CubicBezierEasing(.645f, .045f, .355f, 1f) }
    val t = progress.value
    fun phase(begin: Float, end: Float, easing: Easing = LinearEasing) = easing.transform(((t - begin) / (end - begin)).coerceIn(0f, 1f))
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            fun shape(path: Path, at: Offset, extent: Float, degrees: Float, color: Color) {
                if (extent <= 0f) return
                withTransform({ translate(at.x, at.y); rotate(degrees, Offset.Zero); scale(extent, extent, Offset.Zero); translate(-.5f, -.5f) }) {
                    drawPath(path, color)
                }
            }
            val halo = phase(.08f, .75f, back); val body = phase(0f, .65f, back)
            shape(ProfileIllustrationPaths.cookie7, center, size.minDimension * .84f * (.6f + .4f * halo), -60f * (1f - halo), colors.primary.copy(alpha = .14f))
            shape(ProfileIllustrationPaths.cookie7, center, size.minDimension * .72f * (.6f + .4f * body), -60f * (1f - body), colors.primaryContainer)
            val offsets = listOf(Offset(-.32f, .3f), Offset(-.4f, -.18f), Offset(.42f, .15f))
            val extents = listOf(.14f, .05f, .075f)
            repeat(3) { index ->
                val begin = .4f + index * .08f; val enter = phase(begin, begin + .35f, back)
                val at = center + offsets[index] * size.minDimension * (.7f + .3f * enter)
                val diameter = size.minDimension * extents[index] * enter
                if (diameter > 0f) when (index) {
                    0 -> shape(ProfileIllustrationPaths.cookie4, at, diameter, 15f - 45f * (1f - enter), colors.tertiaryContainer)
                    1 -> drawCircle(colors.tertiary, diameter / 2f, at)
                    else -> drawCircle(colors.primary, diameter * .36f, at, style = Stroke(diameter * .28f))
                }
            }
        }
        val scale = .5f + .5f * phase(.1f, .55f, back) + sin(PI.toFloat() * phase(.55f, .85f)) * .08f
        AppGlyphIcon(AppGlyphs.profiles, null, Modifier.size(64.dp).graphicsLayer {
            scaleX = scale; scaleY = scale; alpha = phase(.1f, .3f)
        }, colors.onPrimaryContainer, fillProgress = phase(.2f, .6f, cubic))
    }
}
