package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileSnifferOverrides
import com.lonnnnnng.flclash.service.YamlConfigCodec

private data class MoreField(val key: String, val label: String, val kind: String, val default: String)
private val ntpFields = listOf(
    MoreField("enable", "状态", "toggle", "false"),
    MoreField("server", "服务器", "text", "time.apple.com"),
    MoreField("port", "端口", "port", "123"),
    MoreField("interval", "同步间隔（分钟）", "number", "30"),
    MoreField("dialer-proxy", "出站代理", "text", ""),
    MoreField("write-to-system", "写入系统时间", "toggle", "false"),
)
private val snifferFields = listOf(
    MoreField("enable", "状态", "toggle", "false"),
    MoreField("override-destination", "覆盖目标地址", "toggle", "true"),
    MoreField("force-dns-mapping", "强制 DNS 映射", "toggle", "true"),
    MoreField("parse-pure-ip", "嗅探纯 IP 连接", "toggle", "true"),
    MoreField("force-domain", "强制嗅探域名", "list", ""),
    MoreField("skip-domain", "跳过嗅探域名", "list", ""),
    MoreField("skip-src-address", "跳过源地址", "cidrs", ""),
    MoreField("skip-dst-address", "跳过目标地址", "cidrs", ""),
)

/** 作者 long：每个字段有独立覆盖选择；未选字段不写入元数据，源配置与显式 false/空列表可准确区分。 */
@Composable
internal fun ProfileMoreEditor(
    state: ProfileMoreEditorUiState,
    section: String,
    sourceContent: String,
    onDismiss: () -> Unit,
    onSave: (ProfileNtpOverrides, ProfileSnifferOverrides) -> Unit,
) {
    val profile = state.profile ?: return
    val isNtp = section == "ntp"
    val initial = remember(profile.id, isNtp) { if (isNtp) state.ntp.toJson() else state.sniffer.toJson() }
    var json by remember(profile.id, section) { mutableStateOf(initial.toString()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val clearFormFocus = remember { arrayOf<(() -> Unit)?>(null) }
    val source = remember(profile.id, section, sourceContent) {
        runCatching { JsonParser.parseString(YamlConfigCodec.toJson(sourceContent)).asJsonObject.get(section)?.asJsonObject }.getOrNull() ?: JsonObject()
    }
    val root = JsonParser.parseString(json).asJsonObject
    val dirty = root != initial
    fun exit() { if (!state.isSaving) { if (dirty) confirmDiscard = true else onDismiss() } }
    fun change(action: (JsonObject) -> Unit) { val next = root.deepCopy(); action(next); json = next.toString(); error = null }
    ProfileEditorPage(
        onDismissRequest = ::exit, backTag = "profile_more_editor_back",
        title = { Text(if (isNtp) "NTP" else "Sniffer") },
        confirmButton = {
            TextButton(enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_editor_save"), onClick = {
                try {
                    validateMoreFields(root, isNtp)
                    clearFormFocus[0]?.invoke()
                    onSave(if (isNtp) ProfileNtpOverrides.fromJson(root) else state.ntp,
                        if (isNtp) state.sniffer else ProfileSnifferOverrides.fromJson(root))
                } catch (failure: IllegalArgumentException) { error = failure.message }
            }) { if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("保存") }
        },
        text = {
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            DisposableEffect(focus, keyboard) {
                // 作者 long：保存后子页面退栈前释放本窗口焦点，避免把软键盘带到更多页或下一张编辑页。
                clearFormFocus[0] = { focus.clearFocus(force = true); keyboard?.hide() }
                onDispose { clearFormFocus[0] = null }
            }
            Column(Modifier.fillMaxSize().testTag("profile_more_editor_scroll").verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("勾选需要覆盖的字段；取消勾选后沿用原配置。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_more_editor_error")) }
                (if (isNtp) ntpFields else snifferFields).forEach { field ->
                    MoreOverrideField(field, root.get(field.key), source.get(field.key), !state.isSaving,
                        onChange = { value -> change { if (value == null) it.remove(field.key) else it.add(field.key, value) } })
                }
                if (!isNtp) {
                    Text("嗅探协议", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    for (protocol in listOf("HTTP", "TLS", "QUIC")) {
                        val protocols = root.get("sniff")?.takeIf { it.isJsonObject }?.asJsonObject
                        val config = protocols?.get(protocol)?.takeIf { it.isJsonObject }?.asJsonObject
                        ProfileFormSection {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(config != null, enabled = !state.isSaving,
                                    modifier = Modifier.testTag("sniff_" + protocol + "_override"), onCheckedChange = { selected -> change {
                                        val sniff = it.get("sniff")?.takeIf { v -> v.isJsonObject }?.asJsonObject ?: JsonObject().also { v -> it.add("sniff", v) }
                                        if (selected) sniff.add(protocol, JsonObject()) else { sniff.remove(protocol); if (sniff.size() == 0) it.remove("sniff") }
                                    } })
                                Text(protocol, Modifier.weight(1f))
                                Text(if (config == null) "沿用原配置" else "覆盖", style = MaterialTheme.typography.labelMedium)
                            }
                            if (config != null) {
                                val ports = config.get("ports")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
                                var text by remember(protocol) { mutableStateOf(ports.joinToString("\n")) }
                                OutlinedTextField(text, { value -> text = value; change {
                                    it.getAsJsonObject("sniff").getAsJsonObject(protocol).add("ports", Gson().toJsonTree(splitMoreList(value)))
                                } }, label = { Text("端口或范围（每行一项）") }, supportingText = { Text("留空使用协议默认端口，例如 80 或 8000-9000") },
                                    enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("sniff_" + protocol + "_ports"))
                                val destination = config.get("override-destination")?.asBoolean
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(null to "默认", true to "覆盖目标", false to "保留目标").forEach { (value, label) ->
                                        FilterChip(destination == value, onClick = { change {
                                            val entry = it.getAsJsonObject("sniff").getAsJsonObject(protocol)
                                            if (value == null) entry.remove("override-destination") else entry.addProperty("override-destination", value)
                                        } }, enabled = !state.isSaving, label = { Text(label) })
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(enabled = !state.isSaving, onClick = { json = "{}"; error = null }, modifier = Modifier.testTag("profile_more_reset")) { Text("清除本分区覆盖") }
                Spacer(Modifier.height(16.dp))
            }
        },
    )
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } })
}

@Composable
private fun MoreOverrideField(field: MoreField, value: JsonElement?, inherited: JsonElement?, enabled: Boolean, onChange: (JsonElement?) -> Unit) {
    val selected = value != null
    fun fallback(): JsonElement = inherited?.deepCopy() ?: when (field.kind) {
        "toggle" -> Gson().toJsonTree(field.default.toBoolean())
        "list", "cidrs" -> Gson().toJsonTree(emptyList<String>())
        "number", "port" -> Gson().toJsonTree(field.default.toInt())
        else -> Gson().toJsonTree(field.default)
    }
    ProfileFormSection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected, onCheckedChange = { onChange(if (it) fallback() else null) }, enabled = enabled,
                modifier = Modifier.testTag("more_" + field.key + "_override"))
            Text(field.label, Modifier.weight(1f))
            Text(if (selected) "覆盖" else "沿用原配置", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            if (field.kind == "toggle") Switch(value?.asBoolean ?: false, { onChange(Gson().toJsonTree(it)) }, enabled = enabled,
                modifier = Modifier.testTag("more_" + field.key + "_value"))
            else {
                val current = if (value?.isJsonArray == true) value.asJsonArray.joinToString("\n") { it.asString } else value?.asString.orEmpty()
                var text by remember(field.key) { mutableStateOf(current) }
                OutlinedTextField(text, { new -> text = new; onChange(when (field.kind) {
                    "list", "cidrs" -> Gson().toJsonTree(splitMoreList(new))
                    else -> Gson().toJsonTree(new)
                }) }, label = { Text(field.label) }, enabled = enabled,
                    singleLine = field.kind !in listOf("list", "cidrs"),
                    supportingText = if (field.kind in listOf("list", "cidrs")) ({ Text("每行一项；留空会清空此列表") }) else null,
                    modifier = Modifier.fillMaxWidth().testTag("more_" + field.key + "_value"))
            }
        }
    }
}

