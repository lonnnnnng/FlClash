package com.lonnnnnng.flclash.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.GeoResourceSettings
import com.lonnnnnng.flclash.service.GeoResourceType

/** 作者 long：工具资源页显示下载状态，资源定义的增删改留在自定义资源页，保持与 Flutter 相同的入口归属。 */
@Composable
internal fun ResourcesPage(state: ResourceUpdatesState, onDismiss: () -> Unit, onRefresh: () -> Unit,
    onSync: (ResourceUpdateRow) -> Unit, onSyncAll: () -> Unit, onSettings: (GeoResourceSettings) -> Unit,
    onClearFeedback: () -> Unit) {
    val context = LocalContext.current
    var editingGeo by rememberSaveable { mutableStateOf<String?>(null) }
    var editingInterval by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefresh() }
    ProfileEditorPage(onDismissRequest = onDismiss, title = { Text(tr("资源")) }, backTag = "resources_back",
        confirmButton = {
            IconButton(onClick = onSyncAll, enabled = !state.loading && state.updating.isEmpty(), modifier = Modifier.testTag("resources_update_all")) {
                if (state.updating.isNotEmpty()) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Sync, null)
            }
        }, text = {
            LazyColumn(Modifier.fillMaxSize().testTag("resources_page"), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("自动更新"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Switch(state.settings.autoUpdate, { onSettings(state.settings.copy(autoUpdate = it)) },
                                    enabled = !state.settingsBusy && state.updating.isEmpty(), modifier = Modifier.testTag("resources_auto_update"))
                            }
                            if (state.settings.autoUpdate) ListItem(
                                headlineContent = { Text(tr("资源更新周期")) }, supportingContent = { Text(tr("{0} 小时", state.settings.intervalHours)) },
                                modifier = Modifier.combinedClickable(enabled = !state.settingsBusy && state.updating.isEmpty(), onClick = { editingInterval = true }),
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                        }
                    }
                }
                if (state.settingsBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) }
                state.message?.let { message -> item { Text(tr(message), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary) } }
                if (state.failures.isNotEmpty()) item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("resources_failures")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tr("{0} 项操作未完成", state.failures.size), style = MaterialTheme.typography.titleSmall)
                            state.failures.forEach { Text("${it.label}：${tr(it.message)}", style = MaterialTheme.typography.bodyMedium) }
                            TextButton(onClick = onClearFeedback) { Text(tr("关闭提示")) }
                        }
                    }
                }
                ResourceKind.entries.forEach { kind ->
                    val rows = state.rows.filter { it.kind == kind }
                    if (rows.isNotEmpty()) {
                        item(key = "header_$kind") { Text(when (kind) {
                            ResourceKind.GEO -> tr("Geo 资源"); ResourceKind.ICONS -> tr("图标集"); ResourceKind.RULE -> tr("规则资源"); ResourceKind.SCRIPT -> tr("脚本")
                        }, Modifier.padding(top = 24.dp, bottom = 8.dp, start = 12.dp), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary) }
                        items(rows, key = { it.key }) { row ->
                            ResourceOverviewRow(row, row.key in state.updating, onSync = { onSync(row) },
                                onEdit = if (kind == ResourceKind.GEO) ({ editingGeo = row.id }) else null,
                                onCopy = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText(tr("资源链接"), row.url)) })
                        }
                    }
                }
            }
        })
    if (editingInterval) ResourceInputDialog(tr("资源更新周期"), state.settings.intervalHours.toString(), tr("小时"), null,
        onDismiss = { editingInterval = false }, onConfirm = { value ->
            val number = value.toIntOrNull() ?: error(tr("请输入小时数"))
            val next = state.settings.copy(intervalHours = number).validated()
            onSettings(next); editingInterval = false
        }, numeric = true)
    editingGeo?.let { id ->
        val type = GeoResourceType.valueOf(id)
        ResourceInputDialog(type.name, state.settings.urls.getValue(type), tr("下载地址"), type.defaultUrl,
            onDismiss = { editingGeo = null }, onConfirm = { url ->
                val next = state.settings.copy(urls = state.settings.urls + (type to url)).validated()
                onSettings(next); editingGeo = null
            })
    }
}

@Composable
private fun ResourceOverviewRow(row: ResourceUpdateRow, updating: Boolean, onSync: () -> Unit, onEdit: (() -> Unit)?, onCopy: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box {
            Row(Modifier.fillMaxWidth().testTag("resource_${row.key}").combinedClickable(
                onClick = { onEdit?.invoke() ?: run { menu = true } }, onLongClick = { menu = true }).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.updatedAt?.let { tr("更新于 {0}", DateUtils.getRelativeTimeSpanString(it)) } ?: tr("未知"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (updating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else (row.count?.let { "$it" } ?: row.size?.let { Formatter.formatShortFileSize(context, it) })?.let { size ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                        Text(size, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            DropdownMenu(menu, { menu = false }) {
                if (!updating) DropdownMenuItem(text = { Text(tr("同步")) }, leadingIcon = { Icon(Icons.Outlined.Sync, null) }, onClick = { menu = false; onSync() })
                if (onEdit != null && !updating) DropdownMenuItem(text = { Text(tr("编辑")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(tr("复制链接")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; onCopy() })
            }
        }
    }
}

@Composable
internal fun ResourceInputDialog(title: String, initial: String, label: String, defaultValue: String?, onDismiss: () -> Unit,
    onConfirm: (String) -> Unit, numeric: Boolean = false) {
    var value by rememberSaveable(title) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, { value = it; error = null }, Modifier.fillMaxWidth().testTag("resource_setting_input"),
                label = { Text(label) }, singleLine = true, isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Uri),
                supportingText = { error?.let { Text(tr(it)) } })
            if (defaultValue != null) TextButton(onClick = { value = defaultValue; error = null }, modifier = Modifier.testTag("resource_setting_reset")) { Text(tr("恢复默认")) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { onConfirm(value.trim()) } catch (failure: IllegalArgumentException) { error = failure.message ?: "输入无效" }
        catch (_: Exception) { error = "输入无效，请检查后重试" }
    }, modifier = Modifier.testTag("resource_setting_save")) { Text(tr("保存")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } })
}
