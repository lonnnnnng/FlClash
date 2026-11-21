package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.*

@Composable
internal fun rememberRuntimeToggleShape(running: Boolean): RuntimeToggleShape {
    // 作者 long：仅运行状态改变轮廓，初次回到运行页直接显示终态；每帧在绘制时读值，不重组出口IP和计时正文。
    val progress = animateFloatAsState(if (running) 1f else 0f, tween(450, easing =
        if (running) CubicBezierEasing(.175f, .885f, .32f, 1.275f)
        else runtimeReverseEasing), label = "runtime-toggle")
    return remember { RuntimeToggleShape { progress.value } }
}

// 作者 long：Flutter反放控制器后再求easeInCubic；Compose从当前值向0插值，须翻转时间和值才能保留同一反向曲线。
private val runtimeReverseEasing = Easing { fraction ->
    1f - runtimeEaseInCubic.transform(1f - fraction)
}
private val runtimeEaseInCubic = CubicBezierEasing(.55f, .055f, .675f, .19f)

/** 作者 long：匹配曲线来自固定Prism Morph；填充、裁剪、触光与玻璃共用一条轮廓，不能只换遮罩留下圆形折射边线。 */
internal class RuntimeToggleShape(private val progress: () -> Float) : Shape {
    private var lastSize = Size.Unspecified
    private var lastProgress = Float.NaN
    private var cachedOutline: Outline.Generic? = null
    private var cachedProfile: FloatArray? = null

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline.Generic {
        val value = progress()
        if (lastSize == size && lastProgress == value) return checkNotNull(cachedOutline)
        val start = RuntimeToggleCurves.start
        val end = RuntimeToggleCurves.end
        val turn = value * (2 * PI / 9).toFloat()
        val c = cos(turn); val s = sin(turn)
        val points = FloatArray(start.size)
        for (i in points.indices step 2) {
            val x = start[i] + (end[i] - start[i]) * value - .5f
            val y = start[i + 1] + (end[i + 1] - start[i + 1]) * value - .5f
            points[i] = size.width / 2 + c * x * size.width - s * y * size.height
            points[i + 1] = size.height / 2 + s * x * size.width + c * y * size.height
        }
        val path = Path().apply {
            moveTo(points[0], points[1])
            for (i in points.indices step 8) cubicTo(points[i + 2], points[i + 3], points[i + 4],
                points[i + 5], points[i + 6], points[i + 7])
            close()
        }
        lastSize = size; lastProgress = value; cachedProfile = null
        return Outline.Generic(path).also { cachedOutline = it }
    }

    fun profile(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray {
        val outline = createOutline(size, layoutDirection, density)
        cachedProfile?.let { return it }
        // 作者 long：只在玻璃开启且轮廓改变时采64方向；静止、实色和同帧多消费者复用结果，不持续做路径测量。
        return runtimeOutlineProfile(outline.path, size).also { cachedProfile = it }
    }
}

internal fun runtimeOutlineProfile(path: Path, size: Size): FloatArray {
    val profile = FloatArray(64)
    val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
    val point = FloatArray(2)
    val step = 2 * PI / 64
    do {
        val length = measure.length
        if (length <= 0f) continue
        val samples = max(256, ceil(length).toInt())
        measure.getPosTan(0f, point, null)
        var x0 = point[0] - size.width / 2; var y0 = point[1] - size.height / 2
        for (index in 1..samples) {
            measure.getPosTan(length * index / samples, point, null)
            val x1 = point[0] - size.width / 2; val y1 = point[1] - size.height / 2
            val from = atan2(y0.toDouble(), x0.toDouble())
            var span = atan2(y1.toDouble(), x1.toDouble()) - from
            if (span > PI) span -= 2 * PI else if (span <= -PI) span += 2 * PI
            val dx = x1 - x0; val dy = y1 - y0
            for (turn in ceil(min(from, from + span) / step).toInt()..floor(max(from, from + span) / step).toInt()) {
                val facing = cos(turn * step) * dy - sin(turn * step) * dx
                profile[(turn % 64 + 64) % 64] = if (abs(facing) < 1e-9)
                    max(hypot(x0, y0), hypot(x1, y1)) else ((x0 * dy - y0 * dx) / facing).toFloat()
            }
            x0 = x1; y0 = y1
        }
    } while (measure.nextContour())
    return profile
}
