package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 作者 long：固定射线旧路径只作为测试参照，检查自适应点简化不会改变实际开合轮廓。 */
class AppMenuGooAdaptiveInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun adaptiveOutlineMatchesFixedRayRasterAcrossOpeningAndReturnFrames() {
        assertPortraitDefaultFontConfiguration()
        val density = compose.activity.resources.displayMetrics.density
        val anchor = Rect(280f * density, -44f * density, 328f * density, 4f * density)
        val card = Size(260f * density, 380f * density)
        val frames = buildList {
            listOf(.02f, .07f, .14f, .25f, .4f, .45f).forEach { add(appMenuMorphFrame(anchor, card, it, density, true)) }
            val opened = appMenuMorphFrame(anchor, card, .45f, density, true)
            listOf(.02f, .07f, .14f, .25f, .4f).forEach { add(appMenuReturnFrame(anchor, opened, it, density)) }
        }
        frames.forEachIndexed { index, frame ->
            val droplet = frame.droplet ?: return@forEachIndexed
            val old = fixedRayOutline(frame.platter, frame.radius, droplet, frame.blend)
            val adaptive = appMenuGooPath(frame.platter, frame.radius, droplet, frame.blend, .05f * density)
            val oldBounds = old.getBounds()
            val newBounds = adaptive.getBounds()
            val left = floor(min(oldBounds.left, newBounds.left)).toInt() - 2
            val top = floor(min(oldBounds.top, newBounds.top)).toInt() - 2
            val right = ceil(max(oldBounds.right, newBounds.right)).toInt() + 2
            val bottom = ceil(max(oldBounds.bottom, newBounds.bottom)).toInt() + 2
            val clip = android.graphics.Region(left, top, right + 1, bottom + 1)
            val oldRegion = android.graphics.Region().apply { setPath(old.asAndroidPath(), clip) }
            val newRegion = android.graphics.Region().apply { setPath(adaptive.asAndroidPath(), clip) }
            var compared = 0
            var oldCovered = 0
            var different = 0
            for (y in top..bottom step 2) for (x in left..right step 2) {
                val oldContains = oldRegion.contains(x, y)
                val newContains = newRegion.contains(x, y)
                if (oldContains) oldCovered++
                if (oldContains != newContains) different++
                compared++
            }
            assertTrue("第${index + 1}帧轮廓差异 $different/$oldCovered 超过旧轮廓面积的采样容差（总采样$compared）",
                oldCovered > 0 && different.toFloat() / oldCovered < .002f)
        }
    }

    @Test fun adaptivePathExecutionTimeAgainstFixedRayReference() {
        assertPortraitDefaultFontConfiguration()
        val density = compose.activity.resources.displayMetrics.density
        val anchor = Rect(280f * density, -44f * density, 328f * density, 4f * density)
        val card = Size(260f * density, 380f * density)
        val opened = appMenuMorphFrame(anchor, card, .45f, density, true)
        val frames = (listOf(.02f, .07f, .14f, .25f, .4f, .45f).map { appMenuMorphFrame(anchor, card, it, density, true) } +
            listOf(.02f, .07f, .14f, .25f, .4f).map { appMenuReturnFrame(anchor, opened, it, density) })
            .filter { it.droplet != null && it.blend > 0f }
        fun oldRun(): Float = frames.sumOf { frame -> fixedRayOutline(frame.platter, frame.radius, checkNotNull(frame.droplet), frame.blend).getBounds().width.toDouble() }.toFloat()
        fun newRun(): Float = frames.sumOf { frame -> appMenuGooPath(frame.platter, frame.radius, checkNotNull(frame.droplet), frame.blend, .05f * density).getBounds().width.toDouble() }.toFloat()
        repeat(3) { oldRun(); newRun() }
        val oldSamples = mutableListOf<Long>()
        val newSamples = mutableListOf<Long>()
        repeat(9) { round ->
            // 作者 long：交错先后顺序并预热，避免把JIT和顺序热度差当作算法收益；只报告局部耗时，不断言设备帧率。
            val oldFirst = round % 2 == 0
            listOf(oldFirst, !oldFirst).forEach { old ->
                val start = System.nanoTime()
                repeat(8) { if (old) oldRun() else newRun() }
                val elapsed = System.nanoTime() - start
                if (old) oldSamples += elapsed else newSamples += elapsed
            }
        }
        val oldMedian = oldSamples.sorted()[oldSamples.size / 2]
        val newMedian = newSamples.sorted()[newSamples.size / 2]
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("menu_goo_path_benchmark", "frames=${frames.size} repetitions=8 oldMedianNs=$oldMedian adaptiveMedianNs=$newMedian ratio=${newMedian.toDouble() / oldMedian}")
            putString("menu_goo_old_samples_ns", oldSamples.joinToString(","))
            putString("menu_goo_adaptive_samples_ns", newSamples.joinToString(","))
        })
    }

    private fun fixedRayOutline(a: Rect, radius: Float, b: Rect, blend: Float): Path {
        fun distance(rect: Rect, r: Float, point: Offset): Float {
            val round = min(r, rect.size.minDimension / 2)
            val x = abs(point.x - rect.center.x) - rect.width / 2 + round
            val y = abs(point.y - rect.center.y) - rect.height / 2 + round
            return hypot(max(x, 0f), max(y, 0f)) + min(max(x, y), 0f) - round
        }
        fun contour(rect: Rect, r: Float) = superellipsePath(rect.size, List(4) { r }).apply { translate(rect.topLeft) }
        fun depth(rect: Rect, r: Float, other: Rect) = -listOf(other.topLeft, other.topRight, other.bottomLeft, other.bottomRight).maxOf { distance(rect, r, it) } - blend
        val platter = contour(a, radius)
        if (b.isEmpty || depth(a, radius, b) >= 0) return platter
        val pillRadius = b.size.minDimension / 2
        val pill = contour(b, pillRadius)
        if (depth(b, pillRadius, a) >= 0) return pill
        val union = Path.combine(androidx.compose.ui.graphics.PathOperation.Union, platter, pill)
        if (blend <= 0f) return union
        fun field(point: Offset): Float {
            val x = distance(a, radius, point)
            val y = distance(b, pillRadius, point)
            val h = (.5f + .5f * (y - x) / blend).coerceIn(0f, 1f)
            return y + (x - y) * h - blend * h * (1f - h)
        }
        val bridge = Path()
        val fan = mutableListOf<Offset>()
        fun closeFan() {
            if (fan.size > 1) {
                bridge.moveTo(b.center.x, b.center.y)
                fan.forEach { bridge.lineTo(it.x, it.y) }
                bridge.close()
            }
            fan.clear()
        }
        val endpoints = ArrayList<Pair<Offset, Boolean>>(128)
        repeat(128) { turn ->
            val angle = turn * 2f * Math.PI.toFloat() / 128
            val direction = Offset(kotlin.math.cos(angle), kotlin.math.sin(angle))
            val limit = b.size.maxDimension / 2 + a.size.maxDimension + blend
            var inside = 0f
            var at = 0f
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
            endpoints += end to (distance(a, radius, end) < blend)
        }
        val first = endpoints.indexOfFirst { !it.second }.coerceAtLeast(0)
        repeat(129) { index ->
            val (point, flowing) = endpoints[(first + index) % 128]
            if (flowing) fan.add(point) else closeFan()
        }
        closeFan()
        return Path.combine(androidx.compose.ui.graphics.PathOperation.Union, union, bridge)
    }
}
