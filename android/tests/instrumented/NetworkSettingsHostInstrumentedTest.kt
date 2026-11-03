package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：只改并恢复全局网络设置，正式配置正文与当前选择必须保持不变，停止态保存不能启动服务。 */
class NetworkSettingsHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun runningNetworkSettingsRebuildActualVpnRoutesMtuAndProxy() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashNetworkAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        assertNull(android.net.VpnService.prepare(context))
        val before = entry.database().profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        val profile = before.single { it.id == selected }
        val sourceHash = java.security.MessageDigest.getInstance("SHA-256").digest(profile.url.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("kclashAuthorizedSourceSha256"), sourceHash)
        val files = before.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        val original = entry.networkSettingsStore().read()
        val originalVpn = entry.vpnSettingsStore().read()
        try {
            manager.setVpnEnabled(true).getOrThrow()
            manager.startProxy().getOrThrow()
            val start = manager.runningSinceElapsedMillis.value
            val next = original.copy(mtu = if (original.mtu == 1400) 1500 else 1400, systemProxy = false,
                ipv6 = false, bypassPrivateRoute = true, routeAddress = listOf("0.0.0.0/0"), routeExcludeAddress = listOf("192.0.2.0/24"))
            manager.updateNetworkSettings(original, next).getOrThrow()
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertEquals(start, manager.runningSinceElapsedMillis.value)
            val applied = checkNotNull(com.lonnnnnng.flclash.service.ServiceConfig.vpnOptions)
            assertEquals(next.mtu, applied.mtu)
            assertFalse(applied.systemProxy)
            assertFalse(applied.routeAll)
            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
            var verified = false
            repeat(100) {
                val links = connectivity.allNetworks.mapNotNull { network ->
                    if (connectivity.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true)
                        connectivity.getLinkProperties(network) else null
                }
                if (links.any { link -> link.mtu == next.mtu && link.httpProxy == null &&
                    link.routes.any { route -> route.destination.contains(java.net.InetAddress.getByName("8.8.8.8")) } &&
                    link.routes.none { route -> route.destination.contains(java.net.InetAddress.getByName("192.0.2.1")) ||
                        route.destination.contains(java.net.InetAddress.getByName("192.168.1.1")) }
                }) { verified = true; return@repeat }
                kotlinx.coroutines.delay(100)
            }
            assertTrue("系统 VPN 必须回读到新 MTU、关闭的 HTTP 代理和排除后的路由", verified)
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, before.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
        } finally {
            manager.stop().getOrThrow()
            manager.updateNetworkSettings(entry.networkSettingsStore().read(), original).getOrThrow()
            manager.setVpnEnabled(originalVpn).getOrThrow()
        }
    }

    @Test fun actualToolsNetworkPersistsAcrossRecreationAndKeepsProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val profiles = entry.database().profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        val files = profiles.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        val original = entry.networkSettingsStore().read()
        val mtu = if (original.mtu == 1400) 1500 else 1400
        try {
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("tools_item_disclaimer").assertDoesNotExist()
            compose.onNodeWithTag("tools_item_about").assertDoesNotExist()
            compose.onNodeWithTag("tools_item_language").assertExists()
            compose.onNodeWithTag("tools_item_theme").assertExists()
            capture("tools_after_removal")
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_advanced"))
            compose.onNodeWithTag("tools_item_advanced").performClick()
            compose.onNodeWithTag("advanced_item_network").performClick()
            capture("network_actual_top")
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("network_mtu"))
            compose.onNodeWithTag("network_mtu").performClick()
            compose.onNodeWithTag("network_editor_input").performTextReplacement(mtu.toString())
            compose.onNodeWithTag("network_editor_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("network_editor").fetchSemanticsNodes().isEmpty() }
            assertEquals(mtu, entry.networkSettingsStore().read().mtu)
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("page_network").assertExists()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("network_mtu"))
            compose.onNodeWithTag("network_mtu").assertTextContains(mtu.toString())
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("network_excluded"))
            capture("network_actual_tun")
            compose.onNodeWithTag("tools_subpage_back").performClick()
            compose.onNodeWithTag("page_advanced").assertIsDisplayed()
            compose.onNodeWithTag("tools_subpage_back").performClick()
            compose.onNodeWithTag("tools_item_general").assertExists()
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            assertEquals(profiles, entry.database().profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, profiles.associate { row -> row.id to entry.profileRepository().profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
        } finally {
            manager.updateNetworkSettings(entry.networkSettingsStore().read(), original).getOrThrow()
            assertEquals(original, entry.networkSettingsStore().read())
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
