package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.data.ScriptStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScriptsUiState(
    val scripts: List<Script> = emptyList(),
    val isBusy: Boolean = false,
    val updatingIds: Set<Long> = emptySet(),
    val completed: Long = 0,
    val error: String? = null,
)

data class ScriptEditorUiState(
    val isOpen: Boolean = false,
    val script: Script? = null,
    val label: String = "",
    val content: String = SCRIPT_TEMPLATE,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val session: Long = 0,
    val error: String? = null,
    val loadFailed: Boolean = false,
    val originalLabel: String = label,
    val originalContent: String = content,
)

data class ScriptRemoteUiState(
    val isOpen: Boolean = false,
    val script: Script? = null,
    val label: String = "",
    val url: String = "",
)

private const val SCRIPT_MUTATION_BLOCKED_MESSAGE = "核心运行中，请先停止核心后再修改脚本"
internal const val SCRIPT_TEMPLATE = "const main = (config) => {\n  return config;\n};"

@HiltViewModel
class ScriptsViewModel @Inject constructor(
    private val repository: ScriptStore,
    private val runtimeManager: CoreRuntimeStatusProvider,
) : ViewModel() {
    private val operations = MutableStateFlow(ScriptsUiState())
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableEditor = MutableStateFlow(ScriptEditorUiState())
    private val mutableRemote = MutableStateFlow(ScriptRemoteUiState())
    private var generation = 0L
    private var pendingPick: Long? = null

    val state: StateFlow<ScriptsUiState> = combine(repository.scripts, operations) { scripts, operation ->
        operation.copy(scripts = scripts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScriptsUiState())
    val errors = mutableErrors.asSharedFlow()
    val editorState: StateFlow<ScriptEditorUiState> = mutableEditor.asStateFlow()
    val remoteState: StateFlow<ScriptRemoteUiState> = mutableRemote.asStateFlow()

    fun openRemote(script: Script? = null) {
        if (operations.value.isBusy || isBlocked()) return
        operations.value = operations.value.copy(error = null)
        // 作者 long：表单与原脚本版本一起保留在ViewModel，Activity重建不丢地址，也不依赖尚未回读的列表猜测导入类型。
        mutableRemote.value = ScriptRemoteUiState(isOpen = true, script = script, url = script?.url.orEmpty())
    }

    fun editRemoteDraft(label: String, url: String) {
        if (!operations.value.isBusy) mutableRemote.value = mutableRemote.value.copy(label = label, url = url)
    }

    fun closeRemote() { if (!operations.value.isBusy) mutableRemote.value = ScriptRemoteUiState() }

    fun submitRemote(label: String, url: String) {
        val form = mutableRemote.value
        if (!form.isOpen) return
        editRemoteDraft(label, url)
        launchAction(form.script?.id, onSuccess = { mutableRemote.value = ScriptRemoteUiState() }) {
            form.script?.let { repository.updateRemote(it, url) } ?: repository.createRemote(label, url)
        }
    }

    fun openNewEditor() {
        if (isBlocked() || operations.value.isBusy || mutableEditor.value.isSaving) return
        mutableEditor.value = ScriptEditorUiState(isOpen = true, session = ++generation)
    }

    fun openEditor(script: Script) {
        if (isBlocked() || operations.value.isBusy || mutableEditor.value.isSaving) return
        val token = ++generation
        mutableEditor.value = ScriptEditorUiState(isOpen = true, script = script, label = script.label, isLoading = true, session = token)
        viewModelScope.launch {
            runCatching { repository.readContent(script.id) }.onSuccess { content ->
                // 作者 long：关闭或改开另一脚本后，旧读取结果不能重新弹出编辑页或覆盖新草稿。
                if (generation == token) mutableEditor.value = mutableEditor.value.copy(content = content, originalContent = content, isLoading = false)
            }.onFailure { error ->
                if (generation == token) mutableEditor.value = mutableEditor.value.copy(isLoading = false, loadFailed = true, error = "无法读取脚本，请重试")
                report(error, "无法读取脚本")
            }
        }
    }

    fun editDraft(label: String, content: String) {
        val old = mutableEditor.value
        if (old.isOpen && !old.isSaving && !old.isLoading) mutableEditor.value = old.copy(label = label, content = content, error = null)
    }

    fun closeEditor() {
        if (mutableEditor.value.isSaving) return
        generation++
        mutableEditor.value = ScriptEditorUiState()
    }

    fun save(label: String, content: String) {
        val editor = mutableEditor.value
        if (!editor.isOpen || editor.isLoading || editor.loadFailed || editor.isSaving || operations.value.isBusy || isBlocked()) return
        // 作者 long：同步认领保存，连续点击和退出不能触发同一草稿两次写入；失败保留当前文本。
        mutableEditor.value = editor.copy(label = label, content = content, isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                editor.script?.let { repository.updateIfUnchanged(it, label, content) } ?: repository.create(label, content)
            }.onSuccess {
                mutableEditor.value = ScriptEditorUiState()
                generation++
            }.onFailure { error ->
                mutableEditor.value = mutableEditor.value.copy(isSaving = false, error = safeMessage(error, "保存脚本失败，请重试"))
                report(error, "保存脚本失败")
            }
        }
    }

    fun importRemote(label: String, url: String) = launchAction { repository.createRemote(label, url) }
    fun updateRemote(script: Script, url: String) = launchAction(script.id) { repository.updateRemote(script, url) }
    fun sync(script: Script) = launchAction(script.id) { repository.sync(script.id) }
    fun delete(script: Script) = launchAction(script.id) { repository.delete(script.id) }
    fun importFile(fileName: String, bytes: ByteArray) = launchAction { repository.importFile(fileName, bytes) }

    fun beginFilePick(): Long? {
        if (operations.value.isBusy || mutableEditor.value.isSaving || isBlocked()) return null
        val token = ++generation
        pendingPick = token
        operations.value = operations.value.copy(isBusy = true, error = null)
        return token
    }

    fun completeFilePick(token: Long?, load: (suspend () -> Pair<String, ByteArray>)?) {
        if (token == null || pendingPick != token) return
        pendingPick = null
        operations.value = operations.value.copy(isBusy = false)
        if (load == null) return
        launchAction { val (name, bytes) = load(); repository.importFile(name, bytes) }
    }

    fun reorder(ids: List<Long>) {
        if (ids.isEmpty()) return
        launchAction { repository.reorder(ids) }
    }

    private fun isBlocked(): Boolean {
        if (!runtimeManager.state.value.isRunning) return false
        mutableErrors.tryEmit(SCRIPT_MUTATION_BLOCKED_MESSAGE)
        return true
    }

    private fun safeMessage(error: Throwable, fallback: String): String =
        error.message?.takeIf { !it.contains("://") && it.length < 160 } ?: fallback

    private fun report(error: Throwable, fallback: String) { mutableErrors.tryEmit(safeMessage(error, fallback)) }

    private fun launchAction(id: Long? = null, onSuccess: () -> Unit = {}, action: suspend () -> Any?) {
        if (operations.value.isBusy || mutableEditor.value.isSaving || isBlocked()) return
        operations.value = operations.value.copy(isBusy = true, updatingIds = id?.let(::setOf).orEmpty(), error = null)
        viewModelScope.launch {
            try {
                action()
                operations.value = operations.value.copy(completed = operations.value.completed + 1)
                onSuccess()
            } catch (error: Exception) {
                operations.value = operations.value.copy(error = safeMessage(error, "脚本操作失败，请重试"))
                report(error, "脚本操作失败")
            } finally {
                operations.value = operations.value.copy(isBusy = false, updatingIds = emptySet())
            }
        }
    }
}
