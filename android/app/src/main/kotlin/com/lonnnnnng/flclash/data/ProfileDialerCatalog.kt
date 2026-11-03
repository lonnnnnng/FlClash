package com.lonnnnnng.flclash.data

data class DialerNode(val name: String, val type: String)

/** 作者 long：链式代理的目标沿节点和组成员寻路，不能只禁止选择自身，否则经策略组绕回也会形成环。 */
class ProfileDialerCatalog(source: String) {
    val groups = ProxyGroupDrafts.read(source)
    val nodes = ProxyGroupDrafts.root(source).get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
        val value = it.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val name = value.get("name")?.asString ?: return@mapNotNull null
        DialerNode(name, value.get("type")?.asString.orEmpty())
    }.orEmpty()
    private val nodeNames = nodes.map { it.name }.toSet()
    private val groupByName = groups.associateBy { it.name }
    fun wouldLoop(proxy: String, target: String, dialers: Map<String, String>): Boolean {
        val pending = ArrayDeque<String>().apply { add(target) }
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val name = pending.removeLast()
            if (name == proxy) return true
            if (!visited.add(name)) continue
            val group = groupByName[name]
            if (group != null) {
                pending.addAll(group.strings("proxies"))
                // 作者 long：对齐 Flutter 无过滤 include-all-proxies 的静态环检测；过滤后的实际成员仍由核心决定。
                if (group.fields.get("include-all-proxies")?.asBoolean == true &&
                    listOf("filter", "exclude-filter", "exclude-type").all { group.text(it).isEmpty() }) pending.addAll(nodeNames)
            } else if (name in nodeNames) dialers[name]?.let(pending::add)
        }
        return false
    }
    fun issue(proxy: String, target: String?, dialers: Map<String, String>): String? = when {
        target == null -> null
        target !in nodeNames && target !in groupByName && target !in ProxyGroupDrafts.builtins -> "目标不存在，请重新选择"
        wouldLoop(proxy, target, dialers) -> "链式代理形成循环，请重新选择"
        else -> null
    }
    fun selectable(proxy: String, target: String, dialers: Map<String, String>) =
        (target in nodeNames || target in groupByName) && !wouldLoop(proxy, target, dialers)
}
