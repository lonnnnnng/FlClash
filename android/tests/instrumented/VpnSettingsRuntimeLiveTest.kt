package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实模式切换必须显式开启，结束恢复原偏好、布局、选择和停止态，保留已导入配置。 */
class VpnSettingsRuntimeLiveTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun vpnChoicePersistsAndExplicitRestartSwitchesRealTunAndProxyModes() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashVpnAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        val observer = NetworkObserver.snapshot()
        assertTrue("加载配置前就应注册进程级底层网络观察", observer.started && observer.registeredAt > 0)
        val repository = entry.profileRepository()
        runBlocking { repository.profilesLoaded.first { it } }
        val originalRows = runBlocking { entry.database().profiles().getAll() }
        val originalSelected = repository.currentProfileId.value
        val localYamlPath = args.getString("kclashVpnAcceptanceLocalYamlPath").orEmpty()
        val localYamlSha = args.getString("kclashVpnAcceptanceLocalYamlSha256").orEmpty()
        var temporaryProfile: com.lonnnnnng.flclash.data.Profile? = null
        val original = entry.vpnSettingsStore().read()
        val originalNetwork = entry.networkSettingsStore().read()
        val context = compose.activity.applicationContext
        val layout = DashboardLayoutStore.load(context)
        try {
            val activeProfile = if (localYamlPath.isNotEmpty()) {
                require(Regex("/data/local/tmp/kclash-vpn-settings-[a-f0-9]{32}\\.yaml").matches(localYamlPath))
                require(localYamlSha.matches(Regex("[a-f0-9]{64}")))
                val descriptor = instrumentation.uiAutomation.executeShellCommand("cat $localYamlPath")
                val source = descriptor.use { FileInputStream(it.fileDescriptor).readBytes() }
                assertEquals(localYamlSha, java.security.MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) })
                runBlocking { repository.addFileProfile("常规设置 VPN 重启验收", source) }.also { temporaryProfile = it }
            } else {
                compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
                repository.profiles.value.first { it.id == repository.currentProfileId.value }
            }
            if (localYamlPath.isEmpty()) assertEquals("只复用授权来源", args.getString("kclashVpnAuthorizedSource"), activeProfile.url)
            runBlocking { manager.selectProfile(activeProfile.id, repository::selectProfile) }
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { file -> file.length() to file.lastModified() } }
            runBlocking(Dispatchers.IO) { manager.setVpnEnabled(true).getOrThrow() }
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND)))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_add_vpn"))
            compose.onNodeWithTag("dashboard_add_vpn").performClick()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            vpn().performClick()
            compose.waitUntil(5_000) { !manager.vpnSettings.value.enabled }
            assertFalse(entry.vpnSettingsStore().read())
            compose.activityRule.scenario.recreate()
            vpn().assertIsOff()
            compose.onNodeWithTag("dashboard_runtime_toggle").performScrollTo().performClick()
            waitForRuntime()
            assertFalse(ServiceConfig.vpnAlive.value)
            assertFalse(hasTunInterface())
            assertTrue(ServiceConfig.proxyAlive.value)
            runBlocking(Dispatchers.IO) { manager.refreshLanSharing() }
            val port = manager.lanSharing.value.config.mixedPort
            assertTrue(port > 0)
            assertTrue(runBlocking(Dispatchers.IO) { runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1500) } }.isSuccess })
            val proxySession = manager.runningSinceElapsedMillis.value
            vpn().performClick()
            compose.waitUntil(5_000) { manager.vpnSettings.value.restartRequired }
            assertEquals(proxySession, manager.runningSinceElapsedMillis.value)
            assertFalse(ServiceConfig.vpnAlive.value)
            compose.onNodeWithTag("vpn_restart").performScrollTo().performClick()
            waitForRuntime()
            assertTrue(ServiceConfig.vpnAlive.value)
            assertEquals(VpnRuntimeState.CONNECTED, ServiceConfig.vpnState.value)
            assertTrue(hasTunInterface())
            // 作者 long：新网络参数只能作为下次启动意图；保存后立刻改现有 TUN 会中断正在使用的代理连接。
            val appliedBefore = checkNotNull(ServiceConfig.vpnOptions)
            val beforeParameterSave = manager.runningSinceElapsedMillis.value
            val nextNetwork = originalNetwork.copy(mtu = if (originalNetwork.mtu == 1400) 1500 else 1400,
                systemProxy = !originalNetwork.systemProxy)
            runBlocking { manager.updateNetworkSettings(originalNetwork, nextNetwork).getOrThrow() }
            assertTrue(manager.vpnSettings.value.restartRequired)
            assertEquals(beforeParameterSave, manager.runningSinceElapsedMillis.value)
            assertEquals(appliedBefore, ServiceConfig.vpnOptions)
            compose.onNodeWithTag("vpn_restart").performScrollTo().performClick()
            waitForRuntime()
            assertNotEquals(beforeParameterSave, manager.runningSinceElapsedMillis.value)
            assertEquals(nextNetwork.mtu, ServiceConfig.vpnOptions?.mtu)
            assertEquals(nextNetwork.systemProxy, ServiceConfig.vpnOptions?.systemProxy)
            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val links = connectivity.allNetworks.filter { connectivity.getNetworkCapabilities(it)
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true }.mapNotNull(connectivity::getLinkProperties)
            assertTrue("系统必须建立带新 MTU/HTTP代理的 VPN", links.any { it.mtu == nextNetwork.mtu && (it.httpProxy != null) == nextNetwork.systemProxy })
            val vpnSession = manager.runningSinceElapsedMillis.value
            vpn().performClick()
            compose.waitUntil(5_000) { manager.vpnSettings.value.restartRequired }
            assertEquals(vpnSession, manager.runningSinceElapsedMillis.value)
            assertTrue(hasTunInterface())
            compose.onNodeWithTag("vpn_restart").performScrollTo().performClick()
            waitForRuntime()
            assertFalse(ServiceConfig.vpnAlive.value)
            assertFalse(hasTunInterface())
            assertTrue(ServiceConfig.proxyAlive.value)
            assertFalse(manager.vpnSettings.value.restartRequired)
            runBlocking(Dispatchers.IO) { manager.stop().getOrThrow() }
            assertFalse(ServiceConfig.proxyAlive.value)
            // 作者 long：纯代理→VPN→纯代理和停止都不能注销共用观察器，否则停止态Provider与下次启动失去系统DNS。
            assertTrue(NetworkObserver.snapshot().started)
            assertEquals(observer.registeredAt, NetworkObserver.snapshot().registeredAt)
            assertTrue(NetworkObserver.snapshot().dnsServerCount > 0)
            assertEquals(metadata.keys, repository.profiles.value.map { it.id }.toSet())
            assertEquals(activeProfile.id, repository.currentProfileId.value)
            metadata.forEach { (id, stamp) -> repository.profileFile(id).let { assertEquals(stamp, it.length() to it.lastModified()) } }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_vpn_acceptance", "proxy-tun-parameter-pending-explicit-restart-system-mtu-http-proxy-proxy-listener-cleanup-passed") })
        } finally {
            runBlocking(Dispatchers.IO) {
                manager.stop().getOrThrow()
                manager.updateNetworkSettings(entry.networkSettingsStore().read(), originalNetwork).getOrThrow()
                manager.setVpnEnabled(original).getOrThrow()
                originalSelected?.let { selected ->
                    if (repository.profiles.value.any { it.id == selected }) manager.selectProfile(selected, repository::selectProfile)
                }
                temporaryProfile?.let { row ->
                    val owned = repository.profiles.value.firstOrNull { it.id == row.id }
                    if (owned?.label == row.label && owned.type == com.lonnnnnng.flclash.data.ProfileType.FILE) repository.deleteProfile(row.id)
                }
            }
            DashboardLayoutStore.save(context, layout)
            compose.activityRule.scenario.recreate()
            assertEquals(originalRows, runBlocking { entry.database().profiles().getAll() })
            assertEquals(originalSelected, repository.currentProfileId.value)
            temporaryProfile?.let { row -> assertTrue("验收副本必须收回", repository.profiles.value.none { it.id == row.id }) }
        }
    }

    private fun hasTunInterface(): Boolean {
        // 作者 long：应用沙箱不能以 File.exists 可靠读取 sysfs；通过验收 shell 独立核对真实接口。
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("ls /sys/class/net")
        val names = descriptor.use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
        return names.lineSequence().any { it.trim() == "tun0" }
    }

    private fun vpn(): SemanticsNodeInteraction {
        compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("vpn_enabled"))
        return compose.onNodeWithTag("vpn_enabled")
    }

    private fun waitForRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(35_000) {
            // 作者 long：测试仅确认用户触发的系统 VPN 授权，停止/重启逻辑仍通过正式页面入口。
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() == "com.android.vpndialogs") {
                root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            val manager = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java).runtimeManager()
            manager.state.value == CoreRuntimeStatus.FAILED ||
                (manager.state.value.isRunning && !manager.vpnSettings.value.restartRequired)
        }
        val manager = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java).runtimeManager()
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
    }
}
