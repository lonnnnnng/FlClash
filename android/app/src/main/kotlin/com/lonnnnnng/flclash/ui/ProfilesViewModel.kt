package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileRepository
import com.lonnnnnng.flclash.data.ProfileType
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

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val state: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        repository.currentProfileId,
        busy,
    ) { profiles, currentId, isBusy ->
        ProfilesUiState(profiles, currentId, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    val errors = mutableErrors.asSharedFlow()

    fun select(id: Long) = launchAction { repository.selectProfile(id) }

    fun addRemote(url: String, label: String) = launchAction {
        repository.addRemoteProfile(url, label)
    }

    fun addCustom(label: String) = launchAction {
        repository.addCustomProfile(label)
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
