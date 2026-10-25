package com.lonnnnnng.flclash.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/**
 * 作者 long：网络和核心回调来自不同服务线程，统一写入固定上限的历史，
 * 让诊断可以解释最近生命周期变化，同时避免长时间运行不断复制事件占满内存。
 */
object RuntimeDiagnosticHistory {
    internal const val MAX_EVENTS = 200

    private val lock = Any()
    private val mutableEvents = MutableStateFlow<List<RuntimeDiagnosticEvent>>(emptyList())
    val events = mutableEvents.asStateFlow()

    fun record(kind: RuntimeDiagnosticKind, detail: String) {
        val event = RuntimeDiagnosticEvent(
            kind = kind,
            detail = detail,
            timestampMillis = System.currentTimeMillis(),
        )
        synchronized(lock) {
            mutableEvents.value = (mutableEvents.value + event).takeLast(MAX_EVENTS)
        }
    }

    fun clear() {
        synchronized(lock) {
            mutableEvents.value = emptyList()
        }
    }
}
