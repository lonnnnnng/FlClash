package com.lonnnnnng.flclash

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ServiceConfig
import com.lonnnnnng.flclash.service.VpnRuntimeState
import com.lonnnnnng.flclash.ui.MainViewModel
import com.lonnnnnng.flclash.ui.ProfileEditorDraftSnapshot
import com.lonnnnnng.flclash.ui.ProfileDocumentEditorState
import com.lonnnnnng.flclash.ui.VisualMotionTestActivity
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：未授权时调用真实运行管理器，让系统establish拒绝建TUN；不以假服务或坏配置替代原生失败。 */
class NativeTunFailureInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun shell(command: String) = ins.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun files(root: File) = root.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(root).path to "${it.length()}:${it.lastModified()}:${hash(it.readBytes())}"
    }
    private fun emit(phase: String, report: JSONObject) = ins.sendStatus(2, Bundle().apply {
        putString("kclash_native_tun_failure", report.put("phase", phase).toString())
    })
    private fun vpnNetworks() = context.getSystemService(ConnectivityManager::class.java).let { connectivity ->
        connectivity.allNetworks.count { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    @Test fun unpreparedNativeEstablishFailureCleansUpAndExplicitGrantRestoresRealTraffic(): Unit = runBlocking(Dispatchers.IO) {
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashNativeTunFailure"))
        assertNotNull("仅在本验收包尚未获VPN授权时执行，不撤销其他VPN应用权限", VpnService.prepare(context))
        val runtime = entry.runtimeManager(); val repo = entry.profileRepository(); val db = entry.database()
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        assertEquals(0, vpnNetworks())
        val rows = db.profiles().getAll(); val selected = repo.currentProfileId.value
        val row = rows.single(); assertEquals(ProfileType.FILE, row.type)
        val source = repo.profileFile(row.id).readBytes()
        assertEquals(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"), hash(source))
        val sourceFiles = files(File(context.filesDir, "profiles"))
        val oldVpn = runtime.vpnSettings.value.enabled
        assertNull(entry.profileDraftStore().read())
        val draft = com.google.gson.Gson().toJson(ProfileEditorDraftSnapshot(document = ProfileDocumentEditorState(
            ownerId = row.id, original = source.decodeToString(), content = source.decodeToString() + "\n# native tun pending draft\n"),
            fileBytes = source)).toByteArray()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val timeline = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        fun trace(kind: String, value: String) {
            timeline.add(JSONObject().put("at", SystemClock.elapsedRealtime()).put("kind", kind).put("value", value))
        }
        fun timelineSnapshot() = synchronized(timeline) { timeline.toList() }
        listOf(
            scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.state.collect { trace("runtime", it.name) } },
            scope.launch(start = CoroutineStart.UNDISPATCHED) { ServiceConfig.vpnState.collect { trace("vpn", it.name) } },
            scope.launch(start = CoroutineStart.UNDISPATCHED) { ServiceConfig.proxyAlive.collect { trace("proxyAlive", it.toString()) } },
            scope.launch(start = CoroutineStart.UNDISPATCHED) { ServiceConfig.vpnAlive.collect { trace("vpnAlive", it.toString()) } },
        )
        val viewModels = ViewModelStore()
        val vm = MainViewModel(runtime).also { viewModels.put("native-tun", it) }
        var primaryFailure: Throwable? = null
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use { scenario ->
            try {
                entry.profileDraftStore().write(draft)
                val encrypted = files(context.noBackupFilesDir)
                runtime.setVpnEnabled(true).getOrThrow()
                val failures = JSONArray()
                // 作者 long：连续失败两次不提前stop/等销毁，覆盖用户收到失败后立即重试的真实清理交错。
                repeat(2) { index ->
                    val start = SystemClock.elapsedRealtime()
                    val failure = runtime.startProxy().exceptionOrNull()
                    failures.put(JSONObject().put("attempt", index + 1).put("elapsedMillis", SystemClock.elapsedRealtime() - start)
                        .put("failureType", failure?.javaClass?.simpleName ?: "none")
                        .put("nativeRejectionForwarded", failure?.message == NATIVE_REJECTION)
                        .put("runtime", runtime.state.value.name).put("hasRunningSince", runtime.runningSinceElapsedMillis.value != null))
                }
                // 作者 long：用户反馈从生产MainViewModel.errors读取，不能把测试自行生成的提示记成页面已收到错误。
                val uiError = scope.async(start = CoroutineStart.UNDISPATCHED) { withTimeout(35_000) { vm.errors.first() } }
                val uiStart = SystemClock.elapsedRealtime()
                ins.runOnMainSync { vm.startRuntime() }
                val message = uiError.await()
                withTimeout(10_000) { ServiceConfig.vpnAlive.first { !it }; ServiceConfig.proxyAlive.first { !it } }
                val nativeLines = shell("logcat -d --pid=${Process.myPid()} -v raw -s KClash:D '*:S'").lineSequence()
                    .count { it.contains("VPN service start failed: java.lang.IllegalStateException: $NATIVE_REJECTION") }
                val report = JSONObject().put("attempts", failures).put("uiErrorElapsedMillis", SystemClock.elapsedRealtime() - uiStart)
                    .put("uiReceivedNativeRejection", message == NATIVE_REJECTION).put("nativeServiceRejectionLogCount", nativeLines)
                    .put("runtime", runtime.state.value.name).put("vpnNetworkCount", vpnNetworks())
                    .put("vpnAlive", ServiceConfig.vpnAlive.value).put("proxyAlive", ServiceConfig.proxyAlive.value)
                    .put("resumePresent", db.appPreferences().get("runtime_should_resume") != null)
                    .put("timeline", JSONArray(timelineSnapshot()))
                emit("rejected", report)
                assertEquals(CoreRuntimeStatus.FAILED, runtime.state.value)
                assertNull(runtime.runningSinceElapsedMillis.value); assertNull(runtime.vpnSettings.value.appliedEnabled)
                assertNull(db.appPreferences().get("runtime_should_resume")); assertEquals(0, vpnNetworks())
                assertEquals(rows, db.profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
                assertEquals(sourceFiles, files(File(context.filesDir, "profiles")))
                assertEquals(encrypted, files(context.noBackupFilesDir)); assertArrayEquals(draft, entry.profileDraftStore().read())
                assertTrue("真实服务日志未记录establish拒绝", nativeLines >= 3)
                assertEquals(NATIVE_REJECTION, message)
                repeat(failures.length()) { index ->
                    assertTrue(failures.getJSONObject(index).getBoolean("nativeRejectionForwarded"))
                    assertTrue("建TUN拒绝不应等满服务启动超时", failures.getJSONObject(index).getLong("elapsedMillis") < 12_000)
                }
                assertFalse(timelineSnapshot().any { it.optString("kind") == "runtime" && it.optString("value") == "RUNNING" })
                val permission = checkNotNull(VpnService.prepare(context))
                scenario.onActivity { it.startActivityForResult(permission, 8136) }
                withTimeout(15_000) {
                    while (VpnService.prepare(context) != null) {
                        val root = ins.uiAutomation.rootInActiveWindow
                        if (root?.packageName?.toString() == "com.android.vpndialogs") {
                            root.findAccessibilityNodeInfosByViewId("android:id/button1").singleOrNull()
                                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        delay(100)
                    }
                }
                runtime.startProxy().getOrThrow()
                assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value)
                assertEquals(VpnRuntimeState.CONNECTED, ServiceConfig.vpnState.value)
                assertNotNull(runtime.runningSinceElapsedMillis.value)
                // 作者 long：CONNECTED说明服务已接管FD，系统Network发布仍异步；按条件等待可见，不用固定睡眠假定路由就绪。
                withTimeout(10_000) { while (vpnNetworks() != 1) delay(50) }
                val traffic = semanticVpnTrafficAcceptance(entry, "before")
                assertEquals(rows, db.profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
                assertEquals(sourceFiles, files(File(context.filesDir, "profiles")))
                assertEquals(encrypted, files(context.noBackupFilesDir)); assertArrayEquals(draft, entry.profileDraftStore().read())
                emit("recovered", JSONObject().put("realDefaultVpnTraffic", traffic).put("prepared", VpnService.prepare(context) == null)
                    .put("sourceAndDraftHeld", true).put("running", runtime.state.value.name).put("resume", db.appPreferences().get("runtime_should_resume") == "true"))
            } catch (failure: Throwable) {
                primaryFailure = failure
                throw failure
            } finally {
                withContext(NonCancellable) {
                    // 作者 long：服务或偏好恢复失败不能跳过自有草稿/协程清理；收尾错误附到原失败，不覆盖首个现场。
                    val cleanupFailures = mutableListOf<Throwable>()
                    suspend fun cleanup(block: suspend () -> Unit) {
                        try { block() } catch (failure: Throwable) { cleanupFailures += failure }
                    }
                    cleanup { runtime.stop().getOrThrow() }
                    cleanup { runtime.setVpnEnabled(oldVpn).getOrThrow() }
                    cleanup { if (entry.profileDraftStore().read()?.contentEquals(draft) == true) entry.profileDraftStore().write(null) }
                    cleanup { scope.coroutineContext[Job]?.cancelAndJoin() }
                    cleanup { ins.runOnMainSync { viewModels.clear() } }
                    if (cleanupFailures.isNotEmpty()) {
                        emit("cleanup-failed", JSONObject().put("failureTypes", JSONArray(cleanupFailures.map { it.javaClass.simpleName })))
                        val failure = primaryFailure ?: cleanupFailures.removeAt(0)
                        cleanupFailures.forEach(failure::addSuppressed)
                        if (primaryFailure == null) throw failure
                    }
                }
            }
        }
        assertEquals(rows, db.profiles().getAll()); assertEquals(sourceFiles, files(File(context.filesDir, "profiles")))
        assertEquals(0, vpnNetworks()); assertFalse(ServiceConfig.vpnAlive.value); assertFalse(ServiceConfig.proxyAlive.value)
        emit("cleaned", JSONObject().put("stopped", true).put("sourceHeld", true).put("draftRemoved", entry.profileDraftStore().read() == null))
    }

    private companion object { const val NATIVE_REJECTION = "VPN establishment was rejected by the system" }
}
