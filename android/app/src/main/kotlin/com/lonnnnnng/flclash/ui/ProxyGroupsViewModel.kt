package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProxyGroupsUiState(
    val groups: List<ProxyGroupInfo> = emptyList(),
    val isLoading: Boolean = false,
    val switchingGroup: String? = null,
    val delayResults: Map<ProxyDelayKey, ProxyDelayUiState> = emptyMap(),
)

data class ProxyDelayKey(
    val groupName: String,
    val proxyName: String,
    val testUrl: String,
)

data class ProxyDelayUiState(
    val isTesting: Boolean = false,
    val value: Int? = null,
)

fun ProxyGroupsUiState.delayState(
    group: ProxyGroupInfo,
    proxyName: String,
): ProxyDelayUiState? = delayResults[ProxyDelayKey(group.name, proxyName, group.testUrl.orDefaultTestUrl())]

@HiltViewModel
class ProxyGroupsViewModel @Inject constructor(
    private val runtimeManager: CoreRuntimeManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProxyGroupsUiState())
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val state: StateFlow<ProxyGroupsUiState> = mutableState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProxyGroupsUiState())
    val errors = mutableErrors.asSharedFlow()

    init {
        viewModelScope.launch {
            runtimeManager.state.collect { status ->
                if (status == CoreRuntimeStatus.RUNNING) {
                    refresh()
                } else {
                    mutableState.value = ProxyGroupsUiState()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true)
            runtimeManager.getProxyGroups()
                .onSuccess { snapshot ->
                    mutableState.value = ProxyGroupsUiState(groups = snapshot.groups)
                }
                .onFailure { error ->
                    mutableState.value = ProxyGroupsUiState()
                    mutableErrors.emit(error.message ?: "读取代理组失败")
                }
        }
    }

    fun selectProxy(group: ProxyGroupInfo, proxyName: String) {
        if (!group.isSelector || proxyName == group.now) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(switchingGroup = group.name)
            runtimeManager.changeProxy(group.name, proxyName)
                .onSuccess { snapshot ->
                    mutableState.value = ProxyGroupsUiState(groups = snapshot.groups)
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(switchingGroup = null)
                    mutableErrors.emit(error.message ?: "切换代理失败")
                }
        }
    }

    /**
     * 作者 long：结果键包含代理组、节点和测试地址，避免同名节点在不同代理组之间串用延迟结果。
     */
    fun testDelay(group: ProxyGroupInfo, proxyName: String) {
        val key = ProxyDelayKey(group.name, proxyName, group.testUrl.orDefaultTestUrl())
        if (mutableState.value.delayResults[key]?.isTesting == true) return
        mutableState.update {
            it.copy(delayResults = it.delayResults + (key to ProxyDelayUiState(isTesting = true)))
        }
        viewModelScope.launch {
            runtimeManager.testDelay(proxyName, key.testUrl)
                .onSuccess { result ->
                    mutableState.update { state ->
                        state.copy(
                            delayResults = state.delayResults + (
                                key to ProxyDelayUiState(value = result?.value)
                                ),
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { state ->
                        state.copy(
                            delayResults = state.delayResults + (
                                key to ProxyDelayUiState(value = CoreBridge.DELAY_FAILED)
                                ),
                        )
                    }
                    mutableErrors.emit(error.message ?: "测速失败")
                }
        }
    }
}

private fun String?.orDefaultTestUrl(): String =
    this?.takeIf { it.isNotBlank() } ?: CoreBridge.DEFAULT_TEST_URL
