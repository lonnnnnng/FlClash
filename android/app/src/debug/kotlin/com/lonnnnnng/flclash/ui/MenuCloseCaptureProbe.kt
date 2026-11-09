package com.lonnnnnng.flclash.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** 作者 long：只在独立诊断中于原500ms回源完成后暂留Popup，便于读取尚未销毁窗口的完整帧表；动画本身不加等待。 */
object MenuCloseCaptureProbe {
    class Gate internal constructor() {
        val completed = CompletableDeferred<Long>()
        internal val released = CompletableDeferred<Unit>()
        fun release() { released.complete(Unit) }
    }
    @Volatile private var gate: Gate? = null
    fun arm(): Gate {
        check(gate == null)
        return Gate().also { gate = it }
    }
    fun reset() { gate?.release(); gate = null }
    suspend fun afterAnimation() {
        val current = gate ?: return
        current.completed.complete(System.nanoTime())
        try { withTimeout(10_000) { current.released.await() } }
        finally { if (gate === current) gate = null }
    }
}
