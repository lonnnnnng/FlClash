package com.lonnnnnng.flclash.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lonnnnnng.flclash.core.Core
import com.lonnnnnng.flclash.service.models.NotificationParams
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val context: Context,
) {
    private val transitionLock = Mutex()
    private val listenerStarted = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(CoreRuntimeStatus.STOPPED)

    val state = mutableState.asStateFlow()

    suspend fun startProxy(): Result<Unit> = transitionLock.withLock {
        if (mutableState.value == CoreRuntimeStatus.RUNNING) {
            return@withLock Result.success(Unit)
        }
        mutableState.value = CoreRuntimeStatus.STARTING
        runCatching {
            ServiceConfig.updateNotificationParams(
                NotificationParams(title = "KClash", showStopAction = false),
            )
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProxyService::class.java),
            )
            if (listenerStarted.compareAndSet(false, true)) {
                Core.startListener()
            }
            mutableState.value = CoreRuntimeStatus.RUNNING
        }.onFailure {
            mutableState.value = CoreRuntimeStatus.FAILED
        }
    }

    suspend fun stop(): Result<Unit> = transitionLock.withLock {
        if (mutableState.value == CoreRuntimeStatus.STOPPED) {
            return@withLock Result.success(Unit)
        }
        mutableState.value = CoreRuntimeStatus.STOPPING
        runCatching {
            context.stopService(Intent(context, ProxyService::class.java))
            context.stopService(Intent(context, VpnService::class.java))
            if (listenerStarted.compareAndSet(true, false)) {
                Core.stopListener()
            }
            mutableState.value = CoreRuntimeStatus.STOPPED
        }.onFailure {
            mutableState.value = CoreRuntimeStatus.FAILED
        }
    }
}
