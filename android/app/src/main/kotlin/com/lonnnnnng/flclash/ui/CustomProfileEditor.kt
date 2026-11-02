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
) {
    val initialDraftLabel by rememberSaveable(profile.id) { mutableStateOf(profile.label) }
    var showTitleActions by remember { mutableStateOf(false) }
    var showRename by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showMore by rememberSaveable(profile.id) { mutableStateOf(false) }
    var content by rememberSaveable(profile.id, initialContent) { mutableStateOf(initialContent) }
    var showDiscardConfirmation by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showSectionDiscardConfirmation by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showDraftNameDialog by rememberSaveable(profile.id) { mutableStateOf(false) }
    var draftName by rememberSaveable(profile.id) { mutableStateOf(profile.label) }
    var showYaml by rememberSaveable(profile.id) { mutableStateOf(false) }
    var pendingSectionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isDirty = content != initialContent
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

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
        if (saving) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (isDirty) showDiscardConfirmation = true else finishExit()
    }

    fun openSection(action: () -> Unit) {
        // 作者 long：进入分区或确认弹窗前释放 YAML 输入焦点，避免键盘恢复焦点后改变点击位置。
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
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
            if (showYaml || profile.type != ProfileType.CUSTOM) TextButton(
                onClick = {
                    keyboardController?.hide()
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
                        ProfileSectionRow("脚本", if (profile.scriptId == null) "未绑定" else "已绑定", Icons.Outlined.Code,
                            !saving, Modifier.testTag("custom_profile_script")) { openSection { onOpenScriptBinding(profile) } }
                    }
                }
            }
        },
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
                TextButton(onClick = { showDiscardConfirmation = false; keyboardController?.hide(); finishExit() }) { Text("放弃") }
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
