package com.lonnnnnng.flclash.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ScriptDownload(
    val bytes: ByteArray,
    val suggestedLabel: String?,
)

interface ScriptClient {
    suspend fun download(url: String): ScriptDownload
}

class HttpScriptClient : ScriptClient {
    override suspend fun download(url: String): ScriptDownload {
        var currentUrl = runCatching { URL(url) }.getOrElse {
            throw IllegalArgumentException("脚本地址格式无效", it)
        }
        requireHttpProtocol(currentUrl)

        repeat(MAX_REDIRECTS + 1) { attempt ->
            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                // 作者 long：远程脚本允许有限次跨协议重定向，但不能把任意协议带入下载链路。
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "KClash/0.1")
            }
            try {
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    if (attempt == MAX_REDIRECTS) {
                        throw IOException("脚本重定向次数过多")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("脚本服务器缺少重定向地址")
                    currentUrl = resolveRedirect(currentUrl, location)
                    return@repeat
                }
                if (code !in 200..299) {
                    throw IOException("脚本服务器返回 HTTP $code")
                }
                val bytes = connection.inputStream.use { input ->
                    val declaredLength = connection.contentLengthLong
                    if (declaredLength > MAX_BYTES) {
                        throw IOException("脚本文件超过 2 MiB 限制")
                    }
                    input.readBoundedBytes(MAX_BYTES)
                }
                if (bytes.isEmpty()) throw IOException("脚本内容为空")
                return ScriptDownload(
                    bytes = bytes,
                    suggestedLabel = connection.getHeaderField("Content-Disposition")
                        ?.substringAfter("filename=", "")
                        ?.trim(' ', '"', '\'')
                        ?.substringBeforeLast('.')
                        ?.takeIf { it.isNotBlank() },
                )
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("脚本重定向次数过多")
    }

    private fun requireHttpProtocol(url: URL) {
        require(url.protocol == "http" || url.protocol == "https") {
            "仅支持 HTTP/HTTPS 脚本地址"
        }
    }

    private fun resolveRedirect(base: URL, location: String): URL =
        URL(base, location).also(::requireHttpProtocol)

    private companion object {
        const val MAX_BYTES = 2L * 1024L * 1024L
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

private fun java.io.InputStream.readBoundedBytes(maxBytes: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("脚本文件超过 2 MiB 限制")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
