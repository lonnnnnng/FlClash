package com.lonnnnnng.flclash.service

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 作者 long：SSID按上游精确匹配，空格和大小写都属于名称，不能trim后误暂停另一张Wi-Fi。 */
object OnDemandSettings {
    fun validate(ssids: List<String>): List<String> {
        require(ssids.size <= 1024 && ssids.distinct().size == ssids.size) { "SSID 列表重复或超过数量限制" }
        require(ssids.all { it.isNotEmpty() && it.codePointCount(0, it.length) <= 32 && it.none { char -> char == '\u0000' || char == '\n' || char == '\r' } }) { "SSID 不能为空，最多 32 个字符" }
        return ssids.toList()
    }
    fun matches(ssids: List<String>, current: String?): Boolean = current != null && current in ssids
    fun normalizeSsid(raw: String?): String? = raw?.removeSurrounding("\"")?.takeUnless { it.isEmpty() || it == "<unknown ssid>" || it == "0x" }
}

interface OnDemandSettingsStore {
    fun read(): List<String>
    fun write(value: List<String>)
    fun compareAndSet(expected: List<String>, value: List<String>) = synchronized(this) {
        check(read() == expected) { "SSID 列表已变化，请重新打开后编辑" }
        write(value)
    }
    val changes: StateFlow<List<String>>? get() = null
}

class SharedPreferencesOnDemandSettingsStore(context: Context) : OnDemandSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences("on_demand", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    override val changes = mutable.asStateFlow()
    @Synchronized override fun read(): List<String> = preferences.getString("exclude_ssids", null)?.let { raw ->
        val array = JsonParser.parseString(raw).asJsonArray
        require(array.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) { "SSID 存储格式无效" }
        OnDemandSettings.validate(array.map { it.asString })
    } ?: emptyList()
    @Synchronized override fun write(value: List<String>) {
        val next = OnDemandSettings.validate(value)
        check(preferences.edit().putString("exclude_ssids", JsonArray().apply { next.forEach(::add) }.toString()).commit()) { "无法保存 SSID 列表" }
        mutable.value = next
    }
}
