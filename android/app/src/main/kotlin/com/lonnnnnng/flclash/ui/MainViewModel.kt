package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val runtimeManager: CoreRuntimeManager,
) : ViewModel() {
    val runtimeState = runtimeManager.state
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val errors = mutableErrors.asSharedFlow()

    private val mutableStats = MutableStateFlow(RuntimeStatsUiState())
    val stats = mutableStats.asStateFlow()

    private val mutableConnections = MutableStateFlow(ConnectionsUiState())
    val connections = mutableConnections.asStateFlow()

    private val mutableRequests = MutableStateFlow(RequestsUiState())
    val requests = mutableRequests.asStateFlow()

    private val mutableDns = MutableStateFlow(DnsUiState())
    val dns = mutableDns.asStateFlow()

    private val mutableLogs = MutableStateFlow(LogsUiState())
    val logs = mutableLogs.asStateFlow()

    init {
        viewModelScope.launch {
            runtimeManager.events.collect { event ->
                mutableRequests.update { current -> current.apply(event) }
                mutableDns.update { current -> current.apply(event) }
                mutableLogs.update { current -> current.apply(event) }
            }
        }
        viewModelScope.launch {
            runtimeState.collectLatest { state ->
                if (!state.isRunning) {
                    mutableStats.value = RuntimeStatsUiState()
                    mutableConnections.value = ConnectionsUiState()
                    mutableRequests.value = RequestsUiState()
                    mutableDns.value = DnsUiState()
                    mutableLogs.value = LogsUiState()
                    return@collectLatest
                }
                while (isActive) {
                    refreshStats()
                    refreshConnections()
                    delay(STATS_REFRESH_MILLIS)
                }
            }
        }
    }

    fun startRuntime() {
        viewModelScope.launch {
            runtimeManager.startProxy().onFailure { error ->
                mutableErrors.emit(error.message ?: "核心启动失败")
            }
        }
    }

    fun stopRuntime() {
        viewModelScope.launch {
            runtimeManager.stop().onFailure { error ->
                mutableErrors.emit(error.message ?: "核心停止失败")
            }
        }
    }

    fun reportError(message: String) {
        mutableErrors.tryEmit(message)
    }

    fun clearDns() {
        mutableDns.value = DnsUiState()
    }

    fun clearLogs() {
        mutableLogs.value = LogsUiState()
    }

    fun toggleRuntime() {
        viewModelScope.launch {
            val result = if (runtimeManager.state.value.isRunning) {
                runtimeManager.stop()
            } else {
                runtimeManager.startProxy()
            }
            result.onFailure { error ->
                mutableErrors.emit(error.message ?: "核心操作失败")
            }
        }
    }

    private suspend fun refreshStats() {
        mutableStats.value = mutableStats.value.copy(isLoading = true, error = null)
        runtimeManager.getStats()
            .onSuccess { value ->
                mutableStats.value = RuntimeStatsUiState(
                    traffic = value.traffic,
                    totalTraffic = value.totalTraffic,
                    connectionCount = value.connectionCount,
                )
            }
            .onFailure { error ->
                // 作者 long：统计轮询失败不能把运行状态改成停止；只在卡片上保留可解释错误，下一轮继续恢复。
                mutableStats.value = mutableStats.value.copy(
                    isLoading = false,
                    error = error.message ?: "读取运行统计失败",
                )
            }
    }

    private suspend fun refreshConnections() {
        mutableConnections.value = mutableConnections.value.copy(isLoading = true, error = null)
        runtimeManager.getConnections()
            .onSuccess { value ->
                mutableConnections.value = ConnectionsUiState(connections = value)
            }
            .onFailure { error ->
                // 作者 long：连接列表读取失败只影响观测卡片，不得把核心运行状态误判为停止。
                mutableConnections.value = mutableConnections.value.copy(
                    isLoading = false,
                    error = error.message ?: "读取活动连接失败",
                )
            }
    }

    companion object {
        private const val STATS_REFRESH_MILLIS = 1_000L
    }
}

data class RuntimeStatsUiState(
    val traffic: CoreTraffic = CoreTraffic(0L, 0L),
    val totalTraffic: CoreTraffic = CoreTraffic(0L, 0L),
    val connectionCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class ConnectionsUiState(
    val connections: List<CoreConnection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class RequestsUiState(
    val requests: List<CoreConnection> = emptyList(),
) {
    /**
     * 作者 long：请求事件按连接 ID 增量更新，保留最近 2000 条历史，
     * 这样流量事件不会重复插入记录，也不会让长时间运行的手机应用无限增长内存。
     */
    internal fun apply(event: CoreEvent): RequestsUiState = when (event) {
        is CoreEvent.Request -> {
            val replaced = requests.indexOfFirst { it.id == event.connection.id }
            val next = if (replaced >= 0) {
                requests.toMutableList().apply { set(replaced, event.connection) }
            } else {
                (requests + event.connection).takeLast(MAX_REQUEST_HISTORY)
            }
            copy(requests = next)
        }

        is CoreEvent.RequestTraffic -> copy(
            requests = requests.map { request ->
                event.updates.firstOrNull { it.id == request.id }?.let { update ->
                    request.copy(upload = update.upload, download = update.download)
                } ?: request
            },
        )

        else -> this
    }
}

data class DnsUiState(
    val queries: List<CoreDnsQuery> = emptyList(),
) {
    /**
     * 作者 long：DNS 查询是高频事件，只保留最近 1000 条，避免长时间运行时把每次解析都永久留在内存中。
     */
    internal fun apply(event: CoreEvent): DnsUiState = when (event) {
        is CoreEvent.Dns -> copy(queries = (queries + event.query).takeLast(MAX_DNS_HISTORY))
        else -> this
    }
}

data class LogsUiState(
    val entries: List<CoreEvent.Log> = emptyList(),
) {
    /**
     * 作者 long：日志需要保留故障上下文但不能无限增长；清空由用户显式触发，便于围绕一次问题重新收集证据。
     */
    internal fun apply(event: CoreEvent): LogsUiState = when (event) {
        is CoreEvent.Log -> copy(entries = (entries + event).takeLast(MAX_LOG_HISTORY))
        else -> this
    }
}

private const val MAX_REQUEST_HISTORY = 2_000
private const val MAX_DNS_HISTORY = 1_000
private const val MAX_LOG_HISTORY = 1_000
