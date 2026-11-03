package com.lonnnnnng.flclash.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.ProxyProviderOverride

/** 作者 long：资源列表只消费配置的语义引用，未使用定义继续保留在正文中，可从快捷 YAML 查看。 */
internal data class ProviderCatalog(val proxies: List<String>, val rules: List<String>) {
    companion object {
        fun from(profile: Profile, source: String, proxySection: String, ruleSection: String): ProviderCatalog {
            val root = ProxyGroupDrafts.root(source)
            if (profile.type != ProfileType.CUSTOM) return ProviderCatalog(
                ProfileProxyResources.definitions(proxySection).keySet().toList(), ruleDefinitions(ruleSection).keySet().toList(),
            )
            val rules = linkedSetOf<String>()
            fun addRule(raw: String) { rules += referencedRuleSets(raw) }
            root.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { value ->
                if (value.isJsonPrimitive && value.asJsonPrimitive.isString) addRule(value.asString)
            }
            ProfileProviderOverrides.fromJsonString(profile.overridesJson).extension.rules.forEach { addRule(it.value) }
            fun visit(value: JsonElement) {
                fun addReference(text: String) { if (text.startsWith("rule-set:")) rules.addAll(text.removePrefix("rule-set:").split(',').map(String::trim).filter(String::isNotEmpty)) }
                when {
                    value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, child) -> addReference(key); visit(child) }
                    value.isJsonArray -> value.asJsonArray.forEach(::visit)
                    value.isJsonPrimitive && value.asJsonPrimitive.isString -> addReference(value.asString)
                }
            }
            listOf("dns", "sniffer").forEach { root.get(it)?.let(::visit) }
            return ProviderCatalog(ProfileProxyResources.referenced(root).toList(), rules.toList())
        }

        fun ruleDefinitions(section: String): JsonObject = RuleResources.document("rule-providers:\n" + section.lines().joinToString("\n") { "  $it" })
            .get("rule-providers")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

        private fun referencedRuleSets(raw: String): Set<String> {
            val result = linkedSetOf<String>()
            fun walk(value: String, depth: Int) {
                if (depth > 64) return
                var text = value.trim()
                if (text.startsWith('(')) {
                    var balance = 0
                    val end = text.indices.firstOrNull { index ->
                        when (text[index]) { '(' -> balance++; ')' -> balance-- }
                        balance == 0
                    }
                    if (end == text.lastIndex) { walk(text.substring(1, text.lastIndex), depth + 1); return }
                    runCatching { com.lonnnnnng.flclash.service.ExtensionRules.fields(text) }.getOrNull()?.forEach { walk(it, depth + 1) }
                    return
                }
                val type = text.substringBefore(',').trim().uppercase()
                if (type == "RULE-SET") text.substringAfter(',', "").substringBefore(',').trim().takeIf(String::isNotEmpty)?.let(result::add)
                else if (type in setOf("AND", "OR", "NOT")) {
                    runCatching { com.lonnnnnng.flclash.service.ExtensionRules.fields(text) }.getOrNull()?.getOrNull(1)?.let { walk(it, depth + 1) }
                }
            }
            walk(raw, 0)
            return result
        }
    }
}

internal enum class ProviderOption(val title: String, val section: String, val default: String) {
    URL("测速地址", "健康检查", CoreBridge.DEFAULT_TEST_URL),
    INTERVAL("测速间隔", "健康检查", "300"),
    TIMEOUT("超时", "健康检查", "5000"),
    LAZY("使用时测速", "健康检查", "true"),
    EXPECTED_STATUS("期望状态", "健康检查", ""),
    PREFIX("名称前缀", "名称", ""),
    SUFFIX("名称后缀", "名称", ""),
    UDP("UDP", "连接", "true"),
    SKIP_CERT("跳过证书校验", "连接", "true"),
    IP_VERSION("IP 版本", "连接", "dual");

