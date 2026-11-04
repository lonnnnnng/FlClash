package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

// 作者 long：Flutter 的 ThreePointCubic 分两段求值，不能用普通 Material 缓动替代中间拐点。
private fun threePointEasing(ax: Float, ay: Float, bx: Float, by: Float, mx: Float, my: Float,
    cx: Float, cy: Float, dx: Float, dy: Float): Easing {
    val first = CubicBezierEasing(ax / mx, ay / my, bx / mx, by / my)
    val second = CubicBezierEasing((cx - mx) / (1 - mx), (cy - my) / (1 - my), (dx - mx) / (1 - mx), (dy - my) / (1 - my))
    return Easing { t -> if (t < mx) first.transform(t / mx) * my else second.transform((t - mx) / (1 - mx)) * (1 - my) + my }
}

internal val SheetPageEasing = threePointEasing(.05f, 0f, .133333f, .06f, .166666f, .4f, .208333f, .82f, .25f, 1f)
private val SheetBackSettleEasing = threePointEasing(.056f, .024f, .108f, .3085f, .198f, .541f, .3655f, 1f, .5465f, .989f)

internal class SheetBackGesture(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    var active by mutableStateOf(false)
        private set
    var settling by mutableStateOf(false)
        private set
    var committedTransition: Pair<String, String>? = null
        private set
    private var job: Job? = null
    fun start() { active = true; progress = 0f }
    fun move(delta: Float) { progress = (progress + delta).coerceIn(0f, .999999f) }
    fun release(pageKey: String, parentKey: String, velocity: Float, cancelled: Boolean, back: () -> Unit) {
        if (!active || settling) return
        val commit = !cancelled && if (abs(velocity) >= 1f) velocity > 0 else progress >= .5f
        settling = true
        job = scope.launch {
            animate(progress, if (commit) 1f else 0f, animationSpec = tween(350, easing = SheetBackSettleEasing)) { value, _ -> progress = value }
            if (commit) { committedTransition = pageKey to parentKey; back() }
            else { active = false; settling = false }
        }
    }
    fun reset() { job?.cancel(); active = false; settling = false; progress = 0f }
    fun forgetTransition() { committedTransition = null }
}

internal fun Modifier.sheetBackGesture(pageKey: String, parentKey: String?, direction: Int, state: SheetBackGesture,
    enabled: () -> Boolean, back: () -> Unit): Modifier = pointerInput(pageKey, direction) {
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabled() || state.active) return@awaitEachGesture
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var previous = down.position
            var claimed = false
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                // 作者 long：子控件先获手势；竖向滚动不触发返回，多指或系统取消也不切换页面。
                if (change == null || event.changes.any { it.id != down.id && it.pressed }) { cancelled = true; break }
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) { cancelled = change.isConsumed; break }
                if (claimed && change.isConsumed) { cancelled = true; break }
                if (!claimed) {
                    if (change.isConsumed) break
                    val distance = change.position - down.position
                    if (abs(distance.y) > viewConfiguration.touchSlop || distance.x * direction < -viewConfiguration.touchSlop) break
                    if (distance.x * direction > viewConfiguration.touchSlop) {
                        state.start(); claimed = true
                        state.move((distance.x * direction - viewConfiguration.touchSlop) / size.width)
                    }
                } else state.move((change.position.x - previous.x) * direction / size.width)
                if (claimed) change.consume()
                previous = change.position
            }
            if (claimed && parentKey != null) state.release(pageKey, parentKey, tracker.calculateVelocity().x * direction / size.width, cancelled, back)
        }
    } finally { state.reset() }
}
