package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.FilterPreset
import com.lonnnnnng.flclash.data.FilterPresets

val LocalFilterPresets = staticCompositionLocalOf<List<FilterPreset>> { emptyList() }

@Composable
internal fun FilterPresetChoices(value: String, onChange: (String) -> Unit) {
    val available = LocalFilterPresets.current.filterNot { FilterPresets.isApplied(it, value) }
    if (available.isNotEmpty()) Column {
        Text("添加过滤预设", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        available.forEach { preset ->
            TextButton(onClick = { onChange(FilterPresets.apply(preset, value)) }, modifier = Modifier.fillMaxWidth().testTag("filter_preset_choice_${preset.label}")) {
                Column(Modifier.weight(1f)) {
                    Text(preset.label, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(preset.regex, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.Add, null)
            }
        }
    }
}

@Composable
internal fun FilterPresetsPage(state: FilterPresetsUiState, onDismiss: () -> Unit,
    onEdit: (FilterPreset?) -> Unit, onCloseEditor: () -> Unit, onSave: (String, String) -> Unit,
    onDelete: (List<FilterPreset>) -> Unit, onReorder: (List<String>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(state.rows) { selected = selected.intersect(state.rows.map { it.label }.toSet()) }
    ProfileEditorPage(onDismissRequest = { if (!state.busy) onDismiss() }, title = { Text(if (selecting) "已选择 ${selected.size} 项" else "过滤预设") }, backTag = "filter_presets_back",
        confirmButton = {
            if (selecting) {
                IconButton(onClick = { selected = state.rows.filter { it.label.contains(query, true) || it.regex.contains(query, true) }.map { it.label }.toSet() }) { Icon(Icons.Outlined.SelectAll, "全选") }
                IconButton(onClick = { deleting = true }, enabled = selected.isNotEmpty() && !state.busy) { Icon(Icons.Outlined.Delete, "删除所选预设") }
            }
            IconButton(onClick = { selecting = !selecting; sorting = false; selected = emptySet() }, enabled = !state.busy, modifier = Modifier.testTag("filter_presets_select")) { Icon(Icons.Outlined.Checklist, if (selecting) "完成选择" else "批量选择") }
            IconButton(onClick = { sorting = !sorting; selecting = false; query = "" }, enabled = !state.busy, modifier = Modifier.testTag("filter_presets_sort")) { Icon(Icons.Outlined.SwapVert, "排序") }
            IconButton(onClick = { onEdit(null) }, enabled = !state.busy, modifier = Modifier.testTag("filter_presets_add")) { Icon(Icons.Outlined.Add, "添加预设") }
        }, text = {
            Column(Modifier.fillMaxSize().testTag("filter_presets_page")) {
                if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!state.creating && state.editing == null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(query, { query = it }, singleLine = true, enabled = !sorting, label = { Text("搜索名称或表达式") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清除搜索") } }, modifier = Modifier.fillMaxWidth().testTag("filter_presets_search"))
                if (sorting) Text("长按拖动调整顺序", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                val rows = state.rows.filter { it.label.contains(query, true) || it.regex.contains(query, true) }
                if (!state.loading && rows.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (query.isBlank()) "暂无过滤预设" else "没有匹配的结果", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (rows.isNotEmpty()) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    rows.forEach { row -> key(row.label) {
                        GroupOrderRow(state.rows.indexOf(row), state.rows.size, row.label, sorting, !state.busy, onMove = { target ->
                            onReorder(state.rows.toMutableList().apply { add(target, removeAt(indexOf(row))) }.map { it.label })
                        }) {
                            Row(Modifier.weight(1f).heightIn(min = 72.dp).clickable(enabled = !state.busy && !sorting) {
                                if (selecting) selected = if (row.label in selected) selected - row.label else selected + row.label else onEdit(row)
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selecting) Checkbox(row.label in selected, null)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(row.regex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (!selecting && !sorting) Icon(Icons.Outlined.ChevronRight, null)
                            }
                        }
                        HorizontalDivider()
                    } }
                }
            }
        })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除 ${selected.size} 个预设？") }, text = { Text("配置中已经使用的过滤表达式会保留。") },
        confirmButton = { TextButton(onClick = { onDelete(state.rows.filter { it.label in selected }); deleting = false; selected = emptySet() }, enabled = !state.busy) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
    if (state.creating || state.editing != null) {
        val original = state.editing
        var label by rememberSaveable(original) { mutableStateOf(original?.label.orEmpty()) }
        var regex by rememberSaveable(original) { mutableStateOf(original?.regex.orEmpty()) }
        var discard by remember { mutableStateOf(false) }
        val close = { if (!state.busy) { if (label != original?.label.orEmpty() || regex != original?.regex.orEmpty()) discard = true else onCloseEditor() } }
        ProfileEditorPage(onDismissRequest = close, title = { Text(if (original == null) "添加过滤预设" else "编辑过滤预设") }, backTag = "filter_preset_editor_back",
            confirmButton = { TextButton(onClick = { onSave(label, regex) }, enabled = !state.busy && label.isNotBlank() && regex.isNotBlank(), modifier = Modifier.testTag("filter_preset_save")) { Text(if (state.busy) "校验中…" else "保存") } }, text = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(label, { label = it }, enabled = !state.busy, singleLine = true, label = { Text("名称") }, modifier = Modifier.fillMaxWidth().testTag("filter_preset_label"))
                    OutlinedTextField(regex, { regex = it }, enabled = !state.busy, minLines = 3, label = { Text("过滤表达式") }, modifier = Modifier.fillMaxWidth().testTag("filter_preset_regex"))
                    Text("多个表达式用反引号 ` 分隔。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃修改？") }, confirmButton = { TextButton(onClick = onCloseEditor) { Text("放弃") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    }
}
