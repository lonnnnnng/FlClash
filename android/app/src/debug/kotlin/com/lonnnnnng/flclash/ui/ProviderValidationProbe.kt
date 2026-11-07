package com.lonnnnnng.flclash.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** 作者 long：仅测试可挂起真实过滤校验返回后的提交边界，保留原生校验结果和正式保存链路。 */
object ProviderValidationProbe {
    class Gate internal constructor() {
        internal val reached = CompletableDeferred<Unit>()
        internal val released = CompletableDeferred<Unit>()
        internal val cancelled = CompletableDeferred<Unit>()
        internal var visits = 0
        fun isReached() = reached.isCompleted
        fun isCancelled() = cancelled.isCompleted
        fun visits(): Int = synchronized(lock) { visits }
        fun release() { released.complete(Unit) }
    }

    private val lock = Any()
    private var armed: Gate? = null

    fun arm(): Gate = synchronized(lock) {
        check(armed == null) { "本进程已有Provider校验等待点" }
        Gate().also { armed = it }
    }

    fun reset() = synchronized(lock) { armed?.release(); armed = null }

    suspend fun checkpoint() {
        val gate = synchronized(lock) {
            val current = armed ?: return
            current.visits++
            current.takeIf { it.visits == 1 }
        } ?: return
        gate.reached.complete(Unit)
        try {
            // 作者 long：只挂起首次校验，重建后的显式重试仍走真实校验；超时不能冒充窗口取消证据。
            check(withTimeoutOrNull(60_000) { gate.released.await(); true } == true) { "Provider校验验收等待超时" }
        } catch (cancelled: CancellationException) {
            gate.cancelled.complete(Unit)
            throw cancelled
        }
    }
}
