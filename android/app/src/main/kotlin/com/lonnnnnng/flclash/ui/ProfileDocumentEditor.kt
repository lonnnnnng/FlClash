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
import com.lonnnnnng.flclash.service.YamlErrorLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ProfileDocumentEditorState(val ownerId: Long? = null, val session: Long = 0, val remote: Boolean = false,
    val title: String = "配置正文", val original: String = "", val content: String = "", val saving: Boolean = false,
    val error: String? = null, val errorLocation: YamlErrorLocation? = null) {
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
    val edits = remember(state.session) { ProfileDocumentHistory() }
    val documentText = value.text
    // 作者 long：长正文匹配计数不能阻塞输入；正文或查询变化会取消旧任务，旧结果不能覆盖新查询。
    val matches by produceState<Int?>(null, query, documentText) {
        this.value = null
        this.value = withContext(Dispatchers.Default) {
            var count = 0
            var offset = 0
            if (query.isNotEmpty()) while (offset <= documentText.length - query.length) {
                ensureActive()
                val found = documentText.indexOf(query, offset, ignoreCase = true)
                if (found < 0) break
                count++; offset = found + query.length
            }
            count
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    fun locateError() {
        val location = state.errorLocation ?: return
        if (value.text != state.content) return
        value = value.copy(selection = TextRange(location.offset.coerceIn(0, value.text.length)))
        keyboard?.hide()
    }
    // 作者 long：校验失败只移动选区，不走正文变更回调；原输入、撤销栈和父草稿必须完整保留。
    LaunchedEffect(state.session, state.errorLocation) { locateError() }
    fun change(next: TextFieldValue) {
        val textChanged = next.text != value.text
        if (textChanged) {
            edits.record(value.text, next.text)
        }
        value = next
        // 作者 long：选择文字、移动光标和复制只改变选区；清错误与写加密草稿只由正文编辑触发。
        if (textChanged) onChange(next.text)
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
            state.error?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(message, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f).testTag("profile_document_error"))
                    if (state.errorLocation != null) TextButton(::locateError, Modifier.testTag("profile_document_locate_error")) { Text("定位") }
                }
            }
            SearchOptionsTransition(search) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("profile_document_query"), label = { Text("查找") }, singleLine = true)
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton({ find(false) }, enabled = query.isNotEmpty()) { Text("上一处") }
                    TextButton({ find(true) }, enabled = query.isNotEmpty(), modifier = Modifier.testTag("profile_document_next")) { Text("下一处") }
                    Text(if (query.isEmpty()) "" else matches?.let { "$it 处匹配" } ?: "正在查找…", style = MaterialTheme.typography.bodySmall)
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
            AppMenuSourceContent(historySource) {
                IconButton({ history = true }, modifier = Modifier.appMenuSource(historySource).testTag("profile_document_history")) { AppGlyphIcon(AppGlyphs.more) }
            }
            AppDropdownMenu(history, { history = false }, source = historySource) {
                AppDropdownMenuItem({ Text("撤销") }, {
                    history = false; value = TextFieldValue(edits.undo(value.text)); onChange(value.text)
                }, enabled = !state.saving && edits.canUndo, modifier = Modifier.testTag("profile_document_undo"))
                AppDropdownMenuItem({ Text("重做") }, {
                    history = false; value = TextFieldValue(edits.redo(value.text)); onChange(value.text)
                }, enabled = !state.saving && edits.canRedo, modifier = Modifier.testTag("profile_document_redo"))
            }
        }
        IconButton({ search = !search }, enabled = !state.saving, modifier = Modifier.testTag("profile_document_find")) { Icon(Icons.Outlined.Search, null) }
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
