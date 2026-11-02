package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.CoreIpResponse
import java.net.InetAddress

enum class IpType(val label: String) {
    RESIDENTIAL("住宅"), MOBILE("移动网络"), BUSINESS("企业"), HOSTING("数据中心");
}

enum class IpQualitySource(val label: String) {
    IDENT_ME("ident.me"), IP_API_COM("ip-api.com"), IP_QUERY("ipquery.io"),
    IP_LOCATE("iplocate.io"), IP_API_IS("ipapi.is"), PROXY_CHECK("proxycheck.io");

    fun url(ip: String): String {
        require(isPublicIpLiteral(ip)) { "IP 地址无效" }
        return when (this) {
            IDENT_ME -> "https://ident.me/json"
            IP_API_COM -> "http://ip-api.com/json/$ip?fields=status,isp,org,as,mobile,proxy,hosting"
            IP_QUERY -> "https://api.ipquery.io/$ip?format=json"
            IP_LOCATE -> "https://iplocate.io/api/lookup/$ip"
            IP_API_IS -> "https://api.ipapi.is/?q=$ip"
            PROXY_CHECK -> "https://proxycheck.io/v3/$ip"
        }
    }
}

enum class IpSourceFailure(val label: String) {
    NO_TYPE("未返回类型"), TIMEOUT("查询超时"), RATE_LIMITED("请求受限"),
    FAILED("查询失败"), IP_MISMATCH("IP 不一致");
}

data class OutboundIpInfo(val ip: String, val countryCode: String)

data class IpQuality(
    val ip: String,
    val source: IpQualitySource,
    val type: IpType,
    val organization: String? = null,
    val asn: Long? = null,
    val isProxy: Boolean = false,
    val isVpn: Boolean = false,
    val isTor: Boolean = false,
    val isAbuser: Boolean = false,
) {
    // 作者 long：沿用 Flutter 的等级语义；仅 proxy/VPN 标记不自动降低等级，Tor 与滥用标记优先。
    val levelLabel: String get() = when {
        isTor || isAbuser -> "高风险"
        type == IpType.HOSTING -> "一般"
        else -> "良好"
    }
    val flags: List<String> get() = listOfNotNull(
        "Tor".takeIf { isTor }, "滥用".takeIf { isAbuser },
        "VPN".takeIf { isVpn }, "代理".takeIf { isProxy },
    )
}

data class IpQualityAnswer(val quality: IpQuality, val inferred: Boolean = false)
class IpSourceException(val failure: IpSourceFailure) : Exception(failure.label)
class IpQualityException(val failures: Map<IpQualitySource, IpSourceFailure>) : Exception("IP 类型查询失败")

internal fun isPublicIpLiteral(ip: String): Boolean {
    // 作者 long：先限定字面地址字符，不能让服务返回的主机名触发 DNS 或进入 URL 路径。
    if (ip.length !in 3..45 || !ip.matches(Regex("[0-9A-Fa-f:.]+"))) return false
    if (':' !in ip && !ip.matches(Regex("""(?:[0-9]{1,3}\.){3}[0-9]{1,3}"""))) return false
    // 作者 long：InetAddress 可能把越界数字当主机名解析，必须先确认四段数值，禁止查询触发额外 DNS。
    if (':' !in ip && ip.split('.').any { it.toIntOrNull() !in 0..255 || (it.length > 1 && it.startsWith('0')) }) return false
    val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return false
    val bytes = address.address.map { it.toInt() and 255 }
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) return false
    return if (bytes.size == 4) {
        bytes[0] != 0 && bytes[0] < 224 && !(bytes[0] == 100 && bytes[1] in 64..127)
    } else (bytes[0] and 0xfe) != 0xfc
}

internal val outboundIpSources = listOf(
    "https://www.cloudflare.com/cdn-cgi/trace", "https://api.ip.sb/geoip",
    "https://ipinfo.io/json", "https://ipwho.is", "https://get.geojs.io/v1/ip/geo.json",
    "https://api.country.is", "https://api.ipquery.io/?format=json", "https://ident.me/json",
)

internal fun parseOutboundIp(response: CoreIpResponse): OutboundIpInfo {
    require(response.error.isBlank() && response.statusCode == 200 && response.sourceUrl in outboundIpSources) { "出口 IP 查询失败" }
    val ip: String?
    val country: String?
    if (response.sourceUrl == outboundIpSources.first()) {
        val fields = response.body.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index > 0) line.take(index) to line.drop(index + 1).trim() else null
        }.toMap()
        ip = fields["ip"]
        country = fields["loc"]
    } else {
        val json = JsonParser.parseString(response.body).asJsonObject
        ip = json.text("ip")
        country = when (response.sourceUrl) {
            outboundIpSources[2], outboundIpSources[5] -> json.text("country")
            outboundIpSources[6] -> json.obj("location").text("country_code")
            outboundIpSources[7] -> json.text("cc")
            else -> json.text("country_code")
        }
    }
    require(ip != null && isPublicIpLiteral(ip)) { "出口服务未返回有效公共 IP" }
    require(country != null && country.matches(Regex("[A-Za-z]{2}"))) { "出口服务未返回国家代码" }
    return OutboundIpInfo(ip, country.uppercase(java.util.Locale.ROOT))
}

