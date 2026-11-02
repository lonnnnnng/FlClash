package com.lonnnnnng.flclash.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.service.models.VpnOptions
import com.lonnnnnng.flclash.service.ServiceConfig
import com.lonnnnnng.flclash.service.VpnRuntimeState
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

data class ActiveProfile(
    val id: Long,
    val label: String,
    val filePath: String,
    val selectedMap: Map<String, String> = emptyMap(),
    val overridesJson: String = "{}",
    val scriptPath: String? = null,
    val standardExtension: StandardProfileExtension? = null,
    val deferExtensionApplication: Boolean = false,
)

data class ProxyDelayRequest(
    val proxyName: String,
    val testUrl: String,
)

data class ProxyDelayBatchResult(
    val request: ProxyDelayRequest,
    val result: ProxyDelay?,
    val error: Throwable? = null,
)

interface ActiveProfileProvider {
    suspend fun activeProfile(): ActiveProfile?

    // 作者 long：指定 ID 的只读查询不能回退到另一份配置，更不能通过改当前选择来复用启动入口。
    suspend fun profileById(id: Long): ActiveProfile? = activeProfile()?.takeIf { it.id == id }

    suspend fun activeProfileId(): Long? = activeProfile()?.id
}

/**
 * 作者 long：脚本执行器放在服务层契约中，核心生命周期不依赖具体 JavaScript 运行时；
 * Android 应用层可以注入 WebView，JVM 测试则可以注入确定性的 fake 实现。
 */
interface ProfileScriptEvaluator {
    suspend fun evaluate(script: String, configJson: String): String
}

/**
 * 作者 long：没有注入真实执行器时必须直接失败，不能把已绑定脚本静默当作未绑定，
 * 否则用户会看到核心启动成功但脚本修改没有生效。
 */
object MissingProfileScriptEvaluator : ProfileScriptEvaluator {
    override suspend fun evaluate(script: String, configJson: String): String =
        error("KClash 尚未配置 JavaScript 执行器")
}

interface ProxyServiceController {
    suspend fun start(options: VpnOptions)

    fun stop()

    suspend fun stopAndAwait() { stop() }
}

interface VpnOptionsProvider {
    suspend fun current(): VpnOptions
}

/**
 * 作者 long：按应用代理设置由 Android 应用层持久化，但服务层只依赖这个窄接口，
 * 这样启动核心时可以读取最新选择，同时避免 service 模块反向依赖 Room 或 Compose。
 */
interface AccessControlSettingsProvider {
    suspend fun current(): AccessControlProps

    suspend fun save(value: AccessControlProps)
}

object DisabledAccessControlSettingsProvider : AccessControlSettingsProvider {
    override suspend fun current(): AccessControlProps = AccessControlProps(
        enable = false,
        mode = AccessControlMode.ACCEPT_SELECTED,
        acceptList = emptyList(),
        rejectList = emptyList(),
    )

    override suspend fun save(value: AccessControlProps) = Unit
}

/**
 * 作者 long：运行意图必须跨越应用进程重建保存，才能区分“系统杀进程后恢复”和“用户主动停止”。
 */
interface RuntimeResumeStore {
    suspend fun shouldResume(): Boolean

    suspend fun setShouldResume(value: Boolean)
}

/**
 * 作者 long：VPN 启用选择独立于 Profile，默认保持手机 TUN 接管；
 * HTTP 代理端口与 TUN 参数读取核心已经校验的输入，防止脚本改写后系统仍连接旧端口。
 */
