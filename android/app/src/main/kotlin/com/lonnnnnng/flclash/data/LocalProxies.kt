package com.lonnnnnng.flclash.data

import com.google.gson.*
import java.util.UUID
import com.lonnnnnng.flclash.service.YamlConfigCodec

data class LocalProxy(val id: String = UUID.randomUUID().toString(), val json: String = "{\"name\":\"\",\"type\":\"ss\"}") {
    override fun toString(): String = "LocalProxy(id=$id)"
    val fields: JsonObject get() = JsonParser.parseString(json).asJsonObject
    val name: String get() = fields.get("name")?.asString.orEmpty()
    val type: String get() = fields.get("type")?.asString.orEmpty()
}
enum class ProxyFieldKind { TEXT, SECRET, NUMBER, TOGGLE, CHOICE, LIST, MAP }
data class ProxyField(val id: String, val path: String, val label: String, val kind: ProxyFieldKind = ProxyFieldKind.TEXT)

/** 作者 long：全局节点保留核心原始定义，表单只改已知字段，避免导入后丢失协议扩展参数。 */
object LocalProxies {
    const val KEY = "global_local_proxies"
    val reserved = (ProxyGroupDrafts.builtins + "GLOBAL").toSet()
    val types = "ss ssr socks5 http vmess vless snell trojan hysteria hysteria2 wireguard tuic shadowquic gost-relay direct dns reject rematch ssh mieru anytls sudoku masque trusttunnel openvpn tailscale zerotier easytier".split(' ')
    val catalog = listOf(
        ProxyField("username", "username", "用户名", ProxyFieldKind.TEXT),
        ProxyField("password", "password", "密码", ProxyFieldKind.SECRET),
        ProxyField("uuid", "uuid", "UUID", ProxyFieldKind.TEXT),
        ProxyField("psk", "psk", "预共享密钥", ProxyFieldKind.SECRET),
        ProxyField("key", "key", "密钥", ProxyFieldKind.SECRET),
        ProxyField("privateKey", "private-key", "私钥", ProxyFieldKind.SECRET),
        ProxyField("publicKey", "public-key", "公钥", ProxyFieldKind.TEXT),
        ProxyField("privateKeyPassphrase", "private-key-passphrase", "私钥口令", ProxyFieldKind.SECRET),
        ProxyField("preSharedKey", "pre-shared-key", "预共享密钥", ProxyFieldKind.SECRET),
        ProxyField("authStr", "auth-str", "密码", ProxyFieldKind.SECRET),
        ProxyField("cipher", "cipher", "加密方式", ProxyFieldKind.CHOICE),
        ProxyField("alterId", "alterId", "Alter ID", ProxyFieldKind.NUMBER),
        ProxyField("protocol", "protocol", "协议", ProxyFieldKind.CHOICE),
        ProxyField("obfs", "obfs", "混淆", ProxyFieldKind.CHOICE),
        ProxyField("transport", "transport", "传输", ProxyFieldKind.CHOICE),
        ProxyField("up", "up", "上传带宽", ProxyFieldKind.TEXT),
        ProxyField("down", "down", "下载带宽", ProxyFieldKind.TEXT),
        ProxyField("network", "network", "传输协议", ProxyFieldKind.CHOICE),
        ProxyField("flow", "flow", "Flow", ProxyFieldKind.CHOICE),
        ProxyField("wsPath", "ws-opts.path", "WebSocket Path", ProxyFieldKind.TEXT),
        ProxyField("wsHost", "ws-opts.headers.Host", "WebSocket Host", ProxyFieldKind.TEXT),
        ProxyField("wsHeaders", "ws-opts.headers", "WebSocket 请求头", ProxyFieldKind.MAP),
        ProxyField("wsMaxEarlyData", "ws-opts.max-early-data", "Max Early Data", ProxyFieldKind.NUMBER),
        ProxyField("wsEarlyDataHeaderName", "ws-opts.early-data-header-name", "Early Data Header", ProxyFieldKind.TEXT),
        ProxyField("wsHttpUpgrade", "ws-opts.v2ray-http-upgrade", "HTTPUpgrade", ProxyFieldKind.TOGGLE),
        ProxyField("wsHttpUpgradeFastOpen", "ws-opts.v2ray-http-upgrade-fast-open", "HTTPUpgrade Fast Open", ProxyFieldKind.TOGGLE),
        ProxyField("httpMethod", "http-opts.method", "HTTP Method", ProxyFieldKind.TEXT),
        ProxyField("httpPath", "http-opts.path", "HTTP Path", ProxyFieldKind.LIST),
        ProxyField("httpHost", "http-opts.headers.Host", "HTTP Host", ProxyFieldKind.LIST),
        ProxyField("httpHeaders", "http-opts.headers", "HTTP 请求头", ProxyFieldKind.MAP),
        ProxyField("h2Host", "h2-opts.host", "HTTP/2 Host", ProxyFieldKind.LIST),
        ProxyField("h2Path", "h2-opts.path", "HTTP/2 Path", ProxyFieldKind.TEXT),
        ProxyField("grpcServiceName", "grpc-opts.grpc-service-name", "gRPC Service Name", ProxyFieldKind.TEXT),
        ProxyField("xhttpPath", "xhttp-opts.path", "XHTTP Path", ProxyFieldKind.TEXT),
        ProxyField("xhttpHost", "xhttp-opts.host", "XHTTP Host", ProxyFieldKind.TEXT),
        ProxyField("mkcpSeed", "mkcp-opts.seed", "mKCP Seed", ProxyFieldKind.SECRET),
        ProxyField("mkcpHeader", "mkcp-opts.header", "mKCP Header", ProxyFieldKind.CHOICE),
        ProxyField("mekyaUrl", "mekya-opts.url", "Mekya URL", ProxyFieldKind.TEXT),
        ProxyField("headers", "headers", "请求头", ProxyFieldKind.MAP),
        ProxyField("tls", "tls", "TLS", ProxyFieldKind.TOGGLE),
        ProxyField("sni", "sni", "SNI", ProxyFieldKind.TEXT),
        ProxyField("servername", "servername", "SNI", ProxyFieldKind.TEXT),
        ProxyField("alpn", "alpn", "ALPN", ProxyFieldKind.LIST),
        ProxyField("skipCertVerify", "skip-cert-verify", "跳过证书验证", ProxyFieldKind.TOGGLE),
        ProxyField("clientFingerprint", "client-fingerprint", "客户端指纹", ProxyFieldKind.CHOICE),
        ProxyField("realityPublicKey", "reality-opts.public-key", "Reality Public Key", ProxyFieldKind.TEXT),
        ProxyField("realityShortId", "reality-opts.short-id", "Reality Short ID", ProxyFieldKind.TEXT),
        ProxyField("plugin", "plugin", "插件", ProxyFieldKind.CHOICE),
        ProxyField("pluginMode", "plugin-opts.mode", "插件模式", ProxyFieldKind.TEXT),
        ProxyField("pluginHost", "plugin-opts.host", "插件主机", ProxyFieldKind.TEXT),
        ProxyField("pluginPath", "plugin-opts.path", "插件路径", ProxyFieldKind.TEXT),
        ProxyField("pluginUsername", "plugin-opts.username", "插件用户名", ProxyFieldKind.TEXT),
        ProxyField("pluginPassword", "plugin-opts.password", "插件密码", ProxyFieldKind.SECRET),
        ProxyField("pluginVersion", "plugin-opts.version", "协议版本", ProxyFieldKind.NUMBER),
        ProxyField("pluginVersionHint", "plugin-opts.version-hint", "TLS Version", ProxyFieldKind.CHOICE),
        ProxyField("pluginRestlsScript", "plugin-opts.restls-script", "Restls Script", ProxyFieldKind.TEXT),
        ProxyField("pluginKey", "plugin-opts.key", "密钥", ProxyFieldKind.SECRET),
        ProxyField("pluginCrypt", "plugin-opts.crypt", "插件加密方式", ProxyFieldKind.CHOICE),
        ProxyField("pluginTls", "plugin-opts.tls", "TLS", ProxyFieldKind.TOGGLE),
        ProxyField("pluginMux", "plugin-opts.mux", "Mux", ProxyFieldKind.TOGGLE),
        ProxyField("pluginSkipCertVerify", "plugin-opts.skip-cert-verify", "跳过证书验证", ProxyFieldKind.TOGGLE),
        ProxyField("udpOverTcp", "udp-over-tcp", "UDP over TCP", ProxyFieldKind.TOGGLE),
        ProxyField("protocolParam", "protocol-param", "协议参数", ProxyFieldKind.TEXT),
        ProxyField("obfsParam", "obfs-param", "混淆参数", ProxyFieldKind.TEXT),
        ProxyField("obfsPassword", "obfs-password", "混淆密码", ProxyFieldKind.SECRET),
        ProxyField("obfsSecret", "obfs", "混淆密码", ProxyFieldKind.SECRET),
        ProxyField("obfsMode", "obfs-opts.mode", "混淆模式", ProxyFieldKind.CHOICE),
        ProxyField("obfsHost", "obfs-opts.host", "混淆主机", ProxyFieldKind.TEXT),
        ProxyField("version", "version", "协议版本", ProxyFieldKind.NUMBER),
        ProxyField("ports", "ports", "端口跳跃", ProxyFieldKind.TEXT),
        ProxyField("hopInterval", "hop-interval", "跳跃间隔", ProxyFieldKind.TEXT),
        ProxyField("congestionController", "congestion-controller", "拥塞控制", ProxyFieldKind.CHOICE),
        ProxyField("udpRelayMode", "udp-relay-mode", "UDP 转发模式", ProxyFieldKind.CHOICE),
        ProxyField("reduceRtt", "reduce-rtt", "0-RTT", ProxyFieldKind.TOGGLE),
        ProxyField("ip", "ip", "IPv4", ProxyFieldKind.TEXT),
        ProxyField("ipv6", "ipv6", "IPv6", ProxyFieldKind.TEXT),
        ProxyField("allowedIps", "allowed-ips", "允许的 IP", ProxyFieldKind.LIST),
        ProxyField("dns", "dns", "DNS", ProxyFieldKind.LIST),
        ProxyField("remoteDnsResolve", "remote-dns-resolve", "远程 DNS 解析", ProxyFieldKind.TOGGLE),
        ProxyField("mtu", "mtu", "MTU", ProxyFieldKind.NUMBER),
        ProxyField("tfo", "tfo", "TCP Fast Open", ProxyFieldKind.TOGGLE),
        ProxyField("mptcp", "mptcp", "MPTCP", ProxyFieldKind.TOGGLE),
        ProxyField("interfaceName", "interface-name", "网络接口", ProxyFieldKind.TEXT),
        ProxyField("routingMark", "routing-mark", "路由标记", ProxyFieldKind.NUMBER),
        ProxyField("ipVersion", "ip-version", "IP 版本", ProxyFieldKind.CHOICE),
    )
    fun decode(text: String?): List<LocalProxy> {
        if (text == null) return emptyList()
        require(text.toByteArray().size <= 4 * 1024 * 1024) { "本地代理目录超过 4 MiB" }
        val rows = JsonParser.parseString(text).asJsonArray.map { value ->
            val row = value.asJsonObject
            LocalProxy(row.get("id").asString, row.get("definition").asJsonObject.toString())
        }
        require(rows.size <= 4096 && rows.map { it.id }.distinct().size == rows.size) { "本地代理目录无效" }
        return rows
    }
    fun encode(rows: List<LocalProxy>): String = JsonArray().apply {
        rows.forEach { row -> add(JsonObject().apply { addProperty("id", row.id); add("definition", row.fields) }) }
    }.toString().also { decode(it) }
    fun validate(rows: List<LocalProxy>, groups: Set<String> = emptySet()) {
        encode(rows)
        val names = mutableSetOf<String>()
        rows.forEach {
            require(it.name.isNotBlank() && it.name.length <= 256 && !it.name.contains('\n') && !it.name.contains(',')) { "节点名称不能为空或包含逗号/换行" }
            require(it.name !in reserved && names.add(it.name) && it.name !in groups) { "节点名称与保留名称、其他节点或策略组重复" }
            require(it.type.isNotBlank()) { "请选择代理类型" }
        }
    }
    fun value(root: JsonObject, path: String): JsonElement? {
        var value: JsonElement = root
        path.split('.').forEach { key ->
            val map = value.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            value = map.get(map.keySet().firstOrNull { it.equals(key, true) } ?: key) ?: return null
        }
        return value
    }
    fun withValue(row: LocalProxy, path: String, value: JsonElement?): LocalProxy {
        val root = row.fields
        fun write(map: JsonObject, keys: List<String>) {
            val key = map.keySet().firstOrNull { it.equals(keys.first(), true) } ?: keys.first()
            if (keys.size == 1) {
                if (value == null || value.isJsonNull || value.isJsonPrimitive && value.asString.isEmpty()) map.remove(key) else map.add(key, value)
            } else {
                val child = map.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                write(child, keys.drop(1))
                if (child.size() == 0) map.remove(key) else map.add(key, child)
            }
        }
        write(root, path.split('.')); return row.copy(json = root.toString())
    }
    fun required(type: String): List<String> = when (type) {
        "ss" -> listOf("cipher", "password")
        "ssr" -> listOf("cipher", "protocol", "obfs", "password")
        "vmess" -> listOf("cipher", "alterId", "uuid")
        "vless" -> listOf("uuid")
        "tuic" -> listOf("uuid", "password")
        "trojan", "anytls", "hysteria2" -> listOf("password")
        "hysteria" -> listOf("up", "down", "authStr")
        "snell" -> listOf("psk")
        "sudoku" -> listOf("key")
        "wireguard", "masque" -> listOf("privateKey", "publicKey")
        "mieru" -> listOf("transport", "username", "password")
        "socks5", "http", "ssh", "shadowquic", "trusttunnel", "gost-relay", "openvpn" -> listOf("username", "password")
        else -> emptyList()
    }
    fun referenced(source: String): Set<String> {
        val root = ProxyGroupDrafts.root(source)
        val names = ProxyGroupDrafts.read(source).flatMap { it.strings("proxies") + listOf(it.text("default-selected"), it.text("empty-fallback")) }.toMutableSet()
        fun rules(value: JsonElement?) {
            if (value?.isJsonArray == true) value.asJsonArray.forEach { rule ->
                if (rule.isJsonPrimitive && rule.asJsonPrimitive.isString && !rule.asString.startsWith("SUB-RULE,"))
                    runCatching { CustomRules.fields(rule.asString).target }.getOrNull()?.let(names::add)
            }
        }
        rules(root.get("rules"))
        root.get("sub-rules")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { rules(it.value) }
        fun dialers(value: JsonElement?) {
            if (value?.isJsonObject == true) value.asJsonObject.entrySet().forEach { (key, child) ->
                if (key == "dialer-proxy" && child.isJsonPrimitive && child.asJsonPrimitive.isString) names += child.asString
                else dialers(child)
            } else if (value?.isJsonArray == true) value.asJsonArray.forEach(::dialers)
        }
        dialers(root)
        return names
    }
    fun project(source: String, rows: List<LocalProxy>, dialers: Map<String, String>? = null): String {
        // 作者 long：规则目标和Dialer同样能引用全局节点，仅看组成员会使合法导入在实际应用时丢掉出站。
        val named = referenced(source) + dialers.orEmpty().keys + dialers.orEmpty().values
        val selected = rows.filter { it.name in named }
        if (selected.isEmpty()) return source
        val root = ProxyGroupDrafts.root(source)
        val proxies = root.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val existing = proxies.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.asString }.toSet()
        // 作者 long：旧 CUSTOM 的内联节点拥有该配置的名称；新增同名全局节点不能改变旧配置的出站或阻止其加载。
        selected.filterNot { it.name in existing }.forEach { proxies.add(it.fields.apply { remove("dialer-proxy") }) }
        root.add("proxies", proxies)
        return YamlConfigCodec.toYaml(root.toString())
    }
    /** 作者 long：成员候选需要全局节点，但不把候选写进源配置；真正运行时只投影显式引用。 */
    fun candidates(source: String, rows: List<LocalProxy>): String {
        val root = ProxyGroupDrafts.root(source)
        val proxies = root.get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val names = proxies.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.asString }.toMutableSet()
        rows.forEach { if (names.add(it.name)) proxies.add(it.fields) }
        root.add("proxies", proxies); return YamlConfigCodec.toYaml(root.toString())
    }
}
