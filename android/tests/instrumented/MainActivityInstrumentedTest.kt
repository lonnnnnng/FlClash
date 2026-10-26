package com.lonnnnnng.flclash

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：主页面宿主测试必须从真实 MainActivity 进入，确认 Hilt、ViewModel、主题和 Compose 根页面
 * 已经连成可交互入口；测试只打开添加配置弹窗，不触发订阅网络、VPN 授权或核心启动。
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
        composeRule.onNodeWithText("启动核心").assertIsDisplayed()
        // 作者 long：ExtendedFloatingActionButton 的文本会被父节点合并，入口断言必须查看未合并语义树。
        composeRule.onNodeWithText("添加配置", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun mainActivityNavigatesBetweenDashboardAndProfiles() {
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("核心状态").assertIsDisplayed()
    }
}
