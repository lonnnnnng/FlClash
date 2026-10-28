package com.lonnnnnng.flclash

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
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
    fun mainActivityRendersObservationEmptyStatesFromEachTab() {
        composeRule.onNodeWithText("观测").performClick()
        composeRule.onNodeWithTag("page_observations").assertIsDisplayed()
        composeRule.onNodeWithTag("page_requests").assertIsDisplayed()
        composeRule.onNodeWithText("请求记录").assertIsDisplayed()
        // 作者 long：停止态必须明确告诉用户数据何时产生，避免空列表被误解为加载失败或数据丢失。
        composeRule.onNodeWithText("启动核心后显示请求、目标和规则命中信息。").assertIsDisplayed()

        composeRule.onNodeWithText("DNS").performClick()
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()
        composeRule.onNodeWithText("DNS 查询").assertIsDisplayed()
        composeRule.onNodeWithText("启动核心后显示 DNS 查询记录。").assertIsDisplayed()

        composeRule.onNodeWithText("日志").performClick()
        composeRule.onNodeWithTag("page_logs").assertIsDisplayed()
        composeRule.onNodeWithText("核心日志").assertIsDisplayed()
        composeRule.onNodeWithText("启动核心后显示 Mihomo 日志。").assertIsDisplayed()

        // 作者 long：真实宿主完成页签切换后仍能回到首页，保证空态回归不会破坏主导航状态。
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

    @Test
    fun mainActivityPersistsCustomProfileRulesThroughRealViewModels() {
        val label = "Activity E2E 规则"
        val rule = "DOMAIN-SUFFIX,example.com,DIRECT"

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
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isEmpty()
        }

        // 作者 long：规则编辑必须经过真实 ProfilesViewModel 和文件写回，再次打开才能验证不是只更新了对话框内存。
        composeRule.onNodeWithText("高级").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("规则 YAML").performTextInput(rule)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("高级").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(rule).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        val deleteButtons = composeRule.onAllNodesWithText("删除")
        deleteButtons[deleteButtons.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityPersistsCustomProfileProxiesThroughRealViewModels() {
        val label = "Activity E2E 代理"
        val proxy = "- name: DIRECT-TEST\n  type: direct"

        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("名称（可选）").performTextInput(label)
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        // 作者 long：先写入最小基础 YAML，确保代理分区回写时要保留同一份 Profile 的其他内容。
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isEmpty()
        }

        // 作者 long：配置卡片按钮与底部导航同名，必须点击卡片内的第一个节点，避免误切到代理页。
        composeRule.onAllNodesWithText("代理")[0].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("代理 YAML").performTextInput(proxy)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onAllNodesWithText("代理")[0].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(proxy).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        val deleteButtons = composeRule.onAllNodesWithText("删除")
        deleteButtons[deleteButtons.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityPersistsCustomProfileProxyGroupsThroughRealViewModels() {
        val label = "Activity E2E 代理组"
        val group = "- name: AUTO-TEST\n  type: select\n  proxies:\n    - DIRECT-TEST"

        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("名称（可选）").performTextInput(label)
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        // 作者 long：先写入最小基础 YAML，确保代理组分区回写时要保留同一份 Profile 的其他内容。
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("代理组").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理组 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("代理组 YAML").performTextInput(group)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理组 · $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("代理组").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理组 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(group).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        val deleteButtons = composeRule.onAllNodesWithText("删除")
        deleteButtons[deleteButtons.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityPersistsCustomProfileProvidersThroughRealViewModels() {
        val label = "Activity E2E Provider"
        val provider = "test:\n  type: file\n  path: ./providers/test.yaml"

        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("名称（可选）").performTextInput(label)
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        // 作者 long：先写入最小基础 YAML，确保 Provider 分区回写时要保留同一份 Profile 的其他内容。
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("Provider").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Provider · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("proxy-providers").performTextInput(provider)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Provider · $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("Provider").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Provider · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(provider).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        val deleteButtons = composeRule.onAllNodesWithText("删除")
        deleteButtons[deleteButtons.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityPersistsCustomProfileDnsOverridesThroughRealViewModels() {
        val label = "Activity E2E DNS"
        val nameserver = "198.18.0.2"

        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("名称（可选）").performTextInput(label)
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("DNS").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("DNS 覆盖 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        // 作者 long：Nameserver 位于可滚动表单下方，先滚到字段再写入，验证真实页面而非直接调用 ViewModel。
        composeRule.onNodeWithTag("profile-dns-override-list")
            .performScrollToNode(hasText("Nameserver"))
        composeRule.onNodeWithText("Nameserver").performTextInput(nameserver)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("DNS 覆盖 · $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("DNS").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("DNS 覆盖 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile-dns-override-list")
            .performScrollToNode(hasText("Nameserver"))
        composeRule.onNodeWithText(nameserver).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        val deleteButtons = composeRule.onAllNodesWithText("删除")
        deleteButtons[deleteButtons.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityPersistsCustomProfileDialersThroughRealViewModels() {
        val label = "Activity E2E Dialers"
        val dialer = "DIRECT-TEST -> AUTO-TEST"

        composeRule.onNodeWithText("配置").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("名称（可选）").performTextInput(label)
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Dialers").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Dialers · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("代理名 -> 目标名").performTextInput(dialer)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Dialers · $label").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("Dialers").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Dialers · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(dialer).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        val deleteButtons = composeRule.onAllNodesWithText("删除")
        deleteButtons[deleteButtons.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityCompletesAccessClipboardLifecycleThroughRealViewModels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidatePackage = findSelectableLauncherPackage(context)
        check(candidatePackage != null) { "设备没有可用于按应用代理 E2E 的非系统网络应用" }

        val dashboard = composeRule.onNodeWithTag("page_dashboard")
        dashboard.performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_search_toggle")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val enabledSwitch = composeRule.onNodeWithTag("access_control_enabled")
        val wasEnabled = runCatching {
            enabledSwitch.assertIsOn()
            true
        }.getOrDefault(false)
        if (!wasEnabled) {
            enabledSwitch.performClick()
            composeRule.waitForIdle()
        }

        val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_export").performClick()
        composeRule.waitForIdle()
        val originalText = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()

        clipboard.setPrimaryClip(ClipData.newPlainText("Access E2E", candidatePackage))
        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_import").performClick()
        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithTag("access_control_search").performTextInput(candidatePackage)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_app_$candidatePackage")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("access_control_app_$candidatePackage").assertIsOn()

        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_export").performClick()
        composeRule.waitForIdle()
        assertEquals(candidatePackage, clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString())

        clipboard.setPrimaryClip(ClipData.newPlainText("Access E2E restore", originalText))
        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_import").performClick()
        if (!wasEnabled) {
            enabledSwitch.performClick()
        }
        composeRule.onNodeWithTag("access_control_save").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun mainActivityRestoresAccessListAfterSearchNoResults() {
        val dashboard = composeRule.onNodeWithTag("page_dashboard")
        dashboard.performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("access_control_app_list")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithTag("access_control_search")
            .performTextInput("__flclash_no_match__")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_no_results")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("access_control_no_results").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_app_list").assertDoesNotExist()
        composeRule.onNodeWithTag("access_control_select_visible").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_clear_visible").assertIsNotEnabled()

        // 作者 long：关闭搜索必须同时清空查询，确保真实宿主不会把无结果状态留在下一次进入页面。
        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_app_list")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("access_control_search").assertDoesNotExist()
        composeRule.onNodeWithTag("access_control_no_results").assertDoesNotExist()
        composeRule.onNodeWithTag("access_control_app_list").assertIsDisplayed()
    }

    @Test
    fun mainActivityTogglesOnlyVisibleAccessResultsAndDiscardsChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidatePackage = findSelectableLauncherPackage(context)
        check(candidatePackage != null) { "设备没有可用于按应用代理 E2E 的非系统网络应用" }

        val dashboard = composeRule.onNodeWithTag("page_dashboard")
        dashboard.performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("access_control_app_list")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val enabledSwitch = composeRule.onNodeWithTag("access_control_enabled")
        val initialEnabled = enabledSwitch.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        val wasEnabled = initialEnabled == ToggleableState.On
        if (!wasEnabled) {
            enabledSwitch.performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithTag("access_control_search").performTextInput(candidatePackage)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_app_$candidatePackage")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val appNode = composeRule.onNodeWithTag("access_control_app_$candidatePackage")
        val initialPackageState = appNode.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

        // 作者 long：先把当前搜索结果归一化为未选中，才能同时验证顶栏 toggle 的选中和取消两条分支。
        if (initialPackageState == ToggleableState.On) {
            composeRule.onNodeWithTag("access_control_toggle_visible").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("access_control_toggle_visible").performClick()
        composeRule.onNodeWithContentDescription("取消全选当前结果").assertIsDisplayed()
        assertEquals(
            ToggleableState.On,
            appNode.fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )

        composeRule.onNodeWithTag("access_control_clear_visible").performClick()
        composeRule.onNodeWithContentDescription("全选当前结果").assertIsDisplayed()
        assertEquals(
            ToggleableState.Off,
            appNode.fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )

        composeRule.onNodeWithTag("access_control_select_visible").performClick()
        assertEquals(
            ToggleableState.On,
            appNode.fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )
        composeRule.onNodeWithTag("access_control_toggle_visible").performClick()
        composeRule.onNodeWithContentDescription("全选当前结果").assertIsDisplayed()
        assertEquals(
            ToggleableState.Off,
            appNode.fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )

        // 作者 long：关闭搜索后离开首页，走真实未保存对话框并放弃修改，确保批量操作不会写入 Room。
        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithText("观测").performClick()
        composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_discard_changes").performClick()
        composeRule.onNodeWithTag("page_observations").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_search_toggle")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        assertEquals(
            initialEnabled,
            composeRule.onNodeWithTag("access_control_enabled")
                .fetchSemanticsNode()
                .config[SemanticsProperties.ToggleableState],
        )
        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithTag("access_control_search").performTextInput(candidatePackage)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_app_$candidatePackage")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertEquals(
            initialPackageState,
            composeRule.onNodeWithTag("access_control_app_$candidatePackage")
                .fetchSemanticsNode()
                .config[SemanticsProperties.ToggleableState],
        )
        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
    }

    @Test
    fun mainActivityPersistsDirectAccessSelectionAcrossRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidatePackage = findSelectableLauncherPackage(context)
        check(candidatePackage != null) { "设备没有可用于按应用代理 E2E 的非系统网络应用" }

        val dashboard = composeRule.onNodeWithTag("page_dashboard")
        dashboard.performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_search_toggle")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_export").performClick()
        composeRule.waitForIdle()
        val originalText = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()

        val enabledSwitch = composeRule.onNodeWithTag("access_control_enabled")
        val wasEnabled = runCatching {
            enabledSwitch.assertIsOn()
            true
        }.getOrDefault(false)
        if (!wasEnabled) {
            enabledSwitch.performClick()
        }

        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithTag("access_control_search").performTextInput(candidatePackage)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_app_$candidatePackage")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val appNode = composeRule.onNodeWithTag("access_control_app_$candidatePackage")
        val wasSelected = runCatching {
            appNode.assertIsOn()
            true
        }.getOrDefault(false)
        appNode.performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_topbar_save").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("access_control_topbar_save").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("保存按应用代理").fetchSemanticsNodes().isNotEmpty()
        }

        // 作者 long：真实宿主重建后重新读取 Room 设置，才能证明页面编辑不是只停留在内存 StateFlow。
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("page_dashboard").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_search_toggle")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("access_control_app_$candidatePackage")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // 重建后不再次输入搜索，直接读取完整应用列表，避免把搜索动画时序混入 Room 恢复断言。
        val restoredNode = composeRule.onNodeWithTag("access_control_app_$candidatePackage")
        if (wasSelected) {
            assertEquals(
                ToggleableState.Off,
                restoredNode.fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
            )
        } else {
            restoredNode.assertIsOn()
        }

        // 恢复测试前的应用选择；原本关闭时通过“放弃更改”回到持久化基线，避免关闭态保存入口被禁用造成污染。
        clipboard.setPrimaryClip(ClipData.newPlainText("Access E2E restore", originalText))
        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_import").performClick()
        if (!wasEnabled) {
            composeRule.onNodeWithTag("access_control_enabled").performClick()
            composeRule.onNodeWithText("观测").performClick()
            composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
            composeRule.onNodeWithText("放弃更改").performClick()
            composeRule.onNodeWithTag("page_observations").assertIsDisplayed()
            composeRule.onNodeWithText("首页").performClick()
        } else {
            composeRule.onNodeWithTag("access_control_save").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun mainActivityReflowsAccessCardAfterRotation() {
        val dashboard = composeRule.onNodeWithTag("page_dashboard")
        dashboard.performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_card").fetchSemanticsNodes().isNotEmpty()
        }

        try {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(5_000) {
                var landscape = false
                composeRule.activityRule.scenario.onActivity { activity ->
                    landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                landscape
            }
            composeRule.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("access_control_card"))
            composeRule.onNodeWithTag("access_control_card").assertIsDisplayed()
            composeRule.onNodeWithTag("access_control_search_toggle").assertIsDisplayed()
            composeRule.onNodeWithTag("access_control_enabled").assertIsDisplayed()
        } finally {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun mainActivityGuardsAccessChangesWhenSystemBackIsPressed() {
        val dashboard = composeRule.onNodeWithTag("page_dashboard")
        dashboard.performScrollToNode(hasTestTag("access_control_card"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_search_toggle")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val enabledSwitch = composeRule.onNodeWithTag("access_control_enabled")
        enabledSwitch.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("观测").performClick()
        composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
        composeRule.onNodeWithText("保存后才会应用新的白名单、黑名单和筛选设置。").assertIsDisplayed()

        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("按应用代理有未保存的修改").assertDoesNotExist()
        enabledSwitch.performClick()
        composeRule.waitForIdle()
    }
}

private fun findSelectableLauncherPackage(context: Context): String? {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager
        .queryIntentActivities(launcherIntent, 0)
        .asSequence()
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()
        .filter { it != context.packageName }
        .firstOrNull { packageName ->
            val appInfo = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }
                .getOrNull()
                ?: return@firstOrNull false
            val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            val hasInternet = context.packageManager.checkPermission(
                Manifest.permission.INTERNET,
                packageName,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            !isSystem && hasInternet
        }
}
