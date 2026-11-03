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

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val updates: ResourceUpdatesRepository,
    private val icons: IconSetRepository,
    private val reader: ProfileFileDocumentReader,
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
    fun refresh() { viewModelScope.launch { updates.refresh() } }
    fun settings(value: GeoResourceSettings) { viewModelScope.launch { updates.saveSettings(value) } }
    fun sync(row: ResourceUpdateRow) { viewModelScope.launch { updates.updateRows(listOf(row)) } }
    fun syncAll() { viewModelScope.launch { updates.updateRows(state.value.rows) } }
    fun clearFeedback() = updates.clearFeedback()
    fun importIcons(name: String, url: String) = iconAction { icons.importUrl(url, name) }
    fun importIconsFile(uri: String) = iconAction {
        val document = reader.read(uri)
        icons.importContent(document.bytes.toString(Charsets.UTF_8), fallbackName = document.name.substringBeforeLast('.'))
    }
    fun renameIcons(id: String, name: String) = iconAction { icons.rename(id, name) }
    fun iconOptions(expected: ResourceIconSet, name: String, url: String) = iconAction { icons.options(expected, name, url) }
    fun deleteIcons(id: String) = iconAction { icons.delete(id) }
    fun moveIcons(id: String, offset: Int) = iconAction { icons.move(id, offset) }
    fun reorderIcons(ids: List<String>) = iconAction { icons.reorder(ids) }
    private fun iconAction(action: suspend () -> Unit) {
        if (mutableIconBusy.value) return
        mutableIconBusy.value = true; mutableIconError.value = null
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action(); updates.refresh() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableIconError.value = "操作失败，请检查图标集 JSON、名称或网络后重试" }
            finally { mutableIconBusy.value = false; mutableIconCompleted.value++ }
        }
    }
}
