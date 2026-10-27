package com.lonnnnnng.flclash.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

const val DEFAULT_WEBDAV_FILE_NAME = "backup.zip"

enum class WebDavFailureKind {
    AUTHENTICATION,
    PERMISSION,
    NOT_FOUND,
    OFFLINE,
    TIMEOUT,
    SERVER,
}

/**
 * 作者 long：WebDAV 的网络错误需要在 UI 中给出可执行的下一步，不能把 OkHttp 或底层 socket
 * 的英文异常直接展示给用户；技术原因保留在 cause 中，避免把账号、地址和实现细节拼进提示。
 */
class WebDavException(
    val kind: WebDavFailureKind,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * WebDAV 远程备份连接描述。密码只在内存中短暂存在，由配置页从 Android Keystore 凭据存储读取，
 * 不能直接写入普通 Room 偏好表、日志或备份归档。
 */
data class WebDavEndpoint(
    val baseUrl: String,
    val fileName: String,
    val username: String? = null,
    val password: String? = null,
) {
    override fun toString(): String =
        "WebDavEndpoint(fileName=$fileName, hasCredentials=${!username.isNullOrEmpty()})"
}

interface WebDavClient {
    suspend fun probe(endpoint: WebDavEndpoint)

    suspend fun upload(endpoint: WebDavEndpoint, archive: ByteArray)

    suspend fun download(endpoint: WebDavEndpoint): ByteArray
}

/**
 * 作者 long：远程备份先限定为手动 WebDAV 上传/下载，协议层不引入自动同步和冲突覆盖语义；
 * 每个请求都手动处理有限次同源重定向，跨 origin 直接失败，避免 Basic 凭据被转发到第三方。
 */
class HttpWebDavClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) : WebDavClient {
    override suspend fun probe(endpoint: WebDavEndpoint) {
        withContext(Dispatchers.IO) {
            request(endpoint, method = "OPTIONS", url = collectionUrl(endpoint)) { code ->
                // 一些 WebDAV 服务对 OPTIONS 返回 405，但仍能接受后续 MKCOL/PUT；这里把它视为可继续探测。
                code in 200..299 || code == HTTP_METHOD_NOT_ALLOWED
            }
        }
    }

    override suspend fun upload(endpoint: WebDavEndpoint, archive: ByteArray) {
        withContext(Dispatchers.IO) {
            require(archive.isNotEmpty()) { "备份文件为空" }
            require(archive.size <= MAX_ARCHIVE_BYTES) { "备份文件超过 64 MiB 限制" }
            val collection = collectionUrl(endpoint)
            request(endpoint, method = "MKCOL", url = collection) { code ->
                // 已存在的远程目录通常返回 405，不能因此阻止覆盖同一个手动备份文件。
                code in 200..299 || code == HTTP_METHOD_NOT_ALLOWED
            }
            request(endpoint, method = "PUT", url = fileUrl(endpoint), body = archive) { code ->
                code in 200..299
            }
        }
    }

    override suspend fun download(endpoint: WebDavEndpoint): ByteArray = withContext(Dispatchers.IO) {
        request(endpoint, method = "GET", url = fileUrl(endpoint), readBody = true) { code ->
            code == HTTP_OK || code == HTTP_PARTIAL
        } ?: throw IOException("WebDAV 下载没有返回内容")
    }

    private fun collectionUrl(endpoint: WebDavEndpoint): HttpUrl {
        val base = parseBaseUrl(endpoint.baseUrl)
        return if (base.encodedPath.endsWith('/')) base else base.newBuilder()
            .encodedPath("${base.encodedPath}/")
            .build()
    }

    private fun fileUrl(endpoint: WebDavEndpoint): HttpUrl {
        validateFileName(endpoint.fileName)
        return collectionUrl(endpoint).newBuilder()
            .addPathSegment(endpoint.fileName)
            .build()
    }

    private fun parseBaseUrl(raw: String): HttpUrl {
        val parsed = runCatching { URL(raw.trim()) }.getOrElse {
            throw IllegalArgumentException("WebDAV 地址格式无效", it)
        }
        require(parsed.protocol == "http" || parsed.protocol == "https") { "WebDAV 仅支持 HTTP/HTTPS" }
        require(parsed.userInfo == null) { "WebDAV 地址不能包含内嵌账号" }
        require(parsed.query == null && parsed.ref == null) { "WebDAV 地址不能包含查询参数或片段" }
        return (if (parsed.toString().endsWith('/')) parsed.toString() else "${parsed}/").toHttpUrl()
    }

