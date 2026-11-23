package com.lonnnnnng.flclash.update

import com.lonnnnnng.flclash.service.CoreDownloadRoute
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 作者 long：只读取官方最新发布页的跳转头，避免共享代理出口耗尽匿名REST API额度。 */
internal class UpdateTransport internal constructor(private val client: OkHttpClient) : AutoCloseable {
    constructor(route: CoreDownloadRoute) : this(OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false)
        .proxy(route.port?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", it)) } ?: Proxy.NO_PROXY)
        .proxyAuthenticator { _, response ->
            val username = route.username; val password = route.password
            if (username == null || password == null || response.request.header("Proxy-Authorization") != null) null
            else response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(username, password, Charsets.UTF_8)).build()
        }.build())

    suspend fun latestRelease(): AppRelease {
        // 作者 long：HEAD仅取Location，不拉取网页正文、更新说明或APK；运行核心时仍沿用当前HTTP代理。
        return request(Request.Builder().url("https://github.com/$UPDATE_REPOSITORY/releases/latest").head()) { response ->
            if (response.code !in setOf(301, 302, 303, 307, 308)) throw UpdateHttpStatusException(response.code)
            ReleaseCatalog.fromLatestRedirect(response.header("Location"))
        }
    }

    private suspend fun <T> request(builder: Request.Builder, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(builder.header("User-Agent", "KClash").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use(read)
                    continuation.resume(result)
                } catch (failure: Exception) { continuation.resumeWithException(failure) }
            }
        })
    }
    override fun close() { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}

internal class UpdateHttpStatusException(val statusCode: Int) : IOException("GitHub returned HTTP $statusCode")
