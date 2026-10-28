package com.lonnnnnng.flclash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.lonnnnnng.flclash.common.ExternalActionContract

/**
 * 作者 long：Tasker/MacroDroid 需要一个公开的 Action 合约，但核心生命周期仍由内部入口统一处理。
 * 这里只接受三个固定 action，并拒绝 data/extras，避免把外部 Intent 当成可扩展命令通道。
 */
class ExternalActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingIntent = intent ?: run {
            finish()
            return
        }
        val action = incomingIntent.action
        if (!ExternalActionContract.accepts(
                action = action,
                hasData = incomingIntent.data != null,
                hasExtras = incomingIntent.extras?.isEmpty == false,
            )
        ) {
            finish()
            return
        }
        // 作者 long：外部入口只负责做协议校验，实际 VPN 授权、启停和失败回滚继续走内部 Activity。
        startActivity(
            Intent(this, QuickActionActivity::class.java).setAction(action),
        )
        finish()
    }
}
