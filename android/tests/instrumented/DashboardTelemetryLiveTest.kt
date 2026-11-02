package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：显式开启后复用已导入的唯一授权配置，真实计时和流量验收不另造 YAML 或清理用户 Profile。 */
class DashboardTelemetryLiveTest {
    @get:Rule(order = 0)
    val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardTelemetryFollowsRealCoreSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("kclashDashboardTelemetryAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val target = checkNotNull(arguments.getString("kclashTelemetryTarget"))
        val repository = EntryPointAccessors.fromApplication(
            compose.activity.application, ConnectionAcceptanceEntryPoint::class.java,
        ).profileRepository()
        compose.waitUntil(10_000) { repository.profiles.value.isNotEmpty() }
        val current = repository.profiles.value.firstOrNull { it.id == repository.currentProfileId.value }
        assertTrue("必须复用已导入的当前授权配置", current?.url == target)
        lateinit var runtime: MainViewModel
        compose.activityRule.scenario.onActivity { runtime = ViewModelProvider(it)[MainViewModel::class.java] }
        assumeTrue("保留用户已有运行会话", runtime.runtimeState.value == CoreRuntimeStatus.STOPPED)
        var requestedStart = false
        try {
            requestedStart = true
            compose.onNodeWithContentDescription("启动核心").performClick()
            compose.waitUntil(20_000) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root?.packageName?.toString() == "com.android.vpndialogs") {
                    root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                runtime.runtimeState.value in listOf(CoreRuntimeStatus.RUNNING, CoreRuntimeStatus.FAILED)
            }
            assertEquals(CoreRuntimeStatus.RUNNING, runtime.runtimeState.value)
            compose.waitUntil(10_000) { (runtime.uptimeMillis.value ?: 0L) >= 1_000L }
            compose.onNodeWithTag("dashboard_runtime_duration").assertIsDisplayed()
            // 作者 long：只请求本轮传入的授权来源，正文不输出；验证曲线采样确实来自核心网络统计。
            compose.runOnIdle { runtime.probe(target) }
            compose.waitUntil(30_000) { !runtime.probeState.value.isLoading && runtime.probeState.value.result != null }
            assertTrue("授权请求应返回 HTTP 状态", (runtime.probeState.value.result?.statusCode ?: 0) > 0)
            compose.waitUntil(10_000) { runtime.stats.value.trafficHistory.any { it.up > 0 || it.down > 0 } }
            val elapsed = checkNotNull(runtime.uptimeMillis.value)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.waitForIdleSync()
            val backgroundHistory = runtime.stats.value.trafficHistory
            SystemClock.sleep(2_200)
            assertEquals("后台不继续采样或补零", backgroundHistory, runtime.stats.value.trafficHistory)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5_000) { (runtime.uptimeMillis.value ?: 0L) >= elapsed + 2_000L }
            compose.activityRule.scenario.recreate()
            lateinit var restored: MainViewModel
            compose.activityRule.scenario.onActivity { restored = ViewModelProvider(it)[MainViewModel::class.java] }
            assertTrue("Activity 重建必须保留核心会话", runtime === restored)
            compose.waitUntil(5_000) { (restored.uptimeMillis.value ?: 0L) >= elapsed + 2_000L }
            compose.waitUntil(45_000) { runtime.stats.value.trafficHistory.size == DASHBOARD_TRAFFIC_CAPACITY }
            compose.onNodeWithContentDescription("上传速度历史，最近 30 次采样").assertIsDisplayed()
            val peak = runtime.stats.value.trafficHistory.maxOf { it.up + it.down }
            compose.runOnIdle { runtime.stopRuntime() }
            compose.waitUntil(10_000) { runtime.runtimeState.value == CoreRuntimeStatus.STOPPED && runtime.uptimeMillis.value == null }
            compose.waitUntil(5_000) { runtime.stats.value.trafficHistory.isEmpty() }
            compose.onNodeWithTag("dashboard_runtime_duration").assertTextEquals("—")
            compose.onNodeWithContentDescription("下载速度历史，最近 0 次采样").assertIsDisplayed()
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_dashboard_telemetry", "real-core-uptime-history-background-recreate-stop-passed")
                putLong("kclash_observed_peak_bytes", peak)
            })
        } finally {
            if (requestedStart) {
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                compose.runOnIdle { runtime.stopRuntime() }
                compose.waitUntil(10_000) { runtime.runtimeState.value == CoreRuntimeStatus.STOPPED }
            }
        }
    }
}
