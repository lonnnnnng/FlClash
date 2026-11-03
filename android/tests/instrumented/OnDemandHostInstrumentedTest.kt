package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：只暂存自己的SSID规则，真实配置及选择保持，结束无论成功失败都恢复原列表。 */
class OnDemandHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun runningOnDemandPauseReleasesVpnAndListenerThenResumesSameProfileAndUptime() = runtimeAcceptance(false)

    @Test fun currentWifiRuleAutomaticallyPausesAndRemovalResumesActualVpnAndListener() = runtimeAcceptance(true)

    private fun runtimeAcceptance(useSystemSsid: Boolean) = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("kclashOnDemandAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        assertNull(android.net.VpnService.prepare(context))
        val before = entry.database().profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        val profile = before.single { it.id == selected }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(profile.url.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("kclashAuthorizedSourceSha256"), hash)
        val files = before.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        val originalVpn = entry.vpnSettingsStore().read()
        val ssidStore = entry.onDemandSettingsStore()
        val originalSsids = ssidStore.read()
        val coordinator = entry.onDemandCoordinator()
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        suspend fun awaitVpn(present: Boolean) {
            repeat(100) {
                if (connectivity.allNetworks.any { connectivity.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true } == present) return
                delay(100)
            }
            error("系统VPN未达到预期状态")
        }
        suspend fun awaitListener(port: Int, listening: Boolean) {
            repeat(50) {
                val connected = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 300) } }.isSuccess
                }
                if (connected == listening) return
                delay(100)
            }
            error("本地监听未达到预期状态")
        }
        try {
            var systemSsid: String? = null
            if (useSystemSsid) {
                // 作者 long：只读取当前Wi-Fi，不切换网络；真实名称只在内存中用于匹配，不写日志或截图。
                ssidStore.write(listOf("nonmatching-${System.nanoTime()}"))
                coordinator.refresh()
                repeat(100) {
                    systemSsid = coordinator.network.value.ssid
                    if (systemSsid == null) delay(100)
                }
                check(systemSsid != null && coordinator.network.value.authorized) { "当前SSID不可读，请核实定位总开关和前后台定位授权" }
            }
            manager.setOnDemandExcluded(false).getOrThrow()
            manager.setVpnEnabled(true).getOrThrow(); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            val port = requireNotNull(ServiceConfig.vpnOptions).port
            assertTrue(port > 0)
            awaitVpn(true); awaitListener(port, true)
            val revision = manager.ipRouteRevision.value
            if (useSystemSsid) {
                ssidStore.write(listOf(requireNotNull(systemSsid)))
                repeat(100) { if (!manager.onDemandSuspended.value) delay(100) }
            } else manager.setOnDemandExcluded(true).getOrThrow()
            awaitVpn(false); awaitListener(port, false)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertTrue(manager.onDemandSuspended.value)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertTrue(manager.ipRouteRevision.value > revision)
            if (useSystemSsid) {
                ssidStore.write(emptyList())
                repeat(100) { if (manager.onDemandSuspended.value) delay(100) }
            } else manager.setOnDemandExcluded(false).getOrThrow()
            awaitVpn(true); awaitListener(port, true)
            assertFalse(manager.onDemandSuspended.value)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            manager.setOnDemandExcluded(true).getOrThrow()
            manager.stop().getOrThrow()
            manager.setOnDemandExcluded(false).getOrThrow()
            awaitVpn(false); awaitListener(port, false)
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            assertNull(manager.runningSinceElapsedMillis.value)
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, before.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
        } finally {
            manager.stop().getOrThrow(); manager.setOnDemandExcluded(false).getOrThrow()
            manager.setVpnEnabled(originalVpn).getOrThrow()
            ssidStore.write(originalSsids)
            coordinator.refresh()
        }
    }

    @Test fun actualOnDemandEditorRetainsSnapshotAcrossRecreationAndRejectsExternalRestore() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val store = entry.onDemandSettingsStore()
        val original = store.read()
        val profiles = entry.database().profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        val files = profiles.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        try {
            store.write(listOf("fixture-original"))
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_general"))
            compose.onNodeWithTag("tools_item_general").performClick()
            compose.onNodeWithTag("general_item_on_demand").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("on_demand_add"))
            compose.onNodeWithTag("on_demand_add").performClick()
            compose.onNodeWithTag("on_demand_input").performTextReplacement(" fixture-added ")
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("on_demand_input").assertTextContains(" fixture-added ")
            compose.onNodeWithTag("on_demand_save").performClick()
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("on_demand_input").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf("fixture-original", " fixture-added "), store.read())
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("on_demand_ssid_0"))
            compose.onNodeWithTag("on_demand_ssid_0").performClick()
            compose.onNodeWithTag("on_demand_input").performTextReplacement("should-not-win")
            store.write(listOf("external-restore"))
            compose.onNodeWithTag("on_demand_save").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("on_demand_editor_error").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("on_demand_input").assertTextContains("should-not-win")
            assertEquals(listOf("external-restore"), store.read())
            assertEquals(profiles, entry.database().profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, profiles.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally { store.write(original) }
    }
}
