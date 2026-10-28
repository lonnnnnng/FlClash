package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：按应用代理的 Compose 宿主用固定应用清单验证白名单/黑名单和搜索选择，
 * 不依赖设备当前安装包，也不启动 VPN；真实包名读取和 Room 保存由后续真机流程覆盖。
 */
@RunWith(AndroidJUnit4::class)
class AccessControlPanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchAndSelectPackageUpdatesBlacklist() {
        val apps = listOf(
            InstalledAppInfo("com.example.mail", "示例邮件"),
            InstalledAppInfo("com.example.video", "示例视频"),
        )
        var state by mutableStateOf(
            AccessControlUiState(
                settings = AccessControlProps(
                    enable = true,
                    mode = AccessControlMode.REJECT_SELECTED,
                    acceptList = emptyList(),
                    rejectList = emptyList(),
                ),
                apps = apps,
                isLoading = false,
            ),
        )
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = state,
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    onEnabledChange = { state = state.copy(settings = state.settings.copy(enable = it)) },
                    onModeChange = { state = state.copy(settings = state.settings.copy(mode = it)) },
                    onSortChange = { state = state.copy(settings = state.settings.copy(sort = it)) },
                    onFilterSystemAppsChange = { state = state.copy(settings = state.settings.copy(isFilterSystemApp = it)) },
                    onFilterNonInternetAppsChange = { state = state.copy(settings = state.settings.copy(isFilterNonInternetApp = it)) },
                    onSearchChange = { state = state.copy(searchQuery = it) },
                    onTogglePackage = { packageName ->
                        val selected = currentPackageList(state.settings).toMutableSet()
                        if (!selected.add(packageName)) selected.remove(packageName)
                        state = state.copy(settings = state.settings.withCurrentPackageList(selected))
                    },
                    onSelectVisible = {},
                    onClearVisible = {},
                    onSmartSelect = {},
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_search").performTextInput("视频")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("示例视频").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_app_com.example.video").performClick()

        assertEquals(listOf("com.example.video"), state.settings.rejectList)
        composeRule.onNodeWithTag("access_control_smart_select").assertIsEnabled()
    }

    @Test
    fun runningCoreLocksAccessControlEdits() {
        val profile = AccessControlUiState(
            settings = AccessControlProps(
                enable = false,
                mode = AccessControlMode.REJECT_SELECTED,
                acceptList = emptyList(),
                rejectList = emptyList(),
            ),
            apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
        )
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = profile,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    onEnabledChange = {},
                    onModeChange = {},
                    onSortChange = {},
                    onFilterSystemAppsChange = {},
                    onFilterNonInternetAppsChange = {},
                    onSearchChange = {},
                    onTogglePackage = {},
                    onSelectVisible = {},
                    onClearVisible = {},
                    onSmartSelect = {},
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_enabled").assertIsDisplayed()
        composeRule.onNodeWithText("核心运行中，按应用代理设置已锁定；停止核心后才能修改。").assertIsDisplayed()
    }

    @Test
    fun clipboardActionsInvokeCallbacks() {
        val apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件"))
        var exported = 0
        var imported = 0
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = AccessControlUiState(
                        settings = AccessControlProps(
                            enable = true,
                            mode = AccessControlMode.REJECT_SELECTED,
                            rejectList = listOf("com.example.mail"),
                        ),
                        apps = apps,
                        isLoading = false,
                    ),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    onEnabledChange = {},
                    onModeChange = {},
                    onSortChange = {},
                    onFilterSystemAppsChange = {},
                    onFilterNonInternetAppsChange = {},
                    onSearchChange = {},
                    onTogglePackage = {},
                    onSelectVisible = {},
                    onClearVisible = {},
                    onSmartSelect = {},
                    onExportToClipboard = { exported++ },
                    onImportFromClipboard = { imported++ },
                    onSave = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_export").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("access_control_clipboard_import").performClick()

        assertEquals(1, exported)
        assertEquals(1, imported)
    }

    @Test
    fun disabledAccessControlMasksEditingAndOneTapEnables() {
        val apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件"))
        var state by mutableStateOf(
            AccessControlUiState(
                settings = AccessControlProps(
                    enable = false,
                    mode = AccessControlMode.REJECT_SELECTED,
                    acceptList = emptyList(),
                    rejectList = emptyList(),
                ),
                apps = apps,
                isLoading = false,
            ),
        )
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = state,
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    onEnabledChange = { state = state.copy(settings = state.settings.copy(enable = it)) },
                    onModeChange = { state = state.copy(settings = state.settings.copy(mode = it)) },
                    onSortChange = { state = state.copy(settings = state.settings.copy(sort = it)) },
                    onFilterSystemAppsChange = { state = state.copy(settings = state.settings.copy(isFilterSystemApp = it)) },
                    onFilterNonInternetAppsChange = { state = state.copy(settings = state.settings.copy(isFilterNonInternetApp = it)) },
                    onSearchChange = { state = state.copy(searchQuery = it) },
                    onTogglePackage = {},
                    onSelectVisible = {},
                    onClearVisible = {},
                    onSmartSelect = {},
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("应用访问控制已关闭").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_mode_allow").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_sort_name").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_filter_system").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_filter_no_network").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_search").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_smart_select").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_actions").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_app_com.example.mail").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_save").assertIsNotEnabled()

        composeRule.onNodeWithTag("access_control_turn_on").performClick()

        assertEquals(true, state.settings.enable)
        composeRule.onNodeWithTag("access_control_turn_on").assertDoesNotExist()
        composeRule.onNodeWithText("选中应用将会被排除在 VPN 之外").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_mode_allow").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_sort_name").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_filter_system").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_filter_no_network").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_search").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_smart_select").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_app_com.example.mail").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_save").assertIsEnabled()
    }
}
