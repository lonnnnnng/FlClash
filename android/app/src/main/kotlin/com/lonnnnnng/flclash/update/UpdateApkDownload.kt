package com.lonnnnnng.flclash.update

import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Response

/** 作者 long：HTTP失败与内容损坏分开，网络断流保留部分文件，合法短段按已收到的位置继续请求。 */
internal suspend fun downloadUpdateApk(
    part: File,
    expectedBytes: Long,
    initialEtag: String?,
    request: suspend (Map<String, String>, suspend (Response) -> Unit) -> Unit,
    progress: suspend (Long, String?) -> Unit,
) {
    require(expectedBytes in 1..MAX_APK_BYTES)
    var offset = if (part.isFile) part.length() else 0L
    if (offset > expectedBytes) {
        check(part.delete()) { "无法清理超出大小的安装包" }
        offset = 0L
    }
    var etag = initialEtag
    var segments = 0
    while (offset < expectedBytes) {
        currentCoroutineContext().ensureActive()
        // 作者 long：限制异常服务器的小片段循环；已收字节保留供显式重试，不把资源消耗伪装为内容损坏。
        if (++segments > 128) throw IOException("安装包分段过多，可继续下载")
        val headers = mutableMapOf("Accept-Encoding" to "identity")
        if (offset > 0) {
            headers["Range"] = "bytes=$offset-"
            etag?.takeUnless { it.startsWith("W/") }?.let { headers["If-Range"] = it }
        }
        request(headers) { response ->
            if (response.code !in setOf(200, 206)) {
                throw IOException("安装包下载返回HTTP ${response.code}")
            }
            val responseEnd = if (response.code == 206) {
                val range = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)")
                    .matchEntire(response.header("Content-Range").orEmpty())
                val start = range?.groupValues?.get(1)?.toLongOrNull()
                val end = range?.groupValues?.get(2)?.toLongOrNull()
                val total = range?.groupValues?.get(3)?.toLongOrNull()
                require(start == offset && end != null && end >= offset && end < expectedBytes && total == expectedBytes) {
                    "下载续传范围无效"
                }
                end + 1
            } else {
                // 作者 long：服务器忽略Range或If-Range失效时，200是完整新正文，必须截断旧文件，不能追加。
                offset = 0L
                expectedBytes
            }
            val body = checkNotNull(response.body) { "安装包响应没有正文" }
            val responseEtag = response.header("ETag")?.takeIf { it.length <= 1024 && !it.any(Char::isISOControl) }
            require(response.code != 206 || etag == null || responseEtag == null || etag == responseEtag) {
                "续传安装包的版本已变化，请重新下载"
            }
            etag = responseEtag ?: etag.takeIf { response.code == 206 }
            progress(offset, etag)
            var savedAt = System.nanoTime()
            FileOutputStream(part, offset > 0).use { output ->
                try {
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(offset + count <= responseEnd) { "安装包正文超过声明范围" }
                            output.write(buffer, 0, count)
                            offset += count
                            val now = System.nanoTime()
                            if (now - savedAt >= 500_000_000L) {
                                progress(offset, etag)
                                savedAt = now
                            }
                        }
                    }
                } finally {
                    output.fd.sync()
                }
            }
            progress(offset, etag)
            if (offset != responseEnd) throw EOFException("安装包连接提前结束，可继续下载")
        }
    }
}