    val boolean: Boolean get() = this in setOf(LAZY, UDP, SKIP_CERT)
    val number: Boolean get() = this in setOf(INTERVAL, TIMEOUT)
    fun value(model: ProxyProviderOverride): String? = when (this) {
        URL -> model.url; INTERVAL -> model.interval?.toString(); TIMEOUT -> model.timeout?.toString(); LAZY -> model.lazy?.toString()
        EXPECTED_STATUS -> model.expectedStatus; PREFIX -> model.additionalPrefix; SUFFIX -> model.additionalSuffix
        UDP -> model.udp?.toString(); SKIP_CERT -> model.skipCertVerify?.toString(); IP_VERSION -> model.ipVersion
    }
    fun apply(model: ProxyProviderOverride, value: String?): ProxyProviderOverride = when (this) {
        URL -> model.copy(url = value); INTERVAL -> model.copy(interval = value?.toIntOrNull()); TIMEOUT -> model.copy(timeout = value?.toIntOrNull())
        LAZY -> model.copy(lazy = value?.toBooleanStrictOrNull()); EXPECTED_STATUS -> model.copy(expectedStatus = value)
        PREFIX -> model.copy(additionalPrefix = value); SUFFIX -> model.copy(additionalSuffix = value)
        UDP -> model.copy(udp = value?.toBooleanStrictOrNull()); SKIP_CERT -> model.copy(skipCertVerify = value?.toBooleanStrictOrNull())
        IP_VERSION -> model.copy(ipVersion = value)
    }
}

internal enum class ProviderFilterKind(val layout: String, val title: String) { FILTER("filter", "过滤"), EXCLUDE("excludeFilter", "排除过滤") }
internal data class ProviderFilterMember(val kind: ProviderFilterKind, val value: String) { val key: String get() = kind.layout + ":" + value }

internal object ProviderFilters {
    fun members(model: ProxyProviderOverride): List<ProviderFilterMember> {
        val runs = linkedMapOf(
            ProviderFilterKind.FILTER to FilterPresets.parts(model.filter.orEmpty()).distinct().iterator(),
            ProviderFilterKind.EXCLUDE to FilterPresets.parts(model.excludeFilter.orEmpty()).distinct().iterator(),
        )
        return buildList {
            model.memberLayout.orEmpty().forEach { key ->
                val kind = ProviderFilterKind.entries.firstOrNull { it.layout == key } ?: return@forEach
                val run = runs.getValue(kind)
                if (run.hasNext()) add(ProviderFilterMember(kind, run.next()))
            }
            runs.forEach { (kind, run) -> while (run.hasNext()) add(ProviderFilterMember(kind, run.next())) }
        }
    }

    fun replace(model: ProxyProviderOverride, members: List<ProviderFilterMember>): ProxyProviderOverride {
        val rows = members.distinct()
        val filters = rows.filter { it.kind == ProviderFilterKind.FILTER }.map { it.value }
        val excludes = rows.filter { it.kind == ProviderFilterKind.EXCLUDE }.map { it.value }
        // 作者 long：没有编辑过的 null 继续继承源值；删除最后一条已有条件才写入显式空串，不能重新启用源过滤。
        var updated = model.copy(
            filter = if (FilterPresets.parts(model.filter.orEmpty()) == filters) model.filter else filters.joinToString("`"),
            excludeFilter = if (FilterPresets.parts(model.excludeFilter.orEmpty()) == excludes) model.excludeFilter else excludes.joinToString("`"),
        )
        var end = rows.size
        while (end > 0 && (end == rows.size || rows[end - 1].kind.ordinal <= rows[end].kind.ordinal)) end--
        updated = updated.copy(memberLayout = rows.take(end).map { it.kind.layout }.takeIf { it.isNotEmpty() })
        return updated
    }

    fun append(model: ProxyProviderOverride, kind: ProviderFilterKind, regex: String): ProxyProviderOverride =
        replace(model, members(model) + FilterPresets.parts(regex).map { ProviderFilterMember(kind, it) })
}
