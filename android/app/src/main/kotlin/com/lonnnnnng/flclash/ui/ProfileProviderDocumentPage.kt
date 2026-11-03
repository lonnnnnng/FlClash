package com.lonnnnnng.flclash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileProviderDocumentHost(model: ProfileStatisticsViewModel) {
    val state by model.document.collectAsState()
    // 作者 long：返回令牌保存在launcher会话，不从当前新窗口取目标，旧picker返回不能覆盖后来打开的资源。
    var launchedToken by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        launchedToken?.let { model.receiveProviderFile(it, uri?.toString()) }
        launchedToken = null
    }
    LaunchedEffect(state?.pickerToken) {
        state?.pickerToken?.let { token ->
            if (launchedToken == null) {
                launchedToken = token
                try { picker.launch(arrayOf("*/*")) }
                catch (_: Exception) { model.receiveProviderFile(token, null); launchedToken = null }
            }
        }
    }
    state?.let { draft -> ProfileProviderDocumentPage(draft, model::closeProvider, model::changeProviderContent,
        model::saveProvider, { if (draft.committed) model.retryProviderReadback() else model.openProvider(draft.name, draft.editing, draft.picking) }) }
}

@Composable
internal fun ProfileProviderDocumentPage(state: ProviderDocumentUiState, onClose: () -> Unit,
    onChange: (String) -> Unit, onSave: () -> Unit, onRetry: () -> Unit) {
    var askDiscard by remember(state.name) { mutableStateOf(false) }
    val close = { if (!state.saving) { if (state.dirty) askDiscard = true else onClose() } }
    ProfileEditorPage(onDismissRequest = close, backTag = "profile_provider_document_back", title = { Text(state.name) },
        confirmButton = {
            if (state.saving) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp).testTag("profile_provider_document_saving"), strokeWidth = 2.dp)
            else if (state.editing && !state.committed && state.document?.document?.editable == true) TextButton(onSave, enabled = !state.loading,
                modifier = Modifier.testTag("profile_provider_document_save")) { Text("保存") }
        }, text = {
            Column(Modifier.fillMaxSize().testTag("profile_provider_document"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_provider_document_error"))
                    if (state.document == null || state.picking || state.committed) TextButton(onRetry, enabled = !state.saving) { Text(if (state.committed) "重新读取确认" else "重试") } }
                val document = state.document?.document
                when {
                    state.picking -> Text(if (state.saving) "正在导入文件…" else "选择文件后将替换此 Provider 的缓存内容")
                    document?.kind == "binary" -> Text("此资源为二进制文件，无法预览或编辑；可返回后从文件导入")
                    document != null && state.editing && document.editable -> OutlinedTextField(state.content, onChange,
                        enabled = !state.saving && !state.committed, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxSize().testTag("profile_provider_document_content"))
                    document != null -> {
                        if (!document.editable) Text("MRS 规则只读预览", style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(state.content, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("profile_provider_document_content")) }
                    }
                }
            }
        })
    if (askDiscard) AlertDialog(onDismissRequest = { askDiscard = false }, title = { Text("保存更改？") },
        text = { Text("未保存的更改将在退出后丢失") }, confirmButton = { TextButton({ askDiscard = false; onSave() }) { Text("保存") } },
        dismissButton = { Row { TextButton({ askDiscard = false }) { Text("继续编辑") }; TextButton({ askDiscard = false; onClose() }) { Text("放弃") } } })
}
