package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileProvidersPanel(state: ProfileProvidersUiState, onRetry: () -> Unit, onUpdate: (String) -> Unit,
    onUpdateAll: () -> Unit = {}, onRetryFailed: () -> Unit = {}, onPreview: (String) -> Unit = {},
    onEdit: (String) -> Unit = {}, onUpload: (String) -> Unit = {}) {
    var menu by remember(state.profileId) { mutableStateOf<String?>(null) }
    var subscription by remember(state.profileId) { mutableStateOf<com.lonnnnnng.flclash.core.CoreExternalProvider?>(null) }
    val busy = state.updatingName != null || state.updatingNames.isNotEmpty()
    LaunchedEffect(busy) { if (busy) menu = null }
    Column(Modifier.fillMaxWidth().testTag("profile_providers"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Providers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onUpdateAll, enabled = !busy && state.providers.orEmpty().any { it.canUpdate || it.name in state.managedNames },
                modifier = Modifier.testTag("profile_providers_update_all")) { AppGlyphIcon(AppGlyphs.sync) }
        }
        when {
            state.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().testTag("profile_providers_loading"))
            state.error != null -> {
                Text(tr(state.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_providers_error"))
                TextButton(onClick = onRetry, modifier = Modifier.testTag("profile_providers_retry")) { Text(tr("重试")) }
            }
            state.providers?.isEmpty() == true -> Text(tr("当前配置没有外部 Provider"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profile_providers_empty"))
        }
        if (state.updatingName != null && state.providers?.none { it.name == state.updatingName } == true) {
            Text(tr("正在更新 Provider，请稍候"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.providers.orEmpty().forEachIndexed { index, provider ->
            val menuAnchor = remember(state.profileId, provider.name) { AppContextMenuAnchor() }
            Row(Modifier.fillMaxWidth().testTag("profile_provider_row_" + index)
                // 作者 long：整行长按和更多按钮共用实际触点，菜单不会因滚动或行高变化漂离触发位置。
                .appContextMenuAnchor(menuAnchor, enabled = !busy) { menu = provider.name }
                .combinedClickable(enabled = !busy, onClick = { onPreview(provider.name) }, onLongClick = {
                    menuAnchor.open(); menu = provider.name
                }), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(provider.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text((if (provider.type == "Proxy") tr("代理 · ") else tr("规则 · ")) + provider.count + tr(" 条 · ") + provider.vehicleType,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(provider.updatedAtMillis?.let { tr("更新于 ") + DateUtils.getRelativeTimeSpanString(it) } ?: tr("尚未更新"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (provider.name in state.failedNames) Text(tr(state.failedMessages[provider.name] ?: "更新失败，请重试"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("profile_provider_failure_$index"))
                }
                if (state.updatingName == provider.name || provider.name in state.updatingNames) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp).testTag("profile_provider_updating_" + index), strokeWidth = 2.dp)
                } else if (provider.canUpdate || provider.name in state.managedNames) {
                    // 作者 long：应用登记的远程规则在核心表现为文件，仍经资源仓库同步；普通文件不显示在线更新。
                    IconButton(onClick = { onUpdate(provider.name) }, enabled = !busy,
                        modifier = Modifier.testTag("profile_provider_update_" + index)) {
                        AppGlyphIcon(AppGlyphs.sync)
                    }
                }
                IconButton({ menuAnchor.open(); menu = provider.name }, enabled = !busy, modifier = Modifier.testTag("profile_provider_menu_$index")) {
                    AppGlyphIcon(AppGlyphs.more)
                }
            }
            AppDropdownMenu(menu == provider.name && !busy, { menu = null }, anchor = menuAnchor.point, carriedPointer = menuAnchor.carry,
                placement = AppMenuPlacement.BELOW_POINT, avoid = menuAnchor.bounds,
                modifier = Modifier.testTag("profile_provider_actions_$index")) {
                AppDropdownMenuItem(text = { Text(tr("预览")) }, onClick = { menu = null; onPreview(provider.name) })
                AppDropdownMenuItem(text = { Text(tr("编辑")) }, onClick = { menu = null; onEdit(provider.name) },
                    modifier = Modifier.testTag("profile_provider_edit_$index"))
                AppDropdownMenuItem(text = { Text(tr("从文件导入")) }, onClick = { menu = null; onUpload(provider.name) },
                    modifier = Modifier.testTag("profile_provider_upload_$index"))
                if (provider.canUpdate || provider.name in state.managedNames) AppDropdownMenuItem(
                    text = { Text(tr("同步")) }, onClick = { menu = null; onUpdate(provider.name) },
                )
                if (provider.subscriptionInfo != null) AppDropdownMenuItem(text = { Text(tr("订阅用量")) },
                    onClick = { menu = null; subscription = provider },
                    modifier = Modifier.testTag("profile_provider_subscription_$index"))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        state.updateMessage?.let { message ->
            Text(tr(message), color = if (state.updateFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("profile_provider_update_message"))
        }
        if (state.failedNames.isNotEmpty()) TextButton(onRetryFailed, enabled = !busy, modifier = Modifier.testTag("profile_providers_retry_failed")) { Text(tr("重试失败项")) }
    }
    subscription?.let { provider -> provider.subscriptionInfo?.let { info ->
        AlertDialog(onDismissRequest = { subscription = null }, title = { Text(provider.name) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("已用 {0} / {1}", formatBytes(info.used), formatBytes(info.total)))
                LinearProgressIndicator(progress = { info.usage }, modifier = Modifier.fillMaxWidth())
                Text(tr("上传 {0}", formatBytes(info.upload)))
                Text(tr("下载 {0}", formatBytes(info.download)))
                Text(info.expireAtMillis?.let { tr("到期 {0}", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))) } ?: tr("不限到期时间"))
            }
        }, confirmButton = { TextButton({ subscription = null }) { Text(tr("确定")) } })
    } }
}
