package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：显式开启才启停真实服务，复用已导入授权配置；恢复局域网覆盖、布局和停止状态。 */
class IntranetIpRuntimeLiveTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realAddressCopySharingHotUpdateAndLifecyclePreserveProfiles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashLanAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        val store = entry.lanSharingStore()
        val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        val profile = repository.profiles.value.first { it.id == repository.currentProfileId.value }
        assertTrue("只复用当前授权来源", profile.url == args.getString("kclashLanAuthorizedSource"))
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val metadata = repository.profiles.value.associate { it.id to repository.profileFile(it.id).let { f -> f.length() to f.lastModified() } }
        val originalOverride = store.read()
        val context = compose.activity.applicationContext
        val originalLayout = DashboardLayoutStore.load(context)
        lateinit var model: IntranetIpViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[IntranetIpViewModel::class.java] }
        var requestedStart = false
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND)))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("dashboard_intranet_card").assertDoesNotExist()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_add_intranet_ip"))
            compose.onNodeWithTag("dashboard_add_intranet_ip").performClick()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_intranet_card"))
            compose.waitUntil(15_000) { model.state.value.local.address != null && !model.state.value.settingsLoading }
            assertNull(model.state.value.settingsError)
            val address = checkNotNull(model.state.value.local.address)
            assertFalse(address.startsWith("198.18."))
            assertTrue(manager.lanSharing.value.hasConfig)
            assertTrue(manager.lanSharing.value.config.mixedPort > 0)
            runBlocking(Dispatchers.IO) { manager.updateLanSharing(false) }
            compose.waitUntil(5_000) { !model.state.value.settingsLoading && !model.state.value.sharing.config.allowLan }
            compose.onNodeWithTag("intranet_copy").performClick()
            compose.runOnIdle {
                assertEquals(address, context.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString())
            }
            compose.onNodeWithTag("intranet_allow_lan").performClick()
            compose.waitUntil(10_000) { !model.state.value.saving && model.state.value.sharing.config.allowLan && !model.state.value.settingsLoading }
            assertNull(model.state.value.error)
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            compose.onNodeWithTag("intranet_copy").performClick()
            compose.runOnIdle {
                assertEquals(model.state.value.copyText, context.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString())
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_intranet_card"))
            compose.waitUntil(10_000) { !model.state.value.settingsLoading && model.state.value.local.address != null }
            assertTrue(model.state.value.sharing.config.allowLan)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.waitForIdleSync()
            assertNull(model.state.value.copyText)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_intranet_card"))
            compose.waitUntil(10_000) { model.state.value.local.address != null && !model.state.value.settingsLoading }
            // 作者 long：只建立本机内网 TCP 连接，不向代理发送外部目标；验证监听是否真的随共享开关切换。
            requestedStart = true
            compose.onNodeWithContentDescription("启动核心").performScrollTo().performClick()
            compose.waitUntil(30_000) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root?.packageName?.toString() == "com.android.vpndialogs") root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                manager.state.value in listOf(CoreRuntimeStatus.RUNNING, CoreRuntimeStatus.FAILED)
            }
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            val session = manager.runningSinceElapsedMillis.value
            val port = manager.lanSharing.value.config.mixedPort
            assertTrue("开启共享后内网地址应接受 TCP", connects(address, port))
            runBlocking(Dispatchers.IO) { manager.updateLanSharing(false) }
            assertFalse("关闭共享后内网地址不应接受 TCP", connects(address, port))
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertEquals(session, manager.runningSinceElapsedMillis.value)
            runBlocking(Dispatchers.IO) { manager.updateLanSharing(true) }
            assertTrue("重新开启共享后内网监听应恢复", connects(address, port))
            assertEquals(session, manager.runningSinceElapsedMillis.value)
            runBlocking(Dispatchers.IO) { manager.stop().getOrThrow() }
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_intranet_card"))
            compose.waitUntil(10_000) { !model.state.value.settingsLoading }
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_remove_intranet_ip"))
            compose.onNodeWithTag("dashboard_remove_intranet_ip").performClick()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.activityRule.scenario.recreate()
            assertFalse(DashboardSection.INTRANET_IP in DashboardLayoutStore.load(context).visible)
            assertEquals(metadata.keys, repository.profiles.value.map { it.id }.toSet())
            metadata.forEach { (id, stamp) -> repository.profileFile(id).let { assertEquals(stamp, it.length() to it.lastModified()) } }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_lan_acceptance", "real-address-copy-persistence-background-runtime-listener-toggle-session-passed") })
        } finally {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking(Dispatchers.IO) {
                if (requestedStart) manager.stop().getOrThrow()
                manager.updateLanSharing(originalOverride)
            }
            DashboardLayoutStore.save(context, originalLayout)
            compose.activityRule.scenario.recreate()
        }
    }

    private fun connects(address: String, port: Int): Boolean = runBlocking(Dispatchers.IO) {
        // 作者 long：监听重建同步完成，重试仅消除内核端口关闭的短暂尾部状态。
        runCatching { Socket().use { it.connect(InetSocketAddress(address, port), 1_500) } }.isSuccess
    }
}
