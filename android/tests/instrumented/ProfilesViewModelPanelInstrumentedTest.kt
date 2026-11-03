package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：页面组合测试直接使用真实 ProfilesViewModel，确保状态流、编辑器异步读取和删除守卫
 * 在进入真实 Room、订阅网络和 VPN 生命周期前已经能够串成一条可回归的用户流程。
 */
@RunWith(AndroidJUnit4::class)
class ProfilesViewModelPanelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realViewModelDrivesCreateEditAndDeleteFlow() {
        val store = FakePanelProfileStore()
        val runtime = FakePanelRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val viewModel = ProfilesViewModel(store, runtime)
        var showAddDialog by mutableStateOf(false)

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            val editorState by viewModel.editorState.collectAsState()
            val runtimeState by runtime.state.collectAsState()
            KClashTheme {
                ProfilesPanel(
                    state = state,
                    editorState = editorState,
                    runtimeState = runtimeState,
                    showAddDialog = showAddDialog,
                    onShowAddDialog = { showAddDialog = true },
                    onDismissAddDialog = { showAddDialog = false },
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { label, url ->
                        showAddDialog = false
                        viewModel.addRemote(url, label)
                    },
                    onAddCustom = { label ->
                        showAddDialog = false
                        viewModel.addCustom(label)
                    },
                    onSelect = viewModel::select,
                    onEdit = viewModel::openEditor,
                    onUpdate = viewModel::update,
                    onCloseEditor = viewModel::closeEditor,
                    onDiscardEditor = viewModel::discardEditor,
                    onSaveEditor = viewModel::saveEditor,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("custom_profile_title_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_editor_content").performTextInput("allow-lan: true\n")
        composeRule.onNodeWithTag("custom_profile_back").performClick()
        composeRule.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        composeRule.onNodeWithText("继续编辑").performClick()
        composeRule.onNodeWithTag("custom_profile_title_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_profile_back").performClick()
        composeRule.onNodeWithText("放弃").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("暂无配置").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptyList<Profile>(), store.profiles.value)

        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("custom_profile_title_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("未命名配置").assertIsDisplayed()
        composeRule.onNodeWithText("自定义配置").assertIsDisplayed()

        assertEquals("mixed-port: 7890", store.updatedContents.single())

        composeRule.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“未命名配置”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        composeRule.onAllNodesWithText("删除").assertCountEquals(1)
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("暂无配置").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptyList<Profile>(), store.profiles.value)
        assertEquals(null, store.currentProfileId.value)
    }

