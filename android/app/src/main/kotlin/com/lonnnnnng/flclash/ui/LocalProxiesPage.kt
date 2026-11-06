package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.*
import com.lonnnnnng.flclash.data.*

val LocalAddToLocalProxy = staticCompositionLocalOf<((Long?, String) -> Unit)?> { null }
val LocalGlobalProxies = staticCompositionLocalOf<List<LocalProxy>> { emptyList() }

@Composable
internal fun LocalProxiesPage(state: LocalProxiesUiState, onDismiss: () -> Unit, onEdit: (LocalProxy?) -> Unit,
    onSave: (LocalProxy) -> Unit, onCloseEditor: () -> Unit, onLinks: (LocalProxy?) -> Unit,
    onApplyLinks: (String) -> Unit, onCancelLinks: () -> Unit, onReorder: (List<String>) -> Unit,
    onAskDelete: (Set<String>) -> Unit, onDelete: () -> Unit, onCancelDelete: () -> Unit, onConfirmReplacement: () -> Unit,
    onUpdateEditor: (LocalProxyDraft) -> Unit,
    onUiDraftChange: (EditorSaveableState) -> Unit = {},
    onLinksUiDraftChange: (String, EditorSaveableState) -> Unit = { _, _ -> },
    onDraftFailure: () -> Unit = {},
    onEditorUiDraftChange: (String, EditorSaveableState) -> Unit = { _, _ -> }) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    EncryptedEditorState(state.session, state.uiDraft, onUiDraftChange, onDraftFailure) { checkpoint ->
        LocalProxiesPageContent(state, { if (checkpoint()) onDismiss() }, onEdit, onSave, onCloseEditor, onLinks,
            onApplyLinks, onCancelLinks, onReorder, onAskDelete, onDelete, onCancelDelete, onConfirmReplacement,
            { draft ->
                // 作者 long：重建时旧输入框拆除会失焦并回传旧文本；已停止的宿主不能覆盖VM新值，保存树仍单独在ON_STOP捕获。
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onUpdateEditor(draft)
            }, onLinksUiDraftChange, onDraftFailure, onEditorUiDraftChange)
    }
}

