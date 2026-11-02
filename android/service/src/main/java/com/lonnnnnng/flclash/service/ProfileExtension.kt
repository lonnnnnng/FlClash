package com.lonnnnnng.flclash.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** 作者 long：规则用稳定 ID 标识，排序和编辑不会让各配置的全局禁用记录指向另一条规则。 */
data class ExtensionRule(val id: String, val value: String)

data class ProfileExtensionOverrides(
    val configured: Boolean = false,
    val rules: List<ExtensionRule> = emptyList(),
    val disabledGlobalRuleIds: Set<String> = emptySet(),
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("configured", configured)
        add("rules", ExtensionRules.toJson(rules))
        add("disabled-global-rules", JsonArray().apply { disabledGlobalRuleIds.forEach(::add) })
    }

    companion object {
        fun fromJson(json: JsonObject): ProfileExtensionOverrides = ProfileExtensionOverrides(
            configured = json.get("configured")?.asBoolean ?: false,
            rules = ExtensionRules.fromJson(json.getAsJsonArray("rules") ?: JsonArray()),
            disabledGlobalRuleIds = json.getAsJsonArray("disabled-global-rules")?.map { it.asString }?.toSet().orEmpty(),
        )
    }
}

data class StandardProfileExtension(val rules: List<ExtensionRule>, val matchTarget: String? = null)

object ExtensionRules {
    const val PREFERENCE_KEY = "global_extension_rules"

    fun toJson(rules: List<ExtensionRule>): JsonArray = JsonArray().apply {
        rules.forEach { rule -> add(JsonObject().apply { addProperty("id", rule.id); addProperty("value", rule.value) }) }
    }
    fun decode(raw: String?): List<ExtensionRule> = if (raw.isNullOrBlank()) emptyList() else fromJson(JsonParser.parseString(raw).asJsonArray)
    fun fromJson(array: JsonArray): List<ExtensionRule> = array.map { element ->
        element.asJsonObject.let { ExtensionRule(it.get("id").asString, it.get("value").asString) }
    }.also(::validate)

    fun validate(rules: List<ExtensionRule>) {
        require(rules.size <= 10_000) { "附加规则不能超过 10000 条" }
        require(rules.map { it.id }.toSet().size == rules.size && rules.all { it.id.isNotBlank() }) { "规则标识重复或为空" }
        rules.forEach { rule ->
            require(rule.value.length <= 16_384 && '\n' !in rule.value && '\r' !in rule.value) { "每条规则必须为单行且不超过 16 KiB" }
            val fields = fields(rule.value)
            require(fields.size >= 2 && fields.first().matches(Regex("[A-Z][A-Z0-9_-]*")) && fields.drop(1).all { it.isNotBlank() }) { "规则格式无效，请填写类型、内容和目标" }
            require(fields.first() != "SUB-RULE") { "附加规则不支持 SUB-RULE，请展开为具体规则" }
            require(fields.size >= if (fields.first() in setOf("MATCH", "FINAL")) 2 else 3) { "规则缺少匹配内容或目标" }
        }
    }

    // 作者 long：逻辑规则的括号内部也有逗号，只能按顶层逗号寻找目标，避免改坏 AND/OR/NOT 表达式。
    fun fields(value: String): List<String> {
        var depth = 0
        var start = 0
        return buildList {
            value.forEachIndexed { index, char ->
                when (char) {
                    '(' -> depth++
                    ')' -> { depth--; require(depth >= 0) { "规则括号不匹配" } }
                    ',' -> if (depth == 0) { add(value.substring(start, index).trim()); start = index + 1 }
                }
            }
            require(depth == 0) { "规则括号不匹配" }
            add(value.substring(start).trim())
        }
    }
}

object ProfileExtensionApplier {
    fun apply(yaml: String, extension: StandardProfileExtension?): String {
        if (extension == null || extension.rules.isEmpty()) return yaml
        ExtensionRules.validate(extension.rules)
        val root = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
        val original = root.getAsJsonArray("rules") ?: JsonArray()
        val target = extension.matchTarget?.trim()?.takeIf(String::isNotEmpty) ?: original.reversed().firstNotNullOfOrNull { value ->
            val fields = ExtensionRules.fields(value.asString)
            fields.getOrNull(1)?.takeIf { fields.first() == "MATCH" }
        }
        val rules = JsonArray()
        extension.rules.forEach { rule ->
            val fields = ExtensionRules.fields(rule.value).toMutableList()
            val targetIndex = if (fields.last().equals("no-resolve", true)) fields.lastIndex - 1 else fields.lastIndex
            if (target != null && fields[targetIndex].equals("MATCH", true)) fields[targetIndex] = target
            rules.add(JsonPrimitive(fields.joinToString(",")))
        }
        original.forEach(rules::add)
        root.add("rules", rules)
        return YamlConfigCodec.toYaml(root.toString())
    }

    fun targets(yaml: String): List<String> {
        val root = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
        return (listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "PASS-RULE", "COMPATIBLE", "GLOBAL") + listOf("proxy-groups", "proxies").flatMap { key ->
            root.getAsJsonArray(key)?.mapNotNull { it.asJsonObject.get("name")?.asString }.orEmpty()
        }).distinct()
    }
}
