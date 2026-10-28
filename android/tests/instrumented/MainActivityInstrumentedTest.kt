package com.lonnnnnng.flclash

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：主页面宿主测试必须从真实 MainActivity 进入，确认 Hilt、ViewModel、主题和 Compose 根页面
 * 已经连成可交互入口，并覆盖手机主导航与观测页签；测试不触发订阅网络、VPN 授权或核心启动。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityRendersHomeAndProfileEntry() {
        composeRule.onNodeWithText("KClash").assertIsDisplayed()
        composeRule.onNodeWithText("核心状态").assertIsDisplayed()
        composeRule.onNodeWithTag("always_on_vpn_card").assertIsDisplayed()
        composeRule.onNodeWithText("启动核心").assertIsDisplayed()
        // 作者 long：ExtendedFloatingActionButton 的文本会被父节点合并，入口断言必须查看未合并语义树。
        composeRule.onNodeWithText("添加配置", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun mainActivityNavigatesAcrossPrimaryAndObservationDestinations() {
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("核心状态").assertIsDisplayed()
        composeRule.onNodeWithText("代理").performClick()
        composeRule.onNodeWithTag("page_proxy").assertIsDisplayed()
        composeRule.onNodeWithText("代理组").assertIsDisplayed()
        composeRule.onNodeWithText("连接").performClick()
        composeRule.onNodeWithTag("page_connections").assertIsDisplayed()
        composeRule.onNodeWithText("活动连接").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("观测").performClick()
        composeRule.onNodeWithTag("page_observations").assertIsDisplayed()
        composeRule.onNodeWithTag("page_requests").assertIsDisplayed()
        composeRule.onNodeWithText("DNS").performClick()
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()
        composeRule.onNodeWithText("DNS 查询").assertIsDisplayed()
        composeRule.onNodeWithText("日志").performClick()
        composeRule.onNodeWithTag("page_logs").assertIsDisplayed()
        composeRule.onNodeWithText("核心日志").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityRetainsObservationTabAcrossPrimaryNavigationAndRecreation() {
        composeRule.onNodeWithText("观测").performClick()
        composeRule.onNodeWithText("DNS").performClick()
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()

        composeRule.onNodeWithText("代理").performClick()
        composeRule.onNodeWithTag("page_proxy").assertIsDisplayed()
        composeRule.onNodeWithText("观测").performClick()
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()

        // 作者 long：Activity 重建会重新创建页面组合，只有主页面的可保存导航状态才能恢复用户正在查看的页签。
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("page_dns").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()
    }

    @Test
    fun mainActivityCompletesCustomProfileLifecycleThroughRealViewModels() {
        val label = "Activity E2E 配置"

        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("名称（可选）").performTextInput(label)
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        composeRule.onAllNodesWithText("删除")[1].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("还没有配置").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }
}
