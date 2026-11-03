package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.*

/** 作者 long：手指每帧改变目标时沿用当前速度和时钟，避免反复启动动画而始终停在首帧。 */
@Stable
internal class AppSpring(initial: Float, stiffness: Float, damping: Float, val rest: Float = .001f) {
    var stiffness = stiffness; private set
    var damping = damping; private set
    var value by mutableFloatStateOf(initial); private set
    var target by mutableFloatStateOf(initial); private set
    var velocity = 0f; private set
    var afterFrame: (() -> Unit)? = null
    val moving: Boolean get() = value != target || velocity != 0f
    fun to(next: Float, stiffness: Float = this.stiffness, damping: Float = this.damping) {
        this.stiffness = stiffness; this.damping = damping; target = next
    }
    fun snap(next: Float) { value = next; target = next; velocity = 0f; afterFrame?.invoke() }
    fun advance(seconds: Float) {
        val omega = sqrt(stiffness)
        val displacement = value - target
        val decay = exp(-damping * omega * seconds)
        val next: Float
        val speed: Float
        if (damping < 1f) {
            val frequency = omega * sqrt(1f - damping * damping)
            val c = cos(frequency * seconds); val s = sin(frequency * seconds)
            next = decay * (displacement * c + (velocity + damping * omega * displacement) / frequency * s)
            speed = decay * (velocity * c - (damping * omega * velocity + stiffness * displacement) / frequency * s)
        } else {
            val b = velocity + omega * displacement
            next = decay * (displacement + b * seconds)
            speed = decay * (velocity - omega * b * seconds)
        }
        if (abs(next) <= rest && abs(speed) <= rest * omega) { value = target; velocity = 0f }
        else { value = target + next; velocity = speed }
        afterFrame?.invoke()
    }
}

@Composable
internal fun rememberAppSpring(initial: Float, stiffness: Float = 311f, damping: Float = .593f,
    rest: Float = .001f): AppSpring {
    val spring = remember { AppSpring(initial, stiffness, damping, rest) }
    LaunchedEffect(spring) {
        while (currentCoroutineContext().isActive) {
            snapshotFlow { spring.moving }.first { it }
            var previous = withFrameNanos { it }
            while (spring.moving) {
                val now = withFrameNanos { it }
                val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                if (scale == 0f) spring.snap(spring.target)
                else spring.advance(((now - previous) / 1_000_000_000f / scale).coerceAtMost(.064f))
                previous = now
            }
        }
    }
    return spring
}

// 作者 long：Flutter 的 duration/bounce 定义是物理参数换算，不把时长直接当作 tween 结束时间。
internal fun springStiffness(durationMillis: Int): Float = (4 * PI * PI / (durationMillis / 1000.0).pow(2)).toFloat()
internal fun rubberBand(distance: Float, limit: Float): Float =
    if (limit <= 0f) 0f else limit * (1f - 1f / (abs(distance) * .55f / limit + 1f)) * distance.sign

@Stable
internal class ElasticPressState(val lift: AppSpring, val x: AppSpring, val y: AppSpring) {
    private var releasing = false
    init { lift.afterFrame = { if (releasing && lift.value >= .975f) { releasing = false; lift.to(0f) } } }
    fun press() { releasing = false; lift.to(1f) }
    fun pull(delta: Offset, limit: Float) {
        x.to(rubberBand(delta.x, limit), springStiffness(120), 1f)
        y.to(rubberBand(delta.y, limit), springStiffness(120), 1f)
    }
    fun release() { x.to(0f, 311f, .593f); y.to(0f, 311f, .593f); releasing = true; lift.afterFrame?.invoke() }
    fun reset() { releasing = false; lift.snap(0f); x.snap(0f); y.snap(0f) }
}

@Composable
internal fun rememberElasticPressState(): ElasticPressState {
    val lift = rememberAppSpring(0f)
    val x = rememberAppSpring(0f, springStiffness(120), 1f)
    val y = rememberAppSpring(0f, springStiffness(120), 1f)
    return remember { ElasticPressState(lift, x, y) }
}

@Composable
internal fun Modifier.appElasticPress(enabled: Boolean = true, onPress: () -> Unit = {},
    state: ElasticPressState = rememberElasticPressState()): Modifier {
    val latestPress by rememberUpdatedState(onPress)
    LaunchedEffect(enabled) { if (!enabled) state.reset() }
    return this.pointerInput(enabled, state) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.press(); latestPress()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    // 作者 long：点击控件会消费按下事件；只有移动被滚动手势接管时才结束跟随，长按不能在首帧误释放。
                    if (pointer.isConsumed && pointer.pressed && pointer.position != pointer.previousPosition) break
                    state.pull(pointer.position - down.position, min(size.width, size.height) * 7f / 32f)
                } while (pointer.pressed)
            } finally { state.release() }
        }
    }.drawWithContent {
        // 作者 long：只变换画布，语义、触控命中和菜单锚点仍使用静止矩形，放大的相邻按钮不会抢点击。
        val growth = min(.25f, 16.dp.toPx() / size.maxDimension.coerceAtLeast(1f))
        val swell = 1f + state.lift.value * growth
        withTransform({
            translate(state.x.value, state.y.value)
            scale(swell * (1f + abs(state.x.value) / size.width.coerceAtLeast(1f) * .5f),
                swell * (1f + abs(state.y.value) / size.height.coerceAtLeast(1f) * .5f), center)
        }) { this@drawWithContent.drawContent() }
    }
}

@Composable
internal fun Modifier.appFocusRing(shape: Shape, standsOff: Boolean = false): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    return this.onFocusChanged { focused = it.hasFocus }.focusGroup().drawWithContent {
        drawContent()
        if (focused) {
            // 作者 long：描边跟随整个子树的焦点，选中或按下本身不画焦点；主色填充控件外留1dp间隙。
            val outset = if (standsOff) 2.dp.toPx() else -1.dp.toPx()
            val outline = shape.createOutline(Size(size.width + 2 * outset, size.height + 2 * outset), layoutDirection, this)
            withTransform({ translate(-outset, -outset) }) { drawOutline(outline, color, style = Stroke(2.dp.toPx())) }
        }
    }
}
