package com.lonnnnnng.flclash.data

/** 作者 long：可选项跟随协议、传输和插件展开，未添加的键继续使用核心默认值。 */
object LocalProxyFields {
    fun offered(row: LocalProxy, field: ProxyField): Boolean {
        val type = row.type
        val id = field.id
        if (id in LocalProxies.required(type)) return true
        fun isType(vararg types: String) = type in types
        val network = LocalProxies.value(row.fields, "network")?.asString
        return when {
            id in setOf("username", "password", "uuid", "psk", "key", "publicKey", "authStr", "cipher", "alterId", "transport") -> false
            id in setOf("privateKey", "privateKeyPassphrase") -> type == "ssh"
            id in setOf("preSharedKey", "allowedIps") -> type == "wireguard"
            id in setOf("obfs", "up", "down") -> type == "hysteria2"
            id == "obfsPassword" -> type == "hysteria2" && LocalProxies.value(row.fields, "obfs") != null
            id in setOf("protocol", "obfsSecret") -> type == "hysteria"
            id == "network" -> isType("vmess", "vless", "trojan")
            id == "flow" -> type == "vless"
            id.startsWith("ws") -> network == "ws" && (id != "wsHttpUpgradeFastOpen" || LocalProxies.value(row.fields, "ws-opts.v2ray-http-upgrade")?.asBoolean == true)
            id.startsWith("http") -> network == "http"
            id.startsWith("h2") -> network == "h2"
            id.startsWith("grpc") -> network == "grpc"
            id.startsWith("xhttp") -> network == "xhttp"
            id.startsWith("mkcp") -> network == "mkcp"
            id == "mekyaUrl" -> network == "mekya"
            id == "headers" -> type == "http"
            id == "tls" -> isType("socks5", "http", "vmess", "vless", "gost-relay")
            id == "sni" -> isType("http", "trojan", "hysteria", "hysteria2", "tuic", "shadowquic", "gost-relay", "anytls", "masque", "trusttunnel")
            id == "servername" -> isType("vmess", "vless")
            id == "alpn" -> isType("vmess", "vless", "trojan", "hysteria", "hysteria2", "tuic", "shadowquic", "anytls", "trusttunnel")
            id == "skipCertVerify" -> isType("socks5", "http", "vmess", "vless", "trojan", "hysteria", "hysteria2", "tuic", "gost-relay", "anytls", "masque", "trusttunnel")
            id == "clientFingerprint" -> isType("ss", "vmess", "vless", "snell", "trojan", "gost-relay", "anytls", "trusttunnel")
            id.startsWith("reality") -> isType("vmess", "vless", "trojan")
            id in setOf("plugin", "udpOverTcp") -> type == "ss"
            id.startsWith("plugin") -> type == "ss" && pluginKeys(LocalProxies.value(row.fields, "plugin")?.asString.orEmpty()).contains(field.path.substringAfterLast('.'))
            id in setOf("protocolParam", "obfsParam") -> type == "ssr"
            id in setOf("obfsMode", "obfsHost", "version") -> type == "snell"
            id in setOf("ports", "hopInterval") -> isType("hysteria", "hysteria2")
            id == "congestionController" -> isType("tuic", "shadowquic", "masque", "trusttunnel")
            id in setOf("udpRelayMode", "reduceRtt") -> type == "tuic"
            id in setOf("ip", "ipv6", "dns", "remoteDnsResolve", "mtu") -> isType("wireguard", "masque")
            id in setOf("tfo", "mptcp") -> !isType("dns", "reject", "rematch", "hysteria", "hysteria2", "tuic", "wireguard", "shadowquic", "masque", "tailscale", "zerotier", "easytier")
            id in setOf("interfaceName", "routingMark", "ipVersion") -> !isType("dns", "reject", "rematch")
            else -> false
        }
    }
    private fun pluginKeys(plugin: String): Set<String> = when (plugin) {
        "obfs" -> setOf("mode", "host")
        "v2ray-plugin", "gost-plugin" -> setOf("mode", "host", "path", "tls", "mux", "skip-cert-verify")
        "kcptun" -> setOf("mode", "key", "crypt")
        "shadow-tls" -> setOf("host", "password", "version", "skip-cert-verify")
        "restls" -> setOf("host", "password", "version-hint", "restls-script", "skip-cert-verify")
        "jls" -> setOf("host", "username", "password")
        else -> emptySet()
    }
    fun choices(row: LocalProxy, field: ProxyField): List<String> = when (field.id) {
        "cipher" -> when (row.type) {
            "vmess" -> "auto aes-128-gcm chacha20-poly1305 aes-128-cfb none zero"
            "ssr" -> "aes-128-ctr aes-192-ctr aes-256-ctr aes-128-cfb aes-192-cfb aes-256-cfb rc4-md5 chacha20-ietf xchacha20 chacha20 none"
            else -> "aes-128-gcm aes-192-gcm aes-256-gcm chacha20-ietf-poly1305 xchacha20-ietf-poly1305 2022-blake3-aes-128-gcm 2022-blake3-aes-256-gcm 2022-blake3-chacha20-poly1305 aes-128-ctr aes-192-ctr aes-256-ctr aes-128-cfb aes-192-cfb aes-256-cfb rc4-md5 chacha20-ietf xchacha20 chacha20 none"
        }
        "protocol" -> if (row.type == "ssr") "origin auth_sha1_v4 auth_aes128_md5 auth_aes128_sha1 auth_chain_a auth_chain_b" else "udp wechat-video faketcp"
        "obfs" -> if (row.type == "ssr") "plain http_simple http_post random_head tls1.2_ticket_auth tls1.2_ticket_fastauth" else "salamander gecko"
        "transport" -> "TCP UDP"
        "network" -> when (row.type) { "vmess" -> "tcp ws http h2 grpc mekya mkcp"; "vless" -> "tcp ws http h2 grpc xhttp"; else -> "tcp ws grpc" }
        "flow" -> "xtls-rprx-vision"
        "clientFingerprint" -> "chrome firefox safari ios android edge 360 qq random chrome120 firefox120 safari16 none"
        "plugin" -> "obfs v2ray-plugin gost-plugin shadow-tls restls jls kcptun"
        "pluginVersionHint" -> "tls13 tls12"
        "pluginCrypt" -> "aes aes-128 aes-192 aes-128-gcm salsa20 blowfish twofish cast5 3des tea xtea xor none null"
        "mkcpHeader" -> "none srtp utp wechat-video dtls wireguard"
        "obfsMode" -> "http tls shadow-tls restls jls"
        "congestionController" -> "cubic new_reno bbr bbr_meta_v1 bbr_meta_v2"
        "udpRelayMode" -> "native quic"
        "ipVersion" -> "dual ipv4 ipv6 ipv4-prefer ipv6-prefer"
        else -> ""
    }.split(' ').filter(String::isNotEmpty)
    fun changeType(row: LocalProxy, type: String): LocalProxy {
        if (type == row.type) return row
        var next = LocalProxies.withValue(row, "type", com.google.gson.JsonPrimitive(type))
        LocalProxies.catalog.forEach { field ->
            val value = LocalProxies.value(next.fields, field.path)
            if (value != null && (!offered(next, field) || field.kind == ProxyFieldKind.CHOICE && value.asString !in choices(next, field))) next = LocalProxies.withValue(next, field.path, null)
        }
        if (type == "vmess") {
            next = LocalProxies.withValue(next, "cipher", com.google.gson.JsonPrimitive("auto"))
            next = LocalProxies.withValue(next, "alterId", com.google.gson.JsonPrimitive(0))
        }
        if (type == "mieru") next = LocalProxies.withValue(next, "transport", com.google.gson.JsonPrimitive("TCP"))
        return next
    }
    fun change(row: LocalProxy, field: ProxyField, value: com.google.gson.JsonElement?): LocalProxy {
        var next = LocalProxies.withValue(row, field.path, value)
        LocalProxies.catalog.filter { offered(row, it) && !offered(next, it) }.forEach { next = LocalProxies.withValue(next, it.path, null) }
        return next
    }
}