    @Test
    fun structuredSectionSaveRefreshesNewProfileAndOffersRenameOnExit() {
        val store = FakePanelProfileStore()
        val runtime = FakePanelRuntimeStatusProvider(CoreRuntimeStatus.STOPPED)
        val viewModel = ProfilesViewModel(store, runtime)
        var showAddDialog by mutableStateOf(false)

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            val editorState by viewModel.editorState.collectAsState()
            val rulesEditorState by viewModel.rulesEditorState.collectAsState()
            val runtimeState by runtime.state.collectAsState()
            KClashTheme {
                ProfilesPanel(
                    state = state,
                    editorState = editorState,
                    rulesEditorState = rulesEditorState,
                    runtimeState = runtimeState,
                    showAddDialog = showAddDialog,
                    onShowAddDialog = { showAddDialog = true },
                    onDismissAddDialog = { showAddDialog = false },
                    onPickFile = {},
                    onPickQrCode = {},
                    onAddRemote = { _, _ -> },
                    onAddCustom = { label ->
                        showAddDialog = false
                        viewModel.addCustom(label)
                    },
                    onSelect = viewModel::select,
                    onEdit = viewModel::openEditor,
                    onUpdate = viewModel::update,
                    onCloseEditor = viewModel::closeEditor,
                    onDiscardEditor = viewModel::discardEditor,
                    onFinishNewDraftEditor = viewModel::finishNewDraftEditor,
                    onSaveEditor = viewModel::saveEditor,
                    onOpenRulesEditor = viewModel::openRulesEditor,
                    onCloseRulesEditor = viewModel::closeRulesEditor,
                    onSaveRules = viewModel::saveRules,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("规则").performClick()
        composeRule.onNodeWithText("规则 · 未命名配置").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_rules_menu").performClick()
        composeRule.onNodeWithTag("custom_rules_yaml").performClick()
        composeRule.onNodeWithTag("profile_rules_yaml").performTextClearance()
        composeRule.onNodeWithTag("profile_rules_yaml").performTextInput("MATCH,DIRECT")
        composeRule.onNodeWithTag("custom_rules_yaml_confirm").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("custom_rules_yaml_confirm").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithTag("custom_rules_back").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("规则 · 未命名配置").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("custom_profile_toggle_yaml").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_editor_content").assertTextContains("MATCH,DIRECT", substring = true)
        composeRule.onNodeWithTag("custom_profile_back").performClick()
        composeRule.onNodeWithText("为配置命名").assertIsDisplayed()
        composeRule.onNodeWithTag("new_profile_name").performTextClearance()
        composeRule.onNodeWithTag("new_profile_name").performTextInput("家庭网络")
        composeRule.onNodeWithText("保存名称").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("custom_profile_title_menu").fetchSemanticsNodes().isEmpty()
        }

        assertEquals(0, store.deleteCalls)
        assertEquals("家庭网络", store.profiles.value.single().label)
        assertTrue(store.updatedContents.single().contains("MATCH,DIRECT"))
    }
}

private class FakePanelRuntimeStatusProvider(
    status: CoreRuntimeStatus,
) : CoreRuntimeStatusProvider {
    private val mutableState = MutableStateFlow(status)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
}

private class FakePanelProfileStore : ProfileStore {
    private val mutableProfiles = MutableStateFlow<List<Profile>>(emptyList())
    private val mutableCurrentProfileId = MutableStateFlow<Long?>(null)
    private val contents = mutableMapOf<Long, String>()
    val updatedContents = mutableListOf<String>()
    var deleteCalls = 0

    override val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()
    override val currentProfileId: StateFlow<Long?> = mutableCurrentProfileId.asStateFlow()

    override suspend fun selectProfile(id: Long?) {
        mutableCurrentProfileId.value = id?.takeIf { candidate -> mutableProfiles.value.any { it.id == candidate } }
    }

    override suspend fun addRemoteProfile(url: String, label: String, ageSecretKey: String): Profile =
        error("该页面流程不访问远程订阅")

    override suspend fun addFileProfile(label: String, bytes: ByteArray, ageSecretKey: String): Profile =
        error("该页面流程不访问文件选择器")

    override suspend fun addCustomProfile(label: String): Profile {
        val profile = Profile.custom(201L, label.ifBlank { "未命名配置" })
        contents[profile.id] = "mixed-port: 7890"
        mutableProfiles.value = listOf(profile)
        mutableCurrentProfileId.value = profile.id
        return profile
    }

    override suspend fun readLocalProfileContent(id: Long): String =
        contents[id] ?: error("测试配置内容不存在")

    override suspend fun updateProfileLabel(id: Long, label: String): Profile {
        val updated = mutableProfiles.value.single { it.id == id }.copy(label = label)
        mutableProfiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile {
        val current = mutableProfiles.value.single { it.id == id }
        contents[id] = content
        updatedContents += content
        val updated = current.copy(lastUpdateAt = System.currentTimeMillis())
        mutableProfiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateProfileSelectedMap(id: Long, selectedMap: Map<String, String>): Profile =
        mutableProfiles.value.single { it.id == id }.copy(
            selectedMapJson = com.lonnnnnng.flclash.data.encodeSelectedMap(selectedMap),
        )

    override suspend fun updateRemoteProfile(id: Long): Profile =
        error("该页面流程不更新远程订阅")

    override suspend fun deleteProfile(id: Long) {
        deleteCalls++
        mutableProfiles.value = mutableProfiles.value.filterNot { it.id == id }
        if (mutableCurrentProfileId.value == id) mutableCurrentProfileId.value = null
    }

    override suspend fun reorder(ids: List<Long>) = Unit
}