@Composable
private fun LocalProxiesPageContent(state: LocalProxiesUiState, onDismiss: () -> Unit, onEdit: (LocalProxy?) -> Unit,
    onSave: (LocalProxy) -> Unit, onCloseEditor: () -> Unit, onLinks: (LocalProxy?) -> Unit,
    onApplyLinks: (String) -> Unit, onCancelLinks: () -> Unit, onReorder: (List<String>) -> Unit,
    onAskDelete: (Set<String>) -> Unit, onDelete: () -> Unit, onCancelDelete: () -> Unit, onConfirmReplacement: () -> Unit,
    onUpdateEditor: (LocalProxyDraft) -> Unit, onLinksUiDraftChange: (String, EditorSaveableState) -> Unit,
    onDraftFailure: () -> Unit, onEditorUiDraftChange: (String, EditorSaveableState) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Set<String>, List<String>>(
        save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val shown = state.rows.filter { listOf(it.name, it.type, it.fields.get("server")?.asString.orEmpty()).any { value -> value.contains(query, true) } }
    val visibleIds = shown.map { it.id }.toSet()
    val keptSelected = selected.intersect(state.rows.map { it.id }.toSet())
    fun exit() { if (!state.busy) when { keptSelected.isNotEmpty() -> selected = emptySet(); sorting -> sorting = false; else -> onDismiss() } }
    ProfileEditorPage(onDismissRequest = ::exit, backTag = "local_proxies_back", title = { Text(if (keptSelected.isEmpty()) "本地代理" else "已选择 ${keptSelected.size} 项") },
        confirmButton = { Row {
            if (keptSelected.isNotEmpty()) {
                IconButton(onClick = { selected = if (keptSelected.containsAll(visibleIds)) keptSelected - visibleIds else keptSelected + visibleIds }, enabled = !state.busy) { Icon(Icons.Outlined.SelectAll, null) }
                IconButton(onClick = { onAskDelete(keptSelected.intersect(visibleIds)) }, enabled = !state.busy && keptSelected.any { it in visibleIds }) { Icon(Icons.Outlined.Delete, null, modifier = Modifier.testTag("action:删除选中节点")) }
            } else {
                IconButton(onClick = { sorting = !sorting }, enabled = !state.busy && query.isEmpty(), modifier = Modifier.testTag("local_proxies_sort")) { Icon(Icons.AutoMirrored.Outlined.Sort, null) }
                IconButton(onClick = { onLinks(null) }, enabled = !state.busy, modifier = Modifier.testTag("local_proxies_links")) { Icon(Icons.Outlined.EditNote, null) }
                IconButton(onClick = { onEdit(null) }, enabled = !state.busy, modifier = Modifier.testTag("local_proxies_add")) { Icon(Icons.Outlined.Add, null) }
            }
        } }, text = {
            Column(Modifier.fillMaxSize().testTag("local_proxies_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it; sorting = false }, Modifier.fillMaxWidth().testTag("local_proxies_search"), singleLine = true,
                    label = { Text("搜索名称、类型或服务器") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null, modifier = Modifier.testTag("action:清空搜索")) } })
                if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("local_proxies_error")) }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (sorting) Text("长按拖动节点调整顺序", style = MaterialTheme.typography.bodyMedium)
                if (!state.loading && shown.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Hub, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (query.isEmpty()) "暂无本地代理" else "没有匹配的节点")
                        if (query.isEmpty()) FilledTonalButton(onClick = { onEdit(null) }, enabled = !state.busy) { Text("添加节点") }
                    }
                }
                if (sorting) ResourceOrderList(state.rows.map { ResourceOrderItem(it.id, it.name, it.type) }, state.busy,
                    "local_proxies", onReorder, Modifier.weight(1f))
                else LazyColumn(Modifier.weight(1f).testTag("local_proxies_list"), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    itemsIndexed(shown, key = { _, row -> row.id }) { _, row ->
                        ListItem(headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(row.type, maxLines = 1) },
                            trailingContent = { Checkbox(row.id in keptSelected, onCheckedChange = { selected = if (row.id in keptSelected) keptSelected - row.id else keptSelected + row.id }, enabled = !state.busy) },
                            colors = ListItemDefaults.colors(containerColor = if (row.id in keptSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth().testTag("local_proxy_${row.id}").clickable(enabled = !state.busy) {
                                if (keptSelected.isEmpty()) onEdit(row) else selected = if (row.id in keptSelected) keptSelected - row.id else keptSelected + row.id
                            })
                    }
                }
                if (state.editor == null && state.links == null) LocalProxyConfirmations(state, onCancelDelete, onDelete, onConfirmReplacement)
            }
        })
    state.editor?.let { draft ->
      // 作者 long：子页有独立注册表，父页滚动不携带凭据；放弃一个节点后不能在下一窗口恢复它的输入。
      EncryptedEditorState(draft.session, draft.uiDraft, { onEditorUiDraftChange(draft.session, it) }, onDraftFailure) { checkpoint ->
      LocalProxyEditor(draft, state.busy, state.error, { if (checkpoint()) onCloseEditor() }, { if (checkpoint()) onSave(it) }, onLinks, { onAskDelete(setOf(it.id)) }, onUpdateEditor) {
        if (state.links == null) LocalProxyConfirmations(state, onCancelDelete, onDelete, onConfirmReplacement)
      } }
    }
    state.links?.let { draft ->
      EncryptedEditorState(draft.session, draft.uiDraft, { onLinksUiDraftChange(draft.session, it) }, onDraftFailure) { checkpoint ->
        var text by rememberSaveable(draft.session) { mutableStateOf(draft.text) }
        var discard by rememberSaveable { mutableStateOf(false) }
        fun exitLinks() { if (!state.busy) { if (text != draft.text) discard = true else onCancelLinks() } }
        ProfileEditorPage(onDismissRequest = ::exitLinks, title = { Text("分享链接快捷编辑") }, backTag = "local_proxy_links_back",
            confirmButton = { TextButton(onClick = { if (checkpoint()) onApplyLinks(text) }, enabled = !state.busy, modifier = Modifier.testTag("local_proxy_links_save")) { Text("确认") } }, text = {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (draft.single) "输入一个分享链接" else "每行一个分享链接。无法转换为链接的节点会保留。")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("local_proxy_links_error")) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().weight(1f).testTag("local_proxy_links_content"), enabled = !state.busy, label = { Text("分享链接") })
                }
                LocalProxyConfirmations(state, onCancelDelete, onDelete, onConfirmReplacement)
            })
        if (discard) DiscardLocalProxyDialog({ discard = false }, onCancelLinks)
      }
    }
}

