package com.lonnnnnng.flclash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lonnnnnng.flclash.common.BroadcastAction
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.common.action
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 作者 long：系统撤销 VPN 后由显式应用内广播通知这里，统一交给 Runtime Manager 收口，
 * 不在接收器中直接启动或停止任何服务，避免形成第二套核心生命周期。
 */
@AndroidEntryPoint
class ServiceBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var runtimeManager: CoreRuntimeManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != BroadcastAction.VPN_REVOKED.action) return
        val pendingResult = goAsync()
        GlobalState.launch {
            try {
                runtimeManager.handleVpnRevoked()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
