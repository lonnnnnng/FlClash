package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreEvent
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
    val updatedAtMillis: Long = 0L,
)

fun ProxyGroupsUiState.delayState(
    group: ProxyGroupInfo,
    proxyName: String,
    nowMillis: Long = System.currentTimeMillis(),
): ProxyDelayUiState? = delayResults[ProxyDelayKey(group.name, proxyName, group.testUrl.orDefaultTestUrl())]
    ?.takeIf { it.isTesting || it.updatedAtMillis == 0L || nowMillis - it.updatedAtMillis <= DELAY_RESULT_TTL_MS }

internal fun filterProxyMembers(group: ProxyGroupInfo, query: String): List<String> {
    val normalized = query.trim()
    return if (normalized.isEmpty()) group.all else group.all.filter { it.contains(normalized, ignoreCase = true) }
}

internal const val DELAY_RESULT_TTL_MS = 45_000L

/**
 * 作者 long：Mihomo 的 URLTest/Fallback 自动测速事件只携带测试地址和叶子节点名称，
 * 因此要把同一结果分发到当前页面中所有匹配的代理组，保持不同测试地址之间的结果隔离。
 */
internal fun ProxyGroupsUiState.applyDelayEvent(event: CoreEvent.Delay): ProxyGroupsUiState {
    val updates = groups
        .filter { group ->
            group.testUrl.orDefaultTestUrl() == event.result.url && event.result.name in group.all
        }
        .associate { group ->
            ProxyDelayKey(group.name, event.result.name, event.result.url) to
                ProxyDelayUiState(
                    value = event.result.value,
                    updatedAtMillis = System.currentTimeMillis(),
                )
        }
    return if (updates.isEmpty()) this else copy(delayResults = delayResults + updates)
}

internal fun computedSelectionTarget(group: ProxyGroupInfo, proxyName: String): String? {
    if (!group.isSelectable || proxyName !in group.all) return null
    if (!group.isComputed && proxyName == group.now) return null
    return if (group.isComputed && proxyName == group.now) "" else proxyName
}

private fun retainDelayResults(
    groups: List<ProxyGroupInfo>,
    values: Map<ProxyDelayKey, ProxyDelayUiState>,
    nowMillis: Long = System.currentTimeMillis(),
): Map<ProxyDelayKey, ProxyDelayUiState> {
    val valid = groups.flatMap { group ->
        group.all.map { proxy -> ProxyDelayKey(group.name, proxy, group.testUrl.orDefaultTestUrl()) }
    }.toSet()
    return values
        .filterKeys(valid::contains)
        .filterValues { it.isTesting || it.updatedAtMillis == 0L || nowMillis - it.updatedAtMillis <= DELAY_RESULT_TTL_MS }
}

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
        viewModelScope.launch {
            runtimeManager.events.collect { event ->
                if (event is CoreEvent.Delay) {
                    mutableState.update { it.applyDelayEvent(event) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true)
            runtimeManager.getProxyGroups()
                .onSuccess { snapshot ->
                    mutableState.value = ProxyGroupsUiState(
                        groups = snapshot.groups,
                        delayResults = retainDelayResults(
                            snapshot.groups,
                            mutableState.value.delayResults,
                        ),
                    )
                }
                .onFailure { error ->
                    mutableState.value = ProxyGroupsUiState()
                    mutableErrors.emit(error.message ?: "读取代理组失败")
                }
        }
    }

    fun selectProxy(group: ProxyGroupInfo, proxyName: String) {
        val target = computedSelectionTarget(group, proxyName) ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(switchingGroup = group.name)
            runtimeManager.changeProxy(group.name, target)
                .onSuccess { snapshot ->
                    mutableState.value = ProxyGroupsUiState(
                        groups = snapshot.groups,
                        delayResults = retainDelayResults(
                            snapshot.groups,
                            mutableState.value.delayResults,
                        ),
                    )
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
                                key to ProxyDelayUiState(
                                    value = result?.value,
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
                                ),
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { state ->
                        state.copy(
                            delayResults = state.delayResults + (
                                key to ProxyDelayUiState(
                                    value = CoreBridge.DELAY_FAILED,
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
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
