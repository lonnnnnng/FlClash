package com.lonnnnnng.flclash.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.common.AccessSortType
import com.lonnnnnng.flclash.service.AccessControlSettingsProvider
import com.lonnnnnng.flclash.service.models.AccessControlProps
import javax.inject.Inject

/**
 * 作者 long：按应用代理属于 KClash 自己的全局设置，和某一份订阅配置无关，
 * 因此沿用 app_preferences 键值表持久化，避免把包名列表写入用户 YAML。
 */
class RoomAccessControlSettingsStore @Inject constructor(
    private val preferences: AppPreferenceDao,
) : AccessControlSettingsProvider {
    override suspend fun current(): AccessControlProps {
        // 作者 long：只有从未保存过才使用默认值；读取/解码失败必须拒绝编辑和启动，不能把旧名单静默当成空名单覆盖。
        val raw = preferences.get(KEY) ?: return DEFAULT
        return decode(raw).sanitize()
    }

    override suspend fun save(value: AccessControlProps) {
        preferences.put(
            AppPreferenceEntity(
                key = KEY,
                value = Gson().toJson(value.sanitize()),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun AccessControlProps.sanitize(): AccessControlProps = copy(
        acceptList = acceptList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
        rejectList = rejectList.orEmpty().filter(String::isNotBlank).distinct().sorted(),
    )

    private fun decode(raw: String): AccessControlProps {
        // 作者 long：给旧版四字段 JSON 补上新增筛选字段后再交给 Gson，
        // 这样缺失字段使用产品默认值，同时保留新版本显式关闭的筛选开关。
        val json = JsonParser.parseString(raw).asJsonObject
        if (!json.has("mode")) json.addProperty("mode", "rejectSelected")
        if (!json.has("sort")) json.addProperty("sort", AccessSortType.NONE.name)
        if (!json.has("isFilterSystemApp")) json.addProperty("isFilterSystemApp", true)
        if (!json.has("isFilterNonInternetApp")) json.addProperty("isFilterNonInternetApp", true)
        // 作者 long：字段缺省可沿用已有默认值，但显式null、错误类型和未知枚举表示损坏，不能静默转成空名单。
        for (key in listOf("acceptList", "rejectList")) {
            val value = json.get(key)
            require(value != null && value.isJsonArray && value.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) { "按应用代理名单损坏" }
        }
        for (key in listOf("enable", "isFilterSystemApp", "isFilterNonInternetApp")) {
            val value = json.get(key)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "按应用代理开关损坏" }
        }
        require(json["mode"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString in setOf("acceptSelected", "rejectSelected")) { "按应用代理模式损坏" }
        require(json["sort"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString in AccessSortType.entries.map { it.name }) { "按应用代理排序损坏" }
        return Gson().fromJson(json, AccessControlProps::class.java)
    }

    private companion object {
        const val KEY = "vpn_access_control"
        val DEFAULT = AccessControlProps(
            enable = false,
            mode = AccessControlMode.REJECT_SELECTED,
            acceptList = emptyList(),
            rejectList = emptyList(),
        )
    }
}
