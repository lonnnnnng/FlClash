package com.lonnnnnng.flclash

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.*
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：同一生产管理器重建候选网络参数，真实Android/Go拒绝后验证补偿，不更改授权原配置或使用假服务。 */
class TunRebuildCompensationInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun files(root: File) = root.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(root).path to "${it.length()}:${it.lastModified()}:${hash(it.readBytes())}"
    }
    private fun emit(phase: String, report: JSONObject) = ins.sendStatus(2, Bundle().apply {
        putString("kclash_tun_rebuild", report.put("phase", phase).toString())
    })
    private fun networks(): List<Long> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.filter { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            .map { it.networkHandle }
    }
    private suspend fun readyNetwork(): Long = withTimeout(10_000) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        while (true) {
            val handles = networks()
            if (handles.size == 1 && cm.activeNetwork?.networkHandle == handles.single()) return@withTimeout handles.single()
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE") error("VPN网络未就绪")
    }
    private suspend fun stoppedNetwork() = withTimeout(10_000) {
        ServiceConfig.proxyAlive.first { !it }; ServiceConfig.vpnAlive.first { !it }
        while (networks().isNotEmpty()) delay(50)
        assertEquals(0, TunStartBoundary.tunFds().getInt("tunCount"))
        assertEquals(0, TunStartBoundary.tunFds().getInt("unreadableCount"))
    }

    @Test fun systemEstablishFailureRestoresPreviousNetwork() = run(TunStartBoundary.Fault.ESTABLISH_MTU, false, 48200)
    @Test fun coreFdRejectionRestoresPreviousNetwork() = run(TunStartBoundary.Fault.CORE_CONGESTION, false, 48210)
    @Test fun systemEstablishAndRollbackFailureStopsThenRetries() = run(TunStartBoundary.Fault.ESTABLISH_MTU, true, 48220)
    @Test fun coreFdAndRollbackRejectionStopsThenRetries() = run(TunStartBoundary.Fault.CORE_CONGESTION, true, 48230)
    @Test fun profileSwitchWithGlobalNetworkSettingsKeepsExistingTun() = run(null, false, 48240)

    private fun run(fault: TunStartBoundary.Fault?, doubleFailure: Boolean, port: Int): Unit = runBlocking(Dispatchers.IO) {
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashTunRebuild"))
        val verifyNetwork = InstrumentationRegistry.getArguments().getString("kclashTunNativeOnly") != "true"
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        val runtime = entry.runtimeManager(); val repo = entry.profileRepository(); val db = entry.database()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value); assertTrue(networks().isEmpty())
        assertNull(entry.profileDraftStore().read())
        val rows = db.profiles().getAll(); val row = rows.single(); assertEquals(ProfileType.FILE, row.type)
        val source = repo.profileFile(row.id).readBytes()
        assertEquals(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"), hash(source))
        val selected = repo.currentProfileId.value; val originals = files(File(context.filesDir, "profiles"))
        val oldVpn = runtime.vpnSettings.value.enabled; val oldNetwork = entry.networkSettingsStore().read()
        val endpoint = JSONObject(checkNotNull(stabilityInput(context).read()).decodeToString()).optJSONObject("endpointControl")
        val fixedNode = endpoint?.let {
            assertEquals(hash(source), it.getString("sourceSha256"))
            val node = JsonParser.parseString(YamlConfigCodec.toJson(source.decodeToString())).asJsonObject
                .getAsJsonArray("proxies")[it.getInt("nodeIndex")].asJsonObject.get("name").asString
            assertTrue("固定节点必须来自本轮授权原配置", node == it.getString("node"))
            node
        }
        suspend fun assertFixedSelection() {
            if (fixedNode != null) assertTrue("核心重建后必须自行恢复已保存节点，不能补切掩盖失败",
                entry.coreBridge().getProxyGroups().groups.single { it.name == "GLOBAL" }.now == fixedNode)
        }
        // 作者 long：候选只在内存投影不同端口/MTU；原授权正文不改，实际应用仍须服从全局设置。
        val candidateBytes = if (fault == null) JsonParser.parseString(YamlConfigCodec.toJson(source.decodeToString())).asJsonObject.let { root ->
            root.addProperty("mixed-port", 27891)
            val tun = root.getAsJsonObject("tun") ?: com.google.gson.JsonObject().also { root.add("tun", it) }
            tun.addProperty("mtu", 1450); tun.addProperty("stack", "system")
            YamlConfigCodec.toYaml(root.toString()).toByteArray()
        } else source
        val draft = Gson().toJson(ProfileEditorDraftSnapshot(document = ProfileDocumentEditorState(ownerId = row.id,
            original = source.decodeToString(), content = source.decodeToString() + "\n# TUN rebuild pending draft\n"), fileBytes = source)).toByteArray()
        var candidateId: Long? = null; var primary: Throwable? = null
        val case = fault?.name?.lowercase()?.plus(if (doubleFailure) "-double" else "-single") ?: "profile-switch"
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
            try {
                VpnService.prepare(context)?.let { permission ->
                    scenario.onActivity { it.startActivityForResult(permission, 8146) }
                    withTimeout(15_000) {
                        while (VpnService.prepare(context) != null) {
                            val root = ins.uiAutomation.rootInActiveWindow
                            if (root?.packageName?.toString() == "com.android.vpndialogs") root.findAccessibilityNodeInfosByViewId("android:id/button1")
                                .singleOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                            delay(100)
                        }
                    }
                }
                if (fault == null) candidateId = repo.addFileProfile("tun-rebuild-owned", candidateBytes).id
                if (fixedNode != null) {
                    assertEquals(com.lonnnnnng.flclash.core.CoreOutboundMode.GLOBAL, runtime.outboundMode.value)
                    // 作者 long：正式UI会同时切核心并保存Profile选择；仅切运行态会在重启时回到DIRECT，不能用于恢复验收。
                    runtime.changeProxy("GLOBAL", fixedNode).getOrThrow()
                    repo.updateProfileProxySelection(row.id, "GLOBAL", fixedNode)
                    candidateId?.let { repo.updateProfileProxySelection(it, "GLOBAL", fixedNode) }
                }
                val expectedRows = db.profiles().getAll(); val expectedFiles = files(File(context.filesDir, "profiles"))
                entry.profileDraftStore().write(draft); val encrypted = files(context.noBackupFilesDir)
                runtime.setVpnEnabled(true).getOrThrow(); runtime.startProxy().getOrThrow()
                val oldHandle = readyNetwork(); val oldSince = runtime.runningSinceElapsedMillis.value
                val applied = entry.paths().configFile.readBytes(); val epoch = entry.coreBridge().ipRouteStamp().epoch
                val oldOptions = checkNotNull(ServiceConfig.vpnOptions)
                assertFixedSelection()
                // 作者 long：外网故障不能阻挡原生补偿诊断，但跳过实流必须显式标记，不能计作网络恢复通过。
                val before = if (verifyNetwork) semanticVpnTrafficAcceptance(entry, "before", port).getJSONObject("client")
                    else JSONObject().put("skipped", true)
                TunStartBoundary.arm(context.packageName, true, List(if (doubleFailure) 2 else 1) { fault ?: TunStartBoundary.Fault.ESTABLISH_MTU })
                val started = SystemClock.elapsedRealtime()
                val failure = if (fault == null) {
                    // 作者 long：Profile中的端口/TUN字段被全局设置覆盖，真实切换应保持旧TUN；未触发重建不能假称故障补偿通过。
                    runtime.selectProfile(checkNotNull(candidateId), repo::selectProfile)
                    assertEquals(candidateId, repo.currentProfileId.value)
                    assertEquals(oldOptions, ServiceConfig.vpnOptions)
                    assertTrue(entry.coreBridge().ipRouteStamp().epoch > epoch)
                    assertEquals(0, TunStartBoundary.snapshot().getInt("visits"))
                    assertEquals(oldHandle, readyNetwork())
                    runtime.selectProfile(row.id, repo::selectProfile)
                    null
                } else {
                    val candidate = oldNetwork.copy(mtu = if (oldNetwork.mtu == 1400) 1500 else 1400)
                    runtime.updateNetworkSettings(oldNetwork, candidate).exceptionOrNull()
                }
                val observed = TunStartBoundary.snapshot()
                emit("$case-outcome", JSONObject().put("observed", observed).put("elapsedMillis", SystemClock.elapsedRealtime() - started)
                    .put("failureType", failure?.javaClass?.simpleName ?: "none")
                    .put("restoredMessage", failure?.message == "配置切换失败，已恢复原配置")
                    .put("stoppedMessage", failure?.message == "配置切换失败，原配置恢复失败，核心已停止")
                    .put("runtime", runtime.state.value.name))
                if (fault != null) {
                    assertEquals(2, observed.getInt("visits"))
                    assertEquals(if (doubleFailure) "配置切换失败，原配置恢复失败，核心已停止" else "配置切换失败，已恢复原配置", failure?.message)
                    val attempts = observed.getJSONArray("attempts")
                    repeat(if (doubleFailure) 2 else 1) { i ->
                        val a = attempts.getJSONObject(i)
                        assertEquals(fault.name, a.getString("fault"))
                        if (fault == TunStartBoundary.Fault.ESTABLISH_MTU) {
                            assertTrue(a.getBoolean("establishCalled")); assertTrue(a.has("establishException")); assertFalse(a.has("coreCalled"))
                            assertTrue(a.getJSONArray("establishExceptionFrames").toString().contains("android.net.VpnService"))
                            assertEquals(0, a.getJSONObject("afterEstablishFailureFds").getInt("tunCount"))
                        } else {
                            assertTrue(a.getBoolean("establishReturnedFd")); assertEquals("TUN", a.getString("coreFdKindBefore"))
                            assertTrue(a.getBoolean("coreCalled")); assertFalse(a.getBoolean("coreAccepted"))
                            assertEquals(0, a.getJSONObject("afterCoreFds").getInt("tunCount"))
                            assertEquals(0, a.getJSONObject("afterCoreFds").getInt("unreadableCount"))
                            assertNotEquals("TUN", a.getString("coreFdKindAfter"))
                        }
                    }
                    if (!doubleFailure) assertTrue(attempts.getJSONObject(1).getBoolean("coreAccepted"))
                }
                assertEquals(oldNetwork, entry.networkSettingsStore().read()); assertEquals(oldNetwork, runtime.networkSettings.value)
                assertEquals(expectedRows, db.profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
                assertEquals(expectedFiles, files(File(context.filesDir, "profiles")))
                assertArrayEquals(applied, entry.paths().configFile.readBytes())
                assertArrayEquals(draft, entry.profileDraftStore().read()); assertEquals(encrypted, files(context.noBackupFilesDir))
                TunStartBoundary.reset()
                if (doubleFailure) {
                    assertEquals(CoreRuntimeStatus.FAILED, runtime.state.value); assertNull(runtime.runningSinceElapsedMillis.value)
                    assertNull(runtime.vpnSettings.value.appliedEnabled); assertNull(db.appPreferences().get("runtime_should_resume"))
                    stoppedNetwork()
                    runtime.startProxy().getOrThrow()
                    assertNotEquals(oldSince, runtime.runningSinceElapsedMillis.value)
                } else {
                    assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value); assertEquals(oldSince, runtime.runningSinceElapsedMillis.value)
                }
                val recoveredHandle = readyNetwork()
                assertEquals(oldOptions, ServiceConfig.vpnOptions)
                if (fault != null) assertNotEquals(oldHandle, recoveredHandle)
                assertEquals(VpnRuntimeState.CONNECTED, ServiceConfig.vpnState.value)
                assertEquals("true", db.appPreferences().get("runtime_should_resume"))
                assertFixedSelection()
                val after = if (verifyNetwork) semanticVpnTrafficAcceptance(entry, "after", port).getJSONObject("client")
                    else JSONObject().put("skipped", true)
                emit("$case-verified", JSONObject().put("passed", true).put("fault", fault?.name ?: "NONE")
                    .put("doubleFailure", doubleFailure).put("oldHandle", oldHandle).put("newHandle", recoveredHandle)
                    .put("epochBefore", epoch).put("epochAfter", entry.coreBridge().ipRouteStamp().epoch)
                    .put("sourceDraftAndSelectionHeld", true).put("settingsAndAppliedBytesRestored", true)
                    .put("realNetworkVerified", verifyNetwork)
                    .put("fixedNodeIndex", endpoint?.getInt("nodeIndex") ?: -1)
                    .put("fixedNodeHash", fixedNode?.let { hash(it.toByteArray()) } ?: "none")
                    .put("beforeClient", before).put("afterClient", after))
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                withContext(NonCancellable) {
                    TunStartBoundary.reset()
                    val errors = mutableListOf<Throwable>()
                    suspend fun cleanup(block: suspend () -> Unit) { try { block() } catch (e: Throwable) { errors += e } }
                    cleanup { runtime.stop().getOrThrow() }
                    cleanup { runtime.updateNetworkSettings(entry.networkSettingsStore().read(), oldNetwork).getOrThrow() }
                    cleanup { if (repo.currentProfileId.value != selected) repo.selectProfile(selected) }
                    cleanup { candidateId?.let { id -> check(hash(repo.profileFile(id).readBytes()) == hash(candidateBytes)); repo.deleteProfile(id) } }
                    cleanup {
                        // 作者 long：只还原本例临时选择；其他行字段若发生漂移必须报错，不能用整行覆盖掩盖损坏。
                        val current = checkNotNull(db.profiles().getById(row.id))
                        assertEquals(row, current.copy(selectedMapJson = row.selectedMapJson))
                        if (current.selectedMapJson != row.selectedMapJson) db.profiles().update(current.copy(selectedMapJson = row.selectedMapJson))
                    }
                    cleanup { runtime.setVpnEnabled(oldVpn).getOrThrow() }
                    cleanup { if (entry.profileDraftStore().read()?.contentEquals(draft) == true) entry.profileDraftStore().write(null) }
                    if (errors.isNotEmpty()) {
                        val failure = primary ?: errors.removeAt(0); errors.forEach(failure::addSuppressed)
                        if (primary == null) throw failure
                    }
                }
            }
        }
        stoppedNetwork(); assertEquals(rows, db.profiles().getAll()); assertEquals(originals, files(File(context.filesDir, "profiles")))
        assertNull(entry.profileDraftStore().read())
        emit("$case-cleaned", JSONObject().put("passed", true).put("noTunFds", true).put("noServicesOrVpnNetwork", true))
    }
}
