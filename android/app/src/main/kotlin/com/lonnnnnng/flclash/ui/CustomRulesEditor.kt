package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private fun rulesDigest(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/** 作者 long：规则操作逐次保存后仍留在列表；等待回读成功再关闭子页，写入失败保留编辑内容供修复。 */
@Composable
internal fun ProfileRulesEditorDialog(profile: Profile, initialRules: String, saving: Boolean, onDismiss: () -> Unit,
    onSave: (String) -> Unit, catalog: CustomRuleCatalog = CustomRuleCatalog(), saveError: String? = null,
    interruptedRules: String? = null, onDiscardInterrupted: () -> Unit = {}) {
    // 作者 long：表单保存的是行身份，重建必须恢复同一组 key；仅保存摘要和 key，避免把整份规则正文重复塞进系统 Bundle。
    val rowsSaver = listSaver<List<CustomRuleDraft>, String>(
        save = { values -> listOf(rulesDigest(CustomRules.editorText(values))) + values.map { it.key } },
        restore = { saved ->
            val current = CustomRules.decode(initialRules, validate = false)
            if (saved.size == current.size + 1 && saved.first() == rulesDigest(CustomRules.editorText(current)))
                current.mapIndexed { index, row -> row.copy(key = saved[index + 1]) }
            else current
        },
    )
    var rows by rememberSaveable(profile.id, stateSaver = rowsSaver) { mutableStateOf(CustomRules.decode(initialRules, validate = false)) }
    val pendingSaver = listSaver<List<CustomRuleDraft>?, String>(
        save = { values -> values.orEmpty().flatMap { listOf(it.key, it.value) } },
        restore = { saved -> saved.takeIf { it.isNotEmpty() }?.chunked(2)?.map { CustomRuleDraft(it[0], it[1]) } },
    )
    var pending by rememberSaveable(profile.id, stateSaver = pendingSaver) { mutableStateOf<List<CustomRuleDraft>?>(null) }
    var retryRules by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var discardOperation by rememberSaveable(profile.id) { mutableStateOf(false) }
    var pendingDigest by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(profile.id) { mutableStateOf("") }
    var search by rememberSaveable(profile.id) { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(profile.id, stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(setOf<String>()) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var yaml by rememberSaveable { mutableStateOf(false) }
    var presets by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = saving || pendingDigest != null
    val visible = rows.filter { it.value.contains(query, ignoreCase = true) }
    val issues = remember(rows, catalog) { rows.mapNotNull { rule -> catalog.issue(rule.value)?.let { rule.key to it } }.toMap() }
    LaunchedEffect(initialRules, saving, saveError, pendingDigest, interruptedRules) {
        if (!saving) {
            if (saveError != null) {
                // 作者 long：排序先在本地预览，写入失败必须回到已保存顺序；表单和 YAML 子页另持草稿供重试。
                if (interruptedRules != null) {
                    // 作者 long：提交前被回收的批选/排序仍是用户操作，保留原行身份和结果，由用户确认重试，不能自动重放。
                    retryRules = interruptedRules
                    rows = pending ?: CustomRules.decode(interruptedRules, previous = rows, validate = false)
                } else if (retryRules == null) rows = CustomRules.decode(initialRules, previous = rows, validate = false)
                pending = null; pendingDigest = null
            }
            else if (pendingDigest == null || rulesDigest(initialRules) == pendingDigest) {
                rows = CustomRules.decode(initialRules, previous = pending ?: rows, validate = false)
                if (pendingDigest != null) { form = null; yaml = false; pending = null; pendingDigest = null; selected = emptySet() }
                selected = selected.intersect(rows.map { it.key }.toSet())
            }
        }
    }
    fun persist(next: List<CustomRuleDraft>) {
        if (saving || pendingDigest != null) return
        runCatching { CustomRules.validateAll(next, unchangedValues = CustomRules.decode(initialRules, validate = false).map { it.value }.toSet()) }.onSuccess {
            val text = CustomRules.editorText(next)
            error = null; retryRules = null; pending = next; pendingDigest = rulesDigest(text); onSave(text)
        }.onFailure { error = it.message ?: "规则无效，请检查后重试" }
    }
    fun duplicate(keys: Set<String>) = persist(rows.flatMap { if (it.key in keys) listOf(it, it.copy(key = UUID.randomUUID().toString())) else listOf(it) })
    fun back() {
        if (!busy) when {
            selecting -> { selecting = false; selected = emptySet() }
            search -> { search = false; query = "" }
            retryRules != null -> discardOperation = true
            else -> onDismiss()
        }
    }
    ProfileEditorPage(::back, title = { Text(if (selecting) tr("已选 {0} 项", selected.size) else tr("规则 · {0}", profile.realLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        backTag = "custom_rules_back", confirmButton = {
            if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp))
            else if (selecting) {
                IconButton(onClick = { selected = if (visible.all { it.key in selected }) selected - visible.map { it.key }.toSet() else selected + visible.map { it.key } }, modifier = Modifier.testTag("custom_rules_select_all")) { Icon(Icons.Outlined.SelectAll, null, modifier = Modifier.testTag("action:全选当前结果")) }
                IconButton(onClick = { duplicate(selected) }, enabled = selected.isNotEmpty(), modifier = Modifier.testTag("custom_rules_duplicate_selected")) { Icon(Icons.Outlined.ContentCopy, null) }
                IconButton(onClick = { delete = selected }, enabled = selected.isNotEmpty(), modifier = Modifier.testTag("custom_rules_delete_selected")) { Icon(Icons.Outlined.Delete, null) }
            } else {
                // 作者 long：筛选后的行号不代表完整规则位置，进入搜索即结束排序，避免把过滤位置写入源列表。
                IconButton(onClick = { sorting = false; search = !search; if (!search) query = "" }, modifier = Modifier.testTag("custom_rules_search_toggle")) { Icon(if (search) Icons.Outlined.Close else Icons.Outlined.Search, null) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("custom_rules_menu")) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("快捷 YAML")) }, onClick = { menu = false; yaml = true }, modifier = Modifier.testTag("custom_rules_yaml"))
                        DropdownMenuItem(text = { Text(tr("规则预设")) }, onClick = { menu = false; presets = true }, modifier = Modifier.testTag("custom_rules_presets"))
                        DropdownMenuItem(text = { Text(tr("批量选择")) }, onClick = { menu = false; selecting = true; sorting = false }, modifier = Modifier.testTag("custom_rules_select"))
                        DropdownMenuItem(text = { Text(if (sorting) tr("完成排序") else tr("排序")) }, onClick = { menu = false; sorting = !sorting; search = false; query = "" }, enabled = rows.size > 1, modifier = Modifier.testTag("custom_rules_sort"))
                    }
                }
                IconButton(onClick = { form = "new" }, modifier = Modifier.testTag("custom_rules_add")) { Icon(Icons.Outlined.Add, null) }
            }
        }, text = {
            Column(Modifier.fillMaxSize()) {
                SearchOptionsTransition(search) {
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(tr("搜索类型、内容或策略")) }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("custom_rules_search"))
                }
                (error ?: saveError)?.let { Text(tr(it), Modifier.padding(vertical = 8.dp).testTag("custom_rules_error"), color = MaterialTheme.colorScheme.error) }
                retryRules?.let { text -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !busy, modifier = Modifier.testTag("custom_rules_retry_interrupted"), onClick = {
                        persist(CustomRules.decode(text, previous = rows, validate = false))
                    }) { Text(tr("重试上次操作")) }
                    TextButton(enabled = !busy, modifier = Modifier.testTag("custom_rules_discard_interrupted"), onClick = {
                        retryRules = null; onDiscardInterrupted(); rows = CustomRules.decode(initialRules, previous = rows, validate = false)
                        selected = selected.intersect(rows.map { it.key }.toSet())
                    }) { Text(tr("放弃上次操作")) }
                } }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (query.isBlank()) tr("共 {0} 条规则", rows.size) else tr("{0} / {1} 条规则", visible.size, rows.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (busy) tr("保存中…") else tr("操作后自动保存"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (issues.isNotEmpty()) Text(tr("{0} 条规则需要检查；可编辑修复目标或资源引用", issues.size), Modifier.padding(bottom = 12.dp).testTag("custom_rules_issues"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (sorting) Text(tr("长按拖动调整顺序，松手保存；也可使用上下移动按钮"), Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall)
                if (visible.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Outlined.Rule, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (rows.isEmpty()) tr("暂无规则") else tr("没有匹配的规则"), Modifier.padding(top = 16.dp).testTag("custom_rules_empty"), style = MaterialTheme.typography.titleMedium)
                    if (rows.isEmpty()) TextButton(onClick = { form = "new" }, enabled = !busy) { Text(tr("添加规则")) }
                } else LazyColumn(Modifier.fillMaxSize().testTag("custom_rules_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(visible, key = { _, rule -> rule.key }) { index, rule ->
                        CustomRuleRow(rule, index, rows.size, issues[rule.key], selecting, rule.key in selected, sorting, !busy,
                            onSelect = { selecting = true; sorting = false; selected = if (rule.key in selected) selected - rule.key else selected + rule.key },
                            onEdit = { form = rule.key }, onDuplicate = { duplicate(setOf(rule.key)) }, onDelete = { delete = setOf(rule.key) },
                            onMove = { to -> val from = rows.indexOfFirst { it.key == rule.key }; if (from >= 0 && to in rows.indices) rows = rows.toMutableList().apply { add(to, removeAt(from)) } },
                            onDrop = { persist(rows) }, onCancel = { rows = CustomRules.decode(initialRules, previous = rows, validate = false) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        })
    form?.let { key ->
        val original = rows.firstOrNull { it.key == key }
        // 作者 long：源列表在外部更新后找不到原行时不能把编辑误作新增，重复规则也必须按身份定位。
        if (key != "new" && original == null && !busy) EditorAlertDialog(onDismissRequest = { form = null },
            title = { Text(tr("原规则已变更")) }, text = { Text(tr("请返回列表，重新选择要编辑的规则。")) },
            confirmButton = { TextButton(onClick = { form = null }) { Text(tr("返回列表")) } })
        else CustomRuleForm(original, catalog, busy, error ?: saveError, { form = null; error = null }, { delete = setOf(key) }) { value ->
            if (key != "new" && original == null) return@CustomRuleForm
            val next = original?.copy(value = value) ?: CustomRuleDraft(value = value)
            persist(if (original == null) rows + next else rows.map { if (it.key == key) next else it })
        }
    }
    if (yaml) CustomRuleYaml(rows, busy, error ?: saveError, { yaml = false; error = null }, ::persist)
    if (presets) ExtensionPresets({ presets = false }) { values -> presets = false; persist(rows + values.map { CustomRuleDraft(value = it) }) }
    if (discardOperation) EditorAlertDialog(onDismissRequest = { discardOperation = false }, title = { Text(tr("放弃上次未完成的规则操作？")) },
        confirmButton = { TextButton(onClick = { onDiscardInterrupted(); onDismiss() }) { Text(tr("放弃")) } },
        dismissButton = { TextButton(onClick = { discardOperation = false }) { Text(tr("继续编辑")) } })
    if (delete.isNotEmpty()) EditorAlertDialog(onDismissRequest = { delete = emptySet() }, title = { Text(tr("删除规则")) }, text = { Text(tr("删除所选 {0} 条规则？", delete.size)) },
        confirmButton = { TextButton(onClick = { val ids = delete; delete = emptySet(); persist(rows.filterNot { it.key in ids }) }, modifier = Modifier.testTag("custom_rules_delete_confirm")) { Text(tr("删除")) } },
        dismissButton = { TextButton(onClick = { delete = emptySet() }) { Text(tr("取消")) } })
}

@Composable
private fun CustomRuleRow(rule: CustomRuleDraft, index: Int, count: Int, issue: String?, selecting: Boolean, selected: Boolean, sorting: Boolean,
    enabled: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit, onDrop: () -> Unit, onCancel: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val move by rememberUpdatedState(onMove); val drop by rememberUpdatedState(onDrop); val cancel by rememberUpdatedState(onCancel)
    val position by rememberUpdatedState(index)
    val extent = with(LocalDensity.current) { 88.dp.toPx() }
    val fields = remember(rule.value) { CustomRules.fields(rule.value) }
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).testTag("custom_rule_row_$index").then(if (sorting && enabled) Modifier.pointerInput(rule.key, count) {
        var distance = 0f; var dragging = false
        // 作者 long：拖动期间只预览顺序，松手统一保存；手势取消恢复回读顺序，不能把已删除的规则写回来。
        detectDragGesturesAfterLongPress(onDragStart = { distance = 0f; dragging = true }, onDragEnd = { if (dragging) { dragging = false; drop() } },
            onDragCancel = { if (dragging) { dragging = false; cancel() } }, onDrag = { change, delta ->
                change.consume(); distance += delta.y
                if (abs(distance) >= extent) { val to = (position + if (distance > 0) 1 else -1).coerceIn(0, count - 1); if (to != position) move(to); distance = 0f }
            })
    } else Modifier), verticalAlignment = Alignment.CenterVertically) {
        if (selecting) Checkbox(selected, { onSelect() }, enabled = enabled, modifier = Modifier.testTag("custom_rule_select_$index"))
        else Icon(if (sorting) Icons.Outlined.DragHandle else Icons.AutoMirrored.Outlined.Rule, null, Modifier.padding(end = 16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).combinedClickable(enabled = enabled,
            onClick = { if (selecting) onSelect() else if (!sorting) onEdit() },
            onLongClick = if (sorting) null else onSelect).padding(vertical = 14.dp).testTag("custom_rule_edit_$index")) {
            Text(if (fields.type == "MATCH") "MATCH" else fields.payload.ifEmpty { fields.type }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text("${fields.type} → ${fields.target}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            issue?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (sorting) {
            IconButton(onClick = { move(index - 1); drop() }, enabled = enabled && index > 0, modifier = Modifier.testTag("custom_rule_up_$index")) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
            IconButton(onClick = { move(index + 1); drop() }, enabled = enabled && index < count - 1, modifier = Modifier.testTag("custom_rule_down_$index")) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
        } else if (!selecting) Box {
            IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.testTag("custom_rule_menu_$index")) { Icon(Icons.Outlined.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(tr("复制")) }, onClick = { menu = false; onDuplicate() }, modifier = Modifier.testTag("custom_rule_duplicate_$index"))
                DropdownMenuItem(text = { Text(tr("删除")) }, onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("custom_rule_delete_$index"))
            }
        }
    }
}

@Composable
private fun CustomRuleForm(initial: CustomRuleDraft?, catalog: CustomRuleCatalog, saving: Boolean, saveError: String?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (String) -> Unit) {
    val original = remember(initial?.key) { initial?.let { CustomRules.fields(it.value) } ?: CustomRuleFields() }
    var type by rememberSaveable { mutableStateOf(original.type) }; var payload by rememberSaveable { mutableStateOf(original.payload) }
    var target by rememberSaveable { mutableStateOf(original.target) }; var src by rememberSaveable { mutableStateOf(original.src) }
    var noResolve by rememberSaveable { mutableStateOf(original.noResolve) }; var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }; var discard by rememberSaveable { mutableStateOf(false) }
    val fields = CustomRuleFields(type, payload, target, src, noResolve)
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    fun exit() { if (!saving) { if (fields != original) discard = true else onDismiss() } }
    ProfileEditorPage(::exit, title = { Text(if (initial == null) tr("添加规则") else tr("编辑规则")) }, backTag = "custom_rule_form_back", confirmButton = {
        TextButton(enabled = !saving, modifier = Modifier.testTag("custom_rule_confirm"), onClick = {
            runCatching { CustomRules.validate(fields.raw()) }.onSuccess { focus.clearFocus(force = true); keyboard?.hide(); onSave(fields.raw()) }.onFailure { error = it.message }
        }) { if (saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text(tr("确认")) }
    }, text = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            (error ?: saveError)?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("custom_rule_form_error")) }
            ProfileFormSection {
                ProfileSectionRow(tr("规则类型"), type, Icons.AutoMirrored.Outlined.Rule, !saving, Modifier.testTag("custom_rule_type")) { picker = "type" }
                if (type != "MATCH") {
                    if (type == "RULE-SET") ProfileSectionRow(tr("规则资源"), payload.ifBlank { tr("选择规则资源") }, Icons.Outlined.Cloud, !saving, Modifier.testTag("custom_rule_provider")) { picker = "provider" }
                    else OutlinedTextField(payload, { payload = it; error = null }, enabled = !saving, singleLine = true, label = { Text(tr("规则内容")) }, modifier = Modifier.fillMaxWidth().testTag("custom_rule_payload"))
                }
                ProfileSectionRow(tr("分流策略"), target.ifBlank { tr("选择目标") }, Icons.Outlined.Route, !saving, Modifier.testTag("custom_rule_target")) { picker = "target" }
            }
            catalog.issue(fields.raw())?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("custom_rule_reference_issue")) }
            if (type in CustomRules.parameterTypes) ProfileFormSection {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("不解析域名"), Modifier.weight(1f)); Switch(noResolve || src, { noResolve = it }, enabled = !saving && !src, modifier = Modifier.testTag("custom_rule_no_resolve")) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("匹配源 IP"), Modifier.weight(1f)); Switch(src, { src = it }, enabled = !saving, modifier = Modifier.testTag("custom_rule_src")) }
            }
            if (initial != null) TextButton(onClick = onDelete, enabled = !saving, modifier = Modifier.testTag("custom_rule_form_delete")) { Text(tr("删除规则"), color = MaterialTheme.colorScheme.error) }
        }
    })
    picker?.let { mode ->
        val sections: List<Pair<String?, List<String>>> = when (mode) {
            "type" -> listOf(null to CustomRules.types)
            "provider" -> listOf(null to catalog.providers)
            else -> listOf(tr("基础策略") to catalog.basic, tr("策略组") to catalog.groups, tr("本地代理") to catalog.proxies)
        }
        val selection = when (mode) {
            "type" -> type
            "provider" -> payload
            else -> target
        }
        CustomRulePicker(mode, sections, selection, { picker = null }) { value ->
            when (mode) {
                "type" -> type = value
                "provider" -> payload = value
                else -> target = value
            }
            error = null
            picker = null
        }
    }
    if (discard) EditorAlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("放弃未保存的规则？")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("放弃")) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(tr("继续编辑")) } })
}