/** 作者 long：确认窗口必须依附当前整页编辑窗口，否则 Android 会把父页面的弹窗遮在子编辑器下面。 */
@Composable
private fun LocalProxyConfirmations(state: LocalProxiesUiState, onCancelDelete: () -> Unit, onDelete: () -> Unit, onConfirmReplacement: () -> Unit) {
    if (state.deleting.isNotEmpty()) EditorAlertDialog(onDismissRequest = onCancelDelete, title = { Text("删除 ${state.deleting.size} 个节点？") },
        text = { LocalProxyConfirmationBody(state, "local_proxy_delete") {
            if (state.users.isEmpty()) Text("删除后可重新添加。") else {
                Text("以下配置正在引用节点，删除后引用会保留为缺失状态：")
                state.users.forEach { Text(it) }
            }
        } },
        confirmButton = { TextButton(onClick = onDelete, enabled = !state.busy, modifier = Modifier.testTag("local_proxy_delete_confirm")) { Text("删除") } },
        dismissButton = { TextButton(onClick = onCancelDelete, enabled = !state.busy, modifier = Modifier.testTag("local_proxy_delete_cancel")) { Text("取消") } })
    if (state.replacement != null) EditorAlertDialog(onDismissRequest = onCancelDelete, title = { Text("确认替换节点？") },
        text = {
            val existed = state.rows.map { it.id }.toSet()
            val added = state.replacement.filter { it.id !in existed }.map { it.name }
            LocalProxyConfirmationBody(state, "local_proxy_replace") {
                Text("将移除：${state.removed.joinToString("、")}。" +
                    (if (added.isEmpty()) "" else "将添加：${added.joinToString("、")}。") +
                    "快捷编辑中的改名按新节点处理，原名称引用将保留为缺失。")
            }
        },
        confirmButton = { TextButton(onClick = onConfirmReplacement, enabled = !state.busy, modifier = Modifier.testTag("local_proxy_links_replace")) { Text("确认替换") } },
        dismissButton = { TextButton(onClick = onCancelDelete, enabled = !state.busy) { Text("继续编辑") } })
}

