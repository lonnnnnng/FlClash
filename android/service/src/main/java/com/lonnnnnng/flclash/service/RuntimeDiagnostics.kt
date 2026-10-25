package com.lonnnnnng.flclash.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RuntimeDiagnosticKind {
    CORE_STARTED,
    CORE_RESTARTED,
    CORE_STOPPED,
    CORE_FAILED,
    VPN_REVOKED,
    NETWORK_AVAILABLE,
    NETWORK_LOSING,
    NETWORK_LOST,
    NETWORK_SWITCHED,
    DNS_UPDATED,
}

data class RuntimeDiagnosticEvent(
    val kind: RuntimeDiagnosticKind,
    val detail: String,
    val timestampMillis: Long,
)

interface RuntimeDiagnosticStore {
    suspend fun loadRecent(limit: Int): List<RuntimeDiagnosticEvent>

    suspend fun append(event: RuntimeDiagnosticEvent, limit: Int)

    suspend fun clear()
}

object NoopRuntimeDiagnosticStore : RuntimeDiagnosticStore {
    override suspend fun loadRecent(limit: Int): List<RuntimeDiagnosticEvent> = emptyList()

    override suspend fun append(event: RuntimeDiagnosticEvent, limit: Int) = Unit

    override suspend fun clear() = Unit
}

/**
 * 作者 long：网络和核心回调来自不同服务线程，统一写入固定上限的历史，
 * 让诊断可以解释最近生命周期变化，同时避免长时间运行不断复制事件占满内存。
 */
object RuntimeDiagnosticHistory {
    internal const val MAX_EVENTS = 200

    private val lock = Any()
    private val mutableEvents = MutableStateFlow<List<RuntimeDiagnosticEvent>>(emptyList())
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()
    private val persistenceJobs = mutableSetOf<Job>()
    private var store: RuntimeDiagnosticStore? = null
    private var persistenceTail: Job? = null
    val events = mutableEvents.asStateFlow()

    /**
     * 作者 long：服务模块不能反向依赖 app 的 Room 实体，只接收抽象存储；Application 创建的
     * Runtime Manager 会在任何网络或核心回调发生前安装具体实现。
     */
    fun configure(runtimeStore: RuntimeDiagnosticStore?) {
        synchronized(lock) {
            store = runtimeStore
        }
    }

    /**
     * 作者 long：进程重建后先恢复上一轮有界历史，再允许 CoreRuntimeManager 记录新的重启事件，
     * 这样诊断证据可以把崩溃前的网络变化和重建后的 CORE_RESTARTED 连成一条时间线。
     */
    suspend fun restore(): List<RuntimeDiagnosticEvent> {
        flush()
        val restored = synchronized(lock) { store }?.loadRecent(MAX_EVENTS).orEmpty()
            .sortedBy { it.timestampMillis }
            .takeLast(MAX_EVENTS)
        synchronized(lock) {
            mutableEvents.value = restored
        }
        return restored
    }

    fun record(kind: RuntimeDiagnosticKind, detail: String) {
        val event = RuntimeDiagnosticEvent(
            kind = kind,
            detail = detail,
            timestampMillis = System.currentTimeMillis(),
        )
        synchronized(lock) {
            mutableEvents.value = (mutableEvents.value + event).takeLast(MAX_EVENTS)
        }
        enqueuePersistence { runtimeStore -> runtimeStore.append(event, MAX_EVENTS) }
    }

    /**
     * 作者 long：核心启停和 VPN 撤销属于跨进程恢复的锚点，方法返回前必须等待对应事件落盘，
     * 否则系统刚杀掉应用时，下一次进程只能看到恢复意图却看不到上一轮生命周期结尾。
     */
    suspend fun recordAndFlush(kind: RuntimeDiagnosticKind, detail: String) {
        record(kind, detail)
        flush()
    }

    fun clear() {
        synchronized(lock) {
            mutableEvents.value = emptyList()
        }
        enqueuePersistence { runtimeStore -> runtimeStore.clear() }
    }

    /**
     * 作者 long：测试和进程恢复边界需要等待异步 Room 写入完成，避免用尚未落盘的内存状态
     * 假定跨进程历史已经存在。
     */
    suspend fun flush() {
        while (true) {
            // 作者 long：先抓取当前写入尾巴并等待，再确认尾巴没有被新的网络回调替换；
            // 单纯检查 jobs 为空会在回调恰好入队的窗口提前返回，导致进程重建丢最后事件。
            val barrier = synchronized(lock) { persistenceTail }
            barrier?.join()
            val stable = synchronized(lock) {
                barrier === persistenceTail && persistenceJobs.none { it.isActive }
            }
            if (stable) return
        }
    }

    private fun enqueuePersistence(action: suspend (RuntimeDiagnosticStore) -> Unit) {
        val runtimeStore = synchronized(lock) { store } ?: return
        synchronized(lock) {
            val previous = persistenceTail
            val job = persistenceScope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                persistenceMutex.withLock { action(runtimeStore) }
            }
            persistenceTail = job
            persistenceJobs += job
            job.invokeOnCompletion {
                synchronized(lock) {
                    persistenceJobs -= job
                }
            }
            job.start()
        }
    }
}
