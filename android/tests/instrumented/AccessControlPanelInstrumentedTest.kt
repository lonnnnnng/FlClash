package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

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
        composeRule.onNodeWithTag("access_control_search").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_search").performTextInput("视频")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("示例视频").assertIsDisplayed()
        composeRule.onNodeWithTag("access_control_app_com.example.video").performClick()

        assertEquals(listOf("com.example.video"), state.settings.rejectList)
        composeRule.onNodeWithTag("access_control_smart_select").assertIsEnabled()
        composeRule.onNodeWithTag("access_control_search_toggle").performClick()
        composeRule.onNodeWithTag("access_control_search").assertDoesNotExist()
        assertEquals("", state.searchQuery)
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
    fun dirtyAccessControlShowsTopBarSaveAndMenuActions() {
        var saved = 0
        var exported = 0
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = AccessControlUiState(
                        settings = AccessControlProps(
                            enable = true,
                            mode = AccessControlMode.REJECT_SELECTED,
                            rejectList = listOf("com.example.mail"),
                        ),
                        persistedSettings = AccessControlProps(
                            enable = true,
                            mode = AccessControlMode.REJECT_SELECTED,
                            rejectList = emptyList(),
                        ),
                        apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
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
                    onImportFromClipboard = {},
                    onSave = { saved++ },
                )
            }
        }

        composeRule.onNodeWithTag("access_control_topbar_save").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("access_control_topbar_actions").performClick()
        composeRule.onNodeWithTag("access_control_menu_actions").performClick()
        composeRule.onNodeWithTag("access_control_clipboard_export").performClick()

        assertEquals(1, saved)
        assertEquals(1, exported)
    }

    @Test
    fun topBarMenuMatchesFlutterAccessControlHierarchy() {
        var enabled = true
        var mode = AccessControlMode.REJECT_SELECTED
        var sort = com.lonnnnnng.flclash.common.AccessSortType.NONE
        var filterSystemApps = true
        var filterNonInternetApps = true
        var smartSelected = 0
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = AccessControlUiState(
                        settings = AccessControlProps(
                            enable = enabled,
                            mode = mode,
                            sort = sort,
                            isFilterSystemApp = filterSystemApps,
                            isFilterNonInternetApp = filterNonInternetApps,
                        ),
                        apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
                        isLoading = false,
                    ),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    onEnabledChange = { enabled = it },
                    onModeChange = { mode = it },
                    onSortChange = { sort = it },
                    onFilterSystemAppsChange = { filterSystemApps = it },
                    onFilterNonInternetAppsChange = { filterNonInternetApps = it },
                    onSearchChange = {},
                    onTogglePackage = {},
                    onSelectVisible = {},
                    onClearVisible = {},
                    onToggleVisible = {},
                    onSmartSelect = { smartSelected++ },
                    onExportToClipboard = {},
                    onImportFromClipboard = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("access_control_topbar_actions").performClick()
        composeRule.onNodeWithTag("access_control_menu_settings").performClick()
        composeRule.onNodeWithTag("access_control_menu_mode_allow").performClick()
        assertEquals(AccessControlMode.ACCEPT_SELECTED, mode)

        composeRule.onNodeWithTag("access_control_topbar_actions").performClick()
        composeRule.onNodeWithTag("access_control_menu_settings").performClick()
        composeRule.onNodeWithTag("access_control_menu_sort_name").performClick()
        assertEquals(com.lonnnnnng.flclash.common.AccessSortType.NAME, sort)

        composeRule.onNodeWithTag("access_control_topbar_actions").performClick()
        composeRule.onNodeWithTag("access_control_menu_settings").performClick()
        composeRule.onNodeWithTag("access_control_menu_system_apps").performClick()
        assertEquals(false, filterSystemApps)

        composeRule.onNodeWithTag("access_control_topbar_actions").performClick()
        composeRule.onNodeWithTag("access_control_menu_actions").performClick()
        composeRule.onNodeWithTag("access_control_menu_smart_select").performClick()
        assertEquals(1, smartSelected)

        composeRule.onNodeWithTag("access_control_topbar_actions").performClick()
        composeRule.onNodeWithTag("access_control_menu_disable").performClick()
        assertEquals(false, enabled)
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

    @Test
    fun largeFontScaleKeepsAccessControlActionsReachable() {
        val apps = listOf(InstalledAppInfo("com.example.mail", "一个较长的示例邮件应用名称"))
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                // 作者 long：用 1.5 倍字体模拟系统大字号，验证操作入口能换行且不依赖横向挤压。
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
            ) {
                KClashTheme {
                    AccessControlCard(
                        state = AccessControlUiState(
                            settings = AccessControlProps(
                                enable = true,
                                mode = AccessControlMode.REJECT_SELECTED,
                                rejectList = emptyList(),
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
                        onExportToClipboard = {},
                        onImportFromClipboard = {},
                        onSave = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("access_control_card").assertExists()
        composeRule.onNodeWithTag("access_control_search_toggle").assertExists().performClick()
        composeRule.onNodeWithTag("access_control_search").assertExists()
        composeRule.onNodeWithTag("access_control_actions").assertExists()
        composeRule.onNodeWithTag("access_control_select_visible").assertExists()
        composeRule.onNodeWithTag("access_control_clear_visible").assertExists()
        composeRule.onNodeWithTag("access_control_save").assertExists()
        composeRule.onNodeWithTag("access_control_app_com.example.mail").assertExists()
    }

    @Test
    fun extraLargeFontScaleKeepsAccessControlActionsReachable() {
        val apps = listOf(InstalledAppInfo("com.example.mail", "一个较长的示例邮件应用名称"))
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                // 作者 long：2.0 倍字体覆盖系统“超大”档位，避免只验证常规大字号而遗漏按钮换行后的可达性。
                LocalDensity provides Density(baseDensity.density, fontScale = 2.0f),
            ) {
                KClashTheme {
                    AccessControlCard(
                        state = AccessControlUiState(
                            settings = AccessControlProps(
                                enable = true,
                                mode = AccessControlMode.REJECT_SELECTED,
                                rejectList = emptyList(),
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
                        onExportToClipboard = {},
                        onImportFromClipboard = {},
                        onSave = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("access_control_card").assertExists()
        composeRule.onNodeWithTag("access_control_search_toggle").assertExists().performClick()
        composeRule.onNodeWithTag("access_control_search").assertExists()
        composeRule.onNodeWithTag("access_control_topbar_actions").assertExists()
        composeRule.onNodeWithTag("access_control_select_visible").assertExists()
        composeRule.onNodeWithTag("access_control_clear_visible").assertExists()
        composeRule.onNodeWithTag("access_control_save").assertExists()
        composeRule.onNodeWithTag("access_control_app_com.example.mail").assertExists()
    }

    @Test
    fun defaultAccessControlCardMatchesScreenshotBaseline() {
        composeRule.setContent {
            KClashTheme {
                AccessControlCard(
                    state = AccessControlUiState(
                        settings = AccessControlProps(
                            enable = true,
                            mode = AccessControlMode.REJECT_SELECTED,
                            rejectList = emptyList(),
                        ),
                        apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
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

        composeRule.waitForIdle()
        val bitmap = composeRule
            .onNodeWithTag("access_control_card")
            .captureToImage()
            .asAndroidBitmap()
        val png = ByteArrayOutputStream().also { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(png)
            .joinToString("") { byte -> "%02x".format(byte) }

        // 作者 long：基线哈希锁定默认 Access 卡片的像素输出，布局或主题变化必须显式更新视觉证据。
        assertEquals("74d0dc07b35597f6e6f19905ae4047108290ae8bb6c8a5275c7976237e8236e3", digest)
    }

    @Test
    fun accessControlCardStateScreenshotMatrixMatchesBaseline() {
        var screenshotState by mutableStateOf("closed")
        composeRule.setContent {
            val settings = when (screenshotState) {
                "closed" -> AccessControlProps(
                    enable = false,
                    mode = AccessControlMode.REJECT_SELECTED,
                    rejectList = emptyList(),
                )
                "dirty" -> AccessControlProps(
                    enable = true,
                    mode = AccessControlMode.REJECT_SELECTED,
                    rejectList = listOf("com.example.mail"),
                )
                else -> error("unknown screenshot state: $screenshotState")
            }
            val persisted = when (screenshotState) {
                "closed" -> settings
                "dirty" -> settings.copy(rejectList = emptyList())
                else -> settings
            }
            KClashTheme {
                AccessControlCard(
                    state = AccessControlUiState(
                        settings = settings,
                        persistedSettings = persisted,
                        apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
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

        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("access_control_card")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            "closed" to "697332c4f518d575e67b342a1535b1a74be9ff6fffdbb0b99a072fce859ae90c",
            "dirty" to "303caf72a6bfb85694a64981e0246f324a14f0637fb063b424a4f29f6aa88193",
        )
        val actual = expected.keys.associateWith { state ->
            screenshotState = state
            captureHash()
        }
        val mismatches = expected.mapNotNull { (state, expectedHash) ->
            val actualHash = actual.getValue(state)
            if (actualHash == expectedHash) null else "$state expected=$expectedHash actual=$actualHash"
        }
        if (mismatches.isNotEmpty()) {
            error("state screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }

    @Test
    fun accessControlCardFontScaleScreenshotMatrixMatchesBaseline() {
        var fontScale by mutableStateOf(1.3f)
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                // 作者 long：在同一内容树内切换字体档位，避免重复 setContent 破坏 Compose 测试宿主。
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
            ) {
                KClashTheme {
                    AccessControlCard(
                        state = AccessControlUiState(
                            settings = AccessControlProps(
                                enable = true,
                                mode = AccessControlMode.REJECT_SELECTED,
                                rejectList = emptyList(),
                            ),
                            apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
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
        }

        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("access_control_card")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            1.3f to "7e230a41dc0e03e5220a47c5df55a90d8668fed02fbd0359e55a308849b2fc71",
            1.5f to "8f13a4b9e236f2ea6997f4828486d06d532766f4dfcd751cf791866adf85b0c8",
            1.8f to "3e18033ee22e687cf7cf39a2f945f4fb7bb97c9fbb54745fe1590a574cfa15d7",
            2.0f to "e4ab4b4d3136c0dd017f4e47030a1b8aef8448c71e8df92953373329a5a3b0f5",
        )
        val actualHashes = mutableMapOf<Float, String>()
        expected.keys.forEachIndexed { index, scale ->
            if (index > 0) fontScale = scale
            actualHashes[scale] = captureHash()
        }
        val mismatches = buildList {
            expected.forEach { (scale, expectedHash) ->
                val actualHash = actualHashes.getValue(scale)
                if (actualHash != expectedHash) {
                    add("fontScale=$scale expected=$expectedHash actual=$actualHash")
                }
            }
        }
        if (mismatches.isNotEmpty()) {
            error("font screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }
}
