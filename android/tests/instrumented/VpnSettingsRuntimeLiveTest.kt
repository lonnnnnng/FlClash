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
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        val profile = repository.profiles.value.first { it.id == repository.currentProfileId.value }
        assertTrue("只复用授权来源", profile.url == args.getString("kclashVpnAuthorizedSource"))
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { file -> file.length() to file.lastModified() } }
        val original = entry.vpnSettingsStore().read()
        val context = compose.activity.applicationContext
        val layout = DashboardLayoutStore.load(context)
        try {
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
            assertEquals(profile.id, repository.currentProfileId.value)
            metadata.forEach { (id, stamp) -> repository.profileFile(id).let { assertEquals(stamp, it.length() to it.lastModified()) } }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_vpn_acceptance", "saved-choice-pending-explicit-restart-proxy-tun-proxy-listener-cleanup-passed") })
        } finally {
            runBlocking(Dispatchers.IO) {
                manager.stop().getOrThrow()
                manager.setVpnEnabled(original).getOrThrow()
            }
            DashboardLayoutStore.save(context, layout)
            compose.activityRule.scenario.recreate()
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
