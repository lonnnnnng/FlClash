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

    @Test fun persistentSuccessSurvivesNewRepositoryWithoutExtendingOriginalExpiry() = runTest {
        val runtime = IpTestRuntime(); var calls = 0; var wall = 1_000L
        runtime.metadata = { calls++; response("""{"ip":"8.8.8.8","type":"isp"}""") }
        val disk = MemoryQualityCache()
        fun repository() = IpInformationRepository(runtime, { testScheduler.currentTime }, { wall }, disk)
        val first = repository().quality("8.8.8.8", false); val used = calls
        wall += IP_QUALITY_MAX_AGE - 1
        assertEquals(first, repository().quality("8.8.8.8", false)); assertEquals(used, calls)
        wall++
        repository().quality("8.8.8.8", false); assertTrue(calls > used)
    }

    @Test fun forceSkipsDiskAndCacheReadWriteFailuresDoNotHideNetworkSuccess() = runTest {
        val runtime = IpTestRuntime(); var calls = 0
        runtime.metadata = { calls++; response("""{"ip":"8.8.8.8","type":"hosting"}""") }
        val disk = object : IpQualityCache {
            override suspend fun fresh(ip: String, now: Long): IpQualityRecord? = error("disk read failed")
            override suspend fun put(record: IpQualityRecord, now: Long) = error("disk write failed")
        }
        val repo = IpInformationRepository(runtime, { 0 }, { 1_000 }, disk)
        assertEquals(IpType.HOSTING, repo.quality("8.8.8.8", false).type)
        val first = calls; repo.quality("8.8.8.8", false); assertEquals(first, calls)
        repo.quality("8.8.8.8", true); assertTrue(calls > first)
    }

    @Test fun failureCooldownEndsOnRouteChangeAndIsNeverPersisted() = runTest {
        val runtime = IpTestRuntime(); var calls = 0; val disk = MemoryQualityCache()
        runtime.metadata = { calls++; response("{}", 429) }
        val repo = IpInformationRepository(runtime, { 0 }, { 1_000 }, disk)
        assertTrue(runCatching { repo.quality("8.8.8.8", false) }.isFailure)
        assertNull(disk.raw); val first = calls
        assertTrue(runCatching { repo.quality("8.8.8.8", false) }.isFailure); assertEquals(first, calls)
        runtime.ipRouteRevision.value++
        runtime.metadata = { calls++; response("""{"ip":"8.8.8.8","type":"isp"}""") }
        assertEquals(IpType.RESIDENTIAL, repo.quality("8.8.8.8", false).type); assertTrue(calls > first)
    }

    @Test fun corruptedAndFutureDiskEntriesFallBackToSourcesAndRepairOnSuccess() = runTest {
        val runtime = IpTestRuntime(); var calls = 0
        runtime.metadata = { calls++; response("""{"ip":"8.8.8.8","type":"isp"}""") }
        val disk = MemoryQualityCache(); disk.raw = "{truncated"
        fun repo() = IpInformationRepository(runtime, { 0 }, { 1_000 }, disk)
        repo().quality("8.8.8.8", false); assertEquals(1_000, disk.fresh("8.8.8.8", 1_000)!!.checkedAt)
        val first = calls
        disk.raw = disk.raw!!.replace("\"checkedAt\":1000", "\"checkedAt\":1001")
        repo().quality("8.8.8.8", false); assertTrue(calls > first)
        assertEquals(1_000, disk.fresh("8.8.8.8", 1_000)!!.checkedAt)
    }

    @Test fun lateConcurrentFailureDoesNotHideNewSuccessfulQuality() = runTest {
        val runtime = IpTestRuntime()
        val releaseFailure = CompletableDeferred<Unit>()
        runtime.metadata = { releaseFailure.await(); response("{}", 429) }
        val repo = IpInformationRepository(runtime) { testScheduler.currentTime }
        val slow = async { runCatching { repo.quality("8.8.8.8", true) } }
        runCurrent()
        runtime.metadata = { response("""{"ip":"8.8.8.8","type":"hosting"}""") }
        val success = repo.quality("8.8.8.8", true)
        // 作者 long：先让失败请求的后续波次也只返回受限，再释放最早三源，复现跨入口迟到失败。
        runtime.metadata = { response("{}", 429) }
        releaseFailure.complete(Unit)
        assertTrue(slow.await().isFailure)
        assertEquals(success, repo.quality("8.8.8.8", false))
    }
}

// 作者 long：模拟持久层只保存正式编解码后的字符串，新建Repository必须重新解码，避免同对象内存命中伪装持久恢复。
internal class MemoryQualityCache : IpQualityCache {
    var raw: String? = null
    override suspend fun fresh(ip: String, now: Long) = IpQualityCacheCodec.decode(raw, now)[ip]
    override suspend fun put(record: IpQualityRecord, now: Long) {
        val records = IpQualityCacheCodec.decode(raw, now).toMutableMap(); records[record.quality.ip] = record
        raw = IpQualityCacheCodec.encode(records.values, now)
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
