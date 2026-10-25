package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch

data class ProxyGroupsUiState(
    val groups: List<ProxyGroupInfo> = emptyList(),
    val isLoading: Boolean = false,
    val switchingGroup: String? = null,
)

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
}
