package com.lonnnnnng.flclash.service

import java.net.URI
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class GeoResourceType(val fileName: String, val defaultUrl: String) {
    MMDB("GEOIP.metadb", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb"),
    ASN("ASN.mmdb", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb"),
    GEOIP("GEOIP.dat", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat"),
    GEOSITE("GEOSITE.dat", "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat"),
}

data class GeoResourceSettings(
    val autoUpdate: Boolean = false,
    val intervalHours: Int = 24,
    val urls: Map<GeoResourceType, String> = GeoResourceType.entries.associateWith { it.defaultUrl },
) {
    fun validated(): GeoResourceSettings {
        require(intervalHours in 1..87600) { "更新周期须为 1 至 87600 小时" }
        val normalized = GeoResourceType.entries.associateWith { type ->
            (urls[type] ?: type.defaultUrl).trim().also { value ->
                val uri = URI(value)
                require(value.length <= 8192 && uri.scheme in listOf("http", "https") &&
                    !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.fragment == null) {
                    "请输入有效的 HTTP 或 HTTPS 地址"
                }
            }
        }
        return copy(urls = normalized)
    }
}

interface GeoResourceSettingsStore {
    val changes: kotlinx.coroutines.flow.Flow<GeoResourceSettings>? get() = null
    fun read(): GeoResourceSettings
    fun write(settings: GeoResourceSettings)
}

/** 作者 long：Geo 与 Flutter 的全局补丁一致，只进入运行输入，不能改写订阅原文件。 */
object GeoResourceSettingsApplier {
    fun apply(content: String, settings: GeoResourceSettings): String {
        val validated = settings.validated()
        val root = JsonParser.parseString(YamlConfigCodec.toJson(content)).asJsonObject
        root.addProperty("geo-auto-update", validated.autoUpdate)
        root.addProperty("geo-update-interval", validated.intervalHours)
        root.add("geox-url", JsonObject().apply {
            validated.urls.forEach { (type, url) -> addProperty(type.name.lowercase(), url) }
        })
        return YamlConfigCodec.toYaml(root.toString())
    }
}
