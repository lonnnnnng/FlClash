package com.lonnnnnng.flclash.update

import org.junit.Assert.*
import org.junit.Test
import com.lonnnnnng.flclash.service.CoreDownloadRoute
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ReleaseCatalogTest {
    @Test fun versionOrderAndOverflowCannotOfferDowngradeOrAlias() {
        assertEquals(1002L, ReleaseCatalog.versionCode("0.1.2"))
        assertTrue(ReleaseCatalog.versionCode("1.0.0")>ReleaseCatalog.versionCode("0.999.999"))
        for (value in listOf("0.1000.0","0.1.1000","01.2.3","2147.999.999","1.2.3-beta")) assertTrue(value,runCatching { ReleaseCatalog.versionCode(value) }.isFailure)
    }
    @Test fun comparesCurrentVersionWithoutOfferingEqualOrOlderRelease() {
        val release = ReleaseCatalog.fromLatestRedirect("https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v0.1.17")
        assertEquals(UpdatePhase.AVAILABLE, ReleaseCatalog.check(release, 1016).phase)
        assertEquals(UpdatePhase.LATEST, ReleaseCatalog.check(release, 1017).phase)
        assertEquals(UpdatePhase.LATEST, ReleaseCatalog.check(release, 1018).phase)
        assertEquals("https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v0.1.17", release.releaseUrl)
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
