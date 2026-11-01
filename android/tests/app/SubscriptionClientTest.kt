package com.lonnnnnng.flclash.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionClientTest {
    @Test
    fun `follows an http redirect before reading subscription bytes`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val userAgents = Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/redirect") { exchange ->
            userAgents.add(exchange.requestHeaders.getFirst("User-Agent").orEmpty())
            exchange.responseHeaders.add("Location", "/profile")
            exchange.sendResponseHeaders(301, -1)
            exchange.close()
        }
        server.createContext("/profile") { exchange ->
            userAgents.add(exchange.requestHeaders.getFirst("User-Agent").orEmpty())
            val bytes = "proxy-groups: []\n".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/yaml")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        try {
            val result = HttpSubscriptionClient().download(
                URI("http://127.0.0.1:${server.address.port}/redirect").toURL().toString(),
            )
            assertArrayEquals("proxy-groups: []\n".toByteArray(), result.bytes)
            // 作者 long：重定向前后都保留 Android 客户端标记，避免订阅入口或最终下载地址返回不同格式。
            assertEquals(2, userAgents.size)
            assertTrue(userAgents.all {
                it.startsWith("KClash/v") && it.endsWith(" clash-verge Platform/android")
            })
        } finally {
            server.stop(0)
        }
    }
}
