package com.lonnnnnng.flclash.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.math.BigInteger
import java.net.InetAddress

/** 作者 long：Android 网络参数独立于订阅保存，切换配置时仍沿用用户的接管和路由选择。 */
data class NetworkSettings(
    val systemProxy: Boolean = true,
    val bypassDomain: List<String> = DEFAULT_BYPASS_DOMAINS,
    val allowBypass: Boolean = true,
    val ipv6: Boolean = false,
    val dnsHijacking: Boolean = false,
    val stack: String = "mips",
    val mtu: Int = 9000,
    val congestionController: String = "cubic",
    val bypassPrivateRoute: Boolean = true,
    val routeAddress: List<String> = emptyList(),
    val routeExcludeAddress: List<String> = emptyList(),
) {
    fun validated(): NetworkSettings {
        require(stack in listOf("gvisor", "system", "mixed", "mips")) { "请选择有效的协议栈" }
        require(mtu in 1280..65535) { "MTU 必须介于 1280 和 65535" }
        require(congestionController in listOf("cubic", "reno", "bbr", "bbr3")) { "请选择有效的拥塞控制算法" }
        require(bypassDomain.size <= 128) { "绕过域名最多 128 项" }
        require(bypassDomain.all { it.isNotBlank() && it.length <= 253 && it.all { c -> c.isLetterOrDigit() || c in ".-_*" } }) {
            "绕过域名只能包含域名、IP 或通配符，每项最多 253 字符"
        }
        for (routes in listOf(routeAddress, routeExcludeAddress)) {
            require(routes.size <= 256) { "路由地址最多 256 项" }
            routes.forEach(::parseNetworkCidr)
        }
        return copy(bypassDomain = bypassDomain.distinct(), routeAddress = routeAddress.distinct(), routeExcludeAddress = routeExcludeAddress.distinct())
    }

    companion object {
        val DEFAULT_BYPASS_DOMAINS = listOf("*zhihu.com", "*zhimg.com", "*jd.com", "100ime-iat-api.xfyun.cn",
            "*360buyimg.com", "localhost", "*.local", "127.*", "10.*", "172.16.*", "172.17.*", "172.18.*",
            "172.19.*", "172.2*", "172.30.*", "172.31.*", "192.168.*")
        val PRIVATE_ROUTES = listOf("0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "255.255.255.255/32", "fc00::/7", "fe80::/10", "ff00::/8")
    }
}

interface NetworkSettingsStore {
    fun read(): NetworkSettings
    fun write(value: NetworkSettings)
}

class SharedPreferencesNetworkSettingsStore(context: Context) : NetworkSettingsStore {
    private val preferences = com.lonnnnnng.flclash.common.auditedPreferences(context.applicationContext, "network_settings")
    private val gson = Gson()
    override fun read(): NetworkSettings = preferences.getString("settings", null)?.let {
        // 作者 long：保存损坏不能悄悄重置接管范围，否则界面与实际流量路径会不一致。
        gson.fromJson(it, NetworkSettings::class.java).validated()
    } ?: NetworkSettings()
    override fun write(value: NetworkSettings) {
        check(preferences.edit().putString("settings", gson.toJson(value.validated())).commit()) { "无法保存网络设置" }
    }
}

internal data class NetworkCidr(val start: BigInteger, val prefix: Int, val bits: Int) {
    val end: BigInteger get() = start + BigInteger.ONE.shiftLeft(bits - prefix) - BigInteger.ONE
    fun text(): String {
        val source = start.toByteArray()
        val bytes = ByteArray(bits / 8)
        val count = minOf(source.size, bytes.size)
        source.copyInto(bytes, bytes.size - count, source.size - count)
        return "${InetAddress.getByAddress(bytes).hostAddress}/$prefix"
    }
}

internal fun parseNetworkCidr(value: String): NetworkCidr {
    val parts = value.split('/')
    require(parts.size == 2 && parts[0].length <= 45) { "请输入 IP/前缀长度格式的路由地址" }
    val ip = parts[0]
    // 作者 long：路由输入只接受数字 IP，不能让保存设置发起域名解析或接受主机名。
    require(if (':' in ip) ip.all { it in "0123456789abcdefABCDEF:." } else {
        val octets = ip.split('.')
        octets.size == 4 && octets.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) in 0..255 }
    }) { "路由地址必须是数字 IPv4 或 IPv6" }
    val address = try { InetAddress.getByName(ip) } catch (_: Exception) { error("路由 IP 地址无效") }
    val bits = address.address.size * 8
    val prefix = parts[1].toIntOrNull()
    require(prefix != null && prefix in 0..bits) { "路由前缀长度无效" }
    val hostBits = bits - prefix
    return NetworkCidr(BigInteger(1, address.address).shiftRight(hostBits).shiftLeft(hostBits), prefix, bits)
}

/** 作者 long：Android Builder 没有低版本通用的排除路由接口，像 Flutter 一样先求 CIDR 差集，再交付正向路由。 */
internal fun NetworkSettings.vpnRoutes(): List<String> {
    val excluded = (routeExcludeAddress + if (bypassPrivateRoute) NetworkSettings.PRIVATE_ROUTES else emptyList()).map(::parseNetworkCidr)
    if (routeAddress.isEmpty() && excluded.isEmpty()) return emptyList()
    var routes = (routeAddress.ifEmpty { listOf("0.0.0.0/0", "::/0") }).map(::parseNetworkCidr)
    for (cut in excluded) {
        fun subtract(route: NetworkCidr): List<NetworkCidr> {
            if (route.bits != cut.bits || route.end < cut.start || route.start > cut.end) return listOf(route)
            if (cut.start <= route.start && cut.end >= route.end) return emptyList()
            val prefix = route.prefix + 1
            val half = BigInteger.ONE.shiftLeft(route.bits - prefix)
            return subtract(route.copy(prefix = prefix)) + subtract(NetworkCidr(route.start + half, prefix, route.bits))
        }
        routes = routes.flatMap(::subtract)
        require(routes.size <= 8192) { "排除后路由过多，请简化路由地址" }
    }
    return routes.distinct().map(NetworkCidr::text)
}

internal fun patchNetworkSettings(yaml: String, settings: NetworkSettings?): String {
    if (settings == null) return yaml
    val root = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
    val tun = root.get("tun")?.takeIf { it.isJsonObject }?.asJsonObject ?: com.google.gson.JsonObject().also { root.add("tun", it) }
    // 作者 long：只修改运行副本，保留订阅原文、DNS 劫持基线和其他未在网络页编辑的 TUN 字段。
    tun.addProperty("stack", settings.stack)
    tun.addProperty("mtu", settings.mtu)
    tun.addProperty("congestion-controller", settings.congestionController)
    tun.add("route-address", Gson().toJsonTree(settings.routeAddress))
    tun.add("route-exclude-address", Gson().toJsonTree(settings.routeExcludeAddress + if (settings.bypassPrivateRoute) NetworkSettings.PRIVATE_ROUTES else emptyList()))
    return YamlConfigCodec.toYaml(root.toString())
}
