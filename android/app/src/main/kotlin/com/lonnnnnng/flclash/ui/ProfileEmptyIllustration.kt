package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 作者 long：空态入场与 Flutter 保持同一 900ms 时间窗；固定编排避免重组时随机跳帧并方便截图回归。 */
internal enum class EmptyIllustrationEntrance { SPIN, MORPH, ORBIT }

internal data class EmptyIllustrationPose(
    val haloScale: Float,
    val haloRotation: Float,
    val haloAlpha: Float,
    val bodyScale: Float,
    val bodyRotation: Float,
    val bodyMorph: Float,
    val glyphScale: Float,
    val glyphRise: Float,
    val glyphFill: Float,
    val glyphAlpha: Float,
    val accentScale: List<Float>,
    val accentDrift: List<Float>,
    val accentOrbit: List<Float>,
    val accentRotation: List<Float>,
)

private val emptyIllustrationBack = CubicBezierEasing(.175f, .885f, .32f, 1.275f)
private val emptyIllustrationCubic = CubicBezierEasing(.645f, .045f, .355f, 1f)
private val emptyIllustrationElastic = CubicBezierEasing(.2f, 1.35f, .35f, 1f)

private fun emptyIllustrationPhase(t: Float, begin: Float, end: Float, easing: Easing = LinearEasing): Float =
    easing.transform(((t - begin) / (end - begin)).coerceIn(0f, 1f))

/** 作者 long：每种编排都落在同一静止帧，切换页面时只改变进入路径，不改变空态最终视觉。 */
internal fun emptyIllustrationPose(entrance: EmptyIllustrationEntrance, t: Float): EmptyIllustrationPose {
    fun spin(settle: Float) = Triple(.6f + .4f * settle, -60f * (1f - settle), 1f)
    return when (entrance) {
        EmptyIllustrationEntrance.SPIN -> {
            val halo = spin(emptyIllustrationPhase(t, .08f, .75f, emptyIllustrationBack))
            val body = spin(emptyIllustrationPhase(t, 0f, .65f, emptyIllustrationBack))
            val mark = emptyIllustrationPhase(t, .1f, .55f, emptyIllustrationBack)
            EmptyIllustrationPose(
                haloScale = halo.first, haloRotation = halo.second, haloAlpha = halo.third,
                bodyScale = body.first, bodyRotation = body.second, bodyMorph = 1f,
                glyphScale = .5f + .5f * mark + sin(PI.toFloat() * emptyIllustrationPhase(t, .55f, .85f)) * .08f,
                glyphRise = 0f, glyphFill = emptyIllustrationPhase(t, .2f, .6f, emptyIllustrationCubic),
                glyphAlpha = emptyIllustrationPhase(t, .1f, .3f),
                accentScale = List(3) { index -> emptyIllustrationPhase(t, .4f + index * .08f, .75f + index * .08f, emptyIllustrationBack) },
                accentDrift = List(3) { index ->
                    val enter = emptyIllustrationPhase(t, .4f + index * .08f, .75f + index * .08f, emptyIllustrationBack)
                    .7f + .3f * enter
                },
                accentOrbit = List(3) { 0f },
                accentRotation = List(3) { index ->
                    val enter = emptyIllustrationPhase(t, .4f + index * .08f, .75f + index * .08f, emptyIllustrationBack)
                    -45f * (1f - enter)
                },
            )
        }
        EmptyIllustrationEntrance.MORPH -> {
            fun morphed(begin: Float, end: Float): Triple<Float, Float, Float> {
                val morph = emptyIllustrationPhase(t, begin, end, CubicBezierEasing(.5f, 0f, .35f, 1f))
                val settle = emptyIllustrationPhase(t, begin, begin + .5f, emptyIllustrationBack)
                return Triple(.5f + .5f * settle, 90f * (1f - morph), morph)
            }
            val halo = morphed(.15f, .8f)
            val body = morphed(.05f, .65f)
            val rise = emptyIllustrationPhase(t, .2f, .6f, emptyIllustrationBack)
            EmptyIllustrationPose(
                haloScale = halo.first, haloRotation = halo.second, haloAlpha = 1f,
                bodyScale = body.first, bodyRotation = body.second, bodyMorph = body.third,
                glyphScale = .8f + .2f * rise, glyphRise = .14f * (1f - rise),
                glyphFill = emptyIllustrationPhase(t, .3f, .7f, emptyIllustrationCubic),
                glyphAlpha = emptyIllustrationPhase(t, .2f, .35f),
                accentScale = List(3) { index ->
                    emptyIllustrationPhase(t, .45f + (2 - index) * .07f, .75f + (2 - index) * .07f, emptyIllustrationBack)
                },
                accentDrift = List(3) { 1f },
                accentOrbit = List(3) { 0f },
                accentRotation = List(3) { index ->
                    val enter = emptyIllustrationPhase(t, .45f + (2 - index) * .07f, .75f + (2 - index) * .07f, emptyIllustrationBack)
                    -90f * (1f - enter)
                },
            )
        }
        EmptyIllustrationEntrance.ORBIT -> {
            val ripple = emptyIllustrationPhase(t, .2f, .75f, CubicBezierEasing(.2f, .8f, .2f, 1f))
            val bodySettle = emptyIllustrationPhase(t, 0f, .6f, emptyIllustrationElastic)
            val glyphSettle = emptyIllustrationPhase(t, .15f, .7f, emptyIllustrationElastic)
            EmptyIllustrationPose(
                haloScale = .72f + .28f * ripple, haloRotation = 0f, haloAlpha = ripple,
                bodyScale = .3f + .7f * bodySettle, bodyRotation = 0f, bodyMorph = 1f,
                glyphScale = glyphSettle, glyphRise = 0f,
                glyphFill = emptyIllustrationPhase(t, .3f, .7f, emptyIllustrationCubic),
                glyphAlpha = emptyIllustrationPhase(t, .15f, .25f),
                accentScale = List(3) { index -> emptyIllustrationPhase(t, .3f + index * .1f, .6f + index * .1f, emptyIllustrationBack) },
                accentDrift = List(3) { index ->
                    val travel = emptyIllustrationPhase(t, .3f + index * .1f, .7f + index * .1f, CubicBezierEasing(.2f, .8f, .2f, 1f))
                    .55f + .45f * travel
                },
                accentOrbit = List(3) { index ->
                    val travel = emptyIllustrationPhase(t, .3f + index * .1f, .7f + index * .1f, CubicBezierEasing(.2f, .8f, .2f, 1f))
                    -126f * (1f - travel)
                },
                accentRotation = List(3) { index ->
                    val travel = emptyIllustrationPhase(t, .3f + index * .1f, .7f + index * .1f, CubicBezierEasing(.2f, .8f, .2f, 1f))
                    -90f * (1f - travel)
                },
            )
        }
    }
}

