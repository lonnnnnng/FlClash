package com.lonnnnnng.flclash.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {
    @Test
    fun `uploads and downloads archive with basic auth`() = runBlocking {
        val archive = byteArrayOf(1, 2, 3, 4)
        var uploaded = byteArrayOf()
        var authHeader = ""
        val server = server { exchange ->
            authHeader = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            when (exchange.requestMethod to exchange.requestURI.path) {
                "OPTIONS" to "/dav/" -> respond(exchange, 200)
                "MKCOL" to "/dav/" -> respond(exchange, 405)
                "PUT" to "/dav/kclash-test.zip" -> {
                    uploaded = exchange.requestBody.use { it.readBytes() }
                    respond(exchange, 201)
                }
                "GET" to "/dav/kclash-test.zip" -> respond(exchange, 200, archive)
                else -> respond(exchange, 404)
            }
        }
        try {
            val endpoint = endpoint(server, username = "alice", password = "secret")
            val client = HttpWebDavClient()
            client.probe(endpoint)
            client.upload(endpoint, archive)
            val downloaded = client.download(endpoint)
            assertArrayEquals(archive, uploaded)
            assertArrayEquals(archive, downloaded)
            assertTrue(authHeader.startsWith("Basic "))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `follows same origin redirect`() = runBlocking {
        val archive = byteArrayOf(7, 8)
        val server = server { exchange ->
            when (exchange.requestURI.path) {
                "/dav/backup.zip" -> {
                    exchange.responseHeaders.add("Location", "/dav/target.zip")
                    respond(exchange, 302)
                }
                "/dav/target.zip" -> respond(exchange, 200, archive)
                else -> respond(exchange, 404)
            }
        }
        try {
            val endpoint = WebDavEndpoint(
                baseUrl = "http://127.0.0.1:${server.address.port}/dav/",
                fileName = "backup.zip",
            )
            assertArrayEquals(archive, HttpWebDavClient().download(endpoint))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `rejects cross origin redirect before sending credentials`() = runBlocking {
        val targetHits = AtomicInteger()
        var targetAuthHeader = ""
        val target = server { exchange ->
            targetHits.incrementAndGet()
            targetAuthHeader = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            respond(exchange, 200, byteArrayOf(9))
        }
        val source = server { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/target")
            respond(exchange, 302)
        }
        try {
            val client = HttpWebDavClient()
            val endpoint = WebDavEndpoint(
                baseUrl = "http://127.0.0.1:${source.address.port}/",
                fileName = "backup.zip",
                username = "alice",
                password = "secret",
            )
            val error = runCatching { client.download(endpoint) }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("跨站重定向"))
            assertEquals(0, targetHits.get())
            assertEquals("", targetAuthHeader)
        } finally {
            source.stop(0)
            target.stop(0)
        }
    }

    @Test
    fun `rejects unsafe file name before making a request`() = runBlocking {
        val client = HttpWebDavClient()
        val error = runCatching {
            client.download(WebDavEndpoint("http://127.0.0.1/", "../backup.zip"))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `maps authentication and permission failures without exposing credentials`() = runBlocking {
        val server = server { exchange ->
            when (exchange.requestURI.path) {
                "/auth/" -> respond(exchange, 401)
                "/permission/" -> respond(exchange, 403)
                else -> respond(exchange, 404)
            }
        }
        try {
            val client = HttpWebDavClient()
            val authError = runCatching {
                client.probe(WebDavEndpoint(
                    baseUrl = "http://127.0.0.1:${server.address.port}/auth/",
                    fileName = "backup.zip",
                    username = "alice",
                    password = "do-not-leak",
                ))
            }.exceptionOrNull()
            assertTrue(authError is WebDavException)
            assertEquals(WebDavFailureKind.AUTHENTICATION, (authError as WebDavException).kind)
            assertTrue(authError.message.orEmpty().contains("认证失败"))
            assertFalse(authError.message.orEmpty().contains("do-not-leak"))

            val permissionError = runCatching {
                client.probe(WebDavEndpoint(
                    baseUrl = "http://127.0.0.1:${server.address.port}/permission/",
                    fileName = "backup.zip",
                    username = "alice",
                    password = "do-not-leak",
                ))
            }.exceptionOrNull()
            assertEquals(WebDavFailureKind.PERMISSION, (permissionError as WebDavException).kind)
            assertTrue(permissionError.message.orEmpty().contains("访问被拒绝"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `maps offline and timeout failures to actionable messages`() = runBlocking {
        val server = server { exchange -> respond(exchange, 200) }
        val port = server.address.port
        server.stop(0)
        val offlineError = runCatching {
            HttpWebDavClient().probe(WebDavEndpoint("http://127.0.0.1:$port/", "backup.zip"))
        }.exceptionOrNull()
        assertEquals(WebDavFailureKind.OFFLINE, (offlineError as WebDavException).kind)
        assertTrue(offlineError.message.orEmpty().contains("无法连接"))

        val timeoutClient = HttpWebDavClient(
            OkHttpClient.Builder()
                .addInterceptor { throw SocketTimeoutException("simulated timeout") }
                .build(),
        )
        val timeoutError = runCatching {
            timeoutClient.probe(WebDavEndpoint("https://dav.example.test/", "backup.zip"))
        }.exceptionOrNull()
        assertEquals(WebDavFailureKind.TIMEOUT, (timeoutError as WebDavException).kind)
        assertTrue(timeoutError.message.orEmpty().contains("超时"))
    }

    private fun endpoint(server: HttpServer, username: String, password: String) = WebDavEndpoint(
        baseUrl = "http://127.0.0.1:${server.address.port}/dav/",
        fileName = "kclash-test.zip",
        username = username,
        password = password,
    )

    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
            server.createContext("/") { exchange -> handler(exchange) }
            server.executor = Executors.newCachedThreadPool()
            server.start()
        }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: ByteArray = byteArrayOf()) {
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
}
