package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.BackupRestoreScope
import com.lonnnnnng.flclash.data.BackupRestoreStrategy
import com.lonnnnnng.flclash.data.WebDavCredentials

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
) {
    var restore by remember { mutableStateOf(false) }
    var webDav by remember { mutableStateOf(false) }
    var downloadCredentials by remember { mutableStateOf<WebDavCredentials?>(null) }
    ProfileEditorPage(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("备份与恢复") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("tools_backup_page"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileFormSection {
                ProfileSectionRow("导出备份", icon = Icons.Outlined.Backup, enabled = !busy,
                    modifier = Modifier.testTag("backup_export"), onClick = onExport)
                ProfileSectionRow("从文件恢复", icon = Icons.Outlined.Restore, enabled = canRestore && !busy,
                    modifier = Modifier.testTag("backup_restore"), onClick = { restore = true })
            }
            ProfileFormSection {
                ProfileSectionRow("WebDAV", icon = Icons.Outlined.Cloud, enabled = !busy,
                    modifier = Modifier.testTag("webdav_open"), onClick = { webDav = true })
            }
        }
    })
    if (restore) BackupRestoreOptionsDialog(onDismiss = { restore = false }, onConfirm = { scope, strategy ->
        restore = false
        onImport(scope, strategy)
    })
    if (webDav) WebDavBackupDialog(initial = credentials, busy = busy,
        onDismiss = { if (!busy) webDav = false }, onSave = onSaveCredentials,
        onClear = { onClearCredentials(); webDav = false }, onProbe = onProbe, onUpload = onUpload,
        onDownload = { downloadCredentials = it; webDav = false })
    downloadCredentials?.let { selected ->
        BackupRestoreOptionsDialog(title = "从 WebDAV 恢复", confirmLabel = "下载并恢复",
            onDismiss = { downloadCredentials = null }, onConfirm = { scope, strategy ->
                downloadCredentials = null
                onDownload(selected, scope, strategy)
            })
    }
}
