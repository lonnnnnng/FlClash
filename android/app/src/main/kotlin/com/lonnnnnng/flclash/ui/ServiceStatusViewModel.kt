package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.data.ServiceSettings
import com.lonnnnnng.flclash.data.ServiceSettingsStore
import com.lonnnnnng.flclash.data.ServiceExitStore
import com.lonnnnnng.flclash.data.IpQualityException
import com.lonnnnnng.flclash.data.IpQualitySource
import com.lonnnnnng.flclash.data.IpSourceFailure
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ServiceStatusRuntime
import com.lonnnnnng.flclash.service.ServiceRouteChangedException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ServiceEntry(val result: CoreServiceCheck? = null, val loading: Boolean = false, val failed: Boolean = false)
data class ServiceStatusUiState(
    val settings: ServiceSettings = ServiceSettings(),
    val entries: Map<ServiceTarget, ServiceEntry> = emptyMap(),
    val saving: Boolean = false,
    val settingsLoading: Boolean = true,
    val error: String? = null,
    val exits: Map<String, OutboundIpUiState> = emptyMap(),
    val qualityNode: String? = null,
)

@HiltViewModel
class ServiceStatusViewModel internal constructor(
    private val runtime: ServiceStatusRuntime,
    private val store: ServiceSettingsStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val exitStore: ServiceExitStore? = null,
) : ViewModel() {
    @Inject constructor(runtime: ServiceStatusRuntime, store: ServiceSettingsStore, exits: ServiceExitStore) : this(runtime, store, Dispatchers.IO, exits)
    private val mutableState = MutableStateFlow(ServiceStatusUiState())
    val state = mutableState.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private val cardVisible = MutableStateFlow(false)
    private val detailVisible = MutableStateFlow(false)
    private val requests = mutableMapOf<ServiceTarget, Job>()
    private val queuedChecks = mutableSetOf<ServiceTarget>()
    private val exitRequests = mutableMapOf<String, Job>()
    private var qualityRequest: Job? = null
    private var qualityGeneration = 0L
    private val exitSlots = Semaphore(2)
    private var generation = 0L
    private var stamp: CoreRouteStamp? = null
    private var routeKey: List<Any?>? = null
    private var routeRevision: Long? = null
    private val candidates = mutableMapOf<ServiceTarget, CoreServiceCheck>()
    private val active get() = foreground.value && (cardVisible.value || detailVisible.value) &&
        runtime.state.value !in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)

    init {
        reloadSettings()
        viewModelScope.launch {
            val visible = combine(foreground, cardVisible, detailVisible) { f, c, d -> listOf(f, c, d) }
            val session = combine(runtime.state, runtime.runningSinceElapsedMillis, runtime.serviceCacheRevision) { status, since, revision ->
                listOf(status, since, revision)
            }
            combine(session, runtime.ipRouteRevision, visible,
                state.map { it.settings to it.settingsLoading }.distinctUntilChanged()) { sessionKey, revision, shown, settings ->
                listOf(sessionKey, revision, shown, settings)
            }.collectLatest { key ->
                val nextRoute = key[0] as List<*>
                cancelRequests()
                if (routeKey != nextRoute) {
                    routeKey = nextRoute
                    candidates.clear()
                    clearRouteResults()
                } else if (routeRevision != key[1]) {
                    rememberCandidates()
                    clearRouteResults()
                }
                routeRevision = key[1] as Long
                if (!active || state.value.settingsLoading) {
                    queuedChecks.clear()
                    return@collectLatest
                }
                var ticks = 0
                while (isActive) {
                    try {
                        val current = runtime.ipRouteStamp()
                        if (stamp != current) {
                            cancelRequests()
                            rememberCandidates()
                            clearRouteResults()
                            // 作者 long：核验期间隐藏旧结果，连续切换先等待稳定；后台或会话变化会取消整轮核验。
                            delay(1_500L)
                            if (runtime.ipRouteStamp() != current) continue
                            val cacheRevision = routeKey?.get(2) as? Long ?: continue
                            val previous = candidates.values.filter { it.stamp.epoch == current.epoch &&
                                !it.status.retryable && it.hits.isNotEmpty() && it.hits.all { hit -> hit.reusable } }
                            val held = if (previous.isEmpty()) emptyList() else try {
                                withTimeout(3_500L) { runtime.reuseServiceChecks(previous, current, cacheRevision) }
                            } catch (_: TimeoutCancellationException) { emptyList() }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { emptyList() }
                            currentCoroutineContext().ensureActive()
                            if (runtime.serviceCacheRevision.value != cacheRevision || runtime.ipRouteRevision.value != routeRevision ||
                                runtime.ipRouteStamp() != current) continue
                            // 作者 long：只换核验后的路由快照，保留原检测时间；IP 与质量另行重取，不借服务缓存复用。
                            candidates.clear()
                            stamp = current
                            val reusable = held.filter { it.stamp == current && previous.any { old ->
                                old.target == it.target && old.copy(stamp = current) == it
                            } }.associate { it.target to ServiceEntry(it) }
                            mutableState.update { it.copy(entries = reusable) }
                        }
                        if (queuedChecks.isNotEmpty()) {
                            val requested = queuedChecks.toList()
                            queuedChecks.clear()
                            check(requested)
                        }
                        // 作者 long：首页仅自动检测前两项；详情展示缓存，其余目标由用户主动检测，后台不启动新请求。
                        if (cardVisible.value) state.value.settings.enabled.take(2).forEach { target ->
                            val entry = state.value.entries[target]
                            if (entry == null || ((entry.failed || entry.result?.status?.retryable == true) && ticks % 5 == 0)) check(listOf(target))
                        }
                        if (detailVisible.value && exitStore != null) {
                            val nodes = state.value.settings.enabled.mapNotNull { state.value.entries[it]?.result?.chains?.firstOrNull() }.toSet()
                            mutableState.update { it.copy(exits = it.exits.filterKeys(nodes::contains), qualityNode = it.qualityNode?.takeIf(nodes::contains)) }
                            nodes.forEach { node ->
                                val entry = state.value.exits[node]
                                if (entry == null || (entry.error != null && ticks % 5 == 0)) refreshExit(node)
                            }
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) {
                        cancelRequests()
                        candidates.clear()
                        stamp = null
                        mutableState.update { it.copy(entries = emptyMap(), exits = emptyMap(), qualityNode = null, error = "出口状态读取失败，请重试") }
                    }
                    ticks++
                    delay(1_000L)
                }
            }
        }
    }

    fun setForeground(value: Boolean) { foreground.value = value }
    fun setVisible(value: Boolean) { cardVisible.value = value }
    fun setDetailVisible(value: Boolean) { detailVisible.value = value }

    private fun rememberCandidates() {
        state.value.entries.forEach { (target, entry) ->
            entry.result?.takeIf { !entry.loading && !entry.failed }?.let { candidates[target] = it }
        }
    }

    private fun clearRouteResults() {
        stamp = null
        mutableState.update { it.copy(entries = emptyMap(), exits = emptyMap(), qualityNode = null) }
    }

    private fun cancelRequests() {
        generation++
        requests.values.forEach(Job::cancel)
        requests.clear()
        exitRequests.values.forEach(Job::cancel)
        exitRequests.clear()
        qualityRequest?.cancel()
        qualityGeneration++
        mutableState.update { it.copy(entries = it.entries.filterValues { entry -> !entry.loading },
            exits = it.exits.filterValues { entry -> !entry.isLoading }.mapValues { (_, entry) -> entry.copy(qualityLoading = false) }, qualityNode = null) }
    }

    fun check(targets: List<ServiceTarget> = state.value.settings.enabled) {
        if (!active || state.value.settingsLoading) return
        if (stamp == null) {
            // 作者 long：用户在路由稳定等待期间点击检测，保留意图到新路由就绪；离开或后台则取消。
            queuedChecks += targets.filter { it in state.value.settings.enabled }
            return
        }
        targets.distinct().filter { it in state.value.settings.enabled && requests[it]?.isActive != true }.forEach { target ->
            val token = generation
            val expected = stamp
            mutableState.update { it.copy(error = null, entries = it.entries + (target to ServiceEntry(loading = true))) }
            requests[target] = viewModelScope.launch {
                try {
                    val result = withTimeout(42_000L) { runtime.checkServices(listOf(target)).single() }
                    val current = runtime.ipRouteStamp()
                    if (token != generation) return@launch
                    if (result.target != target || result.stamp != current || (expected != null && expected != current)) {
                        stamp = null
                        mutableState.update { it.copy(entries = it.entries - target) }
                    } else {
                        mutableState.update { it.copy(entries = it.entries + (target to ServiceEntry(result))) }
                        // 作者 long：同一节点可能使用轮换出口，手动重测成功后也重新取 IP，不能只依赖路由版本变化。
                        result.chains.firstOrNull()?.let { if (detailVisible.value) refreshExit(it) }
                    }
                } catch (_: TimeoutCancellationException) {
                    if (token == generation) failed(target)
                } catch (_: ServiceRouteChangedException) {
                    if (token == generation) {
                        stamp = null
                        mutableState.update { it.copy(entries = it.entries - target) }
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { if (token == generation) failed(target) }
                finally { if (token == generation) requests.remove(target) }
            }
        }
    }

    private fun failed(target: ServiceTarget) { mutableState.update { it.copy(entries = it.entries + (target to ServiceEntry(failed = true))) } }

    fun refreshExit(node: String) {
        val repository = exitStore ?: return
        if (!active || !detailVisible.value || exitRequests[node]?.isActive == true) return
        val expected = state.value.settings.enabled.mapNotNull { state.value.entries[it]?.result }
            .firstOrNull { it.chains.firstOrNull() == node }?.stamp ?: return
        val token = generation
        mutableState.update { it.copy(exits = it.exits + (node to OutboundIpUiState(isLoading = true)),
            qualityNode = it.qualityNode?.takeUnless { value -> value == node }) }
        exitRequests[node] = viewModelScope.launch {
            try {
                val info = exitSlots.withPermit { withTimeout(18_000L) { repository.outbound(node, expected) } }
                val current = runtime.ipRouteStamp()
                if (token != generation) return@launch
                if (expected != current) invalidateExit(node)
                else mutableState.update { it.copy(exits = it.exits + (node to OutboundIpUiState(info = info))) }
            } catch (_: TimeoutCancellationException) { if (token == generation) exitFailed(node) }
            catch (_: ServiceRouteChangedException) { if (token == generation) invalidateExit(node) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (token == generation) exitFailed(node) }
            finally { if (token == generation) exitRequests.remove(node) }
        }
    }

    private fun invalidateExit(node: String) {
        stamp = null
        mutableState.update { it.copy(exits = it.exits - node, qualityNode = null) }
    }
    private fun exitFailed(node: String) {
        mutableState.update { it.copy(exits = it.exits + (node to OutboundIpUiState(error = "出口 IP 查询失败，请重试"))) }
    }

    fun openQuality(node: String) {
        if (!active || !detailVisible.value || state.value.exits[node]?.info == null) return
        mutableState.update { it.copy(qualityNode = node) }
        refreshQuality()
    }
    fun closeQuality() {
        qualityRequest?.cancel()
        qualityGeneration++
        mutableState.update { current -> current.copy(qualityNode = null,
            exits = current.exits.mapValues { (_, entry) -> entry.copy(qualityLoading = false) }) }
    }
    fun refreshQuality(force: Boolean = false) {
        val repository = exitStore ?: return
        val node = state.value.qualityNode ?: return
        val ip = state.value.exits[node]?.info?.ip ?: return
        if (!active || !detailVisible.value || state.value.exits[node]?.qualityLoading == true) return
        qualityRequest?.cancel()
        val qualityToken = ++qualityGeneration
        val token = generation
        mutableState.update { it.copy(exits = it.exits + (node to it.exits.getValue(node).copy(qualityLoading = true, qualityFailures = emptyMap(), qualityError = null))) }
        qualityRequest = viewModelScope.launch {
            fun current() = token == generation && qualityToken == qualityGeneration && state.value.qualityNode == node && state.value.exits[node]?.info?.ip == ip
            try {
                val quality = withTimeout(30_000L) { repository.quality(ip, force) }
                if (current()) {
                    check(quality.ip == ip) { "IP 不一致" }
                    mutableState.update { it.copy(exits = it.exits + (node to it.exits.getValue(node).copy(quality = quality, qualityLoading = false))) }
                }
            } catch (_: TimeoutCancellationException) {
                if (current()) qualityFailed(node, emptyMap(), "IP 类型查询超时，请重试")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current()) qualityFailed(node, (error as? IpQualityException)?.failures.orEmpty(),
                    if (error is IpQualityException) null else "IP 类型查询失败，请重试")
            }
        }
    }
    private fun qualityFailed(node: String, failures: Map<IpQualitySource, IpSourceFailure>, error: String? = null) {
        mutableState.update { it.copy(exits = it.exits + (node to it.exits.getValue(node).copy(qualityLoading = false, qualityFailures = failures, qualityError = error))) }
    }

    fun toggle(target: ServiceTarget, enabled: Boolean) {
        val current = state.value.settings
        if (!enabled && current.enabled.size == 1 && target in current.enabled) return
        save(current.copy(disabled = if (enabled) current.disabled - target else current.disabled + target))
    }

    fun move(target: ServiceTarget, offset: Int) {
        val settings = state.value.settings
        val from = settings.order.indexOf(target)
        val to = from + offset
        if (from < 0 || to !in settings.order.indices) return
        save(settings.copy(order = settings.order.toMutableList().apply { add(to, removeAt(from)) }))
    }

    fun reorder(order: List<ServiceTarget>) {
        val current = state.value.settings
        // 作者 long：拖动只能提交现有目标的完整排列，缺项、重复或相同顺序不能覆盖已保存设置。
        if (order.size != current.order.size || order.toSet() != current.order.toSet() || order == current.order) return
        save(current.copy(order = order.toList()))
    }

    private fun save(settings: ServiceSettings) {
        if (state.value.saving || state.value.settingsLoading) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                // 作者 long：成功落盘才切换目标列表，保存失败不能丢失用户之前可用的排序和启用选择。
                withContext(ioDispatcher) { store.write(settings) }
                mutableState.update { it.copy(settings = settings, saving = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(saving = false, error = "保存失败，请重试") } }
        }
    }

    fun reloadSettings() {
        viewModelScope.launch {
            try {
                val settings = withContext(ioDispatcher) { store.read().normalized() }
                mutableState.update { it.copy(settings = settings, settingsLoading = false, error = null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(error = "读取服务设置失败，请重试") } }
        }
    }
}
