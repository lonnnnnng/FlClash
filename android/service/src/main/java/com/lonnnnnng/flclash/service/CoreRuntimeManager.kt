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
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance

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

class ProfileApplyAfterSaveException(val profileId: Long? = null) : IllegalStateException("配置已保存，当前配置应用失败，请重试应用")

interface CoreRuntimeStatusProvider {
    val state: StateFlow<CoreRuntimeStatus>
    suspend fun selectProfile(id: Long, select: suspend (Long?) -> Unit) { select(id) }
    suspend fun profileInputsApplied(id: Long): Boolean = false
    suspend fun applyCurrentProfile(id: Long) { error("此运行环境不支持配置应用") }
    suspend fun <T> withProfileWrite(applyCurrentId: Long? = null, write: suspend () -> T): T {
        val result = write()
        if (applyCurrentId != null) {
            try { applyCurrentProfile(applyCurrentId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw ProfileApplyAfterSaveException() }
        }
        return result
    }
    suspend fun <T> withScriptWrite(scriptPath: String?, write: suspend () -> T): T {
        check(state.value != CoreRuntimeStatus.STARTING && state.value != CoreRuntimeStatus.STOPPING) { "核心正在启停，请稍后重试" }
        return write()
    }
    suspend fun <T> withExtensionWrite(targetId: Long?, write: suspend () -> T): T = withProfileWrite(targetId, write)
    suspend fun removeProfile(id: Long, remaining: suspend () -> List<Long>, select: suspend (Long?) -> Unit, remove: suspend () -> Unit) {
        check(!state.value.isRunning) { "当前运行环境不支持运行中删除配置" }
        remove()
    }
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
    private val geoSettingsStore: GeoResourceSettingsStore? = null,
    private val networkSettingsStore: NetworkSettingsStore? = null,
    private val globalDnsSettingsStore: GlobalDnsSettingsStore? = null,
    private val generalSettingsStore: GeneralSettingsStore? = null,
) : ProxyGroupsRuntime, OutboundIpRuntime, RuntimeConfigPreviewProvider, ProfileStatisticsRuntime, MemoryRuntime, LanSharingRuntime, ServiceStatusRuntime {
    init {
        RuntimeDiagnosticHistory.configure(runtimeDiagnosticStore)
    }

    private val transitionLock = Mutex()
    private var onDemandExcluded = false
    private val mutableOnDemandSuspended = MutableStateFlow(false)
    val onDemandSuspended = mutableOnDemandSuspended.asStateFlow()

    private fun forwardingActive() = mutableState.value.isRunning && !mutableOnDemandSuspended.value

    /** 作者 long：排除Wi-Fi只暂停转发，不结束用户本次运行；所有入口仍由同一启停锁决定是否恢复。 */
    suspend fun setOnDemandExcluded(excluded: Boolean): Result<Unit> = transitionLock.withLock {
        withContext(Dispatchers.IO + NonCancellable) {
            onDemandExcluded = excluded
            if (!mutableState.value.isRunning || mutableOnDemandSuspended.value == excluded) return@withContext Result.success(Unit)
            runCatching {
                if (excluded) {
                    serviceController.stopAndAwait()
                    if (listenerStarted.get()) { bridge.stopListener(); listenerStarted.set(false) }
                } else {
                    ensureConfigLoaded()
                    val options = vpnOptionsProvider.current().copy(enable = appliedVpnOptions?.enable ?: mutableVpnSettings.value.enabled)
                    serviceController.start(options)
                    if (!listenerStarted.get()) { bridge.startListener(); listenerStarted.set(true) }
                    appliedVpnOptions = options
                    mutableVpnSettings.update { it.copy(appliedEnabled = options.enable) }
                }
                mutableOnDemandSuspended.value = excluded
                invalidateIpRoute()
            }.onFailure { failure ->
                // 作者 long：暂停或恢复失败时收掉半开的系统服务，保留明确失败态，不能误报仍在接管网络。
                mutableRunningSince.value = null
                mutableOnDemandSuspended.value = false
                mutableVpnSettings.update { it.copy(appliedEnabled = null) }
                mutableState.value = CoreRuntimeStatus.FAILED
                invalidateIpRoute()
                // 作者 long：持久化或诊断再次失败也不能打断收尾、更不能终止应用级网络观察协程。
                runCatching { serviceController.stopAndAwait() }.onFailure { failure.addSuppressed(it) }
                runCatching { stopListeners() }.onFailure { failure.addSuppressed(it) }
                runCatching { runtimeResumeStore.setShouldResume(false) }.onFailure { failure.addSuppressed(it) }
                runCatching { RuntimeDiagnosticHistory.recordAndFlush(RuntimeDiagnosticKind.CORE_FAILED, "按需连接切换失败，请重新启动") }
                    .onFailure { failure.addSuppressed(it) }
            }
        }
    }

    suspend fun <T> withStoppedDataRestore(action: suspend () -> T): T = transitionLock.withLock {
        check(mutableState.value == CoreRuntimeStatus.STOPPED || mutableState.value == CoreRuntimeStatus.FAILED) { "请先停止服务后恢复备份" }
        check(mutableGeoUpdates.value.values.none { it.updating }) { "请等待资源更新完成后恢复备份" }
        withContext(Dispatchers.IO + NonCancellable) {
            try { action() } finally {
                // 作者 long：恢复可能成功或完成补偿，均以实际落盘值刷新；不加载新配置或启动VPN，旧预览不能继续视为有效。
                loadedConfigKey = null; loadedAutomaticConfigKey = null
                mutableGeneralSettings.value = generalSettingsStore?.read() ?: GeneralSettings()
                mutableNetworkSettings.value = networkSettingsStore?.read() ?: NetworkSettings()
                mutableGlobalDns.value = globalDnsSettingsStore?.read() ?: ProfileDnsOverrides()
                mutableGeoSettings.value = geoSettingsStore?.read() ?: GeoResourceSettings()
                mutableVpnSettings.value = VpnSettingsState(vpnSettingsStore.read())
                mutableLanSharing.value = LanSharingState(com.lonnnnnng.flclash.core.CoreLanConfig(lanSharingStore.read() ?: false, 0))
                syncGeneralRuntimeHooks(mutableGeneralSettings.value)
                invalidateIpRoute()
            }
        }
    }

    suspend fun compileRuleResource(name: String) = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            // 作者 long：资源编译只需要初始化核心，不能为编辑资源启动监听、服务或 VPN。
            ensureInitialized()
            bridge.compileRuleSet(name)
        }
    }

    suspend fun previewRuleResource(path: String) = transitionLock.withLock {
        withContext(Dispatchers.IO) { ensureInitialized(); bridge.dumpRuleSet(path) }
    }
    private val mutableVpnSettings = MutableStateFlow(VpnSettingsState(vpnSettingsStore.read()))
    val vpnSettings = mutableVpnSettings.asStateFlow()
    private val mutableNetworkSettings = MutableStateFlow(networkSettingsStore?.read() ?: NetworkSettings())
    val networkSettings = mutableNetworkSettings.asStateFlow()

    suspend fun updateNetworkSettings(expected: NetworkSettings, value: NetworkSettings): Result<Unit> = transitionLock.withLock {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val store = networkSettingsStore ?: return@withContext Result.failure(IllegalStateException("网络设置存储不可用"))
            runCatching {
                val previous = store.read()
                check(previous == expected) { "网络设置已变化，请重新打开后保存" }
                val next = value.validated()
                next.vpnRoutes()
                if (next == previous) return@runCatching
                store.write(next)
                try {
                    // 作者 long：保存和核心应用共用启停锁；运行中沿用当前 VPN 启用状态，只为实际网络参数变化重建系统网络。
                    profileProvider.activeProfileId()?.let { id -> applyProfileLocked(id) { target -> check(target == id) } }
                    mutableNetworkSettings.value = next
                    invalidateIpRoute()
                } catch (failure: Throwable) {
                    // 作者 long：核心或系统网络重建失败时，既有应用回滚恢复连接，这里同时恢复落盘偏好。
                    try { store.write(previous) } catch (_: Exception) {
                        // 作者 long：磁盘连回滚都无法写入时，回读真实偏好并停止服务，不能继续展示旧值却在下次启动使用新路径。
                        mutableNetworkSettings.value = runCatching { store.read() }.getOrDefault(next)
                        loadedConfigKey = null
                        loadedAutomaticConfigKey = null
                        stopLocked()
                        error("网络设置恢复失败，服务已停止，请重新保存后启动")
                    }
                    throw failure
                }
            }
        }
    }

    private val mutableGlobalDns = MutableStateFlow(globalDnsSettingsStore?.read() ?: ProfileDnsOverrides())
    val globalDns = mutableGlobalDns.asStateFlow()

    suspend fun updateGlobalDns(expected: ProfileDnsOverrides, value: ProfileDnsOverrides): Result<Unit> = transitionLock.withLock {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val store = globalDnsSettingsStore ?: return@withContext Result.failure(IllegalStateException("DNS 设置存储不可用"))
            runCatching {
                val previous = store.read()
                check(previous == expected) { "DNS 设置已变化，请重新打开后保存" }
                val next = GlobalDnsSettings.validate(value)
                if (next == previous) return@runCatching
                store.write(next)
                try {
                    // 作者 long：写盘和应用共用启停锁；先补基线再合并全局字段，运行失败由既有配置事务恢复原服务。
                    profileProvider.activeProfileId()?.let { id ->
                        if (profileProvider.profileById(id)?.usesGlobalDns == true) applyProfileLocked(id) { target -> check(target == id) }
                    }
                    mutableGlobalDns.value = next
                    invalidateIpRoute()
                } catch (failure: Throwable) {
                    try { store.write(previous) } catch (_: Exception) {
                        mutableGlobalDns.value = runCatching { store.read() }.getOrDefault(next)
                        loadedConfigKey = null; loadedAutomaticConfigKey = null
                        stopLocked()
                        error("DNS 设置恢复失败，服务已停止，请重新保存后启动")
                    }
                    throw failure
                }
            }
        }
    }

    private val mutableGeneralSettings = MutableStateFlow(generalSettingsStore?.read() ?: GeneralSettings())
    val generalSettings = mutableGeneralSettings.asStateFlow()

    suspend fun updateGeneralSettings(expected: GeneralSettings, value: GeneralSettings): Result<Unit> = transitionLock.withLock {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val store = generalSettingsStore ?: return@withContext Result.failure(IllegalStateException("常规设置存储不可用"))
            runCatching {
                val previous = store.read()
                check(previous == expected) { "常规设置已变化，请重新打开后保存" }
                val next = value.prepared()
                if (next == previous) return@runCatching
                store.write(next)
                try {
                    if (previous.runtimeKey() != next.runtimeKey()) {
                        profileProvider.activeProfileId()?.let { id -> applyProfileLocked(id) { target -> check(target == id) } }
                        invalidateIpRoute()
                    }
                    syncGeneralRuntimeHooks(next)
                    mutableGeneralSettings.value = next
                } catch (failure: Throwable) {
                    try {
                        store.write(previous)
                        // 作者 long：日志订阅失败也需要恢复刚应用的核心参数，避免偏好已回滚而核心仍用新端口或新认证。
                        if (previous.runtimeKey() != next.runtimeKey()) profileProvider.activeProfileId()?.let { id ->
                            applyProfileLocked(id) { target -> check(target == id) }
                        }
                        syncGeneralRuntimeHooks(previous)
                    } catch (_: Exception) {
                        mutableGeneralSettings.value = runCatching { store.read() }.getOrDefault(next)
                        loadedConfigKey = null; loadedAutomaticConfigKey = null
                        stopLocked()
                        error("常规设置恢复失败，服务已停止，请重新保存后启动")
                    }
                    throw failure
                }
            }
        }
    }

    private suspend fun syncGeneralRuntimeHooks(value: GeneralSettings) {
        ServiceConfig.updateNotificationParams(NotificationParams(title = "KClash", onlyStatisticsProxy = value.onlyStatisticsProxy,
            showStopAction = value.showNotificationStopAction))
        if (!mutableState.value.isRunning) return
        if (value.openLogs && !logListenerStarted.get()) { bridge.startLog(); logListenerStarted.set(true) }
        else if (!value.openLogs && logListenerStarted.get()) { bridge.stopLog(); logListenerStarted.set(false) }
    }

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
    private var appliedVpnOptions: com.lonnnnnng.flclash.service.models.VpnOptions? = null
    private var hasSuccessfulRun = false
    private var loadedConfigKey: List<Any?>? = null
    private var loadedAutomaticConfigKey: List<Any?>? = null

    private val resourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableGeoSettings = MutableStateFlow(geoSettingsStore?.read() ?: GeoResourceSettings())
    val geoSettings = mutableGeoSettings.asStateFlow()
    private val mutableGeoUpdates = MutableStateFlow<Map<String, CoreEvent.GeoUpdate>>(emptyMap())
    val geoUpdates = mutableGeoUpdates.asStateFlow()

    init {
        // 作者 long：先订阅再发下载请求，极快的失败也必须清掉更新状态；停止 VPN 不取消核心已有下载。
        resourceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bridge.events.filterIsInstance<CoreEvent.GeoUpdate>().collect { event ->
                mutableGeoUpdates.update { it + (event.type to event) }
                if (!event.updating) transitionLock.withLock {
                    if (!event.skipped && event.error.isEmpty()) invalidateIpRoute()
                    stopIdleResourceEventListener()
                }
            }
        }
        resourceScope.launch {
            val store = geoSettingsStore ?: return@launch
            store.changes?.collect {
                transitionLock.withLock {
                    // 作者 long：备份从持久层恢复设置，不在 Room 事务内反向调用核心；这里只失效加载输入，下一次应用使用新设置。
                    val current = store.read().validated()
                    if (current != mutableGeoSettings.value) {
                        mutableGeoSettings.value = current
                        loadedConfigKey = null
                        loadedAutomaticConfigKey = null
                    }
                }
            }
        }
    }

    suspend fun requestGeoUpdate(type: GeoResourceType) = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            check(mutableGeoUpdates.value[type.name]?.updating != true) { "此资源正在更新" }
            ensureInitialized()
            ensureConfigLoaded()
            if (eventListenerStarted.compareAndSet(false, true)) bridge.startEventListener()
            mutableGeoUpdates.update { it + (type.name to CoreEvent.GeoUpdate(type.name, true)) }
            try { bridge.updateGeoData(type.name) }
            catch (failure: Exception) {
                mutableGeoUpdates.update { it + (type.name to CoreEvent.GeoUpdate(type.name, false, error = "Geo 更新请求失败")) }
                stopIdleResourceEventListener()
                throw failure
            }
        }
    }

    suspend fun setGeoSettings(settings: GeoResourceSettings) = transitionLock.withLock {
        withContext(Dispatchers.IO) {
            val next = settings.validated()
            val previous = mutableGeoSettings.value
            check(mutableGeoUpdates.value.values.none { it.updating }) { "请等待资源更新完成后修改设置" }
            val oldContent = paths.configFile.takeIf { loadedConfigKey != null && it.isFile }?.readText()
            try {
                if (oldContent != null) {
                    if (next.autoUpdate && eventListenerStarted.compareAndSet(false, true)) bridge.startEventListener()
                    bridge.updateGeoSettings(next)
                    paths.configFile.writeText(GeoResourceSettingsApplier.apply(oldContent, next))
                }
                geoSettingsStore?.write(next)
                mutableGeoSettings.value = next
                stopIdleResourceEventListener()
            } catch (failure: Exception) {
                if (oldContent != null) {
                    val restored = runCatching {
                        bridge.updateGeoSettings(previous)
                        paths.configFile.writeText(oldContent)
                    }.isSuccess
                    if (!restored) { loadedConfigKey = null; loadedAutomaticConfigKey = null }
                }
                stopIdleResourceEventListener()
                throw failure
            }
        }
    }

    private fun stopIdleResourceEventListener() {
        if (mutableState.value in setOf(CoreRuntimeStatus.STOPPED, CoreRuntimeStatus.FAILED) && !needsResourceEvents() &&
            eventListenerStarted.compareAndSet(true, false)) bridge.stopEventListener()
    }

    private fun needsResourceEvents(): Boolean = mutableGeoSettings.value.autoUpdate || mutableGeoUpdates.value.values.any { it.updating }

    private fun configKey(profile: ActiveProfile): List<Any?> {
        val source = File(profile.filePath)
        val script = profile.scriptPath?.let(::File)
        return listOf(profile.id, profile.filePath, source.length(), source.lastModified(),
            profile.overridesJson, profile.standardExtension, profile.scriptPath, script?.length(), script?.lastModified(), networkSettingsStore?.read(), if (profile.usesGlobalDns) globalDnsSettingsStore?.read() else null, generalSettingsStore?.read()?.runtimeKey(), lanSharingStore.read())
    }

    private fun automaticConfigKey(profile: ActiveProfile): List<Any?> {
        val source = File(profile.filePath)
        val overrides = ProfileProviderOverrides.fromJsonString(profile.overridesJson).copy(extension = ProfileExtensionOverrides())
        return listOf(profile.id, profile.filePath, source.length(), source.lastModified(), overrides.toJsonString(), networkSettingsStore?.read(), if (profile.usesGlobalDns) globalDnsSettingsStore?.read() else null, generalSettingsStore?.read()?.runtimeKey(), lanSharingStore.read())
    }

    private suspend fun ensureConfigLoaded(force: Boolean = false) = withContext(Dispatchers.IO) {
        val profile = requireNotNull(profileProvider.activeProfile()) { "请先添加可用配置" }
        val key = configKey(profile)
        if (!force && loadedConfigKey == key) return@withContext
        // 作者 long：代理页会因扩展元数据变化后台读取节点；只改扩展/脚本时沿用已应用配置，不能绕过页面的应用与回滚入口。
        // 订阅正文、DNS/Provider 等覆盖或 LAN 真正改变后仍重新加载，保持原有更新链路。
        if (!force && profile.deferExtensionApplication && loadedConfigKey?.firstOrNull() == profile.id &&
            loadedAutomaticConfigKey == automaticConfigKey(profile)) return@withContext
        ensureInitialized()
        stageConfig(profile)
        bridge.validateConfig(paths.configFile.path)
        // 作者 long：Go 已受理的加载不能随页面刷新取消；先完成核心与输入登记，再通知观察者，避免刷新取消自身并循环重载。
        withContext(NonCancellable) {
            loadedConfigKey = null
            loadedAutomaticConfigKey = null
            if (mutableGeoSettings.value.autoUpdate && eventListenerStarted.compareAndSet(false, true)) bridge.startEventListener()
            bridge.setupConfig(profile.selectedMap, skipCertVerify = generalSettingsStore?.read()?.checkCertificate == false)
            val mode = preferredOutboundMode ?: bridge.getConfiguredMode(paths.configFile.path)
            if (preferredOutboundMode != null) bridge.updateMode(mode)
            mutableOutboundMode.value = mode
            if (preferredOutboundMode == null) {
                preferredOutboundMode = mode
                outboundModeStore.write(mode)
            }
            // 作者 long：配置加载不启用监听或 VPN，停止态也能读取真实节点、选择和测速。
            loadedConfigKey = key
            loadedAutomaticConfigKey = automaticConfigKey(profile)
            invalidateIpRoute()
        }
    }

    override suspend fun profileInputsApplied(id: Long): Boolean = transitionLock.withLock {
        val profile = profileProvider.activeProfile()
        profile?.id == id && loadedConfigKey == configKey(profile)
    }

    override suspend fun applyCurrentProfile(id: Long) = transitionLock.withLock {
        // 作者 long：退出扩展页时配置可能已在别处切换，自动应用只能刷新当前目标，绝不能把旧配置重新选中。
        check(profileProvider.activeProfileId() == id) { "当前配置已切换，扩展已保存，将在选用时应用" }
        applyProfileLocked(id) { target -> check(target == id) { "当前配置已变化" } }
    }

    override suspend fun selectProfile(id: Long, select: suspend (Long?) -> Unit) = transitionLock.withLock {
        applyProfileLocked(id, select)
    }

    override suspend fun <T> withProfileWrite(applyCurrentId: Long?, write: suspend () -> T): T = transitionLock.withLock {
        check(mutableState.value != CoreRuntimeStatus.STARTING && mutableState.value != CoreRuntimeStatus.STOPPING) { "核心正在启停，请稍后重试" }
        // 作者 long：固定锁序为生命周期锁→文件锁；受理后的源文件提交不能被启动、切换或取消打断。
        withContext(kotlinx.coroutines.NonCancellable) {
            val result = write()
            if (applyCurrentId != null) {
                // 作者 long：提交与核心应用共持启停锁，节点轮询不能抢先加载新正文却沿用旧端口或旧TUN。
                val selected = profileProvider.activeProfileId()
                if (selected != null) {
                    try { applyProfileLocked(selected) { check(it == selected) { "当前配置已变化" } } }
                    catch (_: Exception) { throw ProfileApplyAfterSaveException(selected) }
                }
            }
            result
        }
    }

    override suspend fun <T> withScriptWrite(scriptPath: String?, write: suspend () -> T): T = transitionLock.withLock {
        check(mutableState.value != CoreRuntimeStatus.STARTING && mutableState.value != CoreRuntimeStatus.STOPPING) { "核心正在启停，请稍后重试" }
        // 作者 long：删除会先解除绑定，因此在提交前记住当前使用者；只改无关脚本或排序不应重载当前配置。
        val target = if (scriptPath == null) null else profileProvider.activeProfile()?.takeIf { it.scriptPath == scriptPath }?.id
        withContext(NonCancellable) {
            val result = write()
            if (target != null) {
                try { applyProfileLocked(target) { check(it == profileProvider.activeProfileId()) { "当前配置已变化" } } }
                catch (_: Exception) { throw ProfileApplyAfterSaveException(target) }
            }
            result
        }
    }

    override suspend fun <T> withExtensionWrite(targetId: Long?, write: suspend () -> T): T = transitionLock.withLock {
        check(mutableState.value != CoreRuntimeStatus.STARTING && mutableState.value != CoreRuntimeStatus.STOPPING) { "核心正在启停，请稍后重试" }
        withContext(NonCancellable) {
            val result = write()
            // 作者 long：全局目录提交与应用共持锁，但编辑期间切换过配置时只保存目录，不把旧目标重新选中。
            if (targetId != null && profileProvider.activeProfileId() == targetId) {
                try { applyProfileLocked(targetId) { check(it == targetId) { "当前配置已变化" } } }
                catch (_: Exception) { throw ProfileApplyAfterSaveException(targetId) }
            }
            result
        }
    }

    override suspend fun removeProfile(id: Long, remaining: suspend () -> List<Long>, select: suspend (Long?) -> Unit, remove: suspend () -> Unit) = transitionLock.withLock {
        check(mutableState.value != CoreRuntimeStatus.STARTING && mutableState.value != CoreRuntimeStatus.STOPPING) { "核心正在启停，请稍后重试" }
        withContext(kotlinx.coroutines.NonCancellable) {
            val previousId = profileProvider.activeProfileId()
            if (previousId != id) {
                remove()
                return@withContext
            }
            val fallback = remaining().firstOrNull { it != id }
            // 作者 long：先验证并应用替代配置，再删除旧源；替代加载失败时旧配置和运行会话都可完整保留。
            if (fallback != null) applyProfileLocked(fallback, select) else stopLocked().getOrThrow()
            try {
                remove()
                if (fallback == null) {
                    // 作者 long：删除事务已经持久化最终选择；提交后不再重复写偏好，避免删除成功却被误报为失败。
                    loadedConfigKey = null
                    loadedAutomaticConfigKey = null
                    invalidateIpRoute()
                }
            } catch (failure: Exception) {
                if (fallback != null) {
                    try { applyProfileLocked(id, select) }
                    catch (rollback: Exception) {
                        failure.addSuppressed(rollback)
                        stopLocked()
                    }
                }
                val stopped = if (!mutableState.value.isRunning) "，核心已停止" else ""
                throw IllegalStateException("删除配置失败，原配置已保留$stopped，请重试", failure)
            }
        }
    }

    private suspend fun applyProfileLocked(id: Long, select: suspend (Long?) -> Unit) {
        withContext(Dispatchers.IO) {
            val previous = profileProvider.activeProfile()
            if (previous?.id == id && loadedConfigKey == configKey(previous)) return@withContext
            val previousContent = paths.configFile.takeIf { it.isFile }?.readBytes()
            val previousKey = loadedConfigKey
            val previousAutomaticKey = loadedAutomaticConfigKey
            val previousOptions = appliedVpnOptions
            var servicesChanged = false
            try {
                select(id)
                ensureConfigLoaded(force = true)
                if (mutableState.value.isRunning && previousOptions != null) {
                    // 作者 long：配置换端口或 TUN 参数后必须同步系统网络；待重启的 VPN 开关仍保持当前会话选择。
                    val nextOptions = vpnOptionsProvider.current().copy(enable = previousOptions.enable)
                    if (nextOptions != previousOptions) {
                        // 作者 long：暂停期间仍允许配置投影更新，但不能因为端口/TUN变化重新接管排除Wi-Fi。
                        if (!mutableOnDemandSuspended.value) {
                            servicesChanged = true
                            serviceController.stopAndAwait()
                            serviceController.start(nextOptions)
                        }
                        appliedVpnOptions = nextOptions
                    }
                }
            } catch (failure: Throwable) {
                var restored = true
                // 作者 long：加载失败必须恢复选择和已应用输入，不能让页面新配置与仍在服务的旧节点分裂。
                withContext(kotlinx.coroutines.NonCancellable) {
                    select(previous?.id)
                    loadedConfigKey = null
                    if (previousContent != null && previousKey != null && previous != null) {
                        try {
                            paths.configFile.writeBytes(previousContent)
                            bridge.setupConfig(previous.selectedMap, skipCertVerify = !mutableGeneralSettings.value.checkCertificate)
                            bridge.updateMode(mutableOutboundMode.value)
                            if (servicesChanged && previousOptions != null) {
                                serviceController.stopAndAwait()
                                serviceController.start(previousOptions)
                                appliedVpnOptions = previousOptions
                            }
                            loadedConfigKey = previousKey
                            loadedAutomaticConfigKey = previousAutomaticKey
                        } catch (_: Exception) {
                            restored = false
                            runCatching { serviceController.stop() }
                            runCatching { stopListeners() }
                            runCatching { runtimeResumeStore.setShouldResume(false) }
                            mutableOnDemandSuspended.value = false
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
            if (generalSettingsStore?.read()?.openLogs != false && logListenerStarted.compareAndSet(false, true)) {
                bridge.startLog()
            }
            ServiceConfig.updateNotificationParams(
                // 作者 long：通知动作和流量口径跟随全局偏好，修改后由通知模块立即回读刷新。
                NotificationParams(title = "KClash", showStopAction = mutableGeneralSettings.value.showNotificationStopAction,
                    onlyStatisticsProxy = mutableGeneralSettings.value.onlyStatisticsProxy),
            )
            val vpnOptions = vpnOptionsProvider.current()
            appliedVpnOptions = vpnOptions
            if (!onDemandExcluded) {
                serviceController.start(vpnOptions)
                if (!listenerStarted.get()) {
                    bridge.startListener()
                    listenerStarted.set(true)
                }
            }
            // 作者 long：排除Wi-Fi也记录运行意图与计时，但监听保持关闭；离开后续接同一会话而非重新启动。
            mutableOnDemandSuspended.value = onDemandExcluded
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
            mutableOnDemandSuspended.value = false
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
        mutableOnDemandSuspended.value = false
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
            if (eventListenerStarted.get() && !needsResourceEvents()) {
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
        mutableOnDemandSuspended.value = false
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

    suspend fun getProxyDefinition(profileId: Long, name: String): com.google.gson.JsonObject = transitionLock.withLock {
        // 作者 long：菜单属于打开时的配置；与切配置共用锁，防止同名节点被另一份配置的定义替换。
        val profile = requireNotNull(profileProvider.activeProfile()) { "请先添加可用配置" }
        check(profile.id == profileId) { "配置已切换，请重新打开节点菜单" }
        ensureConfigLoaded()
        val inputs = automaticConfigKey(profile)
        check(loadedAutomaticConfigKey == inputs) { "配置已变化，请刷新代理列表后重试" }
        val definition = bridge.getProxyDefinition(name)
        val current = profileProvider.activeProfile()
        check(current?.id == profileId && current == profile && automaticConfigKey(current) == inputs) {
            "配置已变化，请刷新代理列表后重试"
        }
        requireNotNull(definition) { "无法读取节点定义，请刷新代理列表后重试" }.deepCopy()
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
                if (generalSettingsStore?.read()?.closeConnections == true && mutableState.value.isRunning) {
                    // 作者 long：核心已切换后关闭旧连接，让下一次请求使用新节点；关闭失败不能把已完成的切换伪装成失败。
                    runCatching { bridge.closeConnections() }
                }
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
            bridge.testDelay(proxyName, testUrl, if (timeoutMs == 0L) mutableGeneralSettings.value.delayTestTimeout.toLong() else timeoutMs)
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
            val effectiveTimeout = if (timeoutMs == 0L) mutableGeneralSettings.value.delayTestTimeout.toLong() else timeoutMs
            require(effectiveTimeout > 0) { "测速超时必须大于 0" }
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
                                    result = bridge.testDelay(request.proxyName, request.testUrl, effectiveTimeout),
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
            check(allowLan != null || !profile.deferExtensionApplication || loadedConfigKey == configKey(profile)) {
                "扩展尚未应用，请先在扩展页应用后再恢复配置的局域网设置"
            }
            val previous = withTimeout(3_000L) { bridge.getLanConfig() }
            val savedOverride = lanSharingStore.read()
            val savedContent = paths.configFile.readBytes()
            val savedKey = loadedConfigKey
            val savedAutomaticKey = loadedAutomaticConfigKey
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
                // 作者 long：普通 LAN 开关只补丁已应用正文；保留旧扩展指纹，不能把尚未应用的规则/脚本误标为已应用。
                loadedConfigKey = if (allowLan != null && savedKey != null) savedKey.dropLast(1) + listOf(lanSharingStore.read()) else configKey(profile)
                loadedAutomaticConfigKey = if (allowLan != null && savedAutomaticKey != null) savedAutomaticKey.dropLast(1) + listOf(lanSharingStore.read()) else automaticConfigKey(profile)
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
                        loadedAutomaticConfigKey = savedAutomaticKey
                        mutableLanSharing.value = LanSharingState(previous, true)
                    } catch (_: Exception) {
                        restored = false
                        loadedConfigKey = null
                        runCatching { serviceController.stop() }
                        runCatching { stopListeners() }
                        runCatching { runtimeResumeStore.setShouldResume(false) }
                        mutableOnDemandSuspended.value = false
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
                traffic = bridge.getTraffic(mutableGeneralSettings.value.onlyStatisticsProxy),
                totalTraffic = bridge.getTotalTraffic(mutableGeneralSettings.value.onlyStatisticsProxy),
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
            Triple(mutableIpRouteRevision.value, forwardingActive(), withTimeout(3_000L) { bridge.ipRouteStamp() })
        }
        // 作者 long：外部服务可能超时，网络等待不占启停锁；停止态强制直连，不沿用上次核心规则。
        val result = withTimeout(41_000L) { bridge.checkServices(targets, direct = !running, timeoutMs = 6_000L) }
        check(result.size == targets.size && result.map { it.target }.toSet() == targets.toSet()) { "服务检测结果不完整" }
        transitionLock.withLock {
            if (mutableIpRouteRevision.value != revision || forwardingActive() != running ||
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
            val running = forwardingActive()
            if ((!running && proxyName != "DIRECT") || withTimeout(3_000L) { bridge.ipRouteStamp() } != expected) {
                throw ServiceRouteChangedException()
            }
            mutableIpRouteRevision.value to running
        }
        // 作者 long：指定出口查询不能占用启停锁；停止态只允许 DIRECT，旧运行结果不能借此重新使用代理。
        val result = withTimeout(17_000L) { bridge.outboundIpForProxy(urls, 6_000L, proxyName) }
        transitionLock.withLock {
            if (revision != mutableIpRouteRevision.value || running != forwardingActive() ||
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
            mutableIpRouteRevision.value to forwardingActive()
        }
        // 作者 long：外部网络等待不得占用启停锁；Kotlin 取消后 Go 请求仍由自身超时收口。
        val result = withTimeout(timeoutMs * 2 + 5_000L) { request(running) }
        transitionLock.withLock {
            check(forwardingActive() == running && mutableIpRouteRevision.value == revision) { "出口已变化，请重新查询" }
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
        if (eventListenerStarted.get() && !needsResourceEvents()) {
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

    override suspend fun readProfileProvider(profileId: Long, name: String): ProfileProviderDocument = withContext(Dispatchers.IO) {
        try {
            val snapshot = providerSnapshot(profileId)
            val document = withTimeout(8_000L) { bridge.readExternalProvider(name, snapshot.stamp) }
            transitionLock.withLock { verifyProviderSnapshot(snapshot) }
            ProfileProviderDocument(profileId, document, snapshot, providerRecoveryKey(snapshot))
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { throw ProviderOperationException("无法读取 Provider，配置或文件可能已变化，请重试") }
    }

    private fun providerRecoveryKey(snapshot: ProviderSnapshot): String {
        // 作者 long：进程恢复只能重建绑定；持久摘要同时覆盖配置、脚本与全局输入，不能复用上个进程的核心令牌。
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        hash.update(com.google.gson.Gson().toJson(snapshot.key).toByteArray(Charsets.UTF_8))
        listOfNotNull(snapshot.profile.filePath, snapshot.profile.scriptPath).forEach { path ->
            File(path).inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    override suspend fun restoreProfileProvider(profileId: Long, name: String, recoveryKey: String, digest: String): ProfileProviderDocument {
        transitionLock.withLock {
            check(profileProvider.activeProfile()?.id == profileId)
            ensureConfigLoaded()
        }
        return super<ProfileStatisticsRuntime>.restoreProfileProvider(profileId, name, recoveryKey, digest)
    }

    override suspend fun saveProfileProvider(document: ProfileProviderDocument, content: String): Unit = withContext(Dispatchers.IO) {
        if (!providerUpdateLock.tryLock()) throw ProviderOperationException("已有 Provider 正在更新，请稍后重试")
        try {
            val snapshot = document.binding as? ProviderSnapshot ?: throw ProviderOperationException("编辑会话已失效，请重新打开")
            require(document.profileId == snapshot.profile.id)
            require(content.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024 && '\u0000' !in content)
            transitionLock.withLock { verifyProviderSnapshot(snapshot) }
            // 作者 long：核心已受理侧载后不能因窗口取消丢弃提交结果；Go在同一配置锁内校验、写缓存并处理失败回滚。
            withContext(kotlinx.coroutines.NonCancellable) {
                bridge.sideLoadExternalProvider(document.document.name, content, document.document.digest, snapshot.stamp)
                try {
                    transitionLock.withLock {
                        verifyProviderSnapshot(snapshot, allowRouteChange = true)
                        invalidateIpRoute()
                    }
                } catch (_: Exception) {
                    // 作者 long：核心已写入缓存，后置绑定校验失败不能再报告为可安全重交的保存失败。
                    throw ProviderOperationException("Provider 已提交，但配置已变化，请重新读取确认", committed = true)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: ProviderOperationException) { throw error }
        catch (error: Exception) {
            val message = when ((error as? com.lonnnnnng.flclash.core.CoreBridgeException)?.code) {
                "provider_content_error" -> "内容无法被此 Provider 读取，请检查格式后重试"
                "provider_updating" -> "Provider 正在更新，请稍后重试"
                "provider_stale" -> "配置或文件已变化，请重新打开后再保存"
                else -> "Provider 保存未完成，请检查内容并重试"
            }
            throw ProviderOperationException(message)
        } finally { providerUpdateLock.unlock() }
    }

    override suspend fun updateProfileProviders(profileId: Long, names: List<String>): List<com.lonnnnnng.flclash.core.CoreProviderUpdateResult> = withContext(Dispatchers.IO) {
        if (!providerUpdateLock.tryLock()) throw ProviderOperationException("已有 Provider 正在更新，请稍后重试")
        try {
            val snapshot = providerSnapshot(profileId)
            val providers = withTimeout(3_000L) { bridge.externalProviders() }
            require(names.isNotEmpty() && names.distinct().size == names.size && names.all { name -> providers.any { it.name == name && it.canUpdate } })
            transitionLock.withLock { verifyProviderSnapshot(snapshot) }
            withContext(kotlinx.coroutines.NonCancellable) {
                val result = bridge.updateExternalProviders(names, snapshot.stamp)
                transitionLock.withLock {
                    // 作者 long：同一批资源各自完成会改变路由版本，最终只接受原配置和原核心会话，不把正常更新当作过期。
                    verifyProviderSnapshot(snapshot, allowRouteChange = true)
                    invalidateIpRoute()
                }
                result
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: ProviderOperationException) { throw error }
        catch (_: Exception) { throw ProviderOperationException("批量更新未完成，配置或网络可能已变化，请重试") }
        finally { providerUpdateLock.unlock() }
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
        val snapshot = transitionLock.withLock {
            val target = requireNotNull(profileProvider.profileById(profileId)) { "配置不存在" }
            val currentId = profileProvider.activeProfileId()
            if (mutableState.value.isRunning && currentId == target.id) {
                // 作者 long：只有确实交给核心的当前配置可标为已应用，不能把另一会话的 config.yaml 当作目标预览。
                check(loadedConfigKey == configKey(target)) { "配置或核心状态已变化，请重试" }
                return@withContext RuntimeConfigPreview(target.id, target.label, readPreviewFile(paths.configFile), true)
            }
            PreviewInput(target, currentId, mutableIpRouteRevision.value, mutableState.value, lanSharingStore.read(), networkSettingsStore?.read(), globalDnsSettingsStore?.read(), generalSettingsStore?.read())
        }
        val profile = snapshot.profile
        val source = File(profile.filePath)
        val script = profile.scriptPath?.let(::File)
        val sourceBytes = readPreviewBytes(source)
        script?.let { require(it.isFile) { "绑定脚本正文不存在" } }
        val scriptBytes = script?.let { readPreviewBytes(it) }
        // 作者 long：非当前配置与停止态都只在内存渲染，慢脚本不占启停锁，不加载 Provider 或覆盖 config.yaml。
        val content = renderProfileContent(profile, sourceBytes.toString(Charsets.UTF_8), snapshot.lanOverride,
            scriptBytes?.toString(Charsets.UTF_8), snapshot.networkSettings, snapshot.globalDns, snapshot.generalSettings)
        check(content.toByteArray(Charsets.UTF_8).size <= MAX_PREVIEW_BYTES) { "配置超过 2 MiB，暂不支持预览" }
        // 作者 long：比较原字节，等长且保留 mtime 的替换也必须被识别；大文件读取放在启停锁之外。
        val filesUnchanged = source.isFile && sourceBytes.contentEquals(readPreviewBytes(source)) &&
            (script == null || script.isFile && checkNotNull(scriptBytes).contentEquals(readPreviewBytes(script)))
        transitionLock.withLock {
            check(mutableIpRouteRevision.value == snapshot.revision && mutableState.value == snapshot.runtime &&
                profileProvider.activeProfileId() == snapshot.currentProfileId &&
                profileProvider.profileById(profileId) == profile && lanSharingStore.read() == snapshot.lanOverride &&
                networkSettingsStore?.read() == snapshot.networkSettings && globalDnsSettingsStore?.read() == snapshot.globalDns && generalSettingsStore?.read() == snapshot.generalSettings && filesUnchanged
            ) { "配置或核心状态已变化，请重试" }
        }
        RuntimeConfigPreview(profile.id, profile.label, content, false)
    }

    // 作者 long：快照不生成 toString，避免源配置和绑定脚本的信息意外进入异常日志。
    private class PreviewInput(val profile: ActiveProfile, val currentProfileId: Long?, val revision: Long,
        val runtime: CoreRuntimeStatus, val lanOverride: Boolean?, val networkSettings: NetworkSettings?, val globalDns: ProfileDnsOverrides?, val generalSettings: GeneralSettings?)

    private fun readPreviewFile(file: File, limit: Int = MAX_PREVIEW_BYTES): String =
        readPreviewBytes(file, limit).toString(Charsets.UTF_8)

    private fun readPreviewBytes(file: File, limit: Int = MAX_PREVIEW_BYTES): ByteArray {
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
        return bytes
    }

    private suspend fun renderProfileContent(profile: ActiveProfile, sourceContent: String,
        lanOverride: Boolean? = lanSharingStore.read(), scriptContent: String? = null,
        networkSettings: NetworkSettings? = networkSettingsStore?.read(), globalDns: ProfileDnsOverrides? = globalDnsSettingsStore?.read(), generalSettings: GeneralSettings? = generalSettingsStore?.read()): String {
        // 作者 long：脚本生成配置后先补 DNS 运行基线，再合并显式覆盖；预览和启动共用顺序，防止无 DNS 订阅接管后断网。
        val scriptedContent = if (scriptContent == null) profileScriptApplier.apply(sourceContent, profile.scriptPath)
            else profileScriptApplier.applyContent(sourceContent, scriptContent)
        val content = patchLanSharing(GeneralSettingsApplier.apply(patchNetworkSettings(ProfileProviderOverrideApplier.apply(
            GlobalDnsSettings.apply(ProfileDnsBaselineApplier.apply(ProfileExtensionApplier.apply(scriptedContent, profile.standardExtension)),
                if (profile.usesGlobalDns) globalDns ?: ProfileDnsOverrides() else ProfileDnsOverrides()),
            ProfileProviderOverrides.fromJsonString(profile.overridesJson),
        ), networkSettings), generalSettings), lanOverride)
        return if (geoSettingsStore == null) content else GeoResourceSettingsApplier.apply(content, mutableGeoSettings.value)
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