class DefaultVpnOptionsProvider(
    private val accessControlSettingsProvider: AccessControlSettingsProvider =
        DisabledAccessControlSettingsProvider,
    private val vpnSettingsStore: VpnSettingsStore = DefaultVpnSettingsStore,
    private val appliedConfigFile: File? = null,
) : VpnOptionsProvider {
    override suspend fun current(): VpnOptions {
        val profileOptions = appliedConfigFile?.let(::readProfileTunOptions)
        val port = profileOptions?.mixedPort ?: DEFAULT_MIXED_PORT
        return VpnOptions(
            enable = vpnSettingsStore.read(),
            port = port,
            ipv6 = false,
            dnsHijacking = false,
            accessControlProps = accessControlSettingsProvider.current().sanitized(),
            allowBypass = true,
            // 作者 long：与 Flutter 一样广播本地代理，让浏览器把域名交给核心解析；
            // Android 的代理广播不能携带认证信息，有认证的端口必须继续走 TUN。
            systemProxy = port > 0 && profileOptions?.authenticated != true,
            bypassDomain = emptyList(),
            stack = profileOptions?.stack ?: DEFAULT_STACK,
            routeAddress = emptyList(),
            mtu = profileOptions?.mtu ?: 0,
            congestionController = profileOptions?.congestionController,
        )
    }

    private fun readProfileTunOptions(file: File): ProfileTunOptions {
        // 作者 long：生产调用发生在配置暂存、脚本和覆盖应用成功之后，读取失败必须阻止启动，不能广播猜测端口。
        require(file.isFile) { "已应用配置文件不存在" }
        val root = JsonParser.parseString(YamlConfigCodec.toJson(file.readText(Charsets.UTF_8))).asJsonObject
        val port = root.intValue("mixed-port") ?: 0
        require(port in 0..65535) { "mixed-port 超出有效范围" }
        val tun = root.objectValue("tun")
        return ProfileTunOptions(
            mixedPort = port,
            authenticated = root.stringListValue("authentication").any(String::isNotBlank),
            stack = tun?.stringValue("stack")?.takeIf(String::isNotBlank) ?: DEFAULT_STACK,
            mtu = tun?.intValue("mtu")?.takeIf { it > 0 },
            congestionController = tun?.stringValue("congestion-controller")?.takeIf(String::isNotBlank),
        )
    }

    private companion object {
        const val DEFAULT_MIXED_PORT = 7890
        const val DEFAULT_STACK = "mips"
    }

    private fun AccessControlProps.sanitized(): AccessControlProps = copy(
        acceptList = acceptList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
        rejectList = rejectList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
    )
}

private data class ProfileTunOptions(
    val mixedPort: Int,
    val authenticated: Boolean = false,
    val stack: String = "mips",
    val mtu: Int? = null,
    val congestionController: String? = null,
)

