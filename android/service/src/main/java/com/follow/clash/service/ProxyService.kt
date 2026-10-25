package com.lonnnnnng.flclash.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.core.Core
import com.lonnnnnng.flclash.service.modules.ServiceModules

class ProxyService : Service(), ManagedService {
    private val modules = ServiceModules(this)
    private val binder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stop()
            return START_NOT_STICKY
        }
        return runCatching {
            // 作者 long：startForegroundService() 只负责拉起进程，必须在这里进入模块启动链并及时发布前台通知。
            start()
            START_STICKY
        }.getOrElse { error ->
            GlobalState.log("Proxy service start failed: $error")
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        try {
            cleanup()
        } finally {
            super.onDestroy()
        }
    }

    override fun onLowMemory() {
        Core.forceGC()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        trimCoreMemory(level)
        super.onTrimMemory(level)
    }

    inner class LocalBinder : Binder() {
        val service: ProxyService
            get() = this@ProxyService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun start() {
        try {
            modules.start()
        } catch (error: Exception) {
            stop()
            throw error
        }
    }

    override fun stop() {
        try {
            cleanup()
        } finally {
            stopSelf()
        }
    }

    private fun cleanup() = modules.stop()
}
