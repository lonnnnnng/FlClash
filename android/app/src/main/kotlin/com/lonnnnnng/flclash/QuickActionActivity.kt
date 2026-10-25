package com.lonnnnnng.flclash

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.common.QuickAction
import com.lonnnnnng.flclash.common.action
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 作者 long：通知栏和系统快捷入口必须复用 Runtime Manager，避免后台入口绕过统一的 VPN、TUN 和恢复意图边界。
 */
@AndroidEntryPoint
class QuickActionActivity : ComponentActivity() {
    @Inject
    lateinit var runtimeManager: CoreRuntimeManager

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runRuntimeAction { runtimeManager.startProxy() }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            QuickAction.STOP.action -> runRuntimeAction { runtimeManager.stop() }
            QuickAction.START.action -> requestStart()
            QuickAction.TOGGLE.action -> {
                if (runtimeManager.state.value.isRunning) {
                    runRuntimeAction { runtimeManager.stop() }
                } else {
                    requestStart()
                }
            }
            else -> finish()
        }
    }

    private fun requestStart() {
        val consentIntent = runCatching { VpnService.prepare(this) }.getOrElse {
            finish()
            return
        }
        if (consentIntent == null) {
            runRuntimeAction { runtimeManager.startProxy() }
        } else {
            vpnConsentLauncher.launch(consentIntent)
        }
    }

    private fun runRuntimeAction(action: suspend () -> Result<Unit>) {
        lifecycleScope.launch {
            action().onFailure {
                // 作者 long：快捷入口没有可承载错误文案的页面，只记录无敏感内容的动作失败并立即关闭临时 Activity。
                GlobalState.log("Quick action failed")
            }
            finish()
        }
    }
}
