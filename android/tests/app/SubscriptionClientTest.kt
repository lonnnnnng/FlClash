package com.lonnnnnng.flclash.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SubscriptionClientTest {
    @Test
    fun `follows an http redirect before reading subscription bytes`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/profile")
            exchange.sendResponseHeaders(301, -1)
            exchange.close()
        }
        server.createContext("/profile") { exchange ->
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
        } finally {
            server.stop(0)
        }
    }
}
