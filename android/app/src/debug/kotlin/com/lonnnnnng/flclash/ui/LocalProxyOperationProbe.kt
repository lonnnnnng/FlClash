package com.lonnnnnng.flclash.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** 作者 long：仅同进程测试显式布置等待；真实定义、校验、数据库结果均由正式链路产生。 */
object LocalProxyOperationProbe {
    class Gate internal constructor(val window: LocalProxyOperationWindow) {
        internal val reached = CompletableDeferred<Unit>()
        internal val released = CompletableDeferred<Unit>()
        internal var claimed = false
        internal val visits = mutableMapOf<LocalProxyOperationWindow, Int>()
        internal var databaseAborts = 0
        suspend fun awaitReached() = withTimeout(30_000) { reached.await() }
        fun isReached(): Boolean = reached.isCompleted
        fun release() { released.complete(Unit) }
        fun visits(window: LocalProxyOperationWindow): Int = synchronized(lock) { visits[window] ?: 0 }
        fun databaseAborts(): Int = synchronized(lock) { databaseAborts }
    }
    private val lock = Any()
    private var armed: Gate? = null

    fun arm(window: LocalProxyOperationWindow): Gate = synchronized(lock) {
        check(armed == null) { "本进程已有本地代理等待点" }
        Gate(window).also { armed = it }
    }

    fun reset() = synchronized(lock) { armed?.release(); armed = null }

    fun saveFailed(error: Exception) {
        // 作者 long：只识别测试SQL固定标记，不保存异常文本；CAS或核心校验失败不能冒充数据库故障已命中。
        val injectedAbort = generateSequence(error as Throwable) { it.cause }.take(8).any {
            it is android.database.sqlite.SQLiteConstraintException && it.message.orEmpty().contains("fixture-local-save-failure")
        }
        if (injectedAbort) synchronized(lock) { armed?.let { it.databaseAborts++ } }
    }

    suspend fun checkpoint(window: LocalProxyOperationWindow) {
        val gate = synchronized(lock) {
            val current = armed ?: return
            current.visits[window] = (current.visits[window] ?: 0) + 1
            current.takeIf { it.window == window && !it.claimed }?.also { it.claimed = true }
        } ?: return
        gate.reached.complete(Unit)
        // 作者 long：测试中断或忘记释放不能让正式页面无限忙碌；超时按正常失败链路收口，VM取消仍可传播。
        withTimeout(60_000) { gate.released.await() }
    }
}
