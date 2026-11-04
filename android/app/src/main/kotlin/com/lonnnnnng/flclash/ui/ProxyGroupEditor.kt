package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class GroupOption(val key: String, val label: String, val default: String, val kind: String = "text")
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
    save = { groups -> groups.flatMap { listOf(it.key, it.originalName.orEmpty(), it.json) } },
    restore = { values -> values.chunked(3).map { ProxyGroupDraft(it[0], it[1].ifEmpty { null }, it[2]) } },
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
    onValidateFilters: suspend (List<String>) -> List<String> = { error("过滤表达式校验不可用") },
    recentIcons: List<String> = emptyList(),
    onRecordIcon: (String, Boolean) -> Unit = { _, _ -> },
    onReadIcon: suspend (String) -> String = { error("图片读取不可用") },
    resourceProfiles: List<Profile> = emptyList(),
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
    val parsed = remember(source) { runCatching { ProxyGroupDrafts.read(source) } }
    var groups by rememberSaveable(profile.id, source, stateSaver = groupDraftSaver) { mutableStateOf(parsed.getOrDefault(emptyList())) }
    val initial = remember(source) { parsed.getOrDefault(emptyList()) }
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
    val dirty = groups.map { it.json } != initial.map { it.json }
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
                    errors.drop(index * 2).take(2).filter(String::isNotEmpty).map { ProxyGroupIssue("过滤表达式无效：$it") } }.toMap()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                listFilterIssues = groups.associate { it.key to if (it.text("filter").isNotBlank() || it.text("exclude-filter").isNotBlank())
                    listOf(ProxyGroupIssue("无法校验过滤表达式，请重试")) else emptyList() }
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
        title = { Text("代理组 · " + profile.realLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = ::commit, enabled = !saving && parsed.isSuccess, modifier = Modifier.testTag("proxy_group_list_save")) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("保存")
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("proxy_group_error")) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selecting) "已选择 ${selected.size} 项" else "策略组 · ${groups.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
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
                if (sorting) Text("长按拖动调整顺序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (groups.isEmpty()) Text("暂无策略组", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                groups.forEachIndexed { index, group ->
                    key(group.key) {
                        GroupOrderRow(index, groups.size, group.key, sorting, !saving,
                            onMove = { to -> groups = groups.toMutableList().apply { add(to, removeAt(index)) } }) {
                            Row(Modifier.weight(1f).heightIn(min = 72.dp).testTag("proxy_group_row_$index")
                                .clickable(enabled = !saving && !sorting) { if (selecting) toggle(group.key) else { selectedKey = group.key; creating = false } }
                                .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfileGroupIcon(group.text("icon"), group.name, Modifier.padding(end = 12.dp).size(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name.ifBlank { "未命名策略组" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(group.text("type") + " · " + ProxyGroupDrafts.members(group).size + " 个成员",
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
                                        AppDropdownMenuItem(text = { Text("复制") }, onClick = {
                                            groups = ProxyGroupIssues.duplicate(groups, setOf(group.key), source); menu = false
                                        }, modifier = Modifier.testTag("proxy_group_copy_$index"))
                                        AppDropdownMenuItem(text = { Text("删除") }, danger = true,
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
                    }.toString())) }, enabled = !saving, modifier = Modifier.testTag("proxy_groups_yaml")) { Text("编辑原始 YAML") }
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
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃策略组修改？") }, text = { Text("尚未保存的策略组修改会丢失。") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    if (deleteKeys.isNotEmpty()) {
        val targets = groups.filter { it.key in deleteKeys }
        val inUse = ProxyGroupIssues.referencedNames(groups, deleteKeys, source)
        AlertDialog(onDismissRequest = { deleteKeys = emptySet() }, title = { Text("删除策略组？") },
            text = { Text(if (inUse.isNotEmpty()) "以下策略组仍被引用：${inUse.joinToString("、")}。删除后需要修复规则或节点引用。"
                else "将删除 ${targets.size} 个策略组：${targets.joinToString("、") { it.name }}") },
            confirmButton = { TextButton(onClick = {
                val values = groups.filterNot { it.key in deleteKeys }
                groups = values; if (selectedKey in deleteKeys) selectedKey = null
                deleteKeys = emptySet(); selected = emptyList(); selecting = false
                if (initialGroupIndex != null && onSaveDraft != null) onSaveDraft(saveDraft(values))
            }, enabled = !saving, modifier = Modifier.testTag("proxy_group_delete_confirm")) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteKeys = emptySet() }) { Text("取消") } })
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
    val group = initial.copy(json = json)
    var iconPicker by rememberSaveable(initial.key) { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var filterErrors by remember { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()
    val filters = listOf(group.text("filter"), group.text("exclude-filter"))
    LaunchedEffect(filters) {
        filterErrors = emptyList()
        if (filters.any(String::isNotBlank)) {
            delay(300)
            try { filterErrors = validateFilters(filters) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { filterErrors = listOf("无法校验过滤表达式，请重试") }
        }
    }
    val issues = remember(json, groups, source) { runCatching { ProxyGroupIssues.inspect(group, groups.map { if (it.key == group.key) group else it }, source) }.getOrDefault(emptyList()) } +
        filterErrors.filter(String::isNotEmpty).map { ProxyGroupIssue("过滤表达式无效：$it") }
    // 作者 long：只保存选项身份，重建时从同一字段目录恢复子页，避免父草稿还在而未确认的值窗口已丢失。
    var editingKey by rememberSaveable(initial.key) { mutableStateOf<String?>(null) }
    val editing = (groupIdentityOptions + groupOptions).firstOrNull { it.key == editingKey }
    var addOption by rememberSaveable(initial.key) { mutableStateOf(false) }
    var members by rememberSaveable(initial.key) { mutableStateOf(false) }
    var yaml by rememberSaveable(initial.key) { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val clearFocus = remember { arrayOf<(() -> Unit)?>(null) }
    fun change(action: (com.google.gson.JsonObject) -> Unit) { json = group.edit(action).json; error = null }
    fun exit() { clearFocus[0]?.invoke(); if (json != initial.json) discard = true else onDismiss() }
    fun confirm() {
        if (confirming) return
        clearFocus[0]?.invoke()
        confirming = true
        scope.launch {
            try {
                // 作者 long：核心异步校验期间表单仍可编辑；只提交最后一次确认的同一份快照，迟到结果不能写入新草稿。
                val frozen = json
                val candidate = initial.copy(json = frozen)
                val candidates = groups.map { if (it.key == candidate.key) candidate else it }
                val blocking = ProxyGroupIssues.inspect(candidate, candidates, source).filter { it.blocking }
                require(blocking.isEmpty()) { blocking.joinToString("\n") { it.message } }
                val values = listOf(candidate.text("filter"), candidate.text("exclude-filter"))
                val errors = if (values.all(String::isBlank)) emptyList() else validateFilters(values).filter(String::isNotEmpty)
                require(errors.isEmpty()) { "过滤表达式无效：" + errors.joinToString("\n") }
                if (json != frozen) { error = "表单已更新，请再次确认"; return@launch }
                onConfirm(candidate)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "无法校验策略组，请重试" }
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
    ProfileEditorPage(onDismissRequest = ::exit, title = { Text(if (creating) "添加策略组" else "编辑策略组") }, backTag = "proxy_group_form_back", contentPadding = PaddingValues(0.dp),
        confirmButton = {
            IconButton(onClick = { clearFocus[0]?.invoke(); yaml = true }, modifier = Modifier.testTag("proxy_group_quick_yaml")) { Icon(Icons.Outlined.Code, null) }
            TextButton(onClick = ::confirm, enabled = !confirming, modifier = Modifier.testTag("proxy_group_form_confirm")) { Text(if (confirming) "校验中…" else "确认") }
        }, text = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            DisposableEffect(focus, keyboard) { clearFocus[0] = { focus.clearFocus(force = true); keyboard?.hide() }; onDispose { clearFocus[0] = null } }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                error?.takeUnless { message -> issues.any { it.message == message } }?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("proxy_group_form_error")) }
                GroupIssuesBanner(issues, "proxy_group_form_issues")
                GroupSectionHeading("常规")
                ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                    GroupFieldRow("名称", group.name.ifBlank { "未命名" }, "proxy_group_name") { editingKey = "name" }
                    GroupFieldRow("类型", group.text("type"), "proxy_group_type") { editingKey = "type" }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileGroupIcon(group.text("icon"), group.name, Modifier.size(32.dp).padding(end = 8.dp))
                        Box(Modifier.weight(1f)) { GroupFieldRow("图标", if (group.text("icon").isBlank()) "默认" else "已设置", "proxy_group_icon") { clearFocus[0]?.invoke(); iconPicker = true } }
                    }
                }
                GroupSectionHeading("节点")
                ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                    GroupFieldRow("成员", ProxyGroupDrafts.members(group).size.toString() + " 个", "proxy_group_members") { members = true }
                    listOf("include-all-proxies" to "包含全部代理", "include-all-providers" to "包含全部资源").forEach { (key, label) ->
                        ProfileFormRow(label) {
                            Switch(group.flag(key), { value -> change { if (value) it.addProperty(key, true) else it.remove(key) } }, Modifier.testTag("proxy_group_$key"))
                        }
                    }
                    GroupFieldRow("过滤表达式", group.text("filter").ifBlank { "未设置" }, "proxy_group_filter") { editingKey = "filter" }
                    GroupFieldRow("排除表达式", group.text("exclude-filter").ifBlank { "未设置" }, "proxy_group_exclude_filter") { editingKey = "exclude-filter" }
                }
                GroupSectionHeading("可选设置")
                ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                    applicable.filter { group.fields.has(it.key) }.forEach { option ->
                        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { change { it.remove(option.key) } }, modifier = Modifier.testTag("group_remove_" + option.key)) { Icon(Icons.Outlined.RemoveCircleOutline, null) }
                            if (option.kind == "boolean") {
                                Text(option.label, Modifier.weight(1f).padding(start = 16.dp)); Switch(group.flag(option.key), { value -> change { it.addProperty(option.key, value) } }, modifier = Modifier.padding(end = 16.dp))
                            } else Box(Modifier.weight(1f)) { GroupFieldRow(option.label, group.text(option.key).ifBlank { "默认" }, "group_option_" + option.key) { editingKey = option.key } }
                        }
                    }
                    TextButton(onClick = { addOption = true }, modifier = Modifier.testTag("proxy_group_add_option")) { Icon(Icons.Outlined.Add, null); Text("添加设置项") }
                }
                if (!creating) TextButton(onClick = onDelete, modifier = Modifier.testTag("proxy_group_delete")) { Text("删除策略组", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(16.dp))
            }
        })
    if (iconPicker) ProfileGroupIconPicker(group.text("icon"), group.name, recentIcons, onRecordIcon, onReadIcon,
        onDismiss = { iconPicker = false }, onSelect = { value -> change { if (value.isEmpty()) it.remove("icon") else it.addProperty("icon", value) }; iconPicker = false })
    if (members) GroupMembersEditor(group, groups, source, onDismiss = { members = false }, onConfirm = { json = it.json; members = false; error = null })
    if (addOption) ProfileEditorPage(onDismissRequest = { addOption = false }, title = { Text("添加设置项") },
        backTag = "proxy_group_add_option_back", contentPadding = PaddingValues(0.dp), text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val healthKeys = setOf("url", "interval", "timeout", "max-failed-times", "lazy", "expected-status", "tolerance")
            listOf("健康检查" to true, "其他" to false).forEach { (heading, health) ->
                val options = applicable.filterNot { group.fields.has(it.key) }.filter { (it.key in healthKeys) == health }
                if (options.isNotEmpty()) {
                    GroupSectionHeading(heading)
                    options.forEach { option ->
                        // 作者 long：添加只更新本组草稿并移除候选；剩余候选可继续添加，最后一项自动返回，始终不触发父表单保存。
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("group_add_" + option.key).clickable {
                            val last = applicable.count { !group.fields.has(it.key) } == 1
                            change { when (option.kind) {
                                "boolean" -> it.addProperty(option.key, option.default.toBoolean())
                                "number" -> it.addProperty(option.key, option.default.toLong())
                                else -> it.addProperty(option.key, option.default)
                            } }
                            if (last) addOption = false
                        }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (applicable.all { group.fields.has(it.key) }) Text("已添加全部设置项", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    })
    editing?.let { option ->
        val choices = when (option.kind) {
            "type" -> ProxyGroupDrafts.types
            "strategy" -> listOf("consistent-hashing", "round-robin", "sticky-sessions")
            "hash" -> listOf("", "in-user")
            "target" -> if (option.key == "empty-fallback") ProxyGroupDrafts.builtins + ProxyGroupDrafts.proxyNames(source)
                else listOf("") + ProxyGroupDrafts.members(group).filterNot { it.provider }.map { it.name }
            else -> emptyList()
        }
        GroupValueDialog(option, group.text(option.key), choices, onDismiss = { editingKey = null }, onSave = { value ->
            change { fields -> if (option.kind == "number") fields.addProperty(option.key, value.toLong()) else fields.addProperty(option.key, value) }
            editingKey = null
        })
    }
    if (yaml) GroupQuickYaml(group, onDismiss = { yaml = false }, onApply = { next -> json = next.json; yaml = false; error = null })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃此策略组修改？") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}

@Composable
private fun GroupsQuickYaml(original: String, groups: List<ProxyGroupDraft>,
    onDismiss: () -> Unit, onApply: (List<ProxyGroupDraft>) -> Unit) {
    GroupYamlPage(original, "策略组 YAML", "proxy_groups_yaml", onDismiss,
        parse = { value -> ProxyGroupDrafts.readEditorYaml(value).map { next ->
            groups.firstOrNull { it.name == next.name }?.let { next.copy(key = it.key, originalName = it.originalName) }
                ?: next.copy(originalName = null)
        } }, locate = ProxyGroupDrafts::editorErrorLocation, onApply = onApply)
}

@Composable
private fun GroupQuickYaml(group: ProxyGroupDraft, onDismiss: () -> Unit, onApply: (ProxyGroupDraft) -> Unit) {
    val original = remember(group.key) { ProxyGroupDrafts.definitionYaml(group) }
    GroupYamlPage(original, "快捷 YAML", "proxy_group_yaml", onDismiss,
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
                else "第 ${position.line} 行，第 ${position.column} 列格式无效，请检查缩进和括号"
            locateError()
            if (leaving) discard = true
        }
    }
    ProfileEditorPage(onDismissRequest = { apply(true) }, title = { Text(title) }, backTag = "${tag}_back",
        confirmButton = { TextButton(onClick = { apply(false) }, Modifier.testTag("${tag}_confirm")) { Text("确认") } },
        contentPadding = PaddingValues(), text = {
            Column(Modifier.fillMaxSize().testTag("${tag}_scroll")) {
                failure?.let { message -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f).testTag("${tag}_error"))
                    if (errorOffset != null) TextButton(::locateError, Modifier.testTag("${tag}_locate_error")) { Text("定位") }
                } }
                ProfileCodeField(value, { next ->
                    if (next.text != value.text) { failure = null; errorOffset = null }
                    value = next
                }, true, Modifier.fillMaxWidth().weight(1f), contentTag = "${tag}_content")
            }
        })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃 YAML 修改？") },
        text = { Text(failure.orEmpty()) }, confirmButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}

