package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.saveable.rememberSaveable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.lonnnnnng.flclash.data.BackupRestoreScope
import com.lonnnnnng.flclash.data.BackupRestoreStrategy
import com.lonnnnnng.flclash.data.WebDavCredentials

enum class WebDavConnectionState(val label: String) { UNKNOWN("未测试"), CHECKING("连接中"), CONNECTED("连接成功"), FAILED("连接失败") }

/** 作者 long：备份操作属于工具设置；迁移入口时复用既有 SAF/WebDAV 保存链，避免功能随配置页菜单收敛而丢失。 */
@Composable
internal fun ProfileMaintenancePage(
    canRestore: Boolean,
    busy: Boolean,
    credentials: WebDavCredentials?,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: (BackupRestoreScope, BackupRestoreStrategy) -> Unit,
    onSaveCredentials: (WebDavCredentials) -> Unit,
    onClearCredentials: () -> Unit,
    onProbe: (WebDavCredentials) -> Unit,
    onUpload: (WebDavCredentials, BackupRestoreScope) -> Unit,
    onDownload: (WebDavCredentials, BackupRestoreScope, BackupRestoreStrategy) -> Unit,
    strategy: BackupRestoreStrategy = BackupRestoreStrategy.COMPATIBLE,
    onStrategy: (BackupRestoreStrategy) -> Unit = {},
    connection: WebDavConnectionState = WebDavConnectionState.UNKNOWN,
) {
    var restore by rememberSaveable { mutableStateOf<String?>(null) }
    var webDav by rememberSaveable { mutableStateOf(false) }
    var uploading by rememberSaveable { mutableStateOf(false) }
    var pickingStrategy by rememberSaveable { mutableStateOf(false) }
    var fileName by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(credentials, busy) { if (!busy && credentials != null && connection == WebDavConnectionState.UNKNOWN) onProbe(credentials) }
    ProfileEditorPage(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("备份与恢复") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("tools_backup_page"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("远程", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ProfileFormSection {
                ProfileSectionRow(credentials?.username ?: "尚未绑定 WebDAV", icon = Icons.Outlined.Cloud, enabled = !busy,
                    modifier = Modifier.testTag("webdav_open"), onClick = { webDav = true })
                if (credentials != null) {
                    Text("连接状态：${connection.label}", modifier = Modifier.testTag("webdav_connection"), color = if (connection == WebDavConnectionState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    ProfileSectionRow("文件：${credentials.fileName}", icon = Icons.Outlined.Description, enabled = !busy, modifier = Modifier.testTag("webdav_file"), onClick = { fileName = true })
                    ProfileSectionRow("备份到 WebDAV", icon = Icons.Outlined.CloudUpload, enabled = !busy, modifier = Modifier.testTag("backup_remote_export"), onClick = { uploading = true })
                    ProfileSectionRow("从 WebDAV 恢复", icon = Icons.Outlined.CloudDownload, enabled = canRestore && !busy, modifier = Modifier.testTag("backup_remote_restore"), onClick = { restore = "remote" })
                    TextButton({ onProbe(credentials) }, enabled = !busy, modifier = Modifier.testTag("webdav_probe")) { Text("重新测试连接") }
                } else Text("绑定账号后可以手动上传和恢复备份。", style = MaterialTheme.typography.bodySmall)
            }
            Text("本地", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ProfileFormSection {
                ProfileSectionRow("导出备份", icon = Icons.Outlined.Backup, enabled = !busy,
                    modifier = Modifier.testTag("backup_export"), onClick = onExport)
                ProfileSectionRow("从文件恢复", icon = Icons.Outlined.Restore, enabled = canRestore && !busy,
                    modifier = Modifier.testTag("backup_restore"), onClick = { restore = "local" })
                Text("完整备份包含配置、资源和常规/网络/DNS设置，含节点与入站凭据。请妥善保管备份文件。", style = MaterialTheme.typography.bodySmall)
            }
            Text("选项", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ProfileFormSection {
                ProfileSectionRow("恢复策略：${if (strategy == BackupRestoreStrategy.COMPATIBLE) "兼容" else "覆盖"}", icon = Icons.Outlined.Settings, enabled = !busy,
                    modifier = Modifier.testTag("backup_strategy"), onClick = { pickingStrategy = true })
                Text(if (strategy == BackupRestoreStrategy.COMPATIBLE) "保留归档中未包含的数据；归档内同 ID 数据和全局设置使用备份值。" else "替换所选范围内的数据。", style = MaterialTheme.typography.bodySmall)
            }
            if (!canRestore) Text("请先停止服务并等待配置操作完成后恢复备份。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("backup_busy"))
        }
    })
    if (webDav) WebDavAccountPage(credentials, busy, { webDav = false }, onSaveCredentials, onClearCredentials)
    if (pickingStrategy) AlertDialog(onDismissRequest = { pickingStrategy = false }, title = { Text("恢复策略") }, text = {
        Column { BackupRestoreStrategy.entries.forEach { item -> TextButton({ onStrategy(item); pickingStrategy = false }, modifier = Modifier.testTag("backup_strategy_${item.name}")) {
            Text(if (item == BackupRestoreStrategy.COMPATIBLE) "兼容：保留未包含的数据" else "覆盖：替换当前数据")
        } } }
    }, confirmButton = { TextButton({ pickingStrategy = false }) { Text("取消") } })
    restore?.let { source -> RestoreScopeDialog(busy, strategy, { restore = null }) { scope ->
        restore = null
        if (source == "local") onImport(scope, strategy) else credentials?.let { onDownload(it, scope, strategy) }
    } }
    if (uploading) AlertDialog(onDismissRequest = { uploading = false }, title = { Text("覆盖远程备份？") },
        text = { Text("上传将替换远程文件“${credentials?.fileName.orEmpty()}”。") }, confirmButton = { TextButton({
            uploading = false; credentials?.let { onUpload(it, BackupRestoreScope.ALL_DATA) }
        }, enabled = !busy, modifier = Modifier.testTag("webdav_upload_confirm")) { Text("确认覆盖") } }, dismissButton = { TextButton({ uploading = false }) { Text("取消") } })
    if (fileName && credentials != null) WebDavFileNameDialog(credentials, busy, { fileName = false }, onSaveCredentials)
}

@Composable
private fun RestoreScopeDialog(busy: Boolean, strategy: BackupRestoreStrategy, onDismiss: () -> Unit, onRestore: (BackupRestoreScope) -> Unit) {
    var scope by rememberSaveable { mutableStateOf(BackupRestoreScope.ALL_DATA) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("恢复备份") }, text = {
        Column { BackupRestoreScope.entries.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(scope == item, { scope = item }, enabled = !busy)
                TextButton({ scope = item }, enabled = !busy, modifier = Modifier.testTag("backup_scope_${item.name}")) {
                    Text(if (item == BackupRestoreScope.PROFILES_ONLY) "仅恢复配置文件" else "恢复全部数据")
                }
            }
        }; Text(if (strategy == BackupRestoreStrategy.OVERRIDE) "覆盖恢复将替换所选范围内的现有数据。" else "兼容恢复会保留归档未包含的数据。") }
    }, confirmButton = { TextButton({ onRestore(scope) }, enabled = !busy, modifier = Modifier.testTag("backup_restore_confirm")) { Text("继续") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } })
}

@Composable
private fun WebDavAccountPage(initial: WebDavCredentials?, busy: Boolean, onDismiss: () -> Unit, onSave: (WebDavCredentials) -> Unit, onClear: () -> Unit) {
    // 作者 long：密码只保留在当前编辑会话，不进rememberSaveable/系统状态；后台由既有凭据存储保留已确认值。
    var address by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var revealed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<WebDavCredentials?>(null) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(initial, busy, pending, deleting) { if (!busy && (pending != null && initial == pending || deleting && initial == null)) onDismiss() }
    ProfileEditorPage({ if (!busy) onDismiss() }, { Text("WebDAV 配置") }, {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("webdav_account_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("webdav_account_error")) }
            OutlinedTextField(address, { if (it.length <= 4096) { address = it; error = null } }, label = { Text("WebDAV 地址") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("webdav_base_url"))
            OutlinedTextField(username, { if (it.length <= 1024) { username = it; error = null } }, label = { Text("账号") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("webdav_username"))
            // 作者 long：隐藏字符本身不能阻止输入法候选回显，密码键盘同时关闭普通文本的候选与学习行为。
            OutlinedTextField(password, { if (it.length <= 1024) { password = it; error = null } }, label = { Text("密码") }, enabled = !busy, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ revealed = !revealed }) { androidx.compose.material3.Icon(Icons.Outlined.Visibility, "显示或隐藏密码") } },
                modifier = Modifier.fillMaxWidth().testTag("webdav_password"))
            Text("填写 WebDAV 目录地址。账号保存在本机，备份文件不包含 WebDAV 登录凭据。", style = MaterialTheme.typography.bodySmall)
            if (initial != null) TextButton({ deleting = true; onClear() }, enabled = !busy, modifier = Modifier.testTag("webdav_clear")) { Text("删除账号") }
        }
    }, confirmButton = { TextButton({
        val url = address.trim().toHttpUrlOrNull()
        if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null || username.isBlank() || password.isEmpty()) {
            error = "请填写有效的目录地址、账号和密码"; return@TextButton
        }
        val next = WebDavCredentials(address.trim(), username.trim(), password, initial?.fileName ?: com.lonnnnnng.flclash.data.DEFAULT_WEBDAV_FILE_NAME)
        pending = next; onSave(next)
    }, enabled = !busy, modifier = Modifier.testTag("webdav_save")) { Text("保存") } }, backTag = "webdav_account_back")
}

@Composable
private fun WebDavFileNameDialog(initial: WebDavCredentials, busy: Boolean, onDismiss: () -> Unit, onSave: (WebDavCredentials) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial.fileName) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(initial, busy, pending) { if (!busy && pending && initial.fileName == value) onDismiss() }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("远程文件名") }, text = {
        Column { OutlinedTextField(value, { if (it.length <= 128) value = it }, singleLine = true, enabled = !busy, modifier = Modifier.testTag("webdav_file_name"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton({
        if (!value.matches(Regex("[A-Za-z0-9._-]{1,128}"))) error = "文件名只允许字母、数字、点、下划线和短横线"
        else { pending = true; onSave(initial.copy(fileName = value)) }
    }, enabled = !busy, modifier = Modifier.testTag("webdav_file_save")) { Text("保存") } }, dismissButton = {
        Row { TextButton({ value = com.lonnnnnng.flclash.data.DEFAULT_WEBDAV_FILE_NAME }, enabled = !busy) { Text("恢复默认") }; TextButton(onDismiss, enabled = !busy) { Text("取消") } }
    })
}