internal fun parseIpQuality(source: IpQualitySource, ip: String, response: CoreIpResponse): IpQualityAnswer {
    if (response.error.isNotBlank()) throw IpSourceException(
        if (response.error == "timeout") IpSourceFailure.TIMEOUT else IpSourceFailure.FAILED,
    )
    if (response.statusCode == 429) throw IpSourceException(IpSourceFailure.RATE_LIMITED)
    if (response.statusCode != 200) throw IpSourceException(IpSourceFailure.FAILED)
    val json = JsonParser.parseString(response.body).asJsonObject
    // 作者 long：质量必须归属于点击的 IP，响应明确返回另一地址时不把另一出口的类型写入缓存。
    val returnedIp = json.text("ip") ?: json.text("query")
    if ((source == IpQualitySource.IDENT_ME && returnedIp == null) ||
        (returnedIp != null && returnedIp != ip)
    ) throw IpSourceException(IpSourceFailure.IP_MISMATCH)
    var inferred = false
    var organization: String? = null
    var asn: Long? = null
    var proxy = false
    var vpn = false
    var tor = false
    var abuser = false
    val type = when (source) {
        IpQualitySource.IDENT_ME -> {
            organization = json.text("aso"); asn = json.asn("asn")
            declaredType(json.text("type"))
        }
        IpQualitySource.IP_API_COM -> {
            if (json.text("status") != "success") throw IpSourceException(IpSourceFailure.FAILED)
            organization = json.text("org") ?: json.text("isp"); asn = json.asn("as")
            proxy = json.flag("proxy")
            pickType(json.flag("hosting"), json.flag("mobile")).also { inferred = it == null } ?: IpType.RESIDENTIAL
        }
        IpQualitySource.IP_QUERY -> {
            val risk = json.obj("risk"); val isp = json.obj("isp")
            // 作者 long：缺失整个 risk/isp 对象是无效响应，不能把空 JSON 推断成“住宅、良好”。
            if (risk.size() == 0 || isp.size() == 0) throw IpSourceException(IpSourceFailure.NO_TYPE)
            organization = isp.text("org") ?: isp.text("isp"); asn = isp.asn("asn")
            proxy = risk.flag("is_proxy"); vpn = risk.flag("is_vpn"); tor = risk.flag("is_tor")
            pickType(risk.flag("is_datacenter"), risk.flag("is_mobile")).also { inferred = it == null } ?: IpType.RESIDENTIAL
        }
        IpQualitySource.IP_LOCATE, IpQualitySource.IP_API_IS -> {
            val privacy = if (source == IpQualitySource.IP_LOCATE) json.obj("privacy") else json
            val company = json.obj("company"); val network = json.obj("asn")
            organization = company.text("name") ?: network.text(if (source == IpQualitySource.IP_LOCATE) "name" else "org")
            asn = network.asn("asn")
            proxy = privacy.flag("is_proxy"); vpn = privacy.flag("is_vpn")
            tor = privacy.flag("is_tor"); abuser = privacy.flag("is_abuser")
            pickType(
                privacy.flag(if (source == IpQualitySource.IP_LOCATE) "is_hosting" else "is_datacenter"),
                privacy.flag("is_mobile"), declaredType(company.text("type")) ?: declaredType(network.text("type")),
            )
        }
        IpQualitySource.PROXY_CHECK -> {
            if (json.text("status") == "denied") throw IpSourceException(IpSourceFailure.RATE_LIMITED)
            if (json.text("status") !in listOf("ok", "warning")) throw IpSourceException(IpSourceFailure.FAILED)
            val result = json.obj(ip); val network = result.obj("network"); val detections = result.obj("detections")
            organization = network.text("organisation"); asn = network.asn("asn")
            proxy = detections.flag("proxy"); vpn = detections.flag("vpn"); tor = detections.flag("tor")
            pickType(detections.flag("hosting") || network.text("type") == "Hosting", network.text("type") == "Wireless",
                when (network.text("type")) { "Residential" -> IpType.RESIDENTIAL; "Business" -> IpType.BUSINESS; else -> null })
        }
    } ?: throw IpSourceException(IpSourceFailure.NO_TYPE)
    return IpQualityAnswer(IpQuality(ip, source, type, organization, asn, proxy, vpn, tor, abuser), inferred)
}

private fun declaredType(value: String?): IpType? = when (value) {
    "isp" -> IpType.RESIDENTIAL
    "business", "education", "government", "banking" -> IpType.BUSINESS
    "hosting" -> IpType.HOSTING
    else -> null
}
private fun pickType(hosting: Boolean, mobile: Boolean = false, declared: IpType? = null): IpType? = when {
    hosting || declared == IpType.HOSTING -> IpType.HOSTING
    mobile -> IpType.MOBILE
    else -> declared
}
private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString?.trim()?.takeIf { it.isNotBlank() }?.take(256)
private fun JsonObject.obj(key: String): JsonObject = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
private fun JsonObject.flag(key: String): Boolean = get(key)?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
private fun JsonObject.asn(key: String): Long? {
    val value = get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: return null
    return Regex("[0-9]+").find(value)?.value?.toLongOrNull()?.takeIf { it > 0 }
}
