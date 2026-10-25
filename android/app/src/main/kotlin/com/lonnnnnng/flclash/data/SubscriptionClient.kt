package com.lonnnnnng.flclash.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SubscriptionDownload(
    val bytes: ByteArray,
    val suggestedLabel: String?,
    val subscriptionInfo: SubscriptionInfo,
)

interface SubscriptionClient {
    suspend fun download(url: String): SubscriptionDownload
}

class HttpSubscriptionClient : SubscriptionClient {
    override suspend fun download(url: String): SubscriptionDownload {
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
                setRequestProperty("User-Agent", "KClash/0.1")
            }
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
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) throw IOException("订阅内容为空")
                return SubscriptionDownload(
                    bytes = bytes,
                    suggestedLabel = connection.getHeaderField("Content-Disposition")
                        ?.substringAfter("filename=", "")
                        ?.trim(' ', '"', '\'')
                        ?.substringBeforeLast('.')
                        ?.takeIf { it.isNotBlank() },
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
