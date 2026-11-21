package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun RuntimeToggleGlyph(running: Boolean, suspended: Boolean, busy: Boolean, modifier: Modifier = Modifier) {
    // 作者 long：上游字形双向都用easeOutBack，不能借用外轮廓的反向曲线；忙态仍保留时钟，完成后不会从播放端点重放。
    val progress = animateFloatAsState(if (running) 1f else 0f,
        tween(450, easing = CubicBezierEasing(.175f, .885f, .32f, 1.275f)), label = "runtime-glyph")
    val paused = rememberUpdatedState(suspended)
    val cache = remember { RuntimeGlyphCache() }
    val painter = remember { AppGlyphPainter({ if (paused.value) runtimeWifiOffGlyph else cache.resolve(progress.value) }, 1f) }
    if (busy) CircularProgressIndicator(modifier.size(28.dp).testTag("runtime_toggle_busy"), strokeWidth = 2.dp)
    else Icon(painter, null, modifier.size(29.92.dp).testTag(if (suspended) "runtime_toggle_suspended" else "runtime_toggle_glyph"), LocalContentColor.current)
}

internal class RuntimeGlyphCache {
    private var previous = Float.NaN
    private var glyph: AppGlyph? = null
    fun resolve(progress: Float): AppGlyph {
        if (progress == previous) return checkNotNull(glyph)
        previous = progress
        return runtimePlayPauseGlyph(progress).also { glyph = it }
    }
}

/** 作者 long：固定Flutter AppGlyphs.playPause把三角形分成两半再展开成双条；超过端点只继续旋转，不能拉坏暂停条形。 */
internal fun runtimePlayPauseGlyph(progress: Float): AppGlyph {
    val fold = progress.coerceIn(0f, 1f)
    val turn = (PI / 2 * progress).toFloat()
    val c = cos(turn); val s = sin(turn)
    fun turned(p: Offset): GlyphVertex {
        val x = p.x - 12f; val y = p.y - 12f
        return GlyphVertex(12f + x * c - y * s, 12f + x * s + y * c, 1.2f)
    }
    if (fold == 0f) return AppGlyph(listOf(GlyphPolyline(playCorners.map(::turned), closed = true)))
    return AppGlyph(playHalves.map { (half, bar) ->
        GlyphPolyline(half.indices.map { i -> turned(half[i] * (1f - fold) + bar[i] * fold) }, closed = true)
    })
}

private val playCorners = listOf(Offset(8.3f, 5.6f), Offset(18.8f, 12f), Offset(8.3f, 18.4f))
private val playHalves = listOf(
    listOf(Offset(8.3f, 5.6f), Offset(18.8f, 12f), Offset(18.8f, 12f), Offset(8.3f, 12f)) to
        listOf(Offset(5.9f, 6.9f), Offset(18.1f, 6.9f), Offset(18.1f, 9.1f), Offset(5.9f, 9.1f)),
    listOf(Offset(8.3f, 12f), Offset(18.8f, 12f), Offset(18.8f, 12f), Offset(8.3f, 18.4f)) to
        listOf(Offset(5.9f, 14.9f), Offset(18.1f, 14.9f), Offset(18.1f, 17.1f), Offset(5.9f, 17.1f)),
)

internal val runtimeWifiOffGlyph by lazy { AppGlyph(listOf(
    GlyphPath("M3.32 9.56 A13.5 13.5 0 0 1 6.15 7.73", role = GlyphRole.line),
    GlyphPath("M10.75 6.46 A13.5 13.5 0 0 1 20.68 9.56", role = GlyphRole.line),
    GlyphPath("M6.54 13.39 A8.5 8.5 0 0 1 10.56 11.52", role = GlyphRole.line),
    GlyphPath("M15.27 12.05 A8.5 8.5 0 0 1 17.46 13.39", role = GlyphRole.line),
    GlyphCircle(12f, 18.6f, 1.7f, role = GlyphRole.line, solid = true),
    GlyphPolyline(listOf(GlyphVertex(4.5f, 3f), GlyphVertex(20.5f, 19f)), role = GlyphRole.line),
)) }
