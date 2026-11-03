package com.lonnnnnng.flclash.data

data class ProxyGroupIssue(val message: String, val blocking: Boolean = true)

/** 作者 long：沿用 Flutter 的可修复警告与保存阻断区分，失效引用不能在编辑时被静默删除。 */
object ProxyGroupIssues {
    fun inspect(group: ProxyGroupDraft, groups: List<ProxyGroupDraft>, source: String): List<ProxyGroupIssue> {
        val root = ProxyGroupDrafts.root(source)
        val proxyNames = ProxyGroupDrafts.proxyNames(source)
        val providers = ProxyGroupDrafts.providerNames(source).toSet()
        val proxies = root.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }?.associate { it.asJsonObject.get("name").asString to it.asJsonObject }.orEmpty()
        val byName = groups.associateBy { it.name }
        return buildList {
            when {
                group.name.isBlank() -> add(ProxyGroupIssue("策略组名称不能为空"))
                group.name in ProxyGroupDrafts.builtins -> add(ProxyGroupIssue("策略组不能使用保留名称：${group.name}"))
                group.name in proxyNames || groups.any { it.key != group.key && it.name == group.name } ->
                    add(ProxyGroupIssue("代理或策略组名称重复：${group.name}"))
            }
            if (group.text("type") !in ProxyGroupDrafts.types) add(ProxyGroupIssue("不支持的策略组类型：${group.text("type")}"))
            listOf("proxies", "use").forEach { key ->
                val values = group.fields.get(key)
                if (values != null && (!values.isJsonArray || !values.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }))
                    add(ProxyGroupIssue("$key 必须是名称列表"))
            }
            if (none { it.message.endsWith("必须是名称列表") }) {
                val members = group.strings("proxies")
                val use = group.strings("use")
                if (members.isEmpty() && use.isEmpty() && !group.flag("include-all") && !group.flag("include-all-proxies") &&
                    !(group.flag("include-all-providers") && groups.any { it.key != group.key && it.strings("use").any(providers::contains) }))
                    add(ProxyGroupIssue("策略组没有节点来源：${group.name}"))
                val missing = members.filterNot { it in byName || it in proxyNames || it in ProxyGroupDrafts.builtins }
                if (missing.isNotEmpty()) add(ProxyGroupIssue("缺失代理或策略组：${missing.joinToString("、")}", false))
                val missingProviders = use.filterNot(providers::contains)
                if (missingProviders.isNotEmpty()) add(ProxyGroupIssue("缺失资源：${missingProviders.joinToString("、")}", false))
            }
            val fallback = group.text("empty-fallback")
            if (fallback.isNotBlank() && fallback !in ProxyGroupDrafts.builtins && fallback !in proxyNames)
                add(ProxyGroupIssue("空组回退必须是代理节点：$fallback"))
            listOf("interval", "timeout", "max-failed-times", "tolerance").forEach { key ->
                if (group.fields.has(key) && group.text(key).toLongOrNull()?.let { it >= 0 } != true)
                    add(ProxyGroupIssue("$key 必须是非负整数"))
            }
            val path = linkedSetOf<String>()
            val complete = mutableSetOf<String>()
            fun loop(name: String): List<String>? {
                if (name in complete) return null
                if (!path.add(name)) return path.toList().dropWhile { it != name } + name
                val current = byName[name]
                val next = if (current != null) current.strings("proxies") +
                    if (current.flag("include-all") || current.flag("include-all-proxies")) proxyNames else emptyList()
                else proxies[name]?.get("dialer-proxy")?.takeIf { it.isJsonPrimitive }?.asString?.let(::listOf).orEmpty()
                for (target in next.filter { it in byName || it in proxyNames }) loop(target)?.let { return it }
                path.remove(name); complete.add(name)
                return null
            }
            loop(group.name)?.let { add(ProxyGroupIssue("策略组或链式代理循环引用：${it.joinToString(" → ")}")) }
        }
    }

    fun duplicate(groups: List<ProxyGroupDraft>, keys: Set<String>, source: String): List<ProxyGroupDraft> {
        val taken = (groups.map { it.name } + ProxyGroupDrafts.proxyNames(source) + ProxyGroupDrafts.builtins).toMutableSet()
        return buildList {
            groups.forEach { group ->
                add(group)
                if (group.key in keys) {
                    var name = group.name + " 副本"; var suffix = 2
                    while (!taken.add(name)) { name = group.name + " 副本 " + suffix; suffix++ }
                    // 作者 long：副本必须有新标识且不携带原名称映射，否则保存会把原组的引用误改到副本。
                    add(ProxyGroupDraft(json = group.json).edit { it.addProperty("name", name) })
                }
            }
        }
    }

    fun referencedNames(groups: List<ProxyGroupDraft>, keys: Set<String>, source: String): List<String> {
        val names = groups.filter { it.key in keys }.map { it.name }.toSet()
        val root = ProxyGroupDrafts.root(source)
        val references = groups.filterNot { it.key in keys }.flatMap { it.strings("proxies") + it.text("default-selected") + it.text("empty-fallback") }.toMutableSet()
        fun rules(value: com.google.gson.JsonElement?) {
            value?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.forEach {
                val parts = it.asString.split(',').map(String::trim)
                references += if (parts.lastOrNull() == "no-resolve") parts.getOrNull(parts.lastIndex - 1).orEmpty() else parts.lastOrNull().orEmpty()
            }
        }
        rules(root.get("rules"))
        root.get("sub-rules")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { rules(it.value) }
        root.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonObject }?.forEach {
            it.asJsonObject.get("dialer-proxy")?.takeIf { it.isJsonPrimitive }?.asString?.let(references::add)
        }
        listOf("ntp", "dns").forEach { section -> root.get(section)?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("dialer-proxy")?.takeIf { it.isJsonPrimitive }?.asString?.let(references::add) }
        return names.filter(references::contains)
    }
}