private fun splitMoreList(value: String) = value.lines().map(String::trim).filter(String::isNotEmpty).distinct()

internal fun validateMoreFields(root: JsonObject, ntp: Boolean) {
    if (ntp) {
        root.get("server")?.let { require(it.asString.isNotBlank() && it.asString.none(Char::isWhitespace) && it.asString.length <= 253) { "NTP 服务器格式无效" } }
        for (key in listOf("port", "interval")) root.get(key)?.let {
            val value = it.asString.toIntOrNull()
            require(value != null && value > 0 && (key != "port" || value <= 65535)) { if (key == "port") "端口必须在 1–65535 之间" else "同步间隔必须为正整数" }
            root.addProperty(key, value)
        }
    } else {
        for (key in listOf("skip-src-address", "skip-dst-address")) root.get(key)?.asJsonArray?.forEach {
            val parts = it.asString.split('/')
            val address = parts[0]
            val ipv6 = address.contains(':')
            val numeric = if (ipv6) address.matches(Regex("[0-9a-fA-F:]+")) else address.split('.').let { octets ->
                octets.size == 4 && octets.all { value -> value.toIntOrNull()?.let { it in 0..255 } == true }
            }
            val validAddress = numeric && runCatching { java.net.InetAddress.getByName(address) }.isSuccess
            require(validAddress && parts.size <= 2 && (parts.size == 1 || parts[1].toIntOrNull() in 0..(if (ipv6) 128 else 32))) { "地址应为有效 IP 或 CIDR：" + it.asString }
        }
        root.get("sniff")?.asJsonObject?.entrySet()?.forEach { (_, config) ->
            config.asJsonObject.get("ports")?.asJsonArray?.forEach { element ->
                val range = element.asString.split('-').map { it.toIntOrNull() }
                require(range.size in 1..2 && range.all { it != null && it in 1..65535 } && (range.size == 1 || range[0]!! <= range[1]!!)) { "端口应为 1–65535 或递增范围：" + element.asString }
            }
        }
    }
}
