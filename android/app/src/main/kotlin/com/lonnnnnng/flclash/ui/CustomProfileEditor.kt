package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType

@Composable
internal fun ProfileEditorDialog(
    profile: Profile,
    initialContent: String,
    saving: Boolean,
    isNewDraft: Boolean = false,
    hasPersistedChanges: Boolean = false,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit = onDismiss,
    onFinishNewDraft: (String?) -> Unit = { onDiscard() },
    onSave: (String) -> Unit,
    onOpenRulesEditor: (Profile) -> Unit = {},
    onOpenProxiesEditor: (Profile) -> Unit = {},
    onOpenProxyGroupsEditor: (Profile) -> Unit = {},
    onOpenProvidersEditor: (Profile) -> Unit = {},
    onOpenDnsEditor: (Profile) -> Unit = {},
    onOpenDialersEditor: (Profile) -> Unit = {},
    onOpenScriptBinding: (Profile) -> Unit = {},
    overview: CustomProfileSummary? = null,
    onRename: (Profile, String) -> Unit = { _, _ -> },
    onPreview: (Long) -> Unit = {},
    moreEditorState: ProfileMoreEditorUiState = ProfileMoreEditorUiState(),
    onOpenMoreEditor: (Profile) -> Unit = {},
    onCloseMoreEditor: () -> Unit = {},
    onSaveMoreEditor: (com.lonnnnnng.flclash.service.ProfileNtpOverrides, com.lonnnnnng.flclash.service.ProfileSnifferOverrides) -> Unit = { _, _ -> },
    customImportState: CustomImportUiState = CustomImportUiState(),
    onPickCustomImportFile: () -> Unit = {},
    onImportCustomUrl: (String, String) -> Unit = { _, _ -> },
    onConfirmCustomImport: () -> Unit = {},
    onCancelCustomImport: () -> Unit = {},
) {
    val initialDraftLabel by rememberSaveable(profile.id) { mutableStateOf(profile.label) }
    var showTitleActions by remember { mutableStateOf(false) }
    var showRename by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showMore by rememberSaveable(profile.id) { mutableStateOf(false) }
    var moreSection by rememberSaveable(profile.id) { mutableStateOf("ntp") }
    var content by rememberSaveable(profile.id, initialContent) { mutableStateOf(initialContent) }
    var showDiscardConfirmation by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showSectionDiscardConfirmation by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showDraftNameDialog by rememberSaveable(profile.id) { mutableStateOf(false) }
    var draftName by rememberSaveable(profile.id) { mutableStateOf(profile.label) }
    var showYaml by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showCustomImport by rememberSaveable(profile.id) { mutableStateOf(false) }
    var importUrl by remember(profile.id) { mutableStateOf("") }
    var importAgeKey by remember(profile.id) { mutableStateOf("") }
    var showImportMenu by remember { mutableStateOf(false) }
    var pendingSectionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isDirty = content != initialContent
    val clearEditorFocus = remember { arrayOf<(() -> Unit)?>(null) }
    val clearImportFocus = remember { arrayOf<(() -> Unit)?>(null) }

    // 作者 long：分区编辑即时持久化；退出时区分空草稿与已有内容，防止误删已保存配置。
    fun finishExit() {
        if (isNewDraft && hasPersistedChanges && profile.label == initialDraftLabel) {
            draftName = profile.label
            showDraftNameDialog = true
        } else {
            onDiscard()
        }
    }

    fun requestExit() {
        if (saving || customImportState.isLoading) return
        clearEditorFocus[0]?.invoke()
        if (isDirty) showDiscardConfirmation = true else finishExit()
    }

    fun openSection(action: () -> Unit) {
        // 作者 long：进入分区或确认弹窗前释放 YAML 输入焦点，避免键盘恢复焦点后改变点击位置。
        clearEditorFocus[0]?.invoke()
        if (isDirty) {
            pendingSectionAction = action
            showSectionDiscardConfirmation = true
        } else {
            action()
        }
    }

    ProfileEditorPage(
        onDismissRequest = { requestExit() },
        backTag = "custom_profile_back",
        title = {
            Box {
                TextButton(onClick = { showTitleActions = true }, enabled = !saving,
                    modifier = Modifier.testTag("custom_profile_title_menu")) {
                    Text(profile.realLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Outlined.ArrowDropDown, "配置操作")
                }
                DropdownMenu(showTitleActions, { showTitleActions = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { showTitleActions = false; showRename = true },
                        modifier = Modifier.testTag("custom_profile_rename"))
                    DropdownMenuItem(text = { Text("运行配置") }, onClick = { showTitleActions = false; openSection { onPreview(profile.id) } },
                        modifier = Modifier.testTag("custom_profile_preview"))
                }
            }
        },
        text = {
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            DisposableEffect(focus, keyboard) {
                // 作者 long：全屏编辑器是独立窗口，必须清理它自己的焦点；外层 Activity 的管理器无法阻止确认框关闭后重新弹键盘。
                clearEditorFocus[0] = { focus.clearFocus(force = true); keyboard?.hide() }
                onDispose { clearEditorFocus[0] = null }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_editor_scroll")
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (profile.type == ProfileType.CUSTOM) {
                    CustomProfileOverview(
                        summary = overview, enabled = !saving,
                        onRules = { openSection { onOpenRulesEditor(profile) } },
                        onResources = { openSection { onOpenProvidersEditor(profile) } },
                        onDns = { openSection { onOpenDnsEditor(profile) } },
                        onMore = { openSection { showMore = true } },
                        onGroups = { openSection { onOpenProxyGroupsEditor(profile) } },
                        onProxies = { openSection { onOpenProxiesEditor(profile) } },
                    )
                    TextButton(onClick = { showYaml = !showYaml }, modifier = Modifier.testTag("custom_profile_toggle_yaml")) {
                        Text(if (showYaml) "收起原始 YAML" else "编辑原始 YAML")
                    }
                } else {
                    Text(
                        "保存后会替换 KClash 本地配置文件，核心启动时仍会再次校验 YAML。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (profile.type != ProfileType.CUSTOM || showYaml) OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().testTag("profile_editor_content"),
                    minLines = if (profile.type == ProfileType.CUSTOM) 3 else 12,
                    maxLines = if (profile.type == ProfileType.CUSTOM) 6 else 20,
                    enabled = !saving,
                    label = { Text("YAML") },
                )
            }
        },
        confirmButton = {
            if (profile.type == ProfileType.CUSTOM) Box {
                        IconButton(onClick = { showImportMenu = true }, enabled = !saving && !customImportState.isLoading,
                            modifier = Modifier.testTag("custom_profile_import")) { Icon(Icons.Outlined.FileDownload, "导入") }
                        DropdownMenu(showImportMenu, { showImportMenu = false }) {
                            DropdownMenuItem(text = { Text("从 URL 导入") }, onClick = { showImportMenu = false; openSection { showCustomImport = true } })
                            DropdownMenuItem(text = { Text("从文件导入") }, onClick = {
                                showImportMenu = false; openSection { onPickCustomImportFile() }
                            })
                        }
                    }
            if (showYaml || profile.type != ProfileType.CUSTOM) TextButton(
                onClick = {
                    clearEditorFocus[0]?.invoke()
                    onSave(content)
                },
                enabled = !saving && content.isNotBlank(),
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
    )

    if (showMore) ProfileEditorPage(
        onDismissRequest = { showMore = false }, backTag = "custom_profile_more_back", title = { Text("更多") }, text = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(24.dp), color = dashboardCardColor()) {
                    Column {
                        ProfileSectionRow("Dialers", overview?.let { if (it.dialers > 0) "${it.dialers} 个代理" else "无" },
                            Icons.Outlined.SwapVert, !saving, Modifier.testTag("custom_profile_dialers")) {
                            openSection { onOpenDialersEditor(profile) }
                        }
                        ProfileSectionRow("NTP", overview?.let { if (it.ntpEnabled) "已启用" else "未启用" },
                            Icons.Outlined.Schedule, !saving, Modifier.testTag("custom_profile_ntp")) {
                            moreSection = "ntp"; onOpenMoreEditor(profile)
                        }
                        ProfileSectionRow("Sniffer", overview?.let { if (it.snifferEnabled) "已启用" else "未启用" },
                            Icons.Outlined.Radar, !saving, Modifier.testTag("custom_profile_sniffer")) {
                            moreSection = "sniffer"; onOpenMoreEditor(profile)
                        }
                        ProfileSectionRow("脚本", if (profile.scriptId == null) "未绑定" else "已绑定", Icons.Outlined.Code,
                            !saving, Modifier.testTag("custom_profile_script")) { openSection { onOpenScriptBinding(profile) } }
                    }
                }
            }
        },
    )
    if (moreEditorState.profile?.id == profile.id) ProfileMoreEditor(moreEditorState, moreSection,
        initialContent, onCloseMoreEditor, onSaveMoreEditor)

    if (customImportState.isLoading) AlertDialog(onDismissRequest = onCancelCustomImport,
        title = { Text("正在读取配置") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onCancelCustomImport) { Text("取消") } })

    if (showCustomImport) AlertDialog(
        onDismissRequest = { if (!customImportState.isLoading) showCustomImport = false },
        title = { Text("从 URL 导入") },
        text = {
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            DisposableEffect(focus, keyboard) {
                clearImportFocus[0] = { focus.clearFocus(force = true); keyboard?.hide() }
                onDispose { clearImportFocus[0] = null }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(importUrl, { importUrl = it }, label = { Text("HTTP/HTTPS 链接") }, singleLine = true,
                    enabled = !customImportState.isLoading, modifier = Modifier.fillMaxWidth().testTag("custom_import_url"))
                AgeSecretKeyField(importAgeKey, { importAgeKey = it }, !customImportState.isLoading, "custom_import_age_key")
                if (customImportState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = importUrl.isNotBlank() && !customImportState.isLoading,
            modifier = Modifier.testTag("custom_import_url_confirm"),
            onClick = { clearImportFocus[0]?.invoke(); showCustomImport = false; onImportCustomUrl(importUrl.trim(), importAgeKey.trim()); importAgeKey = "" }) { Text("读取") } },
        dismissButton = { TextButton(enabled = !customImportState.isLoading, onClick = { showCustomImport = false; importAgeKey = "" }) { Text("取消") } },
    )
    if (customImportState.name != null) AlertDialog(
        onDismissRequest = { if (!saving) onCancelCustomImport() },
        title = { Text("替换自定义配置？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(customImportState.name, style = MaterialTheme.typography.titleSmall)
                val summary = customImportState.summary
                Text("代理 " + (summary?.proxies ?: 0) + " · 策略组 " + (summary?.groups?.size ?: 0) + " · 规则 " + (summary?.rules ?: 0))
                Text("确认后将替换当前代理、规则及 DNS、NTP、Sniffer 设置。配置名称和绑定脚本保留。")
                if (customImportState.droppedSubRules > 0) Text("已忽略 " + customImportState.droppedSubRules + " 条 SUB-RULE 子规则引用，其余规则将继续导入。",
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirmCustomImport, enabled = !saving, modifier = Modifier.testTag("custom_import_replace_confirm")) { Text("替换") } },
        dismissButton = { TextButton(onClick = onCancelCustomImport, enabled = !saving) { Text("取消") } },
    )

    if (showRename) ProfileRenameDialog(profile, saving, { showRename = false }) { label ->
        showRename = false
        onRename(profile, label)
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("退出后，本次编辑内容不会写入配置。") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirmation = false; clearEditorFocus[0]?.invoke(); finishExit() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("继续编辑") }
            },
        )
    }

    if (showSectionDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showSectionDiscardConfirmation = false
                pendingSectionAction = null
            },
            title = { Text("原始 YAML 尚未保存") },
            text = { Text("进入分区编辑会放弃当前 YAML 修改，继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        content = initialContent
                        showSectionDiscardConfirmation = false
                        val action = pendingSectionAction
                        pendingSectionAction = null
                        action?.invoke()
                    },
                ) { Text("放弃并继续") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSectionDiscardConfirmation = false
                        pendingSectionAction = null
                    },
                ) { Text("继续编辑") }
            },
        )
    }

    if (showDraftNameDialog) {
        AlertDialog(
            onDismissRequest = { onFinishNewDraft(null) },
            title = { Text("为配置命名") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    enabled = !saving,
                    label = { Text("配置名称") },
                    modifier = Modifier.testTag("new_profile_name"),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onFinishNewDraft(draftName) },
                    enabled = !saving && draftName.isNotBlank(),
                ) {
                    if (saving) CircularProgressIndicator() else Text("保存名称")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onFinishNewDraft(null) },
                    enabled = !saving,
                ) { Text("保留默认名称") }
            },
        )
    }
}
