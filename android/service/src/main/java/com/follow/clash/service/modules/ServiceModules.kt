package com.lonnnnnng.flclash.service.modules

import android.app.Service
import com.lonnnnnng.flclash.service.NetworkObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal interface ServiceModule {
    fun start()

    fun stop()
}

internal class ServiceModules(private val service: Service) {
    private var scope: CoroutineScope? = null
    private var modules = emptyList<ServiceModule>()

    @Synchronized
    fun start() {
        if (scope != null) return

        val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val nextModules = listOf(
            NetworkObserverModule,
            NotificationModule(service, nextScope),
        )
        val startedModules = mutableListOf<ServiceModule>()

        try {
            nextModules.forEach { module ->
                module.start()
                startedModules.add(module)
            }
            scope = nextScope
            modules = nextModules
        } catch (error: Throwable) {
            nextScope.cancel()
            startedModules.asReversed().forEach { module ->
                runCatching { module.stop() }
            }
            throw error
        }
    }

    @Synchronized
    fun stop() {
        val currentScope = scope ?: return
        val currentModules = modules
        scope = null
        modules = emptyList()

        currentScope.cancel()
        currentModules.asReversed().forEach { module ->
            runCatching { module.stop() }
        }
    }
}

/**
 * 作者 long：网络观察必须和前台服务同生命周期，才能把当前底层网络的 DNS
 * 及时交给 Mihomo，并在停止时释放 ConnectivityManager 回调。
 */
private object NetworkObserverModule : ServiceModule {
    override fun start() = NetworkObserver.start()

    override fun stop() = NetworkObserver.stop()
}
