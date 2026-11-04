package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.NoopProxyDelayStore
import com.lonnnnnng.flclash.data.ProxyDelayEntity
import com.lonnnnnng.flclash.data.ProxyDelayStore
import com.lonnnnnng.flclash.data.decodeSelectedMap
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProxyGroupsRuntime
import com.lonnnnnng.flclash.service.ProxyDelayRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

data class ProxyGroupsUiState(
    val groups: List<ProxyGroupInfo> = emptyList(),
    val selectedMap: Map<String, String> = emptyMap(),
    val unfoldSet: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val switchingGroup: String? = null,
    val batchTestingGroup: String? = null,
    val delayResults: Map<ProxyDelayKey, ProxyDelayUiState> = emptyMap(),
    val proxyTypes: Map<String, String> = emptyMap(),
    val providerProxies: Set<String> = emptySet(),
    val profileId: Long? = null,
    val customProfile: Boolean = false,
)

// 作者 long：首页卡片与完整代理页共用受理条件，入口不同不能绕过启停、节点切换或批量测速的忙状态。
internal fun ProxyGroupsUiState.canRefresh(runtime: CoreRuntimeStatus): Boolean =
    runtime != CoreRuntimeStatus.STARTING && runtime != CoreRuntimeStatus.STOPPING &&
        !isLoading && switchingGroup == null && batchTestingGroup == null

internal fun ProxyGroupsUiState.canAddToLocal(name: String): Boolean =
    name !in com.lonnnnnng.flclash.data.LocalProxies.reserved && groups.none { it.name == name } &&
        proxyTypes[name]?.lowercase() !in setOf("selector", "select", "urltest", "url-test", "fallback", "loadbalance", "load-balance", "relay") &&
        (!customProfile || name in providerProxies)

internal fun ProxyGroupsUiState.proxyType(name: String, cardSize: ProxyNodeCardSize): String? {
    val group = groups.firstOrNull { it.name == name }
    val type = proxyTypes[name]?.takeIf { it.isNotBlank() } ?: group?.type ?: return null
    if (group == null || cardSize != ProxyNodeCardSize.LOOSE) return type
    val leaf = resolveSelectedProxy(group, groups, selectedMap).leafName
    // 作者 long：宽松卡片沿用原版“组类型(最终出口)”说明；叶子只显示自己的协议，不借用组或出口的协议。
    return if (leaf.isNullOrBlank() || leaf == name) type else "$type($leaf)"
}

internal fun visibleProxyGroups(groups: List<ProxyGroupInfo>, mode: CoreOutboundMode): List<ProxyGroupInfo> =
    // 作者 long：直连模式只改变真实路由，不隐藏节点管理；用户仍可预选节点和测速，切回代理模式立即生效。
    when (mode) {
        CoreOutboundMode.DIRECT -> groups.filterNot { it.hidden }
        CoreOutboundMode.RULE -> groups.filterNot { it.hidden }
        CoreOutboundMode.GLOBAL -> groups
    }

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

data class ProxyDelaySummary(
    val tested: Int = 0,
    val success: Int = 0,
    val timedOut: Int = 0,
    val failed: Int = 0,
    val testing: Int = 0,
)

/**
 * 作者 long：汇总只读取当前组和当前测速 URL 的直接成员，避免同名节点或其他 URL 的旧结果污染组标题。
 * 空结果不计入失败，用户可以据此区分“尚未测速”和“本次测速明确失败”。
 */
fun ProxyGroupsUiState.delaySummary(
    group: ProxyGroupInfo,
    nowMillis: Long = System.currentTimeMillis(),
): ProxyDelaySummary {
    var success = 0
    var timedOut = 0
    var failed = 0
    var testing = 0
    group.all.forEach { proxyName ->
        val result = delayState(group, proxyName, nowMillis) ?: return@forEach
        when {
            result.isTesting -> testing++
            result.value == null -> Unit
            result.value > 0 -> success++
            result.value == CoreBridge.DELAY_TIMED_OUT -> timedOut++
            else -> failed++
        }
    }
    return ProxyDelaySummary(
        tested = success + timedOut + failed,
        success = success,
        timedOut = timedOut,
        failed = failed,
        testing = testing,
    )
}

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
internal fun delayUpdatesForEvent(
    groups: List<ProxyGroupInfo>,
    event: CoreEvent.Delay,
    updatedAtMillis: Long = System.currentTimeMillis(),
): Map<ProxyDelayKey, ProxyDelayUiState> = groups
        .filter { group ->
            group.testUrl.orDefaultTestUrl() == event.result.url && event.result.name in group.all
        }
        .associate { group ->
            ProxyDelayKey(group.name, event.result.name, event.result.url) to
                ProxyDelayUiState(
                    value = event.result.value,
                    updatedAtMillis = updatedAtMillis,
                )
        }

