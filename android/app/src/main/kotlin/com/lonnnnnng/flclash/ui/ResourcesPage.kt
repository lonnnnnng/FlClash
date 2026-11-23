package com.lonnnnnng.flclash.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.input.TextFieldValue
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
    ProfileEditorPage(onDismissRequest = onDismiss, title = { Text(tr("资源"), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleMedium) }, backTag = "resources_back",
        toolbarHeight = 72.dp, navigationButtonSurface = true, navigationButtonSize = 40.dp,
        confirmButton = {
            IconButton(onClick = onSyncAll, enabled = !state.loading && state.updating.isEmpty(), modifier = Modifier.padding(end = 16.dp).size(44.dp).background(dashboardCardColor(), CircleShape).testTag("resources_update_all")) {
                if (state.updating.isNotEmpty()) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else AppGlyphIcon(AppGlyphs.sync)
            }
        }, text = {
            LazyColumn(Modifier.fillMaxSize().testTag("resources_page"), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                        val enabled = !state.settingsBusy && state.updating.isEmpty()
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled) {
                            onSettings(state.settings.copy(autoUpdate = !state.settings.autoUpdate))
                        }.padding(horizontal = 16.dp).testTag("resources_auto_update_row"), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("自动更新"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(state.settings.autoUpdate, { onSettings(state.settings.copy(autoUpdate = it)) },
                                enabled = enabled, modifier = Modifier.testTag("resources_auto_update"))
                        }
                        if (state.settings.autoUpdate) {
                            ProfileFormDivider()
                            Column(Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(enabled) { editingInterval = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(tr("资源更新周期"), style = MaterialTheme.typography.bodyLarge)
                                Text(tr("{0} 小时", state.settings.intervalHours), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (state.settingsBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) }
                state.message?.let { message -> item { Text(tr(message), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary) } }
                val globalFailures = state.failures.filter { it.rowKey == null }
                if (globalFailures.isNotEmpty()) item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("resources_failures")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tr("{0} 项操作未完成", globalFailures.size), style = MaterialTheme.typography.titleSmall)
                            globalFailures.forEach { Text("${it.label}：${tr(it.message)}", style = MaterialTheme.typography.bodyMedium) }
                            TextButton(onClick = onClearFeedback) { Text(tr("关闭提示")) }
                        }
                    }
                }
                ResourceKind.entries.forEach { kind ->
                    val rows = state.rows.filter { it.kind == kind }
                    if (rows.isNotEmpty()) {
                        item(key = "header_$kind") { Text(when (kind) {
                            ResourceKind.GEO -> "Geo"; ResourceKind.ICONS -> tr("图标集"); ResourceKind.RULE -> tr("规则资源"); ResourceKind.SCRIPT -> tr("脚本")
                        }, Modifier.padding(top = 24.dp, bottom = 13.dp, start = 16.dp), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            ResourceOverviewRow(row, row.key in state.updating, state.transfers[row.key],
                                state.failures.firstOrNull { it.rowKey == row.key }, index == 0, index == rows.lastIndex, onSync = { onSync(row) },
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
            onSettings(next)
        }, numeric = true)
    editingGeo?.let { id ->
        val type = GeoResourceType.valueOf(id)
        ResourceInputDialog(type.name, state.settings.urls.getValue(type), tr("下载地址"), type.defaultUrl,
            onDismiss = { editingGeo = null }, onConfirm = { url ->
                val next = state.settings.copy(urls = state.settings.urls + (type to url)).validated()
                onSettings(next)
            })
    }
}

@Composable
private fun ResourceOverviewRow(row: ResourceUpdateRow, updating: Boolean, progress: ResourceDownloadProgress?, failure: ResourceUpdateFailure?,
    first: Boolean, last: Boolean, onSync: () -> Unit, onEdit: (() -> Unit)?, onCopy: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val anchor = remember(row.key) { AppContextMenuAnchor() }
    // 作者 long：每行仍由Lazy列表独立回收，只在分组首尾裁圆角；同步状态不能改变卡片边界或挤动邻行。
    Surface(color = dashboardCardColor(), shape = AppSuperellipseShape(
        topStart = if (first) 24.dp else 0.dp, topEnd = if (first) 24.dp else 0.dp,
        bottomStart = if (last) 24.dp else 0.dp, bottomEnd = if (last) 24.dp else 0.dp)) {
        Box {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag("resource_${row.key}")
                    .appContextMenuAnchor(anchor) { menu = true }.combinedClickable(
                    onClick = { onEdit?.invoke() ?: run { anchor.open(fromKeyboard = true); menu = true } },
                    onLongClick = { anchor.open(); menu = true }).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(row.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.updatedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: tr("未知"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (progress != null) {
                        val downloaded = Formatter.formatShortFileSize(context, progress.downloadedBytes)
                        val total = progress.totalBytes
                        val fraction = progress.fraction
                        Text(if (total != null && fraction != null)
                            tr("已下载 {0} / {1}", downloaded, Formatter.formatShortFileSize(context, total))
                            else tr("已下载 {0}", downloaded), Modifier.widthIn(max = 180.dp).testTag("resource_transfer_label_${row.key}"),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    } else if (updating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else (row.count?.let { "$it" } ?: row.size?.let { Formatter.formatShortFileSize(context, it) })?.let { size ->
                        Surface(color = dashboardCardColor(), shape = MaterialTheme.shapes.small) {
                            Text(size, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (progress != null) {
                    val fraction = progress.fraction
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp)
                        .testTag("resource_progress_${row.key}"))
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp)
                        .testTag("resource_progress_${row.key}"))
                }
                if (failure != null) {
                    Text(tr(failure.message), Modifier.fillMaxWidth().testTag("resource_error_${row.key}")
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (!last) HorizontalDivider(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            AppDropdownMenu(menu, { menu = false }, anchor = anchor.point, carriedPointer = anchor.carry,
                placement = AppMenuPlacement.BELOW_POINT, avoid = anchor.bounds) {
                if (!updating) AppDropdownMenuItem(text = { Text(tr("同步")) }, leadingIcon = { AppGlyphIcon(AppGlyphs.sync) },
                    onClick = { menu = false; onSync() })
                if (onEdit != null && !updating) AppDropdownMenuItem(text = { Text(tr("编辑")) }, leadingIcon = { AppGlyphIcon(AppGlyphs.edit) },
                    onClick = { menu = false; onEdit() })
                AppDropdownMenuItem(text = { Text(tr("复制链接")) }, leadingIcon = { AppGlyphIcon(AppGlyphs.copy) },
                    onClick = { menu = false; onCopy() })
            }
        }
    }
}

@Composable
internal fun ResourceInputDialog(title: String, initial: String, label: String, defaultValue: String?, onDismiss: () -> Unit,
    onConfirm: (String) -> Unit, numeric: Boolean = false) {
    var value by rememberSaveable(title, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial)) }
    var error by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var accepted by rememberSaveable(title) { mutableStateOf(false) }
    val confirm by rememberUpdatedState(onConfirm)
    fun submit(input: String) {
        if (accepted) return
        try {
            // 作者 long：提交在点击时受理一次；退出只保留外观，不在动画结束或Activity重建时再次写资源设置。
            confirm(input.trim())
            accepted = true
        } catch (failure: IllegalArgumentException) { error = failure.message ?: "输入无效" }
        catch (_: Exception) { error = "输入无效，请检查后重试" }
    }
    val canReset = defaultValue != null && value.text != defaultValue
    FloatingInputDialog(title = title, tag = "resource_setting_dialog", busy = false, confirmEnabled = true,
        onDismiss = onDismiss, onConfirm = { submit(value.text) }, confirmLabel = tr("保存"),
        cancelLabel = if (canReset) tr("恢复默认") else tr("取消"), error = error,
        errorTag = "resource_setting_error", confirmTag = "resource_setting_save",
        externallyClosing = accepted, onExitComplete = onDismiss,
        // 作者 long：与上游InputDialog一致，恢复默认直接提交默认地址；Back和点击遮罩仍只取消，不能误重置。
        cancelButtonAction = if (canReset) ({ submit(checkNotNull(defaultValue)) }) else null,
        cancelTag = if (canReset) "resource_setting_reset" else "resource_setting_cancel",
    ) { enabled, save ->
        FloatingInputField(value, { next ->
            if (next.text != value.text) error = null
            value = next
        }, "resource_setting_input", enabled, save, placeholder = if (numeric) "" else label, invalid = error != null,
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Uri, suffix = if (numeric) label else null)
    }
}
