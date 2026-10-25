package com.lonnnnnng.flclash.service

import android.app.Service
import android.content.ComponentCallbacks2
import com.lonnnnnng.flclash.common.BroadcastAction
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.common.sendBroadcast
import com.lonnnnnng.flclash.core.Core

/**
 * 作者 long：系统会继续绑定 VpnService 直到 VPN 被撤销，单独调用 stopService() 不一定触发
 * onDestroy；显式停止 action 让服务在绑定仍存在时也能先释放 TUN、监听器和通知。
 */
internal const val ACTION_STOP_SERVICE = "com.lonnnnnng.flclash.action.STOP_SERVICE"

interface ManagedService {
    fun start()

    fun stop()
}

internal fun Service.notifyVpnStartRequested() {
    GlobalState.log("VPN start requested")
    BroadcastAction.VPN_START_REQUESTED.sendBroadcast()
}

internal fun Service.notifyVpnRevoked() {
    GlobalState.log("VPN permission revoked")
    BroadcastAction.VPN_REVOKED.sendBroadcast()
}

// UI_HIDDEN reaches the services too, since they share the activity's process,
// and Android 14 stopped delivering the running levels this answers to.
@Suppress("DEPRECATION")
internal fun trimCoreMemory(level: Int) {
    if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
    ) {
        Core.forceGC()
    }
}
