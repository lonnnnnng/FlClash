package com.lonnnnnng.flclash

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.lonnnnnng.flclash.data.AppPreferenceEntity
import com.lonnnnnng.flclash.data.EncryptedDraftFile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.Core
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

private const val SYSTEM_VPN_TAG = "KClashSystemVpnAcceptance"
private const val RESUME_PREFERENCE = "runtime_should_resume"
private data class VpnSystemState(val alwaysOn: Boolean, val lockdown: Boolean, val protection: SocketProtectionSnapshot)
private data class SystemVpnSnapshot(
    val token: String, val sourceSha: String, val rowsSha: String, val files: Map<Long, String>,
    val selected: Long, val vpn: Boolean, val mode: CoreOutboundMode, val autoEnabled: List<Long>,
    val resume: AppPreferenceEntity?, val pid: Int, val preparedAt: Long,
    val verifiedPid: Int = -1, val verifiedAt: Long = 0,
)
private fun systemVpnBackup(context: Context) = EncryptedDraftFile(context,
    "system-vpn-acceptance.v1", "com.lonnnnnng.flclash.acceptance.system-vpn")
private fun vpnSha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun vpnReport(token: String, phase: String, detail: JSONObject = JSONObject()) {
    val json = detail.put("token", token).put("phase", phase).put("pid", Process.myPid())
        .put("epochMs", System.currentTimeMillis()).toString()
    if (json.toByteArray().size <= 2800) { Log.i(SYSTEM_VPN_TAG, json); return }
    // 作者 long：logcat会截断长行；用有序分片传输完整脱敏证据，不能靠忽略损坏JSON丢弃失败详情。
    val id = java.util.UUID.randomUUID().toString()
    val chunks = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP).chunked(2800)
    chunks.forEachIndexed { index, data ->
        Log.i(SYSTEM_VPN_TAG, JSONObject().put("token", token).put("phase", "evidence-chunk")
            .put("id", id).put("index", index).put("count", chunks.size).put("data", data).toString())
    }
}
private fun vpnFailure(failure: Throwable): JSONObject {
    // 作者 long：TLS异常原文可能含目标地址；只保留类型链和固定分类，既能定位断流又不泄露授权路径。
    val causes = generateSequence(failure) { it.cause }.take(8).toList()
    val messages = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
    val categories = mapOf(
        "certificate" to listOf("certificate", "certpath", "trust anchor"),
        "hostname" to listOf("hostname", "subject alternative"),
        "eof" to listOf("eof", "closed", "closure"),
        "reset" to listOf("reset", "broken pipe"),
        "timeout" to listOf("timeout", "timed out"),
        "dns" to listOf("dns resolve failed", "couldn't find ip", "no such host"),
        "deadline" to listOf("context deadline exceeded"),
        "unreachable" to listOf("network unreachable", "no route to host", "connection refused"),
        "tls-alert" to listOf("alert", "protocol version", "handshake failure"),
    ).filterValues { words -> words.any { it in messages } }.keys
    return JSONObject().put("type", failure.javaClass.simpleName)
        .put("causes", org.json.JSONArray(causes.map { it.javaClass.simpleName }))
        .put("categories", org.json.JSONArray(categories.toList()))
        .put("at", failure.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" })
}

/** 作者 long：外部先观察系统拉起的新PID/前台服务/TUN，再允许本宿主只读核验，避免Activity制造恢复。 */
class SystemVpnAcceptanceActivity : ComponentActivity() {
    private val entry by lazy { EntryPointAccessors.fromApplication(application, ConnectionAcceptanceEntryPoint::class.java) }
    private val backup by lazy { systemVpnBackup(this) }
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra("token").orEmpty()
        require(token.matches(Regex("[0-9a-f]{32}")) && intent.getBooleanExtra("acceptance", false))
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                (application as KClashApplication).awaitRuntimeInitialization()
                when (intent.getStringExtra("command")) {
                    "prepare" -> prepare(token)
                    "verify" -> verify(token)
                    "restore" -> restore(token)
                    else -> error("未知系统VPN验收阶段")
                }
            } catch (failure: Throwable) {
                vpnReport(token, "failed", vpnFailure(failure))
            } finally { withContext(Dispatchers.Main) { finish() } }
        }
    }

    private suspend fun signatures() = entry.database().profiles().getAll().associate { row ->
        val file = entry.profileRepository().profileFile(row.id)
        check(file.isFile && file.length() > 0)
        row.id to "${file.length()}:${file.lastModified()}:${vpnSha(file.readBytes())}"
    }
    private suspend fun rowsSha() = vpnSha(gson.toJson(entry.database().profiles().getAll().sortedBy { it.id }).toByteArray())
    private suspend fun saved(token: String) = gson.fromJson(checkNotNull(backup.read()).decodeToString(), SystemVpnSnapshot::class.java)
        .also { check(it.token == token) }

    private suspend fun prepare(token: String) {
        val manager = entry.runtimeManager()
        check(manager.state.value == CoreRuntimeStatus.STOPPED && backup.read() == null)
        check(android.net.VpnService.prepare(this) == null) { "只验既有授权，不冒充首次安装" }
        entry.profileRepository().withAutomaticUpdateBarrier {
            val rows = entry.database().profiles().getAll()
            val id = withTimeout(20_000) { entry.profileRepository().currentProfileId.first { id -> rows.any { it.id == id } } }!!
            val sourceSha = intent.getStringExtra("sourceSha256").orEmpty()
            require(sourceSha.matches(Regex("[0-9a-f]{64}")))
            val row = rows.single { it.id == id }
            check(row.type == ProfileType.URL && vpnSha(row.url.toByteArray()) == sourceSha)
            val resume = entry.database().appPreferences().getByPrefix(RESUME_PREFERENCE).singleOrNull { it.key == RESUME_PREFERENCE }
            check(resume?.value != "true")
            val snapshot = SystemVpnSnapshot(token, sourceSha, rowsSha(), signatures(), id, entry.vpnSettingsStore().read(),
                manager.outboundMode.value, rows.filter { it.autoUpdate }.map { it.id }, resume, Process.myPid(), System.currentTimeMillis())
            backup.write(gson.toJson(snapshot).toByteArray())
            // 作者 long：跨进程窗口先暂停自动更新，并故意保留STOPPED/纯代理偏好/无恢复标记，证明系统入口独立有效。
            snapshot.autoEnabled.forEach { enabled -> rows.single { it.id == enabled }.let {
                entry.profileRepository().updateProfileAutoUpdate(enabled, false, it.autoUpdateIntervalMillis)
            } }
            manager.setVpnEnabled(false).getOrThrow()
            manager.setOutboundMode(CoreOutboundMode.RULE).getOrThrow()
            check(!ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
            check(entry.database().appPreferences().get(RESUME_PREFERENCE) != "true")
            vpnReport(token, "ready", JSONObject().put("resume", false).put("vpnPreference", false).put("formalCount", rows.size))
        }
    }

    private suspend fun systemMode(): VpnSystemState = withContext(Dispatchers.Main) {
        check(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
        var bound = false
        lateinit var connection: ServiceConnection
        try {
            withTimeout(5_000) {
                suspendCancellableCoroutine { continuation ->
                    connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            val vpn = (service as VpnService.LocalBinder).service
                            if (continuation.isActive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                continuation.resume(VpnSystemState(vpn.isAlwaysOn, vpn.isLockdownEnabled, vpn.socketProtectionSnapshot()))
                            }
                        }
                        override fun onServiceDisconnected(name: ComponentName?) = Unit
                    }
                    // 作者 long：flags=0禁止绑定时创建服务；外部未观察到真实VPN时不能靠校验宿主补启动。
                    bound = bindService(Intent(this@SystemVpnAcceptanceActivity, VpnService::class.java), connection, 0)
                    check(bound)
                }
            }
        } finally { if (bound) unbindService(connection) }
    }

    private suspend fun verify(token: String) {
        val verifyAt = System.currentTimeMillis()
        val snapshot = saved(token)
        val manager = entry.runtimeManager()
        check(Process.myPid() == intent.getIntExtra("expectedPid", -1) && Process.myPid() != snapshot.pid)
        check(snapshot.verifiedPid != Process.myPid())
        withTimeout(40_000) {
            while (manager.state.value != CoreRuntimeStatus.RUNNING) {
                check(manager.state.value != CoreRuntimeStatus.FAILED); delay(100)
            }
        }
        check(ServiceConfig.proxyState.value == VpnRuntimeState.CONNECTED && ServiceConfig.vpnState.value == VpnRuntimeState.CONNECTED)
        check(manager.vpnSettings.value.enabled && manager.vpnSettings.value.appliedEnabled == true)
        check(entry.database().appPreferences().get(RESUME_PREFERENCE) == "true")
        check(entry.profileRepository().currentProfileId.value == snapshot.selected)
        val mode = systemMode()
        check(mode.alwaysOn && !mode.lockdown)
        val row = checkNotNull(entry.database().profiles().getById(snapshot.selected))
        check(vpnSha(row.url.toByteArray()) == snapshot.sourceSha)
        // 作者 long：用户关闭日志时仅临时打开核心事件泵，不写偏好、不重载配置；握手完成/失败都归还原关闭状态。
        val temporaryLogs = !manager.generalSettings.value.openLogs
        val previousLogLevel = manager.generalSettings.value.logLevel
        val evidence = try {
            entry.coreBridge().updateLogLevel("debug")
            if (temporaryLogs) entry.coreBridge().startLog()
            try {
                tunnelRequest(URI(row.url), verifyAt)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                // 作者 long：首请求失败始终是验收失败；同会话补诊断单独标记，不重启/重载或以重试覆盖失败。
                vpnReport(token, "primary-request-failed", vpnFailure(failure))
                val physical = try { physicalRequest(URI(row.url)) }
                catch (next: Exception) { vpnFailure(next).put("success", false) }
                vpnReport(token, "diagnostic-physical", physical)
                repeat(2) { index ->
                    delay(3_000)
                    val detail = try { tunnelRequest(URI(row.url), verifyAt).put("success", true) }
                    catch (next: Exception) {
                        if (next is CancellationException) throw next
                        vpnFailure(next).put("success", false)
                    }
                    vpnReport(token, "diagnostic-repeat", detail.put("attempt", index + 1))
                }
                throw failure
            }
        } finally {
            withContext(NonCancellable) {
                try { entry.coreBridge().updateLogLevel(previousLogLevel) }
                finally { if (temporaryLogs) entry.coreBridge().stopLog() }
            }
        }
        val previousAt = if (snapshot.verifiedAt > 0) snapshot.verifiedAt else snapshot.preparedAt
        val diagnostic = checkNotNull(manager.diagnosticEvents.value.lastOrNull {
            it.timestampMillis > previousAt && it.kind in setOf(RuntimeDiagnosticKind.CORE_STARTED, RuntimeDiagnosticKind.CORE_RESTARTED)
        })
        val at = System.currentTimeMillis()
        backup.write(gson.toJson(snapshot.copy(verifiedPid = Process.myPid(), verifiedAt = at)).toByteArray())
        vpnReport(token, "verified", evidence.put("alwaysOn", mode.alwaysOn).put("lockdown", mode.lockdown)
            .put("running", true).put("resume", true).put("diagnostic", diagnostic.kind.name)
            .put("diagnosticAt", diagnostic.timestampMillis).put("previousAt", previousAt))
    }

    private fun networkSnapshot(network: android.net.Network): JSONObject {
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(network)
        val links = connectivity.getLinkProperties(network)
        return JSONObject().put("handle", network.networkHandle).put("present", caps != null)
            .put("vpn", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))
            .put("validated", caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .put("internet", caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .put("dnsHashes", org.json.JSONArray(links?.dnsServers.orEmpty().map { vpnSha(it.address) }))
            .put("interfaceSha256", links?.interfaceName?.let { vpnSha(it.toByteArray()) })
    }

    private fun networkEvidence(): JSONObject {
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        return JSONObject().put("observer", JSONObject(gson.toJson(NetworkObserver.snapshot())))
            .put("coreBootstrap", JSONObject(Core.networkBootstrapSnapshot()))
            .put("networks", org.json.JSONArray(connectivity.allNetworks.map(::networkSnapshot)))
    }

    private fun physicalRequest(target: URI): JSONObject {
        // 作者 long：只在首请求失败后对同一授权目标做底层网络对照；绕过TUN的成功不能记为VPN验收通过。
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val underlying = connectivity.allNetworks.single { it.networkHandle == NetworkObserver.snapshot().networkHandle }
        check(connectivity.getNetworkCapabilities(underlying)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == false)
        val connection = underlying.openConnection(target.toURL()) as javax.net.ssl.HttpsURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "clash-verge/Platform/android")
            val code = connection.responseCode
            JSONObject().put("http", code).put("success", code == 200).put("network", networkSnapshot(underlying))
        } finally { connection.disconnect() }
    }

    private suspend fun tunnelRequest(target: URI, verifyAt: Long): JSONObject = supervisorScope {
        check(target.scheme == "https" && target.host != null && target.port in listOf(-1, 443))
        // 作者 long：VPN提供者自身的普通TCP可绕过VPN；沿用既有VpnDns实网测试，显式绑定已建立的VPN网络。
        // DNS也走同一网络，不能拿VPN返回的fake-ip到物理网络直连；全程不配置SOCKS/HTTP代理。
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val network = connectivity.allNetworks.single { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        }
        val existing = entry.coreBridge().getConnections().map { it.id }.toSet()
        network.socketFactory.createSocket().use { socket ->
            // 作者 long：先固定源端口并订阅事件，DNS/connect阶段的短命失败也必须纳入本次请求窗口。
            socket.bind(InetSocketAddress(0))
            val sourcePort = socket.localPort
            Core.beginDialDiagnostic(sourcePort)
            try {
                var address: java.net.InetAddress? = null
                val trace = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
                fun traceSnapshot() = synchronized(trace) { org.json.JSONArray(trace.toList()) }
                val beforeProtection = systemMode().protection
                var ancillaryCount = 0
                val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                    entry.coreBridge().events.collect { event ->
                        val detail = when (event) {
                            is com.lonnnnnng.flclash.core.CoreEvent.Log -> {
                                val payload = event.payload
                                val destination = payload.substringAfter(" --> ", "")
                                val targetMatches = listOfNotNull("${target.host}:443 ", address?.hostAddress?.let { "$it:443 " }).any {
                                    destination.startsWith(it, ignoreCase = true)
                                }
                                val categories = vpnFailure(java.io.IOException(payload)).getJSONArray("categories")
                                if (payload.contains("[TCP]") && payload.contains(":$sourcePort --> ") && targetMatches) {
                                    JSONObject().put("kind", if (" error: " in payload) "TCP_DIAL_FAILED" else "TCP_UPSTREAM_OPENED")
                                        .put("at", event.timestampMillis).put("failure", categories)
                                        .put("errorEndpointHashes", org.json.JSONArray(Regex("(?:[A-Za-z0-9-]+\\.)+[A-Za-z0-9-]+")
                                            .findAll(payload.substringAfter("error:", "")).map { vpnSha(it.value.toByteArray()) }.distinct().toList()))
                                } else if (categories.length() > 0 && ("dns" in payload.lowercase() || "context deadline exceeded" in payload)) {
                                    // 作者 long：配置初载的健康检查可能产生大量旁路DNS日志；为本请求TCP事件保留证据空间。
                                    if (ancillaryCount++ >= 8) return@collect
                                    JSONObject().put("kind", "CORE_NETWORK_DIAGNOSTIC").put("at", event.timestampMillis)
                                        .put("failure", categories).put("payloadSha256", vpnSha(payload.toByteArray()))
                                        .put("mentionsRequestHost", payload.contains(target.host, ignoreCase = true))
                                } else null
                            }
                            is com.lonnnnnng.flclash.core.CoreEvent.Request -> event.connection.takeIf {
                                it.id !in existing && it.metadata.sourcePort == sourcePort.toString() &&
                                    it.metadata.destinationPort == "443" && (it.metadata.host.equals(target.host, ignoreCase = true) || it.metadata.destinationIp == address?.hostAddress)
                            }?.let { JSONObject().put("kind", "TCP_TRACKER_CREATED").put("id", it.id)
                                .put("upload", it.upload).put("download", it.download) }
                            else -> null
                        }
                        if (detail != null && trace.size < 64) trace.add(detail)
                    }
                }
                try {
                    vpnReport(intent.getStringExtra("token").orEmpty(), "request-ready", JSONObject()
                        .put("vpnNetwork", network.networkHandle).put("networkState", networkEvidence())
                        .put("targetHostSha256", vpnSha(target.host.toByteArray())))
                    val dnsAt = System.currentTimeMillis()
                    address = network.getAllByName(target.host).first { it is java.net.Inet4Address }
                    vpnReport(intent.getStringExtra("token").orEmpty(), "request-dns", JSONObject().put("millis", System.currentTimeMillis() - dnsAt)
                        .put("addressSha256", vpnSha(checkNotNull(address).address)))
                    socket.connect(InetSocketAddress(address, 443), 20_000)
                    socket.soTimeout = 20_000
                    // 作者 long：TLS包装关闭原socket后localAddress会变成通配地址，先固定真实端点，避免失败诊断失真。
                    val sourceIp = socket.localAddress.hostAddress
                    val observing = async { withTimeout(20_000) {
                        while (true) {
                            entry.coreBridge().getConnections().singleOrNull {
                                it.id !in existing && it.metadata.network.equals("tcp", ignoreCase = true) &&
                                    it.metadata.sourceIp == sourceIp && it.metadata.sourcePort == sourcePort.toString() &&
                                    it.metadata.destinationPort == "443" && (it.metadata.host.equals(target.host, ignoreCase = true) ||
                                    it.metadata.destinationIp == socket.inetAddress.hostAddress) && it.upload > 0 && it.download > 0
                            }?.let { return@withTimeout it }
                            delay(50)
                        }
                        @Suppress("UNREACHABLE_CODE") error("未观察到本次TUN连接")
                    } }
                    try { (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, target.host, 443, true).use { secure ->
                        secure as SSLSocket
                        secure.sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                        secure.startHandshake()
                        val path = target.rawPath.ifBlank { "/" } + target.rawQuery?.let { "?$it" }.orEmpty()
                        secure.getOutputStream().apply {
                            write("GET $path HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: clash-verge/Platform/android\r\nConnection: keep-alive\r\n\r\n".toByteArray()); flush()
                        }
                        val input = secure.getInputStream().buffered(); var budget = 32768
                        fun line(): String = buildString {
                            while (true) { check(budget-- > 0); val ch = input.read(); check(ch >= 0); if (ch == 10) break; if (ch != 13) append(ch.toChar()) }
                        }
                        val http = line().split(' ').getOrNull(1)?.toIntOrNull(); check(http == 200)
                        while (line().isNotEmpty()) Unit
                        val connection = observing.await()
                        JSONObject().put("http", http).put("tunnelSocketAttributed", true).put("uploadPositive", connection.upload > 0)
                            .put("downloadPositive", connection.download > 0).put("hasNonDirectChain", connection.chains.any { it != "DIRECT" })
                            .put("connectionId", connection.id).put("connectionStart", connection.start)
                            .put("sourceIp", connection.metadata.sourceIp).put("sourcePort", connection.metadata.sourcePort)
                            .put("socketSourceIp", socket.localAddress.hostAddress).put("socketSourcePort", socket.localPort)
                            .put("destinationIp", connection.metadata.destinationIp).put("socketDestinationIp", socket.inetAddress.hostAddress)
                            .put("targetHostSha256", vpnSha(target.host.toByteArray()))
                            .put("connectionHostSha256", vpnSha(connection.metadata.host.toByteArray()))
                            .put("coreTrace", traceSnapshot()).put("networkState", networkEvidence())
                            .put("dialDiagnostic", org.json.JSONArray(Core.dialDiagnosticSnapshot(sourcePort, false)))
                            .put("protectionBefore", JSONObject(gson.toJson(beforeProtection)))
                            .put("protectionAfter", JSONObject(gson.toJson(systemMode().protection)))
                    } } finally { observing.cancelAndJoin() }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    val afterProtection = systemMode().protection
                    vpnReport(intent.getStringExtra("token").orEmpty(), "request-failed", vpnFailure(failure)
                        .put("sourcePort", sourcePort).put("coreTrace", traceSnapshot()).put("networkState", networkEvidence())
                        .put("dialDiagnostic", org.json.JSONArray(Core.dialDiagnosticSnapshot(sourcePort, false)))
                        .put("protectionBefore", JSONObject(gson.toJson(beforeProtection)))
                        .put("protectionAfter", JSONObject(gson.toJson(afterProtection)))
                        .put("runtimeTimeline", org.json.JSONArray(entry.runtimeManager().diagnosticEvents.value.filter {
                            it.timestampMillis >= verifyAt
                        }.map { JSONObject().put("kind", it.kind.name).put("at", it.timestampMillis) })))
                    throw failure
                } finally { collecting.cancelAndJoin() }
            } finally { Core.dialDiagnosticSnapshot(sourcePort, true) }
        }
    }

    private suspend fun restore(token: String) {
        if (backup.read() == null) { vpnReport(token, "already-restored"); return }
        val snapshot = saved(token)
        // 作者 long：Always-on由系统持有；外部必须先从真实设置页关闭，避免刚恢复原偏好就被系统再次拉起。
        if (ServiceConfig.vpnAlive.value) check(!systemMode().alwaysOn) { "请先在系统设置关闭Always-on" }
        val manager = entry.runtimeManager()
        manager.stop().getOrThrow()
        manager.setOutboundMode(snapshot.mode).getOrThrow()
        manager.setVpnEnabled(snapshot.vpn).getOrThrow()
        val dao = entry.database().appPreferences()
        snapshot.resume?.let { dao.put(it) } ?: dao.delete(RESUME_PREFERENCE)
        // 作者 long：恢复自动更新与逐字段保护检查共用屏障，防止到期Worker在检查完成前提交正式正文。
        // 快照只保留摘要，发现差异必须留证据，不能用旧正文回写掩盖失败。
        entry.profileRepository().withAutomaticUpdateBarrier {
            snapshot.autoEnabled.forEach { id -> checkNotNull(entry.database().profiles().getById(id)).let {
                entry.profileRepository().updateProfileAutoUpdate(id, true, it.autoUpdateIntervalMillis)
            } }
            check(rowsSha() == snapshot.rowsSha && signatures() == snapshot.files)
            check(entry.profileRepository().currentProfileId.value == snapshot.selected)
            check(!ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
            backup.write(null); check(backup.read() == null)
        }
        vpnReport(token, "restored", JSONObject().put("formalRowsAndFilesPreserved", true).put("backupCleared", true))
    }
}

/** 作者 long：仅DUMP权限shell能按本轮已验PID触发自身SIGKILL，不能成为普通应用的通用杀进程接口。 */
class SystemVpnKillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val token = intent.getStringExtra("token").orEmpty()
            try {
                check(token.matches(Regex("[0-9a-f]{32}")))
                val snapshot = Gson().fromJson(checkNotNull(systemVpnBackup(context).read()).decodeToString(), SystemVpnSnapshot::class.java)
                check(snapshot.token == token && snapshot.verifiedAt > 0 && snapshot.verifiedPid == Process.myPid())
                check(intent.getIntExtra("expectedPid", -1) == Process.myPid())
                check(ServiceConfig.vpnState.value == VpnRuntimeState.CONNECTED)
                vpnReport(token, "kill-accepted")
                Process.killProcess(Process.myPid())
            } catch (failure: Throwable) { vpnReport(token, "kill-rejected", JSONObject().put("type", failure.javaClass.simpleName)) }
            finally { pending.finish() }
        }
    }
}
