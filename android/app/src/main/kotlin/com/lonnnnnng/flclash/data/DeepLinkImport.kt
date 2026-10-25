package com.lonnnnnng.flclash.data

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class DeepLinkProfileImport(
    val url: String,
    val label: String?,
)

/**
 * 作者 long：Deep Link 只负责把外部链接转换成现有订阅导入所需的 HTTP(S) 地址，
 * 不在 Activity 中重复下载、写文件或创建 Profile，避免外部入口形成第二套数据链路。
 */
object DeepLinkProfileParser {
    private val supportedSchemes = setOf("clash", "clashmeta", "flclash", "kclash")

    fun parse(raw: String): DeepLinkProfileImport? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in supportedSchemes) return null
        if (uri.host?.lowercase() != INSTALL_CONFIG_HOST) return null

        val query = parseQuery(uri.rawQuery ?: return null)
        val profileUrl = query[URL_PARAMETER]?.trim().orEmpty()
        if (!isHttpUrl(profileUrl)) return null

        return DeepLinkProfileImport(
            url = profileUrl,
            label = query[LABEL_PARAMETER]?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun isSupported(raw: String): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in supportedSchemes
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank()
    }

    private fun parseQuery(rawQuery: String): Map<String, String> =
        rawQuery.split('&')
            .asSequence()
            .filter { it.isNotEmpty() }
            .mapNotNull { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = decode(item.substring(0, separator)) ?: return@mapNotNull null
                val value = decode(item.substring(separator + 1)) ?: return@mapNotNull null
                key to value
            }
            .toMap()

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()

    private const val INSTALL_CONFIG_HOST = "install-config"
    private const val URL_PARAMETER = "url"
    private const val LABEL_PARAMETER = "name"
}
