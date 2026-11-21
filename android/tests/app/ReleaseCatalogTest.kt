package com.lonnnnnng.flclash.update

import org.junit.Assert.*
import org.junit.Test
import com.lonnnnnng.flclash.service.CoreDownloadRoute
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ReleaseCatalogTest {
    private fun row(version: String = "0.1.3", tag: String = "kclash-v$version", draft: Boolean = false, prerelease: Boolean = false,
        repository: String = UPDATE_REPOSITORY, name: String = "KClash-$version-android-arm64-v8a.apk", bytes: Long = 100): String =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"assets":[{"name":"$name","size":$bytes,"browser_download_url":"https://github.com/$repository/releases/download/$tag/$name"},{"name":"SHA256SUMS","browser_download_url":"https://github.com/$repository/releases/download/$tag/SHA256SUMS"}]}"""
    @Test fun choosesOnlyStableKotlinArm64AssetsFromOwnRepository() {
        val json = listOf(row(),row("0.1.2"),row(tag="v0.9.3"),row("0.1.4",draft=true),row("0.1.5",prerelease=true),
            row(repository="chen08209/FlClash"),row(name="FlClash-0.1.3.apk")).joinToString(",","[","]")
        assertEquals(listOf("0.1.3","0.1.2"),ReleaseCatalog.parse(json).map { it.version })
    }
    @Test fun versionOrderAndOverflowCannotOfferDowngradeOrAlias() {
        assertEquals(1002L, ReleaseCatalog.versionCode("0.1.2"))
        assertTrue(ReleaseCatalog.versionCode("1.0.0")>ReleaseCatalog.versionCode("0.999.999"))
        for (value in listOf("0.1000.0","0.1.1000","01.2.3","2147.999.999","1.2.3-beta")) assertTrue(value,runCatching { ReleaseCatalog.versionCode(value) }.isFailure)
    }
    @Test fun comparesCurrentVersionWithoutOfferingEqualOrOlderRelease() {
        val json = "[${row()}]"
        assertEquals(UpdatePhase.AVAILABLE, ReleaseCatalog.check(json, 1002).phase)
        assertEquals(UpdatePhase.LATEST, ReleaseCatalog.check(json, 1003).phase)
        assertEquals(UpdatePhase.LATEST, ReleaseCatalog.check(json, 1004).phase)
        assertEquals("https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v0.1.3", ReleaseCatalog.check(json, 1002).release?.releaseUrl)
    }
    @Test fun missingKotlinReleaseDoesNotPretendVersionIsCurrent() {
        assertTrue(runCatching { ReleaseCatalog.check("[${row(tag="v0.9.3")}]", 1002) }.isFailure)
    }

    @Test fun failureDiagnosticsIdentifyRouteAndNetworkStageWithoutRawExceptionText() {
        val proxy = CoreDownloadRoute(7890, "user", "secret", 7)
        assertEquals("CORE_HTTP_PROXY/DNS", UpdateFailureDiagnostics.classify(
            UnknownHostException("github host detail"), proxy, CoreRuntimeStatus.RUNNING,
        ).diagnosticCode)
        assertEquals("DIRECT/TIMEOUT", UpdateFailureDiagnostics.classify(
            SocketTimeoutException("sensitive address"), CoreDownloadRoute(null, null, null, 8), CoreRuntimeStatus.STOPPED,
        ).diagnosticCode)
        assertEquals("CORE_HTTP_PROXY/TLS", UpdateFailureDiagnostics.classify(
            SSLHandshakeException("certificate detail"), proxy, CoreRuntimeStatus.RUNNING,
        ).diagnosticCode)
        assertEquals("CORE_HTTP_PROXY/TIMEOUT", UpdateFailureDiagnostics.classify(
            SocketTimeoutException("proxy request timed out while stopping"), proxy, CoreRuntimeStatus.STOPPING,
        ).diagnosticCode)
        assertEquals("CORE_HTTP_PROXY/HTTP/403", UpdateFailureDiagnostics.classify(
            UpdateHttpStatusException(403), proxy, CoreRuntimeStatus.RUNNING,
        ).diagnosticCode)
        assertEquals("UNKNOWN/CORE_ROUTE", UpdateFailureDiagnostics.classify(
            IllegalStateException("proxy route unavailable"), null, CoreRuntimeStatus.RUNNING,
        ).diagnosticCode)
        assertEquals("UNKNOWN/CORE_TRANSITION", UpdateFailureDiagnostics.classify(
            IllegalStateException("state transition"), null, CoreRuntimeStatus.STARTING,
        ).diagnosticCode)
    }
}
