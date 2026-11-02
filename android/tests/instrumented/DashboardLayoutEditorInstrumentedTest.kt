package com.lonnnnnng.flclash

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.cancel
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.up
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ui.DashboardLayoutStore
import com.lonnnnnng.flclash.ui.DashboardLayoutState
import com.lonnnnnng.flclash.ui.DashboardSection
import com.lonnnnnng.flclash.ui.recordVisualPng
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardLayoutEditorInstrumentedTest {
    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardDragContinuesScrollingAtBottomAndStopsOnRelease() {
        assertPortraitDefaultFontConfiguration()
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = DashboardSection.entries.toSet()))
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            val page = composeRule.onNodeWithTag("page_dashboard")
            val card = composeRule.onNodeWithTag("dashboard_drag_current_outbound")
            val pageBounds = page.fetchSemanticsNode().boundsInRoot
            val cardBounds = card.fetchSemanticsNode().boundsInRoot
            composeRule.mainClock.autoAdvance = false
            card.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(800)
            card.performTouchInput { moveTo(Offset(center.x, pageBounds.bottom - cardBounds.top - 8f)) }
            composeRule.mainClock.advanceTimeBy(200)
            val first = dashboardScrollPosition()
            // 作者 long：第二段没有移动事件，只有画面帧；这能区分持续滚动和旧版逐事件推进。
            composeRule.mainClock.advanceTimeBy(200)
            val second = dashboardScrollPosition()
            assertTrue("到达下边缘后应滚动", first > 0f)
            assertTrue("手指停住后应继续滚动 first=$first second=$second order=${DashboardLayoutStore.load(context).visibleInOrder()}", second > first + 1f)
            page.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(100)
            val released = dashboardScrollPosition()
            val saved = DashboardLayoutStore.load(context)
            composeRule.mainClock.advanceTimeBy(500)
            assertEquals(released, dashboardScrollPosition(), 0.1f)
            assertEquals(saved, DashboardLayoutStore.load(context))
            assertTrue(saved.visibleInOrder().indexOf(DashboardSection.CURRENT_OUTBOUND) > 0)
            composeRule.mainClock.autoAdvance = true
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.activityRule.scenario.recreate()
            assertEquals(saved, DashboardLayoutStore.load(context))
        } finally {
            composeRule.mainClock.autoAdvance = true
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun dashboardDragContinuesScrollingAtTopAndStopsOnCancel() {
        assertPortraitDefaultFontConfiguration()
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            val order = DashboardSection.entries.filterNot { it == DashboardSection.NETWORK_STATS } + DashboardSection.NETWORK_STATS
            DashboardLayoutStore.save(context, DashboardLayoutState(order, order.toSet()))
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            val page = composeRule.onNodeWithTag("page_dashboard")
            page.performScrollToNode(hasTestTag("dashboard_drag_network_stats"))
            val card = composeRule.onNodeWithTag("dashboard_drag_network_stats")
            val pageBounds = page.fetchSemanticsNode().boundsInRoot
            val cardBounds = card.fetchSemanticsNode().boundsInRoot
            composeRule.mainClock.autoAdvance = false
            card.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(800)
            card.performTouchInput { moveTo(Offset(center.x, pageBounds.top - cardBounds.top + 8f)) }
            composeRule.mainClock.advanceTimeBy(200)
            val first = dashboardScrollPosition()
            composeRule.mainClock.advanceTimeBy(200)
            val second = dashboardScrollPosition()
            assertTrue("手指停在上边缘应继续回滚 first=$first second=$second order=${DashboardLayoutStore.load(context).visibleInOrder()}", second < first - 1f)
            page.performTouchInput { cancel() }
            composeRule.mainClock.advanceTimeBy(100)
            val cancelled = dashboardScrollPosition()
            val saved = DashboardLayoutStore.load(context)
            composeRule.mainClock.advanceTimeBy(500)
            assertEquals(cancelled, dashboardScrollPosition(), 0.1f)
            assertEquals(saved, DashboardLayoutStore.load(context))
        } finally {
            composeRule.mainClock.autoAdvance = true
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    private fun dashboardScrollPosition(): Float = composeRule.onNodeWithTag("page_dashboard")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun dashboardEdgeDragDoesNotResumeAfterBackgroundOrEditorExit() {
        assertPortraitDefaultFontConfiguration()
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState(visible = DashboardSection.entries.toSet()))
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            val page = composeRule.onNodeWithTag("page_dashboard")
            val card = composeRule.onNodeWithTag("dashboard_drag_current_outbound")
            val pageBounds = page.fetchSemanticsNode().boundsInRoot
            val cardBounds = card.fetchSemanticsNode().boundsInRoot
            composeRule.mainClock.autoAdvance = false
            card.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(800)
            card.performTouchInput { moveTo(Offset(center.x, pageBounds.bottom - cardBounds.top - 8f)) }
            composeRule.mainClock.advanceTimeBy(100)
            composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            composeRule.mainClock.advanceTimeBy(100)
            val resumed = dashboardScrollPosition()
            val saved = DashboardLayoutStore.load(context)
            composeRule.mainClock.advanceTimeBy(300)
            assertEquals(resumed, dashboardScrollPosition(), 0.1f)
            assertEquals(saved, DashboardLayoutStore.load(context))
            page.performTouchInput { cancel() }
            // 作者 long：完成编辑不能让旧滚动循环进入普通首页，回来后也不恢复旧拿起状态。
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.mainClock.advanceTimeBy(100)
            val finished = dashboardScrollPosition()
            composeRule.mainClock.advanceTimeBy(300)
            assertEquals(finished, dashboardScrollPosition(), 0.1f)
            assertEquals(saved, DashboardLayoutStore.load(context))
            composeRule.mainClock.autoAdvance = true
            composeRule.onNodeWithText("仪表盘").assertIsDisplayed()
        } finally {
            composeRule.mainClock.autoAdvance = true
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun dashboardLayoutEditorReordersAndPersistsSectionVisibility() {
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState())
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.onNodeWithText("编辑仪表盘").assertIsDisplayed()
            composeRule.onNodeWithTag("dashboard_edit_section_network_stats").assertIsDisplayed()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_edit_section_profiles"))
            composeRule.onNodeWithTag("dashboard_edit_section_profiles").assertIsDisplayed()

            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_remove_network_stats"))
            composeRule.onNodeWithTag("dashboard_remove_network_stats").performClick()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_add_network_stats"))
            composeRule.onNodeWithTag("dashboard_add_row_network_stats").assertIsDisplayed()
            composeRule.onNodeWithTag("dashboard_add_network_stats").performClick()
            val expected = DashboardLayoutStore.load(context)
            assertTrue(DashboardSection.NETWORK_STATS in expected.visible)
            assertEquals(DashboardSection.NETWORK_STATS, expected.visibleInOrder().last())

            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.onNodeWithText("仪表盘").assertIsDisplayed()
            composeRule.onNodeWithTag("dashboard_stats_card").performScrollTo().assertIsDisplayed()

            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_stats_card").performScrollTo().assertIsDisplayed()
            composeRule.runOnIdle { assertEquals(expected, DashboardLayoutStore.load(context)) }
        } finally {
            // 作者 long：首页布局是设备级偏好，验收只验证保存回读，退出时恢复测试开始前的布局。
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun dashboardCardsCanBeReorderedByDraggingInPlace() {
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState())
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            val dragDistance = with(composeRule.density) { 170.dp.toPx() }
            composeRule.onNodeWithTag("dashboard_drag_network_stats").performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(0f, dragDistance), delayMillis = 500)
                up()
            }
            val reordered = DashboardLayoutStore.load(context)
            assertNotEquals(DashboardLayoutState().visibleInOrder(), reordered.visibleInOrder())
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
        } finally {
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun dashboardStatusCardCanBeDraggedAndCannotBeRemoved() {
        assertPortraitDefaultFontConfiguration()
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            val sections = listOf(DashboardSection.CURRENT_OUTBOUND, DashboardSection.OUTBOUND_MODE, DashboardSection.CONNECTION_STATS)
            DashboardLayoutStore.save(context, DashboardLayoutState(order = sections, visible = sections.toSet()))
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.onNodeWithTag("dashboard_remove_current_outbound").assertDoesNotExist()
            composeRule.onNodeWithTag("dashboard_mode_rule").assertDoesNotExist()
            // 作者 long：长按状态卡主体模拟 Flutter 的整卡排序，验证启动入口只禁止移除，不禁止移动。
            val dragDistance = with(composeRule.density) { 190.dp.toPx() }
            composeRule.onNodeWithTag("dashboard_drag_current_outbound").performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(0f, dragDistance), delayMillis = 500)
                up()
            }
            val moved = DashboardLayoutStore.load(context)
            assertEquals(DashboardSection.OUTBOUND_MODE, moved.visibleInOrder().first())
            assertTrue(DashboardSection.CURRENT_OUTBOUND in moved.visible)
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.activityRule.scenario.recreate()
            composeRule.runOnIdle { assertEquals(moved, DashboardLayoutStore.load(context)) }
            composeRule.onNodeWithTag("dashboard_runtime_card").performScrollTo().assertIsDisplayed()
        } finally {
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun dashboardEditorUsesOverlayRemovalAndLeadingAddControls() {
        assertPortraitDefaultFontConfiguration()
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            val sections = listOf(DashboardSection.CURRENT_OUTBOUND, DashboardSection.OUTBOUND_MODE)
            DashboardLayoutStore.save(context, DashboardLayoutState(order = sections, visible = sections.toSet()))
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.onNodeWithTag("dashboard_remove_current_outbound").assertDoesNotExist()
            composeRule.onNodeWithTag("dashboard_mode_global").assertDoesNotExist()
            composeRule.onNodeWithTag("dashboard_profile_switch").assertDoesNotExist()
            val card = composeRule.onNodeWithTag("dashboard_drag_outbound_mode").fetchSemanticsNode().boundsInRoot
            val remove = composeRule.onNodeWithTag("dashboard_remove_outbound_mode").fetchSemanticsNode().boundsInRoot
            assertTrue(remove.center.y in card.top..card.bottom)
            assertEquals(28f, with(composeRule.density) { remove.width.toDp().value }, 1f)

            composeRule.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_add_connection_stats"))
            val button = composeRule.onNodeWithTag("dashboard_add_connection_stats").fetchSemanticsNode().boundsInRoot
            val icon = composeRule.onNodeWithTag("dashboard_add_icon_connection_stats").fetchSemanticsNode().boundsInRoot
            val label = composeRule.onNodeWithTag("dashboard_add_label_connection_stats").fetchSemanticsNode().boundsInRoot
            assertTrue(button.right < icon.left && icon.right < label.left)
            assertEquals(28f, with(composeRule.density) { button.width.toDp().value }, 0.5f)
            assertEquals(40f, with(composeRule.density) { icon.width.toDp().value }, 0.5f)
            composeRule.onNodeWithText("实时连接、最近请求、DNS查询").assertIsDisplayed()
            val bitmap = composeRule.onNodeWithTag("page_dashboard").captureToImage().asAndroidBitmap()
            val png = ByteArrayOutputStream().also { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
            recordVisualPng("dashboard_editor_addable_controls", png)
            composeRule.onNodeWithTag("dashboard_add_connection_stats").performClick()
            assertEquals(DashboardSection.CONNECTION_STATS, DashboardLayoutStore.load(context).visibleInOrder().last())
        } finally {
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun optionalConnectionStatsCardOpensEachFlutterFeedDestination() {
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState())
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_add_connection_stats"))
            composeRule.onNodeWithTag("dashboard_add_connection_stats").performClick()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()

            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_connection_stats_card"))
            val bitmap = composeRule.onNodeWithTag("dashboard_connection_stats_card")
                .captureToImage().asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordVisualPng("dashboard_connection_stats", png)
            composeRule.onNodeWithTag("dashboard_connection_stats_connections").performClick()
            composeRule.onNodeWithTag("page_connections").assertIsDisplayed()

            composeRule.onNodeWithTag("nav_dashboard").performClick()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_connection_stats_card"))
            composeRule.onNodeWithTag("dashboard_connection_stats_requests").performClick()
            composeRule.onNodeWithTag("page_observations").assertIsDisplayed()
            composeRule.onNodeWithTag("observation_list_page").assertIsDisplayed()
            composeRule.onNodeWithText("最近请求").assertIsDisplayed()
            composeRule.onNodeWithTag("observation_list_back").performClick()

            composeRule.onNodeWithTag("nav_dashboard").performClick()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_connection_stats_card"))
            composeRule.onNodeWithTag("dashboard_connection_stats_dns").performClick()
            composeRule.onNodeWithTag("page_observations").assertIsDisplayed()
            composeRule.onNodeWithTag("observation_list_page").assertIsDisplayed()
            composeRule.onNodeWithText("DNS查询").assertIsDisplayed()
        } finally {
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun accessExitDecisionSurvivesRecreationBeforeDashboardNavigation() {
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(
                context,
                DashboardLayoutState().setVisible(DashboardSection.CONNECTION_STATS, true),
            )
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("nav_tools").performClick()
            composeRule.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_access"))
            composeRule.onNodeWithTag("tools_item_access").performClick()
            // 作者 long：独立子页进入更快，须等待 Room 设置加载完成，不能点击尚禁用的开关再误判退出保护。
            composeRule.waitUntil(15_000) {
                runCatching {
                    composeRule.onNodeWithTag("access_control_enabled").assertIsEnabled()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("access_control_enabled").performClick()
            composeRule.onNodeWithTag("nav_dashboard").performClick()
            waitForAccessExitDialog()
            composeRule.onNodeWithText("取消").performClick()
            composeRule.onNodeWithTag("page_access_control").assertIsDisplayed()
            composeRule.onNodeWithTag("observation_list_page").assertDoesNotExist()

            composeRule.onNodeWithTag("nav_dashboard").performClick()
            composeRule.activityRule.scenario.recreate()
            waitForAccessExitDialog()
            composeRule.onNodeWithText("放弃更改").performClick()
            composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_connection_stats_card"))
            composeRule.onNodeWithTag("dashboard_connection_stats_requests").performClick()
            composeRule.onNodeWithText("最近请求").assertIsDisplayed()
            composeRule.onNodeWithTag("observation_list_back").performClick()
            composeRule.onNodeWithTag("nav_dashboard").performClick()
        } finally {
            // 作者 long：仅放弃本用例的应用代理草稿，不保存设置或触碰设备上的真实 Profile。
            if (composeRule.onAllNodesWithText("按应用代理有未保存的修改").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText("放弃更改").performClick()
            }
            DashboardLayoutStore.save(context, original)
            composeRule.activityRule.scenario.recreate()
        }
    }

}

// 作者 long：重建后的确认框仍需完成独立窗口布局，实际显示后才继续取消或放弃草稿。
private fun DashboardLayoutEditorInstrumentedTest.waitForAccessExitDialog() {
    composeRule.waitUntil(5_000) {
        runCatching {
            composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
            true
        }.getOrDefault(false)
    }
}
