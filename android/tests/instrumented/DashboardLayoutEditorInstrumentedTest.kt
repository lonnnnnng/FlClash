package com.lonnnnnng.flclash

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.up
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ui.DashboardLayoutStore
import com.lonnnnnng.flclash.ui.DashboardLayoutState
import com.lonnnnnng.flclash.ui.DashboardSection
import com.lonnnnnng.flclash.ui.recordVisualPng
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
    fun dashboardLayoutEditorReordersAndPersistsSectionVisibility() {
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(context, DashboardLayoutState())
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
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
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
            val dragDistance = with(composeRule.density) { 220.dp.toPx() }
            composeRule.onNodeWithTag("dashboard_drag_network_stats").performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(0f, dragDistance), delayMillis = 500)
                up()
            }
            val reordered = DashboardLayoutStore.load(context)
            assertNotEquals(original.visibleInOrder(), reordered.visibleInOrder())
            composeRule.onNodeWithTag("dashboard_edit_layout").performClick()
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
    fun connectionStatsNavigationWaitsForAccessDraftDecisionAcrossRecreation() {
        val context = composeRule.activity.applicationContext
        val original = DashboardLayoutStore.load(context)
        try {
            DashboardLayoutStore.save(
                context,
                DashboardLayoutState().setVisible(DashboardSection.CONNECTION_STATS, true),
            )
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("access_control_enabled"))
            composeRule.onNodeWithTag("access_control_enabled").performClick()
            composeRule.onNodeWithTag("page_dashboard")
                .performScrollToNode(hasTestTag("dashboard_connection_stats_card"))
            composeRule.onNodeWithTag("dashboard_connection_stats_dns").performClick()
            composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
            composeRule.onNodeWithText("取消").performClick()
            composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
            composeRule.onNodeWithTag("observation_list_page").assertDoesNotExist()

            composeRule.onNodeWithTag("dashboard_connection_stats_requests").performClick()
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
            composeRule.onNodeWithText("放弃更改").performClick()
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
