package com.lonnnnnng.flclash

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import com.lonnnnnng.flclash.ui.DashboardSection
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

}
