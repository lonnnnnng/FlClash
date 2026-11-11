package com.lonnnnnng.flclash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import org.json.JSONObject

private const val PROVIDER_TAG = "KClashProviderRuntime"
private fun providerBackup(context: Context) = EncryptedDraftFile(context,
    "provider-runtime-acceptance.v1", "com.lonnnnnng.flclash.acceptance.provider-runtime")
private fun providerSha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun providerReport(token: String, phase: String, detail: JSONObject = JSONObject()) {
    val text = detail.put("token", token).put("phase", phase).put("pid", Process.myPid())
        .put("epochMs", System.currentTimeMillis()).put("elapsedMs", android.os.SystemClock.elapsedRealtime()).toString()
    if (text.toByteArray().size <= 2800) Log.i(PROVIDER_TAG, text)
    else {
        // 作者 long：拨号/DNS摘要可能超过单条Logcat预算，分片校验避免恢复失败时恰好丢掉最需要的证据。
        val raw = text.toByteArray(); val hash = providerSha(raw)
        val chunks = android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP).chunked(2200)
        chunks.forEachIndexed { index, chunk -> Log.i(PROVIDER_TAG, JSONObject().put("token", token).put("phase", "chunk")
            .put("sourcePhase", phase).put("sha256", hash).put("bytes", raw.size).put("index", index).put("count", chunks.size)
            .put("base64", chunk).toString()) }
    }
}
private data class ProviderRuntimeSnapshot(
    val token: String, val case: String, val profiles: List<ProfileEntity>, val files: Map<Long, String>,
    val preferences: Map<String, String>, val scripts: String, val selected: Long, val vpn: Boolean,
    val mode: CoreOutboundMode, val sourceId: Long, val customId: Long, val targetId: Long,
    val originalSha: String, val urlSha: String, val preparedPid: Int,
    val sourceSignature: String = "", val intendedSha: String = "", val readyAt: Long = 0,
) {
    val label get() = "provider-runtime-$token"
    val alias get() = "provider-$token"
    val group get() = "provider-group-$token"
    val changedNode get() = "provider-saved-$token"
}

/** 作者 long：真实系统恢复不能由instrumentation重启制造；本宿主只在Debug通过DUMP权限开放，并用密文轮次限定恢复范围。 */
@AndroidEntryPoint
class ProviderRuntimeAcceptanceActivity : ComponentActivity() {
    private val entry by lazy { EntryPointAccessors.fromApplication(application, ConnectionAcceptanceEntryPoint::class.java) }
    private val model: ProfileStatisticsViewModel by viewModels()
    private val backup by lazy { providerBackup(this) }
    private val gson = Gson()
    @Volatile private var acceptanceStep = "initialize"

