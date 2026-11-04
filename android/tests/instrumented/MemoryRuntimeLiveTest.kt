package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式首页读取实际 Go 内存；测试只改布局并恢复，不加载配置或接管流量。 */
class MemoryRuntimeLiveTest {
    @get:Rule(order = 0)
    val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun optionalMemoryCardReadsReleasesAndRestoresWithoutStartingRuntime() {
        assertPortraitDefaultFontConfiguration()
        lateinit var runtime: MainViewModel
        lateinit var memory: MemoryViewModel
        compose.activityRule.scenario.onActivity {
            runtime = ViewModelProvider(it)[MainViewModel::class.java]
            memory = ViewModelProvider(it)[MemoryViewModel::class.java]
        }
        assumeTrue("保留用户已有运行会话", runtime.runtimeState.value == CoreRuntimeStatus.STOPPED)
        val context = compose.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = setOf(DashboardSection.CURRENT_OUTBOUND)))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("dashboard_memory_card").assertDoesNotExist()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_add_memory_info"))
            compose.onNodeWithTag("dashboard_add_memory_info").performClick()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_memory_card"))
            compose.waitUntil(10_000) { memory.state.value.snapshot != null }
            val first = checkNotNull(memory.state.value.snapshot)
            assertTrue("进程 RSS 应为真实正值", first.rss > 0)
            assertTrue("Go 运行时应为真实正值", first.runtimeTotal > 0)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.runtimeState.value)
            compose.onNodeWithTag("dashboard_memory_card").performClick()
            compose.onNodeWithTag("memory_release").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { memory.state.value.feedback != null || memory.state.value.error != null }
            assertNull(memory.state.value.error)
            compose.onNodeWithTag("memory_detail_feedback").performScrollTo().assertIsDisplayed()
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.runtimeState.value)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("memory_detail_sheet").assertIsDisplayed()
            compose.onNodeWithTag("memory_detail_close").assertIsDisplayed().performClick()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val background = memory.state.value.snapshot
            SystemClock.sleep(2_200)
            assertSame("后台不得继续采样", background, memory.state.value.snapshot)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_memory_card"))
            compose.waitUntil(5_000) { memory.state.value.snapshot !== background }
            assertTrue(DashboardSection.MEMORY_INFO in DashboardLayoutStore.load(context).visible)
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_remove_memory_info"))
            compose.onNodeWithTag("dashboard_remove_memory_info").performClick()
            compose.onNodeWithTag("dashboard_edit_layout").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("dashboard_memory_card").assertDoesNotExist()
            assertFalse(DashboardSection.MEMORY_INFO in DashboardLayoutStore.load(context).visible)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.runtimeState.value)
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_memory_acceptance", "real-rss-runtime-release-background-recreate-layout-stopped-passed")
                putLong("kclash_memory_rss", first.rss)
                putLong("kclash_memory_go", first.runtimeTotal)
            })
        } finally {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            DashboardLayoutStore.save(context, original)
            compose.activityRule.scenario.recreate()
        }
    }
}
