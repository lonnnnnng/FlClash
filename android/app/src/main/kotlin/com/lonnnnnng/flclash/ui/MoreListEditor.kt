package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** 作者 long：列表内增删和排序只更新草稿，确认后沿父分区提交；端口清空保留协议，其他空列表恢复继承。 */
@Composable
internal fun MoreListEditor(field: MoreField, initial: JsonElement, root: JsonObject, saving: Boolean,
    saveError: String?, onDismiss: () -> Unit, onConfirm: (JsonElement?) -> Unit) {
    val protocol = field.kind == MoreFieldKind.PROTOCOL
    val initialItems = if (protocol) initial.asJsonObject.getAsJsonArray("ports")?.map { it.asString }.orEmpty()
        else initial.asJsonArray.map { it.asString }
    val original = initialItems
    var items by rememberSaveable(field.key) { mutableStateOf(initialItems) }
    var selected by rememberSaveable(field.key) { mutableStateOf(emptyList<String>()) }
    var entry by rememberSaveable(field.key) { mutableStateOf<String?>(null) }
    var input by rememberSaveable(field.key) { mutableStateOf("") }
    var quick by rememberSaveable(field.key) { mutableStateOf(false) }
    var quickText by rememberSaveable(field.key) { mutableStateOf("") }
    var quickDiscard by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingReceipt by rememberSaveable(field.key) { mutableStateOf(false) }
    LaunchedEffect(initialItems, saving, saveError) {
        // 作者 long：重建会丢失父页内存中的关闭标记；以持久化列表回读确认本次提交，不能让已保存内容继续显示为草稿。
        if (awaitingReceipt && !saving) {
            if (initialItems == items) { awaitingReceipt = false; onDismiss() }
            else if (saveError != null) awaitingReceipt = false
        }
    }
    val title = if (protocol) "端口" else field.label
    val dirty = items != original
    fun valueOf(values: List<String>): JsonElement? = if (protocol) initial.asJsonObject.deepCopy().apply {
        add("ports", Gson().toJsonTree(values))
    } else values.takeIf { it.isNotEmpty() }?.let { Gson().toJsonTree(it) }
    fun validate(values: List<String>) {
        val next = root.deepCopy()
        setMoreField(next, field, valueOf(values))
        validateMoreFields(next, false)
    }
    fun save() {
        if (saving) return
        runCatching { validate(items); error = null; awaitingReceipt = true; onConfirm(valueOf(items)) }
            .onFailure { awaitingReceipt = false; error = it.message ?: "列表无效，请检查后重试" }
    }
    fun exit() {
        if (saving) return
        when {
            selected.isNotEmpty() -> selected = emptyList()
            dirty -> discard = true
            else -> onDismiss()
        }
    }
    ProfileEditorPage(::exit, title = { Text(if (selected.isEmpty()) title else "已选择 ${selected.size} 项") },
        navigationButtonSurface = true, backTag = "more_value_back", confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            if (selected.isNotEmpty()) {
                IconButton({ delete = true }, enabled = !saving, modifier = Modifier.testTag("more_list_delete")) { AppGlyphIcon(AppGlyphs.delete) }
                IconButton({ selected = if (selected.size == items.size) emptyList() else items.indices.map(Int::toString) }, enabled = !saving,
                    modifier = Modifier.testTag("more_list_select_all")) { Icon(Icons.Outlined.SelectAll, null) }
            } else {
                if (dirty) {
                    IconButton({ reset = true }, enabled = !saving, modifier = Modifier.testTag("more_list_reset")) { AppGlyphIcon(AppGlyphs.refresh) }
                    IconButton({ focus.clearFocus(force = true); keyboard?.hide(); save() }, enabled = !saving,
                        modifier = Modifier.testTag("more_value_confirm")) { AppGlyphIcon(AppGlyphs.check) }
                }
                Box {
                    IconButton({ addMenu = true }, enabled = !saving, modifier = Modifier.testTag("more_list_add_menu")) { AppGlyphIcon(AppGlyphs.addCircle) }
                    DropdownMenu(addMenu, { addMenu = false }) {
                        DropdownMenuItem(text = { Text("添加") }, modifier = Modifier.testTag("more_list_add"), onClick = {
                            addMenu = false; input = ""; inputError = null; entry = ""
                        })
                        DropdownMenuItem(text = { Text("快速编辑") }, modifier = Modifier.testTag("more_list_quick"), onClick = {
                            addMenu = false; quickText = items.joinToString("\n"); inputError = null; quick = true
                        })
                    }
                }
            }
        }, text = {
            Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("more_list_saving"))
                (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("more_value_error")) }
                if (items.isEmpty()) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.offset(y = -(maxHeight * .14f)), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileEmptyIllustration(AppGlyphs.resources, flutterPalette = true)
                        Text("暂无数据", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                } else ResourceOrderList(items.mapIndexed { index, text -> ResourceOrderItem(index.toString(), text, "") }, saving || selected.isNotEmpty(), "more_list",
                    onReorder = { order -> items = order.map { items[it.toInt()] }; error = null }, modifier = Modifier.weight(1f), compactRows = true,
                    rowContent = { row ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !saving) {
                            if (selected.isNotEmpty()) selected = if (row.id in selected) selected - row.id else selected + row.id
                            else { entry = row.id; input = row.title; inputError = null }
                        }.testTag("more_list_row_${row.id}"), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title, Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp))
                            IconButton({ selected = if (row.id in selected) selected - row.id else selected + row.id },
                                enabled = !saving, modifier = Modifier.testTag("more_list_select_${row.title}")) {
                                Icon(if (row.id in selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    null, tint = if (row.id in selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    })
                if (field.description.isNotEmpty()) Text(field.description, Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
    if (entry != null) AlertDialog(onDismissRequest = { entry = null }, title = { Text(if (entry.isNullOrEmpty()) "添加$title" else "编辑$title") },
        text = { Column {
            OutlinedTextField(input, { input = it; inputError = null }, singleLine = true,
                modifier = Modifier.testTag("more_list_entry_input"), label = { Text(if (protocol) "端口或范围" else field.label) })
            inputError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("more_list_input_error")) }
        } }, confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(onClick = {
                runCatching {
                    val next = input.trim(); require(next.isNotEmpty()) { "请输入$title" }
                    require(items.getOrNull(entry?.toIntOrNull() ?: -1) == next || next !in items) { "该项已存在" }
                    val values = if (entry.isNullOrEmpty()) items + next else items.mapIndexed { index, value -> if (index.toString() == entry) next else value }
                    validate(values); items = values; error = null
                    focus.clearFocus(force = true); keyboard?.hide(); entry = null
                }.onFailure { inputError = it.message ?: "内容无效" }
            }, modifier = Modifier.testTag("more_list_entry_confirm")) { Text("确认") }
        }, dismissButton = { TextButton({ entry = null }) { Text("取消") } })
    if (quick) ProfileEditorPage({ if (quickText != items.joinToString("\n")) quickDiscard = true else quick = false }, title = { Text("快速编辑 · $title") }, backTag = "more_list_quick_back",
        navigationButtonSurface = true, confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            IconButton(onClick = {
                runCatching {
                    val values = splitMoreList(quickText); validate(values); items = values; selected = emptyList(); error = null
                    focus.clearFocus(force = true); keyboard?.hide(); quick = false
                }.onFailure { inputError = it.message ?: "列表无效" }
            }, modifier = Modifier.testTag("more_list_quick_confirm")) { AppGlyphIcon(AppGlyphs.check) }
        }, text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("more_list_input_error")) }
                OutlinedTextField(quickText, { quickText = it; inputError = null }, minLines = 6,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).testTag(if (protocol) "sniff_${field.protocol}_ports" else "more_${field.key}_value"),
                    label = { Text("每行一项") }, supportingText = { Text(if (protocol) "留空使用协议默认端口" else "留空恢复继承原配置") })
            }
        })
    if (quickDiscard) AlertDialog(onDismissRequest = { quickDiscard = false }, title = { Text("放弃快速编辑？") },
        confirmButton = { TextButton({ quickDiscard = false; quick = false }) { Text("放弃") } },
        dismissButton = { TextButton({ quickDiscard = false }) { Text("继续编辑") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("保存修改？") },
        confirmButton = { TextButton({ discard = false; save() }) { Text("保存") } },
        dismissButton = { Row {
            TextButton({ discard = false; onDismiss() }) { Text("放弃") }
            TextButton({ discard = false }) { Text("继续编辑") }
        } })
    if (delete || reset) AlertDialog(onDismissRequest = { delete = false; reset = false },
        title = { Text(if (delete) "删除所选项？" else "重置本页修改？") },
        confirmButton = { TextButton({
            // 作者 long：确认删除/重置只改变本页列表，父分区的已保存协议和目标设置必须等明确保存后才更新。
            items = if (delete) items.filterIndexed { index, _ -> index.toString() !in selected } else original
            selected = emptyList(); error = null; delete = false; reset = false
        }, modifier = Modifier.testTag("more_list_action_confirm")) { Text(if (delete) "删除" else "重置") } },
        dismissButton = { TextButton({ delete = false; reset = false }) { Text("取消") } })
}
