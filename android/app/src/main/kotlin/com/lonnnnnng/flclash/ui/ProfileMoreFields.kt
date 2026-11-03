package com.lonnnnnng.flclash.ui

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.YamlConfigCodec

internal enum class MoreFieldKind { TOGGLE, TEXT, PORT, NUMBER, LIST, ADDRESSES, PROTOCOL }
internal data class MoreField(val key: String, val label: String, val kind: MoreFieldKind, val defaultJson: String, val description: String = "") {
    val protocol get() = key.substringAfter("sniff.")
    fun defaultValue(): JsonElement = JsonParser.parseString(defaultJson)
}

internal val ntpFields = listOf(
    MoreField("enable", "状态", MoreFieldKind.TOGGLE, "false", "通过 NTP 同步时间"),
    MoreField("server", "服务器", MoreFieldKind.TEXT, "\"time.apple.com\""),
    MoreField("port", "端口", MoreFieldKind.PORT, "123"),
    MoreField("interval", "同步间隔（分钟）", MoreFieldKind.NUMBER, "30"),
    MoreField("dialer-proxy", "出站代理", MoreFieldKind.TEXT, "\"\"", "用于连接时间服务器的节点或策略组"),
    MoreField("write-to-system", "写入系统时间", MoreFieldKind.TOGGLE, "false", "Android 普通应用不能修改系统时钟"),
)
internal val snifferFields = listOf(
    MoreField("enable", "状态", MoreFieldKind.TOGGLE, "false", "从连接内容中识别域名"),
    MoreField("override-destination", "覆盖目标地址", MoreFieldKind.TOGGLE, "true", "使用嗅探出的域名作为连接目标"),
    MoreField("force-dns-mapping", "强制 DNS 映射", MoreFieldKind.TOGGLE, "true"),
    MoreField("parse-pure-ip", "嗅探纯 IP 连接", MoreFieldKind.TOGGLE, "true"),
    MoreField("force-domain", "强制嗅探域名", MoreFieldKind.LIST, "[]"),
    MoreField("skip-domain", "跳过嗅探域名", MoreFieldKind.LIST, "[]"),
    MoreField("skip-src-address", "跳过源地址", MoreFieldKind.ADDRESSES, "[]"),
    MoreField("skip-dst-address", "跳过目标地址", MoreFieldKind.ADDRESSES, "[]"),
) + listOf("HTTP", "TLS", "QUIC").map { MoreField("sniff.$it", it, MoreFieldKind.PROTOCOL, "{}", "端口与独立目标设置") }

internal fun moreFieldValue(root: JsonObject, field: MoreField): JsonElement? =
    if (field.kind == MoreFieldKind.PROTOCOL) root.getAsJsonObject("sniff")?.get(field.protocol) else root.get(field.key)

internal fun setMoreField(root: JsonObject, field: MoreField, value: JsonElement?) {
    if (field.kind != MoreFieldKind.PROTOCOL) {
        if (value == null) root.remove(field.key) else root.add(field.key, value)
    } else {
        val protocols = root.getAsJsonObject("sniff") ?: JsonObject().also { root.add("sniff", it) }
        if (value == null) protocols.remove(field.protocol) else protocols.add(field.protocol, value)
        // 作者 long：移除最后一个协议恢复继承；快捷 YAML 中显式 sniff: {} 仍可表达清空全部协议。
        if (protocols.size() == 0) root.remove("sniff")
    }
}

internal fun splitMoreList(value: String): List<String> = value.lines().map(String::trim).filter(String::isNotEmpty).distinct()

internal fun moreFieldsYaml(root: JsonObject): String = if (root.size() == 0) "{}\n" else YamlConfigCodec.toYaml(root.toString())

internal fun parseMoreFieldsYaml(content: String, ntp: Boolean): JsonObject {
    require(content.toByteArray().size <= 2 * 1024 * 1024) { "分区 YAML 不能超过 2 MiB" }
    if (content.isBlank()) return JsonObject()
    // 作者 long：把分区片段放在独立键下，避免共享脚本 codec 自动补出的 proxy-providers 被误当成用户字段。
    val document = "settings:\n" + content.lineSequence().joinToString("\n") { "  $it" }
    val value = runCatching { JsonParser.parseString(YamlConfigCodec.toJson(document)).asJsonObject.get("settings") }
        .getOrElse { throw IllegalArgumentException("YAML 格式无效，请检查缩进、引号和列表") }
    if (value == null || value.isJsonNull) return JsonObject()
    require(value.isJsonObject) { "分区 YAML 必须是字段映射" }
    val root = value.asJsonObject.deepCopy()
    val definitions = if (ntp) ntpFields else snifferFields.filter { it.kind != MoreFieldKind.PROTOCOL }
    val allowed = definitions.map { it.key }.toSet() + if (ntp) emptySet() else setOf("sniff")
    require(root.keySet().all { it in allowed }) { "存在本分区不支持的字段，请只填写分区内部字段" }
    root.entrySet().filter { it.value.isJsonNull }.map { it.key }.forEach(root::remove)
    definitions.forEach { field -> root.get(field.key)?.let { entry ->
        val valid = when (field.kind) {
            MoreFieldKind.TOGGLE -> entry.isJsonPrimitive && entry.asJsonPrimitive.isBoolean
            MoreFieldKind.PORT, MoreFieldKind.NUMBER -> entry.isJsonPrimitive && entry.asJsonPrimitive.isNumber && entry.asString.toIntOrNull() != null
            MoreFieldKind.LIST, MoreFieldKind.ADDRESSES -> entry.isJsonArray && entry.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            else -> entry.isJsonPrimitive && entry.asJsonPrimitive.isString
        }
        require(valid) { "${field.label}的数据类型无效" }
    } }
    root.get("sniff")?.let { protocols ->
        require(protocols.isJsonObject) { "sniff 必须是协议映射" }
        val normalized = JsonObject()
        protocols.asJsonObject.entrySet().forEach { (name, config) ->
            val protocol = name.uppercase(java.util.Locale.ROOT)
            require(protocol in setOf("HTTP", "TLS", "QUIC") && !normalized.has(protocol)) { "嗅探协议无效或重复" }
            if (config.isJsonNull) return@forEach
            require(config.isJsonObject && config.asJsonObject.keySet().all { it in setOf("ports", "override-destination") }) { "协议只支持 ports 和 override-destination" }
            val next = config.asJsonObject.deepCopy()
            next.get("ports")?.let { ports ->
                require(ports.isJsonArray && ports.asJsonArray.all { it.isJsonPrimitive && (it.asJsonPrimitive.isNumber || it.asJsonPrimitive.isString) }) { "协议端口必须为端口或范围列表" }
                next.add("ports", com.google.gson.JsonArray().apply { ports.asJsonArray.forEach { add(it.asString) } })
            }
            next.get("override-destination")?.let { destination ->
                if (destination.isJsonNull) next.remove("override-destination")
                else require(destination.isJsonPrimitive && destination.asJsonPrimitive.isBoolean) { "协议目标设置必须为布尔值" }
            }
            normalized.add(protocol, next)
        }
        root.add("sniff", normalized)
    }
    validateMoreFields(root, ntp)
    return root
}

