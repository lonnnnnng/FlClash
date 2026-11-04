package com.lonnnnnng.flclash.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.service.YamlErrorLocation
import java.util.UUID

data class ProxyGroupDraft(
    val key: String = UUID.randomUUID().toString(),
    val originalName: String? = null,
    val json: String = "{\"name\":\"\",\"type\":\"select\"}",
) {
    val fields: JsonObject get() = JsonParser.parseString(json).asJsonObject
    val name: String get() = text("name")
    fun text(key: String): String = fields.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    fun strings(key: String): List<String> = fields.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive }?.asString }.orEmpty()
    fun flag(key: String): Boolean = fields.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true
    fun edit(change: (JsonObject) -> Unit): ProxyGroupDraft = copy(json = fields.apply(change).toString())
}

data class ProxyGroupMember(val name: String, val provider: Boolean = false)
data class ProxyGroupsSave(val groups: List<ProxyGroupDraft>, val providerProfiles: Map<String, Long> = emptyMap())

/** 作者 long：可视表单只修改认识的字段；原定义中的扩展字段始终随草稿保存，不能因切换类型丢失。 */
object ProxyGroupDrafts {
    val types = listOf("select", "url-test", "fallback", "load-balance")
    val builtins = listOf("DIRECT", "REJECT", "REJECT-DROP", "COMPATIBLE", "PASS", "PASS-RULE")

    fun root(content: String): JsonObject = if (content.isBlank()) JsonObject()
        else JsonParser.parseString(YamlConfigCodec.toJson(content)).asJsonObject

    fun read(content: String): List<ProxyGroupDraft> {
        val values = root(content).get("proxy-groups") ?: return emptyList()
        require(values.isJsonArray) { "策略组分区必须是列表" }
        return values.asJsonArray.map { element ->
            require(element.isJsonObject) { "策略组必须是对象" }
            val fields = element.asJsonObject
            ProxyGroupDraft(originalName = fields.get("name")?.asString, json = fields.toString())
        }
    }

