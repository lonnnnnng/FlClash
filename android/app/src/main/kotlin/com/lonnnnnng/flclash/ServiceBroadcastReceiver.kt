package com.lonnnnnng.flclash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lonnnnnng.flclash.common.BroadcastLease
import com.lonnnnnng.flclash.common.BroadcastAction
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.common.action
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 作者 long：系统启动或撤销VPN后由显式应用内广播通知这里，统一交给Runtime Manager收口，
 * 不在接收器中直接启动或停止任何服务，避免形成第二套核心生命周期。
 */
@AndroidEntryPoint
class ServiceBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var runtimeManager: CoreRuntimeManager

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != BroadcastAction.VPN_REVOKED.action && action != BroadcastAction.VPN_START_REQUESTED.action) return
        val pendingResult = goAsync()
        val lease = BroadcastLease { pendingResult.finish() }
        val handler = Handler(Looper.getMainLooper())
        // 作者 long：核心恢复可能包含文件与资源IO，广播九秒后归还系统；任务继续由应用作用域持有，不能重复finish。
        val timeout = Runnable { lease.release() }
        handler.postDelayed(timeout, 9_000L)
        GlobalState.launch {
            try {
                (context?.applicationContext as? KClashApplication)?.awaitRuntimeInitialization()
                when (action) {
                    BroadcastAction.VPN_START_REQUESTED.action -> runtimeManager.handleSystemVpnStart().getOrThrow()
                    BroadcastAction.VPN_REVOKED.action -> runtimeManager.handleVpnRevoked().getOrThrow()
                }
            } catch (_: Exception) {
                // 作者 long：错误原因已进入核心诊断，不在系统日志重复输出可能带订阅信息的异常。
                GlobalState.log("Unable to handle VPN service request")
            } finally {
                handler.removeCallbacks(timeout)
                lease.release()
            }
        }
    }
}
