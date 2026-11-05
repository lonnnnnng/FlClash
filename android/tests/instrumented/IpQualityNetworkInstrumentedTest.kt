package com.lonnnnnng.flclash.data

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import dagger.hilt.android.EntryPointAccessors
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：无Activity轮询干扰，每个源走正式Runtime；第三方受限是结果，不能冒充类型判定成功。 */
class IpQualityNetworkInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val entry get() = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
    private val backup get() = EncryptedDraftFile(context, "ip-quality-acceptance.v1", "com.lonnnnnng.flclash.acceptance.ip-quality")
    private data class Snapshot(val profiles: String, val files: Map<Long, String>, val selected: Long,
        val vpn: Boolean, val mode: CoreOutboundMode, val cache: AppPreferenceEntity?, val pid: Int,
        val autoEnabled: List<Long>, val quality: IpQuality? = null, val checkedAt: Long? = null)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun digest(value: Any) = sha(Gson().toJson(value).toByteArray())
    private fun signature(id: Long) = entry.profileRepository().profileFile(id).let { "${it.name}:${it.length()}:${it.lastModified()}:${sha(it.readBytes())}" }
    private fun report(value: JSONObject) = InstrumentationRegistry.getInstrumentation().sendStatus(2,
        Bundle().apply { putString("kclash_ip_quality", value.toString()) })
    private fun gate() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashIpQualityAcceptance"))
        assertPortraitDefaultFontConfiguration()
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
    }
    private suspend fun snapshot(): Snapshot {
        check(backup.read() == null) { "先恢复上次IP质量验收" }
        val rows = entry.database().profiles().getAll()
        val selected = withTimeout(20_000) { entry.profileRepository().currentProfileId.first { id -> rows.any { it.id == id } } }!!
        val expected = InstrumentationRegistry.getArguments().getString("kclashAuthorizedSourceSha256")
        require(expected?.matches(Regex("[0-9a-f]{64}")) == true)
        check(rows.single { it.id == selected }.let { it.type == ProfileType.URL && sha(it.url.toByteArray()) == expected })
        val cache = entry.database().appPreferences().getByPrefix(IP_QUALITY_CACHE_KEY).singleOrNull { it.key == IP_QUALITY_CACHE_KEY }
        return Snapshot(digest(rows), rows.associate { it.id to signature(it.id) }, selected,
            entry.vpnSettingsStore().read(), entry.runtimeManager().outboundMode.value, cache, android.os.Process.myPid(), rows.filter { it.autoUpdate }.map { it.id })
    }
    private suspend fun freezeAutomaticUpdates(saved: Snapshot) {
        // 作者 long：barrier不跨进程存活；开关先落盘，冷启动间隙也不能让到期Worker改写正式源。
        saved.autoEnabled.forEach { id -> entry.database().profiles().getById(id)?.let {
            entry.profileRepository().updateProfileAutoUpdate(id, false, it.autoUpdateIntervalMillis)
        } }
    }
    private suspend fun restore(saved: Snapshot) {
        val runtime = entry.runtimeManager()
        runtime.stop().getOrThrow()
        runtime.setOutboundMode(saved.mode).getOrThrow()
        runtime.setVpnEnabled(saved.vpn).getOrThrow()
        val dao = entry.database().appPreferences()
        saved.cache?.let { dao.put(it) } ?: dao.delete(IP_QUALITY_CACHE_KEY)
        saved.autoEnabled.forEach { id -> entry.database().profiles().getById(id)?.let {
            entry.profileRepository().updateProfileAutoUpdate(id, true, it.autoUpdateIntervalMillis)
        } }
        val rows = entry.database().profiles().getAll()
        assertEquals(saved.profiles, digest(rows))
        assertEquals(saved.files, rows.associate { it.id to signature(it.id) })
        assertEquals(saved.selected, entry.profileRepository().currentProfileId.value)
        assertTrue("原质量缓存行必须完整恢复", saved.cache == dao.getByPrefix(IP_QUALITY_CACHE_KEY).singleOrNull { it.key == IP_QUALITY_CACHE_KEY })
        assertFalse(ServiceConfig.proxyAlive.value); assertFalse(ServiceConfig.vpnAlive.value)
        backup.write(null); assertNull(backup.read())
        report(JSONObject().put("phase", "restored").put("formalProfiles", rows.size))
    }
    private fun fixture(routed: Boolean, block: suspend () -> Unit): Unit = runBlocking {
        gate()
        entry.profileRepository().withAutomaticUpdateBarrier {
            val saved = snapshot()
            backup.write(Gson().toJson(saved).toByteArray())
            try {
                freezeAutomaticUpdates(saved)
                if (routed) {
                    entry.runtimeManager().setVpnEnabled(false).getOrThrow()
                    entry.runtimeManager().setOutboundMode(CoreOutboundMode.RULE).getOrThrow()
                    entry.runtimeManager().startProxy().getOrThrow()
                }
                val since = entry.runtimeManager().runningSinceElapsedMillis.value
                delay(1_500)
                block()
                assertEquals(since, entry.runtimeManager().runningSinceElapsedMillis.value)
                assertEquals(if (routed) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
            } catch (error: Throwable) {
                report(JSONObject().put("phase", "failed").put("type", error.javaClass.simpleName)
                    .put("at", error.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" }))
                // 作者 long：核心异常可能包含节点/地址；命令输出只给安全定位，原配置仅在设备内核对摘要。
                throw AssertionError("IP质量验收失败：${error.javaClass.simpleName}")
            } finally { withContext(NonCancellable) { restore(saved) } }
        }
    }

    @Test fun allEightOutboundAndSixQualitySourcesDirect() = sources(routed = false)
    @Test fun allEightOutboundAndSixQualitySourcesInRunningRuleMode() = sources(routed = true)
    private fun sources(routed: Boolean) = fixture(routed) {
        assertEquals(8, outboundIpSources.distinct().size)
        assertEquals(6, IpQualitySource.entries.size)
        val runtime = entry.runtimeManager()
        var realIp: String? = null
        var valid = 0
        outboundIpSources.forEachIndexed { index, url ->
            val row = JSONObject().put("phase", "outbound").put("routed", routed).put("source", index)
            try {
                val response = runtime.queryOutboundIp(listOf(url))
                row.put("http", response.statusCode).put("bytes", response.body.toByteArray().size).put("bodySha256", sha(response.body.toByteArray()))
                val result = runCatching { parseOutboundIp(response) }
                result.onSuccess { info ->
                    valid++; if (realIp == null) realIp = info.ip
                    row.put("parsed", true).put("ipSha256", sha(info.ip.toByteArray()))
                }.onFailure { row.put("parsed", false).put("failure", if (response.error in listOf("timeout", "failed")) response.error else "invalid-response") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { row.put("parsed", false).put("failure", error.javaClass.simpleName) }
            report(row)
        }
        assertTrue("至少一个源必须返回真实出口", valid > 0)
        val ip = checkNotNull(realIp)
        var classified = 0
        IpQualitySource.entries.forEach { source ->
            val row = JSONObject().put("phase", "quality-source").put("routed", routed).put("source", source.name).put("ipSha256", sha(ip.toByteArray()))
            try {
                val response = runtime.queryIpMetadata(source.url(ip))
                row.put("http", response.statusCode).put("bytes", response.body.toByteArray().size).put("bodySha256", sha(response.body.toByteArray()))
                val answer = parseIpQuality(source, ip, response)
                classified++
                row.put("parsed", true).put("type", answer.quality.type.name).put("inferred", answer.inferred)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { row.put("parsed", false).put("failure", (error as? IpSourceException)?.failure?.name ?: error.javaClass.simpleName) }
            report(row)
        }
        report(JSONObject().put("phase", "source-summary").put("routed", routed).put("outboundParsed", valid).put("qualityParsed", classified))
        assertTrue("至少一个质量源必须完成真实类型解析，其他源受限逐项保留", classified > 0)
    }

    @Test fun allThirteenServiceTargetsDirect() = services(routed = false)
    @Test fun allThirteenServiceTargetsInRunningRuleMode() = services(routed = true)
    private fun services(routed: Boolean) = fixture(routed) {
        assertEquals(13, ServiceTarget.entries.size)
        var judged = 0
        for (targets in ServiceTarget.entries.chunked(3)) {
            val values = stableServiceCheck(targets)
            assertEquals(targets.toSet(), values.map { it.target }.toSet())
            values.forEach { value ->
                assertTrue(value.checkedAt > 0)
                if (!value.status.retryable) judged++
                if (!routed) assertTrue(value.chains.all { it == "DIRECT" })
                report(JSONObject().put("phase", "service").put("routed", routed).put("target", value.target.id)
                    .put("status", value.status.wire).put("checkedAt", value.checkedAt).put("epoch", value.stamp.epoch)
                    .put("routeVersion", value.stamp.version).put("hitCount", value.hits.size)
                    .put("hasNonDirectChain", value.chains.any { it != "DIRECT" }))
            }
        }
        assertTrue("全网失败不能伪装完整服务验收", judged > 0)
    }

    private suspend fun stableServiceCheck(targets: List<ServiceTarget>): List<CoreServiceCheck> {
        var last: ServiceRouteChangedException? = null
        repeat(3) { attempt ->
            try { return entry.runtimeManager().checkServices(targets) }
            catch (error: ServiceRouteChangedException) {
                last = error
                report(JSONObject().put("phase", "service-retry").put("targets", targets.map { it.id })
                    .put("attempt", attempt + 1).put("reason", "route-changed"))
                delay(1_500L)
            }
        }
        throw checkNotNull(last)
    }

    @Test fun prepareSuccessfulQualityForRealProcessRestart(): Unit = runBlocking {
        gate()
        entry.profileRepository().withAutomaticUpdateBarrier {
            val saved = snapshot()
            backup.write(Gson().toJson(saved).toByteArray())
            try {
                freezeAutomaticUpdates(saved)
                val ip = entry.ipInformationStore().outbound().info.ip
                val quality = entry.ipInformationStore().quality(ip, force = true)
                val record = checkNotNull(RoomIpQualityCache(entry.database()).fresh(ip, System.currentTimeMillis()))
                assertTrue("在线成功结果必须已写入正式Room", quality == record.quality)
                backup.write(Gson().toJson(saved.copy(quality = quality, checkedAt = record.checkedAt)).toByteArray())
                report(JSONObject().put("phase", "cold-prepared").put("pid", saved.pid).put("source", quality.source.name)
                    .put("qualitySha256", digest(quality)).put("checkedAt", record.checkedAt))
            } catch (error: Throwable) {
                withContext(NonCancellable) { restore(saved) }
                throw AssertionError("冷缓存准备失败：${error.javaClass.simpleName}")
            }
        }
    }

    @Test fun verifyDifferentProcessReadsOriginalQualityWithoutNetwork(): Unit = runBlocking {
        gate()
        entry.profileRepository().withAutomaticUpdateBarrier {
            val saved = Gson().fromJson(checkNotNull(backup.read()).decodeToString(), Snapshot::class.java)
            try {
                assertNotEquals(saved.pid, android.os.Process.myPid())
                val quality = checkNotNull(saved.quality)
                var calls = 0
                val refusing = object : OutboundIpRuntime by entry.runtimeManager() {
                    override suspend fun queryIpMetadata(url: String): CoreIpResponse { calls++; error("冷缓存不应请求外网") }
                }
                val repository = IpInformationRepository(refusing, android.os.SystemClock::elapsedRealtime,
                    System::currentTimeMillis, RoomIpQualityCache(entry.database()))
                // 作者 long：断言失败也只输出业务原因，JUnit默认对象差异会泄露真实IP及组织信息。
                assertTrue("拒绝联网时应回读原成功结果", quality == repository.quality(quality.ip, false)); assertEquals(0, calls)
                assertTrue("正式DI应读回同一质量结果", quality == entry.ipInformationStore().quality(quality.ip, false))
                val row = checkNotNull(RoomIpQualityCache(entry.database()).fresh(quality.ip, System.currentTimeMillis()))
                assertEquals(saved.checkedAt, row.checkedAt)
                report(JSONObject().put("phase", "cold-restored").put("preparedPid", saved.pid).put("pid", android.os.Process.myPid())
                    .put("metadataCallsOnRefusingRuntime", calls).put("formalDiMatches", true).put("originalTimestampPreserved", true)
                    .put("qualitySha256", digest(quality)))
            } finally { withContext(NonCancellable) { restore(saved) } }
        }
    }

    @Test fun restoreInterruptedIpQualityAcceptance(): Unit = runBlocking {
        gate()
        entry.profileRepository().withAutomaticUpdateBarrier {
            backup.read()?.let { restore(Gson().fromJson(it.decodeToString(), Snapshot::class.java)) }
        }
    }
}
