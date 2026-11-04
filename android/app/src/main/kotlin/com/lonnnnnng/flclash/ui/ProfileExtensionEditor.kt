package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import java.util.UUID
import kotlin.math.abs

@Composable
internal fun ProfileExtensionRoute(id: Long, controller: ProfileExtensionController, scripts: List<Script>,
    canEditSource: Boolean, onDismiss: () -> Unit, onOpenScripts: () -> Unit, onDns: () -> Unit, onProviders: () -> Unit) {
    val state by controller.state.collectAsState()
    LaunchedEffect(id) { controller.open(id) }
    LaunchedEffect(scripts) { controller.refreshApplied() }
    ProfileExtensionEditor(state, scripts, onDismiss = { controller.close(onDismiss) }, onApply = controller::apply,
        onMode = controller::mode, onScript = controller::script, onTarget = controller::target,
        onRules = controller::rules, onGlobalRules = controller::globalRules, onDisabled = controller::disabled,
        onOpenScripts = onOpenScripts, onRetry = { controller.open(id) },
        onSkipApply = { controller.closeWithoutApplying(onDismiss) }, canEditSource = canEditSource, onDns = onDns, onProviders = onProviders)
}

/** 作者 long：列表操作即时保存为扩展元数据；当前配置显式或退出应用，未选中的配置只保存，不切换核心。 */
@Composable
internal fun ProfileExtensionEditor(state: ProfileExtensionUiState, scripts: List<Script>, onDismiss: () -> Unit,
    onApply: () -> Unit, onMode: (String) -> Unit, onScript: (Long?) -> Unit, onTarget: (String?) -> Unit,
    onRules: (List<ExtensionRule>) -> Unit, onGlobalRules: (List<ExtensionRule>) -> Unit, onDisabled: (Set<String>) -> Unit,
    onOpenScripts: () -> Unit, onRetry: () -> Unit, onSkipApply: () -> Unit, canEditSource: Boolean = false,
    onDns: () -> Unit = {}, onProviders: () -> Unit = {}) {
    var subpage by rememberSaveable(state.profile?.id) { mutableIntStateOf(0) }
    var query by rememberSaveable(subpage) { mutableStateOf("") }
    var formId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRuleValue by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sorting by rememberSaveable(subpage) { mutableStateOf(false) }
    var showTarget by rememberSaveable { mutableStateOf(false) }
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val busy = state.loading || state.saving || state.applying
    val global = subpage == 2
    val rules = if (global) state.globalRules else state.settings.rules
    var ordered by remember(rules) { mutableStateOf(rules) }
    val visible = ordered.filter { it.value.contains(query, true) }
    LaunchedEffect(rules, pendingRuleId, pendingRuleValue, state.saving) {
        if (!state.saving && pendingRuleId != null && rules.any { it.id == pendingRuleId && it.value == pendingRuleValue }) {
            formId = null; pendingRuleId = null; pendingRuleValue = null
        }
    }
    fun persist(value: List<ExtensionRule>) { if (global) onGlobalRules(value) else onRules(value) }
    fun back() {
        if (!busy) {
            if (selected.isNotEmpty()) selected = emptySet()
            else if (subpage > 0) { subpage--; query = ""; sorting = false }
            else onDismiss()
        }
    }
    ProfileEditorPage(onDismissRequest = ::back, backTag = "profile_extension_back",
        title = { Text(when (subpage) { 1 -> "管理全局附加规则"; 2 -> "全局附加规则"; else -> "扩展" }) },
        confirmButton = {
            if (subpage == 0 && state.isCurrent && (!state.applied || state.applying)) TextButton(onClick = onApply, enabled = !busy,
                modifier = Modifier.testTag("profile_extension_apply")) {
                if (state.applying) CircularProgressIndicator(Modifier.size(20.dp)) else Text("立即应用")
            }
        }, text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_extension_error"))
                    Row {
                        TextButton(onClick = if (state.profile == null) onRetry else onApply, enabled = !busy) { Text("重试") }
                        TextButton(onClick = onSkipApply, enabled = !busy) { Text("暂不应用并退出") }
                    }
                }
                if (state.loading) CircularProgressIndicator(Modifier.padding(24.dp))
                state.profile?.let { profile ->
                    if (subpage == 0) {
                        Text(profile.realLabel, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (state.saving) "保存中…" else if (!state.isCurrent) "已保存，选用此配置时应用" else if (state.applied) "已应用" else "已保存，返回时自动应用",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ProfileFormSection {
                            listOf("standard" to "标准", "script" to "脚本").forEach { (mode, label) ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = !busy) { onMode(mode) }.testTag("profile_extension_mode_$mode"), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = state.mode == mode, onClick = null)
                                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                        Text(label, style = MaterialTheme.typography.titleSmall)
                                        Text(if (mode == "standard") "附加规则并设置默认目标" else "通过 JavaScript 修改配置", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (subpage == 1) {
                        OutlinedTextField(query, { query = it }, label = { Text("搜索全局规则") }, modifier = Modifier.fillMaxWidth().testTag("extension_global_search"), singleLine = true)
                        state.globalRules.filter { it.value.contains(query, true) }.forEach { rule ->
                            Row(Modifier.fillMaxWidth().clickable(!busy) { onDisabled(state.settings.disabledGlobalRuleIds.let { if (rule.id in it) it - rule.id else it + rule.id }) }, verticalAlignment = Alignment.CenterVertically) {
                                Text(rule.value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = rule.id !in state.settings.disabledGlobalRuleIds, onCheckedChange = { checked ->
                                    onDisabled(if (checked) state.settings.disabledGlobalRuleIds - rule.id else state.settings.disabledGlobalRuleIds + rule.id)
                                }, enabled = !busy, modifier = Modifier.testTag("extension_global_toggle_${rule.id}"))
                            }
                        }
                        if (state.globalRules.isEmpty()) Text("暂无全局附加规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { subpage = 2; query = "" }, modifier = Modifier.testTag("extension_global_edit")) { Text("前往配置全局附加规则") }
                    } else if (global || state.mode == "standard") {
                        if (global) OutlinedTextField(query, { query = it }, label = { Text("搜索规则") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selected.isEmpty()) "附加规则 · ${rules.size}" else "已选 ${selected.size} 项", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            if (selected.isNotEmpty()) {
                                IconButton(onClick = { selected = if (visible.all { it.id in selected }) emptySet() else visible.map { it.id }.toSet() }, enabled = !busy,
                                    modifier = Modifier.testTag("extension_select_all")) { Icon(Icons.Outlined.SelectAll, null) }
                                IconButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.testTag("extension_delete_selected")) { Icon(Icons.Outlined.Delete, null) }
                            } else {
                                IconButton(onClick = { sorting = !sorting }, enabled = !busy && rules.size > 1, modifier = Modifier.testTag("extension_sort")) { Icon(Icons.Outlined.SwapVert, null) }
                                IconButton(onClick = { showPresets = true }, enabled = !busy, modifier = Modifier.testTag("extension_presets")) { Icon(Icons.Outlined.AutoAwesome, null) }
                                IconButton(onClick = { formId = "new" }, enabled = !busy, modifier = Modifier.testTag("extension_rule_add")) { Icon(Icons.Outlined.Add, null) }
                            }
                        }
                        if (sorting) Text("长按拖动调整顺序，或使用上下移动按钮", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (rules.isEmpty()) Text("暂无附加规则", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        visible.forEach { rule -> key(rule.id) {
                            ExtensionRuleRow(rule, ordered.indexOf(rule), ordered.size, sorting, !busy, rule.id in selected,
                                onSelected = { selected = if (rule.id in selected) selected - rule.id else selected + rule.id },
                                onEdit = { formId = rule.id },
                                onMove = { to ->
                                    val from = ordered.indexOfFirst { it.id == rule.id }
                                    if (from >= 0 && to in ordered.indices) ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
                                }, onDrop = { persist(ordered) }, onCancel = { ordered = rules })
                        } }
                        if (!global) {
                            ProfileSectionRow("默认目标", profile.matchTarget ?: "跟随配置", Icons.Outlined.Route, !busy,
                                Modifier.testTag("extension_match_target")) { showTarget = true }
                            if (profile.matchTarget != null && profile.matchTarget !in state.targets) Text("默认目标已不在配置中，请重新选择", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { subpage = 1; selected = emptySet(); sorting = false }, modifier = Modifier.testTag("extension_global_control")) { Text("管理全局附加规则") }
                        }
                    } else {
                        Text("脚本", style = MaterialTheme.typography.titleSmall)
                        if (scripts.isEmpty()) Text("暂无脚本，请先前往配置脚本", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        scripts.forEach { script ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onScript(if (profile.scriptId == script.id) null else script.id) }
                                .padding(vertical = 8.dp).testTag("extension_script_${script.id}"), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Code, null)
                                Text(script.label, Modifier.weight(1f).padding(horizontal = 16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Checkbox(profile.scriptId == script.id, onCheckedChange = null)
                            }
                        }
                        TextButton(onClick = onOpenScripts, enabled = !busy, modifier = Modifier.testTag("extension_manage_scripts")) { Text("前往配置脚本") }
                    }
                    if (subpage == 0) {
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                        ProfileSectionRow("DNS", "覆盖当前配置的 DNS 设置", Icons.Outlined.Dns, canEditSource && !busy, Modifier.testTag("profile_extension_dns"), onDns)
                        ProfileSectionRow("Provider", "代理和规则资源", Icons.Outlined.Cloud, canEditSource && !busy, Modifier.testTag("profile_extension_providers"), onProviders)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        })
    if (showTarget) ExtensionTargetPicker(state.profile?.matchTarget, state.targets, true, { showTarget = false }) { onTarget(it); showTarget = false }
    if (showPresets) ExtensionPresets({ showPresets = false }) { values ->
        persist(rules + values.map { ExtensionRule(UUID.randomUUID().toString(), it) }); showPresets = false
    }
    formId?.let { id ->
        val initial = rules.firstOrNull { it.id == id }
        ExtensionRuleForm(initial, state.targets, state.saving, state.error, { formId = null; pendingRuleId = null; pendingRuleValue = null }) { rule ->
            pendingRuleId = rule.id; pendingRuleValue = rule.value
            persist(if (initial == null) rules + rule else rules.map { if (it.id == rule.id) rule else it })
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除规则") }, text = { Text("删除所选 ${selected.size} 条规则？") },
        confirmButton = { TextButton(onClick = { persist(rules.filterNot { it.id in selected }); selected = emptySet(); confirmDelete = false }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

@Composable
private fun ExtensionRuleRow(rule: ExtensionRule, index: Int, count: Int, sorting: Boolean, enabled: Boolean, selected: Boolean,
    onSelected: () -> Unit, onEdit: () -> Unit, onMove: (Int) -> Unit, onDrop: () -> Unit, onCancel: () -> Unit) {
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val cancel by rememberUpdatedState(onCancel)
    val position by rememberUpdatedState(index)
    val extent = with(LocalDensity.current) { 76.dp.toPx() }
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).then(if (sorting && enabled) Modifier.pointerInput(rule.id, count) {
        var distance = 0f
        var dragging = false
        // 作者 long：删除/筛选会取消旧列表项的手势，取消时绝不能提交旧顺序，否则会把刚删除的规则重新写回。
        detectDragGesturesAfterLongPress(onDragStart = { distance = 0f; dragging = true }, onDragEnd = {
            if (dragging) { dragging = false; drop() }
        }, onDragCancel = {
            if (dragging) { dragging = false; cancel() }
        }, onDrag = { change, delta ->
            change.consume(); distance += delta.y
            if (abs(distance) >= extent) { val to = (position + if (distance > 0) 1 else -1).coerceIn(0, count - 1); if (to != position) move(to); distance = 0f }
        })
    } else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, { onSelected() }, enabled = enabled, modifier = Modifier.testTag("extension_rule_select_${rule.id}"))
        Text(rule.value, Modifier.weight(1f).clickable(enabled) { onEdit() }.padding(vertical = 12.dp).testTag("extension_rule_${rule.id}"), maxLines = 3, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
        if (sorting) {
            IconButton(onClick = { move(index - 1); drop() }, enabled = enabled && index > 0, modifier = Modifier.testTag("extension_up_${rule.id}")) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
            IconButton(onClick = { move(index + 1); drop() }, enabled = enabled && index < count - 1, modifier = Modifier.testTag("extension_down_${rule.id}")) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
        }
    }
}

@Composable
private fun ExtensionTargetPicker(selected: String?, targets: List<String>, follow: Boolean, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ProfileEditorPage(onDismiss, { Text("选择目标") }, {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索目标") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (follow && query.isBlank()) TextButton(onClick = { onSelect(null) }, modifier = Modifier.testTag("extension_follow_profile")) { Text("跟随配置") }
            targets.filter { it.contains(query, true) }.forEach { target ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(target) }.padding(vertical = 12.dp).testTag("extension_target_$target"), verticalAlignment = Alignment.CenterVertically) {
                    Text(target, Modifier.weight(1f)); if (target == selected) Icon(Icons.Outlined.Check, null)
                }
            }
        }
    })
}

@Composable
internal fun ExtensionRuleForm(initial: ExtensionRule?, targets: List<String>, saving: Boolean, saveError: String?, onDismiss: () -> Unit, onSave: (ExtensionRule) -> Unit) {
    val fields = remember(initial) { initial?.let { ExtensionRules.fields(it.value) }.orEmpty() }
    var type by rememberSaveable { mutableStateOf(fields.firstOrNull() ?: "DOMAIN-SUFFIX") }
    var content by rememberSaveable { mutableStateOf(if (fields.size > 2) fields[1] else "") }
    var target by rememberSaveable { mutableStateOf(fields.getOrNull(if (fields.lastOrNull() == "no-resolve") fields.lastIndex - 1 else fields.lastIndex) ?: "DIRECT") }
    var noResolve by rememberSaveable { mutableStateOf(fields.lastOrNull() == "no-resolve") }
    var rawMode by rememberSaveable { mutableStateOf(false) }
    var raw by rememberSaveable { mutableStateOf(initial?.value.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectTarget by rememberSaveable { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val value = if (rawMode) raw.trim() else listOfNotNull(type, content.trim().takeUnless { type in setOf("MATCH", "FINAL") }, target, "no-resolve".takeIf { noResolve }).joinToString(",")
    fun back() { if (saving) return; if (value != initial?.value && (initial != null || content.isNotEmpty() || raw.isNotEmpty())) discard = true else onDismiss() }
    ProfileEditorPage(::back, { Text(if (initial == null) "添加规则" else "编辑规则") }, {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = {
                if (!rawMode) { raw = value; rawMode = true } else runCatching {
                    ExtensionRules.validate(listOf(ExtensionRule("draft", raw)))
                    val parsed = ExtensionRules.fields(raw)
                    type = parsed.first(); content = if (parsed.size > 2) parsed[1] else ""
                    noResolve = parsed.last() == "no-resolve"
                    target = parsed[if (noResolve) parsed.lastIndex - 1 else parsed.lastIndex]
                    rawMode = false; error = null
                }.onFailure { error = it.message }
            }, enabled = !saving, modifier = Modifier.testTag("extension_rule_raw_mode")) { Text(if (rawMode) "表单编辑" else "原始规则") }
            if (rawMode) OutlinedTextField(raw, { raw = it }, label = { Text("规则") }, modifier = Modifier.fillMaxWidth().testTag("extension_rule_raw"))
            else {
                Box {
                    OutlinedButton(onClick = { typeMenu = true }) { Text("类型：$type") }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        listOf("DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-REGEX", "GEOSITE", "GEOIP", "IP-CIDR", "IP-CIDR6", "SRC-IP-CIDR", "DST-PORT", "SRC-PORT", "PROCESS-NAME", "PROCESS-PATH", "NETWORK", "UID", "AND", "OR", "NOT", "MATCH").forEach { action ->
                            DropdownMenuItem({ Text(action) }, onClick = { type = action; typeMenu = false })
                        }
                    }
                }
                if (type !in setOf("MATCH", "FINAL")) OutlinedTextField(content, { content = it }, label = { Text("匹配内容") }, modifier = Modifier.fillMaxWidth().testTag("extension_rule_content"))
                ProfileSectionRow("目标", target, Icons.Outlined.Route, true, Modifier.testTag("extension_rule_target")) { selectTarget = true }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(noResolve, { noResolve = it }); Text("no-resolve") }
            }
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = {
        if (saving) return@TextButton
        val rule = ExtensionRule(initial?.id ?: UUID.randomUUID().toString(), value)
        runCatching { ExtensionRules.validate(listOf(rule)) }.onSuccess { onSave(rule) }.onFailure { error = it.message }
    }, enabled = !saving, modifier = Modifier.testTag("extension_rule_confirm")) { Text(if (saving) "保存中…" else "确认") } })
    if (selectTarget) ExtensionTargetPicker(target, (listOf("MATCH") + targets).distinct(), false, { selectTarget = false }) { target = it ?: "DIRECT"; selectTarget = false }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存修改？") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}

private val extensionPresets = listOf(
    "阻止 QUIC" to listOf("AND,((NETWORK,UDP),(DST-PORT,443)),REJECT-DROP"),
    "阻止 STUN" to listOf("AND,((NETWORK,UDP),(DST-PORT,3478/19302)),REJECT-DROP"),
    "阻止 DNS over TLS" to listOf("DST-PORT,853,REJECT"),
    "阻止回环连接" to listOf("IP-CIDR,127.0.0.0/8,REJECT,no-resolve", "IP-CIDR,0.0.0.0/32,REJECT,no-resolve", "IP-CIDR6,::1/128,REJECT,no-resolve", "IP-CIDR6,::/128,REJECT,no-resolve", "AND,((IN-TYPE,TUN),(DST-PORT,853)),REJECT"),
    "局域网直连" to listOf("GEOSITE,private,DIRECT", "GEOIP,LAN,DIRECT,no-resolve"),
    "系统服务直连" to listOf("GEOSITE,apple,DIRECT", "GEOSITE,microsoft,DIRECT"),
    "BitTorrent 直连" to listOf("GEOSITE,category-public-tracker,DIRECT", "GEOSITE,category-pt,DIRECT", "PROCESS-NAME-REGEX,(?i)^(qbittorrent|transmission.*|deluge.*|aria2c|motrix|utorrent|bitcomet)(\\.exe)?$,DIRECT"),
)

@Composable
internal fun ExtensionPresets(onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(listOf<Int>()) }
    ProfileEditorPage(onDismiss, { Text("规则预设") }, {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            extensionPresets.forEachIndexed { index, (label, rules) ->
                Row(Modifier.fillMaxWidth().clickable { selected = if (index in selected) selected - index else selected + index }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(label); Text(rules.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Checkbox(index in selected, onCheckedChange = null)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onAdd(extensionPresets.filterIndexed { index, _ -> index in selected }.flatMap { it.second }) }, enabled = selected.isNotEmpty(),
        modifier = Modifier.testTag("extension_preset_confirm")) { Text("添加") } })
}
