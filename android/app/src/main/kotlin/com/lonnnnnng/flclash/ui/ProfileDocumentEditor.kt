package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

data class ProfileDocumentEditorState(val ownerId: Long? = null, val session: Long = 0, val remote: Boolean = false,
    val title: String = "配置正文", val original: String = "", val content: String = "", val saving: Boolean = false, val error: String? = null) {
    override fun toString() = "ProfileDocumentEditorState(id=$ownerId, saving=$saving)"
}

/** 作者 long：正文返回父草稿后才由外层保存；查找/替换和撤销只修改内存，退出不能意外写入订阅缓存。 */
@Composable
internal fun ProfileDocumentEditor(state: ProfileDocumentEditorState, onChange: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    if (state.ownerId == null) return
    var value by remember(state.session) { mutableStateOf(TextFieldValue(state.content)) }
    var search by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    val historySource = rememberAppMenuSource()
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var exiting by remember { mutableStateOf(false) }
    val undo = remember(state.session) { mutableStateListOf<String>() }
    val redo = remember(state.session) { mutableStateListOf<String>() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun change(next: TextFieldValue) {
        if (next.text != value.text) {
            undo.add(value.text)
            // 作者 long：配置可达8MiB，历史有总字符预算，不能把每次按键的整份副本无限保留。
            while (undo.size > 20 || undo.sumOf { it.length.toLong() } > 2_000_000) undo.removeAt(0)
            redo.clear()
        }
        value = next; onChange(next.text)
    }
    fun find(forward: Boolean) {
        if (query.isEmpty()) return
        val start = if (forward) value.selection.max else value.selection.min - 1
        val index = if (forward) value.text.indexOf(query, start, ignoreCase = true).takeIf { it >= 0 } ?: value.text.indexOf(query, ignoreCase = true)
            else (if (start < 0) -1 else value.text.lastIndexOf(query, start, ignoreCase = true)).takeIf { it >= 0 } ?: value.text.lastIndexOf(query, ignoreCase = true)
        if (index >= 0) value = value.copy(selection = TextRange(index, index + query.length))
        keyboard?.hide()
    }
    fun back() { if (!state.saving) { if (value.text != state.original) exiting = true else onDismiss() } }
    ProfileEditorPage(::back, { Text(state.title) }, {
        Column(Modifier.fillMaxSize().testTag("profile_document_editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("保存仅返回配置草稿，外层保存后才替换本地副本。", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).testTag("profile_document_error")) }
            if (search) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("profile_document_query"), label = { Text("查找") }, singleLine = true)
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton({ find(false) }, enabled = query.isNotEmpty()) { Text("上一处") }
                    TextButton({ find(true) }, enabled = query.isNotEmpty(), modifier = Modifier.testTag("profile_document_next")) { Text("下一处") }
                    Text(if (query.isEmpty()) "" else "${Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(value.text).count()} 处匹配", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(replacement, { replacement = it }, Modifier.weight(1f).testTag("profile_document_replacement"), label = { Text("替换为") }, singleLine = true)
                    TextButton({
                        if (!value.selection.collapsed && value.text.substring(value.selection.min, value.selection.max).equals(query, true)) {
                            val start = value.selection.min
                            change(TextFieldValue(value.text.replaceRange(value.selection.min, value.selection.max, replacement), TextRange(start + replacement.length)))
                        } else find(true)
                    }, enabled = query.isNotEmpty() && !state.saving) { Text("替换") }
                    TextButton({ change(TextFieldValue(value.text.replace(query, replacement, ignoreCase = true))) },
                        enabled = query.isNotEmpty() && !state.saving, modifier = Modifier.testTag("profile_document_replace_all")) { Text("全部") }
                }
            }
            ProfileCodeField(value, ::change, !state.saving, Modifier.fillMaxWidth().weight(1f))
        }
    }, confirmButton = {
        Box {
            IconButton({ history = true }, modifier = Modifier.appMenuSource(historySource).testTag("profile_document_history")) { AppGlyphIcon(AppGlyphs.more, "编辑操作") }
            AppDropdownMenu(history, { history = false }, source = historySource) {
                AppDropdownMenuItem({ Text("撤销") }, {
                    history = false; redo.add(value.text); value = TextFieldValue(undo.removeAt(undo.lastIndex)); onChange(value.text)
                }, enabled = !state.saving && undo.isNotEmpty(), modifier = Modifier.testTag("profile_document_undo"))
                AppDropdownMenuItem({ Text("重做") }, {
                    history = false; undo.add(value.text); value = TextFieldValue(redo.removeAt(redo.lastIndex)); onChange(value.text)
                }, enabled = !state.saving && redo.isNotEmpty(), modifier = Modifier.testTag("profile_document_redo"))
            }
        }
        IconButton({ search = !search }, enabled = !state.saving, modifier = Modifier.testTag("profile_document_find")) { Icon(Icons.Outlined.Search, "查找替换") }
        TextButton(onSave, enabled = !state.saving && value.text.isNotBlank(), modifier = Modifier.testTag("profile_document_save")) { Text("保存") }
    }, backTag = "profile_document_back", contentPadding = PaddingValues())
    if (exiting) AlertDialog(onDismissRequest = { exiting = false }, title = { Text("保存正文修改？") },
        text = { Text("保存后返回配置草稿，仍需在外层确认。") },
        confirmButton = { TextButton({ exiting = false; onSave() }, enabled = !state.saving) { Text("保存并返回") } },
        dismissButton = { Row {
            TextButton({ exiting = false; onDismiss() }, modifier = Modifier.testTag("profile_document_discard")) { Text("放弃") }
            TextButton({ exiting = false }) { Text("继续编辑") }
        } })
}
