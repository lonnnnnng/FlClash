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
    fun `reports real body bytes while downloading with the response length`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val body = ByteArray(64 * 1024) { (it % 251).toByte() }
        server.createContext("/profile") { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { output ->
                var offset = 0
                while (offset < body.size) {
                    val end = minOf(offset + 8 * 1024, body.size)
                    output.write(body, offset, end - offset); output.flush(); Thread.sleep(65)
                    offset = end
                }
            }
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        val progress = Collections.synchronizedList(mutableListOf<ResourceDownloadProgress>())
        try {
            val result = HttpSubscriptionClient().download(
                URI("http://127.0.0.1:${server.address.port}/profile").toURL().toString(), progress::add,
            )
            assertArrayEquals(body, result.bytes)
            assertTrue("至少报告开始和结束", progress.size >= 2)
            assertEquals(body.size.toLong(), progress.last().downloadedBytes)
            assertEquals(body.size.toLong(), progress.last().totalBytes)
            assertTrue(progress.zipWithNext().all { (a, b) -> b.downloadedBytes >= a.downloadedBytes })
        } finally { server.stop(0) }
    }

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
            exchange.responseHeaders.add(
                "Content-Disposition",
                "attachment; filename=\"config.yaml\"; filename*=UTF-8''%E6%97%A5%E5%B8%B8%E5%88%86%E6%B5%81.yaml",
            )
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
            assertEquals("日常分流", result.suggestedLabel)
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
