package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp

/** 作者 long：使用固定Prism的275ms临界弹簧并补404ms终点残差，不能把物理周期误当成路由时长。 */
internal fun scalarDialogSpring(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f).toDouble()
    val omega = 2 * PI / .275
    fun displacement(t: Double) = 1 - (1 + omega * t) * exp(-omega * t)
    return (displacement(.404 * p) + (1 - displacement(.404)) * p).toFloat().coerceIn(0f, 1f)
}

@Stable
internal class ScalarDialogMotion(restoredClosing: Boolean = false) {
    var progress by mutableFloatStateOf(0f)
        private set
    var closing by mutableStateOf(restoredClosing)
        private set
    private var reverseFromComplete = false
    private var heldScale = 1f
    val moving get() = if (closing) progress > 0f else progress < 1f
    val alpha get() = if (closing && reverseFromComplete) 1f - scalarDialogSpring(1f - progress) else scalarDialogSpring(progress)
    val scale get() = if (closing) heldScale else 1.2f - .2f * alpha

    fun close() {
        if (closing) return
        // 作者 long：提前返回保留原入场曲线和当前大小；完整入场才使用flipped曲线，避免反转第一帧跳亮或放大。
        heldScale = scale
        reverseFromComplete = progress >= 1f
        closing = true
    }

    fun advance(seconds: Float) {
        val delta = seconds.coerceAtLeast(0f) / .404f
        val next = (progress + if (closing) -delta else delta).coerceIn(0f, 1f)
        // 作者 long：不同帧切分会留下Float端点残差，不能因此多留一个透明窗口阻挡父页触点。
        progress = when { next < .000001f -> 0f; next > .999999f -> 1f; else -> next }
    }

    fun finish() { progress = if (closing) 0f else 1f }
}
