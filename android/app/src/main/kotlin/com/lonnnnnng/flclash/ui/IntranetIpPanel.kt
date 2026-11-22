package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun DashboardIntranetIpCard(state: IntranetIpUiState, onAllowLan: (Boolean) -> Unit, onCopy: () -> Unit, onRetry: () -> Unit) {
    DashboardSurface(modifier = Modifier.testTag("dashboard_intranet_card")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Devices, contentDescription = null)
            Text(tr("内网 IP"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy, enabled = state.copyText != null && !state.saving,
                modifier = Modifier.testTag("intranet_copy")) {
                Icon(Icons.Outlined.ContentCopy, null)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
            when {
                state.local.isLoading -> DashboardSkeletonBar(Modifier.testTag("intranet_address_loading"), 132.dp, 22.dp)
                state.local.error != null -> Text(tr("读取失败"), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("intranet_address"), color = MaterialTheme.colorScheme.error, maxLines = 1)
                state.local.address == null -> Text(tr("无网络"), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("intranet_address"), maxLines = 1)
                else -> Text(state.local.address, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("intranet_address"), maxLines = 1, softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        val enabled = !state.settingsLoading && !state.saving && state.settingsError == null
        // 作者 long：整行共用一个开关语义和点击事件，屏幕阅读器与直接点开关都不会重复提交。
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .toggleable(state.sharing.config.allowLan, enabled = enabled, onValueChange = onAllowLan)
            .testTag("intranet_allow_lan").padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Wifi, null)
            Column(Modifier.weight(1f)) {
                Text(tr("允许局域网连接"), style = MaterialTheme.typography.bodyLarge)
                Text(when {
                    state.saving -> tr("正在保存…")
                    state.settingsLoading -> tr("读取设置…")
                    !state.sharing.config.allowLan -> tr("已停用")
                    !state.sharing.hasConfig -> tr("添加配置后使用")
                    state.sharing.config.mixedPort == 0 -> tr("代理端口未启用")
                    else -> tr("端口 ") + state.sharing.config.mixedPort
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("intranet_port"), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Switch(state.sharing.config.allowLan, onCheckedChange = null, enabled = enabled)
        }
        val error = state.local.error ?: state.error ?: state.settingsError
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                error != null -> {
                    Text(tr(error), Modifier.weight(1f).testTag("intranet_error"), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    IconButton(onClick = onRetry, enabled = !state.saving, modifier = Modifier.size(28.dp).testTag("intranet_retry")) {
                        Icon(Icons.Outlined.Refresh, tr("重试"), Modifier.size(18.dp))
                    }
                }
                state.feedback != null -> Text(state.feedback, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("intranet_feedback"),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
