package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.service.VpnSettingsState

@Composable
internal fun DashboardVpnCard(
    state: VpnSettingsState,
    busy: Boolean,
    onEnabled: (Boolean) -> Unit,
    onRestart: () -> Unit,
) {
    DashboardSurface(modifier = Modifier.testTag("dashboard_vpn_card")) {
        // 作者 long：卡片显示保存的接管选择，当前会话保留旧模式时必须明确提供重启操作。
        Row(Modifier.fillMaxWidth().toggleable(state.enabled, enabled = !busy, onValueChange = onEnabled).testTag("vpn_enabled").padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.VpnKey, null, tint = if (state.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text("VPN", style = MaterialTheme.typography.titleMedium)
                Text(if (state.enabled) "已启用" else "已停用", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(state.enabled, onCheckedChange = null, enabled = !busy)
        }
        if (state.restartRequired) {
            Text(if (state.appliedEnabled == true) "重启后停用 VPN，当前仍在接管流量" else "重启后启用 VPN，当前仅运行本地代理",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("vpn_restart_hint"))
            TextButton(onClick = onRestart, enabled = !busy, modifier = Modifier.testTag("vpn_restart")) { Text("重启生效") }
        }
    }
}
