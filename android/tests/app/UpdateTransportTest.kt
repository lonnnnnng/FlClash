package com.lonnnnnng.flclash.update

import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：单次HEAD读取官方跳转，确保检查更新不再消耗REST API额度或下载安装包。 */
class UpdateTransportTest {
    private val latest = "https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v0.1.17"

    private fun response(request: Request, code: Int, location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body("".toResponseBody()).apply { location?.let { header("Location", it) } }.build()

    @Test fun checksOnlyLatestPageHeadThroughSelectedProxy() = runBlocking {
        val requests = mutableListOf<Request>()
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))
        val client = OkHttpClient.Builder().proxy(proxy).followRedirects(false).addInterceptor { chain ->
            val request = chain.request(); requests += request
            assertEquals("https://github.com/$UPDATE_REPOSITORY/releases/latest", request.url.toString())
            assertEquals("HEAD", request.method)
            response(request, 302, latest)
        }.build()
        val release = UpdateTransport(client).use { it.latestRelease() }
        assertEquals("0.1.17", release.version)
        assertEquals(latest, release.releaseUrl)
        assertEquals(1, requests.size)
        assertEquals(proxy, client.proxy)
        assertEquals("KClash", requests.single().header("User-Agent"))
    }

    @Test fun failedOrMissingRedirectNeverPretendsVersionIsCurrent() = runBlocking {
        for ((status, location) in listOf(403 to null, 429 to null, 500 to null, 200 to null, 302 to null)) {
            var requests = 0
            val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
                requests++
                response(chain.request(), status, location)
            }.build()
            assertTrue(runCatching { UpdateTransport(client).use { it.latestRelease() } }.isFailure)
            assertEquals(1, requests)
        }
    }

    @Test fun latestRedirectRejectsOtherRepositoriesAndNonReleaseVersions() {
        for (value in listOf(null, "", latest.replace("https:", "http:"), latest.replace("github.com", "example.com"),
            latest.replace(UPDATE_REPOSITORY, "chen08209/FlClash"), latest.replace("kclash-v0.1.17", "v0.9.4"),
            "$latest-beta", "$latest?token=secret", "$latest#fragment", latest.replace("github.com/", "github.com:8443/"),
            latest.replace("github.com/", "user:secret@github.com/"))) {
            assertTrue(value, runCatching { ReleaseCatalog.fromLatestRedirect(value) }.isFailure)
        }
    }
}
