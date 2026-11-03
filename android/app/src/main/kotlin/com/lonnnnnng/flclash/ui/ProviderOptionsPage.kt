package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URI

/** 作者 long：资源选项有独立确认边界，子页增删只改草稿；持久化失败时仍保留整份草稿供修正重试。 */
@Composable
internal fun ProviderOptionsPage(name: String, initial: ProxyProviderOverride, saving: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (ProxyProviderOverride) -> Unit,
    validateFilters: suspend (List<String>) -> List<String> = { values -> values.map { "" } }) {
    var filterJson by rememberSaveable(name) { mutableStateOf(initial.toJson().toString()) }
    val filters = remember(filterJson) { ProxyProviderOverride.fromJson(JsonParser.parseString(filterJson).asJsonObject) }
    var values by rememberSaveable(name) { mutableStateOf(HashMap(ProviderOption.entries.mapNotNull { option -> option.value(initial)?.let { option.name to it } }.toMap())) }
    var adding by rememberSaveable(name) { mutableStateOf(false) }
    var pickingFilters by rememberSaveable(name) { mutableStateOf(false) }
    var choosingIp by rememberSaveable(name) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var validating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busy = saving || validating
    val initialValues = remember(initial) { ProviderOption.entries.mapNotNull { option -> option.value(initial)?.let { option.name to it } }.toMap() }
    val dirty = filters != initial || values != initialValues
    val close = { if (!busy) { if (dirty) discard = true else onDismiss() } }
    fun set(option: ProviderOption, value: String?) {
        values = HashMap(values).apply { if (value == null) remove(option.name) else put(option.name, value) }
        localError = null
    }
    fun changeFilters(updated: ProxyProviderOverride) { filterJson = updated.toJson().toString(); localError = null }
    fun save() {
        if (busy) return
        val frozen = runCatching {
            ProviderOption.entries.fold(filters) { model, option ->
                val text = values[option.name]
                if (option.number && !text.isNullOrEmpty()) require(text.toIntOrNull()?.let { it > 0 } == true) { "${option.title}必须是正整数" }
                if (option == ProviderOption.URL && !text.isNullOrBlank()) {
                    val uri = runCatching { URI(text.trim()) }.getOrNull()
                    require(uri?.scheme?.lowercase() in setOf("http", "https") && !uri?.host.isNullOrBlank()) { "请输入有效的 HTTP/HTTPS 测速地址" }
                }
                option.apply(model, text?.takeIf(String::isNotEmpty)?.let { if (option in setOf(ProviderOption.URL, ProviderOption.EXPECTED_STATUS)) it.trim().takeIf(String::isNotEmpty) else it })
            }
        }.getOrElse { localError = it.message; return }
        validating = true; localError = null
        scope.launch {
            try {
                val errors = validateFilters(listOf(frozen.filter.orEmpty(), frozen.excludeFilter.orEmpty()))
                require(errors.size == 2 && errors.all(String::isEmpty)) { "过滤表达式无效，请检查后重试" }
                onSave(frozen)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { localError = failure.message ?: "选项校验失败，请重试" }
            finally { validating = false }
        }
    }
    ProfileEditorPage(onDismissRequest = close, backTag = "provider_options_back", title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = ::save, enabled = !busy, modifier = Modifier.testTag("provider_options_save")) { Text(if (validating) "校验中…" else if (saving) "保存中…" else "保存") } }, text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("provider_options_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                (localError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("provider_options_error")) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("过滤", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.testTag("provider_filter_menu")) { Icon(Icons.Outlined.MoreVert, "过滤选项") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("沿用源过滤") }, enabled = filters.filter != null, onClick = { menu = false; changeFilters(filters.copy(filter = null, memberLayout = null)) })
                            DropdownMenuItem(text = { Text("沿用源排除过滤") }, enabled = filters.excludeFilter != null, onClick = { menu = false; changeFilters(filters.copy(excludeFilter = null, memberLayout = null)) })
                        }
                    }
                }
                ProfileFormSection {
                    val rows = ProviderFilters.members(filters)
                    rows.forEachIndexed { index, member -> key(member.key) {
                        GroupOrderRow(index, rows.size, member.key, sorting = true, enabled = !busy,
                            onMove = { to -> changeFilters(ProviderFilters.replace(filters, rows.toMutableList().apply { add(to, removeAt(index)) })) }) {
                            IconButton(onClick = { changeFilters(ProviderFilters.replace(filters, rows - member)) }, enabled = !busy,
                                modifier = Modifier.testTag("provider_filter_remove_$index")) { Icon(Icons.Outlined.RemoveCircleOutline, "移除条件") }
                            Column(Modifier.weight(1f).heightIn(min = 72.dp).padding(vertical = 8.dp)) {
                                Text(LocalFilterPresets.current.firstOrNull { it.regex == member.value }?.label ?: member.value,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("provider_filter_row_$index"))
                                Text(member.kind.title, style = MaterialTheme.typography.labelSmall,
                                    color = if (member.kind == ProviderFilterKind.FILTER) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                            }
                        }
                    } }
                    if (rows.isEmpty()) Text(if (filters.filter == "" || filters.excludeFilter == "") "条件已清空；可从过滤选项恢复继承。" else "暂无过滤条件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProfileSectionRow("添加过滤", icon = Icons.Outlined.AddCircleOutline, enabled = !busy, modifier = Modifier.testTag("provider_filters_add")) { pickingFilters = true }
                }
                Text("选项", style = MaterialTheme.typography.titleSmall)
                ProfileFormSection {
                    ProviderOption.entries.filter { it.name in values }.forEach { option -> key(option) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { set(option, null) }, enabled = !busy, modifier = Modifier.testTag("provider_option_remove_${option.name}")) { Icon(Icons.Outlined.RemoveCircleOutline, "移除${option.title}") }
                            when {
                                option.boolean -> {
                                    Text(option.title, Modifier.weight(1f))
                                    Switch(values[option.name] == "true", { set(option, it.toString()) }, enabled = !busy, modifier = Modifier.testTag("provider_option_${option.name}"))
                                }
                                option == ProviderOption.IP_VERSION -> ProfileSectionRow(option.title, values[option.name], Icons.Outlined.Public, !busy,
                                    Modifier.weight(1f).testTag("provider_option_${option.name}")) { choosingIp = true }
                                else -> OutlinedTextField(values[option.name].orEmpty(), { if (it.length <= 8192) set(option, it) }, enabled = !busy, singleLine = true,
                                    label = { Text(option.title) }, suffix = { if (option.number) Text(if (option == ProviderOption.INTERVAL) "s" else "ms") },
                                    keyboardOptions = KeyboardOptions(keyboardType = if (option.number) KeyboardType.Number else KeyboardType.Text),
                                    modifier = Modifier.weight(1f).testTag("provider_option_${option.name}"))
                            }
                        }
                    } }
                    if (values.size < ProviderOption.entries.size) ProfileSectionRow("添加设置项", icon = Icons.Outlined.AddCircleOutline, enabled = !busy,
                        modifier = Modifier.testTag("provider_option_add")) { adding = true }
                }
                Spacer(Modifier.height(20.dp))
            }
        })
    if (adding) ProfileEditorPage(onDismissRequest = { adding = false }, backTag = "provider_option_picker_back", title = { Text("添加设置项") }, text = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProviderOption.entries.filterNot { it.name in values }.groupBy { it.section }.forEach { (section, options) ->
                Text(section, style = MaterialTheme.typography.titleSmall)
                ProfileFormSection { options.forEach { option -> ProfileSectionRow(option.title, icon = Icons.Outlined.Add, enabled = !busy,
                    modifier = Modifier.testTag("provider_option_pick_${option.name}")) { set(option, option.default) } } }
            }
            if (values.size == ProviderOption.entries.size) Text("已添加全部设置项", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    })
    if (choosingIp) ProfileEditorPage(onDismissRequest = { choosingIp = false }, backTag = "provider_ip_back", title = { Text("IP 版本") }, text = {
        Column { listOf("dual" to "双栈", "ipv4" to "IPv4", "ipv6" to "IPv6").forEach { (value, title) ->
            Row(Modifier.fillMaxWidth().clickable { set(ProviderOption.IP_VERSION, value); choosingIp = false }.padding(12.dp).testTag("provider_ip_$value"), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f)); RadioButton(values[ProviderOption.IP_VERSION.name] == value, null)
            }
        } }
    })
    if (pickingFilters) ProviderFilterPicker(filters, !busy, onDismiss = { pickingFilters = false }, onChange = ::changeFilters)
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃选项修改？") }, text = { Text("尚未保存的过滤和设置项将丢失。") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}

