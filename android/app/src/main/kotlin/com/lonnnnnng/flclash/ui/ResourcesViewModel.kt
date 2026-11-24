package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.ResourceUpdateRow
import com.lonnnnnng.flclash.data.ResourceUpdatesRepository
import com.lonnnnnng.flclash.service.GeoResourceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** 作者 long：工具资源页只管理核心资源更新和Geo偏好；图标目录已删除，不再注入导入、恢复或同步链路。 */
@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val updates: ResourceUpdatesRepository,
) : ViewModel() {
    val state = updates.state

    fun refresh() { viewModelScope.launch { updates.refresh() } }
    fun settings(value: GeoResourceSettings) { viewModelScope.launch { updates.saveSettings(value) } }
    fun sync(row: ResourceUpdateRow) { viewModelScope.launch { updates.updateRows(listOf(row)) } }
    fun syncAll() { viewModelScope.launch { updates.updateRows(state.value.rows) } }
    fun clearFeedback() = updates.clearFeedback()
}
