package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import java.util.UUID

/** 作者 long：DNS空数组是明确覆盖，不能借用把空列表转null的Sniffer编辑器；只有成功回执才退出。 */
@Composable
internal fun GlobalDnsListEditor(field: String, title: String, initial: List<String>?, busy: Boolean,
    onDismiss: () -> Unit, onSave: (JsonElement, (Boolean) -> Unit) -> Unit) {
    val baseline by rememberSaveable(field) { mutableStateOf(initial) }
    var entries by rememberSaveable(field) { mutableStateOf(initial.orEmpty().flatMap { listOf(UUID.randomUUID().toString(), it) }) }
    val rows = entries.chunked(2).map { ResourceOrderItem(it[0], it[1], "") }
    val values = rows.map { it.title }
    var selected by rememberSaveable(field) { mutableStateOf(emptyList<String>()) }
    var entry by rememberSaveable(field) { mutableStateOf<String?>(null) }
    var input by rememberSaveable(field, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var quick by rememberSaveable { mutableStateOf(false) }
    var quickText by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val addSource = rememberAppMenuSource()
    var awaiting by rememberSaveable { mutableStateOf(false) }
    val dirty = values != baseline.orEmpty()
    val locked = busy || awaiting
    fun json(items: List<String>) = JsonArray().apply { items.forEach(::add) }
    fun validate(items: List<String>) = validateDnsField(field, json(items))
    LaunchedEffect(initial, busy, awaiting) {
        // 作者 long：重建期间旧回调可能无法关闭新窗口，以持久值确认已保存；不自动重放尚未确认的写入。
        if (awaiting && !busy) {
            awaiting = false
            if (initial == values) onDismiss() else error = "保存失败，输入已保留，请重试"
        }
    }
    fun save() {
        if (busy || awaiting) return
        // 作者 long：null代表字段已被外部移除，不能与明确清空的[]相等，否则会撤销用户刚恢复的继承。
        if (initial != baseline && initial != values) { error = "DNS 设置已变化，请重新打开后保存"; return }
        runCatching { validate(values) }.onFailure { error = it.message }.onSuccess {
            error = null; awaiting = true
            onSave(json(values)) { success ->
                awaiting = false
                if (success) onDismiss() else error = "保存失败，输入已保留，请重试"
            }
        }
    }
    fun back() {
        if (locked) return
        when { selected.isNotEmpty() -> selected = emptyList(); dirty -> discard = true; else -> onDismiss() }
    }
    ProfileEditorPage(::back, { Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleMedium) }, {
        Column(Modifier.fillMaxSize().padding(top = 12.dp).testTag("global_dns_list_page")) {
            if (locked) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("global_dns_list_saving"))
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_dns_list_error")) }
            if (rows.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(tr("无"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else ResourceOrderList(rows, locked || selected.isNotEmpty(), "global_dns_list", { ids ->
                val byId = rows.associateBy { it.id }; entries = ids.flatMap { listOf(it, byId.getValue(it).title) }
            }, Modifier.weight(1f, fill = false).fillMaxWidth(), compactRows = true, bottomPadding = 8.dp, rowContent = { row ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !locked) {
                    if (selected.isNotEmpty()) selected = if (row.id in selected) selected - row.id else selected + row.id
                    else { entry = row.id; input = TextFieldValue(row.title); inputError = null }
                }.padding(horizontal = 16.dp, vertical = 7.dp).testTag("global_dns_list_row_${rows.indexOf(row)}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(row.title, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    IconToggleButton(row.id in selected, {
                        selected = if (row.id in selected) selected - row.id else selected + row.id
                    }, enabled = !locked, modifier = Modifier.size(32.dp).testTag("global_dns_list_select_${rows.indexOf(row)}")) {
                        Icon(if (row.id in selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (row.id in selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            })
            Text(tr(dnsListDescription(field)), Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, backTag = "global_dns_list_back", navigationButtonSurface = true, navigationButtonSize = 40.dp, toolbarHeight = 72.dp, confirmButton = {
        Row(Modifier.padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected.isNotEmpty()) MoreListActionPill {
                IconButton({ deleting = true }, enabled = !locked, modifier = Modifier.testTag("global_dns_list_delete")) { AppGlyphIcon(AppGlyphs.delete) }
                IconButton({ selected = if (selected.size == rows.size) emptyList() else rows.map { it.id } },
                    enabled = !locked, modifier = Modifier.testTag("global_dns_list_all")) { Icon(Icons.Outlined.SelectAll, null) }
            } else {
                if (dirty) MoreListActionPill {
                    IconButton(::save, enabled = !locked, modifier = Modifier.size(44.dp).testTag("global_dns_list_save")) { AppGlyphIcon(AppGlyphs.check) }
                    IconButton({ reset = true }, enabled = !locked, modifier = Modifier.size(44.dp).testTag("global_dns_list_reset")) { AppGlyphIcon(AppGlyphs.refresh) }
                }
                Box {
                    AppMenuSourceContent(addSource) { MoreListActionPill {
                        IconButton({ addMenu = true }, enabled = !locked, modifier = Modifier.appMenuSource(addSource, dashboardCardColor())
                            .size(44.dp).testTag("global_dns_list_add_menu")) { AppGlyphIcon(AppGlyphs.addCircle) }
                    } }
                    AppDropdownMenu(addMenu, { addMenu = false }, source = addSource) {
                        AppDropdownMenuItem(text = { Text(tr("添加")) }, leadingIcon = { Icon(Icons.Outlined.Add, null) },
                            modifier = Modifier.testTag("global_dns_list_add"), onClick = {
                                addMenu = false; entry = ""; input = TextFieldValue(""); inputError = null
                            })
                        AppDropdownMenuItem(text = { Text(tr("快速编辑")) }, leadingIcon = { AppGlyphIcon(AppGlyphs.compose) },
                            modifier = Modifier.testTag("global_dns_list_quick"), onClick = {
                                addMenu = false; quickText = values.joinToString("\n"); quick = true
                            })
                    }
                }
            }
        }
    })
    entry?.let { editingId ->
        MoreListEntryDialog(if (editingId.isEmpty()) tr("添加") else tr("编辑"), input.text, inputError,
            { if (it.length <= 4096) { input = TextFieldValue(it); inputError = null } }, { entry = null }) {
                val value = input.text.trim()
                runCatching {
                    require('\n' !in value && '\r' !in value && value.isNotEmpty()) { "每行填写一个有效值" }
                    require(rows.none { it.id != editingId && it.title == value }) { "已存在相同的项" }
                    val next = if (editingId.isEmpty()) values + value else rows.map { if (it.id == editingId) value else it.title }
                    validate(next)
                    entries = if (editingId.isEmpty()) entries + listOf(UUID.randomUUID().toString(), value)
                        else rows.flatMap { listOf(it.id, if (it.id == editingId) value else it.title) }
                    entry = null
                }.onFailure { inputError = it.message }
        }
    }
    if (quick) MoreListQuickEditor(title, quickText, "global_dns_list_quick_input", { quickText = it }, { quick = false }) { text ->
        val items = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(items.distinct().size == items.size) { "已存在相同的项" }
        validate(items); entries = items.flatMap { listOf(UUID.randomUUID().toString(), it) }
    }
    if (discard) EditorAlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("保存修改？")) }, confirmButton = {
        TextButton({ discard = false; save() }, enabled = !locked) { Text(tr("保存")) }
    }, dismissButton = {
        Row { TextButton({ discard = false }) { Text(tr("继续编辑")) }; TextButton({ discard = false; onDismiss() }) { Text(tr("放弃")) } }
    })
    if (reset) EditorAlertDialog(onDismissRequest = { reset = false }, title = { Text(tr("重置本页修改？")) },
        confirmButton = { TextButton({
            entries = baseline.orEmpty().flatMap { listOf(UUID.randomUUID().toString(), it) }; selected = emptyList(); reset = false; error = null
        }, modifier = Modifier.testTag("global_dns_list_reset_confirm")) { Text(tr("重置")) } },
        dismissButton = { TextButton({ reset = false }) { Text(tr("取消")) } })
    if (deleting) EditorAlertDialog(onDismissRequest = { deleting = false }, title = { Text(tr("删除 {0} 项？", selected.size)) },
        confirmButton = { TextButton({ entries = rows.filterNot { it.id in selected }.flatMap { listOf(it.id, it.title) }; selected = emptyList(); deleting = false },
            modifier = Modifier.testTag("global_dns_list_delete_confirm")) { Text(tr("删除")) } },
        dismissButton = { TextButton({ deleting = false }) { Text(tr("取消")) } })
}

private fun dnsListDescription(field: String) = when (field) {
    "nameserver" -> "默认使用的 DNS 服务器，如 223.5.5.5、tls://1.1.1.1、https://dns.google/dns-query。"
    "default-nameserver" -> "用于解析其他 DNS 服务器的域名，只能填写 IP 地址。"
    "fallback" -> "Nameserver 的结果被 Fallback 过滤判定为污染时，改用这些服务器的结果。"
    "proxy-server-nameserver" -> "仅用于解析代理服务器的域名。"
    "direct-nameserver" -> "用于解析直连连接的域名。"
    else -> "每行一项；留空保存为空列表，移除字段恢复继承"
}
