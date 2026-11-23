package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset

data class SubscriptionDownload(
    val bytes: ByteArray,
    val suggestedLabel: String?,
    val subscriptionInfo: SubscriptionInfo,
)

interface SubscriptionClient {
    suspend fun download(url: String): SubscriptionDownload
    suspend fun download(url: String, onProgress: (ResourceDownloadProgress) -> Unit): SubscriptionDownload =
        download(url).also { onProgress(ResourceDownloadProgress(it.bytes.size.toLong(), it.bytes.size.toLong())) }
}

class HttpSubscriptionClient(private val generalSettingsStore: com.lonnnnnng.flclash.service.GeneralSettingsStore? = null) : SubscriptionClient {
    override suspend fun download(url: String): SubscriptionDownload = download(url) {}

    override suspend fun download(url: String, onProgress: (ResourceDownloadProgress) -> Unit): SubscriptionDownload {
        val requestSettings = generalSettingsStore?.read()
        var currentUrl = runCatching { URL(url) }.getOrElse {
            throw IllegalArgumentException("订阅链接格式无效", it)
        }
        requireHttpProtocol(currentUrl)

        repeat(MAX_REDIRECTS + 1) { attempt ->
            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                // 作者 long：订阅入口可能从 HTTP 301 到 HTTPS，手动跟随才能保留协议白名单和重定向上限。
                instanceFollowRedirects = false
                requestMethod = "GET"
                // 作者 long：订阅服务按客户端类型返回 Clash 配置，沿用原版兼容标记并保留 KClash 自身身份与版本。
                setRequestProperty("User-Agent", "KClash/v${BuildConfig.VERSION_NAME} clash-verge Platform/android")
            }
            applyHttpRequestPolicy(connection, requestSettings)
            try {
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    if (attempt == MAX_REDIRECTS) {
                        throw IOException("订阅重定向次数过多")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("订阅服务器缺少重定向地址")
                    currentUrl = resolveRedirect(currentUrl, location)
                    return@repeat
                }
                if (code !in 200..299) {
                    throw IOException("订阅服务器返回 HTTP $code")
                }
                val progress = ResourceProgressReporter(connection.contentLengthLong.takeIf { connection.contentEncoding.isNullOrBlank() }, onProgress)
                val bytes = try { connection.inputStream.use { readProfileFileBytes(it, onRead = progress::read) } }
                    finally { progress.finish() }
                if (bytes.isEmpty()) throw IOException("订阅内容为空")
                return SubscriptionDownload(
                    bytes = bytes,
                    suggestedLabel = suggestedSubscriptionLabel(connection.getHeaderField("Content-Disposition")),
                    subscriptionInfo = SubscriptionInfo.parseHeader(
                        connection.getHeaderField("subscription-userinfo"),
                    ),
                )
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("订阅重定向次数过多")
    }

    private fun suggestedSubscriptionLabel(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        // 作者 long：订阅服务可同时提供兼容旧客户端的 filename 和 UTF-8 filename*，优先扩展参数才能保留中文名。
        val extendedName = contentDispositionParameter(contentDisposition, "filename*")
            ?.let(::decodeExtendedFilename)
        val filename = extendedName ?: contentDispositionParameter(contentDisposition, "filename") ?: return null
        val safeFilename = filename.substringAfterLast('/').substringAfterLast('\\').trim()
        return safeFilename.substringBeforeLast('.', safeFilename).takeIf { it.isNotBlank() }
    }

    private fun contentDispositionParameter(header: String, parameter: String): String? {
        val pattern = Regex(
            "(?:^|;)\\s*${Regex.escape(parameter)}\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))",
            RegexOption.IGNORE_CASE,
        )
        val match = pattern.find(header) ?: return null
        return (match.groups[1]?.value ?: match.groups[2]?.value)
            ?.replace("\\\"", "\"")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun decodeExtendedFilename(value: String): String? {
        val charsetSeparator = value.indexOf('\'')
        val languageSeparator = value.indexOf('\'', charsetSeparator + 1)
        if (charsetSeparator <= 0 || languageSeparator < 0) return null
        val charset = runCatching { Charset.forName(value.substring(0, charsetSeparator)) }.getOrNull() ?: return null
        val encodedFilename = value.substring(languageSeparator + 1)
        return runCatching {
            // 作者 long：filename* 的加号可表示文件名原字符，需保留后再按声明字符集解码百分号字节。
            URLDecoder.decode(encodedFilename.replace("+", "%2B"), charset.name())
        }.getOrNull()
    }

    private fun requireHttpProtocol(url: URL) {
        require(url.protocol == "http" || url.protocol == "https") {
            "仅支持 HTTP/HTTPS 订阅链接"
        }
    }

    private fun resolveRedirect(base: URL, location: String): URL =
        URL(base, location).also(::requireHttpProtocol)

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
    }
}
