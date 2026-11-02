package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.YamlConfigCodec

data class CustomProfileImportContent(val content: String, val droppedSubRules: Int)

/** 作者 long：自定义导入沿用 Flutter 的分区范围，不把远程控制端口、系统路由等全局设置带进自定义页面。 */
internal fun customProfileImportContent(bytes: ByteArray): CustomProfileImportContent {
    require(bytes.isNotEmpty() && bytes.size <= MAX_PROFILE_FILE_BYTES) { "配置为空或超过 8 MiB" }
    val document = try { JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))).asJsonObject }
        catch (_: Exception) { throw IllegalArgumentException("无法读取 YAML 配置，请检查文件内容") }
    val fields = listOf("proxies", "proxy-groups", "proxy-providers", "rule-providers", "rules", "dns", "ntp", "sniffer")
    // 作者 long：Codec 自动补空 proxy-providers，不能让这项默认补值把任意对象误判为可导入配置。
    val hasContent = fields.any { key -> document.get(key)?.let { !it.isJsonNull && when {
        it.isJsonObject -> it.asJsonObject.size() > 0
        it.isJsonArray -> true
        else -> false
    } } == true }
    require(hasContent) { "配置中没有可导入的代理、策略组、规则或网络设置" }
    for (key in fields) document.get(key)?.let {
        require(it.isJsonNull || if (key in listOf("proxies", "proxy-groups", "rules")) it.isJsonArray else it.isJsonObject) { "配置分区格式无效：" + key }
    }
    // 作者 long：沿用 Flutter 移除 SUB-RULE 并报告数量，不能保存指向未导入子规则表的失效引用。
    val rules = document.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
    val kept = rules?.filterNot {
        it.isJsonPrimitive && it.asString.trimStart().startsWith("SUB-RULE,", ignoreCase = true)
    }
    val dropped = (rules?.size() ?: 0) - (kept?.size ?: 0)
    if (dropped > 0) document.add("rules", com.google.gson.JsonArray().apply { kept?.forEach { add(it) } })
    val imported = JsonObject()
    fields.forEach { field -> document.get(field)?.takeUnless { it.isJsonNull }?.let { imported.add(field, it.deepCopy()) } }
    return CustomProfileImportContent(YamlConfigCodec.toYaml(imported.toString()), dropped)
}
