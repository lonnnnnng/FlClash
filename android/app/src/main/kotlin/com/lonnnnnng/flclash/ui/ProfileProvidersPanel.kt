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
    Column(Modifier.fillMaxWidth().testTag("profile_providers"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Providers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onUpdateAll, enabled = !busy && state.providers.orEmpty().any { it.canUpdate || it.name in state.managedNames },
                modifier = Modifier.testTag("profile_providers_update_all")) { Icon(Icons.Outlined.Sync, "全部更新") }
        }
        when {
            state.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().testTag("profile_providers_loading"))
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_providers_error"))
                TextButton(onClick = onRetry, modifier = Modifier.testTag("profile_providers_retry")) { Text("重试") }
            }
            state.providers?.isEmpty() == true -> Text("当前配置没有外部 Provider", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("profile_providers_empty"))
        }
        if (state.updatingName != null && state.providers?.none { it.name == state.updatingName } == true) {
            Text("正在更新 Provider，请稍候", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.providers.orEmpty().forEachIndexed { index, provider ->
            Row(Modifier.fillMaxWidth().testTag("profile_provider_row_" + index)
                .combinedClickable(enabled = !busy, onClick = { onPreview(provider.name) }, onLongClick = { menu = provider.name }), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(provider.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text((if (provider.type == "Proxy") "代理 · " else "规则 · ") + provider.count + " 条 · " + provider.vehicleType,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(provider.updatedAtMillis?.let { "更新于 " + DateUtils.getRelativeTimeSpanString(it) } ?: "尚未更新",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.updatingName == provider.name || provider.name in state.updatingNames) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp).testTag("profile_provider_updating_" + index), strokeWidth = 2.dp)
                } else if (provider.canUpdate || provider.name in state.managedNames) {
                    // 作者 long：应用登记的远程规则在核心表现为文件，仍经资源仓库同步；普通文件不显示在线更新。
                    IconButton(onClick = { onUpdate(provider.name) }, enabled = !busy,
                        modifier = Modifier.testTag("profile_provider_update_" + index)) {
                        Icon(Icons.Outlined.Sync, contentDescription = "更新 " + provider.name)
                    }
                }
                Box {
                    IconButton({ menu = provider.name }, enabled = !busy, modifier = Modifier.testTag("profile_provider_menu_$index")) {
                        Icon(Icons.Outlined.MoreVert, "${provider.name} 更多操作")
                    }
                    DropdownMenu(menu == provider.name, { menu = null }) {
                        DropdownMenuItem(text = { Text("预览") }, onClick = { menu = null; onPreview(provider.name) })
                        DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = null; onEdit(provider.name) }, modifier = Modifier.testTag("profile_provider_edit_$index"))
                        DropdownMenuItem(text = { Text("从文件导入") }, onClick = { menu = null; onUpload(provider.name) }, modifier = Modifier.testTag("profile_provider_upload_$index"))
                        if (provider.canUpdate || provider.name in state.managedNames) DropdownMenuItem(text = { Text("同步") }, onClick = { menu = null; onUpdate(provider.name) })
                        if (provider.subscriptionInfo != null) DropdownMenuItem(text = { Text("订阅用量") }, onClick = { menu = null; subscription = provider }, modifier = Modifier.testTag("profile_provider_subscription_$index"))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        state.updateMessage?.let { message ->
            Text(message, color = if (state.updateFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("profile_provider_update_message"))
        }
        if (state.failedNames.isNotEmpty()) TextButton(onRetryFailed, enabled = !busy, modifier = Modifier.testTag("profile_providers_retry_failed")) { Text("重试失败项") }
    }
    subscription?.let { provider -> provider.subscriptionInfo?.let { info ->
        AlertDialog(onDismissRequest = { subscription = null }, title = { Text(provider.name) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("已用 ${formatBytes(info.used)} / ${formatBytes(info.total)}")
                LinearProgressIndicator(progress = { info.usage }, modifier = Modifier.fillMaxWidth())
                Text("上传 ${formatBytes(info.upload)}")
                Text("下载 ${formatBytes(info.download)}")
                Text(info.expireAtMillis?.let { "到期 ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}" } ?: "不限到期时间")
            }
        }, confirmButton = { TextButton({ subscription = null }) { Text("确定") } })
    } }
}
