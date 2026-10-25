package com.lonnnnnng.flclash.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.service.models.VpnOptions
import com.lonnnnnng.flclash.service.ServiceConfig
import com.lonnnnnng.flclash.service.VpnRuntimeState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

data class ActiveProfile(
    val id: Long,
    val label: String,
    val filePath: String,
)

interface ActiveProfileProvider {
    suspend fun activeProfile(): ActiveProfile?
}

interface ProxyServiceController {
    suspend fun start(options: VpnOptions)

    fun stop()
}

interface VpnOptionsProvider {
    suspend fun current(): VpnOptions
}

/**
 * 作者 long：在设置页面接入前先使用与 Flutter 默认值一致的手机 TUN 配置，
 * 同时关闭系统 HTTP 代理，避免订阅配置的 mixed-port 尚未解析时把流量送到错误端口。
 */
class DefaultVpnOptionsProvider : VpnOptionsProvider {
    override suspend fun current() = VpnOptions(
        enable = true,
        port = DEFAULT_MIXED_PORT,
        ipv6 = false,
        dnsHijacking = false,
        accessControlProps = AccessControlProps(
            enable = false,
            mode = AccessControlMode.ACCEPT_SELECTED,
            acceptList = emptyList(),
            rejectList = emptyList(),
        ),
        allowBypass = true,
        systemProxy = false,
        bypassDomain = emptyList(),
        stack = "mips",
        routeAddress = emptyList(),
    )

    private companion object {
        const val DEFAULT_MIXED_PORT = 7890
    }
}

class AndroidProxyServiceController(
    private val context: Context,
) : ProxyServiceController {
    override suspend fun start(options: VpnOptions) {
        check(options.enable) { "VPN 未启用" }
        ServiceConfig.updateVpnOptions(options)
        ServiceConfig.updateVpnState(VpnRuntimeState.STOPPED)
        ContextCompat.startForegroundService(
            context,
            Intent(context, ProxyService::class.java),
        )
        try {
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

    override fun stop() {
        context.stopService(Intent(context, ProxyService::class.java))
        context.stopService(Intent(context, VpnService::class.java))
    }

    private companion object {
        const val VPN_START_TIMEOUT_MS = 15_000L
    }
}

data class CoreRuntimePaths(
    val homeDir: File,
) {
    val configFile: File
        get() = File(homeDir, "config.yaml")
}

interface CoreRuntimeBridge {
    suspend fun initialize(homeDir: String, sdkVersion: Int)

    suspend fun validateConfig(path: String)

    suspend fun setupConfig()

    suspend fun getProxyGroups(): ProxyGroupsSnapshot

    suspend fun changeProxy(groupName: String, proxyName: String): Boolean

    suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): ProxyDelay?

    fun startListener()

    fun stopListener()
}

class NativeCoreRuntimeBridge(
    private val coreBridge: CoreBridge,
) : CoreRuntimeBridge {
    override suspend fun initialize(homeDir: String, sdkVersion: Int) =
        coreBridge.initialize(homeDir, sdkVersion)

    override suspend fun validateConfig(path: String) = coreBridge.validateConfig(path)

    override suspend fun setupConfig() = coreBridge.setupConfig()

    override suspend fun getProxyGroups(): ProxyGroupsSnapshot = coreBridge.getProxyGroups()

    override suspend fun changeProxy(groupName: String, proxyName: String): Boolean =
        coreBridge.changeProxy(groupName, proxyName)

    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): ProxyDelay? =
        coreBridge.testDelay(proxyName, testUrl, timeoutMs)

    override fun startListener() = coreBridge.startListener()

    override fun stopListener() = coreBridge.stopListener()
}
