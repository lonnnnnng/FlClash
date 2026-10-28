package com.lonnnnnng.flclash

import android.content.Context
import android.content.Intent
import android.provider.Settings

enum class AlwaysOnVpnState {
    DISABLED,
    KCLASH_ENABLED,
    OTHER_APP_ENABLED,
    UNKNOWN,
}

object AlwaysOnVpn {
    fun state(context: Context): AlwaysOnVpnState {
        // 作者 long：普通应用无法通过公开 SDK 稳定读取 OEM 的 Always-on/lockdown 勾选状态，
        // 因此只提供系统设置引导，不能把 VPN 已连接或进程存活误报成 Always-on 已启用。
        return AlwaysOnVpnState.UNKNOWN
    }

    fun openSettings(context: Context): Boolean = runCatching {
        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
    }.isSuccess
}
