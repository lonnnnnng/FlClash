package com.lonnnnnng.flclash.service

import android.content.Context
import com.lonnnnnng.flclash.service.models.VpnOptions
import com.lonnnnnng.flclash.service.models.effective

interface VpnSettingsStore {
    fun read(): Boolean
    fun write(enabled: Boolean)
}

object DefaultVpnSettingsStore : VpnSettingsStore {
    override fun read() = true
    override fun write(enabled: Boolean) = Unit
}

class SharedPreferencesVpnSettingsStore(context: Context) : VpnSettingsStore {
    private val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context.applicationContext, "vpn_settings")
    override fun read() = preferences.getBoolean("enabled", true)
    override fun write(enabled: Boolean) {
        // 作者 long：开关必须落盘成功后才更新页面，避免进程重建恢复成另一个接管模式。
        check(preferences.edit().putBoolean("enabled", enabled).commit()) { "无法保存 VPN 设置" }
    }
}

data class VpnSettingsState(
    val enabled: Boolean = true,
    val appliedEnabled: Boolean? = null,
    val appliedOptions: VpnOptions? = null,
    val desiredOptions: VpnOptions? = null,
) {
    val restartRequired: Boolean get() = appliedOptions?.effective()?.let { applied ->
        desiredOptions?.effective()?.let { desired -> applied != desired }
            ?: (appliedEnabled != null && appliedEnabled != enabled)
    } ?: (appliedEnabled != null && appliedEnabled != enabled)
}
