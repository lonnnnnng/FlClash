package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    init {
        viewModelScope.launch {
            runtimeState.collectLatest { state ->
                if (!state.isRunning) {
                    mutableStats.value = RuntimeStatsUiState()
                    mutableConnections.value = ConnectionsUiState()
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