@Composable
private fun LocalProxyConfirmationBody(state: LocalProxiesUiState, tag: String, content: @Composable ColumnScope.() -> Unit) {
    // 作者 long：引用数量不受单屏限制；失败与等待状态固定在当前确认层，避免被父页或长正文遮住。
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("${tag}_pending"))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("${tag}_error")) }
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).testTag("${tag}_body"),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun LocalProxyEditor(draft: LocalProxyDraft, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (LocalProxy) -> Unit, onLinks: (LocalProxy) -> Unit, onDelete: (LocalProxy) -> Unit, onUpdate: (LocalProxyDraft) -> Unit, confirmations: @Composable () -> Unit) {
    val row = draft.value
    val shown = draft.shown
    val choosing = draft.choosing
    fun change(next: LocalProxy) { onUpdate(draft.copy(value = next)) }
    fun choose(kind: String?) { onUpdate(draft.copy(choosing = kind)) }
    fun collection(field: ProxyField) {
        val initial = LocalProxies.value(row.fields, field.path)
        val values = if (field.kind == ProxyFieldKind.MAP) initial?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.map {
            it.key to (if (it.value.isJsonPrimitive) it.value.asString else it.value.toString())
        }.orEmpty() else initial?.let { if (it.isJsonArray) it.asJsonArray.map { value -> "" to value.asString } else listOf("" to it.asString) }.orEmpty()
        onUpdate(draft.copy(collection = LocalProxyCollectionDraft(field, values)))
    }
    fun exit() { if (!busy) { if (row.json != draft.baselineJson) onUpdate(draft.copy(discarding = true)) else onDismiss() } }
    val required = LocalProxies.required(row.type)
    val fields = LocalProxies.catalog.filter { LocalProxyFields.offered(row, it) }
    val credentialIds = setOf("username", "password", "uuid", "privateKey", "token")
    fun update(field: ProxyField, value: JsonElement?) { change(LocalProxyFields.change(row, field, value)) }
    ProfileEditorPage(onDismissRequest = ::exit, backTag = "local_proxy_editor_back", title = { Text(if (draft.original == null) "添加本地代理" else "编辑代理") },
        confirmButton = { Row {
            IconButton(onClick = { onLinks(row) }, enabled = !busy) { Icon(Icons.Outlined.EditNote, null, modifier = Modifier.testTag("action:从分享链接填写")) }
            TextButton(onClick = { onSave(row) }, enabled = !busy, modifier = Modifier.testTag("local_proxy_save")) { Text("保存") }
        } }, text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("基本信息", Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                ProxyTextField("名称", "name", row, busy) { change(LocalProxies.withValue(row, "name", JsonPrimitive(it))) }
                ProfileFormRow("代理类型", Modifier.clickable(!busy) { choose("type") }) { Text(row.type); Spacer(Modifier.width(2.dp)); AppMenuSubmenuArrow() }
                if (row.type != "easytier") {
                    ProxyTextField("服务器", "server", row, busy) { change(LocalProxies.withValue(row, "server", JsonPrimitive(it))) }
                    ProxyTextField("端口", "port", row, busy, number = true) { change(LocalProxies.withValue(row, "port", it.toLongOrNull()?.let(::JsonPrimitive))) }
                }
                fields.filter { it.id in required && it.id !in credentialIds }.forEach { field -> ProxyFieldRow(row, field, busy, false, { update(field, it) }, { choose(field.id) }, { collection(field) }, {}) }
                ProfileFormRow("UDP") { Switch(LocalProxies.value(row.fields, "udp")?.asBoolean == true,
                    { change(LocalProxies.withValue(row, "udp", JsonPrimitive(it))) }, enabled = !busy) }
                if (row.type == "easytier") {
                    listOf("network-name" to "网络名称", "network-secret" to "网络密钥", "ipv4" to "虚拟 IPv4", "hostname" to "主机名").forEach { (path, label) ->
                        ProxyTextField(label, path, row, busy, secret = path == "network-secret") { change(LocalProxies.withValue(row, path, JsonPrimitive(it))) }
                    }
                    listOf("peers", "exit-nodes").forEach { path ->
                        val field = ProxyField(path, path, if (path == "peers") "对等节点" else "出口节点", ProxyFieldKind.LIST)
                        ProxyFieldRow(row, field, busy, false, {}, {}, { collection(field) }, {})
                    }
                }
                }
                // 作者 long：认证字段按上游独立成组，协议参数与UDP仍留在基本信息，密码显隐和未保存确认沿用原草稿。
                val credentials = fields.filter { it.id in required && it.id in credentialIds }
                if (credentials.isNotEmpty()) {
                    Text("身份验证", Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                    ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                    credentials.forEach { field -> ProxyFieldRow(row, field, busy, false, { update(field, it) }, { choose(field.id) }, { collection(field) }, {}) }
                    }
                }
                Text("可选设置", Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                fields.filter { it.id !in required && (it.id in shown || LocalProxies.value(row.fields, it.path) != null) }.forEach { field ->
                    ProxyFieldRow(row, field, busy, true, { update(field, it) }, { choose(field.id) }, { collection(field) }, { onUpdate(draft.copy(shown = shown - field.id, value = LocalProxyFields.change(row, field, null))) })
                }
                TextButton(onClick = { choose("option") }, enabled = !busy, modifier = Modifier.testTag("local_proxy_add_option")) { Icon(Icons.Outlined.AddCircleOutline, null); Spacer(Modifier.width(8.dp)); Text("添加设置项") }
                }
                if (draft.original != null) TextButton(onClick = { onDelete(draft.original) }, enabled = !busy) { Text("删除节点", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(24.dp))
            }
            confirmations()
        })
    choosing?.let { kind ->
        val choices: List<Pair<String, String>> = when (kind) {
            "type" -> LocalProxies.types.map { it to it }
            "option" -> fields.filter { it.id !in required && it.id !in shown && LocalProxies.value(row.fields, it.path) == null }.map { it.id to it.label }
            else -> LocalProxies.catalog.first { it.id == kind }.let { field -> LocalProxyFields.choices(row, field).map { it to it } }
        }
        ProfileEditorPage(onDismissRequest = { choose(null) }, backTag = "local_proxy_choices_back", title = { Text(if (kind == "type") "代理类型" else if (kind == "option") "添加设置项" else "选择设置值") }, text = {
            LazyColumn(Modifier.testTag("local_proxy_choices_$kind")) { itemsIndexed(choices) { _, (id, label) -> ListItem(headlineContent = { Text(label) }, modifier = Modifier.testTag("local_proxy_choice_$id").clickable {
                val next = when (kind) {
                    "type" -> draft.copy(value = LocalProxyFields.changeType(row, id), shown = emptySet())
                    "option" -> {
                        val field = fields.first { it.id == id }
                        val value = when (field.kind) {
                            ProxyFieldKind.TOGGLE -> JsonPrimitive(true)
                            ProxyFieldKind.CHOICE -> LocalProxyFields.choices(row, field).firstOrNull()?.let(::JsonPrimitive)
                            else -> null
                        }
                        draft.copy(shown = shown + id, value = if (value == null) row else LocalProxyFields.change(row, field, value))
                    }
                    else -> draft.copy(value = LocalProxyFields.change(row, LocalProxies.catalog.first { it.id == kind }, JsonPrimitive(id)))
                }
                onUpdate(next.copy(choosing = null))
            }) } }
        })
    }
    draft.collection?.let { collection -> ProxyCollectionEditor(row, collection, busy,
        { onUpdate(draft.copy(collection = it)) }, { onUpdate(draft.copy(collection = null)) },
        { value -> onUpdate(draft.copy(value = LocalProxyFields.change(row, collection.field, value), collection = null)) }) }
    if (draft.discarding) DiscardLocalProxyDialog({ onUpdate(draft.copy(discarding = false)) }, onDismiss)
}

