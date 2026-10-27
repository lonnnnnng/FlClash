package com.lonnnnnng.flclash.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：在真机本地回环启动一个最小 WebDAV 服务，验证 Android 网络安全配置、OkHttp 和协议实现
 * 能够真实完成 OPTIONS、MKCOL、PUT、GET；服务只存在于测试进程，不访问外部网络或真实订阅地址。
 */
@RunWith(AndroidJUnit4::class)
class WebDavNetworkInstrumentedTest {
    @Test
    fun androidLoopbackWebDavRoundTripAndAuthFailure() = runBlocking {
        val server = LocalWebDavServer(username = "alice", password = "local-secret")
        try {
            val endpoint = WebDavEndpoint(
                baseUrl = "http://localhost:${server.port}/dav/",
                fileName = "kclash-test.zip",
                username = "alice",
                password = "local-secret",
            )
            val archive = byteArrayOf(9, 8, 7, 6)
            val client = HttpWebDavClient()

            client.probe(endpoint)
            client.upload(endpoint, archive)
            assertArrayEquals(archive, client.download(endpoint))
            assertArrayEquals(archive, server.archive)

            val authError = runCatching {
                client.probe(endpoint.copy(password = "wrong-secret"))
            }.exceptionOrNull()
            assertTrue(authError is WebDavException)
            assertEquals(WebDavFailureKind.AUTHENTICATION, (authError as WebDavException).kind)

            server.close()
            val offlineError = runCatching { client.probe(endpoint) }.exceptionOrNull()
            assertTrue(offlineError is WebDavException)
            assertEquals(WebDavFailureKind.OFFLINE, (offlineError as WebDavException).kind)
        } finally {
            server.close()
        }
    }

    private class LocalWebDavServer(
        private val username: String,
        private val password: String,
    ) : Closeable {
        private val socket = ServerSocket(0)
        private val executor: ExecutorService = Executors.newCachedThreadPool()
        @Volatile
        var archive: ByteArray = byteArrayOf()
            private set

        val port: Int get() = socket.localPort

        init {
            executor.execute {
                while (!socket.isClosed) {
                    runCatching { socket.accept() }
                        .onSuccess { connection -> executor.execute { handle(connection) } }
                        .onFailure { if (!socket.isClosed) throw it }
                }
            }
        }

        override fun close() {
            socket.close()
            executor.shutdownNow()
        }

        private fun handle(connection: Socket) {
            connection.use { client ->
                val input = BufferedInputStream(client.getInputStream())
                val output = client.getOutputStream()
                val requestLine = readLine(input) ?: return
                val headers = buildMap {
                    while (true) {
                        val line = readLine(input).orEmpty()
                        if (line.isEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0) {
                            put(line.substring(0, separator).lowercase(), line.substring(separator + 1).trim())
                        }
                    }
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(contentLength)
                input.readFully(body)
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]
                val expectedAuth = "Basic " + Base64.getEncoder()
                    .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
                if (headers["authorization"] != expectedAuth) {
                    respond(output, 401, byteArrayOf(), mapOf("WWW-Authenticate" to "Basic realm=\"KClash\""))
                    return
                }
                when (method to path) {
                    "OPTIONS" to "/dav/" -> respond(output, 200)
                    "MKCOL" to "/dav/" -> respond(output, 405)
                    "PUT" to "/dav/kclash-test.zip" -> {
                        archive = body
                        respond(output, 201)
                    }
                    "GET" to "/dav/kclash-test.zip" -> respond(output, 200, archive)
                    else -> respond(output, 404)
                }
            }
        }

        private fun respond(
            output: java.io.OutputStream,
            status: Int,
            body: ByteArray = byteArrayOf(),
            extraHeaders: Map<String, String> = emptyMap(),
        ) {
            val reason = when (status) {
                200 -> "OK"
                201 -> "Created"
                401 -> "Unauthorized"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                else -> "Error"
            }
            val header = buildString {
                append("HTTP/1.1 $status $reason\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n")
                extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
                append("\r\n")
            }
            output.write(header.toByteArray(Charsets.ISO_8859_1))
            output.write(body)
            output.flush()
        }

        private fun readLine(input: BufferedInputStream): String? {
            val bytes = ArrayList<Byte>()
            while (true) {
                val value = input.read()
                if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray()
                    .toString(Charsets.ISO_8859_1)
                if (value == '\n'.code) {
                    if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                    return bytes.toByteArray().toString(Charsets.ISO_8859_1)
                }
                bytes += value.toByte()
            }
        }

        private fun BufferedInputStream.readFully(buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val count = read(buffer, offset, buffer.size - offset)
                if (count < 0) error("本地 WebDAV 测试服务未收到完整请求体")
                offset += count
            }
        }
    }
}
