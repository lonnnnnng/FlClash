package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.data.ScriptStore
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

data class ScriptsUiState(
    val scripts: List<Script> = emptyList(),
    val isBusy: Boolean = false,
)

data class ScriptEditorUiState(
    val isOpen: Boolean = false,
    val script: Script? = null,
    val label: String = "",
    val content: String = SCRIPT_TEMPLATE,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

private const val SCRIPT_MUTATION_BLOCKED_MESSAGE = "核心运行中，请先停止核心后再修改脚本"
internal const val SCRIPT_TEMPLATE = "const main = (config) => {\n  return config;\n};"

@HiltViewModel
class ScriptsViewModel @Inject constructor(
    private val repository: ScriptStore,
    private val runtimeManager: CoreRuntimeStatusProvider,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableEditor = MutableStateFlow(ScriptEditorUiState())

    val state: StateFlow<ScriptsUiState> = combine(repository.scripts, busy) { scripts, isBusy ->
        ScriptsUiState(scripts, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScriptsUiState())

    val errors = mutableErrors.asSharedFlow()
    val editorState: StateFlow<ScriptEditorUiState> = mutableEditor

    fun openNewEditor() {
        if (isBlocked()) return
        mutableEditor.value = ScriptEditorUiState(isOpen = true)
    }

    fun openEditor(script: Script) {
        if (isBlocked()) return
        viewModelScope.launch {
            mutableEditor.value = ScriptEditorUiState(
                isOpen = true,
                script = script,
                label = script.label,
                isLoading = true,
            )
            runCatching { repository.readContent(script.id) }
                .onSuccess { content ->
                    mutableEditor.value = ScriptEditorUiState(
                        isOpen = true,
                        script = script,
                        label = script.label,
                        content = content,
                    )
                }
                .onFailure { error ->
                    mutableEditor.value = ScriptEditorUiState()
                    mutableErrors.emit(error.message ?: "无法读取脚本")
                }
        }
    }

    fun closeEditor() {
        mutableEditor.value = ScriptEditorUiState()
    }

    fun save(label: String, content: String) {
        if (isBlocked()) return
        val editor = mutableEditor.value
        viewModelScope.launch {
            mutableEditor.value = editor.copy(
                label = label,
                content = content,
                isSaving = true,
            )
            runCatching {
                editor.script?.let {
                    repository.update(it.id, label, content)
                } ?: repository.create(label, content)
            }.onSuccess {
                closeEditor()
            }.onFailure { error ->
                mutableEditor.value = mutableEditor.value.copy(isSaving = false)
                mutableErrors.emit(error.message ?: "保存脚本失败")
            }
        }
    }

    fun importRemote(label: String, url: String) {
        if (isBlocked()) return
        launchAction { repository.createRemote(label, url) }
    }

    fun sync(script: Script) {
        if (isBlocked()) return
        launchAction { repository.sync(script.id) }
    }

    fun delete(script: Script) {
        if (isBlocked()) return
        launchAction { repository.delete(script.id) }
    }

    fun reorder(ids: List<Long>) {
        if (isBlocked()) return
        val currentIds = repository.scripts.value.map(Script::id)
        val requested = ids.filter { it in currentIds }.distinct()
        val orderedIds = requested + currentIds.filterNot { it in requested }
        if (orderedIds.isEmpty()) return
        launchAction { repository.reorder(orderedIds) }
    }

    private fun isBlocked(): Boolean {
        if (!runtimeManager.state.value.isRunning) return false
        mutableErrors.tryEmit(SCRIPT_MUTATION_BLOCKED_MESSAGE)
        return true
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { action() }
                .onFailure { error -> mutableErrors.emit(error.message ?: "脚本操作失败") }
            busy.value = false
        }
    }
}
