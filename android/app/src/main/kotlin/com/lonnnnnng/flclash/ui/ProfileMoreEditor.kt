package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.ProfileExtensionApplier
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileSnifferOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 作者 long：只列出已添加的字段；删除恢复继承，false/空列表则作为明确覆盖。页面保存前均为独立草稿。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileMoreEditor(state: ProfileMoreEditorUiState, section: String, sourceContent: String,
    onDismiss: () -> Unit, onSave: (ProfileNtpOverrides, ProfileSnifferOverrides) -> Unit) {
    val profile = state.profile ?: return
    val ntp = section == "ntp"
    val initial = remember(profile.id, section) { if (ntp) state.ntp.toJson() else state.sniffer.toJson() }
    var json by rememberSaveable(profile.id, section) { mutableStateOf(initial.toString()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var yaml by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val root = remember(json) { JsonParser.parseString(json).asJsonObject }
    val fields = if (ntp) ntpFields else snifferFields
    val listed = fields.filter { moreFieldValue(root, it) != null }
    val remaining = fields.filter { moreFieldValue(root, it) == null }
    val targets = remember(profile.id, sourceContent) { runCatching { ProfileExtensionApplier.targets(sourceContent) }.getOrDefault(emptyList()) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun change(field: MoreField, value: JsonElement?) { val next = root.deepCopy(); setMoreField(next, field, value); json = next.toString(); error = null }
    fun exit() { if (!state.isSaving) { if (root != initial) discard = true else onDismiss() } }
    ProfileEditorPage(::exit, title = { Text(if (ntp) "NTP" else "Sniffer") }, backTag = "profile_more_editor_back", confirmButton = {
        IconButton(onClick = { adding = true }, enabled = !state.isSaving && remaining.isNotEmpty(), modifier = Modifier.testTag("profile_more_add")) { Icon(Icons.Outlined.AddCircleOutline, "添加设置项") }
        IconButton(onClick = { yaml = true }, enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_yaml")) { Icon(Icons.Outlined.Code, "快捷 YAML") }
        TextButton(enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_editor_save"), onClick = {
            runCatching {
                val next = root.deepCopy(); validateMoreFields(next, ntp)
                focus.clearFocus(force = true); keyboard?.hide()
                onSave(if (ntp) ProfileNtpOverrides.fromJson(next) else state.ntp, if (ntp) state.sniffer else ProfileSnifferOverrides.fromJson(next))
            }.onFailure { error = it.message ?: "字段无效，请检查后重试" }
        }) { if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("保存") }
    }, text = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("profile_more_editor_scroll"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_more_editor_error")) }
            if (root.size() == 0) {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp, bottom = 40.dp).testTag("profile_more_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (ntp) Icons.Outlined.Schedule else Icons.Outlined.Radar, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("暂无设置项", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium)
                    Text("未添加的字段沿用原配置。添加设置项后可单独调整。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { adding = true }, enabled = !state.isSaving, modifier = Modifier.padding(top = 20.dp).testTag("profile_more_empty_add")) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("添加设置项") }
                }
            } else {
                listOf(false, true).forEach { protocols ->
                    val entries = listed.filter { (it.kind == MoreFieldKind.PROTOCOL) == protocols }
                    if (entries.isNotEmpty()) {
                        Text(if (protocols) "嗅探协议" else "选项", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                        Surface(shape = RoundedCornerShape(24.dp), color = dashboardCardColor()) {
                            Column {
                                entries.forEachIndexed { index, field ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    MoreSettingRow(field, checkNotNull(moreFieldValue(root, field)), !state.isSaving,
                                        onRemove = { change(field, null) }, onEdit = { editing = field.key }, onToggle = { change(field, Gson().toJsonTree(it)) })
                                }
                            }
                        }
                    }
                }
                if (!ntp && root.getAsJsonObject("sniff")?.size() == 0) {
                    Text("已明确清空全部嗅探协议", modifier = Modifier.testTag("profile_more_protocols_cleared"))
                    TextButton(onClick = { json = root.deepCopy().apply { remove("sniff") }.toString() }, enabled = !state.isSaving) { Text("恢复协议继承") }
                }
                val dialer = if (ntp) root.get("dialer-proxy")?.asString.orEmpty() else ""
                if (dialer.isNotBlank() && targets.isNotEmpty() && dialer !in targets) Text("出站代理已不在配置中，请检查节点或策略组名称", color = MaterialTheme.colorScheme.error)
                Text("未添加的字段沿用原配置。移除设置项可恢复继承；保存后生效。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { clear = true }, enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_reset")) { Text("清除本分区覆盖") }
            }
            Spacer(Modifier.height(16.dp))
        }
    })
    if (adding) ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("添加设置项", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { adding = false }, modifier = Modifier.testTag("profile_more_add_done")) { Text("完成") }
            }
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).testTag("profile_more_available_fields")) {
                if (remaining.isEmpty()) Text("所有设置项均已添加", Modifier.padding(vertical = 24.dp))
                listOf(false, true).forEach { protocols ->
                    val entries = remaining.filter { (it.kind == MoreFieldKind.PROTOCOL) == protocols }
                    if (entries.isNotEmpty()) {
                        Text(if (protocols) "嗅探协议" else "选项", Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                        entries.forEach { field ->
                            Row(Modifier.fillMaxWidth().clickable { change(field, field.defaultValue()) }.heightIn(min = 64.dp).testTag("more_add_${field.key}"), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AddCircleOutline, null, Modifier.padding(end = 16.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text(field.label)
                                    if (field.description.isNotEmpty()) Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    fields.firstOrNull { it.key == editing }?.let { field -> moreFieldValue(root, field)?.let { value ->
        MoreValueEditor(field, value, root, ntp, { editing = null }) { change(field, it); editing = null }
    } }
    if (yaml) MoreYamlEditor(if (ntp) "NTP" else "Sniffer", root, ntp, { yaml = false }) { json = it.toString(); error = null; yaml = false }
    if (discard) MoreDiscardDialog("放弃未保存的修改？", { discard = false }, onDismiss)
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("清除本分区覆盖？") }, text = { Text("保存后所有字段将恢复继承原配置。") },
        confirmButton = { TextButton(onClick = { json = "{}"; error = null; clear = false }) { Text("清除") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } })
}

@Composable
private fun MoreSettingRow(field: MoreField, value: JsonElement, enabled: Boolean, onRemove: () -> Unit, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val toggle = field.kind == MoreFieldKind.TOGGLE
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("more_field_${field.key}"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("more_remove_${field.key}")) { Icon(Icons.Outlined.RemoveCircleOutline, "移除${field.label}", tint = MaterialTheme.colorScheme.error) }
        Column(Modifier.weight(1f).clickable(enabled) { if (toggle) onToggle(!value.asBoolean) else onEdit() }.padding(vertical = 12.dp).testTag("more_edit_${field.key}")) {
            Text(field.label)
            val detail = when {
                toggle -> field.description
                field.kind == MoreFieldKind.PROTOCOL -> value.asJsonObject.get("ports")?.asJsonArray?.let { if (it.size() == 0) "默认端口" else "${it.size()} 个端口或范围" } ?: "默认端口"
                value.isJsonArray -> "${value.asJsonArray.size()} 项" + if (value.asJsonArray.size() == 0) " · 空列表" else ""
                value.asString.isEmpty() -> "空值"
                else -> value.asString
            }
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (toggle) Switch(value.asBoolean, onToggle, enabled = enabled, modifier = Modifier.padding(end = 16.dp).testTag("more_${field.key}_value"))
        else IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.testTag("more_open_${field.key}")) { Icon(Icons.Outlined.ChevronRight, "编辑${field.label}") }
    }
}

@Composable
private fun MoreValueEditor(field: MoreField, initial: JsonElement, root: JsonObject, ntp: Boolean, onDismiss: () -> Unit, onConfirm: (JsonElement) -> Unit) {
    val protocol = field.kind == MoreFieldKind.PROTOCOL
    val list = field.kind in setOf(MoreFieldKind.LIST, MoreFieldKind.ADDRESSES)
    val original = if (protocol) initial.asJsonObject.get("ports")?.asJsonArray?.joinToString("\n") { it.asString }.orEmpty() else if (list) initial.asJsonArray.joinToString("\n") { it.asString } else initial.asString
    val originalDestination = if (protocol) initial.asJsonObject.get("override-destination")?.asBoolean else null
    var text by rememberSaveable(field.key) { mutableStateOf(original) }
    var destination by rememberSaveable(field.key) { mutableStateOf(originalDestination) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun exit() { if (text != original || destination != originalDestination) discard = true else onDismiss() }
    ProfileEditorPage(::exit, title = { Text(field.label) }, backTag = "more_value_back", confirmButton = {
        TextButton(modifier = Modifier.testTag("more_value_confirm"), onClick = {
            runCatching {
                val value = if (protocol) initial.asJsonObject.deepCopy().apply {
                    add("ports", Gson().toJsonTree(splitMoreList(text)))
                    if (destination == null) remove("override-destination") else addProperty("override-destination", destination)
                } else if (list) Gson().toJsonTree(splitMoreList(text)) else Gson().toJsonTree(text.trim())
                val next = root.deepCopy(); setMoreField(next, field, value); validateMoreFields(next, ntp)
                focus.clearFocus(force = true); keyboard?.hide(); onConfirm(checkNotNull(moreFieldValue(next, field)))
            }.onFailure { error = it.message ?: "字段无效，请检查后重试" }
        }) { Text("确认") }
    }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("more_value_error")) }
            if (field.description.isNotEmpty()) Text(field.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { text = it; error = null }, label = { Text(if (protocol) "端口或范围（每行一项）" else field.label) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(if (protocol) "sniff_${field.protocol}_ports" else "more_${field.key}_value"),
                singleLine = !list && !protocol, minLines = if (list || protocol) 6 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = if (field.kind in setOf(MoreFieldKind.PORT, MoreFieldKind.NUMBER)) KeyboardType.Number else KeyboardType.Text),
                supportingText = if (protocol) ({ Text("留空使用协议默认端口，例如 80 或 8000-9000") })
                else if (field.kind == MoreFieldKind.ADDRESSES) ({ Text("每行一个 IP、CIDR、geoip: 或 rule-set: 匹配项；移除字段恢复继承") })
                else if (list) ({ Text("每行一项；留空明确清空列表，移除字段可恢复继承") }) else null)
            if (protocol) {
                Text("覆盖目标地址", style = MaterialTheme.typography.titleSmall)
                listOf(null to "默认，跟随 Sniffer", true to "启用", false to "禁用").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { destination = value }.heightIn(min = 56.dp).testTag("more_protocol_destination_${value ?: "default"}"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(destination == value, onClick = null); Text(label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    })
    if (discard) MoreDiscardDialog("放弃未保存的修改？", { discard = false }, onDismiss)
}

@Composable
private fun MoreYamlEditor(title: String, initial: JsonObject, ntp: Boolean, onDismiss: () -> Unit, onConfirm: (JsonObject) -> Unit) {
    val original = remember { moreFieldsYaml(initial) }
    var content by rememberSaveable { mutableStateOf(original) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun exit() { if (!checking) { if (content != original) discard = true else onDismiss() } }
    ProfileEditorPage(::exit, title = { Text("$title · YAML") }, backTag = "more_yaml_back", confirmButton = {
        TextButton(enabled = !checking, modifier = Modifier.testTag("more_yaml_confirm"), onClick = { scope.launch {
            checking = true
            try {
                val next = withContext(Dispatchers.Default) { parseMoreFieldsYaml(content, ntp) }
                focus.clearFocus(force = true); keyboard?.hide(); onConfirm(next)
            } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: IllegalArgumentException) { error = failure.message ?: "YAML 字段无效" }
            finally { checking = false }
        } }) { if (checking) CircularProgressIndicator(Modifier.size(20.dp)) else Text("确认") }
    }, text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("只填写本分区内部字段，不包含 ${if (ntp) "ntp" else "sniffer"}: 外层。删除字段恢复继承；false、空列表和空协议映射会保留为明确覆盖。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("more_yaml_error")) }
            OutlinedTextField(content, { content = it; error = null }, enabled = !checking, modifier = Modifier.fillMaxWidth().weight(1f).testTag("more_yaml_content"), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), label = { Text("YAML") })
        }
    })
    if (discard) MoreDiscardDialog("放弃 YAML 修改？", { discard = false }, onDismiss)
}

@Composable
private fun MoreDiscardDialog(title: String, onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, confirmButton = { TextButton(onClick = onDiscard) { Text("放弃") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("继续编辑") } })
}
