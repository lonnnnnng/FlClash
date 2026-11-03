package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Script

/** 作者 long：脚本库与Flutter保持整页资源层级；排序模式复用资源视口，正文编辑不再挤在列表弹窗。 */
@Composable
internal fun ScriptsLibraryDialog(
    state: ScriptsUiState,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Script) -> Unit,
    onDelete: (Script) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onImportRemote: () -> Unit = {},
    onSync: (Script) -> Unit = {},
    onImportFile: () -> Unit = {},
    onEditUrl: (Script) -> Unit = {},
) {
    var add by remember { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<Script?>(null) }
    ProfileEditorPage(onDismissRequest = { if (!state.isBusy) onDismiss() }, title = { Text("脚本库") },
        backTag = "scripts_back", confirmButton = {
            if (state.scripts.size > 1) TextButton(onClick = { sorting = !sorting }, enabled = !state.isBusy,
                modifier = Modifier.testTag("scripts_sort")) { Text(if (sorting) "完成" else "排序") }
            Box {
                IconButton(onClick = { add = true }, enabled = !state.isBusy && !sorting, modifier = Modifier.testTag("scripts_add")) {
                    Icon(Icons.Outlined.AddCircleOutline, "添加脚本")
                }
                DropdownMenu(expanded = add, onDismissRequest = { add = false }) {
                    DropdownMenuItem(text = { Text("新建脚本") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { add = false; onCreate() }, modifier = Modifier.testTag("scripts_create"))
                    DropdownMenuItem(text = { Text("导入远程脚本") }, leadingIcon = { Icon(Icons.Outlined.CloudDownload, null) },
                        onClick = { add = false; onImportRemote() })
                    DropdownMenuItem(text = { Text("从文件导入") }, leadingIcon = { Icon(Icons.Outlined.Description, null) },
                        onClick = { add = false; onImportFile() }, modifier = Modifier.testTag("scripts_import_file"))
                }
            }
        }, text = {
            Column(Modifier.fillMaxSize().testTag("scripts_page")) {
                if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, Modifier.padding(vertical = 8.dp).testTag("scripts_error"), color = MaterialTheme.colorScheme.error) }
                if (state.scripts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有脚本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (sorting) {
                    Text("长按拖动调整顺序", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                    ResourceOrderList(state.scripts.map { ResourceOrderItem(it.id.toString(), it.label, if (it.url.isBlank()) "本地" else "远程") },
                        state.isBusy, "scripts", { order -> onReorder(order.map(String::toLong)) }, Modifier.weight(1f))
                } else LazyColumn(Modifier.fillMaxSize().testTag("scripts_list"), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.scripts, key = Script::id) { script ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !state.isBusy) { onEdit(script) }
                            .heightIn(min = 80.dp).testTag("script_row_${script.id}").padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(script.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (script.url.isBlank()) "本地" else "远程", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (script.id in state.updatingIds) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else script.lastUpdateAt?.let { Text(DateUtils.getRelativeTimeSpanString(it).toString(),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                            Box {
                                IconButton(onClick = { menu = script.id }, enabled = !state.isBusy, modifier = Modifier.testTag("script_menu_${script.id}")) {
                                    Icon(Icons.Outlined.MoreVert, "${script.label}菜单")
                                }
                                DropdownMenu(expanded = menu == script.id, onDismissRequest = { menu = null }) {
                                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = null; onEdit(script) })
                                    if (script.url.isNotBlank()) {
                                        DropdownMenuItem(text = { Text("修改地址") }, onClick = { menu = null; onEditUrl(script) }, modifier = Modifier.testTag("script_url_${script.id}"))
                                        DropdownMenuItem(text = { Text("同步") }, onClick = { menu = null; onSync(script) }, modifier = Modifier.testTag("script_sync_${script.id}"))
                                    }
                                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menu = null; pendingDelete = script })
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        })
    pendingDelete?.let { script ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("删除脚本") },
            text = { Text("删除“${script.label}”后，已绑定的配置也会解除绑定。") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(script) }, enabled = !state.isBusy) { Text("删除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } })
    }
}

@Composable
internal fun ScriptRemoteDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onImport: (label: String, url: String) -> Unit,
    script: Script? = null,
    error: String? = null,
    initialLabel: String = "",
    initialUrl: String = script?.url.orEmpty(),
    onDraftChange: (String, String) -> Unit = { _, _ -> },
) {
    var label by remember(script?.id) { mutableStateOf(initialLabel) }
    // 作者 long：地址可能包含访问参数，只保留当前窗口会话，失败时继续编辑而不是提前关页。
    var url by remember(script?.id) { mutableStateOf(initialUrl) }
    ProfileEditorPage(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (script == null) "导入远程脚本" else "修改脚本地址") }, backTag = "script_remote_back",
        confirmButton = { TextButton(onClick = { onImport(label, url) }, enabled = !busy && url.isNotBlank(), modifier = Modifier.testTag("script_remote_submit")) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (script == null) "导入" else "保存")
        } }, text = {
            Column(Modifier.fillMaxWidth().testTag("script_remote_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (script == null) OutlinedTextField(label, { label = it; onDraftChange(it, url) }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text("脚本名称（可选）") }, singleLine = true)
                OutlinedTextField(url, { url = it; onDraftChange(label, it) }, Modifier.fillMaxWidth().testTag("script_remote_url"), enabled = !busy,
                    label = { Text("HTTP(S) 脚本地址") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (script != null) Text("新地址下载成功后才会替换当前脚本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
}

@Composable
internal fun ScriptEditorDialog(
    state: ScriptEditorUiState,
    onDismiss: () -> Unit,
    onSave: (label: String, content: String) -> Unit,
    onDraftChange: (String, String) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
) {
    var label by remember(state.session) { mutableStateOf(state.label) }
    var value by remember(state.session) { mutableStateOf(TextFieldValue(state.content)) }
    var confirmExit by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    val undo = remember(state.session) { mutableStateListOf<String>() }
    val redo = remember(state.session) { mutableStateListOf<String>() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun change(next: TextFieldValue) {
        if (next.text != value.text) {
            undo.add(value.text)
            while (undo.size > 20 || (undo.sumOf { it.length.toLong() } > 2_000_000 && undo.size > 1)) undo.removeAt(0)
            redo.clear()
        }
        value = next
        onDraftChange(label, next.text)
    }
    fun back() {
        if (state.isSaving) return
        if (label != state.originalLabel || value.text != state.originalContent) confirmExit = true else onDismiss()
    }
    fun find(forward: Boolean) {
        if (query.isBlank()) return
        val start = if (forward) value.selection.max else value.selection.min - 1
        val index = if (forward) value.text.indexOf(query, start.coerceAtMost(value.text.length), ignoreCase = true)
            .takeIf { it >= 0 } ?: value.text.indexOf(query, ignoreCase = true)
        else (if (start < 0) -1 else value.text.lastIndexOf(query, start, ignoreCase = true))
            .takeIf { it >= 0 } ?: value.text.lastIndexOf(query, ignoreCase = true)
        if (index >= 0) value = value.copy(selection = TextRange(index, index + query.length))
        keyboard?.hide()
    }
    ProfileEditorPage(onDismissRequest = ::back, title = { Text(if (state.script == null) "新建脚本" else "编辑脚本") },
        backTag = "script_editor_back", confirmButton = {
            IconButton(onClick = { searching = !searching }, enabled = !state.isSaving, modifier = Modifier.testTag("script_find")) { Icon(Icons.Outlined.Search, "查找和替换") }
            TextButton(onClick = { onSave(label, value.text) }, enabled = !state.isSaving && !state.loadFailed && label.isNotBlank() && value.text.isNotBlank(),
                modifier = Modifier.testTag("script_save")) { if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("保存") }
        }, text = {
            Column(Modifier.fillMaxSize().testTag("script_editor_page"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it; onDraftChange(it, value.text) }, Modifier.fillMaxWidth().testTag("script_label"),
                    label = { Text("脚本名称") }, singleLine = true, enabled = !state.isSaving && !state.loadFailed)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("script_editor_error")) }
                if (state.loadFailed) TextButton(onClick = onRetry) { Text("重新读取") }
                if (searching) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("script_query"), label = { Text("查找") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (query.isEmpty()) "输入查找文字" else "${Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(value.text).count()} 处匹配",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { find(false) }, enabled = query.isNotEmpty()) { Text("上一处") }
                        TextButton(onClick = { find(true) }, enabled = query.isNotEmpty(), modifier = Modifier.testTag("script_find_next")) { Text("下一处") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(replacement, { replacement = it }, Modifier.weight(1f).testTag("script_replacement"), label = { Text("替换为") }, singleLine = true)
                        TextButton(onClick = {
                            if (value.selection.max > value.selection.min && value.text.substring(value.selection.min, value.selection.max).equals(query, true)) {
                                val from = value.selection.min
                                change(TextFieldValue(value.text.replaceRange(value.selection.min, value.selection.max, replacement), TextRange(from + replacement.length)))
                            } else find(true)
                        }, enabled = query.isNotEmpty() && !state.isSaving) { Text("替换") }
                        TextButton(onClick = { change(TextFieldValue(value.text.replace(query, replacement, ignoreCase = true))) },
                            enabled = query.isNotEmpty() && !state.isSaving, modifier = Modifier.testTag("script_replace_all")) { Text("全部") }
                    }
                }
                Row {
                    TextButton(onClick = { redo.add(value.text); value = TextFieldValue(undo.removeAt(undo.lastIndex)); onDraftChange(label, value.text) },
                        enabled = undo.isNotEmpty() && !state.isSaving, modifier = Modifier.testTag("script_undo")) { Text("撤销") }
                    TextButton(onClick = { undo.add(value.text); value = TextFieldValue(redo.removeAt(redo.lastIndex)); onDraftChange(label, value.text) },
                        enabled = redo.isNotEmpty() && !state.isSaving, modifier = Modifier.testTag("script_redo")) { Text("重做") }
                }
                OutlinedTextField(value, ::change, Modifier.fillMaxWidth().weight(1f).testTag("script_content"),
                    enabled = !state.isSaving && !state.loadFailed, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("JavaScript") })
            }
        })
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("保存脚本修改？") },
        text = { Text("可以保存后返回，或放弃本次修改。") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onSave(label, value.text) },
            enabled = !state.isSaving && label.isNotBlank() && value.text.isNotBlank()) { Text("保存并返回") } },
        dismissButton = { Row {
            TextButton(onClick = { confirmExit = false; onDismiss() }, modifier = Modifier.testTag("script_discard")) { Text("放弃") }
            TextButton(onClick = { confirmExit = false }) { Text("继续编辑") }
        } })
}
