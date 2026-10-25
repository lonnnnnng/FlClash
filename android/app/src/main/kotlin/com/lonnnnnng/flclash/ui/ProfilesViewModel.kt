package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileRepository
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeManager
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

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val runtimeManager: CoreRuntimeManager,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableEditor = MutableStateFlow(ProfileEditorUiState())

    val state: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        repository.currentProfileId,
        busy,
    ) { profiles, currentId, isBusy ->
        ProfilesUiState(profiles, currentId, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    val errors = mutableErrors.asSharedFlow()
    val editorState: StateFlow<ProfileEditorUiState> = mutableEditor

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

    fun delete(profile: Profile) = launchAction { repository.deleteProfile(profile.id) }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { action() }
                .onFailure { error -> mutableErrors.emit(error.message ?: "操作失败") }
            busy.value = false
        }
    }
}