@Composable
private fun ProxyTextField(label: String, path: String, row: LocalProxy, busy: Boolean, secret: Boolean = false, number: Boolean = false, onChange: (String) -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    ProfileFormRow(label) {
        androidx.compose.foundation.text.BasicTextField(
            LocalProxies.value(row.fields, path)?.let { if (it.isJsonPrimitive) it.asString else it.toString() }.orEmpty(),
            { if (!number || it.all(Char::isDigit)) onChange(it.take(8192)) }, Modifier.weight(1f).testTag("local_proxy_field_$path"),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (busy) .38f else 1f)),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
            enabled = !busy, singleLine = true, visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None)
        if (secret) IconButton(onClick = { revealed = !revealed }) {
            Icon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, modifier = Modifier.testTag("action:" + (if (revealed) "隐藏密码" else "显示密码")))
        }
    }
}

@Composable
private fun ProxyFieldRow(row: LocalProxy, field: ProxyField, busy: Boolean, removable: Boolean, onValue: (JsonElement?) -> Unit, onChoice: () -> Unit, onCollection: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (removable) IconButton(onClick = onRemove, enabled = !busy) { Icon(Icons.Outlined.RemoveCircleOutline, null) }
        Box(Modifier.weight(1f)) {
            when (field.kind) {
                ProxyFieldKind.TEXT, ProxyFieldKind.SECRET, ProxyFieldKind.NUMBER -> ProxyTextField(field.label, field.path, row, busy,
                    field.kind == ProxyFieldKind.SECRET, field.kind == ProxyFieldKind.NUMBER) { onValue(if (field.kind == ProxyFieldKind.NUMBER) it.toLongOrNull()?.let(::JsonPrimitive) else JsonPrimitive(it)) }
                ProxyFieldKind.TOGGLE -> ProfileFormRow(field.label) { Switch(LocalProxies.value(row.fields, field.path)?.asBoolean == true, { onValue(JsonPrimitive(it)) }, enabled = !busy) }
                else -> ProfileFormRow(field.label, Modifier.clickable(!busy) { if (field.kind == ProxyFieldKind.CHOICE) onChoice() else onCollection() }) {
                    Text(LocalProxies.value(row.fields, field.path)?.let {
                        when { it.isJsonArray -> "${it.asJsonArray.size()} 项"; it.isJsonObject -> "${it.asJsonObject.size()} 项"; else -> it.asString }
                    } ?: "未设置", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp)); AppMenuSubmenuArrow()
                }
            }
        }
    }
}

