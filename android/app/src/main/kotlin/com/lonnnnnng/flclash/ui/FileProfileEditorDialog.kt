package com.lonnnnnng.flclash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun FileProfileEditorDialog(
    state: ProfileEditorUiState,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onBeginPick: () -> Long?,
    onFilePicked: (Long?, String?) -> Unit,
    onClearReplacement: () -> Unit,
    onSave: (String?, String) -> Unit,
    onSaveWithLabel: ((String?, String, String) -> Unit)? = null,
) {
    val profile = state.profile ?: return
    // 作者 long：正文和密钥只属于当前编辑会话，不写 SavedState；选择器只恢复“正在选择”标记以接收系统回调。
    var content by remember(profile.id, state.content) { mutableStateOf(state.content) }
    var key by remember(profile.id, profile.ageSecretKey) { mutableStateOf(profile.ageSecretKey) }
    var label by rememberSaveable(profile.id) { mutableStateOf(profile.label) }
    var pickToken by rememberSaveable(profile.id) { mutableStateOf<Long?>(null) }
    val picking = pickToken != null
    var confirmDiscard by remember { mutableStateOf(false) }
    var showYaml by remember(profile.id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = pickToken
        pickToken = null
        onFilePicked(token, uri?.toString())
    }
    ClearAgeInputOnBackground {
        // 作者 long：选择器可能在下一帧重组前触发 ON_STOP，必须现场读取 token；捕获上一帧的 picking 会把系统选择误判为普通后台。
        if (pickToken == null) { key = ""; onDismiss() }
    }
    val replacement = state.replacement
    val dirty = content != state.content || key != profile.ageSecretKey || replacement != null || label != profile.label
    val locked = readOnly || state.isSaving || state.isReadingFile || picking
    val validReplacement = replacement == null || replacement.isValidYaml || replacement.isEncrypted && key.isNotBlank()
    fun exit() {
        if (state.isSaving || state.isReadingFile) return
        focus.clearFocus(force = true)
        keyboard?.hide()
        if (dirty) confirmDiscard = true else onDismiss()
    }
    ProfileEditorPage(
        onDismissRequest = ::exit,
        title = { Text("编辑 ${profile.realLabel}") },
        text = {
            Column(Modifier.fillMaxWidth().testTag("file_profile_editor_form")
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileFormSection {
                    OutlinedTextField(label, { label = it.take(64) }, enabled = !locked, singleLine = true,
                        label = { Text("名称") }, modifier = Modifier.fillMaxWidth().testTag("file_profile_editor_label"))
                    AgeSecretKeyField(key, { key = it }, !locked, "file_profile_editor_age_key")
                }
                ProfileFormSection {
                ProfileSectionRow(title = "从文件替换", detail = replacement?.name,
                    icon = Icons.Outlined.UploadFile, enabled = !locked,
                    modifier = Modifier.testTag("file_profile_replace_pick"), onClick = {
                    onBeginPick()?.let { token ->
                        pickToken = token
                        try { picker.launch(arrayOf("*/*")) }
                        catch (_: Exception) { pickToken = null; onFilePicked(token, null) }
                    }
                })
                if (state.isReadingFile) {
                    Text("正在读取并检查文件…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (replacement != null) {
                    Text(replacement.name, maxLines = 2, modifier = Modifier.testTag("file_profile_replacement_name"))
                    Text("文件大小：${replacement.sizeBytes} 字节")
                    Text(when {
                        replacement.isEncrypted -> "age 加密配置，保存时使用上方密钥解密并校验"
                        replacement.isValidYaml -> "YAML 格式校验通过，保存后替换当前文件"
                        else -> "无法读取 YAML 配置，请重新选择文件"
                    }, color = if (replacement.isEncrypted || replacement.isValidYaml) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    TextButton(onClick = onClearReplacement, enabled = !locked, modifier = Modifier.testTag("file_profile_replacement_clear")) { Text("取消文件替换") }
                } else {
                    OutlinedButton(onClick = { showYaml = !showYaml }, enabled = !state.isSaving,
                        modifier = Modifier.testTag("file_profile_toggle_yaml")) { Text(if (showYaml) "收起原始 YAML" else "编辑原始 YAML") }
                    if (showYaml) OutlinedTextField(content, { content = it }, modifier = Modifier.fillMaxWidth().testTag("profile_editor_content"),
                        label = { Text("YAML") }, minLines = 5, maxLines = 10, enabled = !locked)
                }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                keyboard?.hide()
                val edited = content.takeIf { it != state.content }
                if (onSaveWithLabel != null) onSaveWithLabel(edited, key.trim(), label.trim()) else onSave(edited, key.trim())
            }, enabled = !locked && label.isNotBlank() && validReplacement && (replacement != null || content.isNotBlank() || content == state.content),
                modifier = Modifier.testTag("file_profile_editor_save")) {
                if (state.isSaving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = ::exit, enabled = !state.isSaving && !state.isReadingFile) { Text("取消") } },
    )
    if (confirmDiscard) {
        AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("放弃未保存的修改？") },
            text = { Text("本次名称、密钥、文件选择和 YAML 修改都不会保存。") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } })
    }
}