internal fun validateMoreFields(root: JsonObject, ntp: Boolean) {
    (if (ntp) ntpFields else snifferFields).filter { it.kind == MoreFieldKind.TOGGLE }.forEach { field ->
        root.get(field.key)?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "${field.label}必须为布尔值" } }
    }
    if (ntp) {
        root.get("server")?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "NTP 服务器格式无效" }
            val host = it.asString
            // 作者 long：与 Flutter validateHost 一致，服务器只接受主机名或纯 IPv6，不把 URL、端口、空标签当成主机。
            val valid = if (':' in host) isMoreNumericAddress(host) else host.length <= 253 && host.split('.').all {
                it.matches(Regex("[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?"))
            }
            require(valid) { "NTP 服务器格式无效" }
        }
        root.get("dialer-proxy")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.length <= 256 && '\n' !in it.asString && '\r' !in it.asString) { "出站代理名称无效" } }
        for (key in listOf("port", "interval")) root.get(key)?.let {
            val value = if (it.isJsonPrimitive) it.asString.toIntOrNull() else null
            require(value != null && value > 0 && (key != "port" || value <= 65535)) { if (key == "port") "端口必须在 1–65535 之间" else "同步间隔必须为正整数" }
            root.addProperty(key, value)
        }
    } else {
        for (field in snifferFields.filter { it.kind in setOf(MoreFieldKind.LIST, MoreFieldKind.ADDRESSES) }) root.get(field.key)?.let { list ->
            require(list.isJsonArray && list.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() && it.asString.length <= 253 }) { "${field.label}必须为非空文本列表" }
        }
        for (key in listOf("skip-src-address", "skip-dst-address")) root.get(key)?.asJsonArray?.forEach {
            val matcher = it.asString
            // 作者 long：Mihomo 地址匹配项也支持 GeoIP 与规则资源引用，不能只按 IP/CIDR 拦截上游可保存的设置。
            if (matcher.startsWith("geoip:", ignoreCase = true) || matcher.startsWith("rule-set:", ignoreCase = true)) return@forEach
            val parts = matcher.split('/')
            val address = parts[0]
            val ipv6 = address.contains(':')
            require(isMoreNumericAddress(address) && parts.size <= 2 && (parts.size == 1 || parts[1].toIntOrNull() in 0..(if (ipv6) 128 else 32))) { "地址应为 IP、CIDR、geoip: 或 rule-set: 匹配项" }
        }
        root.get("sniff")?.let { require(it.isJsonObject) { "sniff 必须是协议映射" } }
        root.get("sniff")?.asJsonObject?.entrySet()?.forEach { (_, config) ->
            require(config.isJsonObject) { "协议设置必须为字段映射" }
            config.asJsonObject.get("override-destination")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "协议目标设置必须为布尔值" } }
            config.asJsonObject.get("ports")?.let { require(it.isJsonArray) { "协议端口必须为列表" } }
            config.asJsonObject.get("ports")?.asJsonArray?.forEach { element ->
                require(element.isJsonPrimitive && !element.asJsonPrimitive.isBoolean) { "协议端口格式无效" }
                val range = element.asString.split('-').map { it.toIntOrNull() }
                require(range.size in 1..2 && range.all { it != null && it in 1..65535 } && (range.size == 1 || range[0]!! <= range[1]!!)) { "端口应为 1–65535 或递增范围" }
            }
        }
    }
}

private fun isMoreNumericAddress(address: String): Boolean {
    val numeric = if (':' in address) address.matches(Regex("[0-9a-fA-F:.]+")) else address.split('.').let { octets ->
        octets.size == 4 && octets.all { value -> value.isNotEmpty() && value.all(Char::isDigit) && value.toIntOrNull()?.let { it in 0..255 } == true }
    }
    // 作者 long：先限制为数字地址再调用解析器，字段校验不会因为输入域名触发真实 DNS 查询。
    return numeric && runCatching { java.net.InetAddress.getByName(address) }.isSuccess
}
