package com.lonnnnnng.flclash.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.LocalProxy
import com.lonnnnnng.flclash.data.LocalProxies
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.YamlConfigCodec

data class CustomProfileGroupSummary(val name: String, val type: String, val members: Int, val resources: Int)

data class CustomProfileSummary(
    val rules: Int = 0,
    val referencedProviders: Int = 0,
    val missingProviders: Int = 0,
    val proxies: Int = 0,
    val groups: List<CustomProfileGroupSummary> = emptyList(),
    val dnsEnabled: Boolean = false,
    val dialers: Int = 0,
    val ntpEnabled: Boolean = false,
    val snifferEnabled: Boolean = false,
    val unreadable: Boolean = false,
) {
    val moreInEffect: Int get() = listOf(dialers > 0, ntpEnabled, snifferEnabled).count { it }
}

/** 作者 long：概览统计源配置和显式覆盖，不执行脚本或启动核心，避免打开编辑页就改变运行状态。 */
internal fun customProfileSummary(content: String, profile: Profile, resources: List<Profile>? = null, localProxies: List<LocalProxy> = emptyList()): CustomProfileSummary = runCatching {
    // 作者 long：导入后的源正文只保存名称引用，概览必须与运行输入使用同一投影，不能把有效的全局节点统计为零。
    val projected = if (content.isBlank()) content else LocalProxies.project(content, localProxies)
    val root = if (projected.isBlank()) JsonObject() else JsonParser.parseString(YamlConfigCodec.toJson(projected)).asJsonObject
    fun JsonObject.objects(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
        .filter { it.isJsonObject }.map { it.asJsonObject }
    fun JsonObject.strings(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
        .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }.map { it.asString }
    fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
    fun enabled(key: String) = root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("enable")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true
    fun names(key: String) = root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.keySet().orEmpty()
    val groups = root.objects("proxy-groups")
    val proxies = root.objects("proxies")
    val rules = root.strings("rules")
    val proxyResources = groups.flatMap { it.strings("use") }.toSet()
    val missingProxyResources = proxyResources.count { name ->
        val definition = root.get("proxy-providers")?.takeIf { it.isJsonObject }?.asJsonObject?.get(name)
        val id = com.lonnnnnng.flclash.data.ProfileProxyResources.idOf(definition)
        definition == null || resources != null && com.lonnnnnng.flclash.data.ProfileProxyResources.isReference(definition) &&
            resources.none { it.id == id && it.type != com.lonnnnnng.flclash.data.ProfileType.CUSTOM }
    }
    val ruleResources = rules.mapNotNull { rule ->
        val parts = rule.split(',').map(String::trim)
        parts.getOrNull(1)?.takeIf { parts.first().equals("RULE-SET", ignoreCase = true) }
    }.toSet()
    val overrides = ProfileProviderOverrides.fromJsonString(profile.overridesJson)
    val sourceDialers = proxies.count { it.text("dialer-proxy").isNotBlank() }
    val proxyNames = proxies.map { it.text("name") }.toSet()
    // 作者 long：空 Dialers 覆盖表示清空源映射；只统计仍存在的代理，不能把失效键计成生效配置。
    val dialers = overrides.dialers?.keys?.count { it in proxyNames } ?: sourceDialers
    CustomProfileSummary(
        rules = rules.size, referencedProviders = proxyResources.size + ruleResources.size,
        missingProviders = missingProxyResources + (ruleResources - names("rule-providers")).size,
        proxies = proxies.size,
        groups = groups.map { CustomProfileGroupSummary(it.text("name"), it.text("type"), it.strings("proxies").size, it.strings("use").size) },
        dnsEnabled = overrides.dns.enable ?: enabled("dns"), dialers = dialers,
        ntpEnabled = overrides.ntp.enable ?: enabled("ntp"), snifferEnabled = overrides.sniffer.enable ?: enabled("sniffer"),
    )
}.getOrElse {
    // 作者 long：损坏配置仍可进入 YAML 修复，但不能把解析失败伪装成全部为零。
    CustomProfileSummary(unreadable = true)
}
