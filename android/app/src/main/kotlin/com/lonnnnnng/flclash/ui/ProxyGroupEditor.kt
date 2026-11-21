package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class GroupOption(val key: String, private val labelText: String, val default: String, val kind: String = "text") {
    val label get() = tr(labelText)
}
private data class GroupTargetSection(val key: String, val title: String?, val choices: List<String>)
// 作者 long：Flutter SelectionSheet 在十项起显示搜索，短列表保持一眼可扫。
private const val GROUP_SELECTION_SEARCH_MIN_ITEM_COUNT = 10
private val groupOptions = listOf(
    GroupOption("empty-fallback", "空组回退", "COMPATIBLE", "target"),
    GroupOption("url", "测速地址", "https://www.gstatic.com/generate_204"),
    GroupOption("interval", "测速间隔（秒）", "300", "number"),
    GroupOption("timeout", "超时（毫秒）", "5000", "number"),
    GroupOption("max-failed-times", "最大失败次数", "5", "number"),
    GroupOption("lazy", "使用时测速", "true", "boolean"),
    GroupOption("expected-status", "预期状态码", ""),
    GroupOption("tolerance", "容差（毫秒）", "0", "number"),
    GroupOption("default-selected", "默认选中", "", "target"),
    GroupOption("strategy", "负载均衡策略", "consistent-hashing", "strategy"),
    GroupOption("hash-key", "按入站用户哈希", "", "hash"),
    GroupOption("hidden", "从列表隐藏", "false", "boolean"),
    GroupOption("disable-udp", "禁用 UDP", "false", "boolean"),
)
private val groupIdentityOptions = listOf(
    GroupOption("name", "名称", ""), GroupOption("type", "类型", "select", "type"),
    GroupOption("filter", "过滤表达式", ""), GroupOption("exclude-filter", "排除表达式", ""),
)

private val groupDraftSaver = listSaver<List<ProxyGroupDraft>, String>(
    save = { groups -> listOf("member-layout-v1") + groups.flatMap { listOf(it.key, it.originalName.orEmpty(), it.json, it.memberLayout.orEmpty().joinToString(",")) } },
    restore = { values -> if (values.firstOrNull() == "member-layout-v1") values.drop(1).chunked(4).map {
        ProxyGroupDraft(it[0], it[1].ifEmpty { null }, it[2], it[3].split(',').filter(String::isNotBlank).takeIf { layout -> layout.isNotEmpty() })
    } else values.chunked(3).map { ProxyGroupDraft(it[0], it[1].ifEmpty { null }, it[2]) } },
)