@Composable
private fun CustomRulePicker(
    mode: String,
    sections: List<Pair<String?, List<String>>>,
    selection: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable(mode) { mutableStateOf("") }
    var searchExpanded by rememberSaveable(mode) { mutableStateOf(false) }
    var revealed by rememberSaveable(mode) { mutableStateOf(false) }
    var shownSections by remember(mode) { mutableStateOf(sections) }
    val listState = rememberLazyListState()
    val terms = remember(query) { query.split(Regex("\\s+")).filter(String::isNotBlank) }
    val matches = remember(sections, terms) {
        sections.mapNotNull { (label, options) ->
            options.distinct().filter { option -> terms.all { option.contains(it, ignoreCase = true) } }
                .takeIf { it.isNotEmpty() }?.let { label to it }
        }
    }
    val searchable = searchExpanded || sections.sumOf { it.second.size } >= 10
    val hasCandidates = sections.any { it.second.isNotEmpty() }
    LaunchedEffect(matches) {
        // 作者 long：筛选中的离场行保留一个短过渡周期；再次匹配会复用同一项，避免列表瞬间跳动。
        shownSections = sections.mapNotNull { (label, options) ->
            val retained = shownSections.firstOrNull { it.first == label }?.second.orEmpty().toSet()
            val matching = matches.firstOrNull { it.first == label }?.second.orEmpty().toSet()
            val shown = options.distinct().filter { it in retained || it in matching }
            shown.takeIf { it.isNotEmpty() }?.let { label to it }
        }
        kotlinx.coroutines.delay(300)
        shownSections = matches
    }
    val title = when (mode) {
        "type" -> tr("选择规则类型")
        "provider" -> tr("选择规则资源")
        else -> tr("选择分流策略")
    }
    fun updateQuery(value: String) {
        query = value
        // 作者 long：清空筛选后从完整目录首行恢复，不能继承匹配结果在原目录中的位置。
        listState.requestScrollToItem(0)
    }
    fun back() {
        if (searchExpanded) {
            updateQuery("")
            searchExpanded = false
        } else onDismiss()
    }
    LaunchedEffect(matches, terms, selection) {
        if (matches.isEmpty()) return@LaunchedEffect
        if (terms.isEmpty() && !revealed) {
            // 作者 long：按实际列表索引定位已选项，再用当前视口尺寸居中，避免长目录把现有选择藏在屏外。
            val selectedIndex = matches.flatMap { (label, values) ->
                if (label == null) values else listOf(null) + values
            }.indexOf(selection)
            if (selectedIndex >= 0) {
                listState.scrollToItem(selectedIndex)
                val item = snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex } }
                    .first { it != null } ?: return@LaunchedEffect
                val layout = listState.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                listState.scrollBy(item.offset + item.size / 2f - center)
            }
            revealed = true
        }
    }
    ProfileEditorPage(
        ::back,
        title = { Text(title) },
        backTag = "custom_rule_picker_back",
        topBarSearch = if (searchable) ProfileEditorSearchState(
            searchExpanded, query, tr("搜索"), ::updateQuery, { searchExpanded = it },
        ) else null,
        text = {
            // 作者 long：切换时保留上一屏候选到淡出结束，退场行只绘制，避免搜索期间选到已被过滤的资源。
            AnimatedContent(
                targetState = matches,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it.isEmpty() },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "customRulePickerContent",
            ) { visibleSections ->
                val active = visibleSections.isEmpty() == matches.isEmpty()
                if (visibleSections.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ProxyPageEmptyState(
                            if (hasCandidates) tr("无搜索结果") else tr("暂无可选项"),
                            null, "custom_rule_picker_empty",
                            searching = hasCandidates,
                            actionLabel = tr("清除搜索").takeIf { hasCandidates },
                            actionEnabled = active,
                            actionTag = "custom_rule_picker_clear_search",
                            onAction = { updateQuery("") },
                        )
                    }
                } else LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("custom_rule_picker_list"),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
                ) {
                    shownSections.forEach { (label, options) ->
                        val matchingSection = visibleSections.firstOrNull { it.first == label }
                        if (label != null) item(key = "section:$label") {
                            SelectionEntryTransition(matchingSection != null, Modifier.padding(horizontal = 16.dp)) {
                                Text(label, Modifier.padding(top = 16.dp, bottom = 8.dp),
                                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        itemsIndexed(options, key = { _, option -> "item:$label:$option" }) { index, option ->
                            val currentSection = matchingSection?.second.orEmpty()
                            val visible = option in currentSection
                            val selected = option == selection
                            SelectionEntryTransition(visible, Modifier.padding(horizontal = 16.dp)) {
                                val top = if (index == 0) 24.dp else 0.dp
                                val bottom = if (index == options.lastIndex) 24.dp else 0.dp
                                val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                                Surface(shape = shape, color = if (selected) MaterialTheme.colorScheme.secondaryContainer else dashboardCardColor()) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                            .selectable(selected, enabled = active && visible, role = Role.RadioButton, onClick = { onSelect(option) })
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                            .testTag("custom_rule_pick_$option"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(option, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun CustomRuleYaml(initial: List<CustomRuleDraft>, saving: Boolean, saveError: String?, onDismiss: () -> Unit, onSave: (List<CustomRuleDraft>) -> Unit) {
    val original = remember { CustomRules.yaml(initial) }
    // 作者 long：快捷 YAML 与父编辑器共用加密保存树；进程恢复后保留失败位置和原因，避免用户重新提交才能找回错误。
    var content by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(original)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }; var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    fun exit() { if (!saving && !checking) { if (content.text != original) discard = true else onDismiss() } }
    ProfileEditorPage(::exit, title = { Text(tr("规则 · YAML")) }, backTag = "custom_rules_yaml_back", confirmButton = {
        TextButton(enabled = !saving && !checking, modifier = Modifier.testTag("custom_rules_yaml_confirm"), onClick = { scope.launch {
            checking = true
            val checkedText = content.text
            try { val next = withContext(Dispatchers.Default) { CustomRules.decode(checkedText, previous = initial, dropSubRules = true) }; focus.clearFocus(force = true); keyboard?.hide(); onSave(next) }
            catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: IllegalArgumentException) { error = failure.message ?: "规则格式无效" }
            finally { checking = false }
        } }) { if (saving || checking) CircularProgressIndicator(Modifier.size(20.dp)) else Text(tr("确认")) }
    }, text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("每行一条规则，支持 YAML 列表；确认后将移除 SUB-RULE 条目。"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (error ?: saveError)?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("custom_rules_yaml_error")) }
            OutlinedTextField(content, {
                // 作者 long：移动光标或调整选区并未修正规则，只有正文改变才清除上一轮校验失败。
                if (it.text != content.text) error = null
                content = it
            }, enabled = !saving && !checking, modifier = Modifier.fillMaxWidth().weight(1f).testTag("profile_rules_yaml"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), label = { Text(tr("规则 YAML")) })
        }
    })
    if (discard) EditorAlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("放弃 YAML 修改？")) }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("放弃")) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(tr("继续编辑")) } })
}
