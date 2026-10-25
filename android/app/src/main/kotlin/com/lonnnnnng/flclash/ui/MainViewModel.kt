package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val runtimeManager: CoreRuntimeManager,
) : ViewModel() {
    val runtimeState = runtimeManager.state

    fun toggleRuntime() {
        viewModelScope.launch {
            if (runtimeManager.state.value.isRunning) {
                runtimeManager.stop()
            } else {
                runtimeManager.startProxy()
            }
        }
    }
}
