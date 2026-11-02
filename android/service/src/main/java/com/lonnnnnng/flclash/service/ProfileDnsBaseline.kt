package com.lonnnnnng.flclash.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 作者 long：订阅可以只包含节点和规则；Android 广播的虚拟 DNS 仍需要核心提供完整的解析服务。 */
object ProfileDnsBaselineApplier {
    fun apply(yaml: String): String {
        val root = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
        val dns = root.get("dns")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        if (dns.get("enable")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true) {
            return yaml
        }
        // 作者 long：对齐 Flutter task.dart 的 baselineDns，先补运行基线，再由 Profile 的显式覆盖决定最终值。
        // 已启用的 DNS 完全沿用原文；未启用时保留未知字段，原始 Profile 文件始终不参与写回。
        dns.addProperty("enable", true)
        dns.addProperty("enhanced-mode", "fake-ip")
        dns.add("nameserver", JsonArray().apply {
            add("https://doh.pub/dns-query")
            add("https://dns.alidns.com/dns-query")
        })
        val defaults = ProfileDnsOverrides(
            enable = true,
            enhancedMode = "fake-ip",
            nameserver = listOf("https://doh.pub/dns-query", "https://dns.alidns.com/dns-query"),
        )
        val header = Regex("(?m)^(?:dns|\"dns\"|'dns')\\s*:(.*)$").find(yaml)
        val inline = header?.groupValues?.get(1)?.substringBefore('#')?.trim().orEmpty()
        if (header != null && ((inline.isNotEmpty() && inline != "{}") || !header.value.startsWith("dns:"))) {
            // 作者 long：行内 map/别名不能再追加缩进字段，只替换 DNS 分区，保留 Dialer 等分区依赖的原始缩进。
            val tailStart = yaml.indexOf('\n', header.range.last).let { if (it < 0) yaml.length else it + 1 }
            val end = Regex("(?m)^[^ \t#\r\n][^\r\n]*:").find(yaml, tailStart)?.range?.first ?: yaml.length
            val section = JsonObject().apply { add("dns", dns) }
            return yaml.substring(0, header.range.first) + YamlConfigCodec.toYaml(section.toString()) + yaml.substring(end)
        }
        if (yaml.trimStart().startsWith("{")) {
            root.add("dns", dns)
            return YamlConfigCodec.toYaml(root.toString())
        }
        return ProfileDnsOverrideApplier.apply(yaml, defaults)
    }
}
