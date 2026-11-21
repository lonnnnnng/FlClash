package com.lonnnnnng.flclash

import android.net.VpnService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.Core
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.TunStartBoundary
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest

class VpnProtectStartupWindowInstrumentedTest {
    @Test fun androidProtectAcceptsPrecreatedSocketDuringTunStartup() = runBlocking(Dispatchers.IO) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashPreVpnProtectAcceptance"))
        val context = instrumentation.targetContext
        assertEquals("com.lonnnnnng.flclash.motion", context.packageName)
        val inputPath = args.getString("kclashStabilityInput").orEmpty()
        require(inputPath.matches(Regex("/data/local/tmp/kclash-stability-[a-f0-9]{32}\\.json")))
        val input = JSONObject(instrumentation.uiAutomation.executeShellCommand("cat $inputPath").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }.decodeToString())
        val source = android.util.Base64.decode(input.getString("configBase64"), android.util.Base64.DEFAULT)
        val sha = MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) }
        assertEquals(input.getString("localSha256"), sha)

        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val repo = entry.profileRepository()
        repo.profilesLoaded.first { it }
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        val runtime = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val rowsBefore = entry.database().profiles().getAll().sortedBy { it.id }
        val local = rowsBefore.single { row ->
            row.type == ProfileType.FILE && MessageDigest.getInstance("SHA-256").digest(repo.profileFile(row.id).readBytes())
                .joinToString("") { value -> "%02x".format(value) } == sha
        }
        val filesBefore = rowsBefore.associate { row -> row.id to repo.profileFile(row.id).let {
            Triple(it.length(), it.lastModified(), MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { value -> "%02x".format(value) })
        } }
        val vpnBefore = entry.vpnSettingsStore().read()
        val selectedBefore = repo.currentProfileId.value
        assertEquals("the authorized local fixture must already be selected", local.id, selectedBefore)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            VpnService.prepare(context)?.let { permission ->
                scenario.onActivity { activity -> activity.startActivityForResult(permission, 8174) }
                withTimeout(20_000) {
                    while (VpnService.prepare(context) != null) {
                        val root = instrumentation.uiAutomation.rootInActiveWindow
                        if (root?.packageName?.toString() == "com.android.vpndialogs") {
                            root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()
                                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        delay(100)
                    }
                }
            }
            assertNull(VpnService.prepare(context))
        }

        var socket: DatagramSocket? = null
        var primaryFailure: Throwable? = null
        var runtimeTouched = false
        var socketCreatedElapsedMs = 0L
        try {
            socket = DatagramSocket(null).apply { bind(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0)) }
            socketCreatedElapsedMs = SystemClock.elapsedRealtime()
            TunStartBoundary.armProtectProbe(context.packageName, true, socket)
            runtime.setVpnEnabled(true).getOrThrow()
            runtimeTouched = true
            val startElapsed = SystemClock.elapsedRealtime()
            runtime.startProxy().getOrThrow()

            val probe = TunStartBoundary.protectProbeSnapshot().optJSONObject("result")
                ?: error("VpnService Protect callback was not observed during Core.startTun")
            val finalCore = JSONObject(Core.networkBootstrapSnapshot()).getJSONObject("protection")
            assertTrue("Android VpnService.protect(DatagramSocket) rejected the pre-created socket", probe.getBoolean("platformProtectAccepted"))
            assertTrue("the socket must remain open after platform protection", probe.getBoolean("socketOpenAfterCall"))
            assertEquals("STARTING", probe.getString("vpnServiceState"))
            assertTrue("the call must happen inside Core.startTun", probe.getBoolean("coreStartCallActive"))
            assertTrue("Go must independently observe startup protection calls", finalCore.getLong("callsDuringStart") > 0)
            val report = JSONObject()
                .put("passed", true)
                .put("model", android.os.Build.MODEL)
                .put("api", android.os.Build.VERSION.SDK_INT)
                .put("sourceSha256", sha)
                .put("socketCreatedElapsedMs", socketCreatedElapsedMs)
                .put("vpnStartElapsedMs", startElapsed)
                .put("platformProtect", probe)
                .put("coreProtectionAfterTunStartup", finalCore)
                .put("directSocketAndCorePendingQueueAreSameFd", false)
            instrumentation.sendStatus(2, android.os.Bundle().apply {
                putString("kclash_vpn_protect_startup", report.toString())
            })
        } catch (failure: Throwable) {
            primaryFailure = failure
            instrumentation.sendStatus(2, android.os.Bundle().apply {
                putString("kclash_vpn_protect_startup_failure", JSONObject()
                    .put("failureType", failure.javaClass.simpleName)
                    .put("runtime", runtime.state.value.name)
                    .put("coreProtection", JSONObject(Core.networkBootstrapSnapshot()).optJSONObject("protection") ?: JSONObject())
                    .put("probe", TunStartBoundary.protectProbeSnapshot()).toString())
            })
            throw failure
        } finally {
            var stopFailure: Throwable? = null
            withContext(NonCancellable + Dispatchers.IO) {
                TunStartBoundary.clearProtectProbe()
                runCatching { socket?.close() }
                if (runtimeTouched) runCatching { runtime.stop().getOrThrow() }.onFailure { stopFailure = it; primaryFailure?.addSuppressed(it) }
                runCatching { runtime.setVpnEnabled(vpnBefore).getOrThrow() }.onFailure { primaryFailure?.addSuppressed(it) }
            }
            if (primaryFailure == null) {
                stopFailure?.let { throw it }
                assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
            }
            assertEquals(rowsBefore, entry.database().profiles().getAll().sortedBy { it.id })
            assertEquals(selectedBefore, repo.currentProfileId.value)
            assertEquals(filesBefore, rowsBefore.associate { row -> row.id to repo.profileFile(row.id).let {
                Triple(it.length(), it.lastModified(), MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { value -> "%02x".format(value) })
            } })
            assertEquals(vpnBefore, entry.vpnSettingsStore().read())
        }
    }
}
