package com.lonnnnnng.flclash.service

import android.os.Build
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.service.models.NotificationParams
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow

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
) {
    init {
        RuntimeDiagnosticHistory.configure(runtimeDiagnosticStore)
    }

    private val transitionLock = Mutex()
    private val initialized = AtomicBoolean(false)
    private val listenerStarted = AtomicBoolean(false)
    private val eventListenerStarted = AtomicBoolean(false)
    private val logListenerStarted = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    private var hasSuccessfulRun = false

    val state = mutableState.asStateFlow()
    val events: Flow<CoreEvent> = bridge.events
    val eventFlowStats = bridge.eventFlowStats
    val diagnosticEvents = RuntimeDiagnosticHistory.events

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

    suspend fun startProxy(): Result<Unit> = transitionLock.withLock {
        if (mutableState.value == CoreRuntimeStatus.RUNNING) {
            return@withLock Result.success(Unit)
        }
        mutableState.value = CoreRuntimeStatus.STARTING
        runCatching {
            val profile = requireNotNull(profileProvider.activeProfile()) { "请先添加可用配置" }
            // 作者 long：先记录恢复意图，再进行配置和服务启动，进程若在启动中断开也能由 Application 重试。
            runtimeResumeStore.setShouldResume(true)
            ensureInitialized()
            stageConfig(profile.filePath)
            // 作者 long：Mihomo 解析失败时会回退到内置空配置，所以必须先独立校验文件，
            // 只有校验通过后才允许调用 setupConfig，避免把回退状态报告成可用代理。
            bridge.validateConfig(paths.configFile.path)
            bridge.setupConfig()
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
            mutableState.value = CoreRuntimeStatus.RUNNING
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
            RuntimeDiagnosticHistory.record(
                RuntimeDiagnosticKind.CORE_FAILED,
                error::class.simpleName ?: "核心启动失败",
            )
        }.also { result ->
            if (result.isFailure) RuntimeDiagnosticHistory.flush()
        }
    }

    suspend fun stop(): Result<Unit> = transitionLock.withLock {
        runCatching {
            // 作者 long：显式停止必须先清除恢复意图，避免下一次 Application 启动把用户停止误判成系统重建。
            runtimeResumeStore.setShouldResume(false)
            if (mutableState.value == CoreRuntimeStatus.STOPPED) {
                // 作者 long：即使核心状态已经先被服务回调置为停止，也要等待网络诊断尾巴落盘，
                // 避免用户连续点击停止或系统重复回调时丢掉最后一条事件。
                RuntimeDiagnosticHistory.flush()
                return@runCatching Unit
            }
            mutableState.value = CoreRuntimeStatus.STOPPING
            serviceController.stop()
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
                error::class.simpleName ?: "核心停止失败",
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
                error::class.simpleName ?: "VPN 撤销收口失败",
            )
        }.also { result ->
            if (result.isFailure) RuntimeDiagnosticHistory.flush()
        }
    }

    suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot> = transitionLock.withLock {
        runCatching {
            check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
            bridge.getProxyGroups()
        }
    }

    suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot> =
        transitionLock.withLock {
            runCatching {
                check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
                bridge.changeProxy(groupName, proxyName)
                // 作者 long：切换结果以核心重新返回的当前选择为准，避免 UI 只更新本地猜测而与 Mihomo 状态漂移。
                bridge.getProxyGroups()
            }
        }

    /**
     * 作者 long：测速与启动、停止、代理切换共用生命周期锁，避免核心正在切换配置时读取到半成品结果。
     */
    suspend fun testDelay(
        proxyName: String,
        testUrl: String = CoreBridge.DEFAULT_TEST_URL,
        timeoutMs: Long = CoreBridge.DEFAULT_DELAY_TIMEOUT_MS,
    ): Result<ProxyDelay?> = transitionLock.withLock {
        runCatching {
            check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
            bridge.testDelay(proxyName, testUrl, timeoutMs)
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

    suspend fun getStats(): Result<CoreRuntimeStats> = transitionLock.withLock {
        runCatching {
            check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
            CoreRuntimeStats(
                traffic = bridge.getTraffic(),
                totalTraffic = bridge.getTotalTraffic(),
                connectionCount = bridge.getConnectionCount(),
            )
        }
    }

    suspend fun getConnections(): Result<List<CoreConnection>> = transitionLock.withLock {
        runCatching {
            check(mutableState.value == CoreRuntimeStatus.RUNNING) { "请先启动核心" }
            bridge.getConnections()
        }
    }

    private suspend fun ensureInitialized() {
        if (initialized.get()) return
        paths.homeDir.mkdirs()
        if (initialized.compareAndSet(false, true)) {
            runCatching {
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

    private fun stageConfig(sourcePath: String) {
        val source = File(sourcePath)
        require(source.isFile) { "配置文件不存在：${source.path}" }
        paths.homeDir.mkdirs()
        val temporary = File(paths.homeDir, ".${paths.configFile.name}.tmp")
        try {
            source.inputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
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
}

data class CoreRuntimeStats(
    val traffic: CoreTraffic,
    val totalTraffic: CoreTraffic,
    val connectionCount: Int,
)
