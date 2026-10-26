package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                    onSaveEditor = viewModel::saveEditor,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("添加配置").performClick()
        composeRule.onNodeWithText("创建自定义配置").performClick()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("未命名配置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("未命名配置").assertIsDisplayed()
        composeRule.onNodeWithText("自定义配置").assertIsDisplayed()

        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 未命名配置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("编辑 未命名配置").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("编辑 未命名配置").fetchSemanticsNodes().isEmpty()
        }
        assertEquals("mixed-port: 7890", store.updatedContents.single())

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确定删除“未命名配置”吗？本地配置文件也会一并删除。").assertIsDisplayed()
        composeRule.onAllNodesWithText("删除").assertCountEquals(2)
        composeRule.onAllNodesWithText("删除")[1].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("还没有配置").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptyList<Profile>(), store.profiles.value)
        assertEquals(null, store.currentProfileId.value)
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

    override val profiles: StateFlow<List<Profile>> = mutableProfiles.asStateFlow()
    override val currentProfileId: StateFlow<Long?> = mutableCurrentProfileId.asStateFlow()

    override suspend fun selectProfile(id: Long?) {
        mutableCurrentProfileId.value = id?.takeIf { candidate -> mutableProfiles.value.any { it.id == candidate } }
    }

    override suspend fun addRemoteProfile(url: String, label: String): Profile =
        error("该页面流程不访问远程订阅")

    override suspend fun addFileProfile(label: String, bytes: ByteArray): Profile =
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

    override suspend fun updateLocalProfileContent(id: Long, content: String): Profile {
        val current = mutableProfiles.value.single { it.id == id }
        contents[id] = content
        updatedContents += content
        val updated = current.copy(lastUpdateAt = System.currentTimeMillis())
        mutableProfiles.value = listOf(updated)
        return updated
    }

    override suspend fun updateRemoteProfile(id: Long): Profile =
        error("该页面流程不更新远程订阅")

    override suspend fun deleteProfile(id: Long) {
        mutableProfiles.value = mutableProfiles.value.filterNot { it.id == id }
        if (mutableCurrentProfileId.value == id) mutableCurrentProfileId.value = null
    }

    override suspend fun reorder(ids: List<Long>) = Unit
}
