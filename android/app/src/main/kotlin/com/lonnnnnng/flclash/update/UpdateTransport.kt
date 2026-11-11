package com.lonnnnnng.flclash.update

import com.lonnnnnng.flclash.service.CoreDownloadRoute
import java.io.ByteArrayOutputStream
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

/** 作者 long：仅查询固定GitHub发布目录；取消页面请求时关闭连接，不在应用中下载APK。 */
internal class UpdateTransport(route: CoreDownloadRoute) : AutoCloseable {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false)
        .proxy(route.port?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", it)) } ?: Proxy.NO_PROXY)
        .proxyAuthenticator { _, response ->
            val username = route.username; val password = route.password
            if (username == null || password == null || response.request.header("Proxy-Authorization") != null) null
            else response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(username, password, Charsets.UTF_8)).build()
        }.build()

    suspend fun releases(): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url("https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=100")
            .header("User-Agent", "KClash").header("Accept", "application/vnd.github+json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        check(it.code == 200)
                        checkNotNull(it.body).byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer); if (count < 0) break
                                require(output.size() + count <= 2 * 1024 * 1024)
                                output.write(buffer, 0, count)
                            }
                            output.toString(Charsets.UTF_8.name())
                        }
                    }
                    continuation.resume(text)
                } catch (failure: Exception) { continuation.resumeWithException(failure) }
            }
        })
    }
    override fun close() { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
