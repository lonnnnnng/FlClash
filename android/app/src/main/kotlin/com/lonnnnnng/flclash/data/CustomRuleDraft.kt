package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.YamlConfigCodec
import java.util.Locale
import java.util.UUID

data class CustomRuleDraft(val key: String = UUID.randomUUID().toString(), val value: String)
data class CustomRuleFields(val type: String = "DOMAIN", val payload: String = "", val target: String = "DIRECT", val src: Boolean = false, val noResolve: Boolean = false) {
    fun raw(): String = buildList {
        add(type)
        if (type != "MATCH") add(payload.trim())
        add(target.trim())
        if (type in CustomRules.parameterTypes) {
            if (src) add("src")
            if (noResolve) add("no-resolve")
        }
    }.joinToString(",")
}

data class CustomRuleCatalog(val groups: List<String> = emptyList(), val proxies: List<String> = emptyList(), val providers: List<String> = emptyList()) {
    val basic get() = ProxyGroupDrafts.builtins.filter { it !in groups }
    val targets get() = (basic + groups + proxies).distinct()
    fun issue(value: String): String? = runCatching {
        CustomRules.validate(value)
        val fields = CustomRules.fields(value)
        when {
            fields.target !in targets -> "策略目标不存在：${fields.target}"
            fields.type == "RULE-SET" && fields.payload !in providers -> "规则资源不存在：${fields.payload}"
            else -> null
        }
    }.getOrElse { it.message ?: "规则无效" }
    companion object {
        fun fromSource(source: String): CustomRuleCatalog {
            val root = if (source.isBlank()) JsonObject() else JsonParser.parseString(YamlConfigCodec.toJson(source)).asJsonObject
            fun names(key: String) = root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
                it.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            }.orEmpty()
            return CustomRuleCatalog(names("proxy-groups"), names("proxies"), root.get("rule-providers")?.takeIf { it.isJsonObject }?.asJsonObject?.keySet()?.toList().orEmpty())
        }
    }
}

/** 作者 long：保留规则稳定标识，重复规则按出现顺序匹配；搜索、复制和重排不会把选择指向另一行。 */
object CustomRules {
    val types = listOf("DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-REGEX", "DOMAIN-WILDCARD", "GEOSITE",
        "IP-CIDR", "IP-CIDR6", "IP-SUFFIX", "IP-ASN", "GEOIP", "SRC-GEOIP", "SRC-IP-ASN", "SRC-IP-CIDR", "SRC-IP-SUFFIX",
        "DST-PORT", "SRC-PORT", "IN-PORT", "IN-TYPE", "IN-USER", "IN-NAME", "REMATCH-NAME", "PROCESS-PATH", "PROCESS-PATH-REGEX",
        "PROCESS-PATH-WILDCARD", "PROCESS-NAME", "PROCESS-NAME-REGEX", "PROCESS-NAME-WILDCARD", "UID", "NETWORK", "DSCP", "RULE-SET", "AND", "OR", "NOT", "MATCH")
    val parameterTypes = setOf("GEOIP", "IP-ASN", "IP-CIDR", "IP-CIDR6", "IP-SUFFIX", "RULE-SET")
    private val commaPayloadTypes = setOf("AND", "OR", "NOT", "SUB-RULE", "DOMAIN-REGEX", "PROCESS-NAME-REGEX", "PROCESS-PATH-REGEX")

    fun fields(value: String): CustomRuleFields {
        val parts = value.split(',').map(String::trim)
        val type = parts.first().uppercase(Locale.ROOT)
        val rest = parts.drop(1)
        return when {
            type == "MATCH" -> CustomRuleFields(type, target = rest.firstOrNull().orEmpty())
            type in commaPayloadTypes -> CustomRuleFields(type, rest.dropLast(1).joinToString(","), rest.lastOrNull().orEmpty())
            else -> CustomRuleFields(type, rest.getOrNull(0).orEmpty(), rest.getOrNull(1).orEmpty(), "src" in rest.drop(2), "no-resolve" in rest.drop(2))
        }
    }