internal fun ProxyGroupsUiState.applyDelayEvent(event: CoreEvent.Delay): ProxyGroupsUiState {
    val updates = delayUpdatesForEvent(groups, event)
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
): String? {
    val selected = selectedMap[group.name]?.takeIf { it in group.all }
    val fixed = group.fixed?.takeIf { it in group.all }
    val now = group.now?.takeIf { it in group.all }
    // 作者 long：URLTest/Fallback 的 `now` 是核心当前自动结果，必须优先于旧的手动固定值；
    // 核心暂时没有返回结果时，再用手动映射和 fixed 恢复可见选择，避免页面显示过期节点。
    return if (group.isComputed) now ?: selected ?: fixed else selected ?: fixed ?: now
}

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
    selectedMap: Map<String, String> = emptyMap(),
): List<ProxyDelayTarget> {
    val seen = linkedSetOf<ProxyDelayRequest>()
    return group.all
        .flatMap { proxyName ->
            resolveLeafTargets(
                displayName = proxyName,
                proxyName = proxyName,
                testUrl = group.testUrl.orDefaultTestUrl(),
                groups = groups,
                selectedMap = selectedMap,
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
    selectedMap: Map<String, String>,
    visiting: Set<String>,
): List<ProxyDelayTarget> {
    val nestedGroup = groups.firstOrNull { it.name == proxyName }
        ?: return listOf(ProxyDelayTarget(displayName, ProxyDelayRequest(proxyName, testUrl)))
    if (nestedGroup.name in visiting) return emptyList()
    val nextVisiting = visiting + nestedGroup.name
    val nestedTestUrl = nestedGroup.testUrl?.takeIf { it.isNotBlank() } ?: testUrl
    // 作者 long：测速必须与页面当前选择一致；Selector 先取手动映射，URLTest/Fallback 仍遵循 now、手动映射、fixed 的统一优先级。
    val selected = selectedProxyName(nestedGroup, selectedMap)
    val members = selected?.let(::listOf) ?: nestedGroup.all
    return members.flatMap { member ->
        resolveLeafTargets(
            displayName = displayName,
            proxyName = member,
            testUrl = nestedTestUrl,
            groups = groups,
            selectedMap = selectedMap,
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
    private val runtimeManager: ProxyGroupsRuntime,
    private val profileStore: ProfileStore,
    private val proxyDelayStore: ProxyDelayStore = NoopProxyDelayStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProxyGroupsUiState())
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val profileViewStateMutex = Mutex()
    private var groupDelayJob: Job? = null
    private var refreshJob: Job? = null
    private var snapshotReadJob: Job? = null
    private var profileGeneration = 0L
    private var refreshAfterSelection = false

    val state: StateFlow<ProxyGroupsUiState> = mutableState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProxyGroupsUiState())
    val errors = mutableErrors.asSharedFlow()

    init {
        viewModelScope.launch {
            var observedInputs: List<Any?>? = null
            combine(runtimeManager.state, profileStore.currentProfileId, profileStore.profiles, runtimeManager.proxyGroupsRevision) { status, id, profiles, revision ->
                val profile = profiles.firstOrNull { it.id == id }
                listOf(status, id, profile?.lastUpdateAt, profile?.overridesJson, profile?.scriptId, revision)
            }.distinctUntilChanged().collect { key ->
                val inputs = key.dropLast(1)
                if (inputs != observedInputs) {
                    observedInputs = inputs
                    profileGeneration++
                    refreshAfterSelection = false
                    groupDelayJob?.cancel()
                    refreshJob?.cancel()
                    groupDelayJob = null
                    // 作者 long：只有实际配置输入或运行状态变化才作废操作；节点切换自身的刷新通知不能丢掉待保存选择。
                    mutableState.value = ProxyGroupsUiState(profileId = key[1] as Long?, isLoading = key[1] != null)
                } else if (mutableState.value.switchingGroup != null) {
                    // 作者 long：先完成选择的保存，再消费期间的刷新通知；既不抢先显示旧映射，也不漏掉并发资源更新。
                    refreshAfterSelection = true
                    return@collect
                }
                if (key[1] != null) refresh()
            }
        }
        viewModelScope.launch {
            runtimeManager.events.collect { event ->
                if (event is CoreEvent.Delay) {
                    val profileId = profileStore.currentProfileId.value
                    val generation = profileGeneration
                    val timestamp = System.currentTimeMillis()
                    val updates = delayUpdatesForEvent(mutableState.value.groups, event, timestamp)
                    if (updates.isEmpty()) return@collect
                    if (generation == profileGeneration && profileId == profileStore.currentProfileId.value) {
                        mutableState.update { it.copy(delayResults = it.delayResults + updates) }
                        persistDelayUpdates(profileId, generation, updates)
                    }
                }
            }
        }
    }

    fun refresh() {
        if (mutableState.value.switchingGroup != null) return
        refreshJob?.cancel()
        snapshotReadJob?.cancel()
        val generation = profileGeneration
        val profileId = profileStore.currentProfileId.value
        // 作者 long：页面等待与已受理的核心加载分开；8秒后可显示重试，管理器仍负责原子完成不可取消的配置登记。
        val reading = viewModelScope.async { if (profileId == null) runtimeManager.getProxyGroups() else runtimeManager.getProxyGroups(profileId) }
        snapshotReadJob = reading
        refreshJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            val result = try {
                withTimeout(8_000L) { reading.await() }
            } catch (timeout: TimeoutCancellationException) {
                Result.failure(timeout)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
            finally {
                reading.cancel()
                if (snapshotReadJob === reading) snapshotReadJob = null
            }
            if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
            if (result.isSuccess) {
                val snapshot = result.getOrThrow()
                val delayResults = try {
                    restoreDelayResults(profileId, snapshot.groups, mutableState.value.delayResults)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    mutableErrors.emit(error.message ?: "恢复测速结果失败")
                    retainDelayResults(snapshot.groups, mutableState.value.delayResults)
                }
                if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
                val profile = try {
                    withTimeout(3_000L) { profileId?.let { profileStore.extensionProfile(it) } }
                } catch (error: Exception) {
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    if (generation == profileGeneration && profileId == profileStore.currentProfileId.value) {
                        mutableState.value = mutableState.value.copy(isLoading = false, error = "无法读取配置选择，请重试")
                    }
                    return@launch
                }
                if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
                mutableState.value = ProxyGroupsUiState(
                    groups = snapshot.groups,
                    proxyTypes = snapshot.proxyTypes,
                    providerProxies = snapshot.providerProxies,
                    profileId = profileId,
                    customProfile = profileStore.profiles.value.firstOrNull { it.id == profileId }?.type == com.lonnnnnng.flclash.data.ProfileType.CUSTOM,
                    selectedMap = profile?.let { decodeSelectedMap(it.selectedMapJson) }.orEmpty(),
                    unfoldSet = profile?.unfoldSet.orEmpty(),
                    batchTestingGroup = mutableState.value.batchTestingGroup,
                    delayResults = delayResults,
                )
            } else {
                val error = result.exceptionOrNull() ?: IllegalStateException("读取代理组失败")
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
                val message = if (error is TimeoutCancellationException) "读取代理组超时，请重试" else "无法读取代理组，请重试"
                mutableState.value = mutableState.value.copy(isLoading = false, error = message)
                mutableErrors.emit(message)
            }
        }
    }

    fun selectGroup(name: String) = selectGroup(mutableState.value.profileId, name)

    fun selectGroup(expectedProfileId: Long?, name: String) {
        if (name.isBlank() || mutableState.value.groups.none { it.name == name }) return
        val profileId = profileStore.currentProfileId.value ?: return
        if (expectedProfileId != profileId) return
        val generation = profileGeneration
        viewModelScope.launch {
            if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
            // 作者 long：只把当前核心快照中的组名记入当前 Profile，避免无效标签覆盖重启恢复位置。
            try {
                profileStore.updateProfileCurrentGroupName(profileId, name)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableErrors.emit(error.message ?: "保存当前代理组失败")
            }
        }
    }

    fun selectGlobalGroup() {
        val profileId = profileStore.currentProfileId.value ?: return
        viewModelScope.launch {
            try {
                // 作者 long：首页切到 Global 时与 Flutter 一致地把代理页恢复位置指向 GLOBAL，即使代理页尚未加载。
                profileStore.updateProfileCurrentGroupName(profileId, "GLOBAL")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableErrors.emit(error.message ?: "保存当前代理组失败")
            }
        }
    }

    fun toggleGroupExpanded(name: String) = toggleGroupExpanded(mutableState.value.profileId, name)

    fun toggleGroupExpanded(expectedProfileId: Long?, name: String) {
        if (name.isBlank() || mutableState.value.groups.none { it.name == name }) return
        val profileId = profileStore.currentProfileId.value ?: return
        if (expectedProfileId != profileId) return
        if (mutableState.value.profileId != profileId) return
        val generation = profileGeneration
        val previous = mutableState.value.unfoldSet
        val updated = previous.toMutableSet().apply {
            if (!add(name)) remove(name)
        }.toSet()
        // 作者 long：先更新页面状态让折叠反馈立即出现，再把同一集合写回当前 Profile；失败时回滚，避免视觉状态与下次恢复不一致。
        mutableState.update { it.copy(unfoldSet = updated) }
        viewModelScope.launch {
            try {
                profileViewStateMutex.withLock {
                    if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@withLock
                    // 作者 long：持锁后提交本次手势的快照，不能从已经换配置的页面重新取展开集合。
                    profileStore.updateProfileGroupViewState(profileId, name, updated)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update { state ->
                    if (generation == profileGeneration && profileId == profileStore.currentProfileId.value && state.unfoldSet == updated) state.copy(unfoldSet = previous) else state
                }
                mutableErrors.emit(error.message ?: "保存代理组展开状态失败")
            }
        }
    }

    fun selectProxy(group: ProxyGroupInfo, proxyName: String) = selectProxy(mutableState.value.profileId, group, proxyName)

    fun selectProxy(expectedProfileId: Long?, group: ProxyGroupInfo, proxyName: String) {
        val stateBeforeSwitch = mutableState.value
        if (stateBeforeSwitch.batchTestingGroup != null || stateBeforeSwitch.switchingGroup != null || stateBeforeSwitch.isLoading) return
        val profileId = profileStore.currentProfileId.value ?: return
        if (expectedProfileId != profileId) return
        if (stateBeforeSwitch.profileId != profileId || stateBeforeSwitch.groups.none { it == group }) return
        val generation = profileGeneration
        val target = computedSelectionTarget(group, proxyName, stateBeforeSwitch.selectedMap) ?: return
        val updatedMap = updateSelectedMap(
            current = stateBeforeSwitch.selectedMap,
            group = group,
            proxyName = proxyName,
            target = target,
        )
        // 作者 long：在派发协程前认领操作，连续点击只能受理一次，后续请求仍由核心锁核验原配置。
        mutableState.value = mutableState.value.copy(switchingGroup = group.name)
        viewModelScope.launch {
            if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
            runtimeManager.changeProxy(profileId, group.name, target)
                .onSuccess { snapshot ->
                    // 作者 long：核心已受理的选择仍保存到原配置；随后切到另一配置只阻止旧快照回写页面。
                    var savedMap = updatedMap
                    runCatching {
                        profileStore.updateProfileProxySelection(profileId, group.name, target.takeIf { it.isNotEmpty() })
                    }.onSuccess { saved -> savedMap = decodeSelectedMap(saved.selectedMapJson) }.onFailure { error ->
                        if (error is CancellationException) throw error
                        // 作者 long：核心切换已经成功时仍显示真实核心状态，但明确提示持久化失败，
                        // 避免用户误以为进程重建后仍会恢复本次选择。
                        mutableErrors.emit(error.message ?: "保存代理选择失败")
                    }
                    // 作者 long：持久化可能让出执行权，切配置后不能把旧节点快照写回新页面。
                    if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@onSuccess
                    mutableState.value = ProxyGroupsUiState(
                        groups = snapshot.groups,
                        proxyTypes = snapshot.proxyTypes,
                        providerProxies = snapshot.providerProxies,
                        profileId = profileId,
                        customProfile = profileStore.profiles.value.firstOrNull { it.id == profileId }?.type == com.lonnnnnng.flclash.data.ProfileType.CUSTOM,
                        selectedMap = savedMap,
                        unfoldSet = currentProfileUnfoldSet(),
                        delayResults = retainDelayResults(
                            snapshot.groups,
                            mutableState.value.delayResults,
                        ),
                    )
                    if (refreshAfterSelection) { refreshAfterSelection = false; refresh() }
                }
                .onFailure { error ->
                    if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@onFailure
                    mutableState.value = mutableState.value.copy(switchingGroup = null)
                    mutableErrors.emit(error.message ?: "切换代理失败")
                    if (refreshAfterSelection) { refreshAfterSelection = false; refresh() }
                }
        }
    }

    internal fun updateSelectedMap(
        current: Map<String, String>,
        group: ProxyGroupInfo,
        proxyName: String,
        target: String,
    ): Map<String, String> = current.toMutableMap().apply {
        if (group.isComputed && target.isEmpty()) remove(group.name) else put(group.name, proxyName)
    }

    private fun currentProfileSelectedMap(): Map<String, String> =
        profileStore.currentProfileId.value
            ?.let { id -> profileStore.profiles.value.firstOrNull { it.id == id } }
            ?.let { decodeSelectedMap(it.selectedMapJson) }
            .orEmpty()

    private suspend fun restoreDelayResults(
        profileId: Long?,
        groups: List<ProxyGroupInfo>,
        current: Map<ProxyDelayKey, ProxyDelayUiState>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<ProxyDelayKey, ProxyDelayUiState> {
        val retained = retainDelayResults(groups, current, nowMillis)
        if (profileId == null) return retained
        proxyDelayStore.deleteOlderThan(profileId, nowMillis - DELAY_RESULT_TTL_MS)
        val valid = groups.flatMap { group ->
            group.all.map { proxy -> ProxyDelayKey(group.name, proxy, group.testUrl.orDefaultTestUrl()) }
        }.toSet()
        val restored = proxyDelayStore.load(profileId)
            .asSequence()
            .map { entity ->
                ProxyDelayKey(entity.groupName, entity.proxyName, entity.testUrl) to
                    ProxyDelayUiState(value = entity.value, updatedAtMillis = entity.updatedAtMillis)
            }
            .filter { (key, value) ->
                key in valid && nowMillis - value.updatedAtMillis <= DELAY_RESULT_TTL_MS
            }
            .toMap()
        // 作者 long：当前进程正在进行的测速状态优先于磁盘快照，避免恢复动作覆盖用户刚点下的“测速中”。
        return restored + retained
    }

    private suspend fun persistDelayUpdates(
        profileId: Long?,
        generation: Long,
        updates: Map<ProxyDelayKey, ProxyDelayUiState>,
    ) {
        if (profileId == null) return
        updates.forEach { (key, value) ->
            if (value.isTesting || generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@forEach
            try {
                persistDelayResult(profileId, key, value)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableErrors.emit(error.message ?: "保存测速结果失败")
            }
        }
    }

    private suspend fun persistDelayResult(
        profileId: Long,
        key: ProxyDelayKey,
        value: ProxyDelayUiState,
    ) {
        val result = value.value
        if (result == null) {
            // 作者 long：空结果代表核心没有返回可用数值，必须删除旧缓存，不能在重建后伪造失败。
            proxyDelayStore.delete(profileId, key.groupName, key.proxyName, key.testUrl)
        } else {
            proxyDelayStore.upsert(
                ProxyDelayEntity(
                    profileId = profileId,
                    groupName = key.groupName,
                    proxyName = key.proxyName,
                    testUrl = key.testUrl,
                    value = result,
                    updatedAtMillis = value.updatedAtMillis,
                ),
            )
        }
    }

    private fun currentProfileUnfoldSet(): Set<String> =
        profileStore.currentProfileId.value
            ?.let { id -> profileStore.profiles.value.firstOrNull { it.id == id } }
            ?.unfoldSet
            .orEmpty()

    /**
     * 作者 long：结果键包含代理组、节点和测试地址，避免同名节点在不同代理组之间串用延迟结果。
     */
    fun testDelay(group: ProxyGroupInfo, proxyName: String) = testDelay(mutableState.value.profileId, group, proxyName)

    fun testDelay(expectedProfileId: Long?, group: ProxyGroupInfo, proxyName: String) {
        if (mutableState.value.batchTestingGroup != null || mutableState.value.switchingGroup != null || mutableState.value.isLoading) return
        val profileId = profileStore.currentProfileId.value ?: return
        if (expectedProfileId != profileId) return
        if (mutableState.value.profileId != profileId || mutableState.value.groups.none { it == group } || proxyName !in group.all) return
        val key = ProxyDelayKey(group.name, proxyName, group.testUrl.orDefaultTestUrl())
        val generation = profileGeneration
        if (mutableState.value.delayResults[key]?.isTesting == true) return
        mutableState.update {
            it.copy(delayResults = it.delayResults + (key to ProxyDelayUiState(isTesting = true)))
        }
        viewModelScope.launch {
            if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
            val outcome = runtimeManager.testDelay(profileId, proxyName, key.testUrl)
            if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
            val updated: ProxyDelayUiState
            if (outcome.isSuccess) {
                updated = ProxyDelayUiState(
                    value = outcome.getOrNull()?.value,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            } else {
                updated = ProxyDelayUiState(
                    value = CoreBridge.DELAY_FAILED,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            mutableState.update { state ->
                state.copy(delayResults = state.delayResults + (key to updated))
            }
            try {
                if (generation == profileGeneration && profileId == profileStore.currentProfileId.value) {
                    persistDelayResult(profileId, key, updated)
                }
            } catch (persistError: CancellationException) {
                throw persistError
            } catch (persistError: Throwable) {
                mutableErrors.emit(persistError.message ?: "保存测速结果失败")
            }
            if (outcome.isFailure) {
                mutableErrors.emit(outcome.exceptionOrNull()?.message ?: "测速失败")
            }
        }
    }

    /**
     * 作者 long：批量测速的 UI 状态按原始组成员展示，实际请求使用递归解析后的叶子节点；
     * 这样嵌套代理组不会把组名误传给核心，也能在单个节点失败时保留其他节点结果。
     */
    fun testDelayGroup(group: ProxyGroupInfo) = testDelayGroup(mutableState.value.profileId, group)

    fun testDelayGroup(expectedProfileId: Long?, group: ProxyGroupInfo) {
        if (mutableState.value.batchTestingGroup != null || mutableState.value.switchingGroup != null || mutableState.value.isLoading || group.all.isEmpty()) return
        val profileId = profileStore.currentProfileId.value ?: return
        if (expectedProfileId != profileId) return
        if (mutableState.value.profileId != profileId || mutableState.value.groups.none { it == group }) return
        val targets = resolveDelayTargets(group, mutableState.value.groups, mutableState.value.selectedMap)
        if (targets.isEmpty()) {
            mutableErrors.tryEmit("当前代理组没有可测速节点")
            return
        }
        val generation = profileGeneration
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
                if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
                val outcome = runtimeManager.testDelayBatch(profileId, targets.map { it.request })
                if (generation != profileGeneration || profileId != profileStore.currentProfileId.value) return@launch
                if (outcome.isSuccess) {
                    val results = outcome.getOrThrow()
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
                    persistDelayUpdates(
                        profileId = profileId,
                        generation = generation,
                        updates = updates,
                    )
                    if (failures > 0) {
                        mutableErrors.emit("批量测速完成：$failures 项失败")
                    }
                } else {
                    val error = outcome.exceptionOrNull()
                    if (error is CancellationException) throw error
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
                    persistDelayUpdates(
                        profileId = profileId,
                        generation = generation,
                        updates = failed,
                    )
                    mutableErrors.emit(error?.message ?: "批量测速失败")
                }
            } finally {
                // 作者 long：旧任务的取消收尾不能结束新配置中同名代理组的测速。
                if (generation == profileGeneration && profileId == profileStore.currentProfileId.value) {
                    mutableState.update { state ->
                        if (state.batchTestingGroup == group.name) state.copy(batchTestingGroup = null) else state
                    }
                    groupDelayJob = null
                }
            }
        }
    }
}

private fun String?.orDefaultTestUrl(): String =
    this?.takeIf { it.isNotBlank() } ?: CoreBridge.DEFAULT_TEST_URL