@Composable
private fun ProxyCollectionEditor(row: LocalProxy, draft: LocalProxyCollectionDraft, busy: Boolean,
    onUpdate: (LocalProxyCollectionDraft) -> Unit, onDismiss: () -> Unit, onSave: (JsonElement?) -> Unit) {
    val field = draft.field
    val values = draft.values
    val initial = LocalProxies.value(row.fields, field.path)
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    var attempts by remember { mutableIntStateOf(0) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(draft.error, attempts) { if (draft.error != null) list.animateScrollToItem(0) }
    fun change(next: List<Pair<String, String>>) { if (!busy) onUpdate(draft.copy(values = next, error = null)) }
    fun apply(leaving: Boolean) {
        if (busy) return
        keyboard?.hide(); attempts++
        // 作者 long：Flutter 列表和映射子页的返回保留父表单修改；这里只合并父草稿，正式目录仍由父页保存一次。
        val incomplete = field.kind == ProxyFieldKind.MAP && values.any { it.first.isBlank() && it.second.isNotBlank() }
        val keys = values.filter { it.first.isNotBlank() }.map { it.first.lowercase() }
        val error = when {
            incomplete -> "请填写条目名称，或删除未完成的条目"
            field.kind == ProxyFieldKind.MAP && keys.distinct().size != keys.size -> "条目名称重复，请修改后重试"
            else -> null
        }
        if (error != null) { onUpdate(draft.copy(error = error, discarding = leaving)); return }
        if (field.kind == ProxyFieldKind.MAP) {
            val result = JsonObject()
            values.filter { it.first.isNotBlank() }.forEach { (key, value) ->
                val old = initial?.takeIf { it.isJsonObject }?.asJsonObject?.get(key)
                if (old != null && (if (old.isJsonPrimitive) old.asString else old.toString()) == value) result.add(key, old.deepCopy())
                else if (field.id == "httpHeaders") result.add(key, JsonArray().apply { add(value) }) else result.addProperty(key, value)
            }
            onSave(result.takeIf { it.size() > 0 })
        } else onSave(JsonArray().apply { values.filter { it.second.isNotBlank() }.forEach { add(it.second) } }.takeIf { it.size() > 0 })
    }
    ProfileEditorPage(onDismissRequest = { apply(true) }, title = { Text(field.label) }, backTag = "local_proxy_collection_back",
        confirmButton = { TextButton(onClick = { apply(false) }, enabled = !busy, modifier = Modifier.testTag("local_proxy_collection_confirm")) { Text("确认") } }, text = {
        LazyColumn(state = list, modifier = Modifier.testTag("local_proxy_collection_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            draft.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("local_proxy_collection_error")) } }
            itemsIndexed(values) { i, (key, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { change(values.toMutableList().apply { removeAt(i) }) }, enabled = !busy,
                        modifier = Modifier.testTag("local_proxy_collection_remove_$i")) { Icon(Icons.Outlined.RemoveCircleOutline, null) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (field.kind == ProxyFieldKind.MAP) OutlinedTextField(key, { change(values.toMutableList().apply { set(i, it to value) }) },
                            Modifier.fillMaxWidth().testTag("local_proxy_collection_key_$i"), label = { Text("名称") }, singleLine = true, enabled = !busy)
                        OutlinedTextField(value, { change(values.toMutableList().apply { set(i, key to it) }) },
                            Modifier.fillMaxWidth().testTag("local_proxy_collection_value_$i"), label = { Text("值") }, singleLine = true, enabled = !busy)
                    }
                }
            }
            item { TextButton(onClick = { change(values + ("" to "")) }, enabled = !busy, modifier = Modifier.testTag("local_proxy_collection_add")) { Text("添加条目") } }
        }
        if (draft.discarding) DiscardLocalProxyDialog({ onUpdate(draft.copy(discarding = false)) }, onDismiss)
    })
}

@Composable
private fun DiscardLocalProxyDialog(onCancel: () -> Unit, onDiscard: () -> Unit) {
    EditorAlertDialog(onDismissRequest = onCancel, title = { Text("放弃修改？") }, text = { Text("尚未保存的节点修改会丢失。") },
        confirmButton = { TextButton(onClick = onDiscard) { Text("放弃修改") } }, dismissButton = { TextButton(onClick = onCancel) { Text("继续编辑") } })
}
