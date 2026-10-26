package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileRulesEditor
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val currentProfileId: Long? = null,
    val isBusy: Boolean = false,
)

data class ProfileEditorUiState(
    val profile: Profile? = null,
    val content: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

data class ProfileRulesEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val rules: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

internal const val PROFILE_MUTATION_BLOCKED_MESSAGE = "核心运行中，请先停止核心后再修改配置"

/**
 * 作者 long：核心运行期间配置文件已经被 Mihomo 打开，阻止会改变文件或当前选择的操作，
 * 避免删除/编辑后运行中的核心继续持有失效路径，导致页面状态与实际代理配置分裂。
 */
internal fun isProfileMutationBlocked(runtimeStatus: CoreRuntimeStatus): Boolean =
    runtimeStatus.isRunning

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileStore,
    private val runtimeManager: CoreRuntimeStatusProvider,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableEditor = MutableStateFlow(ProfileEditorUiState())
    private val mutableRulesEditor = MutableStateFlow(ProfileRulesEditorUiState())

    val state: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        repository.currentProfileId,
        busy,
    ) { profiles, currentId, isBusy ->
        ProfilesUiState(profiles, currentId, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    val errors = mutableErrors.asSharedFlow()
    val editorState: StateFlow<ProfileEditorUiState> = mutableEditor
    val rulesEditorState: StateFlow<ProfileRulesEditorUiState> = mutableRulesEditor

    fun select(id: Long) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再切换配置")
            return
        }
        launchAction { repository.selectProfile(id) }
    }

    fun addRemote(url: String, label: String) = launchAction {
        repository.addRemoteProfile(url, label)
    }

    fun addFile(label: String, bytes: ByteArray) = launchAction {
        repository.addFileProfile(label, bytes)
    }

    fun addCustom(label: String) = launchAction {
        repository.addCustomProfile(label)
    }

    fun reportError(message: String) {
        mutableErrors.tryEmit(message)
    }

    fun openEditor(profile: Profile) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑配置")
            return
        }
        viewModelScope.launch {
            mutableEditor.value = ProfileEditorUiState(profile = profile, isLoading = true)
            runCatching { repository.readLocalProfileContent(profile.id) }
                .onSuccess { content ->
                    mutableEditor.value = ProfileEditorUiState(profile = profile, content = content)
                }
                .onFailure { error ->
                    mutableEditor.value = ProfileEditorUiState()
                    mutableErrors.emit(error.message ?: "无法读取配置")
                }
        }
    }

    fun closeEditor() {
        mutableEditor.value = ProfileEditorUiState()
    }

    fun openRulesEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级规则编辑仅支持自定义配置")
            return
        }
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑规则")
            return
        }
        viewModelScope.launch {
            mutableRulesEditor.value = ProfileRulesEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                content to ProfileRulesEditor.extractRules(content)
            }.onSuccess { (content, rules) ->
                mutableRulesEditor.value = ProfileRulesEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    rules = rules,
                )
            }.onFailure { error ->
                mutableRulesEditor.value = ProfileRulesEditorUiState()
                mutableErrors.emit(error.message ?: "无法读取规则配置")
            }
        }
    }

    fun closeRulesEditor() {
        mutableRulesEditor.value = ProfileRulesEditorUiState()
    }

    fun saveRules(rules: String) {
        val state = mutableRulesEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存规则")
            return
        }
        viewModelScope.launch {
            mutableRulesEditor.value = state.copy(isSaving = true)
            runCatching {
                val updatedContent = ProfileRulesEditor.replaceRules(state.sourceContent, rules)
                repository.updateLocalProfileContent(profile.id, updatedContent)
            }.onSuccess {
                closeRulesEditor()
            }.onFailure { error ->
                mutableRulesEditor.value = mutableRulesEditor.value.copy(isSaving = false, rules = rules)
                mutableErrors.emit(error.message ?: "保存规则失败")
            }
        }
    }

    fun saveEditor(content: String) {
        val profile = mutableEditor.value.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存配置")
            return
        }
        viewModelScope.launch {
            mutableEditor.value = mutableEditor.value.copy(isSaving = true)
            runCatching { repository.updateLocalProfileContent(profile.id, content) }
                .onSuccess { closeEditor() }
                .onFailure { error ->
                    mutableEditor.value = mutableEditor.value.copy(isSaving = false)
                    mutableErrors.emit(error.message ?: "保存配置失败")
                }
        }
    }

    fun update(profile: Profile) {
        if (profile.type != ProfileType.URL) return
        launchAction { repository.updateRemoteProfile(profile.id) }
    }

    fun delete(profile: Profile) {
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        launchAction { repository.deleteProfile(profile.id) }
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { action() }
                .onFailure { error -> mutableErrors.emit(error.message ?: "操作失败") }
            busy.value = false
        }
    }
}
