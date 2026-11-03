package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject

internal data class LocalProxyLinkLine(val number: Int, val link: String, val kept: LocalProxy?)

/** 作者 long：链接不能表示完整节点；未编辑的行按原ID和原定义保留，只解码真正改动的行。 */
internal class LocalProxyLinkEdit(text: String, val previous: List<LocalProxy>, val links: List<String>) {
    val lines: List<LocalProxyLinkLine>
    val pending get() = lines.filter { it.kept == null }
    val droppedLinks: Map<String, String>

    init {
        check(previous.size == links.size) { "分享链接读取不完整，请重新打开编辑器" }
        val unused = previous.indices.filter { links[it].isNotEmpty() }.groupBy { links[it] }
            .mapValues { (_, indices) -> indices.map { previous[it] }.toMutableList() }
        lines = text.lines().mapIndexedNotNull { index, value ->
            value.trim().takeIf(String::isNotEmpty)?.let { link ->
                LocalProxyLinkLine(index + 1, link, unused[link]?.takeIf { it.isNotEmpty() }?.removeAt(0))
            }
        }
        val kept = lines.mapNotNull { it.kept?.id }.toSet()
        droppedLinks = previous.indices.filter { links[it].isNotEmpty() && previous[it].id !in kept }
            .associate { previous[it].id to links[it] }
    }

    fun apply(decoded: Map<Int, List<JsonObject>>, groupNames: Set<String>, linked: Map<String, JsonObject>): List<LocalProxy> {
        val kept = lines.mapNotNull { it.kept?.id }.toSet()
        val dropped = previous.filter { it.id in droppedLinks }.associateBy { it.name }.toMutableMap()
        val unsupported = previous.filterIndexed { index, _ -> links[index].isEmpty() }
        val taken = (LocalProxies.reserved + groupNames + unsupported.map { it.name } + lines.mapNotNull { it.kept?.name }).toMutableSet()
        val edited = lines.flatMap { line ->
            line.kept?.let { listOf(it) } ?: checkNotNull(decoded[line.number]) { "分享链接解析不完整，请重试" }.map { definition ->
                val base = localProxyLinkName(definition)
                val replaced = if (base in taken) null else dropped.remove(base)
                val named = definition.deepCopy().apply { addProperty("name", freeLocalProxyLinkName(base, taken)) }
                val fields = if (replaced == null) named else withUnlinkedProxyFields(named, replaced.fields, linked[replaced.id])
                LocalProxy(id = replaced?.id ?: java.util.UUID.randomUUID().toString(), json = fields.toString())
            }
        }
        // 作者 long：无法导出链接的协议不出现在文本里，按原来最近的未改动节点锚定，不能因删除文本被顺带移除。
        val following = mutableMapOf<String?, MutableList<LocalProxy>>()
        var anchor: String? = null
        previous.forEach { old ->
            if (old in unsupported) following.getOrPut(anchor) { mutableListOf() }.add(old)
            else if (old.id in kept) anchor = old.id
        }
        return following[null].orEmpty() + edited.flatMap { listOf(it) + following[it.id].orEmpty() }
    }
}

/** 作者 long：同协议改名也保留链接无法携带的设置；链接能表达但已被删除的字段必须随新链接移除。 */
internal fun withUnlinkedProxyFields(next: JsonObject, previous: JsonObject, linked: JsonObject?): JsonObject {
    val result = next.deepCopy()
    if (linked == null || next.get("type") != previous.get("type")) return result
    fun merge(target: JsonObject, old: JsonObject, carried: JsonObject) {
        old.entrySet().forEach { (key, value) ->
            if (!carried.has(key) && !target.has(key)) target.add(key, value.deepCopy())
            else if (value.isJsonObject && carried.get(key)?.isJsonObject == true && target.get(key)?.isJsonObject == true)
                merge(target.getAsJsonObject(key), value.asJsonObject, carried.getAsJsonObject(key))
        }
    }
    merge(result, previous, linked)
    return result
}

internal fun fillLocalProxyFromLink(definition: JsonObject, current: LocalProxy, linked: JsonObject?, existing: List<LocalProxy>, groupNames: Set<String>): LocalProxy {
    val fields = withUnlinkedProxyFields(definition, current.fields, linked)
    val raw = definition.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val taken = (LocalProxies.reserved + groupNames + existing.filterNot { it.id == current.id }.map { it.name }).toMutableSet()
    val base = if (raw.isEmpty() && current.name.isNotEmpty()) current.name else localProxyLinkName(definition)
    fields.addProperty("name", freeLocalProxyLinkName(base, taken))
    return current.copy(json = fields.toString())
}

private fun localProxyLinkName(definition: JsonObject): String {
    fun text(key: String) = definition.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val name = text("name").trim()
    return if (Regex("^(-\\d+)?$").matches(name)) text("server").ifEmpty { text("type").ifEmpty { "节点" } } else name
}

private fun freeLocalProxyLinkName(base: String, taken: MutableSet<String>): String {
    var name = base
    var index = 1
    while (!taken.add(name)) name = "$base-${(index++).toString().padStart(2, '0')}"
    return name
}