@Composable
private fun GroupSectionHeading(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
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

@Composable
private fun GroupValueDialog(option: GroupOption, initial: String, choices: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(option.key) { mutableStateOf(initial) }
    var error by rememberSaveable(option.key) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(option.label) }, text = {
        if (choices.isNotEmpty()) Column(Modifier.verticalScroll(rememberScrollState())) {
            choices.distinct().forEach { choice -> TextButton(onClick = { onSave(choice) }, Modifier.fillMaxWidth()) { Text(choice.ifBlank { "默认" }) } }
        } else Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(value, { value = it; error = null }, Modifier.fillMaxWidth().testTag("group_value_input"), isError = error != null,
                supportingText = { error?.let { Text(it) } }, minLines = 1, maxLines = 4)
            if (option.key in setOf("filter", "exclude-filter")) FilterPresetChoices(value) { value = it; error = null }
        }
    }, confirmButton = { if (choices.isEmpty()) TextButton(onClick = {
        if (option.kind == "number" && value.toLongOrNull()?.let { it >= 0 } != true) error = "请输入非负整数"
        else if (option.key == "name" && value.isBlank()) error = "名称不能为空"
        else onSave(if (option.key == "name") value.trim() else value)
    }, modifier = Modifier.testTag("group_value_confirm")) { Text("确认") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun GroupMembersEditor(initial: ProxyGroupDraft, groups: List<ProxyGroupDraft>, source: String, onDismiss: () -> Unit, onConfirm: (ProxyGroupDraft) -> Unit) {
    var encoded by rememberSaveable(initial.key) { mutableStateOf(initial.json) }
    val group = initial.copy(json = encoded)
    val members = ProxyGroupDrafts.members(group)
    var picker by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    fun change(values: List<ProxyGroupMember>) { encoded = ProxyGroupDrafts.withMembers(group, values).json }
    ProfileEditorPage(onDismissRequest = { if (encoded != initial.json) discard = true else onDismiss() }, backTag = "proxy_group_members_back", title = { Text("节点成员") },
        confirmButton = { TextButton(onClick = { onConfirm(group) }, Modifier.testTag("proxy_group_members_confirm")) { Text("确认") } }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(members.size.toString() + " 个成员", Modifier.weight(1f))
                    IconButton(onClick = { sorting = !sorting }, Modifier.testTag("group_members_sort")) { Icon(Icons.AutoMirrored.Outlined.Sort, null) }
                    IconButton(onClick = { picker = true }, Modifier.testTag("group_members_add")) { Icon(Icons.Outlined.Add, null) }
                }
                if (sorting) Text("长按拖动调整代理与资源顺序", style = MaterialTheme.typography.bodySmall)
                members.forEachIndexed { index, member -> key(member) {
                    GroupOrderRow(index, members.size, member.toString(), sorting, true,
                        onMove = { to -> change(members.toMutableList().apply { add(to, removeAt(index)) }) }) {
                        Column(Modifier.weight(1f).heightIn(min = 72.dp).padding(vertical = 12.dp)) {
                            Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (member.provider) "资源" else "代理 / 策略组", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!sorting) IconButton(onClick = { change(members.filterIndexed { i, _ -> i != index }) }, Modifier.testTag("group_member_remove_$index")) { Icon(Icons.Outlined.RemoveCircleOutline, null) }
                    }
                } }
                if (members.isEmpty()) Text("添加代理节点、其他策略组或资源", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
    if (picker) ProfileEditorPage(onDismissRequest = { picker = false }, backTag = "group_member_picker_back", title = { Text("添加节点") },
        confirmButton = { TextButton(onClick = { picker = false }, Modifier.testTag("group_member_picker_done")) { Text("完成") } }, text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("group_member_search"), label = { Text("搜索") }, singleLine = true)
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val choices = (ProxyGroupDrafts.builtins + ProxyGroupDrafts.proxyNames(source) + groups.filter { it.key != group.key }.map { it.name }).distinct().filter { it != group.name }.map { ProxyGroupMember(it) } +
                        ProxyGroupDrafts.providerNames(source).map { ProxyGroupMember(it, true) }
                    choices.filter { it.name.contains(query, ignoreCase = true) }.forEach { member ->
                        Row(Modifier.fillMaxWidth().testTag("group_member_choice_${member.provider}_${member.name}").clickable { change(if (member in members) members - member else members + member) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(member in members, { checked -> change(if (checked) (members + member).distinct() else members - member) })
                            Column(Modifier.weight(1f)) { Text(member.name); if (member.provider) Text("资源", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃成员修改？") }, confirmButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