    private fun validateFileName(fileName: String) {
        require(fileName.matches(FILE_NAME_PATTERN)) { "WebDAV 文件名只允许字母、数字、点、下划线和短横线" }
        require(fileName != "." && fileName != "..") { "WebDAV 文件名无效" }
    }

    private fun request(
        endpoint: WebDavEndpoint,
        method: String,
        url: HttpUrl,
        body: ByteArray? = null,
        readBody: Boolean = false,
        accepted: (Int) -> Boolean,
    ): ByteArray? {
        var current = url
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val requestBody = body?.toRequestBody(ZIP_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(current)
                .method(method, requestBody)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/zip, application/octet-stream")
            basicAuthorization(endpoint)?.let { requestBuilder.header("Authorization", it) }

            val response = try {
                httpClient.newCall(requestBuilder.build()).execute()
            } catch (error: IOException) {
                throw networkFailure(error)
            }
            response.use { response ->
                if (response.code in REDIRECT_CODES) {
                    if (attempt == MAX_REDIRECTS) throw IOException("WebDAV 重定向次数过多")
                    val location = response.header("Location")
                        ?: throw IOException("WebDAV 服务器缺少重定向地址")
                    val next = current.resolve(location)
                        ?: throw IOException("WebDAV 重定向地址无效")
                    requireSameOrigin(current, next)
                    current = next
                    return@repeat
                }
                if (!accepted(response.code)) throw httpFailure(response.code)
                if (!readBody) return null
                val responseBody = response.body ?: throw WebDavException(
                    WebDavFailureKind.SERVER,
                    "WebDAV 服务器返回空响应",
                )
                val declaredLength = responseBody.contentLength()
                if (declaredLength > MAX_ARCHIVE_BYTES) {
                    throw IOException("远程备份超过 64 MiB 限制")
                }
                return responseBody.byteStream().use { it.readBoundedBytes(MAX_ARCHIVE_BYTES) }
            }
        }
        throw IOException("WebDAV 重定向次数过多")
    }

    private fun httpFailure(code: Int): WebDavException = when {
        code == 401 -> WebDavException(
            WebDavFailureKind.AUTHENTICATION,
            "WebDAV 认证失败，请检查账号和密码",
        )
        code == 403 -> WebDavException(
            WebDavFailureKind.PERMISSION,
            "WebDAV 访问被拒绝，请检查账号权限和目录权限",
        )
        code == 404 -> WebDavException(
            WebDavFailureKind.NOT_FOUND,
            "WebDAV 文件或目录不存在，请检查地址和文件名",
        )
        code == 408 || code == 429 -> WebDavException(
            WebDavFailureKind.SERVER,
            "WebDAV 服务暂时不可用，请稍后重试",
        )
        code in 500..599 -> WebDavException(
            WebDavFailureKind.SERVER,
            "WebDAV 服务器暂时不可用，请稍后重试",
        )
        else -> WebDavException(
            WebDavFailureKind.SERVER,
            "WebDAV 服务器返回 HTTP $code",
        )
    }

    private fun networkFailure(error: IOException): WebDavException = when (error) {
        is SocketTimeoutException -> WebDavException(
            WebDavFailureKind.TIMEOUT,
            "WebDAV 请求超时，请检查网络或服务器响应",
            error,
        )
        else -> WebDavException(
            WebDavFailureKind.OFFLINE,
            "无法连接 WebDAV 服务器，请检查地址和网络",
            error,
        )
    }

    private fun basicAuthorization(endpoint: WebDavEndpoint): String? {
        val username = endpoint.username?.takeIf { it.isNotEmpty() } ?: return null
        val password = endpoint.password.orEmpty()
        return Credentials.basic(username, password, Charsets.UTF_8)
    }

    private fun requireSameOrigin(from: HttpUrl, to: HttpUrl) {
        require(to.scheme == "http" || to.scheme == "https") { "WebDAV 重定向协议无效" }
        if (from.scheme != to.scheme || !from.host.equals(to.host, ignoreCase = true) || from.port != to.port) {
            throw IOException("WebDAV 禁止跨站重定向")
        }
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
        const val MAX_REDIRECTS = 5
        const val USER_AGENT = "KClash/0.1"
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val HTTP_METHOD_NOT_ALLOWED = 405
        val FILE_NAME_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val ZIP_MEDIA_TYPE = "application/zip".toMediaType()
    }
}

private fun java.io.InputStream.readBoundedBytes(maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("远程备份超过 64 MiB 限制")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