    private suspend fun <T> waitStep(name: String, budget: Long, block: suspend () -> T): T {
        val token = intent.getStringExtra("token").orEmpty()
        acceptanceStep = name
        val start = android.os.SystemClock.elapsedRealtime()
        providerReport(token, "wait-begin", JSONObject().put("name", name).put("budgetMs", budget).put("startElapsedMs", start))
        try {
            return withTimeout(budget) { block() }.also {
                providerReport(token, "wait-end", JSONObject().put("name", name).put("durationMs", android.os.SystemClock.elapsedRealtime() - start))
            }
        } catch (failure: Exception) {
            providerReport(token, "wait-failed", JSONObject().put("name", name).put("type", failure.javaClass.simpleName)
                .put("durationMs", android.os.SystemClock.elapsedRealtime() - start).put("budgetMs", budget))
            throw failure
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "KClash Debug Provider 验收" })
        val token = intent.getStringExtra("token").orEmpty()
        require(token.matches(Regex("[0-9a-f]{32}")) && intent.getBooleanExtra("acceptance", false))
        val command = intent.getStringExtra("command")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                when (command) {
                    "preflight" -> {
                        // 作者 long：部分ROM拒绝run-as；只读密文门禁必须在外部隔离进程前完成，不能先杀进程再猜备份不存在。
                        check(backup.read() == null)
                        waitStep("preflight-initialization", 15_000) { (application as KClashApplication).awaitRuntimeInitialization() }
                        check(entry.runtimeManager().state.value == CoreRuntimeStatus.STOPPED)
                        check(!ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
                        check(entry.database().appPreferences().get("runtime_should_resume") != "true")
                        providerReport(token, "preflight", JSONObject().put("noBackup", true).put("stopped", true))
                    }
                    "prepare" -> prepare(token)
                    "verify" -> verify(token, intent.getIntExtra("expectedPid", -1))
                    "broken-switch" -> brokenSwitch(token)
                    "diagnostic-failure" -> diagnosticFailure(token)
                    "restore" -> restore(token)
                    else -> error("未知验收阶段")
                }
            } catch (failure: Throwable) {
                val document = model.document.value
                // 作者 long：协程超时可能没有业务栈；记录不含正文的阶段和状态，区分网络、重绑定与提交等待。
                providerReport(token, "failed", JSONObject().put("command", command).put("type", failure.javaClass.simpleName)
                    .put("step", acceptanceStep).put("runtime", entry.runtimeManager().state.value.name)
                    .put("proxyState", ServiceConfig.proxyState.value.name).put("proxyAlive", ServiceConfig.proxyAlive.value)
                    .put("vpnAlive", ServiceConfig.vpnAlive.value).put("appliedVpn", entry.runtimeManager().vpnSettings.value.appliedEnabled)
                    .put("documentPresent", document != null).put("documentBound", document?.document != null)
                    .put("documentLoading", document?.loading).put("documentSaving", document?.saving)
                    .put("documentError", document?.error != null).put("recoveryBlocked", document?.recoveryBlocked)
                    // 作者 long：正文错误可能经过核心映射；只发固定分类和摘要，不能为诊断把远端正文或节点文本写进日志。
                    .put("documentErrorHash", document?.error?.let { providerSha(it.toByteArray()) })
                    .put("documentErrorCategory", when (document?.error) {
                        null -> "none"
                        "已有 Provider 正在更新，请稍后重试", "Provider 正在更新，请稍后重试" -> "provider-updating"
                        "内容无法被此 Provider 读取，请检查格式后重试" -> "provider-content-error"
                        "配置或文件已变化，请重新打开后再保存" -> "provider-stale"
                        "编辑会话已失效，请重新打开" -> "provider-session-invalid"
                        "Provider 已提交，但配置已变化，请重新读取确认" -> "provider-committed-stale"
                        "Provider 已提交，但回读确认失败，请重新读取确认" -> "provider-committed-readback"
                        "Provider 保存未完成，请检查内容并重试" -> "provider-save-failed"
                        "保存失败，请检查内容后重试" -> "provider-unmapped-failure"
                        else -> "other"
                    })
                    .put("intent", EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT))
                    .put("committed", EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED))
                    .put("at", failure.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" }))
            } finally { withContext(Dispatchers.Main) { finish() } }
        }
    }

    private fun signature(file: File) = if (!file.exists()) "missing" else "${file.length()}:${file.lastModified()}:${providerSha(file.readBytes())}"
    private suspend fun preferences() = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
    private suspend fun scripts() = providerSha(gson.toJson(entry.database().scripts().getAll().sortedBy { it.id }).toByteArray())
    private suspend fun saved(token: String) = gson.fromJson(checkNotNull(backup.read()).decodeToString(), ProviderRuntimeSnapshot::class.java)
        .also { check(it.token == token) }
    private suspend fun persist(snapshot: ProviderRuntimeSnapshot) { backup.write(gson.toJson(snapshot).toByteArray()) }
    private fun cache(snapshot: ProviderRuntimeSnapshot) = File(filesDir, "mihomo/providers/profiles/${snapshot.sourceId}-${snapshot.originalSha}.yaml")
    private suspend fun initialize(token: String): ProviderRuntimeSnapshot = entry.profileRepository().withAutomaticUpdateBarrier {
        val e = entry; val repo = e.profileRepository(); val runtime = e.runtimeManager()
        repo.profilesLoaded.first { it }
        check(runtime.state.value == CoreRuntimeStatus.STOPPED && !ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
        check(backup.read() == null && e.providerDocumentStore().read() == null && e.providerPickerStore().read() == null)
        check(e.profileDraftStore().read() == null && e.profileManagementStore().read() == null && e.profileExtensionStore().read() == null)
        val rows = e.database().profiles().getAll().sortedBy { it.id }
        val originalSha = intent.getStringExtra("localSha256").orEmpty()
        val urlSha = intent.getStringExtra("sourceSha256").orEmpty()
        require(listOf(originalSha, urlSha).all { it.matches(Regex("[0-9a-f]{64}")) })
        val original = rows.single { it.type == ProfileType.FILE && providerSha(repo.profileFile(it.id).readBytes()) == originalSha }
        check(rows.count { it.type == ProfileType.URL && providerSha(it.url.toByteArray()) == urlSha } == 1)
        val selected = checkNotNull(repo.currentProfileId.value)
        val id = System.nanoTime().and(Long.MAX_VALUE)
        check(rows.none { it.id in id..id + 2 })
        val snapshot = ProviderRuntimeSnapshot(token, intent.getStringExtra("case").orEmpty(), rows,
            rows.associate { it.id to signature(repo.profileFile(it.id)) }, preferences(), scripts(), selected,
            e.vpnSettingsStore().read(), runtime.outboundMode.value, id, id + 1, id + 2, originalSha, urlSha, Process.myPid())
        require(snapshot.case in setOf("before", "after", "stopped-file", "stopped-row", "running-file", "running-row", "diagnostic-close", "diagnostic-timeout"))
        check(snapshot.preferences["runtime_should_resume"] != "true")
        persist(snapshot)
        // 作者 long：先持久化独占ID，再暂停正式源自动更新；跨进程窗口不能依赖本进程互斥锁保护正式正文。
        e.database().withTransaction {
            rows.filter { it.autoUpdate }.forEach { e.database().profiles().update(it.copy(autoUpdate = false)) }
            listOf(snapshot.sourceId to "source", snapshot.customId to "custom", snapshot.targetId to "target").forEachIndexed { index, (own, suffix) ->
                e.database().profiles().insert(Profile.custom(own, "${snapshot.label}-$suffix")
                    .copy(type = if (suffix == "custom") ProfileType.CUSTOM else ProfileType.FILE, orderIndex = rows.size + index).toEntity())
            }
        }
        val bytes = repo.profileFile(original.id).readBytes()
        listOf(snapshot.sourceId, snapshot.customId, snapshot.targetId).forEach { own ->
            repo.profileFile(own).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        }
        val group = ProxyGroupDraft(json = gson.toJson(mapOf("name" to snapshot.group, "type" to "select", "proxies" to listOf("DIRECT"), "use" to listOf(snapshot.alias))))
        repo.saveCustomProxyGroups(snapshot.customId, ProxyGroupsSave(ProxyGroupDrafts.read(bytes.decodeToString()) + group,
            mapOf(snapshot.alias to snapshot.sourceId)), bytes.decodeToString()) { true }
        repo.updateProfileSelectedMap(snapshot.customId, mapOf(snapshot.group to "DIRECT"))
        runtime.setVpnEnabled(false).getOrThrow()
        runtime.selectProfile(snapshot.customId, repo::selectProfile)
        runtime.getProxyGroups(snapshot.customId).getOrThrow()
        check(cache(snapshot).readBytes().contentEquals(bytes))
        snapshot.copy(sourceSignature = signature(repo.profileFile(snapshot.sourceId))).also { persist(it) }
    }

    private suspend fun prepare(token: String) {
        var snapshot = initialize(token)
        check(snapshot.case in setOf("before", "after"))
        val runtime = entry.runtimeManager()
        waitStep("prepare-core-start", 60_000) { runtime.startProxy().getOrThrow() }
        val since = checkNotNull(runtime.runningSinceElapsedMillis.value)
        acceptanceStep = "prepare-network"; proxyEvidence(snapshot)
        withContext(Dispatchers.Main) { model.setForeground(true); model.show(snapshot.customId) }
        acceptanceStep = "prepare-provider-list"
        waitStep("prepare-provider-list", 30_000) { model.providers.first { it.providers?.any { row -> row.name == snapshot.alias } == true && !it.isLoading } }
        withContext(Dispatchers.Main) { model.openProvider(snapshot.alias, edit = true) }
        val original = waitStep("prepare-document-bind", 20_000) { model.document.first { it?.draft != null && !it.loading } }!!
        check(original.error == null && original.editing)
        val root = JsonParser.parseString(YamlConfigCodec.toJson(original.content)).asJsonObject
        // 作者 long：只改原1.yaml副本中一个节点的名称，核心成员变化才足以证明侧载已应用，注释变化只能证明文件写入。
        root.getAsJsonArray("proxies").first().asJsonObject.addProperty("name", snapshot.changedNode)
        val intended = YamlConfigCodec.toYaml(root.toString())
        snapshot = snapshot.copy(intendedSha = providerSha(intended.toByteArray())); persist(snapshot)
        val window = if (snapshot.case == "before") EditorCommitWindow.PROVIDER_INTENT else EditorCommitWindow.PROVIDER_COMMITTED
        val gate = EditorCommitProbe.arm(window)
        withContext(Dispatchers.Main) { model.changeProviderContent(intended); model.saveProvider() }
        waitStep("prepare-commit-window", 30_000) { awaitProviderCommitOrError({ gate.awaitReached() }, model.document) }
        val committed = snapshot.case == "after"
        check(EditorCommitProbe.visits(window) == 1)
        check(providerSha(cache(snapshot).readBytes()) == if (committed) snapshot.intendedSha else snapshot.originalSha)
        check((snapshot.changedNode in coreMembers(snapshot)) == committed)
        check(runtime.runningSinceElapsedMillis.value == since)
        snapshot = snapshot.copy(readyAt = System.currentTimeMillis()); persist(snapshot)
        providerReport(token, "ready", JSONObject().put("case", snapshot.case).put("readyAt", snapshot.readyAt)
            .put("cacheAndCoreMatched", true).put("sessionPreserved", true).put("intent", EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT))
            .put("committed", EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED)))
    }

    private suspend fun coreMembers(snapshot: ProviderRuntimeSnapshot) = waitStep("$acceptanceStep-core-members", 10_000) {
        entry.coreBridge().getProxyGroups().groups.single { it.name == snapshot.group }.all
    }

    private suspend fun verify(token: String, expectedPid: Int) {
        val snapshot = saved(token)
        check(snapshot.readyAt > 0 && snapshot.preparedPid != expectedPid && Process.myPid() == expectedPid)
        // 作者 long：外部必须先观察系统新PID与同PID前台服务；此处不调用启动/应用，也不通过管理器读组补加载。
        acceptanceStep = "verify-restored-network"
        val network = proxyEvidence(snapshot)
        val restarted = checkNotNull(entry.runtimeManager().diagnosticEvents.value.lastOrNull {
            it.kind == RuntimeDiagnosticKind.CORE_RESTARTED && it.timestampMillis > snapshot.readyAt
        })
        val committed = snapshot.case == "after"
        check(providerSha(cache(snapshot).readBytes()) == if (committed) snapshot.intendedSha else snapshot.originalSha)
        check((snapshot.changedNode in coreMembers(snapshot)) == committed)
        val since = entry.runtimeManager().runningSinceElapsedMillis.value
        withContext(Dispatchers.Main) { model.setForeground(true) }
        acceptanceStep = "verify-encrypted-route"
        waitStep("verify-encrypted-route", 20_000) { model.restoredProviderProfileId.first { it == snapshot.customId } }
        acceptanceStep = "verify-document-bind"
        withContext(Dispatchers.Main) { model.resumeProviderDocument(snapshot.customId) }
        val restored = waitStep("verify-document-bind", 20_000) { model.document.first { it?.document != null && !it.loading } }!!
        check(!restored.recoveryBlocked && restored.committed == committed && restored.dirty != committed)
        check(providerSha(restored.content.toByteArray()) == snapshot.intendedSha)
        check(EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT) == 0 && EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED) == 0)
        acceptanceStep = "verify-explicit-save"
        withContext(Dispatchers.Main) { model.saveProvider() }
        if (committed) {
            check(EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT) == 0)
            withContext(Dispatchers.Main) { model.closeProvider() }
        }
        waitStep("verify-save-close", 20_000) { model.document.first { it == null } }
        check(EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_INTENT) == if (committed) 0 else 1)
        check(EditorCommitProbe.visits(EditorCommitWindow.PROVIDER_COMMITTED) == if (committed) 0 else 1)
        check(providerSha(cache(snapshot).readBytes()) == snapshot.intendedSha && snapshot.changedNode in coreMembers(snapshot))
        check(signature(entry.profileRepository().profileFile(snapshot.sourceId)) == snapshot.sourceSignature)
        check(entry.runtimeManager().runningSinceElapsedMillis.value == since)
        acceptanceStep = "verify-post-save-network"; proxyEvidence(snapshot)
        providerReport(token, "verified", network.put("case", snapshot.case).put("preparedPid", snapshot.preparedPid)
            .put("restartAt", restarted.timestampMillis).put("readyAt", snapshot.readyAt).put("noAutomaticReplay", true)
            .put("explicitRetryCommits", if (committed) 0 else 1).put("cacheAndCoreMatched", true).put("sourcePreserved", true))
        restore(token)
    }

    private suspend fun brokenSwitch(token: String) {
        val snapshot = initialize(token)
        acceptanceStep = "broken-switch"
        val runtime = entry.runtimeManager(); val repo = entry.profileRepository(); val bridge = entry.coreBridge()
        val running = snapshot.case.startsWith("running-")
        check(snapshot.case.endsWith("-file") || snapshot.case.endsWith("-row"))
        if (running) { runtime.startProxy().getOrThrow(); proxyEvidence(snapshot) }
        val since = runtime.runningSinceElapsedMillis.value
        val applied = entry.paths().configFile.readBytes()
        val selectedMap = entry.database().profiles().getById(snapshot.customId)!!.selectedMapJson
        val members = coreMembers(snapshot)
        val source = repo.profileFile(snapshot.sourceId)
        check(signature(source) == snapshot.sourceSignature)
        if (snapshot.case.endsWith("-row")) repo.deleteProfile(snapshot.sourceId) else check(source.delete())
        check(!source.exists())
        val target = repo.profileFile(snapshot.targetId)
        val valid = target.readBytes()
        check(providerSha(valid) == snapshot.originalSha)
        val broken = JsonParser.parseString(YamlConfigCodec.toJson(valid.decodeToString())).asJsonObject
        broken.getAsJsonArray("proxy-groups").first().asJsonObject.addProperty("type", "kclash-invalid-group")
        target.writeText(YamlConfigCodec.toYaml(broken.toString()))
        // 作者 long：上游validateConfig只解码RawConfig，组类型到setup才解析；必须走真实setup拒绝及默认回退，再验证Kotlin补偿恢复。
        bridge.validateConfig(target.path)
        val epoch = bridge.ipRouteStamp().epoch
        val failure = runCatching { runtime.selectProfile(snapshot.targetId, repo::selectProfile) }.exceptionOrNull()
        check(failure?.message == "配置切换失败，已恢复原配置")
        // 作者 long：旧引用已经失效，管理器的读组会重解析它；直接Bridge回读才能证明补偿加载了旧已应用字节。
        check(repo.currentProfileId.value == snapshot.customId && entry.paths().configFile.readBytes().contentEquals(applied))
        check(entry.database().profiles().getById(snapshot.customId)!!.selectedMapJson == selectedMap)
        val restoredEpoch = bridge.ipRouteStamp().epoch
        check(coreMembers(snapshot) == members && restoredEpoch >= epoch + 2)
        check(bridge.getProxyGroups().groups.single { it.name == snapshot.group }.now == "DIRECT")
        check(runtime.state.value == if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED)
        check(runtime.runningSinceElapsedMillis.value == since)
        if (running) proxyEvidence(snapshot)
        target.writeBytes(valid)
        runtime.selectProfile(snapshot.targetId, repo::selectProfile)
        check(repo.currentProfileId.value == snapshot.targetId)
        check(bridge.getProxyGroups().groups.none { it.name == snapshot.group })
        check(!source.exists() && runtime.runningSinceElapsedMillis.value == since)
        check(runtime.state.value == if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED)
        if (running) proxyEvidence(snapshot, snapshot.targetId)
        providerReport(token, "verified", JSONObject().put("case", snapshot.case).put("rawValidationAccepted", true)
            .put("realSetupRejectedAndRollbackApplied", true).put("rollbackEpochDelta", restoredEpoch - epoch)
            .put("oldAppliedInputRestored", true).put("coreMembersAndSelectionRestored", true).put("validTargetSwitchPassed", true)
            .put("sessionPreserved", true).put("httpVerified", running))
        restore(token)
    }

    private suspend fun proxyEvidence(snapshot: ProviderRuntimeSnapshot, selected: Long = snapshot.customId): JSONObject {
        val runtime = entry.runtimeManager()
        val ownerStep = acceptanceStep
        waitStep("$ownerStep-runtime-ready", 40_000) { runtime.state.first { it == CoreRuntimeStatus.RUNNING || it == CoreRuntimeStatus.FAILED } }
        // 作者 long：核心RUNNING与服务/已应用VPN设置是分别发布的Flow，单次即时断言会把发布间隙误判为恢复失败。
        // 核心首请求测试仍只等RUNNING；本用例验证完整Provider恢复，须明确等待实际服务状态而非添加固定延迟。
        waitStep("$ownerStep-service-ready", 40_000) {
            combine(runtime.state, ServiceConfig.proxyState, ServiceConfig.proxyAlive, ServiceConfig.vpnAlive, runtime.vpnSettings) {
                    state, proxy, alive, vpn, settings ->
                state == CoreRuntimeStatus.FAILED || state == CoreRuntimeStatus.RUNNING &&
                    proxy == VpnRuntimeState.CONNECTED && alive && !vpn && settings.appliedEnabled == false
            }.first { it }
        }
        providerReport(snapshot.token, "service-ready", JSONObject().put("step", ownerStep)
            .put("runtime", runtime.state.value.name).put("proxyState", ServiceConfig.proxyState.value.name)
            .put("appliedVpn", runtime.vpnSettings.value.appliedEnabled))
        check(runtime.state.value == CoreRuntimeStatus.RUNNING && ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
        check(ServiceConfig.proxyState.value == VpnRuntimeState.CONNECTED && runtime.vpnSettings.value.appliedEnabled == false)
        check(entry.profileRepository().currentProfileId.value == selected && entry.database().appPreferences().get("runtime_should_resume") == "true")
        val target = URI(snapshot.profiles.single { it.type == ProfileType.URL && providerSha(it.url.toByteArray()) == snapshot.urlSha }.url)
        check(target.scheme == "https" && target.host != null && target.port in listOf(-1, 443))
        val evidence = acceptedProxyRequest(entry, target) { phase, detail ->
            providerReport(snapshot.token, "request-$phase", JSONObject(detail.toString()).put("ownerStep", ownerStep))
        }
        check(evidence.getBoolean("passed")) { "首笔网络请求失败，完整分段证据已保留" }
        check(evidence.getBoolean("hasNonDirectChain")) { "未观察到非DIRECT代理链" }
        providerReport(snapshot.token, "network-verified", JSONObject(evidence.toString()).put("step", ownerStep))
        return evidence
    }

    private suspend fun diagnosticFailure(token: String) {
        check(packageName == "com.lonnnnnng.flclash.motion")
        val snapshot = initialize(token)
        val fault = when (snapshot.case) {
            "diagnostic-close" -> AcceptanceRequestFault.CLOSE_AFTER_SOCKS
            "diagnostic-timeout" -> AcceptanceRequestFault.TIMEOUT_AFTER_SOCKS
            else -> error("未授权的诊断故障")
        }
        waitStep("diagnostic-core-start", 60_000) { entry.runtimeManager().startProxy().getOrThrow() }
        val target = URI(snapshot.profiles.single { it.type == ProfileType.URL && providerSha(it.url.toByteArray()) == snapshot.urlSha }.url)
        var result: JSONObject? = null
        var cancelled = false
        try {
            acceptedProxyRequest(entry, target, fault) { phase, detail ->
                providerReport(token, "request-$phase", detail)
                if (phase == "request-result") result = JSONObject(detail.toString())
            }
        } catch (failure: TimeoutCancellationException) {
            check(fault == AcceptanceRequestFault.TIMEOUT_AFTER_SOCKS)
            cancelled = true
        }
        val report = checkNotNull(result)
        check(!report.getBoolean("passed") && report.getBoolean("diagnosticLogRestored"))
        check(report.getInt("sourcePort") > 0 && report.has("dialDiagnostic") && report.has("networkAfter"))
        check(report.getString("injectedFault") == fault.name && report.getBoolean("faultApplied"))
        // 作者 long：TLS实现可能延迟到握手才访问底层socket，两种失败点都必须已完成明确的关闭注入。
        check(report.getString("failedStep") in if (cancelled) setOf("injected-timeout") else setOf("tls-create", "tls-handshake"))
        check(cancelled == (fault == AcceptanceRequestFault.TIMEOUT_AFTER_SOCKS))
        // 作者 long：后续正常请求只验证清理没有污染同一核心会话，不覆盖前一条受控失败，也不充当首请求成功样本。
        acceptanceStep = "diagnostic-follow-up"; proxyEvidence(snapshot)
        providerReport(token, "verified", JSONObject().put("case", snapshot.case).put("injected", true)
            .put("failurePhase", report.getString("failedStep")).put("failureType", report.getString("failureType"))
            .put("cancellationPropagated", cancelled).put("resultPreserved", true).put("diagnosticLogRestored", true))
        restore(token)
    }

    private suspend fun restore(token: String) {
        if (backup.read() == null) { providerReport(token, "already-restored"); return }
        val snapshot = saved(token); val repo = entry.profileRepository(); val runtime = entry.runtimeManager()
        // 作者 long：仍在保存门闩的原进程只能先按轮次杀死，不能用取消协程把“已受理中断”变成继续提交。
        check(snapshot.readyAt == 0L || Process.myPid() != snapshot.preparedPid || snapshot.case !in setOf("before", "after"))
        runtime.stop().getOrThrow()
        val persisted = entry.providerDocumentStore().read()?.let { gson.fromJson(it.decodeToString(), ProviderDocumentDraft::class.java) }
        if (persisted != null) {
            check(persisted.profileId == snapshot.customId && persisted.name == snapshot.alias)
            withContext(Dispatchers.Main) { model.setForeground(true) }
            withTimeout(20_000) { model.document.first { it?.draft != null && !it.loading && !it.saving } }
            withContext(Dispatchers.Main) { model.closeProvider() }
            withTimeout(20_000) { model.document.first { it == null } }
        }
        repo.withAutomaticUpdateBarrier {
            check(snapshot.files == snapshot.profiles.associate { it.id to signature(repo.profileFile(it.id)) })
            snapshot.profiles.forEach { original ->
                check(entry.database().profiles().getById(original.id)?.copy(autoUpdate = original.autoUpdate) == original)
            }
            runtime.selectProfile(snapshot.selected, repo::selectProfile)
            for ((id, suffix) in listOf(snapshot.customId to "custom", snapshot.sourceId to "source", snapshot.targetId to "target")) {
                entry.database().profiles().getById(id)?.let { check(it.label == "${snapshot.label}-$suffix"); repo.deleteProfile(id) }
                for (directory in listOf(File(filesDir, "resolved-profiles"), File(filesDir, "mihomo/providers/profiles"))) {
                    directory.listFiles()?.filter { it.name.matches(Regex("$id-[0-9a-f]{64}\\.yaml")) }?.forEach { check(it.delete()) }
                }
            }
            snapshot.profiles.filter { it.autoUpdate }.forEach { entry.database().profiles().update(it) }
        }
        runtime.setVpnEnabled(snapshot.vpn).getOrThrow(); runtime.setOutboundMode(snapshot.mode).getOrThrow()
        check(entry.vpnSettingsStore().read() == snapshot.vpn && runtime.outboundMode.value == snapshot.mode)
        check(snapshot.profiles == entry.database().profiles().getAll().sortedBy { it.id })
        check(snapshot.files == snapshot.profiles.associate { it.id to signature(repo.profileFile(it.id)) })
        check(snapshot.preferences == preferences() && snapshot.scripts == scripts())
        check(entry.providerDocumentStore().read() == null && !ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
        backup.write(null)
        providerReport(token, "restored", JSONObject().put("formalPreserved", true).put("backupCleared", backup.read() == null))
    }
}

/** 作者 long：保存异常会由VM转成终态而不再抛出，探针必须同时等错误，不能把已失败的保存伪装成30秒未响应。 */
internal suspend fun awaitProviderCommitOrError(reached: suspend () -> Unit, document: kotlinx.coroutines.flow.StateFlow<ProviderDocumentUiState?>) = coroutineScope {
    val committed = async { reached(); true }
    val rejected = async { document.first { it?.error != null && !it.saving }; false }
    try {
        check(kotlinx.coroutines.selects.select<Boolean> { committed.onAwait { it }; rejected.onAwait { it } }) { "Provider save rejected" }
    } finally { committed.cancelAndJoin(); rejected.cancelAndJoin() }
}

/** 作者 long：只有持有DUMP权限的shell可杀死本轮已就绪PID，Release完全不包含这个接收器。 */
class ProviderRuntimeKillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val token = intent.getStringExtra("token").orEmpty()
            try {
                require(token.matches(Regex("[0-9a-f]{32}")))
                val saved = Gson().fromJson(checkNotNull(providerBackup(context).read()).decodeToString(), ProviderRuntimeSnapshot::class.java)
                check(saved.token == token && saved.readyAt > 0 && saved.preparedPid == Process.myPid())
                check(intent.getIntExtra("expectedPid", -1) == Process.myPid())
                providerReport(token, "kill-accepted"); Process.killProcess(Process.myPid())
            } catch (failure: Throwable) {
                providerReport(token, "kill-rejected", JSONObject().put("type", failure.javaClass.simpleName))
            } finally { pending.finish() }
        }
    }
}
