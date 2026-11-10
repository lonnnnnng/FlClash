package com.lonnnnnng.flclash.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class UpdateApkDownloadTest {
    private fun withServer(handler: (HttpExchange) -> Unit, test: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/apk") { exchange -> exchange.use { handler(it) } }
        server.start()
        try { test("http://127.0.0.1:${server.address.port}/apk") } finally { server.stop(0) }
    }
    private fun withPart(initial: String, test: (File) -> Unit) {
        val part = Files.createTempFile("kclash-download-", ".part").toFile().apply { writeText(initial) }
        try { test(part) } finally { assertTrue(part.delete()) }
    }
    private fun download(part: File, url: String, size: Long = 10, etag: String? = "\"one\"") = runBlocking(Dispatchers.IO) {
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).retryOnConnectionFailure(false).build()
        try {
            downloadUpdateApk(part, size, etag, request = { headers, read ->
                val request = Request.Builder().url(url)
                headers.forEach { (key, value) -> request.header(key, value) }
                client.newCall(request.build()).execute().use { read(it) }
            }, progress = { _, _ -> })
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun shortRangesAppendUntilCompleteWithoutDiscardingPreviousBytes() = withPart("012") { part ->
        val ranges = mutableListOf<String>()
        withServer({ exchange ->
            val range = exchange.requestHeaders.getFirst("Range"); ranges += range
            assertEquals("\"one\"", exchange.requestHeaders.getFirst("If-Range"))
            val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
            val end = minOf(start + 2, 9)
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/10")
            exchange.responseHeaders.add("ETag", "\"one\"")
            val body = "0123456789".substring(start, end + 1).toByteArray()
            exchange.sendResponseHeaders(206, body.size.toLong()); exchange.responseBody.write(body)
        }) { url -> download(part, url) }
        assertEquals(listOf("bytes=3-", "bytes=6-", "bytes=9-"), ranges)
        assertEquals("0123456789", part.readText())
    }
    @Test fun temporaryHttpFailurePreservesAlreadyDownloadedBytes() = withPart("01234") { part ->
        withServer({ it.sendResponseHeaders(503, -1) }) { url ->
            val failure = runCatching { download(part, url) }.exceptionOrNull()
            assertTrue(failure.toString(), failure is IOException)
            assertEquals("01234", part.readText())
        }
    }
    @Test fun prematureEofRetainsNewBytesAndNextAttemptResumesThem() = withPart("012") { part ->
        withServer({ exchange ->
            exchange.responseHeaders.add("Content-Range", "bytes 3-9/10")
            exchange.sendResponseHeaders(206, 7); exchange.responseBody.write("345".toByteArray())
        }) { url -> assertTrue(runCatching { download(part, url) }.exceptionOrNull() is IOException) }
        assertEquals("012345", part.readText())
        withServer({ exchange ->
            assertEquals("bytes=6-", exchange.requestHeaders.getFirst("Range"))
            exchange.responseHeaders.add("Content-Range", "bytes 6-9/10")
            exchange.sendResponseHeaders(206, 4); exchange.responseBody.write("6789".toByteArray())
        }) { url -> download(part, url) }
        assertEquals("0123456789", part.readText())
    }
    @Test fun ignoredRangeReplacesOldBytesInsteadOfAppending() = withPart("stale") { part ->
        withServer({ exchange ->
            exchange.responseHeaders.add("ETag", "\"two\"")
            exchange.sendResponseHeaders(200, 10); exchange.responseBody.write("0123456789".toByteArray())
        }) { url -> download(part, url) }
        assertEquals("0123456789", part.readText())
    }
    @Test fun mismatchedRangeIsRejectedBeforeExistingFileIsTouched() = withPart("012") { part ->
        withServer({ exchange ->
            exchange.responseHeaders.add("Content-Range", "bytes 0-6/10")
            exchange.sendResponseHeaders(206, 7); exchange.responseBody.write("0123456".toByteArray())
        }) { url -> assertTrue(runCatching { download(part, url) }.exceptionOrNull() is IllegalArgumentException) }
        assertEquals("012", part.readText())
    }
}
