package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import com.lonnnnnng.flclash.ui.theme.proxyDelaySuccessColor
import com.lonnnnnng.flclash.ui.theme.proxyDelayWarningColor
import java.util.UUID

@Composable
internal fun GlobalRulesRoute(controller: GlobalRulesController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    LaunchedEffect(Unit) { controller.open() }
    if (state.recoveryFailed) {
        ProfileEditorPage({}, { Text(tr("附加规则")) }, {
            Text(tr(state.error.orEmpty()))
            TextButton(controller::retryRecovery, modifier = Modifier.testTag("global_rules_recovery_retry")) { Text(tr("重试恢复")) }
        })
        return
    }
    if (!state.ready || !state.loaded) {
        ProfileEditorPage({}, { Text(tr("附加规则")) }, {
            if (state.loading || !state.ready) CircularProgressIndicator()
            else { Text(tr(state.error.orEmpty())); TextButton(controller::reload) { Text(tr("重新读取")) } }
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
    // 作者 long：Activity 重建时让保存器恢复同一种 State，避免专用长整型状态被还原成普通 ParcelableSnapshotMutableState。
    var sortVersion by rememberSaveable { mutableStateOf(0L) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var originalValue by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingValue by rememberSaveable { mutableStateOf<String?>(null) }
    var closeAfterSave by rememberSaveable { mutableStateOf(false) }
    var presets by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleteVersion by rememberSaveable { mutableStateOf<Long?>(null) }
    val busy = state.loading || state.saving || state.applying
    val canChange = !busy && !state.interrupted
    val terms = query.lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    val visible = state.rules.filter { rule -> terms.all { rule.value.contains(it, true) } }
    // 作者 long：完整目录和搜索结果分别持有视口；短结果不能夹掉原浏览锚点，清除查询后回到原行。
    val directoryList = rememberLazyListState()
    val searchList = rememberLazyListState()
    val searching = query.isNotBlank()
    var appliedQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) {
        // 作者 long：只有用户改查询才归零，Activity恢复同一查询时保留已保存的结果列表锚点。
        if (appliedQuery != query) {
            if (searching) searchList.scrollToItem(0)
            appliedQuery = query
        }
    }
    val emptyOutline = remember {
        // 作者 long：规则空态在Flutter使用圆角规则牌，cookie轮廓属于其他Prism空态，不能共用。
        Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, 1f, 1f), CornerRadius(.3f, .3f))) }
    }
    LaunchedEffect(state.rules, state.saving, state.saveResultVersion, state.interrupted) {
        if (!state.loaded || state.loading) return@LaunchedEffect
        selected = selected.filter { id -> state.rules.any { it.id == id } }
        if (!state.saving && !state.interrupted) {
            if (delete.isNotEmpty() && state.rules.none { it.id in delete } && state.error == null) delete = emptyList()
            if (pendingId != null && state.rules.any { it.id == pendingId && it.value == pendingValue }) {
                // 作者 long：提交成功后保持编辑Sheet到退场动画结束，不能在Room回执帧直接销毁窗口。
                closeAfterSave = true
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
    ProfileEditorPage(onDismissRequest = ::back, title = { Text(tr("附加规则"), Modifier.padding(start = 4.dp)) }, backTag = "global_rules_back",
        navigationButtonSurface = true, navigationButtonSize = 40.dp,
        confirmButton = {
            if (!selecting && !sorting) Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.rules.isNotEmpty()) IconButton({
                    sortSnapshot = state.rules.flatMap { listOf(it.id, it.value) }
                    sortVersion = state.saveResultVersion
                    sorting = true
                }, enabled = canChange && !searching,
                    modifier = Modifier.size(40.dp).background(dashboardCardColor(), CircleShape).testTag("global_rules_sort")) {
                    Icon(Icons.Outlined.DragHandle, null)
                }
                IconButton({ presets = true }, enabled = canChange && state.loaded,
                    modifier = Modifier.size(40.dp).background(dashboardCardColor(), CircleShape).testTag("global_rules_presets")) {
                    AppGlyphIcon(AppGlyphs.rules)
                }
                IconButton({ form = "new"; originalValue = null }, enabled = canChange && state.loaded,
                    modifier = Modifier.size(40.dp).background(dashboardCardColor(), CircleShape).testTag("global_rules_add")) {
                    AppGlyphIcon(AppGlyphs.addCircle)
                }
            }
        },
        bottomBar = { DockedProfileSearch(query, { query = it }, "global_rules_search", enabled = !busy && !sorting) },
        text = {
            Column(Modifier.fillMaxWidth().testTag("page_global_rules"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selecting || sorting) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selecting) tr("已选 {0} 项", selected.size) else tr("共 {0} 条规则", state.rules.size),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (selecting) {
                        IconButton({ selected = if (visible.all { it.id in selected }) emptyList() else visible.map { it.id } }, enabled = canChange,
                            modifier = Modifier.testTag("global_rules_all")) { Icon(Icons.Outlined.SelectAll, null) }
                        IconButton({ delete = selected.filter { id -> visible.any { it.id == id } } }, enabled = canChange && visible.any { it.id in selected },
                            modifier = Modifier.testTag("global_rules_delete")) { Icon(Icons.Outlined.Delete, null) }
                        IconButton({ selecting = false; selected = emptyList() }, enabled = !busy,
                            modifier = Modifier.testTag("global_rules_cancel_selection")) { Icon(Icons.Outlined.Close, null) }
                    } else TextButton({ sorting = false; sortSnapshot = emptyList() }, enabled = !busy,
                        modifier = Modifier.testTag("global_rules_sort_done")) { Text(tr("完成")) }
                }
                state.error?.let {
                    Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_rules_error"))
                    Row {
                        if (state.interrupted) {
                            TextButton(onRetryPending, enabled = !busy && !state.blocked, modifier = Modifier.testTag("global_rules_retry")) {
                                Text(if (state.draft?.pending?.committed == true) tr("继续编辑") else tr("重试保存"))
                            }
                            TextButton(onDiscardPending, enabled = !busy, modifier = Modifier.testTag("global_rules_discard_pending")) {
                                Text(tr("放弃这次保存"))
                            }
                        } else TextButton(onReload, enabled = !busy, modifier = Modifier.testTag("global_rules_reload")) {
                            Text(tr("重新读取"))
                        }
                    }
                }
                if (state.draft?.applyPending == true && !state.interrupted) TextButton(onApply, enabled = !busy,
                    modifier = Modifier.testTag("global_rules_apply")) { Text(tr("应用到原配置")) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!busy && visible.isEmpty()) {
                    if (query.isBlank()) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("global_rules_empty"),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 320.dp).offset(y = maxHeight * -.10f)
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                            .verticalScroll(rememberScrollState()).testTag("global_rules_empty_content"),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.testTag("global_rules_empty_illustration")) {
                                ProfileEmptyIllustration(AppGlyphs.rules, outline = emptyOutline, extent = 144.dp, flutterPalette = true)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(tr("暂无规则"), Modifier.testTag("global_rules_empty_title"),
                                color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(tr("没有匹配的规则"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (sorting) {
                    val ordered = sortSnapshot.chunked(2).map { ExtensionRule(it[0], it[1]) }
                    ResourceOrderList(ordered.map { ResourceOrderItem(it.id, it.value, tr("长按调整顺序")) }, !canChange, "global_rules", { ids ->
                        val byId = ordered.associateBy { it.id }
                        save(ordered, ids.map { byId.getValue(it) })
                    }, Modifier.fillMaxWidth().weight(1f), compactRows = true, rowContent = { item ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GlobalRuleLabel(ordered.first { it.id == item.id }, Modifier.weight(1f))
                            Icon(Icons.Outlined.DragHandle, null, Modifier.padding(start = 12.dp).size(24.dp))
                        }
                    })
                    LaunchedEffect(state.saveResultVersion, state.saving, state.interrupted) {
                        if (!state.saving && !state.interrupted && state.error == null && state.saveResultVersion > sortVersion) {
                            sortSnapshot = state.rules.flatMap { listOf(it.id, it.value) }; sortVersion = state.saveResultVersion
                        }
                    }
                } else key(searching) {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("global_rules_list"),
                        state = if (searching) searchList else directoryList, contentPadding = PaddingValues(top = 20.dp)) {
                    items(visible, key = { it.id }) { rule ->
                        val first = rule.id == visible.first().id
                        val last = rule.id == visible.last().id
                        Row(Modifier.fillMaxWidth().clip(AppSuperellipseShape(
                            topStart = if (first) 24.dp else 0.dp, topEnd = if (first) 24.dp else 0.dp,
                            bottomStart = if (last) 24.dp else 0.dp, bottomEnd = if (last) 24.dp else 0.dp))
                            .background(dashboardCardColor()).heightIn(min = 56.dp).clickable(enabled = canChange) {
                            if (selecting) selected = if (rule.id in selected) selected - rule.id else selected + rule.id
                            else { form = rule.id; originalValue = rule.value }
                        }.padding(horizontal = 16.dp, vertical = 7.dp).testTag("global_rule_${rule.id}"), verticalAlignment = Alignment.CenterVertically) {
                            GlobalRuleLabel(rule, Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            IconToggleButton(rule.id in selected, {
                                selecting = true
                                selected = if (rule.id in selected) selected - rule.id else selected + rule.id
                                if (selected.isEmpty()) selecting = false
                            }, enabled = canChange, modifier = Modifier.size(24.dp).testTag("global_rule_select_${rule.id}")) {
                                Icon(if (rule.id in selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    null, Modifier.size(22.dp), tint = if (rule.id in selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!last) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = .5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    }
                }
            }
        }
    )
    form?.let { id ->
        val initial = originalValue?.let { ExtensionRule(id, it) }
        val actual = state.rules.firstOrNull { it.id == id }
        val ownResult = pendingId == id && actual?.value == pendingValue
        val conflict = if (state.blocked || id != "new" && !ownResult && actual != initial)
            tr("原规则已被修改或删除，草稿已保留，请返回后重新打开") else null
        ExtensionRuleForm(initial, listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS"), busy || ownResult, state.error,
            blockedMessage = conflict, onRetryPending = onRetryPending.takeIf { state.interrupted },
            onDiscardPending = onDiscardPending.takeIf { state.interrupted }, committed = state.draft?.pending?.committed == true,
            onDismiss = { form = null; originalValue = null; pendingId = null; pendingValue = null; closeAfterSave = false; if (state.interrupted) onDiscardPending() }, onSave = { rule ->
                if (conflict == null && canChange) {
                    pendingId = rule.id; pendingValue = rule.value
                    closeAfterSave = false
                    save(state.rules, if (id == "new") state.rules + rule else state.rules.map { if (it.id == id) rule else it })
                }
            }, sheetPresentation = true, sheetCloseRequested = closeAfterSave, defaultType = "DOMAIN")
    }
    if (presets) ExtensionPresets({ presets = false }) { values ->
        presets = false
        save(state.rules, state.rules + values.filterNot { value -> state.rules.any { it.value == value } }.distinct().map { ExtensionRule(UUID.randomUUID().toString(), it) })
    }
    if (delete.isNotEmpty()) EditorAlertDialog(onDismissRequest = { if (!busy) { delete = emptyList(); if (state.interrupted) onDiscardPending() } },
        title = { Text(tr("删除 {0} 条规则？", delete.size)) }, text = {
            Column {
                Text(tr("配置中引用这些全局规则的记录将不再生效。"))
                state.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_rules_delete_error")) }
                if (state.interrupted) TextButton(onRetryPending, enabled = !busy && !state.blocked, modifier = Modifier.testTag("global_rules_delete_retry")) {
                    Text(if (state.draft?.pending?.committed == true) tr("继续编辑") else tr("重试删除"))
                }
            }
        }, confirmButton = {
            TextButton({
                // 作者 long：确认保持到事务成功；失败不清选择，取消不会再触发删除。
                deleteVersion = state.saveResultVersion
                save(state.rules, state.rules.filterNot { it.id in delete })
            }, enabled = canChange, modifier = Modifier.testTag("global_rules_delete_confirm")) { Text(if (busy) tr("删除中…") else tr("删除")) }
        }, dismissButton = {
            TextButton({ delete = emptyList(); deleteVersion = null; if (state.interrupted) onDiscardPending() }, enabled = !busy,
                modifier = Modifier.testTag("global_rules_delete_cancel")) { Text(tr("取消")) }
        })
}

@Composable
private fun GlobalRuleLabel(rule: ExtensionRule, modifier: Modifier) {
    // 作者 long：仅拆分展示；逻辑表达式内部的逗号必须由现有解析器保留，不能影响保存正文或把no-resolve当成分流目标。
    val fields = remember(rule.value) { ExtensionRules.fields(rule.value) }
    val targetIndex = if (fields.last().equals("no-resolve", true)) fields.lastIndex - 1 else fields.lastIndex
    val target = fields[targetIndex]
    val content = fields.subList(1, targetIndex).joinToString(",")
    val targetColor = when (target.uppercase()) {
        "DIRECT", "COMPATIBLE" -> proxyDelaySuccessColor()
        "REJECT", "REJECT-DROP" -> proxyDelayWarningColor()
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(fields.first(), style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (content.isNotEmpty()) Text(content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(target, Modifier.widthIn(max = 140.dp), color = targetColor,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
