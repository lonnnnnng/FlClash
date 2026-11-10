package com.lonnnnnng.flclash.data

enum class GroupMemberKind(val field: String, val layout: String) {
    PROXY("proxies", "proxy"), PROVIDER("use", "proxy"),
    ALL_PROXIES("include-all-proxies", "allProxies"), ALL_PROVIDERS("include-all-providers", "allProviders"),
    FILTER("filter", "filter"), EXCLUDE("exclude-filter", "excludeFilter");

    val isFilter get() = this == FILTER || this == EXCLUDE
}

data class GroupMemberEntry(val kind: GroupMemberKind, val name: String) {
    val id: String get() = kind.name + ":" + name
}

data class GroupMemberChoice(val member: GroupMemberEntry, val title: String, val subtitle: String? = null)
data class GroupMemberSection(val id: String, val title: String, val choices: List<GroupMemberChoice>)

/** 作者 long：成员列表还包含自动引用和过滤条件；界面顺序单独记录，不能把过滤条目混进Mihomo的proxies/use。 */
object ProxyGroupMembers {
    private val runs = listOf("proxy", "allProxies", "allProviders", "filter", "excludeFilter")

    private fun names(group: ProxyGroupDraft, kind: GroupMemberKind): List<String> = when (kind) {
        GroupMemberKind.PROXY, GroupMemberKind.PROVIDER -> group.strings(kind.field)
        GroupMemberKind.ALL_PROXIES, GroupMemberKind.ALL_PROVIDERS -> if (group.flag(kind.field)) listOf("*") else emptyList()
        GroupMemberKind.FILTER, GroupMemberKind.EXCLUDE -> FilterPresets.parts(group.text(kind.field))
    }

    fun read(group: ProxyGroupDraft): List<GroupMemberEntry> {
        val values = linkedMapOf("proxy" to ProxyGroupDrafts.members(group).map {
            GroupMemberEntry(if (it.provider) GroupMemberKind.PROVIDER else GroupMemberKind.PROXY, it.name)
        }.iterator())
        GroupMemberKind.entries.filter { it.layout != "proxy" }.forEach { kind ->
            values[kind.layout] = names(group, kind).map { GroupMemberEntry(kind, it) }.iterator()
        }
        return buildList {
            group.memberLayout.orEmpty().forEach { values[it]?.takeIf { iterator -> iterator.hasNext() }?.let { add(it.next()) } }
            values.values.forEach { addAll(it.asSequence()) }
        }
    }

    fun replace(group: ProxyGroupDraft, entries: List<GroupMemberEntry>): ProxyGroupDraft {
        val next = ProxyGroupDrafts.withMembers(group, entries.filter { it.kind.layout == "proxy" }.map {
            ProxyGroupMember(it.name, it.kind == GroupMemberKind.PROVIDER)
        })
        return next.edit { fields ->
            GroupMemberKind.entries.filter { it.layout != "proxy" }.forEach { kind ->
                val values = entries.filter { it.kind == kind }.map { it.name }
                // 作者 long：没有改动的表达式和显式false保持原文，重新排序不能顺手规范化用户定义。
                if (values != names(group, kind)) {
                    if (values.isEmpty()) fields.remove(kind.field)
                    else if (kind.isFilter) fields.addProperty(kind.field, values.joinToString("`"))
                    else fields.addProperty(kind.field, true)
                }
            }
        }.copy(memberLayout = layout(entries))
    }

    private fun layout(entries: List<GroupMemberEntry>): List<String>? {
        val order = entries.map { runs.indexOf(it.kind.layout) }
        var end = order.size
        while (end > 0 && (end == order.size || order[end - 1] <= order[end])) end--
        return entries.take(end).map { it.kind.layout }.takeIf { it.isNotEmpty() }
    }

