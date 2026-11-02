package com.lonnnnnng.flclash.service

import android.content.Context
import com.lonnnnnng.flclash.core.CoreLanConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.StateFlow

data class LanSharingState(
    val config: CoreLanConfig = CoreLanConfig(false, 0),
    val hasConfig: Boolean = false,
)

interface LanSharingRuntime : CoreRuntimeStatusProvider {
    val lanSharing: StateFlow<LanSharingState>
    val lanRevision: StateFlow<Long>
    suspend fun refreshLanSharing()
    suspend fun updateLanSharing(allowLan: Boolean?)
}

interface LanSharingStore {
    fun read(): Boolean?
    fun write(value: Boolean?)
}
object NoopLanSharingStore : LanSharingStore {
    override fun read(): Boolean? = null
    override fun write(value: Boolean?) = Unit
}

class SharedPreferencesLanSharingStore(context: Context) : LanSharingStore {
    private val preferences = context.getSharedPreferences("kclash_runtime_preferences", Context.MODE_PRIVATE)
    override fun read(): Boolean? = if (preferences.contains("allow_lan")) preferences.getBoolean("allow_lan", false) else null
    override fun write(value: Boolean?) {
        val edit = preferences.edit()
        if (value == null) edit.remove("allow_lan") else edit.putBoolean("allow_lan", value)
        // 作者 long：先确认偏好落盘，再向页面确认成功，重建后不能丢失用户已确认的共享选择。
        check(edit.commit()) { "保存局域网设置失败" }
    }
}

internal fun patchLanSharing(yaml: String, allowLan: Boolean?): String {
    if (allowLan == null) return yaml
    val config = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
    // 作者 long：全局共享选择只覆盖暂存输入；原始订阅、脚本和未知字段继续保留。
    config.addProperty("allow-lan", allowLan)
    return YamlConfigCodec.toYaml(config.toString())
}
