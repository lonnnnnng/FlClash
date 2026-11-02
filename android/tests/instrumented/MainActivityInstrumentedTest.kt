package com.lonnnnnng.flclash

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.lifecycle.ViewModelProvider
import com.lonnnnnng.flclash.ui.AccessControlUiState
import com.lonnnnnng.flclash.ui.AccessControlViewModel
import com.lonnnnnng.flclash.ui.currentPackageList
import com.lonnnnnng.flclash.data.RoomAccessControlSettingsStore
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/**
 * 作者 long：主页面宿主测试必须从真实 MainActivity 进入，确认 Hilt、ViewModel、主题和 Compose 根页面
 * 已经连成可交互入口，并覆盖手机主导航与观测页签；只有显式授权的配置准备用例访问订阅网络。
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
    fun mainActivityPreparesAuthorizedProfilesForUiAcceptance() {
        val arguments = InstrumentationRegistry.getArguments()
        val urls = listOfNotNull(
            arguments.getString("kclashAcceptanceUrl1")?.takeIf { it.isNotBlank() },
        )
        // 作者 long：普通 UI 回归不访问订阅；真机准备只接收工作区当前唯一授权地址，验收后保留配置。
        assumeTrue(
            "配置准备仅用于显式授权的真机验收",
            arguments.getString("kclashPrepareProfiles") == "true" && urls.size == 1,
        )
        val entryPoint = EntryPointAccessors.fromApplication(
            composeRule.activity.application,
            ConnectionAcceptanceEntryPoint::class.java,
        )
        val repository = entryPoint.profileRepository()
        var added = 0
        var repaired = 0
        var reused = 0
        val failures = mutableListOf<String>()
        runBlocking(Dispatchers.IO) {
            urls.forEachIndexed { index, url ->
                try {
                    // 作者 long：逐项等待正式 Repository，已有记录与正文完整时保持原样，正文缺失时只修复同一 ID。
                    val existing = entryPoint.database().profiles().getAll().firstOrNull { it.url == url }
                    when {
                        existing == null -> {
                            repository.addRemoteProfile(url)
                            added++
                        }
                        !repository.profileFile(existing.id).isFile || repository.profileFile(existing.id).length() == 0L -> {
                            repository.updateRemoteProfile(existing.id)
                            repaired++
                        }
                        else -> reused++
                    }
                } catch (failure: Exception) {
                    // 作者 long：验收输出保留 HTTP 状态或异常类型，不携带订阅地址、重定向地址或异常中的凭据。
                    val detail = failure.message.orEmpty().takeIf {
                        Regex("订阅服务器返回 HTTP \\d{3}").matches(it)
                    } ?: failure.javaClass.simpleName
                    failures += "授权配置 ${index + 1} 准备失败：$detail"
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("kclash_profile_preparation", "added=$added repaired=$repaired reused=$reused failures=${failures.size}")
        })
        assertTrue(failures.joinToString("；"), failures.isEmpty())
        composeRule.waitUntil(15_000) {
            urls.all { url -> repository.profiles.value.any { it.url == url } }
        }
        composeRule.onNodeWithTag("nav_proxy").assertIsDisplayed()
    }

    @Test
    fun mainActivityRendersHomeAndProfileEntry() {
        verifyPreservedProfileMetadata()
        composeRule.onNodeWithText("仪表盘").assertIsDisplayed()
        composeRule.onNodeWithText("状态").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("启动核心").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_stats_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_card").assertIsDisplayed()
        composeRule.onNodeWithTag("main_navigation_dock").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_profile_action").performScrollTo().performClick()
        // 作者 long：已有配置直接进入配置列表，空态会同时打开添加弹窗，两条分支都必须可继续添加。
        if (composeRule.onAllNodesWithText("创建自定义配置").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("创建自定义配置").assertIsDisplayed()
            composeRule.onNodeWithText("取消").performClick()
        }
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dashboard").performClick()
        // 作者 long：系统授权和验收诊断不属于 Flutter 默认首页，普通启动不应混入这些入口。
        composeRule.onNodeWithTag("always_on_vpn_card").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_battery_card").assertDoesNotExist()
        composeRule.onNodeWithTag("access_control_card").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_probe_card").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("profiles_add_fab").assertIsDisplayed()
    }

    @Test
    fun mainActivityProxyPageUsesOneToolbarAndKeepsStoppedProfile() {
        val entry = EntryPointAccessors.fromApplication(composeRule.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        val options = com.lonnnnnng.flclash.ui.ProxyPageOptionsStore.load(composeRule.activity)
        composeRule.waitUntil(15_000) { repository.currentProfileId.value != null }
        val currentId = repository.currentProfileId.value
        val file = repository.profileFile(checkNotNull(currentId))
        val metadata = file.length() to file.lastModified()
        try {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag("nav_proxy").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("nav_proxy").performClick()
            composeRule.onNodeWithTag("proxy_page_toolbar").assertIsDisplayed()
            composeRule.onNodeWithTag("main_navigation_dock").assertIsDisplayed()
            assertEquals(1, composeRule.onAllNodesWithTag("proxy_page_toolbar").fetchSemanticsNodes().size)
            for (layout in listOf("LIST", "TAB")) {
                composeRule.onNodeWithTag("proxy_tools").performClick()
                composeRule.onNodeWithTag("proxy_tools_display_mode").performClick()
                composeRule.onNodeWithTag("proxy_display_mode_" + layout).performClick()
                composeRule.onNodeWithTag(if (layout == "LIST") "proxy_groups_list" else "proxy_groups_pager").assertIsDisplayed()
            }
            composeRule.onNodeWithTag("nav_dashboard").performClick()
            composeRule.onNodeWithContentDescription("启动核心").assertIsDisplayed()
            assertEquals(currentId, repository.currentProfileId.value)
            assertEquals(metadata, file.length() to file.lastModified())
        } finally {
            com.lonnnnnng.flclash.ui.ProxyPageOptionsStore.save(composeRule.activity, options)
        }
    }

    @Test
    fun mainActivityPreviewsCurrentConfigWithoutStartingOrWritingProfile() {
        val entry = EntryPointAccessors.fromApplication(composeRule.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        composeRule.waitUntil(10_000) { repository.currentProfileId.value != null }
        val id = checkNotNull(repository.currentProfileId.value)
        val file = repository.profileFile(id)
        val before = file.length() to file.lastModified()
        composeRule.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("dashboard_profile_detail_preview").performClick()
        lateinit var preview: com.lonnnnnng.flclash.ui.RuntimeConfigPreviewViewModel
        composeRule.activityRule.scenario.onActivity { preview = ViewModelProvider(it)[com.lonnnnnng.flclash.ui.RuntimeConfigPreviewViewModel::class.java] }
        composeRule.waitUntil(15_000) { !preview.state.value.isLoading }
        assertTrue("只读预览应加载成功", preview.state.value.preview != null)
        composeRule.onNodeWithTag("runtime_config_preview_content").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("runtime_config_preview_content").assertIsDisplayed()
        composeRule.onNodeWithTag("runtime_config_preview_close").performClick()
        composeRule.onNodeWithTag("runtime_config_preview_sheet").assertDoesNotExist()
        assertEquals(before, file.length() to file.lastModified())
        assertEquals(id, repository.currentProfileId.value)
    }

    @Test
    fun mainActivityNavigatesAcrossFlutterMobileDestinationsAndTools() {
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dashboard").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("状态").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.onNodeWithTag("page_tools").assertIsDisplayed()
        composeRule.onNodeWithTag("tools_item_connections").performClick()
        composeRule.onNodeWithTag("page_connections").assertIsDisplayed()
        composeRule.onNodeWithText("活动连接").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.onNodeWithTag("tools_item_requests").performClick()
        composeRule.onNodeWithTag("page_observations").assertIsDisplayed()
        composeRule.onNodeWithTag("page_requests").assertIsDisplayed()
        composeRule.onNodeWithText("DNS").performClick()
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()
        composeRule.onNodeWithText("DNS 查询").assertIsDisplayed()
        composeRule.onNodeWithText("日志").performClick()
        composeRule.onNodeWithTag("page_logs").assertIsDisplayed()
        composeRule.onNodeWithText("核心日志").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dashboard").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityRendersObservationEmptyStatesFromEachTab() {
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.onNodeWithTag("tools_item_requests").performClick()
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
        composeRule.onNodeWithTag("nav_dashboard").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityRetainsObservationTabAcrossPrimaryNavigationAndRecreation() {
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.onNodeWithTag("tools_item_requests").performClick()
        composeRule.onNodeWithText("DNS").performClick()
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()

        // 作者 long：代理 Tab 仅在存在可用配置/代理组时展示；此回归不依赖设备是否已有有效配置。
        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.onNodeWithTag("tools_item_dns").performClick()
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()

        // 作者 long：Activity 重建会重新创建页面组合，只有主页面的可保存导航状态才能恢复用户正在查看的页签。
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("page_dns").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("page_dns").assertIsDisplayed()
    }

    @Test
    fun mainActivityCreatesAndDiscardsUntouchedCustomDraft() {
        composeRule.onNodeWithTag("nav_profiles").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java).profileRepository()
        val before = repository.profiles.value.map { it.id }.toSet()
        composeRule.onNodeWithTag("profiles_add_fab").performClick()
        composeRule.onNodeWithTag("profile_add_source_custom").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("custom_profile_title_menu").assertIsDisplayed()
        // 作者 long：空草稿不先命名；按真实 ID 集合确认退出只删除新草稿，保留设备原有配置。
        composeRule.onNodeWithTag("custom_profile_back").performClick()
        composeRule.waitUntil(5_000) { repository.profiles.value.map { it.id }.toSet() == before }
        composeRule.onNodeWithTag("profiles_add_fab").assertIsDisplayed()
    }

    @Test
    fun mainActivityCompletesCustomProfileLifecycleThroughRealViewModels() {
        val label = "Activity E2E 配置"

        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        startCustomProfileEditor(label)
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }

        openProfileActions(label)
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“$label”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("暂无配置").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityPersistsCustomProfileRulesThroughRealViewModels() {
        val label = "Activity E2E 规则"
        val rule = "DOMAIN-SUFFIX,example.com,DIRECT"

        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        startCustomProfileEditor(label)
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }

        // 作者 long：规则编辑必须经过真实 ProfilesViewModel 和文件写回，再次打开才能验证不是只更新了对话框内存。
        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("规则").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("规则 YAML").performTextInput(rule)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · $label").fetchSemanticsNodes().isEmpty()
        }

        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("规则").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(rule).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        openProfileActions(label)
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

        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        startCustomProfileEditor(label)
        // 作者 long：先写入最小基础 YAML，确保代理分区回写时要保留同一份 Profile 的其他内容。
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }

        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("代理").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("代理 YAML").performTextInput(proxy)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · $label").fetchSemanticsNodes().isEmpty()
        }

        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("代理").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("代理 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(proxy).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        openProfileActions(label)
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
        val entryPoint = EntryPointAccessors.fromApplication(composeRule.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entryPoint.profileRepository()
        val before = runBlocking(Dispatchers.IO) { entryPoint.database().profiles().getAll().map { it.id }.toSet() }
        val selected = repository.currentProfileId.value
        var createdId: Long? = null
        try {
            composeRule.onNodeWithTag("nav_profiles").performClick()
            startCustomProfileEditor(label)
            createdId = runBlocking(Dispatchers.IO) { entryPoint.database().profiles().getAll().single { it.id !in before && it.label == label }.id }
            // 作者 long：通过真实可视表单创建策略组，避免继续向旧文本控件注入一份另造的验收 YAML。
            composeRule.onNodeWithTag("custom_profile_add_group").performScrollTo().performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("proxy_group_name").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag("proxy_group_name").performClick()
            composeRule.onNodeWithTag("group_value_input").performTextInput("AUTO-TEST")
            composeRule.onNodeWithTag("group_value_confirm").performClick()
            composeRule.onNodeWithTag("proxy_group_members").performClick()
            composeRule.onNodeWithTag("group_members_add").performClick()
            composeRule.onNodeWithText("DIRECT").performClick()
            composeRule.onNodeWithTag("group_member_picker_done").performClick()
            composeRule.onNodeWithTag("proxy_group_members_confirm").performClick()
            composeRule.onNodeWithTag("proxy_group_form_confirm").performClick()
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("proxy_group_list_save").fetchSemanticsNodes().isEmpty() }
            val content = runBlocking(Dispatchers.IO) { repository.readLocalProfileContent(checkNotNull(createdId)) }
            val group = com.lonnnnnng.flclash.data.ProxyGroupDrafts.read(content).single()
            assertEquals("AUTO-TEST", group.name)
            assertEquals(listOf("DIRECT"), group.strings("proxies"))
            composeRule.onNodeWithTag("custom_profile_group_0").performScrollTo().performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("proxy_group_name").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag("proxy_group_name").assertTextContains("AUTO-TEST", substring = true)
            composeRule.onNodeWithTag("proxy_group_form_back").performClick()
            composeRule.onNodeWithTag("custom_profile_manage_groups").performScrollTo().performClick()
            composeRule.onNodeWithTag("proxy_groups_yaml").performClick()
            composeRule.onNodeWithTag("proxy_groups_yaml_content").assertTextContains("AUTO-TEST", substring = true)
            composeRule.onNodeWithTag("proxy_groups_yaml_back").performClick()
            composeRule.onNodeWithTag("proxy_group_list_back").performClick()
            composeRule.onNodeWithTag("custom_profile_back").performClick()
            composeRule.onNodeWithTag("nav_dashboard").performClick()
        } finally {
            // 作者 long：只删除本方法从真实添加入口创建且核对过 ID/名称的临时配置，保留正式 Profile 和原选择。
            runBlocking(Dispatchers.IO) {
                createdId?.let { id ->
                    val row = entryPoint.database().profiles().getById(id)
                    if (row != null) { check(id !in before && row.label == label); repository.deleteProfile(id) }
                }
                repository.selectProfile(selected)
            }
        }
    }

    @Test
    fun mainActivityPersistsCustomProfileProvidersThroughRealViewModels() {
        val label = "Activity E2E Provider"
        val provider = "test:\n  type: file\n  path: ./providers/test.yaml"

        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        startCustomProfileEditor(label)
        // 作者 long：先写入最小基础 YAML，确保 Provider 分区回写时要保留同一份 Profile 的其他内容。
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithText("YAML").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }

        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithTag("custom_profile_resources").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Provider · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("proxy-providers").performTextInput(provider)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Provider · $label").fetchSemanticsNodes().isEmpty()
        }

        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithTag("custom_profile_resources").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Provider · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(provider).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        openProfileActions(label)
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

        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        startCustomProfileEditor(label)
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_editor_content").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }
        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("DNS").performScrollTo().performClick()
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

        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("DNS").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("DNS 覆盖 · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile-dns-override-list")
            .performScrollToNode(hasText("Nameserver"))
        composeRule.onNodeWithText(nameserver).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        openProfileActions(label)
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

        composeRule.onNodeWithTag("nav_profiles").performClick()
        composeRule.onNodeWithTag("page_profiles").assertIsDisplayed()
        startCustomProfileEditor(label)
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_editor_content").performTextInput("mixed-port: 7890\nmode: rule")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }
        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithTag("custom_profile_more").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_profile_dialers").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Dialers · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("代理名 -> 目标名").performTextInput(dialer)
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Dialers · $label").fetchSemanticsNodes().isEmpty()
        }

        openProfileActions(label)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithTag("custom_profile_more").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_profile_dialers").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Dialers · $label").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(dialer).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        openProfileActions(label)
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

        openAccessPage()
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
        openAccessPage()
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

        openAccessPage()
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

        // 作者 long：关闭搜索后离开应用代理页，走真实未保存对话框并放弃修改，确保批量操作不会写入 Room。
        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        // 作者 long：批量选中再取消可能完全回到已保存值，退出保护必须先留下与初始开关不同的真实草稿。
        if (enabledSwitch.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == initialEnabled) {
            enabledSwitch.performClick()
        }
        assertTrue("放弃修改前必须存在未保存草稿", recordAccessState("batch_before_navigation").hasUnsavedChanges)
        composeRule.onNodeWithTag("nav_tools").performClick()
        recordAccessState("batch_after_navigation")
        waitForAccessExitDialog()
        composeRule.onNodeWithTag("access_control_discard_changes").performClick()
        composeRule.onNodeWithTag("page_tools").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dashboard").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
        openAccessPage()
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
        val store = accessSettingsStore()
        val original = runBlocking(Dispatchers.IO) { store.current() }
        try {
            openAccessPage()
            val enabledSwitch = composeRule.onNodeWithTag("access_control_enabled")
            if (enabledSwitch.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] != ToggleableState.On) {
                enabledSwitch.performClick()
            }

            composeRule.onNodeWithTag("access_control_search_toggle").performClick()
            composeRule.onNodeWithTag("access_control_search").performTextInput(candidatePackage)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("access_control_app_$candidatePackage").fetchSemanticsNodes().isNotEmpty()
            }
            val appNode = composeRule.onNodeWithTag("access_control_app_$candidatePackage")
            val wasSelected = appNode.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
            appNode.performClick()
            composeRule.onNodeWithTag("access_control_topbar_save").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("access_control_topbar_save").fetchSemanticsNodes().isEmpty()
            }

            // 作者 long：Activity 重建保留 ViewModel，正式 Room 的独立回读才能证明应用选择已经持久化。
            val saved = runBlocking(Dispatchers.IO) { store.current() }
            assertEquals(true, saved.enable)
            assertEquals(!wasSelected, candidatePackage in currentPackageList(saved))
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("page_access_control").assertIsDisplayed()
            openAccessPage()
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag("access_control_app_$candidatePackage").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                if (wasSelected) ToggleableState.Off else ToggleableState.On,
                composeRule.onNodeWithTag("access_control_app_$candidatePackage")
                    .fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
            )
        } finally {
            // 作者 long：保存已改变持久化 baseline，完整恢复原设置；放弃新草稿不能撤销此前的保存。
            runBlocking(Dispatchers.IO) { store.save(original) }
            composeRule.activityRule.scenario.onActivity { activity ->
                ViewModelProvider(activity)[AccessControlViewModel::class.java].reload()
            }
            assertEquals(original, runBlocking(Dispatchers.IO) { store.current() })
        }
    }

    @Test
    fun mainActivityReflowsAccessCardAfterRotation() {
        openAccessPage()
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
            openAccessPage()
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
        openAccessPage()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("access_control_search_toggle")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val enabledSwitch = composeRule.onNodeWithTag("access_control_enabled")
        val initialState = recordAccessState("before_toggle")
        enabledSwitch.performClick()
        composeRule.waitForIdle()
        val changedState = recordAccessState("after_toggle")
        assertEquals(!initialState.settings.enable, changedState.settings.enable)
        assertTrue("开关变更后必须保留未保存草稿", changedState.hasUnsavedChanges)

        composeRule.onNodeWithTag("nav_tools").performClick()
        recordAccessState("after_navigation")
        waitForAccessExitDialog()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("page_access_control").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForAccessExitDialog()
        composeRule.onNodeWithText("保存后才会应用新的白名单、黑名单和筛选设置。").assertIsDisplayed()

        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("按应用代理有未保存的修改").assertDoesNotExist()
        composeRule.onNodeWithTag("tools_subpage_back").performClick()
        waitForAccessExitDialog()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("page_access_control").assertIsDisplayed()
        enabledSwitch.performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun mainActivitySavesAccessChangesBeforeLeavingTools() {
        openAccessPage()
        val store = accessSettingsStore()
        val original = runBlocking(Dispatchers.IO) { store.current() }
        try {
            composeRule.onNodeWithTag("access_control_enabled").performClick()
            composeRule.onNodeWithTag("nav_tools").performClick()
            waitForAccessExitDialog()
            composeRule.onNodeWithTag("access_control_save_and_exit").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText("按应用代理有未保存的修改").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("page_access_control").assertDoesNotExist()
            composeRule.onNodeWithTag("page_tools").assertIsDisplayed()
            // 作者 long：直接从正式 Room 回读，证明退出在写入完成后发生，不能仅凭对话框关闭宣称保存成功。
            val saved = runBlocking(Dispatchers.IO) { store.current() }
            assertEquals(original.copy(enable = !original.enable), saved)
        } finally {
            // 作者 long：退出验收只改变本用例的全局设置，恢复完整 baseline，不通过放弃草稿撤销已经落库的修改。
            runBlocking(Dispatchers.IO) { store.save(original) }
            composeRule.activityRule.scenario.onActivity { activity ->
                ViewModelProvider(activity)[AccessControlViewModel::class.java].reload()
            }
        }
    }
    @Test
    fun mainActivityPlacesBatteryAuthorizationInOnDemandSettings() {
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.onNodeWithTag("tools_item_developer").assertDoesNotExist()
        composeRule.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_general"))
        composeRule.onNodeWithTag("tools_item_general").performClick()
        composeRule.onNodeWithTag("page_general").assertIsDisplayed()
        composeRule.onNodeWithTag("general_item_on_demand").performClick()
        composeRule.onNodeWithTag("page_on_demand").assertIsDisplayed()
        composeRule.onNodeWithText("前置条件").assertIsDisplayed()
        composeRule.onNodeWithText("忽略电池优化").assertIsDisplayed()
        composeRule.onNodeWithTag("on_demand_battery_authorize").assertIsDisplayed()
        composeRule.onNodeWithTag("always_on_vpn_card").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_probe_card").assertDoesNotExist()

        // 作者 long：返回必须沿按需连接的父页逐级退出，不能跳回首页或把系统授权留在仪表盘。
        composeRule.onNodeWithTag("tools_subpage_back").performClick()
        composeRule.onNodeWithTag("page_general").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("tools_item_general").assertExists()
        composeRule.onNodeWithTag("nav_dashboard").performClick()
        composeRule.onNodeWithTag("page_dashboard").assertIsDisplayed()
    }

    @Test
    fun mainActivityKeepsExtendedDiagnosticsBehindExplicitDebugEntry() {
        try {
            composeRule.activityRule.scenario.onActivity { activity ->
                // 作者 long：用真实启动 Intent 重建同一宿主，避免另一个 Activity 实例让 Scenario 停在 PAUSED。
                activity.intent.putExtra(MainActivity.EXTRA_DEVELOPER_TOOLS, true)
            }
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("nav_tools").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("tools_item_developer").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_developer"))
            composeRule.onNodeWithTag("tools_item_developer").performClick()
            composeRule.onNodeWithTag("always_on_vpn_card").assertIsDisplayed()
            composeRule.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("dashboard_probe_card"))
            composeRule.onNodeWithTag("dashboard_probe_card").assertIsDisplayed()
            composeRule.onNodeWithText("测试当前路由").assertIsNotEnabled()
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("dashboard_probe_card"))
            composeRule.onNodeWithTag("dashboard_probe_card").assertIsDisplayed()
        } finally {
            // 作者 long：撤销显式 Debug 入口后恢复普通 Tools 清单，测试不调用系统授权、订阅或核心诊断。
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.intent.removeExtra(MainActivity.EXTRA_DEVELOPER_TOOLS)
            }
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("nav_tools").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("tools_item_developer").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("tools_item_developer").assertDoesNotExist()
            composeRule.onNodeWithTag("nav_dashboard").performClick()
        }
    }
}

// 作者 long：持久化验收使用正式数据库的独立读取器，避免 Activity 重建保留 ViewModel 时只比较同一份内存状态。
private fun MainActivityInstrumentedTest.accessSettingsStore(): RoomAccessControlSettingsStore {
    val entryPoint = EntryPointAccessors.fromApplication(
        composeRule.activity.application,
        ConnectionAcceptanceEntryPoint::class.java,
    )
    return RoomAccessControlSettingsStore(entryPoint.database().appPreferences())
}

// 作者 long：诊断退出保护时读取正式宿主的同一个 ViewModel，区分草稿没有变化与导航绕过确认，不输出配置内容。
private fun MainActivityInstrumentedTest.recordAccessState(phase: String): AccessControlUiState {
    var snapshot: AccessControlUiState? = null
    composeRule.activityRule.scenario.onActivity { activity ->
        snapshot = ViewModelProvider(activity)[AccessControlViewModel::class.java].state.value
    }
    val state = checkNotNull(snapshot)
    val accessVisible = composeRule.onAllNodesWithTag("page_access_control").fetchSemanticsNodes().isNotEmpty()
    val toolsVisible = composeRule.onAllNodesWithTag("page_tools").fetchSemanticsNodes().isNotEmpty()
    InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
        putString(
            "kclash_access_state",
            "phase=$phase enabled=${state.settings.enable} persisted=${state.persistedSettings.enable} " +
                "dirty=${state.hasUnsavedChanges} loading=${state.isLoading} access=$accessVisible tools=$toolsVisible",
        )
    })
    return state
}

// 作者 long：按应用代理从 Flutter Tools 的设置分组进入，所有真实宿主用例共用该正式路径。
private fun MainActivityInstrumentedTest.openAccessPage() {
    composeRule.onNodeWithTag("nav_tools").performClick()
    composeRule.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_access"))
    composeRule.onNodeWithTag("tools_item_access").performClick()
    composeRule.waitUntil(15_000) {
        // 作者 long：设置及应用列表加载时控件已出现但仍禁用，须等正式数据加载完毕才能修改出真实草稿。
        runCatching {
            composeRule.onNodeWithTag("access_control_enabled").assertIsEnabled()
            true
        }.getOrDefault(false)
    }
    composeRule.onNodeWithTag("page_access_control").assertIsDisplayed()
}

// 作者 long：确认框使用独立窗口，等待实际可见后操作，避免把语义节点创建时序当作窗口已完成布局。
private fun MainActivityInstrumentedTest.waitForAccessExitDialog() {
    try {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    } catch (failure: Throwable) {
        // 作者 long：确认框失败时保留窗口实际画面与标题边界，区分布局问题和宿主焦点问题，不输出配置或订阅。
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val nodes = composeRule.onAllNodesWithText("按应用代理有未保存的修改").fetchSemanticsNodes()
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("kclash_access_dialog_bounds", nodes.joinToString { it.boundsInRoot.toString() })
        })
        if (InstrumentationRegistry.getArguments().getString("kclashCapturePng") == "true") {
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                val png = java.io.ByteArrayOutputStream().also {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }.toByteArray()
                com.lonnnnnng.flclash.ui.recordVisualPng("access_exit_dialog_timeout", png)
            }
        }
        throw failure
    }
}

// 作者 long：通过正式 Room 记录和 Repository 文件路径核验，空库或缺失正文不能被记为配置已保留。
private fun MainActivityInstrumentedTest.verifyPreservedProfileMetadata() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val arguments = InstrumentationRegistry.getArguments()
    val phase = arguments.getString("kclashProfileSnapshot")
    if (phase !in listOf("inspect", "before", "after")) return
    val expectedCount = arguments.getString("kclashExpectedProfileCount")?.toIntOrNull() ?: 1
    require(expectedCount > 0) { "配置保留核验不能接受空库" }
    val context = composeRule.activity.application
    val entryPoint = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
    val rows = runBlocking(Dispatchers.IO) { entryPoint.database().profiles().getAll() }.sortedBy { it.id }
    // 作者 long：只比较 Profile 文件名、大小和修改时间，证明 UI 验收未改配置，不读取订阅地址或 YAML 正文。
    val files = rows.map { entryPoint.profileRepository().profileFile(it.id) }
    val missing = files.count { !it.isFile || it.length() == 0L }
    instrumentation.sendStatus(0, android.os.Bundle().apply {
        putString("kclash_preserved_profiles", "phase=$phase count=${rows.size} missing=$missing expected=$expectedCount")
    })
    if (phase == "inspect") return
    // 作者 long：当前只保留一个授权来源，仍需校验配置及正文存在，禁止把空库当作验收成功。
    assertTrue("真机 UI 验收至少保留 $expectedCount 份配置", rows.size >= expectedCount)
    assertEquals("每份配置都必须存在非空正文文件", 0, missing)
    val metadata = files.joinToString("\n") { "${it.name}\t${it.length()}\t${it.lastModified()}" }
    val snapshot = context.cacheDir.resolve("dashboard-ownership-profile-metadata.txt")
    if (phase == "before") {
        snapshot.writeText(metadata)
    } else {
        check(snapshot.isFile) { "缺少 UI 验收前的 Profile 文件元数据快照" }
        assertEquals(snapshot.readText(), metadata)
    }
}

// 作者 long：所有配置 E2E 从同一来源选择进入创建后编辑，避免继续依赖旧版先回列表再手动编辑的路径。
private fun MainActivityInstrumentedTest.startCustomProfileEditor(label: String) {
    composeRule.onNodeWithTag("profiles_add_fab").performClick()
    composeRule.onNodeWithTag("profile_add_source_custom").performClick()
    composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("custom_profile_title_menu").performClick()
    composeRule.onNodeWithTag("custom_profile_rename").performClick()
    composeRule.onNodeWithTag("profile_rename_label").performTextClearance()
    composeRule.onNodeWithTag("profile_rename_label").performTextInput(label)
    composeRule.onNodeWithTag("profile_rename_save").performClick()
    composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun MainActivityInstrumentedTest.openProfileActions(label: String) {
    // 作者 long：分区保存返回概览；更多页先退一层，随后退出已有配置，才能操作列表长按菜单。
    if (composeRule.onAllNodesWithTag("custom_profile_more_back").fetchSemanticsNodes().isNotEmpty()) {
        composeRule.onNodeWithTag("custom_profile_more_back").performClick()
    }
    if (composeRule.onAllNodesWithTag("custom_profile_back").fetchSemanticsNodes().isNotEmpty()) {
        composeRule.onNodeWithTag("custom_profile_back").performClick()
    }
    composeRule.onNodeWithText(label).assertIsDisplayed()
    composeRule.onNodeWithText(label).performTouchInput { longClick() }
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
