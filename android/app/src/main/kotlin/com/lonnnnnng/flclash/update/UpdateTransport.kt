package com.lonnnnnng.flclash.update

import com.lonnnnnng.flclash.service.CoreDownloadRoute
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** 作者 long：更新始终验证TLS；核心运行时显式走当前入站，失败保留原因，不悄悄切直连或加速源。 */
internal class UpdateTransport(route: CoreDownloadRoute) : AutoCloseable {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .proxy(route.port?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", it)) } ?: Proxy.NO_PROXY)
        .proxyAuthenticator { _, response ->
            val username=route.username; val password=route.password
            if (username == null || password == null || response.request.header("Proxy-Authorization") != null) null
            else response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(username, password, Charsets.UTF_8)).build()
        }.build()

    suspend fun <T> response(url: String, headers: Map<String, String> = emptyMap(), read: suspend (Response) -> T): T = withContext(Dispatchers.IO) { coroutineScope {
        var target = url
        repeat(6) { count ->
            require(ReleaseCatalog.allowedDownloadUrl(target)) { "更新下载地址不在允许的发布源中" }
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(target).header("User-Agent", "KClash-Updater").header("Accept", "application/vnd.github+json")
            headers.forEach { (key, value) -> request.header(key, value) }
            val call = client.newCall(request.build())
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.execute().use { response ->
                    if (response.code in setOf(301,302,303,307,308)) {
                        check(count < 5) { "更新下载重定向次数过多" }
                        target = checkNotNull(response.header("Location")?.let { response.request.url.resolve(it)?.toString() }) { "更新下载缺少重定向地址" }
                    } else return@coroutineScope read(response)
                }
            } finally { withContext(NonCancellable) { cancellation.cancelAndJoin() } }
        }
        error("更新下载重定向次数过多")
    } }

    suspend fun text(url: String, limit: Int): String = response(url) { response ->
        check(response.code == 200) { "发布服务器返回HTTP ${response.code}" }
        val body = checkNotNull(response.body)
        require(body.contentLength() <= limit) { "发布信息超过容量限制" }
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                require(output.size() + count <= limit) { "发布信息超过容量限制" }; output.write(buffer,0,count)
            }
        }
        output.toString(Charsets.UTF_8.name())
    }
    override fun close() { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
