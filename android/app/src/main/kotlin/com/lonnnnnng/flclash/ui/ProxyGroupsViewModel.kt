package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProxyDelayRequest
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ProxyGroupsUiState(
    val groups: List<ProxyGroupInfo> = emptyList(),
    val selectedMap: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val switchingGroup: String? = null,
    val batchTestingGroup: String? = null,
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

internal fun computedSelectionTarget(
    group: ProxyGroupInfo,
    proxyName: String,
    selectedMap: Map<String, String> = emptyMap(),
): String? {
    if (!group.isSelectable || proxyName !in group.all) return null
    if (!group.isComputed && proxyName == (selectedMap[group.name] ?: group.now)) return null
    // 作者 long：computed 组的核心 `now` 是自动测速结果，selectedMap 才表示用户是否主动固定节点；
    // 点击自动节点第一次应固定它，再次点击同一固定节点才恢复自动选择。
    return if (group.isComputed && proxyName == selectedMap[group.name]) "" else proxyName
}

internal fun selectedProxyName(
    group: ProxyGroupInfo,
    selectedMap: Map<String, String> = emptyMap(),
): String? = selectedMap[group.name]
    ?.takeIf { it in group.all }
    ?: group.fixed?.takeIf { it in group.all }
    ?: group.now?.takeIf { it in group.all }

internal data class ProxySelectionResolution(
    val leafName: String?,
    val path: List<String>,
    val cycleDetected: Boolean = false,
)

/**
 * 作者 long：代理组成员可以继续指向其他代理组，页面显示和后续请求必须沿用户选择递归到叶子节点；
 * visited 集合保证异常配置中的自循环/环路只返回不可用结果，不会递归耗尽页面线程。
 */
internal fun resolveSelectedProxy(
    group: ProxyGroupInfo,
    groups: List<ProxyGroupInfo>,
    selectedMap: Map<String, String> = emptyMap(),
): ProxySelectionResolution {
    fun visit(current: ProxyGroupInfo, visiting: Set<String>, path: List<String>): ProxySelectionResolution {
        if (current.name in visiting) return ProxySelectionResolution(null, path, cycleDetected = true)
        val currentPath = path + current.name
        val selected = selectedProxyName(current, selectedMap)
            ?: return ProxySelectionResolution(null, currentPath)
        val nested = groups.firstOrNull { it.name == selected }
            ?: return ProxySelectionResolution(selected, currentPath + selected)
        return visit(nested, visiting + current.name, currentPath)
    }
    return visit(group, emptySet(), emptyList())
}

internal data class ProxyDelayTarget(
    val displayName: String,
    val request: ProxyDelayRequest,
)

/**
 * 作者 long：代理组可以把另一个代理组作为成员，批量测速必须沿当前选择递归到叶子节点；
 * 同一叶子和测试地址只发起一次请求，避免嵌套组重复占用 Mihomo 的测速槽位。
 */
internal fun resolveDelayTargets(
    group: ProxyGroupInfo,
    groups: List<ProxyGroupInfo>,
): List<ProxyDelayTarget> {
    val seen = linkedSetOf<ProxyDelayRequest>()
    return group.all
        .flatMap { proxyName ->
            resolveLeafTargets(
                displayName = proxyName,
                proxyName = proxyName,
                testUrl = group.testUrl.orDefaultTestUrl(),
                groups = groups,
                visiting = emptySet(),
            )
        }
        .filter { seen.add(it.request) }
}

private fun resolveLeafTargets(
    displayName: String,
    proxyName: String,
    testUrl: String,
    groups: List<ProxyGroupInfo>,
    visiting: Set<String>,
): List<ProxyDelayTarget> {
    val nestedGroup = groups.firstOrNull { it.name == proxyName }
        ?: return listOf(ProxyDelayTarget(displayName, ProxyDelayRequest(proxyName, testUrl)))
    if (nestedGroup.name in visiting) return emptyList()
    val nextVisiting = visiting + nestedGroup.name
    val nestedTestUrl = nestedGroup.testUrl?.takeIf { it.isNotBlank() } ?: testUrl
    val selected = nestedGroup.now?.takeIf { it.isNotBlank() }
    val members = selected?.let(::listOf) ?: nestedGroup.all
    return members.flatMap { member ->
        resolveLeafTargets(
            displayName = displayName,
            proxyName = member,
            testUrl = nestedTestUrl,
            groups = groups,
            visiting = nextVisiting,
        )
    }
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
    private var groupDelayJob: Job? = null

    val state: StateFlow<ProxyGroupsUiState> = mutableState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProxyGroupsUiState())
    val errors = mutableErrors.asSharedFlow()

    init {
        viewModelScope.launch {
            runtimeManager.state.collect { status ->
                if (status == CoreRuntimeStatus.RUNNING) {
                    refresh()
                } else {
                    groupDelayJob?.cancel()
                    groupDelayJob = null
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
                        selectedMap = mutableState.value.selectedMap,
                        batchTestingGroup = mutableState.value.batchTestingGroup,
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
        if (mutableState.value.batchTestingGroup != null) return
        val target = computedSelectionTarget(group, proxyName, mutableState.value.selectedMap) ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(switchingGroup = group.name)
            runtimeManager.changeProxy(group.name, target)
                .onSuccess { snapshot ->
                    mutableState.value = ProxyGroupsUiState(
                        groups = snapshot.groups,
                        selectedMap = updateSelectedMap(
                            current = mutableState.value.selectedMap,
                            group = group,
                            proxyName = proxyName,
                            target = target,
                        ),
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

    private fun updateSelectedMap(
        current: Map<String, String>,
        group: ProxyGroupInfo,
        proxyName: String,
        target: String,
    ): Map<String, String> = current.toMutableMap().apply {
        if (group.isComputed && target.isEmpty()) remove(group.name) else put(group.name, proxyName)
    }

    /**
     * 作者 long：结果键包含代理组、节点和测试地址，避免同名节点在不同代理组之间串用延迟结果。
     */
    fun testDelay(group: ProxyGroupInfo, proxyName: String) {
        if (mutableState.value.batchTestingGroup != null) return
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

    /**
     * 作者 long：批量测速的 UI 状态按原始组成员展示，实际请求使用递归解析后的叶子节点；
     * 这样嵌套代理组不会把组名误传给核心，也能在单个节点失败时保留其他节点结果。
     */
    fun testDelayGroup(group: ProxyGroupInfo) {
        if (mutableState.value.batchTestingGroup != null || group.all.isEmpty()) return
        val targets = resolveDelayTargets(group, mutableState.value.groups)
        if (targets.isEmpty()) {
            mutableErrors.tryEmit("当前代理组没有可测速节点")
            return
        }
        val keys = targets.associate { target ->
            ProxyDelayKey(group.name, target.displayName, target.request.testUrl) to
                ProxyDelayUiState(isTesting = true)
        }
        mutableState.update {
            it.copy(
                batchTestingGroup = group.name,
                delayResults = it.delayResults + keys,
            )
        }
        groupDelayJob?.cancel()
        groupDelayJob = viewModelScope.launch {
            try {
                runtimeManager.testDelayBatch(targets.map { it.request })
                    .onSuccess { results ->
                        val displayNames = targets.associateBy { it.request }
                        val updates = results.associate { item ->
                            val target = displayNames[item.request]
                            val displayName = target?.displayName ?: item.request.proxyName
                            ProxyDelayKey(group.name, displayName, item.request.testUrl) to
                                ProxyDelayUiState(
                                    value = when {
                                        item.error != null -> CoreBridge.DELAY_FAILED
                                        else -> item.result?.value
                                    },
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
                        }
                        val failures = results.count { it.error != null }
                        mutableState.update { state ->
                            state.copy(
                                batchTestingGroup = null,
                                delayResults = state.delayResults + updates,
                            )
                        }
                        if (failures > 0) {
                            mutableErrors.emit("批量测速完成：$failures 项失败")
                        }
                    }
                    .onFailure { error ->
                        val failed = keys.mapValues { (_, value) ->
                            value.copy(
                                isTesting = false,
                                value = CoreBridge.DELAY_FAILED,
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        mutableState.update { state ->
                            state.copy(
                                batchTestingGroup = null,
                                delayResults = state.delayResults + failed,
                            )
                        }
                        mutableErrors.emit(error.message ?: "批量测速失败")
                    }
            } finally {
                mutableState.update { state ->
                    if (state.batchTestingGroup == group.name) {
                        state.copy(batchTestingGroup = null)
                    } else {
                        state
                    }
                }
                groupDelayJob = null
            }
        }
    }
}

private fun String?.orDefaultTestUrl(): String =
    this?.takeIf { it.isNotBlank() } ?: CoreBridge.DEFAULT_TEST_URL
