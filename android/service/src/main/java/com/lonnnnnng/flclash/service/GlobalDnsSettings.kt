package com.lonnnnnng.flclash.service

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 作者 long：全局页仅保存 Flutter 普通配置允许的十项覆盖；CUSTOM 继续使用自己的 DNS，移除字段恢复继承。 */
object GlobalDnsSettings {
    val keys = listOf("listen", "ipv6", "enhanced-mode", "fake-ip-filter", "fake-ip-filter-mode",
        "default-nameserver", "nameserver", "fallback", "proxy-server-nameserver", "direct-nameserver")
    val listKeys = setOf("fake-ip-filter", "default-nameserver", "nameserver", "fallback", "proxy-server-nameserver", "direct-nameserver")

    fun validate(value: ProfileDnsOverrides): ProfileDnsOverrides {
        val json = value.toJson()
        require(json.keySet().all { it in keys }) { "全局 DNS 包含不支持的字段" }
        require(value.listen == null || value.listen.length <= 256 && value.listen.none { it.isISOControl() }) { "监听地址格式无效" }
        require(value.enhancedMode == null || value.enhancedMode in listOf("normal", "fake-ip", "redir-host")) { "DNS 模式无效" }
        require(value.fakeIpFilterMode == null || value.fakeIpFilterMode in listOf("blacklist", "whitelist", "rule")) { "Fake-IP 过滤模式无效" }
        listKeys.forEach { key -> json.getAsJsonArray(key)?.let { list ->
            require(list.size() <= 4096) { "列表最多 4096 项" }
            require(list.all { it.asString.isNotBlank() && it.asString.length <= 4096 && it.asString.none(Char::isISOControl) }) { "列表每行填写一个有效值" }
        } }
        return value
    }

    fun apply(yaml: String, value: ProfileDnsOverrides): String {
        if (value.isEmpty) return yaml
        val root = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
        val dns = root.get("dns")?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()
        validate(value).toJson().entrySet().forEach { (key, item) -> dns.add(key, item.deepCopy()) }
        // 作者 long：先把行内/引号 DNS 合并成结构化分区，避免把缩进字段追加到 flow map 后；其他分区仍保留原文。
        val header = Regex("(?m)^(?:dns|\"dns\"|'dns')\\s*:[^\\r\\n]*").find(yaml)
        if (yaml.trimStart().startsWith("{")) { root.add("dns", dns); return YamlConfigCodec.toYaml(root.toString()) }
        val section = YamlConfigCodec.toYaml(JsonObject().apply { add("dns", dns) }.toString())
        if (header == null) return yaml.trimEnd() + "\n" + section
        val tail = yaml.indexOf('\n', header.range.last).let { if (it < 0) yaml.length else it + 1 }
        val end = Regex("(?m)^[^ \t#\r\n][^\r\n]*:").find(yaml, tail)?.range?.first ?: yaml.length
        return yaml.substring(0, header.range.first) + section + yaml.substring(end)
    }
}

interface GlobalDnsSettingsStore {
    fun read(): ProfileDnsOverrides
    fun write(value: ProfileDnsOverrides)
}

class SharedPreferencesGlobalDnsSettingsStore(context: Context) : GlobalDnsSettingsStore {
    private val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context.applicationContext, "global_dns")
    override fun read(): ProfileDnsOverrides = preferences.getString("overrides", null)?.let {
        GlobalDnsSettings.validate(ProfileDnsOverrides.fromJson(JsonParser.parseString(it).asJsonObject))
    } ?: ProfileDnsOverrides()
    override fun write(value: ProfileDnsOverrides) {
        check(preferences.edit().putString("overrides", GlobalDnsSettings.validate(value).toJson().toString()).commit()) { "无法保存 DNS 设置" }
    }
}
