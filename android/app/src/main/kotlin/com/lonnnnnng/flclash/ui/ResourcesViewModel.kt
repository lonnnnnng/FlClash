package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.GeoResourceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val updates: ResourceUpdatesRepository,
    private val icons: IconSetRepository,
    private val reader: ProfileFileDocumentReader,
    @javax.inject.Named("icon-file-picker") pickerStore: ProfileDraftStore = NoopProfileDraftStore,
) : ViewModel() {
    val iconUrlImports = ResourceUrlImportController(viewModelScope) { input ->
        icons.importUrlIfNew(input).also { updates.refresh() }
    }
    val state = updates.state
    private val mutableIconBusy = MutableStateFlow(false)
    val iconBusy = mutableIconBusy.asStateFlow()
    private val mutableIconError = MutableStateFlow<String?>(null)
    val iconError = mutableIconError.asStateFlow()
    private val mutableIconCompleted = MutableStateFlow(0L)
    val iconCompleted = mutableIconCompleted.asStateFlow()
    internal val filePicker = PersistentFilePicker(viewModelScope, pickerStore, "无法读取图标集，请检查JSON后重新选择")
    private var actionBusy = false
    init {
        viewModelScope.launch { filePicker.state.collect { mutableIconBusy.value = actionBusy || it.busy; if (it.error != null) mutableIconError.value = it.error } }
        viewModelScope.launch {
            filePicker.state.map { it.request?.let { r -> r.token to r.phase } }.distinctUntilChanged().collect {
                if (filePicker.state.value.request?.phase in setOf(FilePickerRequest.RESULT, FilePickerRequest.DONE)) completeFileImport()
            }
        }
    }
    private suspend fun completeFileImport() {
        val token = filePicker.state.value.request?.token ?: return
        val success = filePicker.complete(token, { file ->
            icons.importFileOnce(file.token, checkNotNull(file.bytes).toString(Charsets.UTF_8), file.name.orEmpty().substringBeforeLast('.'))
            EditorCommitProbe.checkpoint(EditorCommitWindow.ICON_FILE_APPLIED)
            updates.refresh()
        }, { icons.forgetFileImport(token) })
        if (success) { mutableIconError.value = null; mutableIconCompleted.value++ }
    }
    suspend fun beginIconsFilePick(): String? {
        if (actionBusy) return null
        if (filePicker.state.value.error != null && !discardFileRequest()) return null
        mutableIconError.value = null
        return filePicker.begin("icons")
    }
    fun receiveIconsFilePick(token: String?, uri: String?) = filePicker.receive(token, uri) { reader.readLimited(it, 2 * 1024 * 1024) }
    fun closeIcons(onClosed: () -> Unit) { if (!actionBusy) viewModelScope.launch { if (discardFileRequest()) onClosed() } }
    private suspend fun discardFileRequest(): Boolean {
        val request = filePicker.state.value.request
        if (request != null && (request.phase == FilePickerRequest.DONE || icons.fileImportCommitted(request.token))) {
            completeFileImport()
            return filePicker.state.value.request == null
        }
        return filePicker.cancel()
    }
    fun retryIconsImport() { filePicker.retryRecovery(); viewModelScope.launch { if (filePicker.awaitReady()) completeFileImport() } }
    fun refresh() { viewModelScope.launch { updates.refresh() } }
    fun settings(value: GeoResourceSettings) { viewModelScope.launch { updates.saveSettings(value) } }
    fun sync(row: ResourceUpdateRow) { viewModelScope.launch { updates.updateRows(listOf(row)) } }
    fun syncAll() { viewModelScope.launch { updates.updateRows(state.value.rows) } }
    fun clearFeedback() = updates.clearFeedback()
    fun importIcons(name: String, url: String) = iconAction { icons.importUrl(url, name) }
    fun importIconsFile(uri: String) { viewModelScope.launch { beginIconsFilePick()?.let { receiveIconsFilePick(it, uri) } } }
    fun renameIcons(id: String, name: String) = iconAction { icons.rename(id, name) }
    fun iconOptions(expected: ResourceIconSet, name: String, url: String) = iconAction { icons.options(expected, name, url) }
    fun deleteIcons(id: String) = iconAction { icons.delete(id) }
    fun moveIcons(id: String, offset: Int) = iconAction { icons.move(id, offset) }
    fun reorderIcons(ids: List<String>) = iconAction { icons.reorder(ids) }
    private fun iconAction(action: suspend () -> Unit) {
        if (mutableIconBusy.value) return
        actionBusy = true; mutableIconBusy.value = true; mutableIconError.value = null
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action(); updates.refresh() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableIconError.value = "操作失败，请检查图标集 JSON、名称或网络后重试" }
            finally { actionBusy = false; mutableIconBusy.value = filePicker.state.value.busy; mutableIconCompleted.value++ }
        }
    }
}
