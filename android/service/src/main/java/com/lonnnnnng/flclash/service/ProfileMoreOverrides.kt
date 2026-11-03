package com.lonnnnnng.flclash.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/** 作者 long：null 表示继承源配置，false、空文本和空列表均是明确覆盖，清除选择才能恢复继承。 */
data class ProfileNtpOverrides(
    val enable: Boolean? = null,
    val server: String? = null,
    val port: Int? = null,
    val interval: Int? = null,
    @SerializedName("dialer-proxy") val dialerProxy: String? = null,
    @SerializedName("write-to-system") val writeToSystem: Boolean? = null,
) {
    val isEmpty get() = toJson().size() == 0
    fun toJson(): JsonObject = Gson().toJsonTree(this).asJsonObject
    companion object {
        fun fromJson(json: JsonObject): ProfileNtpOverrides = Gson().fromJson(json, ProfileNtpOverrides::class.java)
    }
}

data class ProfileSniffProtocolOverrides(
    val ports: List<String> = emptyList(),
    @SerializedName("override-destination") val overrideDestination: Boolean? = null,
)

data class ProfileSnifferOverrides(
    val enable: Boolean? = null,
    @SerializedName("override-destination") val overrideDestination: Boolean? = null,
    @SerializedName("force-dns-mapping") val forceDnsMapping: Boolean? = null,
    @SerializedName("parse-pure-ip") val parsePureIp: Boolean? = null,
    @SerializedName("force-domain") val forceDomain: List<String>? = null,
    @SerializedName("skip-domain") val skipDomain: List<String>? = null,
    @SerializedName("skip-src-address") val skipSrcAddress: List<String>? = null,
    @SerializedName("skip-dst-address") val skipDstAddress: List<String>? = null,
    val sniff: Map<String, ProfileSniffProtocolOverrides>? = null,
) {
    val isEmpty get() = toJson().size() == 0
    fun toJson(): JsonObject = Gson().toJsonTree(this).asJsonObject
    companion object {
        fun fromJson(json: JsonObject): ProfileSnifferOverrides = Gson().fromJson(json, ProfileSnifferOverrides::class.java)
    }
}

/** 作者 long：更多设置在脚本与 DNS 覆盖之后合并；未选择的字段和未知扩展字段留在暂存正文中。 */
object ProfileMoreOverrideApplier {
    fun apply(yaml: String, ntp: ProfileNtpOverrides, sniffer: ProfileSnifferOverrides): String {
        if (ntp.isEmpty && sniffer.isEmpty) return yaml
        val root = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
        fun merge(name: String, fields: JsonObject) {
            if (fields.size() == 0) return
            val section = root.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { root.add(name, it) }
            fields.entrySet().forEach { (key, value) ->
                if (name == "sniffer" && key == "sniff" && value.isJsonObject && value.asJsonObject.size() > 0) {
                    // 作者 long：选中一个协议只替换该协议；其他协议继续继承，空 map 则明确清空全部协议。
                    val protocols = section.get("sniff")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: JsonObject().also { section.add("sniff", it) }
                    // 作者 long：旧 sniffing 仅在 sniff 为空时生效；先迁入旧协议，防止新增一个协议后让其他旧协议失效。
                    if (protocols.size() == 0) {
                        val legacy = section.get("sniffing")?.takeIf { it.isJsonArray }?.asJsonArray
                        val ports = section.get("port-whitelist")?.takeIf { it.isJsonArray }
                        legacy?.forEach { protocol ->
                            if (protocol.isJsonPrimitive && protocol.asJsonPrimitive.isString) {
                                protocols.add(protocol.asString.uppercase(java.util.Locale.ROOT), JsonObject().apply {
                                    if (ports != null) add("ports", ports.deepCopy())
                                })
                            }
                        }
                    }
                    value.asJsonObject.entrySet().forEach { (protocol, config) ->
                        // 作者 long：Mihomo 与 Flutter 按不区分大小写的协议名匹配；替换 HTTP 时不能留下另一份 http 抢占旧设置。
                        val name = protocol.uppercase(java.util.Locale.ROOT)
                        val aliases = protocols.keySet().filter { it.equals(name, ignoreCase = true) }
                        val entry = aliases.firstOrNull()?.let { protocols.get(it) }?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()
                        aliases.forEach(protocols::remove)
                        protocols.add(name, entry)
                        // 作者 long：默认目标继承全局嗅探设置；清掉协议原有的独立目标开关，但保留未知协议参数。
                        entry.remove("override-destination")
                        config.asJsonObject.entrySet().forEach { (key, field) -> entry.add(key, field.deepCopy()) }
                    }
                } else {
                    section.add(key, value.deepCopy())
                    // 作者 long：明确清空协议时也屏蔽旧 sniffing，否则核心会在空 sniff 下再次启用旧列表。
                    if (name == "sniffer" && key == "sniff" && value.isJsonObject && value.asJsonObject.size() == 0) {
                        section.add("sniffing", com.google.gson.JsonArray())
                    }
                }
            }
        }
        merge("ntp", ntp.toJson())
        merge("sniffer", sniffer.toJson())
        return YamlConfigCodec.toYaml(root.toString())
    }
}
