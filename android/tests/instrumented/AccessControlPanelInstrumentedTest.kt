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
import androidx.compose.ui.test.onNodeWithContentDescription
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
                    onToggleVisible = {},
                    onSmartSelect = {},
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithContentDescription("关闭搜索").assertIsDisplayed()
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
                    onToggleVisible = {},
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
                    onToggleVisible = {},
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
                    onToggleVisible = {},
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
        composeRule.onNodeWithTag("access_control_search_toggle").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_smart_select").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_actions").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_app_com.example.mail").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_save").assertIsNotEnabled()

        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithTag("access_control_search").assertIsEnabled()

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

    @Test
    fun topBarToggleSelectsAndClearsVisibleApps() {
        val apps = listOf(
            InstalledAppInfo("com.example.mail", "示例邮件"),
            InstalledAppInfo("com.example.video", "示例视频"),
        )
        var state by mutableStateOf(
            AccessControlUiState(
                settings = AccessControlProps(
                    enable = true,
                    mode = AccessControlMode.REJECT_SELECTED,
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
                    onEnabledChange = {},
                    onModeChange = {},
                    onSortChange = {},
                    onFilterSystemAppsChange = {},
                    onFilterNonInternetAppsChange = {},
                    onSearchChange = { state = state.copy(searchQuery = it) },
                    onTogglePackage = {},
                    onSelectVisible = {},
                    onClearVisible = {},
                    onToggleVisible = {
                        val visible = state.visibleApps.map { it.packageName }.toSet()
                        val selected = currentPackageList(state.settings).toMutableSet()
                        if (selected.containsAll(visible)) selected.removeAll(visible) else selected.addAll(visible)
                        state = state.copy(settings = state.settings.withCurrentPackageList(selected))
                    },
                    onSmartSelect = {},
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_toggle_visible").performClick()
        composeRule.onNodeWithContentDescription("取消全选当前结果").assertIsDisplayed()
        assertEquals(setOf("com.example.mail", "com.example.video"), currentPackageList(state.settings).toSet())
        composeRule.onNodeWithTag("access_control_toggle_visible").performClick()
        composeRule.onNodeWithContentDescription("全选当前结果").assertIsDisplayed()
        assertEquals(emptySet<String>(), currentPackageList(state.settings).toSet())
    }

    @Test
    fun emptyInstalledAppListShowsActionableStatusAndDisablesBatchSelection() {
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = AccessControlUiState(
                        settings = AccessControlProps(
                            enable = true,
                            mode = AccessControlMode.REJECT_SELECTED,
                            rejectList = emptyList(),
                        ),
                        apps = emptyList(),
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
                    onToggleVisible = {},
                    onSmartSelect = {},
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("没有可用于按应用代理的已安装应用").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_select_visible").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_clear_visible").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_smart_select").assertIsNotEnabled()
        composeRule.onNodeWithTag("access_control_app_list").assertDoesNotExist()
    }

    @Test
    fun unsavedChangesDialogOffersCancelDiscardAndSave() {
        val persisted = AccessControlProps(
            enable = true,
            mode = AccessControlMode.REJECT_SELECTED,
            rejectList = emptyList(),
        )
        var state by mutableStateOf(
            AccessControlUiState(
                settings = persisted.copy(rejectList = listOf("com.example.mail")),
                persistedSettings = persisted,
                apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
                isLoading = false,
            ),
        )
        var exitCount = 0
        composeRule.setContent {
            KClashTheme {
                AccessControlUnsavedChangesDialog(
                    visible = state.hasUnsavedChanges,
                    isSaving = false,
                    onCancel = {},
                    onDiscard = {
                        state = state.copy(settings = state.persistedSettings)
                        exitCount++
                    },
                    onSaveAndExit = {
                        state = state.copy(persistedSettings = state.settings)
                        exitCount++
                    },
                )
            }
        }

        composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("按应用代理有未保存的修改").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_discard_changes").performClick()
        assertEquals(1, exitCount)
        composeRule.onNodeWithText("按应用代理有未保存的修改").assertDoesNotExist()
    }
}
