package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("保存").performClick()

        composeRule.onNodeWithText("未命名配置").assertIsDisplayed()
        composeRule.onNodeWithText("自定义配置").assertIsDisplayed()
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

        composeRule.onNodeWithText("排序").performClick()
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

        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("编辑 编辑配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals("mixed-port: 7890", savedContent)
        composeRule.onNodeWithText("编辑 编辑配置").assertDoesNotExist()
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

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“待删除配置”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("待删除配置").assertIsDisplayed()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onAllNodesWithText("删除").assertCountEquals(2)
        composeRule.onAllNodesWithText("删除")[1].performClick()

        composeRule.onNodeWithText("还没有配置").assertIsDisplayed()
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

        composeRule.onNodeWithText("编辑").assertIsNotEnabled()
        composeRule.onNodeWithText("排序").assertIsNotEnabled()
        composeRule.onNodeWithText("删除").assertIsNotEnabled()
    }
}
