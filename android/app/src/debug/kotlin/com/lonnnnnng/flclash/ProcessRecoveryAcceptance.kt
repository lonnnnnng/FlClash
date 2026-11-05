package com.lonnnnnng.flclash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.data.AppPreferenceEntity
import com.lonnnnnng.flclash.data.EncryptedDraftFile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private const val RECOVERY_TAG = "KClashProcessAcceptance"
private const val RESUME_KEY = "runtime_should_resume"
private data class RecoverySnapshot(
    val token: String, val sourceSha256: String, val profilesSha256: String,
    val files: Map<Long, String>, val selected: Long, val vpn: Boolean,
    val mode: CoreOutboundMode, val autoEnabled: List<Long>, val resume: AppPreferenceEntity?,
    val pid: Int, val preparedAt: Long, val ready: Boolean = false, val readyAt: Long = 0,
)
private fun recoveryBackup(context: Context) = EncryptedDraftFile(context,
    "proxy-process-acceptance.v1", "com.lonnnnnng.flclash.acceptance.proxy-process")
private fun recoverySha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun recoveryReport(token: String, phase: String, detail: JSONObject = JSONObject()) {
    Log.i(RECOVERY_TAG, detail.put("token", token).put("phase", phase).put("pid", Process.myPid())
        .put("epochMs", System.currentTimeMillis()).toString())
}

