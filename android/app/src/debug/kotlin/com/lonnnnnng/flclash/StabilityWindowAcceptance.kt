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
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest

internal fun stabilityInput(context: Context) = EncryptedDraftFile(context, "stability-input.v1", "kclash.motion.stability-input")
internal fun stabilitySha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun stabilityReport(context: Context, token: String, command: String, result: JSONObject) {
    val bytes = result.put("token", token).put("command", command).put("pid", Process.myPid())
        .put("elapsedMs", SystemClock.elapsedRealtime()).put("epochMs", System.currentTimeMillis()).toString().toByteArray()
    val sha = stabilitySha(bytes)
    if (bytes.size > 24 * 1024) {
        // 作者 long：逐键快照会超过logcat突发配额；仅把脱敏结果写入本包验收目录，外部按SHA完整读取后精确删除。
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "stability-reports").apply { check(isDirectory || mkdirs()) }
        require(token.matches(Regex("[a-f0-9]{32}")) && command.matches(Regex("[a-z0-9-]+")))
        val file = File(directory, "$token-$command-$sha.json")
        check(!file.exists())
        file.writeBytes(bytes)
        Log.i("KClashStability", JSONObject().put("token", token).put("command", command).put("sha256", sha)
            .put("bytes", bytes.size).put("transport", "verified-file-v1").put("path", file.absolutePath).toString())
        return
    }
    val chunks = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP).chunked(2500)
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
                if (command.startsWith("switch-")) {
                    report.put("networkSwitch", timed("network-switch", 60_000) {
                        networkSwitchAcceptance(this@StabilityWindowAcceptanceActivity, entry, input, command, intent.getIntExtra("sourcePort", 0))
                    }).put("passed", true)
                    return@launch
                }
                if (command.startsWith("endpoint-")) {
                    report.put("endpointControl", timed("endpoint-control", 60_000) {
                        endpointControl(this@StabilityWindowAcceptanceActivity, entry, input, command)
                    })
                }
                if (command in setOf("configure-auto", "restore-auto", "manual-sync", "automatic-scan")) {
                    val remote = entry.database().profiles().getAll().single { it.type == ProfileType.URL && it.url == input.getString("authorizedUrl") }
                    when (command) {
                        "configure-auto" -> {
                            check(runtime.state.value == CoreRuntimeStatus.STOPPED && !input.has("autoUpdateOriginal"))
                            input.put("autoUpdateOriginal", JSONObject().put("id", remote.id).put("enabled", remote.autoUpdate).put("interval", remote.autoUpdateIntervalMillis))
                            stabilityInput(this@StabilityWindowAcceptanceActivity).write(input.toString().toByteArray())
                            repo.updateProfileAutoUpdate(remote.id, true, 15 * 60_000)
                        }
                        "restore-auto" -> {
                            check(runtime.state.value == CoreRuntimeStatus.STOPPED)
                            val previous = input.getJSONObject("autoUpdateOriginal"); check(previous.getLong("id") == remote.id)
                            repo.updateProfileAutoUpdate(remote.id, previous.getBoolean("enabled"), previous.getLong("interval"))
                            input.remove("autoUpdateOriginal")
                            stabilityInput(this@StabilityWindowAcceptanceActivity).write(input.toString().toByteArray())
                        }
                        "manual-sync" -> timed("manual-authorized-sync", 90_000) { repo.updateRemoteProfile(remote.id) }
                        "automatic-scan" -> timed("automatic-due-scan", 90_000) { repo.updateDueProfiles(System.currentTimeMillis()) }.also {
                            report.put("automaticAttempted", it.attempted).put("automaticDownloaded", it.succeeded)
                        }
                    }
                }
                if (command == "start" || command.startsWith("start-")) {
                    check(runtime.state.value == CoreRuntimeStatus.STOPPED)
                    val rows = entry.database().profiles().getAll()
                    val local = rows.single { it.type == ProfileType.FILE && if (input.has("localProfileId")) it.id == input.getLong("localProfileId")
                        else stabilitySha(repo.profileFile(it.id).readBytes()) == input.getString("localSha256") }
                    // 作者 long：诊断组复用原FILE和唯一获准URL；新增第二份获准基线不能放宽到任意用户配置。
                    if (input.has("baselineProfileIds")) {
                        val expected = input.getJSONArray("baselineProfileIds").let { ids -> (0 until ids.length()).map { ids.getLong(it) }.toSet() } + local.id
                        check(rows.map { it.id }.toSet() == expected)
                    } else check(rows.size in 1..2 && rows.all { it.id == local.id || it.type == ProfileType.URL && it.url == input.getString("authorizedUrl") })
                    timed("pure-proxy-settings", 10_000) { runtime.setVpnEnabled(false).getOrThrow() }
                    timed("select-local", 30_000) { runtime.selectProfile(local.id, repo::selectProfile) }
                    timed("core-start", 60_000) { runtime.startProxy().getOrThrow() }
                }
                if (command == "stop" || command.startsWith("stop-")) timed("core-stop", 30_000) { runtime.stop().getOrThrow() }
                if (command != "stop" && !command.startsWith("stop-") && !command.startsWith("snapshot-") && !command.startsWith("endpoint-") &&
                    command !in setOf("configure-auto", "restore-auto", "manual-sync", "automatic-scan")) {
                    timed("runtime-ready", 40_000) { runtime.state.first { it == CoreRuntimeStatus.RUNNING || it == CoreRuntimeStatus.FAILED } }
                    check(runtime.state.value == CoreRuntimeStatus.RUNNING && !ServiceConfig.vpnAlive.value)
                    report.put("directRunningObservedElapsedMs", SystemClock.elapsedRealtime())
                        .put("runningSinceElapsedMs", runtime.runningSinceElapsedMillis.value)
                    if (input.has("endpointControl")) report.put("endpointLeaf", timed("endpoint-leaf-lock", 10_000) {
                        endpointControl(this@StabilityWindowAcceptanceActivity, entry, input, "endpoint-lock")
                    })
                    val network = timed("network-total", 90_000) {
                        acceptedProxyRequest(entry, URI(input.getString("authorizedUrl"))) { phase, detail ->
                            stabilityReport(this@StabilityWindowAcceptanceActivity, token, "$command-$phase", detail)
                        }
                    }
                    report.put("network", network)
                    check(network.getBoolean("passed"))
                    check(network.getBoolean("hasNonDirectChain"))
                    val id = checkNotNull(repo.currentProfileId.value)
                    val providers = timed("provider-list", 8_000) { runtime.profileProviders(id) }
                    report.put("providerCount", providers.size)
                }
                report.put("passed", true)
            } catch (failure: Exception) {
                // 作者 long：保留首次失败及具体等待阶段，不输出包含订阅地址的异常message，也不重试后覆盖本次结果。
                report.put("passed", false).put("failedStep", step).put("failureType", failure.javaClass.simpleName)
            } finally {
                runCatching { report.put("snapshot", snapshot()) }.onFailure { report.put("snapshotFailure", it.javaClass.simpleName) }
                report.put("phases", phases)
                stabilityReport(this@StabilityWindowAcceptanceActivity, token, command, report)
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

    private suspend fun snapshot(): JSONObject {
        val started = SystemClock.elapsedRealtime()
        val gson = Gson(); val rows = entry.database().profiles().getAll().sortedBy { it.id }
        val fields = JSONObject(); val files = JSONArray()
        rows.forEach { row ->
            fields.put(row.id.toString(), JSONObject(gson.toJsonTree(row).asJsonObject.entrySet().associate { (key, value) ->
                key to stabilitySha(value.toString().toByteArray()) }))
            val file = entry.profileRepository().profileFile(row.id)
            files.put(JSONObject().put("id", row.id).put("bytes", file.length()).put("mtime", file.lastModified())
                .put("auditFile", JSONObject(com.lonnnnnng.flclash.common.StateWriteAudit.fileState(file)))
                .put("sha256", if (file.isFile) stabilitySha(file.readBytes()) else "missing"))
        }
        val preferenceRows = entry.database().appPreferences().getByPrefix("")
        val preferences = preferenceRows.associate { stabilitySha(it.key.toByteArray()) to stabilitySha(it.value.toByteArray()) }
        val preferenceTimes = preferenceRows.associate { stabilitySha(it.key.toByteArray()) to it.updatedAt }
        val delays = JSONObject()
        for (row in rows) entry.database().proxyDelayResults().load(row.id).forEach { value ->
            // 作者 long：测速按四列复合主键核对，值与时间单独摘要，不能按查询返回顺序误判节点身份丢失。
            val key = gson.toJson(listOf(value.profileId, value.groupName, value.proxyName, value.testUrl))
            delays.put(stabilitySha(key.toByteArray()), JSONObject().put("valueHash", stabilitySha(gson.toJson(value).toByteArray()))
                .put("updatedAtMillis", value.updatedAtMillis))
        }
        val shared = JSONObject()
        val sharedFiles = File(applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty().filter { it.extension == "xml" }
        sharedFiles.forEach { file ->
            val values = getSharedPreferences(file.nameWithoutExtension, Context.MODE_PRIVATE).all
            shared.put(stabilitySha(file.nameWithoutExtension.toByteArray()), JSONObject(values.mapKeys { stabilitySha(it.key.toByteArray()) }
                .mapValues { stabilitySha(gson.toJson(it.value).toByteArray()) }))
        }
        val auditedDelays = StateAuditSnapshots.delays(entry.database(), rows.map { it.id })
        val auditedPreferences = StateAuditSnapshots.preferences(this, sharedFiles.map { it.nameWithoutExtension })
        return JSONObject().put("snapshotStartedElapsedMs", started).put("snapshotFinishedElapsedMs", SystemClock.elapsedRealtime())
            .put("rows", fields).put("files", files).put("roomPreferences", JSONObject(preferences))
            .put("auditDelaySnapshot", auditedDelays).put("auditSharedPreferenceSnapshot", auditedPreferences)
            .put("auditStatus", JSONObject(com.lonnnnnng.flclash.common.StateWriteAudit.sink?.status().orEmpty()))
            .put("auditProfileFields", JSONObject(rows.associate { row -> row.id.toString() to JSONObject(gson.toJsonTree(row).asJsonObject.entrySet()
                .associate { it.key to com.lonnnnnng.flclash.common.StateWriteAudit.value(it.value.toString()) }) }))
            .put("auditPreferenceFields", JSONObject(preferenceRows.associate { row ->
                (com.lonnnnnng.flclash.common.StateWriteAudit.digest(row.key.toByteArray()) ?: "disabled") to JSONObject(gson.toJsonTree(row).asJsonObject.entrySet()
                    .associate { it.key to com.lonnnnnng.flclash.common.StateWriteAudit.value(it.value.toString()) }) }))
            .put("roomPreferenceUpdatedAt", JSONObject(preferenceTimes)).put("delayResults", delays)
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
                stabilityReport(context, token, "kill-accepted", JSONObject().put("passed", true))
                Process.killProcess(Process.myPid())
            } catch (failure: Exception) { stabilityReport(context, token, "kill-rejected", JSONObject().put("passed", false).put("type", failure.javaClass.simpleName)) }
            finally { pending.finish() }
        }
    }
}