    private fun editorHasRoot(text: String) = text.lineSequence().firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("#") }
        ?.trimStart()?.startsWith("proxy-groups:") == true

    private fun editorDocument(text: String) = if (editorHasRoot(text)) text
        else "proxy-groups:\n  " + text.replace(Regex("\\r\\n|\\r|\\n")) { "${it.value}  " }

    fun readEditorYaml(text: String): List<ProxyGroupDraft> = read(editorDocument(text)).also { groups ->
        val names = mutableSetOf<String>()
        groups.forEachIndexed { index, group ->
            require(group.name.isNotBlank()) { "第 ${index + 1} 个策略组缺少名称" }
            require(group.text("type") in types) { "第 ${index + 1} 个策略组类型无效" }
            require(names.add(group.name)) { "第 ${index + 1} 个策略组名称重复" }
        }
    }

    fun editorErrorLocation(text: String, error: Throwable): YamlErrorLocation? {
        val location = YamlConfigCodec.errorLocation(editorDocument(text), error) ?: return null
        if (editorHasRoot(text)) return location
        // 作者 long：解析时临时包裹分区，定位必须扣除标题与每行两格缩进，不能把包装文档坐标当成用户正文坐标。
        val line = (location.line - 1).coerceAtLeast(1)
        val offset = (location.offset - "proxy-groups:\n".length - line * 2).coerceIn(0, text.length)
        return YamlErrorLocation(line, (location.column - 2).coerceAtLeast(1), offset)
    }

    fun definition(yaml: String, previous: ProxyGroupDraft): ProxyGroupDraft = previous.copy(json = root(yaml).apply {
        // 作者 long：Codec 为完整配置补的默认 map 不属于单个策略组定义。
        if (!previous.fields.has("proxy-providers")) remove("proxy-providers")
    }.toString())

    fun definitionYaml(group: ProxyGroupDraft): String = YamlConfigCodec.toYaml(group.json)

    fun members(group: ProxyGroupDraft): List<ProxyGroupMember> {
        val proxies = group.strings("proxies")
        val providers = group.strings("use")
        val positions = group.fields.get("use-position")?.takeIf { it.isJsonObject }?.asJsonObject
        return buildList {
            for (index in 0..proxies.size) {
                providers.filter { name ->
                    val position = positions?.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                    (position ?: proxies.size).coerceIn(0, proxies.size) == index
                }.forEach { add(ProxyGroupMember(it, true)) }
                proxies.getOrNull(index)?.let { add(ProxyGroupMember(it)) }
            }
        }
    }

    fun withMembers(group: ProxyGroupDraft, members: List<ProxyGroupMember>): ProxyGroupDraft = group.edit { fields ->
        listOf("proxies", "use").forEach { key ->
            val names = members.filter { it.provider == (key == "use") }.map { it.name }
            if (names.isEmpty()) fields.remove(key) else fields.add(key, array(names))
        }
        var seen = 0
        val count = members.count { !it.provider }
        val positions = JsonObject()
        members.forEach { if (!it.provider) seen++ else if (seen < count) positions.addProperty(it.name, seen) }
        if (positions.size() == 0) fields.remove("use-position") else fields.add("use-position", positions)
    }

    fun array(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }

    fun renameReferences(group: ProxyGroupDraft, old: String, new: String): ProxyGroupDraft =
        group.edit { rewriteGroupReferences(it, mapOf(old to new)) }

    fun updateProfileReferences(profile: Profile, save: ProxyGroupsSave): Profile {
        val renames = save.groups.mapNotNull { group -> group.originalName?.takeIf { it != group.name }?.let { it to group.name } }.toMap()
        // 作者 long：GLOBAL 是核心内建选择器，不能因它不在自定义组列表里而清掉用户的全局模式选择。
        val names = save.groups.map { it.name }.toSet() + "GLOBAL"
        val selected = decodeSelectedMap(profile.selectedMapJson).map { (key, value) -> (renames[key] ?: key) to (renames[value] ?: value) }
            .filter { it.first in names }.toMap()
        val overrides = rootJson(profile.overridesJson)
        overrides.get("dialers")?.takeIf { it.isJsonObject }?.asJsonObject?.let { dialers ->
            dialers.entrySet().toList().forEach { (key, value) -> renames[value.asString]?.let { dialers.addProperty(key, it) } }
        }
        overrides.get("ntp")?.takeIf { it.isJsonObject }?.asJsonObject?.let { ntp ->
            ntp.get("dialer-proxy")?.asString?.let { renames[it]?.let { name -> ntp.addProperty("dialer-proxy", name) } }
        }
        return profile.copy(selectedMapJson = encodeSelectedMap(selected),
            currentGroupName = (renames[profile.currentGroupName] ?: profile.currentGroupName)?.takeIf { it in names },
            unfoldSetJson = encodeUnfoldSet(profile.unfoldSet.map { renames[it] ?: it }.filter { it in names }.toSet()),
            matchTarget = renames[profile.matchTarget] ?: profile.matchTarget, overridesJson = overrides.toString())
    }

    private fun rootJson(json: String) = JsonParser.parseString(json).asJsonObject

    fun proxyNames(content: String): List<String> = root(content).get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject?.get("name")?.asString }.orEmpty()

    fun providerNames(content: String): List<String> = root(content).get("proxy-providers")
        ?.takeIf { it.isJsonObject }?.asJsonObject?.keySet()?.toList().orEmpty()

    fun validate(groups: List<ProxyGroupDraft>, source: String, availableProxies: List<String> = emptyList()) {
        val proxyNames = (proxyNames(source) + availableProxies).distinct()
        val providers = providerNames(source)
        val proxies = root(source).get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }?.associate { it.asJsonObject.get("name").asString to it.asJsonObject }.orEmpty()
        val names = mutableSetOf<String>()
        groups.forEach { group ->
            require(group.name.isNotBlank()) { "策略组名称不能为空" }
            require(group.name !in builtins) { "策略组不能使用保留名称：" + group.name }
            require(names.add(group.name) && group.name !in proxyNames) { "代理或策略组名称重复：" + group.name }
            require(group.text("type") in types) { "不支持的策略组类型：" + group.text("type") }
            listOf("proxies", "use").forEach { key ->
                val values = group.fields.get(key)
                require(values == null || (values.isJsonArray && values.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString })) { key + " 必须是名称列表" }
            }
            require(group.strings("proxies").isNotEmpty() || group.strings("use").isNotEmpty() || group.flag("include-all") ||
                group.flag("include-all-proxies") || (group.flag("include-all-providers") && groups.any {
                    it.key != group.key && it.strings("use").any { name -> name in providers }
                })) { "策略组没有节点来源：" + group.name }
            val fallback = group.text("empty-fallback")
            require(fallback.isBlank() || fallback in builtins || fallback in proxyNames) { "空组回退必须是代理节点：" + fallback }
            listOf("interval", "timeout", "max-failed-times", "tolerance").forEach { key ->
                if (group.fields.has(key)) require(group.text(key).toLongOrNull()?.let { it >= 0 } == true) { key + " 必须是非负整数" }
            }
        }
        val byName = groups.associateBy { it.name }
        val complete = mutableSetOf<String>()
        val path = mutableSetOf<String>()
        fun visit(name: String) {
            if (name in complete) return
            require(path.add(name)) { "策略组或链式代理循环引用：" + (path + name).joinToString(" → ") }
            val group = byName[name]
            val targets = if (group != null) group.strings("proxies") + if (group.flag("include-all") || group.flag("include-all-proxies")) proxyNames else emptyList()
                else proxies[name]?.get("dialer-proxy")?.takeIf { it.isJsonPrimitive }?.asString?.let { listOf(it) }.orEmpty()
            targets.filter { it in byName || it in proxyNames }.forEach { visit(it) }
            path.remove(name)
            complete.add(name)
        }
        groups.forEach { visit(it.name) }
    }

    fun apply(source: String, save: ProxyGroupsSave, availableProxies: List<String> = emptyList()): String {
        if (save.providerProfiles.isNotEmpty()) {
            val bound = ProfileProxyResources.bindGroups(source, save.providerProfiles, save.groups)
            return apply(bound, save.copy(providerProfiles = emptyMap()), availableProxies)
        }
        val renames = save.groups.mapNotNull { group -> group.originalName?.takeIf { it != group.name }?.let { it to group.name } }.toMap()
        val original = root(source)
        val document = original.deepCopy()
        val groups = save.groups.map { group -> group.edit { fields -> rewriteGroupReferences(fields, renames) } }
        document.add("proxy-groups", JsonArray().apply { groups.forEach { add(it.fields) } })
        // 作者 long：Flutter 用内部 ID 维持引用；Kotlin 的 YAML 使用名称，改名时必须同步规则目标与链式代理，不能全局替换同名文本。
        if (renames.isNotEmpty()) {
            fun rewriteRules(value: com.google.gson.JsonElement?) {
                if (value?.isJsonArray != true) return
                value.asJsonArray.forEachIndexed { index, item ->
                    if (item.isJsonPrimitive && item.asJsonPrimitive.isString && !item.asString.startsWith("SUB-RULE,")) {
                        val parts = item.asString.split(',').toMutableList()
                        val target = if (parts.lastOrNull()?.trim() == "no-resolve") parts.lastIndex - 1 else parts.lastIndex
                        if (target >= 1) renames[parts[target].trim()]?.let { parts[target] = it; value.asJsonArray.set(index, com.google.gson.JsonPrimitive(parts.joinToString(","))) }
                    }
                }
            }
            rewriteRules(document.get("rules"))
            document.get("sub-rules")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { rewriteRules(it.value) }
            document.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonObject }?.forEach {
                val fields = it.asJsonObject
                fields.get("dialer-proxy")?.takeIf { value -> value.isJsonPrimitive }?.asString?.let { name ->
                    renames[name]?.let { next -> fields.addProperty("dialer-proxy", next) }
                }
            }
            listOf("ntp", "dns").forEach { section ->
                document.get(section)?.takeIf { it.isJsonObject }?.asJsonObject?.let { fields ->
                    fields.get("dialer-proxy")?.takeIf { it.isJsonPrimitive }?.asString?.let { name ->
                        renames[name]?.let { next -> fields.addProperty("dialer-proxy", next) }
                    }
                }
            }
        }
        validate(groups, YamlConfigCodec.toYaml(document.toString()), availableProxies)
        // 作者 long：手写 YAML 可能使用 flow 根对象或整体缩进；无法定位独立分区时完整序列化，避免追加出重复键破坏正文。
        if (listOf("proxy-groups", "rules", "sub-rules", "proxies", "ntp", "dns").any { key ->
            document.get(key) != original.get(key) && original.has(key) && source.lineSequence().none { topLevelKey(it) == key }
        }) return YamlConfigCodec.toYaml(document.toString())
        var content = source
        listOf("proxy-groups", "rules", "sub-rules", "proxies", "ntp", "dns").forEach { key ->
            if (document.get(key) != original.get(key)) {
                val section = JsonObject().apply { add(key, document.get(key)) }
                content = replaceSection(content, key, YamlConfigCodec.toYaml(section.toString()))
            }
        }
        return content
    }

    private fun rewriteGroupReferences(fields: JsonObject, renames: Map<String, String>) {
        fields.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray?.let { values ->
            fields.add("proxies", array(values.map { renames[it.asString] ?: it.asString }))
        }
        listOf("default-selected", "empty-fallback").forEach { key ->
            fields.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.let { renames[it]?.let { value -> fields.addProperty(key, value) } }
        }
    }

    /** 作者 long：只重新序列化变化分区，其他分区及边界注释按原文保留。 */
    private fun replaceSection(source: String, key: String, rendered: String): String {
        val lines = source.replace("\r\n", "\n").split('\n').toMutableList()
        val start = lines.indexOfFirst { topLevelKey(it) == key }
        if (start < 0) return source.trimEnd() + (if (source.isBlank()) "" else "\n") + rendered
        var end = (start + 1 until lines.size).firstOrNull { topLevelKey(lines[it]) != null } ?: lines.size
        while (end > start + 1 && (lines[end - 1].isBlank() || lines[end - 1].startsWith("#"))) end--
        return (lines.take(start) + rendered.trimEnd().split('\n') + lines.drop(end)).joinToString("\n").trimEnd() + "\n"
    }

    private fun topLevelKey(line: String): String? = line.takeIf {
        it.firstOrNull()?.let { first -> !first.isWhitespace() && first != '#' && first != '-' && first != '{' } == true && ':' in it
    }?.substringBefore(':')?.trim()?.trim('\'', '"')
}
