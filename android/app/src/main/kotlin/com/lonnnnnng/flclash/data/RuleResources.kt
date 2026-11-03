package com.lonnnnnng.flclash.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lonnnnnng.flclash.service.YamlConfigCodec
import java.net.URI

internal const val RULE_RESOURCES_KEY = "global_rule_resources"
internal const val RESOURCE_PATH_PREFIX = "kclash-resource:"

data class RuleResource(
    val id: String,
    val label: String,
    val url: String = "",
    val fileName: String,
    val behavior: String,
    val format: String,
    val updatedAt: Long,
) {
    val editable: Boolean get() = url.isBlank() && format != "mrs"
    val compiled: Boolean get() = format != "mrs" && behavior != "classical"
    val coreFileName: String get() = fileName + if (compiled) ".mrs" else ""
    override fun toString() = "RuleResource(id=$id, format=$format)"
}

internal object RuleResources {
    fun decode(value: String?): List<RuleResource> {
        if (value == null) return emptyList()
        val type = object : TypeToken<List<RuleResource>>() {}.type
        return Gson().fromJson<List<RuleResource>>(value, type).also { validate(it) }
    }
    fun encode(rows: List<RuleResource>): String {
        validate(rows)
        return Gson().toJson(rows).also { require(it.toByteArray().size <= 1024 * 1024) { "资源目录超过 1 MiB" } }
    }
    fun validate(rows: List<RuleResource>) {
        require(rows.size <= 512 && rows.map { it.id }.distinct().size == rows.size && rows.map { it.label }.distinct().size == rows.size) { "资源重复或数量超过 512" }
        rows.forEach {
            require(it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && it.fileName.matches(Regex("[a-zA-Z0-9-]{1,80}\\.rules"))) { "资源标识无效" }
            require(it.label.isNotBlank() && it.label.length <= 128 && it.label.none { c -> c.isISOControl() || c == ',' }) { "资源名称为空或超过 128 字符" }
            require(it.behavior in setOf("domain", "ipcidr", "classical") && it.format in setOf("yaml", "text", "mrs")) { "规则资源格式无效" }
            if (it.url.isNotEmpty()) normalizeUrl(it.url)
        }
    }
    fun normalizeUrl(value: String): String = value.trim().also {
        val uri = runCatching { URI(it) }.getOrElse { error("请输入有效 HTTP/HTTPS 地址") }
        require(it.length <= 8192 && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "请输入有效 HTTP/HTTPS 地址" }
    }
    fun uniqueLabel(name: String, rows: List<RuleResource>): String {
        val base = name.trim().ifBlank { "规则资源" }.take(118)
        val held = rows.map { it.label }.toSet()
        if (base !in held) return base
        return generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { it !in held }
    }
    fun document(content: String): JsonObject = JsonParser.parseString(YamlConfigCodec.toJson(content)).asJsonObject
    fun reference(resource: RuleResource) = JsonObject().apply {
        addProperty("type", "file"); addProperty("path", RESOURCE_PATH_PREFIX + resource.id)
        addProperty("behavior", resource.behavior); addProperty("format", resource.format)
    }
    fun idOf(definition: JsonElement?): String? = definition?.takeIf { it.isJsonObject }?.asJsonObject?.get("path")
        ?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.startsWith(RESOURCE_PATH_PREFIX) }?.removePrefix(RESOURCE_PATH_PREFIX)

    fun bind(section: String, resource: RuleResource, alias: String? = null): String {
        val root = document("rule-providers:\n" + section.lines().joinToString("\n") { "  $it" } + "\n")
        val providers = root.getAsJsonObject("rule-providers") ?: JsonObject()
        val name = alias ?: resource.label
        val existing = providers.get(name)
        require(alias != null || existing == null || idOf(existing) == resource.id) { "配置内已有同名资源，请先调整名称" }
        providers.add(name, reference(resource))
        return YamlConfigCodec.toYaml(providers.toString()).trimEnd()
    }

    /** 作者 long：只改规则集语义位置，不能用全文替换误伤域名、节点名或 URL。 */
    fun renameRuleReferences(document: JsonObject, names: Map<String, String>) {
        fun renameList(value: String) = value.split(',').joinToString(",") { names[it.trim()] ?: it }
        fun walk(value: JsonElement): JsonElement = when {
            value.isJsonObject -> JsonObject().apply { value.asJsonObject.entrySet().forEach { (key, child) ->
                add(if (key.startsWith("rule-set:")) "rule-set:" + renameList(key.removePrefix("rule-set:")) else key, walk(child))
            } }
            value.isJsonArray -> JsonArray().apply { value.asJsonArray.forEach { add(walk(it)) } }
            value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.startsWith("rule-set:") ->
                com.google.gson.JsonPrimitive("rule-set:" + renameList(value.asString.removePrefix("rule-set:")))
            else -> value.deepCopy()
        }
        document.get("rules")?.takeIf { it.isJsonArray }?.let { rules ->
            document.add("rules", JsonArray().apply { rules.asJsonArray.forEach { rule ->
                require(rule.isJsonPrimitive && rule.asJsonPrimitive.isString) { "规则格式无效" }
                add(Regex("(?i)(^|\\()(RULE-SET,)([^,()]+)").replace(rule.asString) { match ->
                    match.groupValues[1] + match.groupValues[2] + (names[match.groupValues[3].trim()] ?: match.groupValues[3])
                })
            } })
        }
        listOf("dns", "sniffer").forEach { key -> document.get(key)?.let { document.add(key, walk(it)) } }
    }
}