@Composable
private fun ProviderFilterPicker(initial: ProxyProviderOverride, enabled: Boolean, onDismiss: () -> Unit, onChange: (ProxyProviderOverride) -> Unit) {
    val baseJson by rememberSaveable { mutableStateOf(initial.toJson().toString()) }
    val base = remember(baseJson) { ProxyProviderOverride.fromJson(JsonParser.parseString(baseJson).asJsonObject) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(ProviderFilterKind.FILTER) }
    val presets = LocalFilterPresets.current
    fun publish() {
        // 作者 long：预设和自定义条件按点击顺序重放；撤回预设只移除本次追加，不能重排原有条件或后加的排除项。
        val model = selected.fold(base) { model, id ->
            if (id.startsWith("custom:")) {
                val parts = id.split(':', limit = 3)
                ProviderFilters.append(model, ProviderFilterKind.valueOf(parts[1]), parts[2])
            } else {
                val category = ProviderFilterKind.entries.first { id.startsWith(it.layout + ":") }
                val preset = presets.firstOrNull { category.layout + ":" + it.label == id }
                if (preset == null) model else ProviderFilters.append(model, category, preset.regex)
            }
        }
        onChange(model)
    }
    ProfileEditorPage(onDismissRequest = onDismiss, backTag = "provider_filter_picker_back", title = { Text("添加过滤") },
        confirmButton = { TextButton(onDismiss, Modifier.testTag("provider_filter_picker_done")) { Text("完成") } }, text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("搜索名称或表达式") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().testTag("provider_filter_search"))
                ProviderFilterKind.entries.forEach { category ->
                    var expanded by rememberSaveable(category) { mutableStateOf(false) }
                    val value = if (category == ProviderFilterKind.FILTER) base.filter.orEmpty() else base.excludeFilter.orEmpty()
                    val choices = presets.filterNot { FilterPresets.isApplied(it, value) }.filter { it.label.contains(query, true) || it.regex.contains(query, true) }
                    if (choices.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp).testTag("provider_filter_category_${category.name}"), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Icon(if (expanded || query.isNotBlank()) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                        if (expanded || query.isNotBlank()) ProfileFormSection { choices.forEach { preset ->
                            val id = category.layout + ":" + preset.label
                            Row(Modifier.fillMaxWidth().clickable(enabled) { selected = if (id in selected) selected - id else selected + id; publish() }
                                .padding(vertical = 8.dp).testTag("provider_filter_pick_${category.name}_${preset.label}"), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(preset.label); Text(preset.regex, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Checkbox(id in selected, null, enabled = enabled)
                            }
                        } }
                    }
                }
                Text("自定义表达式", style = MaterialTheme.typography.titleSmall)
                ProfileFormSection {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ProviderFilterKind.entries.forEach { category -> FilterChip(kind == category, { kind = category }, label = { Text(category.title) }, enabled = enabled,
                        modifier = Modifier.testTag("provider_filter_kind_${category.name}")) } }
                    OutlinedTextField(custom, { if (it.length <= 8192) custom = it }, enabled = enabled, label = { Text("用反引号分隔多个表达式") }, modifier = Modifier.fillMaxWidth().testTag("provider_filter_custom"))
                    TextButton(onClick = { selected = selected + "custom:${kind.name}:$custom"; custom = ""; publish() }, enabled = enabled && custom.isNotBlank(),
                        modifier = Modifier.testTag("provider_filter_custom_add")) { Text("添加表达式") }
                }
            }
        })
}
