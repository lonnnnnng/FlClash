package com.lonnnnnng.flclash.data

import com.google.gson.*

internal data class LocalProxyAddition(val row: LocalProxy, val added: Boolean)

/** 作者 long：沿用Flutter导入规则，别名和配置独有链式设置不能把同一节点变成多个本地副本。 */
internal fun planLocalProxyAddition(definition: JsonObject, existing: List<LocalProxy>, groupNames: Set<String>, heldNames: Set<String> = emptySet()): LocalProxyAddition {
    val node = definition.deepCopy().apply { remove("dialer-proxy") }
    val original = node.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    require(original.isNotBlank()) { "导入节点缺少名称" }
    fun weak(value: JsonElement): JsonElement = when {
        value.isJsonObject -> JsonObject().apply { value.asJsonObject.entrySet().forEach { (key, child) -> add(key, weak(child)) } }
        value.isJsonArray -> JsonArray().apply { value.asJsonArray.forEach { add(weak(it)) } }
        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> JsonPrimitive(value.asString)
        else -> value
    }
    fun comparable(value: JsonObject) = weak(value.deepCopy().apply { remove("name"); remove("dialer-proxy") })
    fun identity(value: JsonObject): List<String?>? {
        fun text(key: String) = value.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        val type = text("type").orEmpty()
        val server = text("server")?.takeIf(String::isNotEmpty) ?: return null
        val credentials = LocalProxies.required(type).filter { it in setOf("username", "password", "uuid") }
        if (credentials.isEmpty()) return null
        return listOf(text("name"), type, server, text("port")) + credentials.map(::text)
    }
    val comparison = comparable(node)
    existing.firstOrNull { comparable(it.fields) == comparison }?.let { return LocalProxyAddition(it, false) }
    val account = identity(node)
    // 作者 long：同名同服务器账号已存在时保留本地可选设置，不能用Provider覆盖后的细节反向覆盖用户编辑。
    existing.firstOrNull { account != null && identity(it.fields) == account }?.let { return LocalProxyAddition(it, false) }
    var name = original; var suffix = 2
    while (name in LocalProxies.reserved || name in groupNames || existing.any { it.name == name } || name != original && name in heldNames) name = "$original (${suffix++})"
    node.addProperty("name", name)
    return LocalProxyAddition(LocalProxy(json = node.toString()), true)
}
