package com.lonnnnnng.flclash.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lonnnnnng.flclash.service.YamlConfigCodec

internal const val PROFILE_RESOURCE_PREFIX = "kclash-profile:"

data class ProfileProxyResource(val alias: String, val profile: Profile)

/** 作者 long：普通配置兼作代理资源；绑定保存稳定 ID 和配置内别名，资源改名不会改变组成员引用。 */
object ProfileProxyResources {
    fun isReference(value: JsonElement?): Boolean = value?.takeIf { it.isJsonObject }?.asJsonObject?.get("path")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.startsWith(PROFILE_RESOURCE_PREFIX) == true

    fun reference(id: Long) = JsonObject().apply {
        require(id > 0) { "代理资源标识无效" }
        addProperty("type", "file")
        addProperty("path", PROFILE_RESOURCE_PREFIX + id)
    }

    fun idOf(value: JsonElement?): Long? = value?.takeIf { it.isJsonObject }?.asJsonObject?.get("path")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?.takeIf { it.startsWith(PROFILE_RESOURCE_PREFIX) }?.removePrefix(PROFILE_RESOURCE_PREFIX)?.toLongOrNull()?.takeIf { it > 0 }

    fun referenced(document: JsonObject): Set<String> = document.get("proxy-groups")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.filter { it.isJsonObject }?.flatMap { group ->
            group.asJsonObject.get("use")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
        }?.toSet().orEmpty()

    fun definitions(section: String): JsonObject = ProxyGroupDrafts.root("proxy-providers:\n" + section.lines().joinToString("\n") { "  $it" })
        .get("proxy-providers")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

    fun candidates(source: String, profiles: List<Profile>): List<ProfileProxyResource> {
        val definitions = ProxyGroupDrafts.root(source).get("proxy-providers")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val held = definitions.keySet().toMutableSet()
        return profiles.filter { it.type != ProfileType.CUSTOM }.flatMap { profile ->
            val bound = definitions.entrySet().filter { idOf(it.value) == profile.id }.map { it.key }
            if (bound.isNotEmpty()) bound.map { ProfileProxyResource(it, profile) }
            else listOf(ProfileProxyResource(uniqueLabel(profile.realLabel, held).also { held += it }, profile))
        }
    }

    fun uniqueLabel(name: String, held: Set<String>): String {
        val base = name.trim().ifBlank { "代理资源" }.take(118)
        return if (base !in held) base else generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { it !in held }
    }

    fun bind(section: String, profile: Profile, alias: String? = null): String {
        require(profile.type != ProfileType.CUSTOM) { "自定义配置不能作为代理资源" }
        val providers = definitions(section)
        val name = alias ?: uniqueLabel(profile.realLabel, providers.keySet())
        // 作者 long：替换缺失绑定时保留配置内的过滤和健康检查，不能修好引用却丢掉用户选项。
        val fields = providers.get(name)?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()
        listOf("url", "payload", "age-secret-key").forEach(fields::remove)
        reference(profile.id).entrySet().forEach { fields.add(it.key, it.value) }
        providers.add(name, fields)
        return YamlConfigCodec.toYaml(providers.toString()).trimEnd()
    }

    fun bindGroups(source: String, bindings: Map<String, Long>, groups: List<ProxyGroupDraft>): String {
        if (bindings.isEmpty()) return source
        val root = ProxyGroupDrafts.root(source)
        val providers = root.get("proxy-providers")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val used = groups.flatMap { it.strings("use") }.toSet()
        var changed = false
        bindings.filterKeys { it in used }.forEach { (alias, id) ->
            val old = providers.get(alias)
            require(old == null || idOf(old) == id) { "配置已有同名代理资源，请重新打开策略组" }
            if (old == null) { providers.add(alias, reference(id)); changed = true }
        }
        if (!changed) return source
        root.add("proxy-providers", providers)
        return YamlConfigCodec.toYaml(root.toString())
    }

    /** 作者 long：导入重名只改 use 和 use-position，不修改代理密码、节点名或 URL 中的同名文本。 */
    fun renameUses(document: JsonObject, names: Map<String, String>) {
        document.get("proxy-groups")?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonObject }?.forEach { item ->
            val group = item.asJsonObject
            group.get("use")?.takeIf { it.isJsonArray }?.asJsonArray?.let { values ->
                group.add("use", ProxyGroupDrafts.array(values.map { names[it.asString] ?: it.asString }.distinct()))
            }
            group.get("use-position")?.takeIf { it.isJsonObject }?.asJsonObject?.let { positions ->
                group.add("use-position", JsonObject().apply { positions.entrySet().forEach { (name, value) -> add(names[name] ?: name, value) } })
            }
        }
    }
}