/** 作者 long：独立Debug宿主避免instrumentation退出杀进程，系统拉起必须先由外部观察，再打开verify读取。 */
class ProcessRecoveryAcceptanceActivity : ComponentActivity() {
    private val entry by lazy { EntryPointAccessors.fromApplication(application, ConnectionAcceptanceEntryPoint::class.java) }
    private val backup by lazy { recoveryBackup(this) }
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "KClash Debug 进程验收" })
        val token = intent.getStringExtra("token").orEmpty()
        require(token.matches(Regex("[0-9a-f]{32}")))
        require(intent.getBooleanExtra("acceptance", false))
        val command = intent.getStringExtra("command")
        // 作者 long：正式下载器由调用方选择IO上下文；验收不能在Activity主线程进行订阅请求或TLS握手。
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                when (command) {
                    "prepare" -> prepare(token)
                    "verify" -> verify(token, intent.getIntExtra("expectedPid", -1))
                    "restore" -> restore(token)
                    else -> error("未知验收阶段")
                }
            } catch (failure: Throwable) {
                // 作者 long：核心和下载异常可能含真实订阅信息，外部日志只保留安全类型和源码位置。
                recoveryReport(token, "failed", JSONObject().put("command", command).put("type", failure.javaClass.simpleName)
                    .put("at", failure.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" }))
            } finally { withContext(Dispatchers.Main) { finish() } }
        }
    }

    private suspend fun signatures() = withContext(Dispatchers.IO) {
        entry.database().profiles().getAll().associate { row ->
            val file = entry.profileRepository().profileFile(row.id)
            check(file.isFile && file.length() > 0)
            row.id to "${file.length()}:${file.lastModified()}:${recoverySha(file.readBytes())}"
        }
    }
    private suspend fun rowsSha() = recoverySha(gson.toJson(entry.database().profiles().getAll().sortedBy { it.id }).toByteArray())
    private suspend fun saved(token: String): RecoverySnapshot = gson.fromJson(
        checkNotNull(backup.read()).decodeToString(), RecoverySnapshot::class.java).also { check(it.token == token) }

    private suspend fun prepare(token: String) {
        val manager = entry.runtimeManager()
        check(manager.state.value == CoreRuntimeStatus.STOPPED)
        check(backup.read() == null) { "先恢复上一轮验收" }
        entry.profileRepository().withAutomaticUpdateBarrier {
            val rows = entry.database().profiles().getAll()
            val id = withTimeout(20_000) { entry.profileRepository().currentProfileId.first { id -> rows.any { it.id == id } } }!!
            val sourceSha = intent.getStringExtra("sourceSha256").orEmpty()
            require(sourceSha.matches(Regex("[0-9a-f]{64}")))
            val row = rows.single { it.id == id }
            check(row.type == ProfileType.URL && recoverySha(row.url.toByteArray()) == sourceSha)
            val resume = entry.database().appPreferences().getByPrefix(RESUME_KEY).singleOrNull { it.key == RESUME_KEY }
            check(resume?.value != "true")
            val snapshot = RecoverySnapshot(token, sourceSha, rowsSha(), signatures(), id, entry.vpnSettingsStore().read(),
                manager.outboundMode.value, rows.filter { it.autoUpdate }.map { it.id }, resume, Process.myPid(), System.currentTimeMillis())
            backup.write(gson.toJson(snapshot).toByteArray())
            try {
                // 作者 long：系统恢复窗口跨进程，必须先落盘暂停自动更新，不能仅依赖本进程的互斥锁保护正式源。
                snapshot.autoEnabled.forEach { enabled -> rows.single { it.id == enabled }.let {
                    entry.profileRepository().updateProfileAutoUpdate(enabled, false, it.autoUpdateIntervalMillis)
                } }
                val cached = withContext(Dispatchers.IO) { entry.profileRepository().profileFile(id).readBytes() }
                val downloaded = entry.subscriptionClient().download(row.url)
                val online = entry.profileContentDecoder().decode(downloaded.bytes, row.ageSecretKey)
                fun inventory(bytes: ByteArray): JSONObject {
                    val root = JsonParser.parseString(YamlConfigCodec.toJson(bytes.decodeToString())).asJsonObject
                    val groups = root.getAsJsonArray("proxy-groups")
                    val fallback = groups?.filter { it.asJsonObject["type"]?.asString == "fallback" }.orEmpty()
                    return JSONObject().put("sha256", recoverySha(bytes)).put("bytes", bytes.size).put("groups", groups?.size() ?: 0)
                        .put("fallback", fallback.size).put("fallbackWithMembers", fallback.count {
                            val group = it.asJsonObject
                            (group.getAsJsonArray("proxies")?.size() ?: 0) > 0 || (group.getAsJsonArray("use")?.size() ?: 0) > 0
                        })
                }
                recoveryReport(token, "fallback-inventory", JSONObject().put("cached", inventory(cached)).put("online", inventory(online)))
                manager.setVpnEnabled(false).getOrThrow()
                manager.setOutboundMode(CoreOutboundMode.RULE).getOrThrow()
                manager.startProxy().getOrThrow()
                val result = runningEvidence(snapshot)
                val readyAt = System.currentTimeMillis()
                backup.write(gson.toJson(snapshot.copy(ready = true, readyAt = readyAt)).toByteArray())
                recoveryReport(token, "ready", result.put("preparedAt", snapshot.preparedAt).put("readyAt", readyAt))
            } catch (failure: Throwable) {
                withContext(NonCancellable) { restore(token) }
                throw failure
            }
        }
    }

    private suspend fun runningEvidence(snapshot: RecoverySnapshot): JSONObject {
        val manager = entry.runtimeManager()
        withTimeout(40_000) {
            while (manager.state.value != CoreRuntimeStatus.RUNNING) {
                check(manager.state.value != CoreRuntimeStatus.FAILED)
                delay(100)
            }
        }
        check(ServiceConfig.proxyAlive.value && ServiceConfig.proxyState.value == VpnRuntimeState.CONNECTED)
        check(!ServiceConfig.vpnAlive.value && manager.vpnSettings.value.appliedEnabled == false)
        check(entry.database().appPreferences().get(RESUME_KEY) == "true")
        check(entry.profileRepository().currentProfileId.value == snapshot.selected)
        val row = checkNotNull(entry.database().profiles().getById(snapshot.selected))
        check(recoverySha(row.url.toByteArray()) == snapshot.sourceSha256)
        val config = withContext(Dispatchers.IO) { JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject }
        val port = listOf("mixed-port", "socks-port").firstNotNullOfOrNull { key -> config[key]?.asInt?.takeIf { it > 0 } }
            ?: error("没有SOCKS监听器")
        val result = proxyRequest(URI(row.url), port)
        return result.put("running", true).put("proxyConnected", true).put("vpnAlive", false).put("resume", true).put("port", port)
    }

    private suspend fun proxyRequest(target: URI, port: Int): JSONObject = withContext(Dispatchers.IO) { coroutineScope {
        check(target.scheme == "https" && target.host != null && target.port in listOf(-1, 443))
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).use { socket ->
            socket.connect(InetSocketAddress.createUnresolved(target.host, 443), 20_000)
            socket.soTimeout = 20_000
            val observing = async { withTimeout(20_000) {
                while (true) {
                    entry.coreBridge().getConnections().singleOrNull {
                        it.metadata.sourcePort == socket.localPort.toString() && it.metadata.destinationPort == "443" && it.upload > 0 && it.download > 0
                    }?.let { return@withTimeout it }
                    delay(50)
                }
                @Suppress("UNREACHABLE_CODE") error("未捕获本次真实连接")
            } }
            (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, target.host, 443, true).use { secure ->
                secure as SSLSocket
                secure.sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                secure.startHandshake()
                val path = target.rawPath.ifBlank { "/" } + target.rawQuery?.let { "?$it" }.orEmpty()
                // 作者 long：只请求唯一授权地址并校验证书主机名；读取有限响应头，不输出地址、响应头或正文，不跟随重定向。
                secure.getOutputStream().apply {
                    write("GET $path HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: clash-verge/Platform/android\r\nConnection: keep-alive\r\n\r\n".toByteArray()); flush()
                }
                val input = secure.getInputStream().buffered()
                var budget = 32768
                fun line(): String = buildString {
                    while (true) { check(budget-- > 0); val ch = input.read(); check(ch >= 0); if (ch == 10) break; if (ch != 13) append(ch.toChar()) }
                }
                val status = line().split(' ').getOrNull(1)?.toIntOrNull()
                check(status == 200)
                while (line().isNotEmpty()) Unit
                val connection = observing.await()
                JSONObject().put("http", status).put("attributedSocket", true).put("uploadPositive", connection.upload > 0)
                    .put("downloadPositive", connection.download > 0).put("hasNonDirectChain", connection.chains.any { it != "DIRECT" })
            }
        }
    } }

    private suspend fun verify(token: String, expectedPid: Int) {
        val snapshot = saved(token)
        try {
            check(snapshot.ready && Process.myPid() == expectedPid && snapshot.pid != expectedPid)
            // 作者 long：verify禁止调用start/restart/apply；新PID须先由系统独立拉起，否则打开验收页本身就会制造假恢复。
            val evidence = runningEvidence(snapshot)
            // 作者 long：同一进程可验多轮，准备启动也可能叫CORE_RESTARTED；只接受READY之后的新记录，不能复用旧进程事件。
            val restarted = checkNotNull(entry.runtimeManager().diagnosticEvents.value.lastOrNull {
                it.kind == RuntimeDiagnosticKind.CORE_RESTARTED && snapshot.readyAt > 0 && it.timestampMillis > snapshot.readyAt
            })
            recoveryReport(token, "verified", evidence.put("preparedPid", snapshot.pid).put("restartDiagnostic", true)
                .put("restartAt", restarted.timestampMillis).put("readyAt", snapshot.readyAt))
        } finally { withContext(NonCancellable) { restore(token) } }
    }

    private suspend fun restore(token: String) {
        if (backup.read() == null) { recoveryReport(token, "already-restored"); return }
        val snapshot = saved(token)
        val manager = entry.runtimeManager()
        manager.stop().getOrThrow()
        manager.setOutboundMode(snapshot.mode).getOrThrow()
        manager.setVpnEnabled(snapshot.vpn).getOrThrow()
        snapshot.autoEnabled.forEach { id -> checkNotNull(entry.database().profiles().getById(id)).let {
            entry.profileRepository().updateProfileAutoUpdate(id, true, it.autoUpdateIntervalMillis)
        } }
        val dao = entry.database().appPreferences()
        snapshot.resume?.let { dao.put(it) } ?: dao.delete(RESUME_KEY)
        check(rowsSha() == snapshot.profilesSha256 && signatures() == snapshot.files)
        check(entry.profileRepository().currentProfileId.value == snapshot.selected)
        check(!ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
        backup.write(null)
        check(backup.read() == null)
        recoveryReport(token, "restored", JSONObject().put("formalRowsAndFilesPreserved", true).put("backupCleared", true))
    }
}

/** 作者 long：只允许持有DUMP权限的调试shell按本轮token和仍存活PID触发SIGKILL，避免开放通用杀进程入口。 */
class ProcessRecoveryKillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val token = intent.getStringExtra("token").orEmpty()
                check(token.matches(Regex("[0-9a-f]{32}")))
                val snapshot = Gson().fromJson(checkNotNull(recoveryBackup(context).read()).decodeToString(), RecoverySnapshot::class.java)
                check(snapshot.ready && snapshot.token == token && snapshot.pid == Process.myPid())
                check(intent.getIntExtra("expectedPid", -1) == Process.myPid())
                recoveryReport(token, "kill-accepted")
                Process.killProcess(Process.myPid())
            } catch (failure: Throwable) {
                recoveryReport(intent.getStringExtra("token").orEmpty(), "kill-rejected",
                    JSONObject().put("type", failure.javaClass.simpleName))
            } finally { pending.finish() }
        }
    }
}
