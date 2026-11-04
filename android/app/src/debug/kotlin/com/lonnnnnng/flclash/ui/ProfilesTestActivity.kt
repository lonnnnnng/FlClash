package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.SubscriptionInfo
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/**
 * 作者 long：Profiles 页面截图需要排除 Room、相对时间和文件系统差异，Debug 宿主只注入固定 Profile 状态，
 * 正式页面仍由 Hilt ViewModel 提供真实创建、编辑、排序和删除流程。
 */
class ProfilesTestActivity : ComponentActivity() {
    private var scenario by mutableStateOf(ProfilesScenario.EMPTY)
    private var toolbarAction by mutableStateOf<ProfilesToolbarAction?>(null)
    private var darkTheme by mutableStateOf(true)
    private var showAdd by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val profiles = when (scenario) {
                ProfilesScenario.EMPTY -> emptyList()
                ProfilesScenario.CUSTOM_SELECTED -> listOf(sampleProfile())
                ProfilesScenario.REMOTE_USAGE -> listOf(sampleRemoteProfile())
            }
            KClashTheme(darkTheme = darkTheme) {
                MainPageScaffold(
                    selectedDestination = MainDestination.PROFILES,
                    onDestinationSelected = {},
                    modifier = Modifier.testTag("profiles_test_screen"),
                    topBarActions = {
                        ProfilesTopBarActions(
                            profiles = profiles,
                            busy = false,
                            canMutate = true,
                            onAdd = { showAdd = true },
                            onAction = { toolbarAction = it },
                        )
                    },
                ) { insets ->
                    Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) {
                        ProfilesPanel(
                            modifier = Modifier.fillMaxSize(),
                            state = ProfilesUiState(
                                profiles = profiles,
                                currentProfileId = profiles.firstOrNull()?.id,
                            ),
                            toolbarAction = toolbarAction,
                            onToolbarActionHandled = { toolbarAction = null },
                            showInlineToolbar = false,
                            editorState = ProfileEditorUiState(),
                            runtimeState = CoreRuntimeStatus.STOPPED,
                            showAddDialog = showAdd,
                            onShowAddDialog = { showAdd = true },
                            onDismissAddDialog = { showAdd = false },
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
            }
        }
    }

    fun showCustomSelectedScenario() {
        scenario = ProfilesScenario.CUSTOM_SELECTED
    }

    fun showRemoteUsageScenario() {
        scenario = ProfilesScenario.REMOTE_USAGE
    }

    fun showTheme(dark: Boolean) { darkTheme = dark }

    private fun sampleProfile() = Profile.custom(601L, "家庭配置").copy(
        // 作者 long：截图必须避免相对时间随设备时钟变化，因此固定为无更新时间的稳定状态。
        lastUpdateAt = null,
    )

    private fun sampleRemoteProfile() = Profile.remote(602L, "https://example.test/subscription", "机场订阅").copy(
        lastUpdateAt = null,
        subscriptionInfo = SubscriptionInfo(
            upload = 1_073_741_824L,
            download = 2_147_483_648L,
            total = 10_737_418_240L,
            expire = 1_793_318_400L,
        ),
    )
}

private enum class ProfilesScenario {
    EMPTY,
    CUSTOM_SELECTED,
    REMOTE_USAGE,
}
