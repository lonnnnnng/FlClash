package com.lonnnnnng.flclash.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile

/** 作者 long：表单与本地正文草稿同属一轮编辑；选择文件不写盘，外层保存才同时提交设置和本地副本。 */
@Composable
internal fun RemoteProfileEditorDialog(
    profile: Profile, saving: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Long) -> Unit,
    onSaveWithAgeKey: ((String, String, Boolean, Long, String) -> Unit)? = null,
    state: RemoteProfileEditorUiState? = null,
    onDraftChange: (RemoteProfileDraft) -> Unit = {},
    onOpenDocument: () -> Unit = {},
    onBeginPick: () -> Long? = { null },
    onFilePicked: (Long?, String?) -> Unit = { _, _ -> },
    onClearReplacement: () -> Unit = {},
    onRetryRead: () -> Unit = {},
) {
    var draft by remember(profile.id) { mutableStateOf(state?.draft ?: RemoteProfileDraft.from(profile)) }
    LaunchedEffect(state?.draft) { state?.draft?.let { draft = it } }
    var pickToken by rememberSaveable(profile.id) { mutableStateOf<Long?>(null) }
    var discard by remember { mutableStateOf(false) }
    var warnUpdate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val locked = saving || state?.isLoading == true || state?.isReadingFile == true || pickToken != null
    val hasBody = state?.draftContent != null || state?.replacement != null
    val dirty = draft != RemoteProfileDraft.from(profile) || hasBody
    val interval = draft.intervalMinutes.toLongOrNull()
    val valid = !locked && draft.label.isNotBlank() && draft.label.length <= 64 &&
        runCatching { com.lonnnnnng.flclash.data.normalizeSubscriptionUrl(draft.url) }.isSuccess &&
        (!draft.autoUpdate || interval != null && interval > 0)
    fun change(next: RemoteProfileDraft) { draft = next; onDraftChange(next) }
    fun exit() {
        if (saving || state?.isReadingFile == true) return
        focus.clearFocus(force = true); keyboard?.hide()
        if (dirty) discard = true else onDismiss()
    }
    fun save(auto: Boolean = draft.autoUpdate) {
        val minutes = interval?.takeIf { it > 0 } ?: (profile.autoUpdateIntervalMillis / 60_000).coerceAtLeast(1)
        if (onSaveWithAgeKey != null) onSaveWithAgeKey(draft.label.trim(), draft.url.trim(), auto, minutes, draft.ageKey.trim())
        else onSave(draft.label.trim(), draft.url.trim(), auto, minutes)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = pickToken; pickToken = null; onFilePicked(token, uri?.toString())
    }
    ClearAgeInputOnBackground {
        // 作者 long：配置变更保留VM内存草稿；系统选择文件仍属于当前会话，普通退后台才关闭敏感表单。
        if (pickToken == null && activity?.isChangingConfigurations != true && !saving) onDismiss()
    }
    ProfileEditorPage(::exit, { Text("编辑订阅") }, {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("remote_profile_editor_form"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileFormSection {
                OutlinedTextField(draft.label, { change(draft.copy(label = it.take(64))) }, Modifier.fillMaxWidth().testTag("remote_profile_editor_label"),
                    enabled = !locked, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(draft.url, { change(draft.copy(url = it)) }, Modifier.fillMaxWidth().testTag("remote_profile_editor_url"),
                    enabled = !locked, label = { Text("订阅链接") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                AgeSecretKeyField(draft.ageKey, { change(draft.copy(ageKey = it)) }, !locked, "remote_profile_editor_age_key")
            }
            ProfileFormSection {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("自动更新")
                    Switch(draft.autoUpdate, { change(draft.copy(autoUpdate = it)) }, enabled = !locked, modifier = Modifier.testTag("remote_profile_editor_auto_update"))
                }
                if (draft.autoUpdate) OutlinedTextField(draft.intervalMinutes, { change(draft.copy(intervalMinutes = it.filter(Char::isDigit).take(7))) },
                    Modifier.fillMaxWidth().testTag("remote_profile_editor_interval"), enabled = !locked,
                    label = { Text("自动更新间隔（分钟）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            ProfileFormSection {
                Text("本地副本", style = MaterialTheme.typography.titleSmall)
                Text(if (hasBody) "正文已修改，尚未保存" else "编辑或从本机文件替换本地缓存。手动同步仍会覆盖本地修改。", style = MaterialTheme.typography.bodySmall)
                ProfileSectionRow("编辑正文", icon = Icons.Outlined.Description,
                    enabled = !locked && state?.document != null && state.replacement?.isEncrypted != true,
                    modifier = Modifier.testTag("remote_profile_document_edit"), onClick = onOpenDocument)
                ProfileSectionRow("从文件替换", state?.replacement?.name, Icons.Outlined.UploadFile,
                    !locked && state?.document != null, Modifier.testTag("remote_profile_replace_pick")) {
                    onBeginPick()?.let { token ->
                        pickToken = token
                        try { picker.launch(arrayOf("*/*")) }
                        catch (_: Exception) { pickToken = null; onFilePicked(token, null) }
                    }
                }
                state?.replacement?.let {
                    Text(if (it.isEncrypted) "age 加密文件，外层保存时使用上方密钥解密并校验。" else "已读取 ${it.sizeBytes} 字节，YAML 校验通过。")
                }
                if (hasBody) TextButton(onClearReplacement, enabled = !locked, modifier = Modifier.testTag("remote_profile_replacement_clear")) { Text("放弃正文替换") }
                if (state?.document == null && state?.isLoading == false) TextButton(onRetryRead, enabled = !locked) { Text("重新读取本地副本") }
            }
            if (state?.isLoading == true || state?.isReadingFile == true) LinearProgressIndicator(Modifier.fillMaxWidth())
            state?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("remote_profile_editor_error")) }
        }
    }, confirmButton = {
        TextButton({ focus.clearFocus(force = true); keyboard?.hide(); if (hasBody && draft.autoUpdate) warnUpdate = true else save() },
            enabled = valid, modifier = Modifier.testTag("remote_profile_editor_save")) { Text(if (saving) "正在保存" else "保存") }
    }, dismissButton = { TextButton(::exit, enabled = !locked) { Text("取消") } })
    if (warnUpdate) AlertDialog(onDismissRequest = { warnUpdate = false }, title = { Text("自动同步可能覆盖本地修改") },
        text = { Text("本次只修改手机上的本地副本。可以关闭自动更新，或保留自动更新后保存。手动同步仍会替换本地正文。") },
        confirmButton = { TextButton({ warnUpdate = false; change(draft.copy(autoUpdate = false)); save(false) }, modifier = Modifier.testTag("remote_profile_disable_auto_save")) { Text("关闭自动更新并保存") } },
        dismissButton = { TextButton({ warnUpdate = false; save(true) }, modifier = Modifier.testTag("remote_profile_keep_auto_save")) { Text("保留自动更新") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") }, text = { Text("订阅设置和正文草稿都不会写入。") },
        confirmButton = { TextButton({ discard = false; onDismiss() }, modifier = Modifier.testTag("remote_profile_discard")) { Text("放弃") } },
        dismissButton = { TextButton({ discard = false }) { Text("继续编辑") } })
}
