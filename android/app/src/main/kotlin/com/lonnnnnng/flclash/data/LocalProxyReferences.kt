package com.lonnnnnng.flclash.data

import com.google.gson.*
import com.lonnnnnng.flclash.service.YamlConfigCodec

/** 作者 long：节点名也可能出现在密码、域名或路径里，改名只重写核心认识的目标字段。 */
object LocalProxyReferences {
    fun rename(source: String, old: String, next: String): String {
        val root = ProxyGroupDrafts.root(source)
        val groups = ProxyGroupDrafts.read(source)
        if (groups.any { it.name == old || it.name == next }) return source
        fun target(map: JsonObject, key: String) {
            map.get(key)?.takeIf { it.isJsonPrimitive && it.asString == old }?.let { map.addProperty(key, next) }
        }
        root.getAsJsonArray("proxy-groups")?.forEach { value ->
            val group = value.asJsonObject
            group.getAsJsonArray("proxies")?.let { names -> names.forEachIndexed { i, name -> if (name.asString == old) names.set(i, JsonPrimitive(next)) } }
            target(group, "default-selected"); target(group, "empty-fallback")
        }
        fun rules(values: JsonElement?) {
            if (values?.isJsonArray != true) return
            values.asJsonArray.forEachIndexed { index, entry ->
                if (!entry.isJsonPrimitive || !entry.asJsonPrimitive.isString) return@forEachIndexed
                val raw = entry.asString
                if (raw.startsWith("SUB-RULE,")) return@forEachIndexed
                val parsed = CustomRules.fields(raw)
                if (parsed.target == old) {
                    val parts = raw.split(',').toMutableList()
                    val targetIndex = if (parsed.type in setOf("AND", "OR", "NOT", "DOMAIN-REGEX", "PROCESS-NAME-REGEX", "PROCESS-PATH-REGEX")) parts.lastIndex else if (parsed.type == "MATCH") 1 else 2
                    parts[targetIndex] = next
                    values.asJsonArray.set(index, JsonPrimitive(parts.joinToString(",")))
                }
            }
        }
        rules(root.get("rules"))
        root.getAsJsonObject("sub-rules")?.entrySet()?.forEach { rules(it.value) }
        root.getAsJsonArray("proxies")?.forEach { target(it.asJsonObject, "dialer-proxy") }
        listOf("ntp", "dns").forEach { root.get(it)?.takeIf { it.isJsonObject }?.asJsonObject?.let { value -> target(value, "dialer-proxy") } }
        return YamlConfigCodec.toYaml(root.toString())
    }
    fun metadata(profile: Profile, old: String, next: String): Profile {
        val root = JsonParser.parseString(profile.overridesJson).asJsonObject
        fun rewrite(value: JsonElement?) {
            if (value?.isJsonObject != true) return
            value.asJsonObject.entrySet().toList().forEach { (key, field) ->
                if (key == "dialer-proxy" && field.isJsonPrimitive && field.asString == old) value.asJsonObject.addProperty(key, next)
                else rewrite(field)
            }
        }
        rewrite(root)
        root.getAsJsonObject("dialers")?.let { dialers ->
            val entries = dialers.entrySet().map { (key, value) -> (if (key == old) next else key) to (if (value.asString == old) next else value.asString) }
            entries.forEach { (key, value) -> dialers.addProperty(key, value) }
            if (old != next) dialers.remove(old)
        }
        return profile.copy(selectedMapJson = encodeSelectedMap(decodeSelectedMap(profile.selectedMapJson).mapValues { if (it.value == old) next else it.value }),
            matchTarget = if (profile.matchTarget == old) next else profile.matchTarget, overridesJson = root.toString())
    }
}

internal data class LocalProxyImport(val source: String, val before: List<LocalProxy>, val after: List<LocalProxy>, val dialers: Map<String, String>)

/** 作者 long：CUSTOM 导入把节点登记为全局资源；同一定义复用，重名的新定义只改自己的名称引用。 */
internal fun planLocalProxyImport(source: String, existing: List<LocalProxy>, groupNames: Set<String>): LocalProxyImport {
    val root = ProxyGroupDrafts.root(source)
    val values = root.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray ?: return LocalProxyImport(source, existing, existing, emptyMap())
    val rows = existing.toMutableList()
    val renames = linkedMapOf<String, String>()
    val dialers = linkedMapOf<String, String>()
    val originalNames = values.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.asString }.toSet()
    values.forEach { value ->
        require(value.isJsonObject) { "节点定义必须是对象" }
        val definition = value.asJsonObject.deepCopy()
        val original = definition.get("name")?.asString.orEmpty()
        require(original.isNotBlank()) { "导入节点缺少名称" }
        val dialer = definition.remove("dialer-proxy")?.asString
        val addition = planLocalProxyAddition(definition, rows, groupNames, originalNames)
        val row = addition.row
        if (addition.added) rows += row
        renames[original] = row.name
        if (!dialer.isNullOrBlank()) dialers[row.name] = dialer
    }
    var mapped = source
    renames.filter { it.key != it.value }.forEach { (old, next) -> mapped = LocalProxyReferences.rename(mapped, old, next) }
    val document = ProxyGroupDrafts.root(mapped).apply { remove("proxies") }
    return LocalProxyImport(YamlConfigCodec.toYaml(document.toString()), existing, rows,
        dialers.mapValues { renames[it.value] ?: it.value })
}
