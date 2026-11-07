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
    if (state.recoveryFailed) {
        ProfileEditorPage({}, { Text("附加规则") }, {
            Text(state.error.orEmpty())
            TextButton(controller::retryRecovery, modifier = Modifier.testTag("global_rules_recovery_retry")) { Text("重试恢复") }
        })
        return
    }
    if (!state.ready || !state.loaded) {
        ProfileEditorPage({}, { Text("附加规则") }, {
            if (state.loading || !state.ready) CircularProgressIndicator()
            else { Text(state.error.orEmpty()); TextButton(controller::reload) { Text("重新读取") } }
        })
        return
    }
    val draft = state.draft ?: return
    EncryptedEditorState(draft.session, draft.uiDraft, { controller.ui(draft.session, it) }, controller::uiFailure) { checkpoint ->
        GlobalRulesPage(state, { controller.close(onDismiss) }, controller::save, controller::reload, controller::retryApply,
            onSaveSnapshot = { before, after -> if (checkpoint()) controller.save(before, after) },
            onRetryPending = controller::retryPending, onDiscardPending = controller::discardPending)
    }
}

/** 作者 long：编辑原值、批选和排序快照属于当前会话，不能在重建时被最新目录偷偷替换。 */
@Composable
internal fun GlobalRulesPage(state: GlobalRulesUiState, onDismiss: () -> Unit, onSave: (List<ExtensionRule>) -> Unit,
    onReload: () -> Unit, onApply: () -> Unit,
    onSaveSnapshot: ((List<ExtensionRule>, List<ExtensionRule>) -> Unit)? = null,
    onRetryPending: () -> Unit = {}, onDiscardPending: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var sortSnapshot by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var sortVersion by rememberSaveable { mutableStateOf(0L) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var originalValue by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingValue by rememberSaveable { mutableStateOf<String?>(null) }
    var presets by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleteVersion by rememberSaveable { mutableStateOf<Long?>(null) }
    val busy = state.loading || state.saving || state.applying
    val canChange = !busy && !state.interrupted
    val terms = query.lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    val visible = state.rules.filter { rule -> terms.all { rule.value.contains(it, true) } }
    LaunchedEffect(state.rules, state.saving, state.saveResultVersion, state.interrupted) {
        if (!state.loaded || state.loading) return@LaunchedEffect
        selected = selected.filter { id -> state.rules.any { it.id == id } }
        if (!state.saving && !state.interrupted) {
            if (delete.isNotEmpty() && state.rules.none { it.id in delete } && state.error == null) delete = emptyList()
            if (pendingId != null && state.rules.any { it.id == pendingId && it.value == pendingValue }) {
                pendingId = null; pendingValue = null; form = null; originalValue = null
            }
            if (deleteVersion != null && state.saveResultVersion > deleteVersion!!) {
                if (state.rules.none { it.id in delete }) delete = emptyList()
                deleteVersion = null
            }
        }
        if (state.interrupted) deleteVersion = null
    }
    fun save(before: List<ExtensionRule>, after: List<ExtensionRule>) {
        if (canChange) onSaveSnapshot?.invoke(before, after) ?: onSave(after)
    }
    fun back() {
        if (busy) return
        if (selecting) { selecting = false; selected = emptyList() }
        else if (sorting) { sorting = false; sortSnapshot = emptyList() }
        else if (query.isNotEmpty()) query = ""
        else onDismiss()
    }
    ProfileEditorPage(::back, { Text("附加规则") }, {
        Column(Modifier.fillMaxWidth().testTag("page_global_rules"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!sorting) OutlinedTextField(query, { query = it }, singleLine = true, enabled = !busy,
                label = { Text("搜索规则") }, modifier = Modifier.fillMaxWidth().testTag("global_rules_search"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selecting) "已选 ${selected.size} 项" else "共 ${state.rules.size} 条规则", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (selecting) {
                    IconButton({ selected = if (visible.all { it.id in selected }) emptyList() else visible.map { it.id } }, enabled = canChange,
                        modifier = Modifier.testTag("global_rules_all")) { Icon(Icons.Outlined.SelectAll, null) }
                    IconButton({ delete = selected.filter { id -> visible.any { it.id == id } } }, enabled = canChange && visible.any { it.id in selected },
                        modifier = Modifier.testTag("global_rules_delete")) { Icon(Icons.Outlined.Delete, null) }
                    IconButton({ selecting = false; selected = emptyList() }, enabled = !busy, modifier = Modifier.testTag("global_rules_cancel_selection")) { Icon(Icons.Outlined.Close, null) }
                } else if (sorting) TextButton({ sorting = false; sortSnapshot = emptyList() }, enabled = !busy, modifier = Modifier.testTag("global_rules_sort_done")) { Text("完成") }
                else {
                    IconButton({ selecting = true }, enabled = canChange && visible.isNotEmpty(), modifier = Modifier.testTag("global_rules_select")) { Icon(Icons.Outlined.Checklist, null) }
                    IconButton({ sorting = true; sortVersion = state.saveResultVersion; sortSnapshot = state.rules.flatMap { listOf(it.id, it.value) } }, enabled = canChange && query.isBlank() && state.rules.size > 1, modifier = Modifier.testTag("global_rules_sort")) { Icon(Icons.Outlined.SwapVert, null) }
                    IconButton({ presets = true }, enabled = canChange && state.loaded, modifier = Modifier.testTag("global_rules_presets")) { Icon(Icons.Outlined.AutoAwesome, null) }
                    IconButton({ form = "new"; originalValue = null }, enabled = canChange && state.loaded, modifier = Modifier.testTag("global_rules_add")) { Icon(Icons.Outlined.Add, null) }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_rules_error"))
                Row {
                    if (state.interrupted) {
                        TextButton(onRetryPending, enabled = !busy && !state.blocked, modifier = Modifier.testTag("global_rules_retry")) { Text(if (state.draft?.pending?.committed == true) "继续编辑" else "重试保存") }
                        TextButton(onDiscardPending, enabled = !busy, modifier = Modifier.testTag("global_rules_discard_pending")) { Text("放弃这次保存") }
                    } else {
                        TextButton(onReload, enabled = !busy, modifier = Modifier.testTag("global_rules_reload")) { Text("重新读取") }
                    }
                }
            }
            if (state.draft?.applyPending == true && !state.interrupted) TextButton(onApply, enabled = !busy,
                modifier = Modifier.testTag("global_rules_apply")) { Text("应用到原配置") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!busy && visible.isEmpty()) Text(if (query.isBlank()) "暂无附加规则" else "没有匹配的规则", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sorting) {
                val ordered = sortSnapshot.chunked(2).map { ExtensionRule(it[0], it[1]) }
                ResourceOrderList(ordered.map { ResourceOrderItem(it.id, it.value, "长按调整顺序") }, !canChange, "global_rules", { ids ->
                    val byId = ordered.associateBy { it.id }
                    save(ordered, ids.map { byId.getValue(it) })
                }, Modifier.fillMaxWidth().weight(1f))
                LaunchedEffect(state.saveResultVersion, state.saving, state.interrupted) {
                    if (!state.saving && !state.interrupted && state.error == null && state.saveResultVersion > sortVersion) {
                        sortSnapshot = state.rules.flatMap { listOf(it.id, it.value) }; sortVersion = state.saveResultVersion
                    }
                }
            } else LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("global_rules_list")) {
                items(visible, key = { it.id }) { rule ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = canChange) {
                        if (selecting) selected = if (rule.id in selected) selected - rule.id else selected + rule.id
                        else { form = rule.id; originalValue = rule.value }
                    }.padding(vertical = 12.dp).testTag("global_rule_${rule.id}"), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Checkbox(rule.id in selected, null, enabled = canChange)
                        Text(rule.value, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        if (!selecting) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }, backTag = "global_rules_back")
    form?.let { id ->
        val initial = originalValue?.let { ExtensionRule(id, it) }
        val actual = state.rules.firstOrNull { it.id == id }
        val ownResult = pendingId == id && actual?.value == pendingValue
        val conflict = if (state.blocked || id != "new" && !ownResult && actual != initial)
            "原规则已被修改或删除，草稿已保留，请返回后重新打开" else null
        ExtensionRuleForm(initial, listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS"), busy, state.error,
            blockedMessage = conflict, onRetryPending = onRetryPending.takeIf { state.interrupted },
            onDiscardPending = onDiscardPending.takeIf { state.interrupted }, committed = state.draft?.pending?.committed == true,
            onDismiss = { form = null; originalValue = null; pendingId = null; pendingValue = null; if (state.interrupted) onDiscardPending() }, onSave = { rule ->
                if (conflict == null && canChange) {
                    pendingId = rule.id; pendingValue = rule.value
                    save(state.rules, if (id == "new") state.rules + rule else state.rules.map { if (it.id == id) rule else it })
                }
            })
    }
    if (presets) ExtensionPresets({ presets = false }) { values ->
        presets = false
        save(state.rules, state.rules + values.filterNot { value -> state.rules.any { it.value == value } }.distinct().map { ExtensionRule(UUID.randomUUID().toString(), it) })
    }
    if (delete.isNotEmpty()) EditorAlertDialog(onDismissRequest = { if (!busy) { delete = emptyList(); if (state.interrupted) onDiscardPending() } },
        title = { Text("删除 ${delete.size} 条规则？") }, text = {
            Column {
                Text("配置中引用这些全局规则的记录将不再生效。")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_rules_delete_error")) }
                if (state.interrupted) TextButton(onRetryPending, enabled = !busy && !state.blocked, modifier = Modifier.testTag("global_rules_delete_retry")) {
                    Text(if (state.draft?.pending?.committed == true) "继续编辑" else "重试删除")
                }
            }
        }, confirmButton = {
            TextButton({
                // 作者 long：确认保持到事务成功；失败不清选择，取消不会再触发删除。
                deleteVersion = state.saveResultVersion
                save(state.rules, state.rules.filterNot { it.id in delete })
            }, enabled = canChange, modifier = Modifier.testTag("global_rules_delete_confirm")) { Text(if (busy) "删除中…" else "删除") }
        }, dismissButton = {
            TextButton({ delete = emptyList(); deleteVersion = null; if (state.interrupted) onDiscardPending() }, enabled = !busy,
                modifier = Modifier.testTag("global_rules_delete_cancel")) { Text("取消") }
        })
}