internal fun defaultEmptyIllustrationEntrance(glyph: AppGlyph): EmptyIllustrationEntrance = when {
    glyph === AppGlyphs.search -> EmptyIllustrationEntrance.MORPH
    glyph === AppGlyphs.proxies -> EmptyIllustrationEntrance.ORBIT
    else -> EmptyIllustrationEntrance.SPIN
}

@Composable
internal fun ProfileEmptyIllustration(glyph: AppGlyph = AppGlyphs.profiles, outline: Path = ProfileIllustrationPaths.cookie7,
    extent: Dp = 160.dp, entrance: EmptyIllustrationEntrance? = null) {
    val progress = remember { Animatable(0f) }
    val selectedEntrance = entrance ?: remember(glyph) { defaultEmptyIllustrationEntrance(glyph) }
    LaunchedEffect(glyph, outline, selectedEntrance) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = LinearEasing))
    }
    val pose = emptyIllustrationPose(selectedEntrance, progress.value)
    val circle = remember { Path().apply { addOval(Rect(0f, 0f, 1f, 1f)) } }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(extent), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            fun shape(path: Path, at: Offset, extent: Float, degrees: Float, color: Color) {
                if (extent <= 0f) return
                withTransform({ translate(at.x, at.y); rotate(degrees, Offset.Zero); scale(extent, extent, Offset.Zero); translate(-.5f, -.5f) }) {
                    drawPath(path, color)
                }
            }
            fun morphedShape(scale: Float, rotation: Float, morph: Float, color: Color) {
                if (morph < 1f) shape(circle, center, size.minDimension * scale, rotation, color.copy(alpha = color.alpha * (1f - morph)))
                if (morph > 0f) shape(outline, center, size.minDimension * scale, rotation, color.copy(alpha = color.alpha * morph))
            }
            morphedShape(.84f * pose.haloScale, pose.haloRotation, pose.bodyMorph, colors.primary.copy(alpha = .14f * pose.haloAlpha))
            morphedShape(.72f * pose.bodyScale, pose.bodyRotation, pose.bodyMorph, colors.primaryContainer)
            val offsets = listOf(Offset(-.32f, .3f), Offset(-.4f, -.18f), Offset(.42f, .15f))
            val extents = listOf(.14f, .05f, .075f)
            repeat(3) { index ->
                val enter = pose.accentScale[index]
                val angle = atan2(offsets[index].y, offsets[index].x) + pose.accentOrbit[index] * PI.toFloat() / 180f
                val radius = hypot(offsets[index].x, offsets[index].y) * size.minDimension * pose.accentDrift[index]
                val at = center + Offset(cos(angle) * radius, sin(angle) * radius)
                val diameter = size.minDimension * extents[index] * enter
                if (diameter > 0f) when (index) {
                    0 -> shape(ProfileIllustrationPaths.cookie4, at, diameter, 15f + pose.accentRotation[index], colors.tertiaryContainer)
                    1 -> drawCircle(colors.tertiary, diameter / 2f, at)
                    else -> drawCircle(colors.primary, diameter * .36f, at, style = Stroke(diameter * .28f))
                }
            }
        }
        AppGlyphIcon(glyph, Modifier.size(64.dp).offset(y = (-64f * pose.glyphRise).dp).graphicsLayer {
            scaleX = pose.glyphScale; scaleY = pose.glyphScale; alpha = pose.glyphAlpha
        }, colors.onPrimaryContainer, fillProgress = pose.glyphFill)
    }
}