private fun JsonObject.objectValue(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.stringValue(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonObject.intValue(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

private fun JsonObject.stringListValue(key: String): List<String> =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { item -> item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
        .orEmpty()

class AndroidProxyServiceController(
    private val context: Context,
) : ProxyServiceController {
    override suspend fun start(options: VpnOptions) {
        ServiceConfig.updateVpnOptions(options)
        ServiceConfig.updateVpnState(VpnRuntimeState.STOPPED)
        ServiceConfig.updateProxyState(VpnRuntimeState.STARTING)
        ContextCompat.startForegroundService(
            context,
            Intent(context, ProxyService::class.java),
        )
        try {
            // 作者 long：本地代理和 VPN 共用核心；关闭 VPN 时只等待代理前台服务，不创建 TUN。
            val proxyState = withTimeout(VPN_START_TIMEOUT_MS) {
                ServiceConfig.proxyState.first { it == VpnRuntimeState.CONNECTED || it == VpnRuntimeState.FAILED }
            }
            check(proxyState == VpnRuntimeState.CONNECTED) { "本地代理服务启动失败" }
            if (!options.enable) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, VpnService::class.java),
            )
            val state = withTimeout(VPN_START_TIMEOUT_MS) {
                ServiceConfig.vpnState.first { vpnState ->
                    vpnState == VpnRuntimeState.CONNECTED ||
                        vpnState == VpnRuntimeState.FAILED
                }
            }
            if (state == VpnRuntimeState.FAILED) {
                throw IllegalStateException(
                    ServiceConfig.vpnError.value ?: "VPN 服务启动失败",
                )
            }
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    override suspend fun stopAndAwait() {
        stop()
        // 作者 long：STOP action 只发出异步请求；旧实例彻底销毁后才能重用同一网络观察器和前台通知。
        withTimeout(VPN_START_TIMEOUT_MS) {
            ServiceConfig.proxyAlive.first { !it }
            ServiceConfig.vpnAlive.first { !it }
        }
    }

    override fun stop() {
        // 作者 long：VpnService 由系统保持绑定，必须先发送停止 action 让其关闭 TUN，
        // 再停止服务组件；只调用 stopService 会留下 tun0 和系统 VPN 路由。
        stopServiceWithCleanup(ProxyService::class.java, ServiceConfig.proxyAlive.value)
        stopServiceWithCleanup(VpnService::class.java, ServiceConfig.vpnAlive.value)
    }

    private fun stopServiceWithCleanup(serviceClass: Class<out android.app.Service>, alive: Boolean) {
        // 作者 long：未创建的服务也要撤销已排队的启动，但不能为纯代理模式额外创建一个 VPN 实例。
        if (alive) {
            val stopIntent = Intent(context, serviceClass).setAction(ACTION_STOP_SERVICE)
            runCatching { context.startService(stopIntent) }
        }
        context.stopService(Intent(context, serviceClass))
    }

    private companion object {
        const val VPN_START_TIMEOUT_MS = 15_000L
    }
}

data class CoreRuntimePaths(
    val homeDir: File,
    val prepare: suspend () -> Unit = {},
) {
    val configFile: File
        get() = File(homeDir, "config.yaml")
}

interface ProxyGroupsRuntime : CoreRuntimeStatusProvider {
    val proxyGroupsRevision: Flow<Long> get() = kotlinx.coroutines.flow.flowOf(0L)
    val events: Flow<CoreEvent>

    suspend fun getProxyGroups(): Result<ProxyGroupsSnapshot>

    suspend fun changeProxy(groupName: String, proxyName: String): Result<ProxyGroupsSnapshot>

    suspend fun testDelay(
        proxyName: String,
        testUrl: String,
        timeoutMs: Long = CoreBridge.DEFAULT_DELAY_TIMEOUT_MS,
    ): Result<ProxyDelay?>

    suspend fun testDelayBatch(
        requests: List<ProxyDelayRequest>,
        timeoutMs: Long = CoreBridge.DEFAULT_DELAY_TIMEOUT_MS,
        maxConcurrent: Int = 32,
    ): Result<List<ProxyDelayBatchResult>>
}

interface CoreRuntimeBridge {
    suspend fun externalProviders(): List<com.lonnnnnng.flclash.core.CoreExternalProvider> = error("核心未提供 Provider 明细")
    suspend fun updateExternalProvider(name: String, stamp: com.lonnnnnng.flclash.core.CoreRouteStamp): Unit = error("核心未提供 Provider 更新")
    suspend fun providerCounts(): com.lonnnnnng.flclash.core.CoreProviderCounts = error("核心未提供 Provider 统计")
    suspend fun checkRoutes(routes: List<List<com.lonnnnnng.flclash.core.CoreRouteHit>>): com.lonnnnnng.flclash.core.CoreRouteCheck = error("核心未提供路由核验")
    suspend fun checkServices(targets: List<com.lonnnnnng.flclash.core.ServiceTarget>, direct: Boolean, timeoutMs: Long): List<com.lonnnnnng.flclash.core.CoreServiceCheck> = error("核心未提供服务检测")
    suspend fun getLanConfig(path: String? = null): com.lonnnnnng.flclash.core.CoreLanConfig = error("核心未提供局域网状态")
    suspend fun updateAllowLan(value: Boolean) { error("核心未提供局域网设置") }
    suspend fun getMemoryStats(): com.lonnnnnng.flclash.core.CoreMemoryStats = error("核心未提供内存统计")
    suspend fun releaseMemory() { error("核心未提供内存回收") }

    suspend fun directOutboundIp(urls: List<String>, timeoutMs: Long): com.lonnnnnng.flclash.core.CoreIpResponse = outboundIp(urls, timeoutMs)
    suspend fun outboundIpForProxy(urls: List<String>, timeoutMs: Long, proxyName: String): com.lonnnnnng.flclash.core.CoreIpResponse = error("核心未提供节点出口查询")
    suspend fun directIpMetadata(url: String, timeoutMs: Long): com.lonnnnnng.flclash.core.CoreIpResponse = ipMetadata(url, timeoutMs)
    suspend fun outboundIp(urls: List<String>, timeoutMs: Long): com.lonnnnnng.flclash.core.CoreIpResponse =
        error("核心未提供出口查询")
    suspend fun ipMetadata(url: String, timeoutMs: Long): com.lonnnnnng.flclash.core.CoreIpResponse =
        error("核心未提供 IP 信息查询")
    suspend fun ipRouteStamp(): com.lonnnnnng.flclash.core.CoreRouteStamp = error("核心未提供路由版本")
    val events: Flow<CoreEvent>
    val eventFlowStats: StateFlow<CoreEventFlowStats>

    suspend fun initialize(homeDir: String, sdkVersion: Int)

    suspend fun validateConfig(path: String)

    suspend fun setupConfig(selectedMap: Map<String, String> = emptyMap())

    suspend fun getConfiguredMode(path: String): CoreOutboundMode

    suspend fun updateMode(mode: CoreOutboundMode)

    suspend fun getProxyGroups(): ProxyGroupsSnapshot

    suspend fun changeProxy(groupName: String, proxyName: String): Boolean

    suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): ProxyDelay?

    suspend fun probe(url: String, proxyName: String, timeoutMs: Long): CoreProbeResult?

    suspend fun getTraffic(): CoreTraffic

    suspend fun getTotalTraffic(): CoreTraffic

    suspend fun getConnectionCount(): Int

    suspend fun getConnections(): List<CoreConnection>

    suspend fun clearDnsCache(): Boolean

    fun startEventListener()

    fun stopEventListener()

    suspend fun startLog()

    suspend fun stopLog()

    fun startListener()

    fun stopListener()
}

class NativeCoreRuntimeBridge(
    private val coreBridge: CoreBridge,
) : CoreRuntimeBridge {
    override suspend fun checkServices(targets: List<com.lonnnnnng.flclash.core.ServiceTarget>, direct: Boolean, timeoutMs: Long) =
        coreBridge.checkServices(targets, direct, timeoutMs)
    override suspend fun checkRoutes(routes: List<List<com.lonnnnnng.flclash.core.CoreRouteHit>>) = coreBridge.checkRoutes(routes)
    override suspend fun getLanConfig(path: String?) = coreBridge.getLanConfig(path)
    override suspend fun updateAllowLan(value: Boolean) = coreBridge.updateAllowLan(value)
    override suspend fun externalProviders() = coreBridge.externalProviders()
    override suspend fun updateExternalProvider(name: String, stamp: com.lonnnnnng.flclash.core.CoreRouteStamp) = coreBridge.updateExternalProvider(name, stamp)
    override suspend fun providerCounts() = coreBridge.providerCounts()
    override suspend fun getMemoryStats() = coreBridge.getMemoryStats()
    override suspend fun releaseMemory() = coreBridge.releaseMemory()

    override suspend fun directOutboundIp(urls: List<String>, timeoutMs: Long) = coreBridge.outboundIp(urls, timeoutMs, direct = true)
    override suspend fun outboundIpForProxy(urls: List<String>, timeoutMs: Long, proxyName: String) = coreBridge.outboundIpForProxy(urls, timeoutMs, proxyName)
    override suspend fun directIpMetadata(url: String, timeoutMs: Long) = coreBridge.ipMetadata(url, timeoutMs, direct = true)
    override suspend fun outboundIp(urls: List<String>, timeoutMs: Long) = coreBridge.outboundIp(urls, timeoutMs)
    override suspend fun ipMetadata(url: String, timeoutMs: Long) = coreBridge.ipMetadata(url, timeoutMs)
    override suspend fun ipRouteStamp() = coreBridge.ipRouteStamp()
    override val events: Flow<CoreEvent>
        get() = coreBridge.events

    override val eventFlowStats: StateFlow<CoreEventFlowStats>
        get() = coreBridge.eventFlowStats

    override suspend fun initialize(homeDir: String, sdkVersion: Int) =
        coreBridge.initialize(homeDir, sdkVersion)

    override suspend fun validateConfig(path: String) = coreBridge.validateConfig(path)

    override suspend fun setupConfig(selectedMap: Map<String, String>) = coreBridge.setupConfig(selectedMap)

    override suspend fun getConfiguredMode(path: String): CoreOutboundMode = coreBridge.getConfiguredMode(path)

    override suspend fun updateMode(mode: CoreOutboundMode) = coreBridge.updateMode(mode)

    override suspend fun getProxyGroups(): ProxyGroupsSnapshot = coreBridge.getProxyGroups()

    override suspend fun changeProxy(groupName: String, proxyName: String): Boolean =
        coreBridge.changeProxy(groupName, proxyName)

    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): ProxyDelay? =
        coreBridge.testDelay(proxyName, testUrl, timeoutMs)

    override suspend fun probe(url: String, proxyName: String, timeoutMs: Long): CoreProbeResult? =
        coreBridge.probe(url, proxyName, timeoutMs)

    override suspend fun getTraffic(): CoreTraffic = coreBridge.getTraffic()

    override suspend fun getTotalTraffic(): CoreTraffic = coreBridge.getTotalTraffic()

    override suspend fun getConnectionCount(): Int = coreBridge.getConnectionCount()

    override suspend fun getConnections(): List<CoreConnection> = coreBridge.getConnections()

    override suspend fun clearDnsCache(): Boolean = coreBridge.clearDnsCache()

    override fun startEventListener() = coreBridge.startEventListener()

    override fun stopEventListener() = coreBridge.stopEventListener()

    override suspend fun startLog() = coreBridge.startLog()

    override suspend fun stopLog() = coreBridge.stopLog()

    override fun startListener() = coreBridge.startListener()

    override fun stopListener() = coreBridge.stopListener()
}
