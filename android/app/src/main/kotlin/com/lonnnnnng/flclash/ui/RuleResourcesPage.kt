package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.lonnnnnng.flclash.data.RuleResource

/** 作者 long：规则资源管理使用完整页面；列表、上下文菜单、内容与选项保持 Flutter 的导航层级。 */
@Composable
internal fun RuleResourcesPage(
    state: RuleResourcesUiState,
    onDismiss: () -> Unit,
    onCreate: (Boolean) -> Unit,
    onPickFile: () -> Unit,
    onEdit: (RuleResource, Boolean) -> Unit,
    onSync: (RuleResource) -> Unit,
    onSyncAll: () -> Unit,
    onDeleteRequest: (RuleResource) -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onMove: (RuleResource, Int) -> Unit,
    onCloseEditor: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var addMenu by remember { mutableStateOf(false) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    ProfileEditorPage(onDismissRequest = { if (!state.busy) onDismiss() }, title = { Text("规则资源") },
        backTag = "rule_resources_back",
        confirmButton = {
            IconButton(onClick = onSyncAll, enabled = !state.busy && state.rows.any { it.url.isNotBlank() }) { Icon(Icons.Outlined.Sync, "更新全部规则资源") }
            Box {
                IconButton(onClick = { addMenu = true }, enabled = !state.busy, modifier = Modifier.testTag("rule_resources_add")) { Icon(Icons.Outlined.Add, "添加资源") }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("从头开始") }, onClick = { addMenu = false; onCreate(false) })
                    DropdownMenuItem(text = { Text("从 URL 导入") }, onClick = { addMenu = false; onCreate(true) })
                    DropdownMenuItem(text = { Text("从文件导入") }, onClick = { addMenu = false; onPickFile() })
                }
            }
        }, text = {
            Column(Modifier.fillMaxSize().testTag("rule_resources_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.editor == null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("搜索规则资源") }, modifier = Modifier.fillMaxWidth().testTag("rule_resources_search"),
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清除搜索") } })
                val rows = state.rows.filter { query.isBlank() || it.label.contains(query, true) }
                if (!state.loading && rows.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "暂无规则资源" else "没有匹配的结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(rows, key = { _, resource -> resource.id }) { _, resource ->
                        Column {
                            Row(Modifier.fillMaxWidth().combinedClickable(enabled = !state.busy, onClick = { onEdit(resource, false) }, onLongClick = { menuId = resource.id }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(resource.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text((if (resource.url.isEmpty()) "本地" else "远程") + " · " + resource.behavior + " · " + resource.format,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("更新于 " + DateUtils.getRelativeTimeSpanString(resource.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(onClick = { menuId = resource.id }, enabled = !state.busy) { Icon(Icons.Outlined.MoreVert, "资源操作 · ${resource.label}") }
                                    DropdownMenu(expanded = menuId == resource.id, onDismissRequest = { menuId = null }) {
                                        DropdownMenuItem(text = { Text(if (resource.editable) "编辑" else "预览") }, onClick = { menuId = null; onEdit(resource, false) })
                                        if (resource.url.isNotBlank()) DropdownMenuItem(text = { Text("同步") }, onClick = { menuId = null; onSync(resource) })
                                        DropdownMenuItem(text = { Text("选项") }, onClick = { menuId = null; onEdit(resource, true) })
                                        val index = state.rows.indexOf(resource)
                                        DropdownMenuItem(text = { Text("上移") }, enabled = index > 0, onClick = { menuId = null; onMove(resource, -1) })
                                        DropdownMenuItem(text = { Text("下移") }, enabled = index < state.rows.lastIndex, onClick = { menuId = null; onMove(resource, 1) })
                                        DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menuId = null; onDeleteRequest(resource) })
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        })
    state.editor?.let { draft -> RuleResourceEditor(draft, state.busy, state.error, onCloseEditor, onSave) }
    state.deleting?.let { resource -> AlertDialog(onDismissRequest = onCancelDelete, title = { Text("删除 ${resource.label}？") },
        text = { Text(if (state.users.isEmpty()) "删除全局资源？" else "以下配置仍引用此资源：${state.users.joinToString("、")}。删除后引用将显示缺失，可重新选择资源。") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !state.busy) { Text("删除") } }, dismissButton = { TextButton(onClick = onCancelDelete) { Text("取消") } }) }
}

@Composable
private fun RuleResourceEditor(draft: RuleResourceDraft, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var label by rememberSaveable(draft) { mutableStateOf(draft.label) }
    var url by rememberSaveable(draft) { mutableStateOf(draft.url) }
    var content by rememberSaveable(draft) { mutableStateOf(draft.content) }
    var discard by remember { mutableStateOf(false) }
    val dirty = label != draft.label || url != draft.url || content != draft.content
    val close = { if (!busy) { if (dirty) discard = true else onDismiss() } }
    ProfileEditorPage(onDismissRequest = close, title = { Text(if (draft.options) "资源选项" else if (draft.readOnly && draft.original != null) "预览资源" else "编辑资源") },
        backTag = "rule_resource_editor_back", confirmButton = {
            if (!draft.readOnly || draft.original == null || draft.options) TextButton(onClick = { onSave(label, url, content) }, enabled = !busy && label.isNotBlank(), modifier = Modifier.testTag("rule_resource_save")) { Text("保存") }
        }, text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(label, { label = it }, label = { Text("名称") }, singleLine = true, enabled = !busy && (!draft.readOnly || draft.original == null), modifier = Modifier.fillMaxWidth().testTag("rule_resource_label"))
                if (!draft.readOnly || draft.url.isNotBlank()) OutlinedTextField(url, { url = it }, label = { Text("URL（留空为本地资源）") }, enabled = !busy && !draft.readOnly, modifier = Modifier.fillMaxWidth().testTag("rule_resource_url"))
                if (!draft.options) {
                    if (draft.readOnly) SelectionContainer { Text(content, modifier = Modifier.fillMaxWidth().testTag("rule_resource_preview")) }
                    else if (url.isBlank()) OutlinedTextField(content, { content = it }, label = { Text("规则正文 · YAML / Text") }, minLines = 14, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("rule_resource_content"))
                    else Text("保存时下载并识别资源格式，下载失败保留原资源。", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            }
        })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃修改？") }, text = { Text("尚未保存的资源修改将丢失。") },
        confirmButton = { TextButton(onClick = { discard = false; onDismiss() }) { Text("放弃") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
