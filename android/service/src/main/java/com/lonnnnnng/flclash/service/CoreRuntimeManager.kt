package com.lonnnnnng.flclash.service

import android.os.Build
import android.os.SystemClock
import com.lonnnnnng.flclash.core.CoreIpResponse
import kotlinx.coroutines.withTimeout
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.service.models.NotificationParams
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CoreRuntimeStatus(
    val label: String,
    val isRunning: Boolean,
) {
    STOPPED("核心已停止", false),
    STARTING("核心启动中", false),
    RUNNING("核心运行中", true),
    STOPPING("核心停止中", false),
    FAILED("核心启动失败", false),
}

interface CoreRuntimeStatusProvider {
    val state: StateFlow<CoreRuntimeStatus>
    suspend fun selectProfile(id: Long, select: suspend (Long?) -> Unit) { select(id) }
}

/**
 * 作者 long：所有页面和服务共用这一条核心生命周期，避免启动请求在 UI、VPN 服务之间产生两个事实来源。
 */
class CoreRuntimeManager(
    private val paths: CoreRuntimePaths,
    private val profileProvider: ActiveProfileProvider,
    private val bridge: CoreRuntimeBridge,
    private val serviceController: ProxyServiceController,
    private val vpnOptionsProvider: VpnOptionsProvider,
    private val runtimeResumeStore: RuntimeResumeStore,
    private val runtimeDiagnosticStore: RuntimeDiagnosticStore = NoopRuntimeDiagnosticStore,
    private val profileScriptEvaluator: ProfileScriptEvaluator = MissingProfileScriptEvaluator,
    private val outboundModeStore: OutboundModeStore = NoopOutboundModeStore,
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val lanSharingStore: LanSharingStore = NoopLanSharingStore,
    private val vpnSettingsStore: VpnSettingsStore = DefaultVpnSettingsStore,
) : ProxyGroupsRuntime, OutboundIpRuntime, RuntimeConfigPreviewProvider, ProfileStatisticsRuntime, MemoryRuntime, LanSharingRuntime, ServiceStatusRuntime {
    init {
        RuntimeDiagnosticHistory.configure(runtimeDiagnosticStore)
    }

    private val transitionLock = Mutex()
    private val mutableVpnSettings = MutableStateFlow(VpnSettingsState(vpnSettingsStore.read()))
    val vpnSettings = mutableVpnSettings.asStateFlow()

    suspend fun setVpnEnabled(enabled: Boolean): Result<Unit> = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                // 作者 long：运行中只保存下次接管方式，用户点击重启后才中断现有连接。
                vpnSettingsStore.write(enabled)
                mutableVpnSettings.update { it.copy(enabled = enabled) }
            }
        }
    }

    suspend fun restartForVpnSettings(): Result<Unit> = transitionLock.withLock {
        if (!mutableState.value.isRunning || !mutableVpnSettings.value.restartRequired) return@withLock Result.success(Unit)
        stopLocked().getOrElse { return@withLock Result.failure(it) }
        startLocked()
    }

    private val initialized = AtomicBoolean(false)
    private val listenerStarted = AtomicBoolean(false)
    private val eventListenerStarted = AtomicBoolean(false)
    private val logListenerStarted = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    private val mutableRunningSince = MutableStateFlow<Long?>(null)
    private val mutableIpRouteRevision = MutableStateFlow(0L)
    private val mutableServiceCacheRevision = MutableStateFlow(0L)
    override val serviceCacheRevision = mutableServiceCacheRevision.asStateFlow()
    override val profileStatisticsRevision = serviceCacheRevision
    override val proxyGroupsRevision = serviceCacheRevision
    override val ipRouteRevision = mutableIpRouteRevision.asStateFlow()
    override val lanRevision = ipRouteRevision
    private val mutableLanSharing = MutableStateFlow(LanSharingState(
        config = com.lonnnnnng.flclash.core.CoreLanConfig(lanSharingStore.read() ?: false, 0),
    ))
    override val lanSharing = mutableLanSharing.asStateFlow()
    private var preferredOutboundMode: CoreOutboundMode? = outboundModeStore.read()
    private val mutableOutboundMode = MutableStateFlow(preferredOutboundMode ?: CoreOutboundMode.RULE)
    private val profileScriptApplier = ProfileScriptApplier(profileScriptEvaluator)
    private var hasSuccessfulRun = false
    private var loadedConfigKey: List<Any?>? = null

    private fun configKey(profile: ActiveProfile): List<Any?> {
        val source = File(profile.filePath)
        val script = profile.scriptPath?.let(::File)
        return listOf(profile.id, profile.filePath, source.length(), source.lastModified(),
            profile.overridesJson, profile.scriptPath, script?.length(), script?.lastModified(), lanSharingStore.read())
    }

    private suspend fun ensureConfigLoaded(force: Boolean = false) = withContext(Dispatchers.IO) {
        val profile = requireNotNull(profileProvider.activeProfile()) { "请先添加可用配置" }
        val key = configKey(profile)
        if (!force && loadedConfigKey == key) return@withContext
        ensureInitialized()
        stageConfig(profile)
        bridge.validateConfig(paths.configFile.path)
        invalidateIpRoute()
        bridge.setupConfig(profile.selectedMap)
        val mode = preferredOutboundMode ?: bridge.getConfiguredMode(paths.configFile.path)
        if (preferredOutboundMode != null) bridge.updateMode(mode)
        mutableOutboundMode.value = mode
        if (preferredOutboundMode == null) {
            preferredOutboundMode = mode
            outboundModeStore.write(mode)
        }
        // 作者 long：配置加载不启用监听或 VPN，停止态也能读取真实节点、选择和测速。
        loadedConfigKey = key
    }

    override suspend fun selectProfile(id: Long, select: suspend (Long?) -> Unit) = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            val previous = profileProvider.activeProfile()
            if (previous?.id == id && loadedConfigKey == configKey(previous)) return@withContext
            val previousContent = paths.configFile.takeIf { it.isFile }?.readBytes()
            val previousKey = loadedConfigKey
            try {
                select(id)
                ensureConfigLoaded(force = true)
            } catch (failure: Throwable) {
                var restored = true
                // 作者 long：加载失败必须恢复选择和已应用输入，不能让页面新配置与仍在服务的旧节点分裂。
                withContext(kotlinx.coroutines.NonCancellable) {
                    select(previous?.id)
                    loadedConfigKey = null
                    if (previousContent != null && previousKey != null && previous != null) {
                        try {
                            paths.configFile.writeBytes(previousContent)
                            bridge.setupConfig(previous.selectedMap)
                            bridge.updateMode(mutableOutboundMode.value)
                            loadedConfigKey = previousKey
                        } catch (_: Exception) {
                            restored = false
                            serviceController.stop()
                            stopListeners()
                            runtimeResumeStore.setShouldResume(false)
                            mutableRunningSince.value = null
                            mutableVpnSettings.update { it.copy(appliedEnabled = null) }
                            mutableState.value = CoreRuntimeStatus.FAILED
                        }
                    }
                    invalidateIpRoute()
                }
                if (failure is CancellationException) throw failure
                throw IllegalStateException(if (restored) "配置切换失败，已恢复原配置" else "配置切换失败，原配置恢复失败，核心已停止")
            }
        }
    }

    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
    override val runningSinceElapsedMillis: StateFlow<Long?> = mutableRunningSince.asStateFlow()
    val outboundMode: StateFlow<CoreOutboundMode> = mutableOutboundMode.asStateFlow()
    override val events: Flow<CoreEvent> = bridge.events
    val eventFlowStats = bridge.eventFlowStats
    val diagnosticEvents = RuntimeDiagnosticHistory.events

    // 作者 long：单调时钟包含休眠时间，系统校时和 Activity 重建都不能改变本次核心会话的运行时长。
    fun uptimeMillis(): Long? = mutableRunningSince.value?.takeIf { mutableState.value.isRunning }
        ?.let { startedAt -> (elapsedRealtimeMillis() - startedAt).coerceAtLeast(0L) }

    suspend fun restoreDiagnosticHistory(markNextStartAsRestart: Boolean): List<RuntimeDiagnosticEvent> {
        val restored = RuntimeDiagnosticHistory.restore()
        if (markNextStartAsRestart && restored.any { event ->
                event.kind == RuntimeDiagnosticKind.CORE_STARTED ||
                    event.kind == RuntimeDiagnosticKind.CORE_RESTARTED
            }
        ) {
            hasSuccessfulRun = true
        }
        return restored
    }

    suspend fun startProxy(): Result<Unit> = transitionLock.withLock { startLocked() }

    private suspend fun startLocked(): Result<Unit> {
        if (mutableState.value == CoreRuntimeStatus.RUNNING) {
            return Result.success(Unit)
        }
        mutableState.value = CoreRuntimeStatus.STARTING
        invalidateIpRoute()
        mutableRunningSince.value = null
        mutableVpnSettings.update { it.copy(appliedEnabled = null) }
        return runCatching {
            // 作者 long：先记录恢复意图，再进行配置和服务启动，进程若在启动中断开也能由 Application 重试。
            runtimeResumeStore.setShouldResume(true)
            ensureConfigLoaded(force = true)
            if (eventListenerStarted.compareAndSet(false, true)) {
                bridge.startEventListener()
            }
            if (logListenerStarted.compareAndSet(false, true)) {
                bridge.startLog()
            }
            ServiceConfig.updateNotificationParams(
                // 作者 long：核心在后台运行时必须保留停止入口，用户无需重新打开首页即可释放 VPN 和 TUN。
                NotificationParams(title = "KClash", showStopAction = true),
            )
            val vpnOptions = vpnOptionsProvider.current()
            serviceController.start(vpnOptions)
            if (!listenerStarted.get()) {
                bridge.startListener()
                listenerStarted.set(true)
            }
            // 作者 long：服务和核心监听器都就绪才建立会话，失败启动不能留下正在运行的计时。
            mutableRunningSince.value = elapsedRealtimeMillis()
            mutableState.value = CoreRuntimeStatus.RUNNING
            mutableVpnSettings.update { it.copy(appliedEnabled = vpnOptions.enable) }
            RuntimeDiagnosticHistory.recordAndFlush(
                if (hasSuccessfulRun) {
                    RuntimeDiagnosticKind.CORE_RESTARTED
                } else {
                    RuntimeDiagnosticKind.CORE_STARTED
                },
                if (hasSuccessfulRun) "核心重新启动" else "核心启动成功",
            )
            hasSuccessfulRun = true
        }.onFailure { error ->
            runCatching { serviceController.stop() }
            if (listenerStarted.get()) {
                runCatching { bridge.stopListener() }
                listenerStarted.set(false)
            }
            if (eventListenerStarted.get()) {
                runCatching { bridge.stopEventListener() }
                eventListenerStarted.set(false)
            }
            if (logListenerStarted.get()) {
                runCatching { bridge.stopLog() }
                logListenerStarted.set(false)
            }
            runCatching { runtimeResumeStore.setShouldResume(false) }
            mutableState.value = CoreRuntimeStatus.FAILED
            mutableRunningSince.value = null
            mutableVpnSettings.update { it.copy(appliedEnabled = null) }
            RuntimeDiagnosticHistory.record(
                RuntimeDiagnosticKind.CORE_FAILED,
                formatRuntimeFailure(error),
            )
        }.also { result ->
            if (result.isFailure) RuntimeDiagnosticHistory.flush()
        }
    }

    suspend fun stop(): Result<Unit> = transitionLock.withLock { stopLocked() }

    private suspend fun stopLocked(): Result<Unit> {
        invalidateIpRoute()
        mutableRunningSince.value = null
        mutableVpnSettings.update { it.copy(appliedEnabled = null) }
        return runCatching {
            // 作者 long：显式停止必须先清除恢复意图，避免下一次 Application 启动把用户停止误判成系统重建。
            runtimeResumeStore.setShouldResume(false)
            if (mutableState.value == CoreRuntimeStatus.STOPPED) {
                // 作者 long：即使核心状态已经先被服务回调置为停止，也要等待网络诊断尾巴落盘，
                // 避免用户连续点击停止或系统重复回调时丢掉最后一条事件。
                RuntimeDiagnosticHistory.flush()
                return@runCatching Unit
            }
            mutableState.value = CoreRuntimeStatus.STOPPING
            serviceController.stopAndAwait()
            if (listenerStarted.get()) {
                bridge.stopListener()
                listenerStarted.set(false)
            }
            if (eventListenerStarted.get()) {
                bridge.stopEventListener()
                eventListenerStarted.set(false)
            }
            if (logListenerStarted.get()) {
                bridge.stopLog()
                logListenerStarted.set(false)
            }
            mutableState.value = CoreRuntimeStatus.STOPPED
            RuntimeDiagnosticHistory.recordAndFlush(RuntimeDiagnosticKind.CORE_STOPPED, "核心已停止")
        }.onFailure { error ->
            mutableState.value = CoreRuntimeStatus.FAILED
            RuntimeDiagnosticHistory.record(
                RuntimeDiagnosticKind.CORE_FAILED,
                formatRuntimeFailure(error),
            )
        }.also { result ->
            if (result.isFailure) RuntimeDiagnosticHistory.flush()
        }
    }

    /**
     * 作者 long：系统撤销 VPN 时，VpnService 只能释放平台 TUN；这里补齐应用层核心和
     * ProxyService 的生命周期收口，避免 UI 仍显示运行中或下一次启动遇到旧监听器。
     */
    suspend fun handleVpnRevoked(): Result<Unit> = transitionLock.withLock {
        invalidateIpRoute()
        mutableRunningSince.value = null
        mutableVpnSettings.update { it.copy(appliedEnabled = null) }
        runCatching {
            runtimeResumeStore.setShouldResume(false)
            if (mutableState.value == CoreRuntimeStatus.STOPPED ||
                mutableState.value == CoreRuntimeStatus.FAILED
            ) {
                return@runCatching Unit
            }
            mutableState.value = CoreRuntimeStatus.STOPPING
            // onRevoke 已经释放 VpnService；重复发送停止 action 是幂等收口，
            // 同时确保仍存活的 ProxyService 和前台通知也被关闭。
            serviceController.stop()
            stopListeners()
            mutableState.value = CoreRuntimeStatus.FAILED
            RuntimeDiagnosticHistory.recordAndFlush(RuntimeDiagnosticKind.VPN_REVOKED, "系统撤销 VPN")
        }.onFailure { error ->
            stopListeners()
            mutableState.value = CoreRuntimeStatus.FAILED
            RuntimeDiagnosticHistory.record(
                RuntimeDiagnosticKind.CORE_FAILED,
                formatRuntimeFailure(error),
            )
        }.also { result ->
            if (result.isFailure) RuntimeDiagnosticHistory.flush()
        }
    }

    override suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot> = transitionLock.withLock {
        runCatching {
            ensureConfigLoaded()
            bridge.getProxyGroups()
        }
    }

    suspend fun setOutboundMode(mode: CoreOutboundMode): Result<Unit> = transitionLock.withLock {
        runCatching {
            check(mutableState.value != CoreRuntimeStatus.STARTING &&
                mutableState.value != CoreRuntimeStatus.STOPPING
            ) { "核心正在切换状态" }
            if (loadedConfigKey != null) {
                invalidateIpRoute(reusable = true)
                bridge.updateMode(mode)
            }
            preferredOutboundMode = mode
            mutableOutboundMode.value = mode
            outboundModeStore.write(mode)
        }
    }

    override suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot> =
        transitionLock.withLock {
            runCatching {
                ensureConfigLoaded()
                invalidateIpRoute(reusable = true)
                bridge.changeProxy(groupName, proxyName)
                // 作者 long：切换结果以核心重新返回的当前选择为准，避免 UI 只更新本地猜测而与 Mihomo 状态漂移。
                bridge.getProxyGroups()
            }
        }

    /**
     * 作者 long：测速与启动、停止、代理切换共用生命周期锁，避免核心正在切换配置时读取到半成品结果。
     */
    override suspend fun testDelay(
        proxyName: String,
        testUrl: String,
        timeoutMs: Long,
    ): Result<ProxyDelay?> = transitionLock.withLock {
        runCatching {
            ensureConfigLoaded()
            bridge.testDelay(proxyName, testUrl, timeoutMs)
        }
    }

    /**
     * 作者 long：组级测速必须保持核心生命周期互斥，但单个 asyncTestDelay 可以并发发送；
     * 因此锁覆盖整个批次而不是每一项，既避免停止/切换打断配置，又不把批量测速退化为串行循环。
     */
    override suspend fun testDelayBatch(
        requests: List<ProxyDelayRequest>,
        timeoutMs: Long,
        maxConcurrent: Int,
    ): Result<List<ProxyDelayBatchResult>> = transitionLock.withLock {
        try {
            ensureConfigLoaded()
            require(timeoutMs > 0) { "测速超时必须大于 0" }
            require(maxConcurrent > 0) { "批量测速并发数必须大于 0" }
            if (requests.isEmpty()) return@withLock Result.success(emptyList())
            val permits = Semaphore(maxConcurrent)
            Result.success(coroutineScope {
                requests.map { request ->
                    async {
                        permits.withPermit {
                            try {
                                ProxyDelayBatchResult(
                                    request = request,
                                    result = bridge.testDelay(request.proxyName, request.testUrl, timeoutMs),
                                )
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                ProxyDelayBatchResult(request = request, result = null, error = error)
                            }
                        }
                    }
                }.awaitAll()
            })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun probe(
        url: String,
        proxyName: String = "",
        timeoutMs: Long = CoreBridge.DEFAULT_PROBE_TIMEOUT_MS,
    ): Result<CoreProbeResult?> = transitionLock.withLock {
        runCatching {
            check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
            // 作者 long：诊断请求必须和代理切换、启停串行，避免核心路由版本变化时把结果归因到另一份配置。
            bridge.probe(url, proxyName, timeoutMs)
        }
    }

    override suspend fun memoryStats() = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            // 作者 long：嵌入式 Go 即使未接管流量也占用内存，读统计不加载配置、不启动服务。
            ensureInitialized()
            withTimeout(3_000L) { bridge.getMemoryStats() }
        }
    }

    override suspend fun refreshLanSharing() = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            if (profileProvider.activeProfile() == null) {
                mutableLanSharing.value = LanSharingState(com.lonnnnnng.flclash.core.CoreLanConfig(lanSharingStore.read() ?: false, 0))
            } else {
                ensureConfigLoaded()
                mutableLanSharing.value = LanSharingState(withTimeout(3_000L) { bridge.getLanConfig() }, true)
            }
        }
    }

    override suspend fun updateLanSharing(allowLan: Boolean?) = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            val profile = profileProvider.activeProfile()
            if (profile == null) {
                lanSharingStore.write(allowLan)
                mutableLanSharing.value = LanSharingState(com.lonnnnnng.flclash.core.CoreLanConfig(allowLan ?: false, 0))
                return@withContext
            }
            ensureConfigLoaded()
            val previous = withTimeout(3_000L) { bridge.getLanConfig() }
            val savedOverride = lanSharingStore.read()
            val savedContent = paths.configFile.readBytes()
            val savedKey = loadedConfigKey
            try {
                // 作者 long：普通开关只修改当前已应用输入，不能再次执行可能有动态结果的用户脚本。
                if (allowLan == null) stageConfig(profile, null)
                else paths.configFile.writeText(patchLanSharing(savedContent.toString(Charsets.UTF_8), allowLan))
                bridge.validateConfig(paths.configFile.path)
                val candidate = withTimeout(3_000L) { bridge.getLanConfig(paths.configFile.path) }
                withTimeout(5_000L) { bridge.updateAllowLan(candidate.allowLan) }
                val actual = withTimeout(3_000L) { bridge.getLanConfig() }
                check(actual == candidate) { "核心局域网状态与设置不一致" }
                lanSharingStore.write(allowLan)
                loadedConfigKey = configKey(profile)
                mutableLanSharing.value = LanSharingState(actual, true)
                invalidateIpRoute()
            } catch (failure: Throwable) {
                var restored = true
                // 作者 long：热更新可能先改监听再返回失败，必须同时恢复核心、暂存输入和偏好。
                withContext(kotlinx.coroutines.NonCancellable) {
                    try {
                        paths.configFile.writeBytes(savedContent)
                        withTimeout(5_000L) { bridge.updateAllowLan(previous.allowLan) }
                        check(withTimeout(3_000L) { bridge.getLanConfig() } == previous)
                        lanSharingStore.write(savedOverride)
                        loadedConfigKey = savedKey
                        mutableLanSharing.value = LanSharingState(previous, true)
                    } catch (_: Exception) {
                        restored = false
                        loadedConfigKey = null
                        serviceController.stop()
                        stopListeners()
                        runtimeResumeStore.setShouldResume(false)
                        mutableRunningSince.value = null
                        mutableVpnSettings.update { it.copy(appliedEnabled = null) }
                        mutableState.value = CoreRuntimeStatus.FAILED
                    }
                    invalidateIpRoute()
                }
                if (failure is CancellationException) throw failure
                throw IllegalStateException(if (restored) "局域网设置失败，已恢复原状态" else "局域网设置失败且无法恢复，核心已停止")
            }
        }
    }

    override suspend fun releaseMemory() = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            // 作者 long：等待协议回执后重新读取实际统计，回收不改变配置选择或运行状态。
            withTimeout(5_000L) { bridge.releaseMemory() }
            withTimeout(3_000L) { bridge.getMemoryStats() }
        }
    }

    suspend fun getStats(): Result<CoreRuntimeStats> = transitionLock.withLock {
        try {
            check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
            Result.success(CoreRuntimeStats(
                traffic = bridge.getTraffic(),
                totalTraffic = bridge.getTotalTraffic(),
                connectionCount = bridge.getConnectionCount(),
            ))
        } catch (error: CancellationException) {
            // 作者 long：前台采样被取消时必须停止，不能把取消当作普通错误写回已结束的会话。
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override suspend fun checkServices(targets: List<com.lonnnnnng.flclash.core.ServiceTarget>): List<com.lonnnnnng.flclash.core.CoreServiceCheck> {
        require(targets.isNotEmpty() && targets.distinct().size == targets.size)
        val (revision, running, stamp) = transitionLock.withLock {
            ensureInitialized()
            Triple(mutableIpRouteRevision.value, mutableState.value.isRunning, withTimeout(3_000L) { bridge.ipRouteStamp() })
        }
        // 作者 long：外部服务可能超时，网络等待不占启停锁；停止态强制直连，不沿用上次核心规则。
        val result = withTimeout(41_000L) { bridge.checkServices(targets, direct = !running, timeoutMs = 6_000L) }
        check(result.size == targets.size && result.map { it.target }.toSet() == targets.toSet()) { "服务检测结果不完整" }
        transitionLock.withLock {
            if (mutableIpRouteRevision.value != revision || mutableState.value.isRunning != running ||
                withTimeout(3_000L) { bridge.ipRouteStamp() } != stamp || result.any { it.stamp != stamp }) {
                throw ServiceRouteChangedException()
            }
        }
        return result
    }

    private fun invalidateIpRoute(reusable: Boolean = false) {
        // 作者 long：网络、配置和生命周期变化必须强制重测；节点与模式变化才允许按真实命中逐项核验。
        if (!reusable) mutableServiceCacheRevision.update { it + 1 }
        mutableIpRouteRevision.update { it + 1 }
    }

    override suspend fun reuseServiceChecks(
        results: List<com.lonnnnnng.flclash.core.CoreServiceCheck>,
        expected: com.lonnnnnng.flclash.core.CoreRouteStamp,
        cacheRevision: Long,
    ): List<com.lonnnnnng.flclash.core.CoreServiceCheck> {
        val candidates = results.filter { !it.status.retryable && it.stamp.epoch == expected.epoch &&
            it.hits.isNotEmpty() && it.hits.all { hit -> hit.reusable } }
        require(candidates.map { it.target }.distinct().size == candidates.size)
        if (candidates.isEmpty()) return emptyList()
        val revision = transitionLock.withLock {
            ensureInitialized()
            if (mutableServiceCacheRevision.value != cacheRevision || withTimeout(3_000L) { bridge.ipRouteStamp() } != expected) {
                throw ServiceRouteChangedException()
            }
            mutableIpRouteRevision.value
        }
        // 作者 long：核验也可能等待核心回调，锁外执行以免阻塞停止；回写前同时核对两类版本与核心快照。
        val checked = withTimeout(3_000L) { bridge.checkRoutes(candidates.map { it.hits }) }
        return transitionLock.withLock {
            if (mutableServiceCacheRevision.value != cacheRevision || mutableIpRouteRevision.value != revision ||
                checked.stamp != expected || withTimeout(3_000L) { bridge.ipRouteStamp() } != expected) {
                throw ServiceRouteChangedException()
            }
            check(checked.held.size == candidates.size) { "路由核验结果不完整" }
            candidates.filterIndexed { index, _ -> checked.held[index] }.map { it.copy(stamp = expected) }
        }
    }

    override suspend fun queryServiceExit(proxyName: String, expected: com.lonnnnnng.flclash.core.CoreRouteStamp, urls: List<String>): CoreIpResponse {
        require(proxyName.isNotBlank() && urls.isNotEmpty())
        val (revision, running) = transitionLock.withLock {
            ensureInitialized()
            val running = mutableState.value.isRunning
            if ((!running && proxyName != "DIRECT") || withTimeout(3_000L) { bridge.ipRouteStamp() } != expected) {
                throw ServiceRouteChangedException()
            }
            mutableIpRouteRevision.value to running
        }
        // 作者 long：指定出口查询不能占用启停锁；停止态只允许 DIRECT，旧运行结果不能借此重新使用代理。
        val result = withTimeout(17_000L) { bridge.outboundIpForProxy(urls, 6_000L, proxyName) }
        transitionLock.withLock {
            if (revision != mutableIpRouteRevision.value || running != mutableState.value.isRunning ||
                result.stamp != expected || withTimeout(3_000L) { bridge.ipRouteStamp() } != expected) {
                throw ServiceRouteChangedException()
            }
        }
        return result
    }

    override suspend fun ipRouteStamp() = transitionLock.withLock {
        ensureInitialized()
        withTimeout(3_000L) { bridge.ipRouteStamp() }
    }
    override fun networkChanged() {
        invalidateIpRoute()
    }

    override suspend fun queryOutboundIp(urls: List<String>): CoreIpResponse = queryIp(6_000L) {
        if (it) bridge.outboundIp(urls, 6_000L) else bridge.directOutboundIp(urls, 6_000L)
    }

    override suspend fun queryIpMetadata(url: String): CoreIpResponse = queryIp(8_000L) {
        if (it) bridge.ipMetadata(url, 8_000L) else bridge.directIpMetadata(url, 8_000L)
    }

    private suspend fun queryIp(timeoutMs: Long, request: suspend (Boolean) -> CoreIpResponse): CoreIpResponse {
        val (revision, running) = transitionLock.withLock {
            ensureInitialized()
            mutableIpRouteRevision.value to mutableState.value.isRunning
        }
        // 作者 long：外部网络等待不得占用启停锁；Kotlin 取消后 Go 请求仍由自身超时收口。
        val result = withTimeout(timeoutMs * 2 + 5_000L) { request(running) }
        transitionLock.withLock {
            check(mutableState.value.isRunning == running && mutableIpRouteRevision.value == revision) { "出口已变化，请重新查询" }
            check(withTimeout(3_000L) { bridge.ipRouteStamp() } == result.stamp) { "出口已变化，请重新查询" }
        }
        return result
    }

    suspend fun getConnections(): Result<List<CoreConnection>> = transitionLock.withLock {
        runCatching {
            check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
            bridge.getConnections()
        }
    }

    suspend fun clearDnsCache(): Result<Unit> = transitionLock.withLock {
        try {
            check(bridge.clearDnsCache()) { "清除 DNS 缓存失败" }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun ensureInitialized() {
        if (initialized.get()) return
        paths.homeDir.mkdirs()
        if (initialized.compareAndSet(false, true)) {
            runCatching {
                // 作者 long：Mihomo 的 geo-auto-update 会在首次解析配置时下载 GeoIP 数据；
                // 先把 APK 内置数据放入核心目录，避免 Android 首次启动因底层网络尚未可用而回退到无代理默认配置。
                paths.prepare()
                bridge.initialize(paths.homeDir.path, Build.VERSION.SDK_INT)
            }.onFailure {
                initialized.set(false)
            }.getOrThrow()
        }
    }

    private suspend fun stopListeners() {
        if (listenerStarted.get()) {
            runCatching { bridge.stopListener() }
            listenerStarted.set(false)
        }
        if (eventListenerStarted.get()) {
            runCatching { bridge.stopEventListener() }
            eventListenerStarted.set(false)
        }
        if (logListenerStarted.get()) {
            runCatching { bridge.stopLog() }
            logListenerStarted.set(false)
        }
    }

    private val providerUpdateLock = kotlinx.coroutines.sync.Mutex()

    private class ProviderSnapshot(val profile: ActiveProfile, val key: List<Any?>?, val revision: Long,
        val stamp: com.lonnnnnng.flclash.core.CoreRouteStamp)

    private suspend fun providerSnapshot(profileId: Long): ProviderSnapshot = transitionLock.withLock {
        val profile = requireNotNull(profileProvider.activeProfile())
        check(profile.id == profileId && loadedConfigKey != null && loadedConfigKey == configKey(profile))
        check(mutableState.value != CoreRuntimeStatus.STARTING && mutableState.value != CoreRuntimeStatus.STOPPING)
        ProviderSnapshot(profile, loadedConfigKey, mutableServiceCacheRevision.value, withTimeout(3_000L) { bridge.ipRouteStamp() })
    }

    private suspend fun verifyProviderSnapshot(snapshot: ProviderSnapshot, allowRouteChange: Boolean = false) {
        check(profileProvider.activeProfile() == snapshot.profile && loadedConfigKey == snapshot.key &&
            configKey(snapshot.profile) == snapshot.key && mutableServiceCacheRevision.value == snapshot.revision)
        val current = withTimeout(3_000L) { bridge.ipRouteStamp() }
        check(if (allowRouteChange) current.epoch == snapshot.stamp.epoch else current == snapshot.stamp)
    }

    override suspend fun profileProviders(profileId: Long): List<com.lonnnnnng.flclash.core.CoreExternalProvider> = withContext(Dispatchers.IO) {
        val snapshot = providerSnapshot(profileId)
        val providers = withTimeout(3_000L) { bridge.externalProviders() }
        transitionLock.withLock { verifyProviderSnapshot(snapshot) }
        providers
    }

    override suspend fun updateProfileProvider(profileId: Long, name: String): Unit = withContext(Dispatchers.IO) {
        if (!providerUpdateLock.tryLock()) throw ProviderOperationException("已有 Provider 正在更新，请稍后重试")
        try {
            val snapshot = providerSnapshot(profileId)
            val providers = withTimeout(3_000L) { bridge.externalProviders() }
            val provider = providers.singleOrNull { it.name == name }
                ?: throw ProviderOperationException("Provider 已不存在，请刷新列表")
            if (!provider.canUpdate) throw ProviderOperationException("此 Provider 来自本地文件，无需在线更新")
            transitionLock.withLock { verifyProviderSnapshot(snapshot) }
            // 作者 long：下载不持有启停锁；Go 更新不会随 UI 取消而终止，必须等回调结束才释放重复更新门禁。
            withContext(kotlinx.coroutines.NonCancellable) {
                var failure: Exception? = null
                try { bridge.updateExternalProvider(name, snapshot.stamp) } catch (error: Exception) { failure = error }
                transitionLock.withLock {
                    // 作者 long：更新自身会改变路由版本，仅接受同一配置和核心会话；迟到结果不能刷新另一份配置。
                    verifyProviderSnapshot(snapshot, allowRouteChange = true)
                    invalidateIpRoute()
                }
                failure?.let { throw it }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: ProviderOperationException) { throw error }
        catch (error: Exception) {
            val message = when ((error as? com.lonnnnnng.flclash.core.CoreBridgeException)?.code) {
                "provider_updating" -> "Provider 正在更新，请稍后重试"
                "provider_not_found" -> "Provider 已不存在，请刷新列表"
                "request_timeout" -> "更新超时，请重试"
                "request_bad_response" -> "更新服务返回异常，请稍后重试"
                else -> "更新未完成，配置或网络可能已变化，请刷新后重试"
            }
            throw ProviderOperationException(message)
        } finally { providerUpdateLock.unlock() }
    }

    override suspend fun profileStatistics(profileId: Long): ProfileStatistics = withContext(Dispatchers.IO) {
        val snapshot = transitionLock.withLock {
            val profile = requireNotNull(profileProvider.activeProfile()) { "没有可用配置" }
            check(profile.id == profileId) { "当前配置已变化，请重试" }
            check(loadedConfigKey == configKey(profile)) { "配置尚未加载，请稍后重试" }
            // 作者 long：统计只读已交给核心的暂存输入；不能为打开详情重跑脚本或触发配置加载/下载。
            StatisticsInput(profile, loadedConfigKey, mutableIpRouteRevision.value,
                withTimeout(3_000L) { bridge.ipRouteStamp() }, readPreviewFile(paths.configFile, 8 * 1024 * 1024))
        }
        val providers = withTimeout(3_000L) { bridge.providerCounts() }
        val counts = profileStatisticsOf(profileId, snapshot.content, providers)
        transitionLock.withLock {
            check(snapshot.profile == profileProvider.activeProfile() && snapshot.key == loadedConfigKey &&
                snapshot.key == configKey(snapshot.profile) && snapshot.revision == mutableIpRouteRevision.value &&
                snapshot.stamp == withTimeout(3_000L) { bridge.ipRouteStamp() }) { "配置或核心状态已变化，请重试" }
        }
        counts
    }

    // 作者 long：快照包含临时配置正文，不生成 toString，避免错误日志意外泄露订阅内容。
    private class StatisticsInput(val profile: ActiveProfile, val key: List<Any?>?, val revision: Long,
        val stamp: com.lonnnnnng.flclash.core.CoreRouteStamp, val content: String)

    override suspend fun previewConfig(profileId: Long): RuntimeConfigPreview = withContext(Dispatchers.IO) {
        val (profile, revision, applied) = transitionLock.withLock {
            val current = requireNotNull(profileProvider.activeProfile()) { "没有可用配置" }
            check(current.id == profileId) { "当前配置已变化，请重新打开预览" }
            if (mutableState.value.isRunning) {
                // 作者 long：运行时读取已暂存的输入，不能重跑脚本后把另一个结果冒充当前已加载配置。
                return@withContext RuntimeConfigPreview(current.id, current.label, readPreviewFile(paths.configFile), true)
            }
            Triple(current, mutableIpRouteRevision.value, mutableState.value)
        }
        val source = File(profile.filePath)
        val sourceStamp = source.length() to source.lastModified()
        val script = profile.scriptPath?.let(::File)
        val scriptStamp = script?.let { it.length() to it.lastModified() }
        // 作者 long：停止态只在内存中复用启动处理链；脚本可能等待执行器，不能持有启停锁或覆盖 config.yaml。
        val content = renderProfileContent(profile, readPreviewFile(source))
        check(content.toByteArray(Charsets.UTF_8).size <= MAX_PREVIEW_BYTES) { "配置超过 2 MiB，暂不支持预览" }
        transitionLock.withLock {
            check(mutableIpRouteRevision.value == revision && mutableState.value == applied &&
                profileProvider.activeProfile() == profile &&
                sourceStamp == (source.length() to source.lastModified()) &&
                scriptStamp == script?.let { it.length() to it.lastModified() }
            ) { "配置或核心状态已变化，请重试" }
        }
        RuntimeConfigPreview(profile.id, profile.label, content, false)
    }

    private fun readPreviewFile(file: File, limit: Int = MAX_PREVIEW_BYTES): String {
        require(file.isFile) { "配置文件不存在" }
        // 作者 long：先限制读取量再解码，避免大订阅或读取中增长的文件耗尽预览页面内存。
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (output.size() <= limit) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= limit) { if (limit == MAX_PREVIEW_BYTES) "配置超过 2 MiB，暂不支持预览" else "配置超过 8 MiB，暂不支持统计" }
        return bytes.toString(Charsets.UTF_8)
    }

    private suspend fun renderProfileContent(profile: ActiveProfile, sourceContent: String, lanOverride: Boolean? = lanSharingStore.read()): String {
        // 作者 long：脚本先生成配置，再合并 Provider/DNS 覆盖；启动与预览共用顺序，避免预览和实际输入分叉。
        val scriptedContent = profileScriptApplier.apply(sourceContent, profile.scriptPath)
        return patchLanSharing(ProfileProviderOverrideApplier.apply(
            scriptedContent,
            ProfileProviderOverrides.fromJsonString(profile.overridesJson),
        ), lanOverride)
    }

    private suspend fun stageConfig(profile: ActiveProfile, lanOverride: Boolean? = lanSharingStore.read()) {
        val source = File(profile.filePath)
        require(source.isFile) { "配置文件不存在：${source.path}" }
        paths.homeDir.mkdirs()
        val temporary = File(paths.homeDir, ".${paths.configFile.name}.tmp")
        val sourceContent = source.readText(Charsets.UTF_8)
        val effectiveContent = renderProfileContent(profile, sourceContent, lanOverride)
        try {
            temporary.writeText(effectiveContent, Charsets.UTF_8)
            if (!temporary.renameTo(paths.configFile)) {
                paths.configFile.outputStream().use { output ->
                    temporary.inputStream().use { input -> input.copyTo(output) }
                }
                if (!temporary.delete()) {
                    throw IOException("无法清理核心配置临时文件")
                }
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private companion object {
        const val MAX_PREVIEW_BYTES = 2 * 1024 * 1024
        const val DEFAULT_DELAY_BATCH_CONCURRENCY = 32
    }
}

data class CoreRuntimeStats(
    val traffic: CoreTraffic,
    val totalTraffic: CoreTraffic,
    val connectionCount: Int,
)
