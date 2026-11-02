package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ServiceExitRepositoryTest {
    @Test fun parsesOnlyValidatedSourceAndPassesDetectedNodeAndStamp() = runTest {
        val runtime = ExitRuntime()
        val repository = ServiceExitRepository(runtime, ExitQuality())
        assertEquals(OutboundIpInfo("8.8.8.8", "US"), repository.outbound("fixture-exit", runtime.stamp))
        assertEquals("fixture-exit", runtime.node)
        assertEquals(outboundIpSources, runtime.urls)
    }

    @Test fun invalidBodyAndStaleStampCannotBecomeDisplayedAddress() = runTest {
        val runtime = ExitRuntime()
        val repository = ServiceExitRepository(runtime, ExitQuality())
        for (invalid in listOf("ip=127.0.0.1\nloc=US", "ip=8.8.8.8", "captcha")) {
            runtime.body = invalid
            assertTrue(runCatching { repository.outbound("DIRECT", runtime.stamp) }.isFailure)
        }
        runtime.body = "ip=8.8.8.8\nloc=US"
        assertTrue(runCatching { repository.outbound("DIRECT", CoreRouteStamp(9, 9)) }.isFailure)
    }

    @Test fun qualityDelegatesClickedIpAndForceToExistingCache() = runTest {
        val runtime = ExitRuntime(); val quality = ExitQuality()
        ServiceExitRepository(runtime, quality).quality("1.1.1.1", true)
        assertEquals("1.1.1.1", quality.ip)
        assertTrue(quality.force)
    }
}

private class ExitRuntime : ServiceStatusRuntime {
    override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    override val runningSinceElapsedMillis = MutableStateFlow<Long?>(null)
    override val ipRouteRevision = MutableStateFlow(0L)
    val stamp = CoreRouteStamp(1, 1)
    var node: String? = null
    var urls: List<String> = emptyList()
    var body = "ip=8.8.8.8\nloc=US"
    override suspend fun ipRouteStamp() = stamp
    override suspend fun checkServices(targets: List<ServiceTarget>) = error("不应检测服务")
    override suspend fun queryServiceExit(proxyName: String, expected: CoreRouteStamp, urls: List<String>): CoreIpResponse {
        node = proxyName; this.urls = urls
        return CoreIpResponse(outboundIpSources.first(), body, "", 200, stamp)
    }
}
private class ExitQuality : IpInformationStore {
    var ip: String? = null
    var force = false
    override suspend fun outbound(): RoutedIpInfo = error("不能使用首页出口")
    override suspend fun quality(ip: String, force: Boolean): IpQuality {
        this.ip = ip; this.force = force
        return IpQuality(ip, IpQualitySource.IP_QUERY, IpType.HOSTING)
    }
}
