package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.lonnnnnng.flclash.service.GlobalDnsSettings
import com.lonnnnnng.flclash.service.ProfileDnsOverrides

private val dnsLabels = mapOf("listen" to "监听地址", "ipv6" to "IPv6", "enhanced-mode" to "DNS 模式",
    "fake-ip-filter" to "Fake-IP 过滤", "fake-ip-filter-mode" to "Fake-IP 过滤模式",
    "default-nameserver" to "Default Nameserver", "nameserver" to "Nameserver", "fallback" to "Fallback",
    "proxy-server-nameserver" to "Proxy Server Nameserver", "direct-nameserver" to "Direct Nameserver")
private fun dnsDefault(key: String): String = when (key) {
    "ipv6" -> "false"
    "enhanced-mode" -> "redir-host"
    "fake-ip-filter-mode" -> "blacklist"
    "default-nameserver" -> "114.114.114.114\n223.5.5.5\n8.8.8.8\n1.0.0.1"
    "nameserver" -> "https://doh.pub/dns-query\ntls://223.5.5.5:853"
    "fake-ip-filter" -> "dns.msftnsci.com\nwww.msftnsci.com\nwww.msftconnecttest.com"
    else -> ""
}
private fun JsonElement.dnsText(): String = if (isJsonArray) asJsonArray.joinToString("\n") { it.asString } else asString
private fun ProfileDnsOverrides.changed(key: String, value: JsonElement?): ProfileDnsOverrides = ProfileDnsOverrides.fromJson(toJson().apply {
    if (value == null) remove(key) else add(key, value)
})

/** 作者 long：未添加字段继续继承配置；添加后的空列表是明确清空，删除字段才撤销覆盖。 */
@Composable
internal fun GlobalDnsSettingsPanel(settings: ProfileDnsOverrides, busy: Boolean,
    onSave: (ProfileDnsOverrides, (Boolean) -> Unit) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val fields = settings.toJson()
    fun remove(key: String) { onSave(settings.changed(key, null)) { if (!it) error = "移除失败，请重试" } }
    Column(Modifier.fillMaxWidth().testTag("page_global_dns"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("为普通配置设置 DNS 覆盖，未添加的字段沿用配置；自定义配置使用自己的 DNS。",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        listOf("选项" to listOf("listen", "ipv6", "enhanced-mode"), "Fake-IP" to listOf("fake-ip-filter", "fake-ip-filter-mode"),
            "Nameserver" to GlobalDnsSettings.keys.filter { it in GlobalDnsSettings.listKeys && it != "fake-ip-filter" }).forEach { (section, keys) ->
            if (keys.any(fields::has)) {
                Text(section, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                keys.filter(fields::has).forEach { key ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { editing = key }.testTag("global_dns_$key")
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(dnsLabels.getValue(key))
                            Text(fields.get(key).dnsText().ifEmpty { "空值覆盖" }, maxLines = 3,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (key == "ipv6") Switch(fields.get(key).asBoolean, { enabled ->
                            onSave(settings.changed(key, JsonPrimitive(enabled))) { if (!it) error = "保存失败，请重试" }
                        }, enabled = !busy)
                        IconButton({ remove(key) }, enabled = !busy, modifier = Modifier.testTag("global_dns_remove_$key")) {
                            Icon(Icons.Outlined.RemoveCircleOutline, "移除${dnsLabels.getValue(key)}覆盖")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (fields.size() == 0) Text("尚未添加 DNS 覆盖", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ adding = true }, enabled = !busy && fields.size() < GlobalDnsSettings.keys.size,
            modifier = Modifier.testTag("global_dns_add")) { Icon(Icons.Outlined.Add, null); Text("添加字段") }
    }
    if (adding) AlertDialog(onDismissRequest = { if (!busy) adding = false }, title = { Text("添加 DNS 字段") }, text = {
        Column { GlobalDnsSettings.keys.filterNot(fields::has).forEach { key ->
            TextButton({ adding = false; editing = key }, enabled = !busy, modifier = Modifier.testTag("global_dns_add_$key")) { Text(dnsLabels.getValue(key)) }
        } }
    }, confirmButton = { TextButton({ adding = false }, enabled = !busy) { Text("取消") } })
    editing?.let { key ->
        GlobalDnsFieldEditor(key, fields.get(key)?.dnsText() ?: dnsDefault(key), busy, onDismiss = { editing = null }) { value, done ->
            onSave(settings.changed(key, value), done)
        }
    }
}

@Composable
private fun GlobalDnsFieldEditor(key: String, initial: String, busy: Boolean, onDismiss: () -> Unit,
    onSave: (JsonElement, (Boolean) -> Unit) -> Unit) {
    var text by rememberSaveable(key) { mutableStateOf(initial) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val options = when (key) { "enhanced-mode" -> listOf("normal", "fake-ip", "redir-host")
        "fake-ip-filter-mode" -> listOf("blacklist", "whitelist", "rule"); "ipv6" -> listOf("false", "true"); else -> emptyList() }
    fun save(value: String) {
        text = value
        val element = if (key in GlobalDnsSettings.listKeys) JsonArray().apply {
            value.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { add(it) }
        } else if (key == "ipv6") JsonPrimitive(value.toBoolean()) else JsonPrimitive(value.trim())
        val candidate = ProfileDnsOverrides.fromJson(com.google.gson.JsonObject().apply { add(key, element) })
        runCatching { GlobalDnsSettings.validate(candidate) }.onFailure { error = it.message }.onSuccess {
            onSave(element) { success -> if (success) onDismiss() else error = "保存失败，输入已保留，请重试" }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = Modifier.testTag("global_dns_editor"), title = { Text(dnsLabels.getValue(key)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.isNotEmpty()) options.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { save(option) }.padding(vertical = 8.dp)
                    .testTag("global_dns_option_$option"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(text == option, null, enabled = !busy); Text(option, Modifier.padding(start = 12.dp))
                }
            } else OutlinedTextField(text, { if (it.length <= 65536) text = it }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("global_dns_input"), minLines = if (key in GlobalDnsSettings.listKeys) 5 else 1,
                maxLines = 9, singleLine = key == "listen", supportingText = {
                    Text(if (key in GlobalDnsSettings.listKeys) "每行一项；留空保存为空列表，移除字段恢复继承" else "例如 127.0.0.1:1053，留空关闭监听")
                })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_dns_error")) }
        }
    }, confirmButton = { if (options.isEmpty()) TextButton({ save(text) }, enabled = !busy, modifier = Modifier.testTag("global_dns_save")) { Text("保存") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } })
}
