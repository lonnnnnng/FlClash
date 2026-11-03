package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.FilterPreset
import com.lonnnnnng.flclash.data.FilterPresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilterPresetsUiState(
    val rows: List<FilterPreset> = emptyList(), val loading: Boolean = true,
    val busy: Boolean = false, val editing: FilterPreset? = null, val creating: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FilterPresetsViewModel @Inject constructor(private val repository: FilterPresetRepository) : ViewModel() {
    private val mutable = MutableStateFlow(FilterPresetsUiState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { repository.changes.collect { mutable.value = mutable.value.copy(rows = it, loading = false) } } }
    fun edit(row: FilterPreset?) { if (!mutable.value.busy) mutable.value = mutable.value.copy(editing = row, creating = row == null, error = null) }
    fun closeEditor() { if (!mutable.value.busy) mutable.value = mutable.value.copy(editing = null, creating = false, error = null) }
    fun save(label: String, regex: String) = perform {
        repository.save(mutable.value.editing, label, regex)
        mutable.value = mutable.value.copy(editing = null, creating = false)
    }
    fun delete(rows: List<FilterPreset>) = perform { repository.delete(rows) }
    fun reorder(labels: List<String>) = perform { repository.reorder(labels) }
    private fun perform(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = mutable.value.copy(error = error.message ?: "保存失败，请重试") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
