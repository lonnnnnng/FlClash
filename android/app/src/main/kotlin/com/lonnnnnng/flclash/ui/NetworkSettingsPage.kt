package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.service.NetworkSettings
import com.lonnnnnng.flclash.service.VpnSettingsState

private enum class NetworkField(val title: String) {
    DOMAINS("绕过域名"), STACK("协议栈"), MTU("MTU"), CONGESTION("拥塞控制"), ROUTES("路由地址"), EXCLUDED("排除路由地址")
}

/** 作者 long：网络页逐项保存，只有输入确认后才改变接管路径；应用失败时保留原草稿供修正。 */
@Composable
internal fun NetworkSettingsPanel(
    settings: NetworkSettings,
    vpn: VpnSettingsState,
    busy: Boolean,
    onVpnEnabled: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onSave: (NetworkSettings, (Boolean) -> Unit) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<NetworkField?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(value: NetworkSettings) {
        error = null
        onSave(value) { if (!it) error = "设置未能应用，请检查当前状态后重试" }
    }
    Column(Modifier.fillMaxWidth().testTag("page_network"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        NetworkSection {
            NetworkToggle("VPN", "启用后接管此设备的网络流量", "network_vpn", vpn.enabled, busy, onVpnEnabled)
            if (vpn.restartRequired) {
                Text("VPN 接管方式已保存，重启服务后生效", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onRestart, Modifier.padding(horizontal = 8.dp).testTag("network_restart"), enabled = !busy) { Text("重启生效") }
            }
        }
        NetworkSection("VPN") {
            NetworkToggle("系统代理", "向支持系统代理的应用提供本地 HTTP 代理", "network_system_proxy", settings.systemProxy, busy) { save(settings.copy(systemProxy = it)) }
            NetworkOpenRow("绕过域名", "${settings.bypassDomain.size} 项", "network_domains", busy) { editing = NetworkField.DOMAINS }
            NetworkToggle("允许绕过 VPN", null, "network_allow_bypass", settings.allowBypass, busy) { save(settings.copy(allowBypass = it)) }
            NetworkToggle("IPv6", null, "network_ipv6", settings.ipv6, busy) { save(settings.copy(ipv6 = it)) }
            NetworkToggle("DNS 劫持", null, "network_dns_hijacking", settings.dnsHijacking, busy) { save(settings.copy(dnsHijacking = it)) }
        }
        Text("绕过域名只影响系统 HTTP 代理；端口认证开启时不会发布系统代理。IPv6 控制 VPN 的 IPv6 接管。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        NetworkSection("选项") {
            NetworkOpenRow("协议栈", settings.stack, "network_stack", busy) { editing = NetworkField.STACK }
        }
        NetworkSection("TUN") {
            NetworkOpenRow("MTU", settings.mtu.toString(), "network_mtu", busy) { editing = NetworkField.MTU }
            if (settings.stack == "mips") NetworkOpenRow("拥塞控制", settings.congestionController, "network_congestion", busy) { editing = NetworkField.CONGESTION }
            NetworkToggle("绕过私有路由地址", null, "network_bypass_private", settings.bypassPrivateRoute, busy) { save(settings.copy(bypassPrivateRoute = it)) }
            NetworkOpenRow("路由地址", if (settings.routeAddress.isEmpty()) "全部地址" else "${settings.routeAddress.size} 项", "network_routes", busy) { editing = NetworkField.ROUTES }
            NetworkOpenRow("排除路由地址", "${settings.routeExcludeAddress.size} 项", "network_excluded", busy) { editing = NetworkField.EXCLUDED }
        }
        Text("私有地址和排除的路由不进入 VPN；留空路由地址时接管全部其余地址。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("network_saving"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("network_error")) }
    }
    editing?.let { field ->
        NetworkSettingDialog(field, settings, busy, onClose = { editing = null }, onSave = onSave)
    }
}

@Composable
private fun NetworkSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun NetworkToggle(title: String, subtitle: String?, tag: String, value: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, enabled = !busy, onValueChange = onChange)
        .testTag(tag).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(value, onCheckedChange = null, enabled = !busy)
    }
}

