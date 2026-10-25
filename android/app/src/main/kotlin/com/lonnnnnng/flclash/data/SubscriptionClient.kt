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
        val parsed = runCatching { URL(url) }.getOrElse {
            throw IllegalArgumentException("订阅链接格式无效", it)
        }
        require(parsed.protocol == "http" || parsed.protocol == "https") {
            "仅支持 HTTP/HTTPS 订阅链接"
        }
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "KClash/0.1")
        }
        try {
            val code = connection.responseCode
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
}
