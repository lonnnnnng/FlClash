package com.lonnnnnng.flclash.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/** 作者 long：仅同进程测试可布置一次门闩，无导出组件、持久开关或恢复接口；命中后只能由外部结束进程。 */
object EditorCommitProbe {
    class Gate internal constructor(val window: EditorCommitWindow) {
        internal val reached = CompletableDeferred<Unit>()
        internal var claimed = false
        suspend fun awaitReached() = reached.await()
    }
    private val lock = Any()
    private var armed: Gate? = null
    private val visits = mutableMapOf<EditorCommitWindow, Int>()

    fun visits(window: EditorCommitWindow): Int = synchronized(lock) { visits[window] ?: 0 }

    fun arm(window: EditorCommitWindow): Gate = synchronized(lock) {
        check(armed == null) { "本进程已经布置保存窗口" }
        Gate(window).also { armed = it }
    }

    suspend fun checkpoint(window: EditorCommitWindow) {
        val gate = synchronized(lock) {
            visits[window] = (visits[window] ?: 0) + 1
            armed?.takeIf { it.window == window && !it.claimed }?.also { it.claimed = true }
        } ?: return
        // 作者 long：不能让Activity退场或VM取消把已命中的保存继续执行，外部必须在此进程仍存活时终止它。
        withContext(NonCancellable) {
            gate.reached.complete(Unit)
            awaitCancellation()
        }
    }
}