@Composable
private fun NetworkOpenRow(title: String, subtitle: String, tag: String, busy: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onOpen).testTag(tag).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun NetworkField.value(settings: NetworkSettings): String = when (this) {
    NetworkField.DOMAINS -> settings.bypassDomain.joinToString("\n")
    NetworkField.STACK -> settings.stack
    NetworkField.MTU -> settings.mtu.toString()
    NetworkField.CONGESTION -> settings.congestionController
    NetworkField.ROUTES -> settings.routeAddress.joinToString("\n")
    NetworkField.EXCLUDED -> settings.routeExcludeAddress.joinToString("\n")
}

@Composable
private fun NetworkSettingDialog(field: NetworkField, settings: NetworkSettings, busy: Boolean, onClose: () -> Unit,
    onSave: (NetworkSettings, (Boolean) -> Unit) -> Unit) {
    var value by rememberSaveable(field) { mutableStateOf(field.value(settings)) }
    var error by rememberSaveable(field) { mutableStateOf<String?>(null) }
    val options = when (field) {
        NetworkField.STACK -> listOf("gvisor", "system", "mixed", "mips")
        NetworkField.CONGESTION -> listOf("cubic", "reno", "bbr", "bbr3")
        else -> emptyList()
    }
    val list = field in listOf(NetworkField.DOMAINS, NetworkField.ROUTES, NetworkField.EXCLUDED)
    fun commit(text: String) {
        value = text
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val next = runCatching {
            when (field) {
                NetworkField.DOMAINS -> settings.copy(bypassDomain = lines)
                NetworkField.STACK -> settings.copy(stack = text)
                NetworkField.MTU -> settings.copy(mtu = text.toIntOrNull() ?: 0)
                NetworkField.CONGESTION -> settings.copy(congestionController = text)
                NetworkField.ROUTES -> settings.copy(routeAddress = lines)
                NetworkField.EXCLUDED -> settings.copy(routeExcludeAddress = lines)
            }.validated()
        }.getOrElse { error = it.message ?: "输入格式无效"; return }
        error = null
        onSave(next) { success -> if (success) onClose() else error = "保存失败，输入已保留，请重试" }
    }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, modifier = Modifier.testTag("network_editor"),
        title = { Text(field.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isNotEmpty()) options.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { commit(option) }.testTag("network_option_$option").padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(value == option, null, enabled = !busy)
                        Text(option, Modifier.padding(start = 12.dp))
                    }
                } else OutlinedTextField(value, onValueChange = { if (it.length <= 16384) { value = it; error = null } },
                    modifier = Modifier.fillMaxWidth().testTag("network_editor_input"), enabled = !busy,
                    minLines = if (list) 5 else 1, maxLines = if (list) 9 else 1, singleLine = !list,
                    keyboardOptions = KeyboardOptions(keyboardType = if (field == NetworkField.MTU) KeyboardType.Number else KeyboardType.Text),
                    supportingText = { Text(when {
                        field == NetworkField.DOMAINS -> "每行一项，可使用 * 通配符"
                        list -> "每行一项，使用 IP/前缀长度"
                        else -> "1280–65535"
                    }) }, isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("network_editor_error")) }
            }
        },
        confirmButton = { if (options.isEmpty()) TextButton({ commit(value.trim()) }, enabled = !busy, modifier = Modifier.testTag("network_editor_save")) { Text("保存") } },
        dismissButton = {
            Row {
                if (options.isEmpty()) TextButton({ value = field.value(NetworkSettings()); error = null }, enabled = !busy, modifier = Modifier.testTag("network_editor_reset")) { Text("恢复默认") }
                TextButton(onClose, enabled = !busy) { Text("取消") }
            }
        },
    )
}
