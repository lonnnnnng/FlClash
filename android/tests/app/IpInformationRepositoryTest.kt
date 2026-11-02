package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.core.CoreIpResponse
import com.lonnnnnng.flclash.core.CoreRouteStamp
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.OutboundIpRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IpInformationRepositoryTest {
    @Test fun explicitAnswerWinsOverEarlierInferredResidentialAndCancelsLosers() = runTest {
        val runtime = IpTestRuntime()
        var cancelled = false
        runtime.metadata = { url ->
            when {
                "ip-api.com" in url -> response("""{"status":"success","hosting":false,"mobile":false}""")
                "iplocate.io" in url -> response("""{"company":{"type":"hosting"}}""")
                else -> try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val repository = IpInformationRepository(runtime) { testScheduler.currentTime }
        val answer = repository.quality("8.8.8.8", false)
        assertEquals(IpQualitySource.IP_LOCATE, answer.source)
        assertEquals(IpType.HOSTING, answer.type)
        assertEquals(1_000L, testScheduler.currentTime)
        assertTrue(cancelled)
    }

    @Test fun successCacheExpiresAfterThreeDaysAndForceBypassesIt() = runTest {
        val runtime = IpTestRuntime()
        var calls = 0
        runtime.metadata = { calls++; response("""{"ip":"8.8.8.8","type":"isp"}""") }
        var clock = 0L
        val repository = IpInformationRepository(runtime) { clock }
        repository.quality("8.8.8.8", false)
        val first = calls
        repository.quality("8.8.8.8", false)
        assertEquals(first, calls)
        repository.quality("8.8.8.8", true)
        assertTrue(calls > first)
        val forced = calls
        clock = 3 * 24 * 3_600_000L
        repository.quality("8.8.8.8", false)
        assertTrue(calls > forced)
    }

    @Test fun errorsHaveFiveMinuteCooldownAndAllSourceFailures() = runTest {
        val runtime = IpTestRuntime()
        var calls = 0
        runtime.metadata = { calls++; response("{}", status = 429) }
        var clock = 0L
        val repository = IpInformationRepository(runtime) { clock }
        suspend fun fail(): IpQualityException = try {
            repository.quality("8.8.8.8", false); error("应失败")
        } catch (error: IpQualityException) { error }
        assertEquals(6, fail().failures.size)
        assertEquals(6, calls)
        fail()
        assertEquals(6, calls)
        clock = 300_000
        fail()
        assertEquals(12, calls)
    }

    @Test fun inferredAnswerWaitsUntilAllSourcesFinishAndCallerCancellationIsNotCached() = runTest {
        val runtime = IpTestRuntime()
        runtime.metadata = { url ->
            if ("ip-api.com" in url) response("""{"status":"success","hosting":false,"mobile":false}""")
            else response("{}", status = 429)
        }
        val repository = IpInformationRepository(runtime) { testScheduler.currentTime }
        assertEquals(IpType.RESIDENTIAL, repository.quality("8.8.8.8", false).type)
        runtime.metadata = { awaitCancellation() }
        val waiting = launch { repository.quality("1.1.1.1", false) }
        runCurrent()
        waiting.cancelAndJoin()
        runtime.metadata = { response("""{"ip":"1.1.1.1","type":"hosting"}""") }
        assertEquals(IpType.HOSTING, repository.quality("1.1.1.1", false).type)
    }
}

// 作者 long：只替换边界 I/O，波次、取消和缓存使用正式 Repository，测试不访问外网。
internal class IpTestRuntime : OutboundIpRuntime {
    override val state = MutableStateFlow(CoreRuntimeStatus.RUNNING)
    override val runningSinceElapsedMillis = MutableStateFlow<Long?>(1L)
    override val ipRouteRevision = MutableStateFlow(1L)
    var stamp = CoreRouteStamp(1, 1)
    var metadata: suspend (String) -> CoreIpResponse = { error("无夹具") }
    override suspend fun ipRouteStamp() = stamp
    override suspend fun queryOutboundIp(urls: List<String>) = error("不应访问出口")
    override suspend fun queryIpMetadata(url: String) = metadata(url)
}

private fun response(body: String, status: Int = 200) = CoreIpResponse("", body, "", status, CoreRouteStamp(1, 1))
