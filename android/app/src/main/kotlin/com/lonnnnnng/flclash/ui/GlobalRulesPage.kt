package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.service.ExtensionRule
import java.util.UUID

@Composable
internal fun GlobalRulesRoute(controller: GlobalRulesController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    LaunchedEffect(Unit) { controller.open() }
    GlobalRulesPage(state, onDismiss, controller::save, controller::open, controller::retryApply)
}

/** 作者 long：全局目录独立于 Profile，稳定 ID 供配置禁用引用；编辑或排序只提交真实目录，不把搜索结果当全量保存。 */
@Composable
internal fun GlobalRulesPage(state: GlobalRulesUiState, onDismiss: () -> Unit, onSave: (List<ExtensionRule>) -> Unit,
    onReload: () -> Unit, onApply: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingValue by rememberSaveable { mutableStateOf<String?>(null) }
    var presets by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf(setOf<String>()) }
    val busy = state.loading || state.saving || state.applying
    val visible = state.rules.filter { it.value.contains(query, true) }
    LaunchedEffect(state.rules, state.saving) {
        selected = selected.intersect(state.rules.map { it.id }.toSet())
        if (pendingId != null && !state.saving && state.rules.any { it.id == pendingId && it.value == pendingValue }) {
            pendingId = null; pendingValue = null; form = null
        }
    }
    fun back() {
        if (busy) return
        if (selecting) { selecting = false; selected = emptySet() }
        else if (sorting) sorting = false else onDismiss()
    }
    ProfileEditorPage(::back, { Text("附加规则") }, {
        Column(Modifier.fillMaxWidth().testTag("page_global_rules"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!sorting) OutlinedTextField(query, { query = it; selected = emptySet() }, singleLine = true, enabled = !busy,
                label = { Text("搜索规则") }, modifier = Modifier.fillMaxWidth().testTag("global_rules_search"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selecting) "已选 ${selected.size} 项" else "共 ${state.rules.size} 条规则", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (selecting) {
                    IconButton({ selected = if (visible.all { it.id in selected }) emptySet() else visible.map { it.id }.toSet() }, enabled = !busy,
                        modifier = Modifier.testTag("global_rules_all")) { Icon(Icons.Outlined.SelectAll, null) }
                    IconButton({ delete = selected.intersect(visible.map { it.id }.toSet()) }, enabled = !busy && selected.isNotEmpty(),
                        modifier = Modifier.testTag("global_rules_delete")) { Icon(Icons.Outlined.Delete, null) }
                    IconButton({ selecting = false; selected = emptySet() }, enabled = !busy) { Icon(Icons.Outlined.Close, null, modifier = Modifier.testTag("action:取消选择")) }
                } else if (sorting) TextButton({ sorting = false }, enabled = !busy) { Text("完成") }
                else {
                    IconButton({ selecting = true }, enabled = !busy && visible.isNotEmpty(), modifier = Modifier.testTag("global_rules_select")) { Icon(Icons.Outlined.Checklist, null) }
                    IconButton({ sorting = true }, enabled = !busy && query.isBlank() && state.rules.size > 1, modifier = Modifier.testTag("global_rules_sort")) { Icon(Icons.Outlined.SwapVert, null) }
                    IconButton({ presets = true }, enabled = !busy && state.loaded, modifier = Modifier.testTag("global_rules_presets")) { Icon(Icons.Outlined.AutoAwesome, null) }
                    IconButton({ form = "new" }, enabled = !busy && state.loaded, modifier = Modifier.testTag("global_rules_add")) { Icon(Icons.Outlined.Add, null) }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_rules_error"))
                Row {
                    TextButton(onApply, enabled = !busy && state.loaded) { Text("重试应用") }
                    TextButton(onReload, enabled = !busy, modifier = Modifier.testTag("global_rules_reload")) { Text("重新读取") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!busy && visible.isEmpty()) Text(if (query.isBlank()) "暂无附加规则" else "没有匹配的规则", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sorting) ResourceOrderList(state.rules.map { ResourceOrderItem(it.id, it.value, "长按调整顺序") }, busy, "global_rules", { ids ->
                val byId = state.rules.associateBy { it.id }
                onSave(ids.map { byId.getValue(it) })
            }, Modifier.fillMaxWidth().weight(1f))
            else LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("global_rules_list")) {
                items(visible, key = { it.id }) { rule ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = !busy) {
                        if (selecting) selected = if (rule.id in selected) selected - rule.id else selected + rule.id else form = rule.id
                    }.padding(vertical = 12.dp).testTag("global_rule_${rule.id}"), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Checkbox(rule.id in selected, null, enabled = !busy)
                        Text(rule.value, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        if (!selecting) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }, backTag = "global_rules_back")
    form?.let { id ->
        ExtensionRuleForm(state.rules.firstOrNull { it.id == id }, listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS"), busy, state.error,
            onDismiss = { form = null; pendingId = null; pendingValue = null }, onSave = { rule ->
                pendingId = rule.id; pendingValue = rule.value
                onSave(if (state.rules.any { it.id == rule.id }) state.rules.map { if (it.id == rule.id) rule else it } else state.rules + rule)
            })
    }
    if (presets) ExtensionPresets({ presets = false }) { values ->
        presets = false
        onSave(state.rules + values.filterNot { value -> state.rules.any { it.value == value } }.distinct().map { ExtensionRule(UUID.randomUUID().toString(), it) })
    }
    if (delete.isNotEmpty()) AlertDialog(onDismissRequest = { if (!busy) delete = emptySet() }, title = { Text("删除 ${delete.size} 条规则？") },
        text = { Text("配置中引用这些全局规则的记录将不再生效。") }, confirmButton = {
            TextButton({ onSave(state.rules.filterNot { it.id in delete }); delete = emptySet() }, enabled = !busy, modifier = Modifier.testTag("global_rules_delete_confirm")) { Text("删除") }
        }, dismissButton = { TextButton({ delete = emptySet() }, enabled = !busy) { Text("取消") } })
}