    fun restoreLayouts(groups: List<ProxyGroupDraft>, overridesJson: String): List<ProxyGroupDraft> {
        val layouts = com.google.gson.JsonParser.parseString(overridesJson).asJsonObject.get("groupMemberLayouts")
            ?.takeIf { it.isJsonObject }?.asJsonObject
        return groups.map { group -> group.copy(memberLayout = layouts?.get(group.name)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.take(4096)?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive && value.asString in runs }?.asString }?.takeIf { it.isNotEmpty() }) }
    }

    fun saveLayouts(overrides: com.google.gson.JsonObject, groups: List<ProxyGroupDraft>) {
        // 作者 long：按组名保存的显示顺序随同一次配置事务改名/删除，绝不进入生成的Mihomo YAML。
        val layouts = com.google.gson.JsonObject()
        groups.forEach { group -> group.memberLayout?.takeIf { it.isNotEmpty() }?.let { layouts.add(group.name, ProxyGroupDrafts.array(it)) } }
        if (layouts.size() == 0) overrides.remove("groupMemberLayouts") else overrides.add("groupMemberLayouts", layouts)
    }

    fun layoutsJson(groups: List<ProxyGroupDraft>): String = com.google.gson.JsonObject().also { saveLayouts(it, groups) }
        .getAsJsonObject("groupMemberLayouts")?.toString() ?: "{}"

    fun matchesLayouts(overridesJson: String, expected: String): Boolean = runCatching {
        val actual = com.google.gson.JsonParser.parseString(overridesJson).asJsonObject.getAsJsonObject("groupMemberLayouts")
            ?: com.google.gson.JsonObject()
        actual == com.google.gson.JsonParser.parseString(expected)
    }.getOrDefault(false)

    fun title(entry: GroupMemberEntry, presets: List<FilterPreset>): String = when (entry.kind) {
        GroupMemberKind.ALL_PROXIES -> "全部引用的代理"
        GroupMemberKind.ALL_PROVIDERS -> "全部引用的代理集合"
        else -> if (entry.kind.isFilter) presets.firstOrNull { it.regex == entry.name }?.label ?: entry.name else entry.name
    }

    fun subtitle(entry: GroupMemberEntry): String = when (entry.kind) {
        GroupMemberKind.PROXY -> if (entry.name in ProxyGroupDrafts.builtins) "基本策略" else "代理 / 策略组"
        GroupMemberKind.PROVIDER -> "代理集合"
        GroupMemberKind.ALL_PROXIES, GroupMemberKind.ALL_PROVIDERS -> "自动包含"
        GroupMemberKind.FILTER -> "筛选"
        GroupMemberKind.EXCLUDE -> "排除筛选"
    }

    fun sections(origin: ProxyGroupDraft, groups: List<ProxyGroupDraft>, source: String, presets: List<FilterPreset>): List<GroupMemberSection> {
        val root = ProxyGroupDrafts.root(source)
        val nodes = root.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty().filter { it.isJsonObject }.map { it.asJsonObject }
        val providers = root.get("proxy-providers")?.takeIf { it.isJsonObject }?.asJsonObject
        val taken = origin.strings("proxies").toSet() + origin.name
        fun named(kind: GroupMemberKind, name: String, subtitle: String? = null) = GroupMemberChoice(GroupMemberEntry(kind, name), name, subtitle)
        fun field(value: com.google.gson.JsonObject, key: String) = value.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return buildList {
            add(GroupMemberSection("base", "基本策略", ProxyGroupDrafts.builtins.filterNot(taken::contains).map { named(GroupMemberKind.PROXY, it) }))
            add(GroupMemberSection("auto", "自动包含", buildList {
                if (nodes.isNotEmpty() && !origin.flag("include-all-proxies")) add(GroupMemberChoice(GroupMemberEntry(GroupMemberKind.ALL_PROXIES, "*"), "全部引用的代理"))
                if (providers != null && providers.size() > 0 && !origin.flag("include-all-providers")) add(GroupMemberChoice(GroupMemberEntry(GroupMemberKind.ALL_PROVIDERS, "*"), "全部引用的代理集合"))
            }))
            add(GroupMemberSection("local", "本地代理", nodes.mapNotNull { node ->
                val name = field(node, "name")
                if (name.isBlank() || name in taken) null else named(GroupMemberKind.PROXY, name, field(node, "type"))
            }))
            add(GroupMemberSection("groups", "策略组", groups.filter { it.key != origin.key && it.name !in taken && it.name.isNotBlank() }
                .map { named(GroupMemberKind.PROXY, it.name, it.text("type")) }))
            add(GroupMemberSection("providers", "代理集合", providers?.entrySet().orEmpty().filter { it.key !in origin.strings("use") }.map {
                named(GroupMemberKind.PROVIDER, it.key, if (it.value.isJsonObject) field(it.value.asJsonObject, "type").ifBlank { "资源" } else "资源")
            }))
            listOf(GroupMemberKind.FILTER to "筛选", GroupMemberKind.EXCLUDE to "排除筛选").forEach { (kind, title) ->
                add(GroupMemberSection(kind.layout, title, presets.filterNot { FilterPresets.isApplied(it, origin.text(kind.field)) }.map {
                    GroupMemberChoice(GroupMemberEntry(kind, it.regex), it.label, it.regex)
                }))
            }
        }.filter { it.choices.isNotEmpty() }
    }

    fun select(origin: ProxyGroupDraft, selected: List<GroupMemberEntry>): ProxyGroupDraft {
        // 作者 long：每次按打开时的草稿重放本轮选择，撤回一个重叠预设不能删掉原条件或另一个预设仍需的片段。
        if (selected.isEmpty()) return origin
        // 作者 long：Flutter添加只追加各字段，已有Provider位置与显示布局保持；只有显式排序才重算混排位置。
        return origin.edit { fields ->
            GroupMemberKind.entries.forEach { kind ->
                val picked = selected.filter { it.kind == kind }.flatMap { if (kind.isFilter) FilterPresets.parts(it.name) else listOf(it.name) }
                if (picked.isNotEmpty()) {
                    val values = (names(origin, kind) + picked).distinct()
                    when (kind) {
                        GroupMemberKind.PROXY, GroupMemberKind.PROVIDER -> fields.add(kind.field, ProxyGroupDrafts.array(values))
                        GroupMemberKind.FILTER, GroupMemberKind.EXCLUDE -> fields.addProperty(kind.field, values.joinToString("`"))
                        else -> fields.addProperty(kind.field, true)
                    }
                }
            }
        }
    }
}
