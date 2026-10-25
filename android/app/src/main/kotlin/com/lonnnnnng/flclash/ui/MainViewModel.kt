package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val runtimeManager: CoreRuntimeManager,
) : ViewModel() {
    val runtimeState = runtimeManager.state
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val errors = mutableErrors.asSharedFlow()

    fun startRuntime() {
        viewModelScope.launch {
            runtimeManager.startProxy().onFailure { error ->
                mutableErrors.emit(error.message ?: "核心启动失败")
            }
        }
    }

    fun stopRuntime() {
        viewModelScope.launch {
            runtimeManager.stop().onFailure { error ->
                mutableErrors.emit(error.message ?: "核心停止失败")
            }
        }
    }

    fun reportError(message: String) {
        mutableErrors.tryEmit(message)
    }

    fun toggleRuntime() {
        viewModelScope.launch {
            val result = if (runtimeManager.state.value.isRunning) {
                runtimeManager.stop()
            } else {
                runtimeManager.startProxy()
            }
            result.onFailure { error ->
                mutableErrors.emit(error.message ?: "核心操作失败")
            }
        }
    }
}
