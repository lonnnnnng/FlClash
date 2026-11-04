package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Devices
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
            Text("内网 IP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy, enabled = state.copyText != null && !state.saving,
                modifier = Modifier.testTag("intranet_copy")) {
                Icon(Icons.Outlined.ContentCopy, null)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(when {
            state.local.isLoading -> "读取中…"
            state.local.error != null -> "读取失败"
            state.local.address == null -> "无网络"
            else -> state.local.address
        }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("intranet_address"))
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
                Text("允许局域网连接", style = MaterialTheme.typography.bodyLarge)
                Text(when {
                    state.saving -> "正在保存…"
                    state.settingsLoading -> "读取设置…"
                    !state.sharing.config.allowLan -> "已停用"
                    !state.sharing.hasConfig -> "添加配置后使用"
                    state.sharing.config.mixedPort == 0 -> "代理端口未启用"
                    else -> "端口 " + state.sharing.config.mixedPort
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("intranet_port"))
            }
            Switch(state.sharing.config.allowLan, onCheckedChange = null, enabled = enabled)
        }
        val error = state.local.error ?: state.error ?: state.settingsError
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp).testTag("intranet_error"))
            TextButton(onClick = onRetry, enabled = !state.saving, modifier = Modifier.testTag("intranet_retry")) { Text("重试") }
        }
        state.feedback?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).testTag("intranet_feedback"))
        }
    }
}
