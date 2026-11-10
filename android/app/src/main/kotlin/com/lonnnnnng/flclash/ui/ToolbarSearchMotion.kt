package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.*

internal data class ToolbarSearchFrame(
    val growth: Float,
    val slot: Float,
    val field: Float,
    val title: Float,
    val actions: Float,
    val flank: Float,
    val content: Float,
    val glyph: Float,
    val swell: Float,
    val lift: Float,
    val lead: Float = actions,
    val dropletOffset: Float = 0f,
    val dropletSize: Float = 1f,
    val flankScale: Float = 1f,
) {
    companion object {
        val Closed = ToolbarSearchFrame(0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f, 0f)
        val Open = ToolbarSearchFrame(1f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 0f)
    }
}

private val SearchEaseOut = CubicBezierEasing(0f, 0f, .58f, 1f)
private val SearchEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)

/** 作者 long：沿固定Flutter的分段时序驱动根页搜索；反向沿用弹簧速度与当前各通道，避免连点跳回起点。 */
@Stable
internal class ToolbarSearchMotion(initialOpen: Boolean, initialHasPrimary: Boolean = true) {
    private val spring = AppSpring(if (initialOpen) 1f else 0f, 390f, .75f)
    var frame by mutableStateOf(if (initialOpen) ToolbarSearchFrame.Open else ToolbarSearchFrame.Closed)
        private set
    var moving by mutableStateOf(false)
        private set
    private var opening = initialOpen
    private var start = frame
    private var elapsed = 0f
    private var delay = 0f
    private var grouped = initialHasPrimary
    val velocity get() = spring.velocity

    fun moveTo(open: Boolean, hasPrimary: Boolean = true) {
        if (open == opening) return
        if (open) grouped = hasPrimary
        delay = if (spring.moving) 0f else if (open) .05f else if (grouped) .04f else 0f
        opening = open
        start = frame
        elapsed = 0f
        spring.to(if (open) 1f else 0f, if (open) 390f else if (grouped) 600f else 165f,
            if (open || grouped) .75f else .8f)
        moving = true
    }

    fun finish() {
        spring.snap(if (opening) 1f else 0f)
        frame = if (opening) ToolbarSearchFrame.Open else ToolbarSearchFrame.Closed
        moving = false
    }

    fun advance(seconds: Float) {
        if (!moving) return
        val before = max(0f, elapsed - delay)
        elapsed += seconds
        val after = max(0f, elapsed - delay)
        if (after > before) spring.advance(after - before)
        if (elapsed >= (if (opening) .29f else if (grouped) .55f else .8f) && !spring.moving) { finish(); return }
        fun to(from: Float, target: Float, begin: Float, end: Float,
            curve: androidx.compose.animation.core.Easing? = null): Float {
            val t = ((elapsed - begin) / (end - begin)).coerceIn(0f, 1f)
            return from + (target - from) * (curve?.transform(t) ?: t)
        }
        frame = if (opening) ToolbarSearchFrame(
            growth = spring.value,
            slot = to(start.slot, 1f, 0f, .08f, SearchEaseOut),
            field = to(start.field, 1f, 0f, .05f),
            title = to(start.title, 0f, 0f, .08f),
            actions = to(start.actions, 0f, .02f, .08f),
            flank = to(start.flank, 1f, .08f, .2f),
            content = to(start.content, 1f, .07f, .22f),
            glyph = to(start.glyph, 0f, 0f, .03f),
            swell = to(start.swell, 0f, 0f, .1f),
            lift = to(to(start.lift, 1f, .02f, .12f, SearchEaseOut), 0f, .16f, .29f, SearchEaseInOut),
            lead = to(start.lead, 0f, .02f, .08f),
            dropletOffset = to(start.dropletOffset, 0f, 0f, .05f),
            dropletSize = to(start.dropletSize, 1f, 0f, .05f),
            flankScale = to(start.flankScale, 1f, 0f, .05f),
        ) else ToolbarSearchFrame(
            growth = spring.value,
            slot = to(start.slot, 0f, .17f, .33f, SearchEaseInOut),
            field = if (grouped) to(start.field, 0f, .19f, .29f) else to(start.field, 0f, .02f, .12f),
            title = to(start.title, 1f, .08f, .19f),
            actions = to(start.actions, 1f, .18f, .26f),
            flank = if (grouped) to(start.flank, 0f, .11f, .2f) else to(start.flank, 0f, 0f, .03f),
            content = to(start.content, 0f, 0f, .04f),
            glyph = to(start.glyph, 1f, .3f, if (grouped) .46f else .5f),
            swell = if (grouped) to(to(start.swell, 1f, .27f, .36f, SearchEaseOut), 0f, .36f, .55f, SearchEaseInOut)
                else to(start.swell, 0f, 0f, .1f),
            lift = if (grouped) to(to(start.lift, 1f, .06f, .16f, SearchEaseOut), 0f, .21f, .32f, SearchEaseInOut)
                else to(start.lift, 0f, 0f, .1f),
            lead = if (grouped) to(start.lead, 1f, .18f, .26f) else to(start.lead, 1f, 0f, .02f),
            dropletOffset = if (grouped) to(start.dropletOffset, 0f, 0f, .05f) else
                .25f * (1f - searchDropletAt(elapsed) - .71f * searchRiseAndFall(elapsed)),
            dropletSize = if (grouped) to(start.dropletSize, 1f, 0f, .05f) else
                .25f + .75f * searchDropletAt((elapsed - .31f).coerceAtLeast(0f)),
            flankScale = if (grouped) to(start.flankScale, 1f, 0f, .05f) else to(start.flankScale, 0f, 0f, .05f),
        )
    }
}

@Composable
internal fun rememberToolbarSearchMotion(open: Boolean, hasPrimary: Boolean): ToolbarSearchMotion {
    val motion = remember { ToolbarSearchMotion(open, hasPrimary) }
    LaunchedEffect(open) {
        motion.moveTo(open, hasPrimary)
        var previous = withFrameNanos { it }
        while (motion.moving) {
            val now = withFrameNanos { it }
            val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale <= 0f) motion.finish()
            else motion.advance((now - previous) / 1_000_000_000f / scale)
            previous = now
        }
    }
    return motion
}

private fun searchDropletAt(time: Float): Float {
    val omega = sqrt(165f)
    val frequency = omega * sqrt(1f - .8f * .8f)
    return 1f - exp(-.8f * omega * time) * (cos(frequency * time) + .8f * omega / frequency * sin(frequency * time))
}

private fun searchRiseAndFall(time: Float): Float = if (time <= 0) 0f else
    (time / .19f).pow(5.7f) * exp(5.7f * (1f - time / .19f))
