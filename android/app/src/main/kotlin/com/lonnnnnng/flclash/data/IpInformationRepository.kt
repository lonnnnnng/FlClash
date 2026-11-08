package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.OutboundIpRuntime
import com.lonnnnnng.flclash.core.CoreRouteStamp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class RoutedIpInfo(val info: OutboundIpInfo, val stamp: CoreRouteStamp, val hits: List<com.lonnnnnng.flclash.core.CoreRouteHit> = emptyList())

interface IpInformationStore {
    suspend fun outbound(): RoutedIpInfo
    suspend fun quality(ip: String, force: Boolean = false): IpQuality
}

@Singleton
class IpInformationRepository internal constructor(
    private val runtime: OutboundIpRuntime,
    private val elapsedMillis: () -> Long,
    private val wallMillis: () -> Long,
    private val persistent: IpQualityCache,
) : IpInformationStore {
    @Inject constructor(runtime: OutboundIpRuntime, database: KClashDatabase) :
        this(runtime, android.os.SystemClock::elapsedRealtime, System::currentTimeMillis, RoomIpQualityCache(database))
    internal constructor(runtime: OutboundIpRuntime, elapsedMillis: () -> Long) :
        this(runtime, elapsedMillis, elapsedMillis, NoopIpQualityCache)
    private data class Cached(val quality: IpQuality?, val failure: IpQualityException?, val at: Long, val routeRevision: Long)
    private val cache = linkedMapOf<String, Cached>()

    override suspend fun outbound(): RoutedIpInfo {
        val response = runtime.queryOutboundIp(outboundIpSources)
        return RoutedIpInfo(parseOutboundIp(response), response.stamp, response.hits)
    }

    override suspend fun quality(ip: String, force: Boolean): IpQuality {
        require(isPublicIpLiteral(ip)) { "IP 地址无效" }
        val revision = runtime.ipRouteRevision.value
        if (!force) synchronized(cache) { cache[ip] }?.let { entry ->
            val valid = if (entry.quality != null) IpQualityCacheCodec.fresh(entry.at, wallMillis())
                else revision == entry.routeRevision && elapsedMillis() >= entry.at && elapsedMillis() - entry.at < 5 * 60_000L
            if (valid) {
                entry.quality?.let { return it }
                throw checkNotNull(entry.failure)
            }
        }
        if (!force) cacheOrNull { persistent.fresh(ip, wallMillis()) }?.let { record ->
            if (record.quality.ip == ip && IpQualityCacheCodec.fresh(record.checkedAt, wallMillis())) {
                remember(ip, Cached(record.quality, null, record.checkedAt, revision))
                return record.quality
            }
        }
        try {
            return lookupQuality(ip).also {
                val at = wallMillis()
                remember(ip, Cached(it, null, at, revision))
                cacheOrNull { persistent.put(IpQualityRecord(it, at), wallMillis()) }
            }
        } catch (error: IpQualityException) {
            // 作者 long：失败只在当前路由内冷却；断网恢复/切出口后不能继续拿旧失败阻止新查询。
            if (revision == runtime.ipRouteRevision.value) synchronized(cache) {
                // 作者 long：首页和服务详情可并发查同IP；迟到失败不能遮住另一路已经获得的有效成功结果。
                val success = cache[ip]?.takeIf { it.quality != null && IpQualityCacheCodec.fresh(it.at, wallMillis()) }
                if (success == null) remember(ip, Cached(null, error, elapsedMillis(), revision))
            }
            throw error
        }
    }

    private suspend fun <T> cacheOrNull(action: suspend () -> T): T? = try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null } // 作者 long：磁盘缓存是优化，读写失败不能掩盖可用的在线查询结果。

    private fun remember(ip: String, value: Cached) = synchronized(cache) {
        // 作者 long：仅存解析后的质量，不保存响应正文；内存缓存有界，避免用户频繁切换出口无限增长。
        cache.remove(ip)
        cache[ip] = value
        while (cache.size > 64) cache.remove(cache.keys.first())
    }

    private suspend fun lookupQuality(ip: String): IpQuality = coroutineScope {
        val waves = listOf(
            listOf(IpQualitySource.IDENT_ME, IpQualitySource.IP_API_COM, IpQualitySource.IP_QUERY),
            listOf(IpQualitySource.IP_LOCATE), listOf(IpQualitySource.IP_API_IS), listOf(IpQualitySource.PROXY_CHECK),
        )
        val answers = Channel<Pair<IpQualitySource, Result<IpQualityAnswer>>>(Channel.UNLIMITED)
        val failures = mutableMapOf<IpQualitySource, IpSourceFailure>()
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        var fallback: IpQuality? = null
        var wave = 0
        var inFlight = 0
        fun launchWave() {
            if (wave >= waves.size) return
            for (source in waves[wave++]) {
                inFlight++
                jobs += launch {
                    val result = try {
                        Result.success(withTimeout(8_000L) { parseIpQuality(source, ip, runtime.queryIpMetadata(source.url(ip))) })
                    } catch (error: TimeoutCancellationException) {
                        Result.failure(IpSourceException(IpSourceFailure.TIMEOUT))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    answers.send(source to result)
                }
            }
        }
        launchWave()
        var hedge = async { delay(1_000L) }
        try {
            while (inFlight > 0) {
                val item = kotlinx.coroutines.selects.select<Pair<IpQualitySource, Result<IpQualityAnswer>>?> {
                    answers.onReceive { it }
                    if (wave < waves.size) hedge.onAwait { null }
                }
                if (item == null) {
                    launchWave()
                    hedge = async { delay(1_000L) }
                    continue
                }
                inFlight--
                val (source, result) = item
                result.onSuccess { answer ->
                    if (!answer.inferred) return@coroutineScope answer.quality
                    if (fallback == null) fallback = answer.quality
                }.onFailure { error ->
                    failures[source] = (error as? IpSourceException)?.failure ?: IpSourceFailure.FAILED
                }
                if (inFlight == 0 && wave < waves.size) {
                    launchWave()
                    hedge.cancelAndJoin()
                    hedge = async { delay(1_000L) }
                }
            }
            fallback ?: throw IpQualityException(failures.toSortedMap(compareBy { it.ordinal }))
        } finally {
            // 作者 long：赢家出现、页面隐藏或路由失效均取消 Kotlin 等待；Go 内部请求仍受每源超时约束。
            hedge.cancel()
            jobs.forEach { it.cancel() }
            answers.close()
        }
    }
}
