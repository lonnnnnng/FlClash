package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.GeneralSettings
import com.lonnnnnng.flclash.service.GeneralSettingsStore
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HttpRequestPolicyTest {
    @Test fun redirectsKeepOnePreferenceSnapshotAndNextDownloadUsesLatestUserAgent() = runBlocking {
        val store = object : GeneralSettingsStore {
            var value = GeneralSettings(userAgent = "Chosen/1")
            override fun read() = value
            override fun write(value: GeneralSettings) { this.value = value }
        }
        val seen = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/redirect") { exchange ->
            seen += exchange.requestHeaders.getFirst("User-Agent")
            store.write(store.read().copy(userAgent = "Changed/2"))
            exchange.responseHeaders.add("Location", "/body"); exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/body") { exchange ->
            seen += exchange.requestHeaders.getFirst("User-Agent")
            val body = "fixture".toByteArray(); exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/redirect"
            HttpSubscriptionClient(store).download(url)
            HttpScriptClient(store).download(url)
            assertEquals(listOf("Chosen/1", "Chosen/1", "Changed/2", "Changed/2"), seen)
        } finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun certificateOverrideOnlyChangesExplicitlyConfiguredConnection() {
        val initialVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val initialFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val relaxed = Connection(); val strict = Connection()
        applyHttpRequestPolicy(relaxed, GeneralSettings(checkCertificate = false, userAgent = "Custom/1"))
        applyHttpRequestPolicy(strict, GeneralSettings())
        assertEquals("Custom/1", relaxed.getRequestProperty("User-Agent"))
        assertSame(UserSelectedTlsPolicy.socketFactory, relaxed.sslSocketFactory)
        assertSame(initialFactory, strict.sslSocketFactory)
        assertSame(initialVerifier, HttpsURLConnection.getDefaultHostnameVerifier())
        assertSame(initialFactory, HttpsURLConnection.getDefaultSSLSocketFactory())
    }

    private class Connection : HttpsURLConnection(URL("https://example.test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
