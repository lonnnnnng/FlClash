package com.lonnnnnng.flclash.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import java.io.File

data class ActiveProfile(
    val id: Long,
    val label: String,
    val filePath: String,
)

interface ActiveProfileProvider {
    suspend fun activeProfile(): ActiveProfile?
}

interface ProxyServiceController {
    fun start()

    fun stop()
}

class AndroidProxyServiceController(
    private val context: Context,
) : ProxyServiceController {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ProxyService::class.java),
        )
    }

    override fun stop() {
        context.stopService(Intent(context, ProxyService::class.java))
        context.stopService(Intent(context, VpnService::class.java))
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
