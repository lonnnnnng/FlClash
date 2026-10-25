package com.lonnnnnng.flclash.service

import android.os.Build
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreEvent
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
) {
    private val transitionLock = Mutex()
    private val initialized = AtomicBoolean(false)
    private val listenerStarted = AtomicBoolean(false)
    private val eventListenerStarted = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(CoreRuntimeStatus.STOPPED)

    val state = mutableState.asStateFlow()
    val events: Flow<CoreEvent> = bridge.events

    suspend fun startProxy(): Result<Unit> = transitionLock.withLock {
        if (mutableState.value == CoreRuntimeStatus.RUNNING) {
            return@withLock Result.success(Unit)
        }
        mutableState.value = CoreRuntimeStatus.STARTING
        runCatching {
            val profile = requireNotNull(profileProvider.activeProfile()) { "请先添加可用配置" }
            ensureInitialized()
            stageConfig(profile.filePath)
            // 作者 long：Mihomo 解析失败时会回退到内置空配置，所以必须先独立校验文件，
            // 只有校验通过后才允许调用 setupConfig，避免把回退状态报告成可用代理。
            bridge.validateConfig(paths.configFile.path)
            bridge.setupConfig()
            if (eventListenerStarted.compareAndSet(false, true)) {
                bridge.startEventListener()
            }
            ServiceConfig.updateNotificationParams(
                NotificationParams(title = "KClash", showStopAction = false),
            )
            val vpnOptions = vpnOptionsProvider.current()
            serviceController.start(vpnOptions)
            if (!listenerStarted.get()) {
                bridge.startListener()
                listenerStarted.set(true)
            }
            mutableState.value = CoreRuntimeStatus.RUNNING
        }.onFailure {
            runCatching { serviceController.stop() }
            if (listenerStarted.get()) {
                runCatching { bridge.stopListener() }
                listenerStarted.set(false)
            }
            if (eventListenerStarted.get()) {
                runCatching { bridge.stopEventListener() }
                eventListenerStarted.set(false)
            }
            mutableState.value = CoreRuntimeStatus.FAILED
        }
    }

    suspend fun stop(): Result<Unit> = transitionLock.withLock {
        if (mutableState.value == CoreRuntimeStatus.STOPPED) {
            return@withLock Result.success(Unit)
        }
        mutableState.value = CoreRuntimeStatus.STOPPING
        runCatching {
            serviceController.stop()
            if (listenerStarted.get()) {
                bridge.stopListener()
                listenerStarted.set(false)
            }
            if (eventListenerStarted.get()) {
                bridge.stopEventListener()
                eventListenerStarted.set(false)
            }
            mutableState.value = CoreRuntimeStatus.STOPPED
        }.onFailure {
            mutableState.value = CoreRuntimeStatus.FAILED
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
