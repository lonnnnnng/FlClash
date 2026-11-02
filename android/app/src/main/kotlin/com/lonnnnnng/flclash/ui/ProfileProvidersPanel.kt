package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileProvidersPanel(state: ProfileProvidersUiState, onRetry: () -> Unit, onUpdate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("profile_providers"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Providers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
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
            Row(Modifier.fillMaxWidth().testTag("profile_provider_row_" + index), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(provider.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text((if (provider.type == "Proxy") "代理 · " else "规则 · ") + provider.count + " 条 · " + provider.vehicleType,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(provider.updatedAtMillis?.let { "更新于 " + DateUtils.getRelativeTimeSpanString(it) } ?: "尚未更新",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.updatingName == provider.name) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp).testTag("profile_provider_updating_" + index), strokeWidth = 2.dp)
                } else if (provider.canUpdate) {
                    // 作者 long：仅 HTTP 资源提供下载入口，文件 Provider 保留可读明细，不能误触发在线更新。
                    IconButton(onClick = { onUpdate(provider.name) }, enabled = state.updatingName == null,
                        modifier = Modifier.testTag("profile_provider_update_" + index)) {
                        Icon(Icons.Outlined.Sync, contentDescription = "更新 " + provider.name)
                    }
                } else Text("只读", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        state.updateMessage?.let { message ->
            Text(message, color = if (state.updateFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("profile_provider_update_message"))
        }
    }
}
