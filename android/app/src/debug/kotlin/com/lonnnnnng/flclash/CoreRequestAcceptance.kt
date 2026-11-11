package com.lonnnnnng.flclash

import android.os.Process
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.Core
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.service.NetworkObserver
import com.lonnnnnng.flclash.service.YamlConfigCodec
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private fun requestHash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

internal enum class AcceptanceRequestFault { CLOSE_AFTER_SOCKS, TIMEOUT_AFTER_SOCKS }

/** 作者 long：仅Debug验收共享逐阶段诊断；不重试、不改节点，SOCKS成功也不提前认定真实出站成功。 */
internal suspend fun acceptedProxyRequest(entry: ConnectionAcceptanceEntryPoint, target: URI,
    fault: AcceptanceRequestFault? = null,
    emit: (String, JSONObject) -> Unit): JSONObject = supervisorScope {
    require(target.scheme == "https" && target.host != null && target.port in listOf(-1, 443))
    val bridge = entry.coreBridge(); val runtime = entry.runtimeManager()
    val result = JSONObject().put("passed", false).put("pid", Process.myPid()).put("targetHostHash", requestHash(target.host))
        .put("injectedFault", fault?.name ?: JSONObject.NULL)
        .put("runtimeAtEntry", runtime.state.value.name).put("runningSinceElapsedMs", runtime.runningSinceElapsedMillis.value)
        .put("requestEntryElapsedMs", SystemClock.elapsedRealtime())
    val phases = JSONArray()
    suspend fun <T> phase(name: String, budget: Long, block: suspend () -> T): T {
        val start = SystemClock.elapsedRealtime()
        emit("wait-begin", JSONObject().put("name", name).put("atElapsedMs", start).put("budgetMs", budget))
        try {
            return withTimeout(budget) { block() }.also {
                val event = JSONObject().put("name", name).put("startElapsedMs", start)
                    .put("durationMs", SystemClock.elapsedRealtime() - start).put("passed", true)
                phases.put(event); emit("wait-end", event)
            }
        } catch (failure: Exception) {
            result.put("failedStep", name).put("failureType", failure.javaClass.simpleName)
            val event = JSONObject().put("name", name).put("startElapsedMs", start)
                .put("durationMs", SystemClock.elapsedRealtime() - start).put("passed", false).put("type", failure.javaClass.simpleName)
            phases.put(event); emit("wait-failed", event)
            throw failure
        }
    }
    fun networkSnapshot() = JSONObject().put("androidElapsedMs", SystemClock.elapsedRealtime())
        .put("coreBootstrap", JSONObject(Core.networkBootstrapSnapshot()))
        .put("observer", JSONObject(Gson().toJson(NetworkObserver.snapshot())))
    val oldLevel = runtime.generalSettings.value.logLevel
    val temporaryLogs = !runtime.generalSettings.value.openLogs
    var loggerChanged = false
    val dns = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
    var droppedDns = 0
    val beforeStats = bridge.eventFlowStats.value
    try {
        val oldConnections = phase("existing-connections", 3_000) { bridge.getConnections().map { it.id }.toSet() }
        // 作者 long：超时回执不能证明核心未执行改级；发出命令前就登记恢复责任，失败同样恢复原日志等级。
        loggerChanged = true
        phase("diagnostic-log-level", 3_000) { bridge.updateLogLevel("debug") }
        if (temporaryLogs) phase("diagnostic-log-start", 3_000) { bridge.startLog() }
        val config = JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject
        val port = listOf("mixed-port", "socks-port").firstNotNullOf { config[it]?.asInt?.takeIf { p -> p > 0 } }
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).use { socket ->
            socket.bind(InetSocketAddress("127.0.0.1", 0))
            val sourcePort = socket.localPort
            result.put("sourcePort", sourcePort).put("networkBefore", networkSnapshot())
            Core.beginDialDiagnostic(sourcePort)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                bridge.events.collect { event ->
                    if (event is CoreEvent.Dns) synchronized(dns) {
                        if (dns.size >= 64) { droppedDns++; return@synchronized }
                        val q = event.query
                        // 作者 long：DNS没有socket关联ID，只保留时间窗候选摘要；不能把健康检查DNS误认成本次拨号。
                        dns += JSONObject().put("domainHash", requestHash(q.domain.lowercase())).put("type", q.type)
                            .put("initiator", q.initiator).put("cached", q.cached).put("rcode", q.rcode)
                            .put("failed", q.error.isNotEmpty()).put("errorHash", if (q.error.isEmpty()) JSONObject.NULL else requestHash(q.error))
                            .put("errorCategories", JSONArray(q.errorCategories)).put("addressAnswers", q.addressAnswers)
                            .put("upstreamKnown", q.upstream.isNotBlank())
                            .put("upstreamHash", requestHash(q.upstream)).put("answers", q.answers.size)
                            .put("delayMs", q.delay).put("coreTime", q.time).put("receivedElapsedMs", SystemClock.elapsedRealtime())
                    }
                }
            }
            var observation: Deferred<com.lonnnnnng.flclash.core.CoreConnection>? = null
            try {
                phase("socks-connect", 22_000) { socket.connect(InetSocketAddress.createUnresolved(target.host, 443), 20_000) }
                socket.soTimeout = 20_000
                // 作者 long：受控故障仅验证诊断自身的失败与取消收尾；结果显式标注，绝不计作历史网络问题复现。
                if (fault == AcceptanceRequestFault.TIMEOUT_AFTER_SOCKS) phase("injected-timeout", 20) {
                    result.put("faultApplied", true)
                    awaitCancellation()
                }
                if (fault == AcceptanceRequestFault.CLOSE_AFTER_SOCKS) {
                    socket.close()
                    result.put("faultApplied", socket.isClosed)
                }
                val sourceIp = socket.localAddress.hostAddress
                observation = async {
                    withTimeout(60_000) {
                        while (true) {
                            withTimeout(3_000) { bridge.getConnections() }.firstOrNull {
                                it.id !in oldConnections && it.metadata.network.equals("tcp", true) &&
                                    it.metadata.sourceIp == sourceIp && it.metadata.sourcePort == sourcePort.toString() &&
                                    it.metadata.destinationPort == "443" && it.metadata.host.equals(target.host, true) &&
                                    it.upload > 0 && it.download > 0
                            }?.let { return@withTimeout it }
                            delay(100)
                        }
                        @Suppress("UNREACHABLE_CODE") error("connection missing")
                    }
                }
                phase("tls-create", 3_000) {
                    (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, target.host, 443, true)
                }.use { raw ->
                    val tls = raw as SSLSocket
                    tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    phase("tls-handshake", 22_000) { tls.startHandshake() }
                    phase("http-write", 5_000) {
                        val path = target.rawPath.ifBlank { "/" } + target.rawQuery?.let { "?$it" }.orEmpty()
                        tls.getOutputStream().apply {
                            write("GET $path HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: clash-verge/Platform/android\r\nConnection: keep-alive\r\n\r\n".toByteArray()); flush()
                        }
                    }
                    val stream = tls.getInputStream().buffered(); var left = 32768
                    fun line() = buildString {
                        while (true) { check(left-- > 0); val c = stream.read(); check(c >= 0); if (c == 10) break; if (c != 13) append(c.toChar()) }
                    }
                    phase("http-status", 22_000) {
                        val code = line().split(' ').getOrNull(1)?.toIntOrNull()
                        result.put("http", code); check(code == 200) { "HTTP status rejected" }; code
                    }
                    phase("http-headers", 22_000) { while (line().isNotEmpty()) Unit }
                    val connection = phase("connection-attribution", 20_000) { checkNotNull(observation).await() }
                    result.put("passed", true).put("attributedSocket", true).put("positiveTraffic", true)
                        .put("hasNonDirectChain", connection.chains.any { it != "DIRECT" })
                        .put("chainHashes", JSONArray(connection.chains.map(::requestHash)))
                        .put("connectionIdHash", requestHash(connection.id))
                }
            } catch (failure: Exception) {
                result.put("passed", false).put("failureType", failure.javaClass.simpleName)
                if (failure is CancellationException) throw failure
            } finally {
                withContext(NonCancellable) {
                    observation?.cancelAndJoin(); collector.cancelAndJoin()
                    result.put("dialDiagnostic", JSONArray(Core.dialDiagnosticSnapshot(sourcePort, true)))
                        .put("networkAfter", networkSnapshot())
                }
            }
        }
    } catch (failure: Exception) {
        result.put("passed", false).put("failureType", failure.javaClass.simpleName)
        if (failure is CancellationException) throw failure
    } finally {
        withContext(NonCancellable) {
            if (loggerChanged) {
                val restored = runCatching { withTimeout(3_000) { bridge.updateLogLevel(oldLevel) } }.isSuccess
                val stopped = !temporaryLogs || runCatching { withTimeout(3_000) { bridge.stopLog() } }.isSuccess
                result.put("diagnosticLogRestored", restored && stopped)
                if (!restored || !stopped) result.put("passed", false)
            }
            synchronized(dns) { result.put("dnsWindowCandidates", JSONArray(dns.toList())).put("dnsOverflow", droppedDns) }
            result.put("dnsSocketAttributionClaimed", false).put("phases", phases)
                .put("eventStatsBefore", JSONObject(Gson().toJson(beforeStats)))
                .put("eventStatsAfter", JSONObject(Gson().toJson(bridge.eventFlowStats.value)))
            emit("request-result", result)
        }
    }
    result
}
