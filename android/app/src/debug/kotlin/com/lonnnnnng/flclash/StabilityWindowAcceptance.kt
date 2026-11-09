package com.lonnnnnng.flclash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal fun stabilityInput(context: Context) = EncryptedDraftFile(context, "stability-input.v1", "kclash.motion.stability-input")
internal fun stabilitySha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun stabilityReport(token: String, command: String, result: JSONObject) {
    val bytes = result.put("token", token).put("command", command).put("pid", Process.myPid())
        .put("elapsedMs", SystemClock.elapsedRealtime()).put("epochMs", System.currentTimeMillis()).toString().toByteArray()
    val chunks = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP).chunked(2500)
    val sha = stabilitySha(bytes)
    chunks.forEachIndexed { index, chunk -> Log.i("KClashStability", JSONObject().put("token", token)
        .put("command", command).put("sha256", sha).put("bytes", bytes.size).put("index", index)
        .put("count", chunks.size).put("base64", chunk).toString()) }
}

/** 作者 long：长窗仅允许独立motion包，原1.yaml副本由正式Repository导入；URL只作获准HTTPS目标，不新增或改写正式包配置。 */
class StabilityWindowAcceptanceActivity : ComponentActivity() {
    private val entry by lazy { EntryPointAccessors.fromApplication(application, ConnectionAcceptanceEntryPoint::class.java) }
    private lateinit var token: String
    private var step = "initialize"
    private val phases = JSONArray()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.lonnnnnng.flclash.motion")
        token = intent.getStringExtra("token").orEmpty()
        require(token.matches(Regex("[a-f0-9]{32}")))
        val command = intent.getStringExtra("command").orEmpty()
        require(command.matches(Regex("[a-z0-9-]+")))
        setContentView(TextView(this).apply { text = "KClash 独立长窗口验收" })
        lifecycleScope.launch(Dispatchers.IO) {
            val report = JSONObject()
            try {
                val input = JSONObject(checkNotNull(stabilityInput(this@StabilityWindowAcceptanceActivity).read()).decodeToString())
                check(input.getString("token") == token)
                val repo = entry.profileRepository(); val runtime = entry.runtimeManager()
                timed("profile-load", 10_000) { repo.profilesLoaded.first { it } }
                if (command == "start") {
                    check(runtime.state.value == CoreRuntimeStatus.STOPPED)
                    val rows = entry.database().profiles().getAll()
                    check(rows.size == 1 && rows.single().type == ProfileType.FILE)
                    check(stabilitySha(repo.profileFile(rows.single().id).readBytes()) == input.getString("localSha256"))
                    timed("pure-proxy-settings", 10_000) { runtime.setVpnEnabled(false).getOrThrow() }
                    timed("select-local", 30_000) { runtime.selectProfile(rows.single().id, repo::selectProfile) }
                    timed("core-start", 60_000) { runtime.startProxy().getOrThrow() }
                }
                if (command == "stop") timed("core-stop", 30_000) { runtime.stop().getOrThrow() }
                if (command != "stop") {
                    timed("runtime-ready", 40_000) { runtime.state.first { it == CoreRuntimeStatus.RUNNING || it == CoreRuntimeStatus.FAILED } }
                    check(runtime.state.value == CoreRuntimeStatus.RUNNING && !ServiceConfig.vpnAlive.value)
                    val id = checkNotNull(repo.currentProfileId.value)
                    val providers = timed("provider-list", 8_000) { runtime.profileProviders(id) }
                    report.put("providerCount", providers.size)
                    report.put("network", timed("network-total", 65_000) { network(URI(input.getString("authorizedUrl"))) })
                }
                report.put("passed", true)
            } catch (failure: Exception) {
                // 作者 long：保留首次失败及具体等待阶段，不输出包含订阅地址的异常message，也不重试后覆盖本次结果。
                report.put("passed", false).put("failedStep", step).put("failureType", failure.javaClass.simpleName)
            } finally {
                runCatching { report.put("snapshot", snapshot()) }.onFailure { report.put("snapshotFailure", it.javaClass.simpleName) }
                report.put("phases", phases)
                stabilityReport(token, command, report)
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }

    private suspend fun <T> timed(name: String, budget: Long, block: suspend () -> T): T {
        step = name
        val start = SystemClock.elapsedRealtime()
        try {
            return withTimeout(budget) { block() }.also { phases.put(JSONObject().put("name", name).put("startElapsedMs", start)
                .put("durationMs", SystemClock.elapsedRealtime() - start).put("budgetMs", budget).put("passed", true)) }
        } catch (failure: Exception) {
            phases.put(JSONObject().put("name", name).put("startElapsedMs", start).put("durationMs", SystemClock.elapsedRealtime() - start)
                .put("budgetMs", budget).put("passed", false).put("type", failure.javaClass.simpleName))
            throw failure
        }
    }

    private suspend fun network(target: URI): JSONObject = supervisorScope {
        check(target.scheme == "https" && target.host != null && target.port in listOf(-1, 443))
        val config = JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject
        val port = listOf("mixed-port", "socks-port").firstNotNullOf { config[it]?.asInt?.takeIf { p -> p > 0 } }
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).use { socket ->
            timed("socks-connect", 22_000) { socket.connect(InetSocketAddress.createUnresolved(target.host, 443), 20_000) }
            socket.soTimeout = 20_000
            val observed = async { withTimeout(60_000) {
                while (true) {
                    entry.coreBridge().getConnections().firstOrNull { it.metadata.sourcePort == socket.localPort.toString() &&
                        it.metadata.destinationPort == "443" && it.upload > 0 && it.download > 0 }?.let { return@withTimeout it }
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE") error("missing")
            } }
            try {
                (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, target.host, 443, true).use { raw ->
                    val tls = raw as SSLSocket
                    tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    timed("tls-handshake", 22_000) { tls.startHandshake() }
                    val status = timed("http-headers", 22_000) {
                        val path = target.rawPath.ifBlank { "/" } + target.rawQuery?.let { "?$it" }.orEmpty()
                        tls.getOutputStream().apply { write("GET $path HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: clash-verge/Platform/android\r\nConnection: keep-alive\r\n\r\n".toByteArray()); flush() }
                        val stream = tls.getInputStream().buffered(); var budget = 32768
                        fun line() = buildString { while (true) { check(budget-- > 0); val c = stream.read(); check(c >= 0); if (c == 10) break; if (c != 13) append(c.toChar()) } }
                        val code = line().split(' ').getOrNull(1)?.toIntOrNull()
                        while (line().isNotEmpty()) Unit
                        check(code == 200); code
                    }
                    val connection = timed("connection-attribution", 20_000) { observed.await() }
                    JSONObject().put("http", status).put("attributed", true).put("positiveTraffic", true)
                        .put("nonDirect", connection.chains.any { it != "DIRECT" })
                }
            } finally { observed.cancelAndJoin() }
        }
    }

    private suspend fun snapshot(): JSONObject {
        val gson = Gson(); val rows = entry.database().profiles().getAll().sortedBy { it.id }
        val fields = JSONObject(); val files = JSONArray()
        rows.forEach { row ->
            fields.put(row.id.toString(), JSONObject(gson.toJsonTree(row).asJsonObject.entrySet().associate { (key, value) ->
                key to stabilitySha(value.toString().toByteArray()) }))
            val file = entry.profileRepository().profileFile(row.id)
            files.put(JSONObject().put("id", row.id).put("bytes", file.length()).put("mtime", file.lastModified())
                .put("sha256", if (file.isFile) stabilitySha(file.readBytes()) else "missing"))
        }
        val preferences = entry.database().appPreferences().getByPrefix("").associate { stabilitySha(it.key.toByteArray()) to stabilitySha(it.value.toByteArray()) }
        val shared = JSONObject()
        File(applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
            val values = getSharedPreferences(file.nameWithoutExtension, Context.MODE_PRIVATE).all
            shared.put(stabilitySha(file.nameWithoutExtension.toByteArray()), JSONObject(values.mapKeys { stabilitySha(it.key.toByteArray()) }
                .mapValues { stabilitySha(gson.toJson(it.value).toByteArray()) }))
        }
        return JSONObject().put("rows", fields).put("files", files).put("roomPreferences", JSONObject(preferences))
            .put("sharedPreferences", shared).put("selected", entry.profileRepository().currentProfileId.value)
            .put("runtime", entry.runtimeManager().state.value.name).put("autoUpdateEnabled", rows.any { it.autoUpdate })
            .put("autoUpdateAudit", SubscriptionAutoUpdateAudit.read(this))
    }
}

/** 作者 long：仅本轮motion输入令牌与当前PID同时匹配才允许受控死亡，避免对正式包或过期进程发信号。 */
class StabilityWindowKillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val token = intent.getStringExtra("token").orEmpty()
            try {
                check(context.packageName == "com.lonnnnnng.flclash.motion")
                val input = JSONObject(checkNotNull(stabilityInput(context).read()).decodeToString())
                check(input.getString("token") == token && token.matches(Regex("[a-f0-9]{32}")))
                check(intent.getIntExtra("expectedPid", -1) == Process.myPid() && ServiceConfig.proxyAlive.value)
                stabilityReport(token, "kill-accepted", JSONObject().put("passed", true))
                Process.killProcess(Process.myPid())
            } catch (failure: Exception) { stabilityReport(token, "kill-rejected", JSONObject().put("passed", false).put("type", failure.javaClass.simpleName)) }
            finally { pending.finish() }
        }
    }
}
