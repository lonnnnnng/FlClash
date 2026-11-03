package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：页面宿主测试用可替换状态模拟 Store/ViewModel 回写，验证 Profiles 组合流程而不依赖网络、Room 或 VPN。
 */
@RunWith(AndroidJUnit4::class)
class ProfilesPanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun remoteToolbarSyncsEveryUrlProfileOnly() {
        val profiles = listOf(
            Profile.remote(120L, "https://example.test/one", "订阅一"),
            Profile.custom(121L, "自定义配置"),
            Profile.remote(122L, "https://example.test/two", "订阅二"),
        )
        val updatedIds = mutableListOf<Long>()
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles),
                    editorState = ProfileEditorUiState(),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = {},
                    onUpdate = { updatedIds += it.id },
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("profiles_sync_all").performClick()
        composeRule.waitForIdle()

        // 作者 long：批量同步只分发远程订阅，避免自定义配置进入订阅更新流程。
        assertEquals(listOf(120L, 122L), updatedIds)
    }

    @Test
    fun customCreationUpdatesPanelList() {
        var showAddDialog by mutableStateOf(false)
        var profiles by mutableStateOf(emptyList<Profile>())
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles),
                    editorState = ProfileEditorUiState(),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = showAddDialog,
                    onShowAddDialog = { showAddDialog = true },
                    onDismissAddDialog = { showAddDialog = false },
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = { label ->
                        profiles = listOf(Profile.custom(101L, label.ifBlank { "未命名配置" }))
                        showAddDialog = false
                    },
                    onSelect = {},
                    onEdit = {},
                    onUpdate = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("创建自定义配置").performClick()

        composeRule.onNodeWithText("未命名配置").assertIsDisplayed()
        composeRule.onNodeWithText("自定义配置").assertIsDisplayed()
    }

    @Test
    fun customEditorExposesStructuredSectionsBeforeRawYaml() {
        val profile = Profile.custom(102L, "结构化配置")
        var openedSection: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = listOf(profile)),
                    editorState = ProfileEditorUiState(profile = profile, isNew = true),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = {},
                    onUpdate = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = {},
                    onOpenRulesEditor = { openedSection = "Rules:${it.id}" },
                    onOpenProxiesEditor = { openedSection = "Proxies:${it.id}" },
                    onOpenProxyGroupsEditor = { openedSection = "Proxy Groups:${it.id}" },
                    onOpenProvidersEditor = { openedSection = "Providers:${it.id}" },
                    onOpenDnsEditor = { openedSection = "DNS:${it.id}" },
                    onOpenDialersEditor = { openedSection = "Dialers:${it.id}" },
                )
            }
        }

        composeRule.onNodeWithTag("custom_profile_title_menu").assertIsDisplayed()
        composeRule.onNodeWithText("规则").assertIsDisplayed()
        composeRule.onNodeWithText("策略组").assertIsDisplayed()
        composeRule.onNodeWithText("资源").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("规则").performScrollTo().performClick()
        assertEquals("Rules:${profile.id}", openedSection)
    }

    @Test
    fun remoteProfileAutoUpdateSwitchForwardsStateChange() {
        val profile = Profile.remote(109L, "https://example.test/subscription", "远程订阅")
        var changedTo: Boolean? = null
        composeRule.setContent { KClashTheme {
            RemoteProfileEditorDialog(profile, false, {}, { _, _, enabled, _ -> changedTo = enabled })
        } }
        composeRule.onNodeWithTag("remote_profile_editor_auto_update").performClick()
        composeRule.onNodeWithText("保存").performClick()
        assertEquals(false, changedTo)
    }

    @Test
    fun renameDialogWritesTheTrimmedLabelBackToThePanel() {
        val profile = Profile.custom(110L, "旧名称")
        var profiles by mutableStateOf(listOf(profile))
        var renamed: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles, currentProfileId = profile.id),
                    editorState = ProfileEditorUiState(),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = {},
                    onRename = { selected, label ->
                        renamed = label
                        profiles = listOf(selected.copy(label = label))
                    },
                    onUpdate = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        composeRule.onNodeWithTag("profile_rename_${profile.id}").performClick()
        composeRule.onNodeWithText("重命名配置").assertIsDisplayed()
        composeRule.onNodeWithText("名称").performTextClearance()
        composeRule.onNodeWithText("名称").performTextInput("  新名称  ")
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("新名称", renamed)
        composeRule.onNodeWithText("新名称").assertIsDisplayed()
    }

    @Test
    fun backupAndRestoreControlsExposeOptions() {
        var exportCount = 0
        var restoreCount = 0
        composeRule.setContent {
            KClashTheme {
                ProfileMaintenancePage(
                    canRestore = true, busy = false, credentials = null, onDismiss = {},
                    onExport = { exportCount++ }, onImport = { _, _ -> restoreCount++ },
                    onSaveCredentials = {}, onClearCredentials = {}, onProbe = {},
                    onUpload = { _, _ -> }, onDownload = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("backup_export").performClick()
        composeRule.onNodeWithTag("backup_restore").performClick()
        composeRule.onNodeWithText("恢复备份").assertIsDisplayed()
        composeRule.onNodeWithText("选择备份文件").performClick()
        assertEquals(1, exportCount)
        assertEquals(1, restoreCount)
    }

    @Test
    fun reorderDialogMovesProfileBeforeSaving() {
        val profiles = listOf(
            Profile.custom(105L, "第一项"),
            Profile.custom(106L, "第二项"),
            Profile.custom(107L, "第三项"),
        )
        var savedOrder: List<Long>? = null
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles, currentProfileId = profiles.first().id),
                    editorState = ProfileEditorUiState(),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = {},
                    onUpdate = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = {},
                    onReorderProfiles = { savedOrder = it },
                )
            }
        }

        composeRule.onNodeWithTag("profiles_sort").performClick()
        composeRule.onNodeWithText("排序配置").assertIsDisplayed()
        composeRule.onAllNodesWithText("下移")[0].performClick()
        composeRule.onNodeWithText("保存排序").performClick()

        assertEquals(listOf(106L, 105L, 107L), savedOrder)
    }

    @Test
    fun editSaveClosesEditorAndReturnsContentToHost() {
        val profile = Profile.custom(102L, "编辑配置")
        var editorState by mutableStateOf(ProfileEditorUiState())
        var savedContent by mutableStateOf<String?>(null)
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = listOf(profile), currentProfileId = profile.id),
                    editorState = editorState,
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = { selected ->
                        editorState = ProfileEditorUiState(
                            profile = selected,
                            content = "mixed-port: 7890",
                        )
                    },
                    onUpdate = {},
                    onCloseEditor = { editorState = ProfileEditorUiState() },
                    onSaveEditor = { content ->
                        savedContent = content
                        editorState = ProfileEditorUiState()
                    },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithTag("custom_profile_title_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("mixed-port: 7890", savedContent)
        composeRule.onNodeWithTag("custom_profile_title_menu").assertDoesNotExist()
    }

    @Test
    fun dialersEditorReturnsMappingToHostAndCanBeDismissedAfterSave() {
        val profile = Profile.custom(108L, "Dialers 配置")
        var editorState by mutableStateOf(
            ProfileDialersEditorUiState(
                profile = profile,
                dialers = "node-a -> Relay",
            ),
        )
        var savedDialers: String? = null
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = listOf(profile), currentProfileId = profile.id),
                    editorState = ProfileEditorUiState(),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = {},
                    onUpdate = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = {},
                    dialersEditorState = editorState,
                    onCloseDialersEditor = { editorState = ProfileDialersEditorUiState() },
                    onSaveDialers = {
                        savedDialers = it
                        editorState = ProfileDialersEditorUiState()
                    },
                )
            }
        }

        composeRule.onNodeWithTag("profile_dialers_quick").performClick()
        composeRule.onNodeWithText("Dialers · Dialers 配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("node-a -> Relay", savedDialers)
        composeRule.onNodeWithText("Dialers · Dialers 配置").assertDoesNotExist()
    }

    @Test
    fun deleteConfirmationSupportsCancelAndConfirmedRemoval() {
        val profile = Profile.custom(103L, "待删除配置")
        var profiles by mutableStateOf(listOf(profile))
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles, currentProfileId = profile.id),
                    editorState = ProfileEditorUiState(),
                    runtimeState = CoreRuntimeStatus.STOPPED,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = {},
                    onUpdate = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = { profiles = emptyList() },
                )
            }
        }

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“待删除配置”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("待删除配置").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onAllNodesWithText("删除").assertCountEquals(1)
        composeRule.onNodeWithText("删除").performClick()

        composeRule.onNodeWithText("暂无配置").assertIsDisplayed()
    }

    @Test
    fun runningPanelDisablesMutationEntry() {
        val profile = Profile.custom(104L, "运行中配置")
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = listOf(profile), currentProfileId = profile.id),
                    editorState = ProfileEditorUiState(),
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    showAddDialog = false,
                    onShowAddDialog = {},
                    onDismissAddDialog = {},
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = {},
                    onSelect = {},
                    onEdit = {},
                    onUpdate = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        composeRule.onNodeWithText("编辑").assertIsNotEnabled()
        composeRule.onNodeWithText("删除").assertIsNotEnabled()
        composeRule.onNodeWithTag("profiles_sort").assertIsEnabled()
    }
}