/** 作者 long：列表、单组和成员共用一份草稿；子页确认只更新草稿，顶栏保存才原子写入配置。 */
@Composable
internal fun VisualProxyGroupsEditor(
    profile: Profile,
    initialGroups: String,
    sourceContent: String,
    saving: Boolean,
    initialGroupIndex: Int? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSaveDraft: ((ProxyGroupsSave) -> Unit)? = null,
    onValidateFilters: suspend (List<String>) -> List<String> = { error(tr("过滤表达式校验不可用")) },
    recentIcons: List<String> = emptyList(),
    onRecordIcon: (String, Boolean) -> Unit = { _, _ -> },
    onReadIcon: suspend (String) -> String = { error(tr("图片读取不可用")) },
    resourceProfiles: List<Profile> = emptyList(),
    saveError: String? = null,
) {
    val source = remember(sourceContent, initialGroups) {
        sourceContent.ifBlank { if (initialGroups.isBlank()) "" else "proxy-groups:\n" + initialGroups.prependIndent("  ") }
    }
    val globalProxies = LocalGlobalProxies.current
    val resourceCandidates = remember(source, resourceProfiles) { runCatching { ProfileProxyResources.candidates(source, resourceProfiles) }.getOrDefault(emptyList()) }
    val boundSource = remember(source, resourceCandidates, globalProxies) {
        runCatching {
            val root = ProxyGroupDrafts.root(source)
            val providers = root.getAsJsonObject("proxy-providers") ?: com.google.gson.JsonObject()
            providers.entrySet().filter { ProfileProxyResources.isReference(it.value) &&
                resourceCandidates.none { candidate -> candidate.profile.id == ProfileProxyResources.idOf(it.value) } }
                .map { it.key }.forEach(providers::remove)
            resourceCandidates.forEach { if (!providers.has(it.alias)) providers.add(it.alias, ProfileProxyResources.reference(it.profile.id)) }
            root.add("proxy-providers", providers)
            LocalProxies.candidates(com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(root.toString()), globalProxies)
        }.getOrDefault(source)
    }
    fun saveDraft(values: List<ProxyGroupDraft>) = ProxyGroupsSave(values, resourceCandidates.associate { it.alias to it.profile.id })
    // 作者 long：编辑期间无关覆盖项刷新不能改变脏草稿的比较基准，正文或配置更换后再重新读取。
    val parsed = remember(profile.id, source) { runCatching { ProxyGroupMembers.restoreLayouts(ProxyGroupDrafts.read(source), profile.overridesJson) } }
    var groups by rememberSaveable(profile.id, source, stateSaver = groupDraftSaver) { mutableStateOf(parsed.getOrDefault(emptyList())) }
    val initial = rememberSaveable(profile.id, source, saver = groupDraftSaver) { parsed.getOrDefault(emptyList()) }
    var selectedKey by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var creating by rememberSaveable(profile.id) { mutableStateOf(false) }
    var initialized by rememberSaveable(profile.id) { mutableStateOf(false) }
    var yaml by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var sorting by rememberSaveable(profile.id) { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var deleteKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selected by rememberSaveable(profile.id) { mutableStateOf(emptyList<String>()) }
    var selecting by rememberSaveable(profile.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf(parsed.exceptionOrNull()?.message) }
    val dirty = groups.map { it.json to it.memberLayout } != initial.map { it.json to it.memberLayout }
    fun exit() { if (!saving) {
        when { selecting -> { selecting = false; selected = emptyList() }; sorting -> sorting = false
            dirty -> discard = true; else -> onDismiss() }
    } }
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key; selecting = true }
    var listFilterIssues by remember { mutableStateOf<Map<String, List<ProxyGroupIssue>>>(emptyMap()) }
    LaunchedEffect(groups.map { listOf(it.key, it.text("filter"), it.text("exclude-filter")) }) {
        listFilterIssues = emptyMap()
        val filters = groups.flatMap { listOf(it.text("filter"), it.text("exclude-filter")) }
        if (filters.any(String::isNotBlank)) {
            delay(300)
            try {
                val errors = onValidateFilters(filters)
                listFilterIssues = groups.mapIndexed { index, group -> group.key to
                    errors.drop(index * 2).take(2).filter(String::isNotEmpty).map { ProxyGroupIssue(tr("过滤表达式无效：{0}", it)) } }.toMap()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                listFilterIssues = groups.associate { it.key to if (it.text("filter").isNotBlank() || it.text("exclude-filter").isNotBlank())
                    listOf(ProxyGroupIssue(tr("无法校验过滤表达式，请重试"))) else emptyList() }
            }
        }
    }
    val issues = remember(groups, boundSource, listFilterIssues) { groups.associate { it.key to
        (runCatching { ProxyGroupIssues.inspect(it, groups, boundSource) }.getOrDefault(emptyList()) + listFilterIssues[it.key].orEmpty()) } }
    fun add() { val group = ProxyGroupDraft(); groups = groups + group; selectedKey = group.key; creating = true }
    LaunchedEffect(profile.id) {
        if (!initialized) {
            initialized = true
            if (initialGroupIndex == -1) add()
            else selectedKey = initialGroupIndex?.let { groups.getOrNull(it)?.key }
        }
    }
    fun commit() {
        if (saving) return
        runCatching {
            if (onSaveDraft != null) {
                ProxyGroupDrafts.apply(source, saveDraft(groups), globalProxies.map { it.name })
                onSaveDraft(saveDraft(groups))
            } else if (!dirty) onSave(initialGroups)
            else {
                val content = ProxyGroupDrafts.apply(source, saveDraft(groups), globalProxies.map { it.name })
                onSave(ProfileProxyGroupsEditor.extractGroups(content))
            }
        }.onFailure { error = it.message ?: "无法保存策略组" }
    }
    ProfileEditorPage(
        onDismissRequest = ::exit, backTag = "proxy_group_list_back",
        title = { Text(tr("代理组 · ") + profile.realLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = ::commit, enabled = !saving && parsed.isSuccess, modifier = Modifier.testTag("proxy_group_list_save")) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text(tr("保存"))
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (error ?: saveError)?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("proxy_group_error")) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selecting) tr("已选择 {0} 项", selected.size) else tr("策略组 · {0}", groups.size), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (selecting) {
                        IconButton(onClick = { groups = ProxyGroupIssues.duplicate(groups, selected.toSet(), source); selected = emptyList(); selecting = false },
                            enabled = !saving && selected.isNotEmpty(), modifier = Modifier.testTag("proxy_group_batch_copy")) { Icon(Icons.Outlined.ContentCopy, null) }
                        IconButton(onClick = { deleteKeys = selected.toSet() }, enabled = !saving && selected.isNotEmpty(), modifier = Modifier.testTag("proxy_group_batch_delete")) { Icon(Icons.Outlined.DeleteOutline, null) }
                        IconButton(onClick = { selected = if (selected.size == groups.size) emptyList() else groups.map { it.key } }, enabled = !saving,
                            modifier = Modifier.testTag("proxy_group_select_all")) { Icon(Icons.Outlined.SelectAll, null) }
                        IconButton(onClick = { selecting = false; selected = emptyList() }, enabled = !saving, modifier = Modifier.testTag("proxy_group_selection_done")) { Icon(Icons.Outlined.Close, null) }
                    } else {
                        if (groups.size > 1) IconButton(onClick = { sorting = !sorting }, enabled = !saving,
                            modifier = Modifier.testTag("proxy_group_sort")) { Icon(if (sorting) Icons.Outlined.Done else Icons.AutoMirrored.Outlined.Sort, null) }
                        if (!sorting) IconButton(onClick = { selecting = true }, enabled = !saving && groups.isNotEmpty(),
                            modifier = Modifier.testTag("proxy_group_select")) { Icon(Icons.Outlined.Checklist, null) }
                        IconButton(onClick = ::add, enabled = !saving && parsed.isSuccess, modifier = Modifier.testTag("proxy_group_add")) { Icon(Icons.Outlined.Add, null) }
                    }
                }
                if (sorting) Text(tr("长按拖动调整顺序"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (groups.isEmpty()) Text(tr("暂无策略组"), Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                groups.forEachIndexed { index, group ->
                    key(group.key) {
                        GroupOrderRow(index, groups.size, group.key, sorting, !saving,
                            onMove = { to -> groups = groups.toMutableList().apply { add(to, removeAt(index)) } }) {
                            Row(Modifier.weight(1f).heightIn(min = 72.dp).testTag("proxy_group_row_$index")
                                .clickable(enabled = !saving && !sorting) { if (selecting) toggle(group.key) else { selectedKey = group.key; creating = false } }
                                .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfileGroupIcon(group.text("icon"), group.name, Modifier.padding(end = 12.dp).size(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name.ifBlank { tr("未命名策略组") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(group.text("type") + " · " + ProxyGroupMembers.read(group).size + tr(" 个成员"),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (!sorting) {
                                if (issues[group.key].orEmpty().isNotEmpty()) GroupIssueButton(issues[group.key].orEmpty(), "proxy_group_issues_$index")
                                Checkbox(group.key in selected, { toggle(group.key) }, enabled = !saving,
                                    modifier = Modifier.testTag("proxy_group_check_$index"))
                            }
                            if (!sorting && !selecting) {
                                var menu by remember { mutableStateOf(false) }
                                val menuAnchor = remember(group.key) { AppContextMenuAnchor() }
                                Box {
                                    IconButton(onClick = { menuAnchor.open(); menu = true }, enabled = !saving,
                                        modifier = Modifier.appContextMenuAnchor(menuAnchor, enabled = !saving) { menu = true }
                                            .testTag("proxy_group_menu_$index")) { Icon(Icons.Outlined.MoreVert, null) }
                                    AppDropdownMenu(menu && !saving, { menu = false }, anchor = menuAnchor.point, carriedPointer = menuAnchor.carry,
                                        placement = AppMenuPlacement.BELOW_POINT, avoid = menuAnchor.bounds,
                                        modifier = Modifier.testTag("proxy_group_actions_$index")) {
                                        AppDropdownMenuItem(text = { Text(tr("复制")) }, onClick = {
                                            groups = ProxyGroupIssues.duplicate(groups, setOf(group.key), source); menu = false
                                        }, modifier = Modifier.testTag("proxy_group_copy_$index"))
                                        AppDropdownMenuItem(text = { Text(tr("删除")) }, danger = true,
                                            onClick = { menu = false; deleteKeys = setOf(group.key) },
                                            modifier = Modifier.testTag("proxy_group_delete_$index"))
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { yaml = ProfileProxyGroupsEditor.extractGroups(
                    com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(com.google.gson.JsonObject().apply {
                        add("proxy-groups", com.google.gson.JsonArray().apply { groups.forEach { add(it.fields) } })
                    }.toString())) }, enabled = !saving, modifier = Modifier.testTag("proxy_groups_yaml")) { Text(tr("编辑原始 YAML")) }
            }
        },
    )
    selectedKey?.let { selected -> groups.firstOrNull { it.key == selected }?.let { group ->
        SingleProxyGroupEditor(group, groups, boundSource, creating, onValidateFilters, recentIcons, onRecordIcon, onReadIcon,
            onDismiss = { if (creating) groups = groups.filterNot { it.key == selected }; selectedKey = null; if (initialGroupIndex != null) onDismiss() },
            onConfirm = { updated ->
                val values = groups.map { if (it.key == selected) updated else if (group.name != updated.name) ProxyGroupDrafts.renameReferences(it, group.name, updated.name) else it }
                groups = values; selectedKey = null; error = null
                // 作者 long：从概览进入单组时，确认后直接保存并返回概览，避免额外经过一次列表保存。
                if (initialGroupIndex != null && onSaveDraft != null) onSaveDraft(saveDraft(values))
            },
            onDelete = { deleteKeys = setOf(selected) })
    } }
    yaml?.let { initialYaml ->
        GroupsQuickYaml(initialYaml, groups, { yaml = null }) { values ->
            groups = values; yaml = null; error = null
        }
    }
    if (discard) EditorAlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("放弃策略组修改？")) }, text = { Text(tr("尚未保存的策略组修改会丢失。")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("放弃修改")) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(tr("继续编辑")) } })
    if (deleteKeys.isNotEmpty()) {
        val targets = groups.filter { it.key in deleteKeys }
        val inUse = ProxyGroupIssues.referencedNames(groups, deleteKeys, source)
        EditorAlertDialog(onDismissRequest = { deleteKeys = emptySet() }, title = { Text(tr("删除策略组？")) },
            text = { Text(if (inUse.isNotEmpty()) tr("以下策略组仍被引用：{0}。删除后需要修复规则或节点引用。", inUse.joinToString("、"))
                else tr("将删除 {0} 个策略组：{1}", targets.size, targets.joinToString("、") { it.name })) },
            confirmButton = { TextButton(onClick = {
                val values = groups.filterNot { it.key in deleteKeys }
                groups = values; if (selectedKey in deleteKeys) selectedKey = null
                deleteKeys = emptySet(); selected = emptyList(); selecting = false
                if (initialGroupIndex != null && onSaveDraft != null) onSaveDraft(saveDraft(values))
            }, enabled = !saving, modifier = Modifier.testTag("proxy_group_delete_confirm")) { Text(tr("删除")) } },
            dismissButton = { TextButton(onClick = { deleteKeys = emptySet() }) { Text(tr("取消")) } })
    }
}

@Composable
internal fun GroupOrderRow(index: Int, count: Int, itemKey: String, sorting: Boolean, enabled: Boolean,
    onMove: (Int) -> Unit, content: @Composable RowScope.() -> Unit) {
    val currentMove by rememberUpdatedState(onMove)
    val currentIndex by rememberUpdatedState(index)
    val extent = with(LocalDensity.current) { 72.dp.toPx() }
    Row(Modifier.fillMaxWidth().then(if (sorting && enabled) Modifier.pointerInput(itemKey, count) {
        var distance = 0f
        detectDragGesturesAfterLongPress(onDragStart = { distance = 0f }, onDrag = { change, delta ->
            change.consume(); distance += delta.y
            if (abs(distance) >= extent) {
                val destination = (currentIndex + if (distance > 0) 1 else -1).coerceIn(0, count - 1)
                if (destination != currentIndex) { currentMove(destination); distance = 0f }
            }
        })
    } else Modifier), verticalAlignment = Alignment.CenterVertically) {
        content()
        if (sorting) {
            IconButton(onClick = { currentMove(index - 1) }, enabled = enabled && index > 0, modifier = Modifier.testTag("group_move_up_$index")) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
            IconButton(onClick = { currentMove(index + 1) }, enabled = enabled && index < count - 1, modifier = Modifier.testTag("group_move_down_$index")) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
        }
    }
}

@Composable
private fun SingleProxyGroupEditor(initial: ProxyGroupDraft, groups: List<ProxyGroupDraft>, source: String, creating: Boolean,
    validateFilters: suspend (List<String>) -> List<String>,
    recentIcons: List<String>, onRecordIcon: (String, Boolean) -> Unit,
    onReadIcon: suspend (String) -> String,
    onDismiss: () -> Unit, onConfirm: (ProxyGroupDraft) -> Unit, onDelete: () -> Unit) {
    var json by rememberSaveable(initial.key) { mutableStateOf(initial.json) }
    var memberLayout by rememberSaveable(initial.key) { mutableStateOf(initial.memberLayout.orEmpty()) }
    var shownOptionKeys by rememberSaveable(initial.key) {
        mutableStateOf(initial.fields.keySet().filter { key -> groupOptions.any { it.key == key } })
    }
    val group = initial.copy(json = json, memberLayout = memberLayout.takeIf { it.isNotEmpty() })
    var iconPicker by rememberSaveable(initial.key) { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var confirmRevision by remember { mutableIntStateOf(0) }
    var closing by rememberSaveable(initial.key) { mutableStateOf(false) }
    var acceptedJson by rememberSaveable(initial.key) { mutableStateOf<String?>(null) }
    var acceptedLayout by rememberSaveable(initial.key) { mutableStateOf(emptyList<String>()) }
    val formList = rememberLazyListState()
    val iconGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val pickerScroll = rememberScrollState()
    val targetScroll = rememberScrollState()
    val optionScroll = rememberScrollState()
    val rootScroll = rememberScrollState()
    var filterErrors by remember { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()
    val filters = listOf(group.text("filter"), group.text("exclude-filter"))
    LaunchedEffect(filters) {
        filterErrors = emptyList()
        if (filters.any(String::isNotBlank)) {
            delay(300)
            try { filterErrors = validateFilters(filters) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { filterErrors = listOf(tr("无法校验过滤表达式，请重试")) }
        }
    }
    val issues = remember(json, groups, source) { runCatching { ProxyGroupIssues.inspect(group, groups.map { if (it.key == group.key) group else it }, source) }.getOrDefault(emptyList()) } +
        filterErrors.filter(String::isNotEmpty).map { ProxyGroupIssue(tr("过滤表达式无效：{0}", it)) }
    // 作者 long：只保存选项身份，重建时从同一字段目录恢复子页，避免父草稿还在而未确认的值窗口已丢失。
    var editingKey by rememberSaveable(initial.key) { mutableStateOf<String?>(null) }
    val editing = (groupIdentityOptions + groupOptions).firstOrNull { it.key == editingKey }
    var targetQuery by rememberSaveable(initial.key) { mutableStateOf("") }
    var targetSearchExpanded by rememberSaveable(initial.key) { mutableStateOf(false) }
    var optionQuery by rememberSaveable(initial.key) { mutableStateOf("") }
    var optionSearchExpanded by rememberSaveable(initial.key) { mutableStateOf(false) }
    val optionFocusManager = LocalFocusManager.current
    val optionKeyboard = LocalSoftwareKeyboardController.current
    var addOption by rememberSaveable(initial.key) { mutableStateOf(false) }
    var members by rememberSaveable(initial.key) { mutableStateOf(false) }
    var yaml by rememberSaveable(initial.key) { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val clearFocus = remember { arrayOf<(() -> Unit)?>(null) }
    fun change(action: (com.google.gson.JsonObject) -> Unit) { json = group.edit(action).json; error = null }
    fun changeOptionValue(option: GroupOption, value: String) {
        change { fields ->
            when {
                value.isEmpty() -> fields.remove(option.key)
                option.kind == "number" -> fields.addProperty(option.key, value.toLong())
                else -> fields.addProperty(option.key, value)
            }
        }
    }
    fun removeOption(option: GroupOption) {
        shownOptionKeys = shownOptionKeys.filterNot { it == option.key }
        change { it.remove(option.key) }
    }
    fun exit() {
        confirmRevision++
        clearFocus[0]?.invoke()
        if (json != initial.json || memberLayout != initial.memberLayout.orEmpty() || members || iconPicker || addOption || yaml) discard = true else closing = true
    }
    fun confirm() {
        if (confirming || closing) return
        clearFocus[0]?.invoke()
        confirming = true
        val revision = ++confirmRevision
        scope.launch {
            try {
                // 作者 long：核心异步校验期间表单仍可编辑；只提交最后一次确认的同一份快照，迟到结果不能写入新草稿。
                val frozen = json
                val frozenLayout = memberLayout
                val candidate = initial.copy(json = frozen, memberLayout = frozenLayout.takeIf { it.isNotEmpty() })
                val candidates = groups.map { if (it.key == candidate.key) candidate else it }
                val blocking = ProxyGroupIssues.inspect(candidate, candidates, source).filter { it.blocking }
                require(blocking.isEmpty()) { blocking.joinToString("\n") { it.message } }
                val values = listOf(candidate.text("filter"), candidate.text("exclude-filter"))
                val errors = if (values.all(String::isBlank)) emptyList() else validateFilters(values).filter(String::isNotEmpty)
                require(errors.isEmpty()) { tr("过滤表达式无效：") + errors.joinToString("\n") }
                // 作者 long：放弃/退出已开始时，旧核心校验即使成功也不能把被丢弃的草稿改成待提交结果。
                if (closing || discard || revision != confirmRevision) return@launch
                if (json != frozen || memberLayout != frozenLayout) { error = "表单已更新，请再次确认"; return@launch }
                acceptedJson = candidate.json; acceptedLayout = candidate.memberLayout.orEmpty(); closing = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (!closing && revision == confirmRevision) error = failure.message ?: "无法校验策略组，请重试" }
            finally { confirming = false }
        }
    }
    val applicable = groupOptions.filter { option -> when (option.key) {
        "tolerance" -> group.text("type") == "url-test"
        "default-selected" -> group.text("type") == "select"
        "strategy" -> group.text("type") == "load-balance"
        "hash-key" -> group.text("type") == "load-balance" && group.text("strategy") != "round-robin"
        else -> true
    } }
    val remainingOptions = applicable.filterNot { it.key in shownOptionKeys }
    val optionTerms = optionQuery.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    val matchingOptions = remainingOptions.filter { option -> optionTerms.all(option.label.lowercase()::contains) }
    val optionSearchable = optionSearchExpanded || optionQuery.isNotEmpty() ||
        remainingOptions.size >= GROUP_SELECTION_SEARCH_MIN_ITEM_COUNT
    fun collapseOptionSearch() {
        optionQuery = ""; optionSearchExpanded = false
        optionFocusManager.clearFocus(force = true); optionKeyboard?.hide()
    }
    LaunchedEffect(optionQuery, remainingOptions) {
        // 作者 long：筛选后从列表开头展示匹配项，避免旧滚动偏移把短结果留在视口之外。
        if (optionScroll.value > 0) optionScroll.scrollTo(0)
    }
    val targetSections = remember(editing?.key, group.json, source) {
        editing?.takeIf { it.kind == "target" }?.let { groupTargetSections(it, group, source) }.orEmpty()
    }
    val page = when { members -> "members"; iconPicker -> "icons"; addOption -> "options"; yaml -> "yaml"; editing?.kind == "target" -> "target"; else -> "root" }
    val pageScroll = when (page) { "members" -> pickerScroll; "target" -> targetScroll; "options" -> optionScroll; else -> rootScroll }
    fun backPage() {
        if (addOption) collapseOptionSearch()
        members = false; iconPicker = false; addOption = false; yaml = false
        if (editing?.kind == "target") { editingKey = null; targetQuery = ""; targetSearchExpanded = false }
    }
    ProfileEditorSheet(page, pageScroll, closing, onExit = ::exit, onBack = ::backPage,
        scrollForPage = { when (it) { "members" -> pickerScroll; "target" -> targetScroll; "options" -> optionScroll; else -> rootScroll } },
        // 作者 long：用当前子页的真实滚动位置判定手势起点，中部回滚到顶不能顺势拖走整张编辑表单。
        isContentAtTop = { when (page) {
            "root" -> formList.firstVisibleItemIndex == 0 && formList.firstVisibleItemScrollOffset == 0
            "icons" -> iconGrid.firstVisibleItemIndex == 0 && iconGrid.firstVisibleItemScrollOffset == 0
            else -> pageScroll.value == 0
        } },
        onClosed = { acceptedJson?.let { onConfirm(initial.copy(json = it, memberLayout = acceptedLayout.takeIf(List<String>::isNotEmpty))) } ?: onDismiss() }) { shown ->
    when (shown) {
    "root" -> ProfileEditorPage(onDismissRequest = ::exit, title = { Text(if (creating) tr("添加策略组") else tr("编辑策略组")) }, backTag = "proxy_group_form_back", contentPadding = PaddingValues(0.dp),
        confirmButton = {
            IconButton(onClick = { clearFocus[0]?.invoke(); yaml = true }, modifier = Modifier.testTag("proxy_group_quick_yaml")) { Icon(Icons.Outlined.Code, null) }
            IconButton(onClick = ::confirm, enabled = !confirming && !closing, modifier = Modifier.testTag("proxy_group_form_confirm")) {
                if (confirming) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Check, null)
            }
        }, text = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            DisposableEffect(focus, keyboard) { clearFocus[0] = { focus.clearFocus(force = true); keyboard?.hide() }; onDispose { clearFocus[0] = null } }
            ProxyGroupInlineMembers(group, groups, source, formList,
                onChange = { json = it.json; memberLayout = it.memberLayout.orEmpty(); error = null }, onAdd = { members = true },
                onEdit = { editingKey = it.kind.field }, before = {
                error?.takeUnless { message -> issues.any { it.message == message } }?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("proxy_group_form_error")) }
                GroupIssuesBanner(issues, "proxy_group_form_issues")
                GroupSectionHeading(tr("常规"))
                ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                    GroupFieldRow(tr("名称"), group.name.ifBlank { tr("未命名") }, "proxy_group_name") { editingKey = "name" }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    GroupFieldRow(tr("代理类型"), when (val type = group.text("type")) {
                        "select" -> "Selector"; "url-test" -> "URLTest"; "fallback" -> "Fallback"; "load-balance" -> "LoadBalance"; else -> type
                    }, "proxy_group_type") { editingKey = "type" }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    GroupFieldRow(tr("图标"), if (group.text("icon").isBlank()) tr("可选") else tr("已设置"), "proxy_group_icon") { clearFocus[0]?.invoke(); iconPicker = true }
                }
            }, after = {
                GroupSectionHeading(tr("选项"))
                ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                    applicable.filter { it.key in shownOptionKeys }.forEach { option ->
                        if (option.kind == "number" || option.key == "url" || option.key == "expected-status") {
                            GroupOptionInlineInput(
                                groupKey = initial.key,
                                option = option,
                                value = group.text(option.key),
                                onValueChange = { changeOptionValue(option, it) },
                                onRemove = { removeOption(option) },
                            )
                        } else {
                            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { removeOption(option) }, modifier = Modifier.testTag("group_remove_" + option.key)) { Icon(Icons.Outlined.RemoveCircleOutline, null) }
                                if (option.kind == "boolean") {
                                    Text(option.label, Modifier.weight(1f).padding(start = 16.dp))
                                    Switch(group.flag(option.key), { value -> change { it.addProperty(option.key, value) } }, modifier = Modifier.padding(end = 16.dp))
                                } else Box(Modifier.weight(1f)) {
                                    GroupFieldRow(option.label, group.text(option.key).ifBlank { tr("默认") }, "group_option_" + option.key) {
                                        if (option.kind == "target") { targetQuery = ""; targetSearchExpanded = false }
                                        editingKey = option.key
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { optionQuery = ""; optionSearchExpanded = false; addOption = true }, modifier = Modifier.testTag("proxy_group_add_option")) { Icon(Icons.Outlined.Add, null); Text(tr("添加设置项")) }
                }
                if (!creating) TextButton(onClick = onDelete, modifier = Modifier.testTag("proxy_group_delete")) { Text(tr("删除策略组"), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(16.dp))
            })
        })
    "icons" -> ProfileGroupIconPicker(group.text("icon"), group.name, recentIcons, onRecordIcon, onReadIcon,
        onDismiss = { iconPicker = false }, onSelect = { value -> change { if (value.isEmpty()) it.remove("icon") else it.addProperty("icon", value) }; iconPicker = false }, gridState = iconGrid)
    "members" -> ProxyGroupMemberPicker(group, groups, source, onDismiss = { members = false }) {
        json = it.json; memberLayout = it.memberLayout.orEmpty(); error = null
    }
    "target" -> editing?.takeIf { it.kind == "target" }?.let { option ->
        val terms = targetQuery.lowercase().trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val matchingSections = targetSections.map { section -> section.copy(choices = section.choices.filter { choice ->
            val label = choice.ifBlank { tr("默认") }.lowercase()
            terms.all(label::contains)
        }) }.filter { it.choices.isNotEmpty() }
        val searchable = targetQuery.isNotEmpty() || targetSections.sumOf { it.choices.size } >= GROUP_SELECTION_SEARCH_MIN_ITEM_COUNT
        var contentCoordinates by remember(option.key) { mutableStateOf<LayoutCoordinates?>(null) }
        var selectedCoordinates by remember(option.key) { mutableStateOf<LayoutCoordinates?>(null) }
        var viewportHeight by remember(option.key) { mutableIntStateOf(0) }
        LaunchedEffect(option.key, targetSections) {
            // 作者 long：在滚动内容自身的坐标系定位，避免浮层缩放和视口裁切改变屏外选中行的位置。
            val offset = snapshotFlow {
                val content = contentCoordinates
                val row = selectedCoordinates
                if (content?.isAttached == true && row?.isAttached == true && viewportHeight > 0 &&
                    targetScroll.maxValue != Int.MAX_VALUE) {
                    val center = content.localPositionOf(row, Offset.Zero).y + row.size.height / 2f
                    (center - viewportHeight / 2f).roundToInt().coerceIn(0, targetScroll.maxValue)
                } else null
            }.first { it != null }
            targetScroll.scrollTo(checkNotNull(offset))
        }
        ProfileEditorPage(onDismissRequest = { editingKey = null; targetQuery = ""; targetSearchExpanded = false }, title = { Text(option.label) },
            backTag = "group_target_back",
            topBarSearch = if (searchable) ProfileEditorSearchState(targetSearchExpanded, targetQuery, tr("搜索目标"),
                onQueryChange = { targetQuery = it }, onExpandedChange = { targetSearchExpanded = it }) else null,
            text = {
                if (matchingSections.isEmpty() && terms.isNotEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ProxyPageEmptyState(tr("无搜索结果"), tr("试试其他关键词，或清除搜索查看全部目标。"), "group_target_empty",
                        searching = true, actionLabel = tr("清除搜索"), actionTag = "group_target_clear_search", onAction = { targetQuery = "" })
                } else Box(Modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }.testTag("group_target_viewport")) {
                    Column(Modifier.fillMaxWidth().verticalScroll(targetScroll).onGloballyPositioned { contentCoordinates = it }) {
                    matchingSections.forEach { section ->
                        if (section.title == null) Spacer(Modifier.height(12.dp))
                        else GroupSectionHeading(section.title, Modifier.testTag("group_target_section_${option.key}_${section.key}"))
                        ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                            section.choices.distinct().forEach { choice ->
                                val tagValue = choice.ifBlank { "default" }
                                val selectedChoice = choice == group.text(option.key)
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !confirming && !closing) {
                                    change { it.addProperty(option.key, choice) }
                                    editingKey = null; targetQuery = ""; targetSearchExpanded = false
                                }.padding(horizontal = 16.dp, vertical = 12.dp).testTag(
                                    if (selectedChoice) "group_target_selected_${option.key}_$tagValue"
                                    else "group_target_choice_${option.key}_$tagValue").onGloballyPositioned {
                                    if (selectedChoice) selectedCoordinates = it
                                },
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(choice.ifBlank { tr("默认") }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (selectedChoice) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    }
                }
            })
    }
    "options" -> ProfileEditorPage(onDismissRequest = {
        collapseOptionSearch(); addOption = false
    }, title = { Text(tr("添加设置项")) },
        backTag = "proxy_group_add_option_back", contentPadding = PaddingValues(0.dp),
        topBarSearch = if (optionSearchable) ProfileEditorSearchState(optionSearchExpanded, optionQuery, tr("搜索设置项"),
            onQueryChange = { optionQuery = it }, onExpandedChange = { expanded ->
                optionSearchExpanded = expanded
                if (!expanded) collapseOptionSearch()
            }) else null,
        text = {
        val report = LocalEmbeddedProfilePage.current?.reportBodyHeight
        if (matchingOptions.isEmpty() && optionTerms.isNotEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ProxyPageEmptyState(tr("无搜索结果"), tr("试试其他关键词，或清除搜索查看全部设置项。"), "group_option_empty",
                searching = true, actionLabel = tr("清除搜索"), actionTag = "group_option_clear_search", onAction = { optionQuery = "" })
        } else Column(Modifier.fillMaxWidth().verticalScroll(optionScroll).then(Modifier.onSizeChanged { report?.invoke(it.height) })) {
            val healthKeys = setOf("url", "interval", "timeout", "max-failed-times", "lazy", "expected-status", "tolerance")
            listOf(tr("健康检查") to true, tr("其他") to false).forEach { (heading, health) ->
                val options = matchingOptions.filter { (it.key in healthKeys) == health }
                if (options.isNotEmpty()) {
                    GroupSectionHeading(heading)
                    options.forEach { option ->
                        // 作者 long：添加只更新本组草稿并移除候选；剩余候选可继续添加，最后一项自动返回，始终不触发父表单保存。
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("group_add_" + option.key).clickable {
                            // 作者 long：末项按完整候选目录判断，搜索隐藏其他项时不能提前关闭添加面板。
                            val last = remainingOptions.size == 1
                            change { when (option.kind) {
                                "boolean" -> it.addProperty(option.key, option.default.toBoolean())
                                "number" -> it.addProperty(option.key, option.default.toLong())
                                else -> it.addProperty(option.key, option.default)
                            } }
                            shownOptionKeys = (shownOptionKeys + option.key).distinct()
                            if (last) { collapseOptionSearch(); addOption = false }
                        }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (applicable.all { it.key in shownOptionKeys }) Text(tr("已添加全部设置项"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    })
    "yaml" -> GroupQuickYaml(group, onDismiss = { yaml = false }, onApply = { next ->
        json = next.json
        shownOptionKeys = next.fields.keySet().filter { key -> groupOptions.any { it.key == key } }
        yaml = false
        error = null
    })
    }
    }
    editing?.takeUnless { it.kind == "target" }?.let { option ->
        val choices = when (option.kind) {
            "type" -> ProxyGroupDrafts.types
            "strategy" -> listOf("consistent-hashing", "round-robin", "sticky-sessions")
            "hash" -> listOf("", "in-user")
            else -> emptyList()
        }
        GroupValueDialog(option, group.text(option.key), choices, onDismiss = { editingKey = null }, onSave = { value ->
            change { fields -> if (option.kind == "number") fields.addProperty(option.key, value.toLong()) else fields.addProperty(option.key, value) }
            editingKey = null
        })
    }
    if (discard) EditorAlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("放弃此策略组修改？")) },
        confirmButton = { TextButton(onClick = { acceptedJson = null; discard = false; closing = true }) { Text(tr("放弃修改")) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(tr("继续编辑")) } })
}

@Composable
private fun GroupsQuickYaml(original: String, groups: List<ProxyGroupDraft>,
    onDismiss: () -> Unit, onApply: (List<ProxyGroupDraft>) -> Unit) {
    GroupYamlPage(original, tr("策略组 YAML"), "proxy_groups_yaml", onDismiss,
        parse = { value -> ProxyGroupDrafts.readEditorYaml(value).map { next ->
            groups.firstOrNull { it.name == next.name }?.let { next.copy(key = it.key, originalName = it.originalName, memberLayout = it.memberLayout) }
                ?: next.copy(originalName = null)
        } }, locate = ProxyGroupDrafts::editorErrorLocation, onApply = onApply)
}

@Composable
private fun GroupQuickYaml(group: ProxyGroupDraft, onDismiss: () -> Unit, onApply: (ProxyGroupDraft) -> Unit) {
    val original = remember(group.key) { ProxyGroupDrafts.definitionYaml(group) }
    GroupYamlPage(original, tr("快捷 YAML"), "proxy_group_yaml", onDismiss,
        parse = { ProxyGroupDrafts.definition(it, group) },
        locate = com.lonnnnnng.flclash.service.YamlConfigCodec::errorLocation, onApply = onApply)
}

/** 作者 long：两种快捷入口共用校验与返回保护，定位只改选区，确认成功只更新父草稿，不提前写文件。 */
@Composable
private fun <T> GroupYamlPage(original: String, title: String, tag: String, onDismiss: () -> Unit,
    parse: (String) -> T, locate: (String, Throwable) -> com.lonnnnnng.flclash.service.YamlErrorLocation?, onApply: (T) -> Unit) {
    var value by rememberSaveable(original, stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(original))
    }
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    var errorOffset by rememberSaveable { mutableStateOf<Int?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun locateError() {
        errorOffset?.let { value = value.copy(selection = androidx.compose.ui.text.TextRange(it.coerceIn(0, value.text.length))) }
        focus.clearFocus(force = true); keyboard?.hide()
    }
    fun apply(leaving: Boolean) {
        focus.clearFocus(force = true); keyboard?.hide()
        if (leaving && value.text == original) { onDismiss(); return }
        runCatching { parse(value.text) }.onSuccess(onApply).onFailure { error ->
            val position = locate(value.text, error)
            errorOffset = position?.offset
            // 作者 long：解析器异常包含原文片段，界面只显示安全行列和修复提示，不能回显潜在凭据。
            failure = if (position == null) "策略组 YAML 格式无效，请检查列表、名称和类型"
                else tr("第 {0} 行，第 {1} 列格式无效，请检查缩进和括号", position.line, position.column)
            locateError()
            if (leaving) discard = true
        }
    }
    ProfileEditorPage(onDismissRequest = { apply(true) }, title = { Text(title) }, backTag = "${tag}_back",
        confirmButton = { TextButton(onClick = { apply(false) }, Modifier.testTag("${tag}_confirm")) { Text(tr("确认")) } },
        contentPadding = PaddingValues(), text = {
            Column(Modifier.fillMaxSize().testTag("${tag}_scroll")) {
                failure?.let { message -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(message), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f).testTag("${tag}_error"))
                    if (errorOffset != null) TextButton(::locateError, Modifier.testTag("${tag}_locate_error")) { Text(tr("定位")) }
                } }
                ProfileCodeField(value, { next ->
                    if (next.text != value.text) { failure = null; errorOffset = null }
                    value = next
                }, true, Modifier.fillMaxWidth().weight(1f), contentTag = "${tag}_content",
                    scrollState = LocalEmbeddedProfilePage.current?.scroll ?: rememberScrollState())
            }
        })
    if (discard) EditorAlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("放弃 YAML 修改？")) },
        text = { Text(tr(failure.orEmpty())) }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("放弃修改")) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(tr("继续编辑")) } })
}

// 作者 long：Fallback 的内置目标与普通代理来自不同命名域，分组呈现可避免用户把回退策略看成代理节点。
private fun groupTargetSections(option: GroupOption, group: ProxyGroupDraft, source: String): List<GroupTargetSection> =
    if (option.key == "empty-fallback") {
        val basic = ProxyGroupDrafts.builtins.toList()
        listOf(
            GroupTargetSection("basic", tr("基础策略"), basic),
            GroupTargetSection("proxies", tr("代理"), ProxyGroupDrafts.proxyNames(source).filterNot { it in basic }),
        )
    } else {
        listOf(
            GroupTargetSection("default", null, listOf("")),
            GroupTargetSection("proxies", tr("代理"), ProxyGroupDrafts.members(group).filterNot { it.provider }.map { it.name }),
        )
    }

@Composable
private fun GroupSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GroupFieldRow(label: String, value: String, tag: String, onClick: () -> Unit) {
    ProfileFormRow(label, Modifier.testTag(tag).clickable(onClick = onClick)) {
        Text(value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 作者 long：健康检查的可选值直接留在原行编辑，清空只移除 YAML 值，不丢失当前表单里的选项行。 */
@Composable
private fun GroupOptionInlineInput(
    groupKey: String,
    option: GroupOption,
    value: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val number = option.kind == "number"
    val maxLength = when (option.key) {
        "url" -> 2048
        "expected-status" -> 128
        else -> 10
    }
    var draft by rememberSaveable(groupKey, option.key) { mutableStateOf(value) }
    LaunchedEffect(value) { if (draft != value) draft = value }
    ProfileFormInput(
        title = option.label,
        value = draft,
        onValueChange = { candidate ->
            val next = if (number) candidate.filter(Char::isDigit).take(maxLength) else candidate.take(maxLength)
            if (!number || next.isEmpty() || next.toLongOrNull() != null) {
                draft = next
                onValueChange(next)
            }
        },
        tag = "group_option_${option.key}_input",
        leading = {
            IconButton(onRemove, Modifier.testTag("group_remove_${option.key}")) {
                Icon(Icons.Outlined.RemoveCircleOutline, null)
            }
        },
        placeholder = tr("可选"),
        unit = when (option.key) { "interval" -> "s"; "timeout", "tolerance" -> "ms"; else -> null },
        keyboardOptions = KeyboardOptions(keyboardType = when {
            number -> KeyboardType.Number
            option.key == "url" -> KeyboardType.Uri
            else -> KeyboardType.Text
        }),
    )
}

@Composable
private fun GroupValueDialog(option: GroupOption, initial: String, choices: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(option.key) { mutableStateOf(initial) }
    var error by rememberSaveable(option.key) { mutableStateOf<String?>(null) }
    EditorAlertDialog(onDismissRequest = onDismiss, title = { Text(option.label) }, text = {
        if (choices.isNotEmpty()) Column(Modifier.verticalScroll(rememberScrollState())) {
            choices.distinct().forEach { choice -> TextButton(onClick = { onSave(choice) }, Modifier.fillMaxWidth()) { Text(choice.ifBlank { tr("默认") }) } }
        } else Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(value, { value = it; error = null }, Modifier.fillMaxWidth().testTag("group_value_input"), isError = error != null,
                supportingText = { error?.let { Text(tr(it)) } }, minLines = 1, maxLines = 4)
            if (option.key in setOf("filter", "exclude-filter")) FilterPresetChoices(value) { value = it; error = null }
        }
    }, confirmButton = { if (choices.isEmpty()) TextButton(onClick = {
        if (option.kind == "number" && value.toLongOrNull()?.let { it >= 0 } != true) error = "请输入非负整数"
        else if (option.key == "name" && value.isBlank()) error = "名称不能为空"
        else onSave(if (option.key == "name") value.trim() else value)
    }, modifier = Modifier.testTag("group_value_confirm")) { Text(tr("确认")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } })
}
