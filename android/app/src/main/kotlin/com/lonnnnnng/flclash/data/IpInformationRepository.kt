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

data class RoutedIpInfo(val info: OutboundIpInfo, val stamp: CoreRouteStamp)

interface IpInformationStore {
    suspend fun outbound(): RoutedIpInfo
    suspend fun quality(ip: String, force: Boolean = false): IpQuality
}

@Singleton
class IpInformationRepository internal constructor(
    private val runtime: OutboundIpRuntime,
    private val elapsedMillis: () -> Long,
) : IpInformationStore {
    @Inject constructor(runtime: OutboundIpRuntime) : this(runtime, android.os.SystemClock::elapsedRealtime)
    private data class Cached(val quality: IpQuality?, val failure: IpQualityException?, val at: Long)
    private val cache = linkedMapOf<String, Cached>()

    override suspend fun outbound(): RoutedIpInfo {
        val response = runtime.queryOutboundIp(outboundIpSources)
        return RoutedIpInfo(parseOutboundIp(response), response.stamp)
    }

    override suspend fun quality(ip: String, force: Boolean): IpQuality {
        require(isPublicIpLiteral(ip)) { "IP 地址无效" }
        val now = elapsedMillis()
        if (!force) synchronized(cache) { cache[ip] }?.let { entry ->
            val ttl = if (entry.quality != null) 3 * 24 * 3_600_000L else 5 * 60_000L
            if (now >= entry.at && now - entry.at < ttl) {
                entry.quality?.let { return it }
                throw checkNotNull(entry.failure)
            }
        }
        try {
            return lookupQuality(ip).also { remember(ip, Cached(it, null, elapsedMillis())) }
        } catch (error: IpQualityException) {
            remember(ip, Cached(null, error, elapsedMillis()))
            throw error
        }
    }

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
