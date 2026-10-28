package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/**
 * 作者 long：Profiles 页面截图需要排除 Room、相对时间和文件系统差异，Debug 宿主只注入固定 Profile 状态，
 * 正式页面仍由 Hilt ViewModel 提供真实创建、编辑、排序和删除流程。
 */
class ProfilesTestActivity : ComponentActivity() {
    private var scenario by mutableStateOf(ProfilesScenario.EMPTY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val profiles = when (scenario) {
                ProfilesScenario.EMPTY -> emptyList()
                ProfilesScenario.CUSTOM_SELECTED -> listOf(sampleProfile())
            }
            KClashTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfilesPanel(
                        state = ProfilesUiState(
                            profiles = profiles,
                            currentProfileId = profiles.firstOrNull()?.id,
                        ),
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
                    )
                }
            }
        }
    }

    fun showCustomSelectedScenario() {
        scenario = ProfilesScenario.CUSTOM_SELECTED
    }

    private fun sampleProfile() = Profile.custom(601L, "家庭配置").copy(
        // 作者 long：截图必须避免相对时间随设备时钟变化，因此固定为无更新时间的稳定状态。
        lastUpdateAt = null,
    )
}

private enum class ProfilesScenario {
    EMPTY,
    CUSTOM_SELECTED,
}