    fun validate(value: String) {
        require(value.length <= 16_384 && '\n' !in value && '\r' !in value) { "每条规则须为单行且不能超过 16 KiB" }
        val rule = fields(value)
        require(rule.type != "SUB-RULE") { "不支持 SUB-RULE，请展开为具体规则" }
        require(rule.type in types) { "不支持的规则类型：${rule.type}" }
        require(rule.type == "MATCH" || rule.payload.isNotBlank()) { if (rule.type == "RULE-SET") "请选择规则资源" else "规则内容不能为空" }
        require(rule.target.isNotBlank()) { "请选择分流策略" }
        val parts = value.split(',').map(String::trim)
        if (rule.type == "MATCH") require(parts.size == 2) { "MATCH 只包含目标策略" }
        else if (rule.type !in commaPayloadTypes) {
            require(parts.size >= 3 && parts.drop(3).all { rule.type in parameterTypes && it in setOf("src", "no-resolve") }) { "规则附加参数无效" }
        }
        if (rule.type == "NETWORK") require(rule.payload.lowercase(Locale.ROOT) in setOf("tcp", "udp")) { "网络类型只能是 TCP 或 UDP" }
        if (rule.type in setOf("DST-PORT", "SRC-PORT", "IN-PORT", "UID", "DSCP")) {
            val segments = rule.payload.replace(',', '/').split('/')
            val bounds = segments.filter { it.isNotBlank() }.flatMap { segment ->
                val parts = segment.trim().split('-')
                require(parts.size <= 2) { "数字范围格式无效" }
                parts.map { part -> part.replace(Regex("[\\[\\] ]"), "").toLongOrNull()?.takeIf { it >= 0 } ?: throw IllegalArgumentException("数字范围格式无效") }
            }
            require(segments.size <= 28 && bounds.isNotEmpty()) { "数字范围格式无效" }
            if (rule.type == "DSCP") require(bounds.all { it <= 63 }) { "DSCP 必须在 0–63 之间" }
        }
    }

    fun validateAll(rules: List<CustomRuleDraft>, unchangedValues: Set<String> = emptySet()) {
        require(rules.size <= 10_000) { "规则不能超过 10000 条" }
        rules.forEachIndexed { index, rule ->
            // 作者 long：已有 SUB-RULE 或失效规则仍可保留、排序和删除，不能因未编辑的旧条目阻断其他规则修复。
            if (rule.value in unchangedValues) return@forEachIndexed
            try { validate(rule.value) } catch (failure: IllegalArgumentException) { throw IllegalArgumentException("第 ${index + 1} 行：${failure.message}") }
        }
    }

    fun scalar(value: String): String = if (value.contains(": ") || value.contains(" #") || value.endsWith(':') || value.startsWith("'") || value.startsWith('"')) "'${value.replace("'", "''")}'" else value
    fun editorText(rules: List<CustomRuleDraft>): String = rules.joinToString("\n") { scalar(it.value) }
    fun yaml(rules: List<CustomRuleDraft>): String = if (rules.isEmpty()) "[]\n" else rules.joinToString("\n", postfix = "\n") { "- ${scalar(it.value)}" }

    fun decode(text: String, previous: List<CustomRuleDraft> = emptyList(), validate: Boolean = true, dropSubRules: Boolean = false): List<CustomRuleDraft> {
        require(text.toByteArray().size <= 2 * 1024 * 1024) { "规则列表不能超过 2 MiB" }
        if (text.trim() in setOf("", "[]", "rules: []")) return emptyList()
        val lines = text.lines().mapIndexedNotNull { index, line ->
            val value = line.trim()
            if (value.isBlank() || value.startsWith('#') || value.matches(Regex("rules\\s*[:：]", RegexOption.IGNORE_CASE))) null
            else index + 1 to value.removePrefix("-").trimStart()
        }
        if (lines.isEmpty()) return emptyList()
        // 作者 long：单次 YAML 解码保留引号、正则中的冒号/井号；不能把每一行原样拼进源 YAML 造成结构注入。
        val document = "items:\n" + lines.joinToString("\n") { "  - ${it.second}" }
        val values = runCatching { JsonParser.parseString(YamlConfigCodec.toJson(document)).asJsonObject.getAsJsonArray("items") }
            .getOrElse { throw IllegalArgumentException("规则 YAML 格式无效，请检查列表和引号") }
        require(values.size() == lines.size) { "每行只能填写一条规则" }
        val unused = previous.groupBy { it.value }.mapValues { it.value.toMutableList() }
        return values.mapIndexedNotNull { index, entry ->
            require(entry.isJsonPrimitive && entry.asJsonPrimitive.isString) { "第 ${lines[index].first} 行须为规则文本" }
            val raw = entry.asString
            if (dropSubRules && raw.substringBefore(',').trim().equals("SUB-RULE", true)) return@mapIndexedNotNull null
            if (validate) try { CustomRules.validate(raw) } catch (failure: IllegalArgumentException) { throw IllegalArgumentException("第 ${lines[index].first} 行：${failure.message}") }
            unused[raw]?.removeFirstOrNull() ?: CustomRuleDraft(value = raw)
        }.also { require(it.size <= 10_000) { "规则不能超过 10000 条" } }
    }

    fun fromSource(source: String): List<String> {
        if (source.isBlank()) return emptyList()
        val values = JsonParser.parseString(YamlConfigCodec.toJson(source)).asJsonObject.get("rules") ?: return emptyList()
        if (values.isJsonNull) return emptyList()
        require(values.isJsonArray && values.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) { "rules 必须是规则文本列表" }
        return values.asJsonArray.map { it.asString }
    }

}
