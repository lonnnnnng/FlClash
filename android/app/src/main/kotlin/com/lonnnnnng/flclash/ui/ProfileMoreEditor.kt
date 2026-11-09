package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.ProfileExtensionApplier
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileSnifferOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 作者 long：分区逐项保存并留页；子页未确认的输入只留在当前窗口，保存失败也不能丢失。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileMoreEditor(state: ProfileMoreEditorUiState, section: String, sourceContent: String,
    onDismiss: () -> Unit, onSave: (ProfileNtpOverrides, ProfileSnifferOverrides) -> Unit) {
    val profile = state.profile ?: return
    val ntp = section == "ntp"
    val root = if (ntp) state.ntp.toJson() else state.sniffer.toJson()
    var adding by rememberSaveable { mutableStateOf(false) }
    var addQuery by rememberSaveable(profile.id, section) { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var yaml by rememberSaveable { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fields = if (ntp) ntpFields else snifferFields
    val listed = fields.filter { moreFieldValue(root, it) != null }
    val remaining = fields.filter { moreFieldValue(root, it) == null }
    val targets = remember(profile.id, sourceContent) { runCatching { ProfileExtensionApplier.targets(sourceContent) }.getOrDefault(emptyList()) }
    var pendingChild by remember(profile.id, section) { mutableStateOf<String?>(null) }
    LaunchedEffect(profile.id, section, state.revision) {
        when (pendingChild) { "value" -> editing = null; "yaml" -> yaml = false; "last-add" -> { adding = false; addQuery = "" } }
        pendingChild = null
    }
    fun submit(next: JsonObject, child: String? = null) {
        if (state.isSaving) return
        try {
            validateMoreFields(next, ntp)
            error = null
            if (next == root) {
                when (child) { "value" -> editing = null; "yaml" -> yaml = false }
                return
            }
            pendingChild = child
            onSave(if (ntp) ProfileNtpOverrides.fromJson(next) else state.ntp,
                if (ntp) state.sniffer else ProfileSnifferOverrides.fromJson(next))
        } catch (failure: IllegalArgumentException) { error = failure.message ?: "字段无效，请检查后重试" }
    }
    fun change(field: MoreField, value: JsonElement?, child: String? = null) {
        val next = root.deepCopy(); setMoreField(next, field, value); submit(next, child)
    }
    ProfileEditorPage({ if (!state.isSaving) onDismiss() }, title = { Text(if (ntp) "NTP" else "Sniffer") }, backTag = "profile_more_editor_back", confirmButton = {
        if (listed.isNotEmpty()) IconButton(onClick = { adding = true }, enabled = !state.isSaving && remaining.isNotEmpty(), modifier = Modifier.testTag("profile_more_add")) { Icon(Icons.Outlined.AddCircleOutline, null) }
        IconButton(onClick = { yaml = true }, enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_yaml")) { Icon(Icons.Outlined.Code, null) }
    }, text = {
        Column(Modifier.fillMaxSize().testTag("profile_more_editor_scroll")) {
            if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("profile_more_saving"))
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_more_editor_error")) }
            if (listed.isEmpty()) {
                // 作者 long：空态按剩余视口偏上居中，沿用Flutter的-0.2对齐；固定80dp会让高屏留下整片空白。
                Box(Modifier.fillMaxSize(), contentAlignment = BiasAlignment(0f, -.2f)) {
                Column(Modifier.widthIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp).testTag("profile_more_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProfileEmptyIllustration(if (ntp) AppGlyphs.clock else AppGlyphs.eye,
                        extent = if (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() < 560.dp }) 120.dp else 160.dp)
                    Text("暂无设置项", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium)
                    Text("未添加的字段沿用原配置。添加设置项后可单独调整。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { adding = true }, enabled = !state.isSaving, modifier = Modifier.padding(top = 20.dp).testTag("profile_more_empty_add")) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("添加设置项") }
                }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false, true).forEach { protocols ->
                    val entries = listed.filter { (it.kind == MoreFieldKind.PROTOCOL) == protocols }
                    if (entries.isNotEmpty()) {
                        Text(if (protocols) "嗅探协议" else "选项", Modifier.padding(start = 16.dp, top = 12.dp),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
                            Column {
                                entries.forEachIndexed { index, field ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    MoreSettingRow(field, checkNotNull(moreFieldValue(root, field)), !state.isSaving,
                                        onRemove = { change(field, null) }, onEdit = { editing = field.key }, onToggle = { change(field, Gson().toJsonTree(it)) })
                                }
                            }
                        }
                    }
                }
                val dialer = if (ntp) root.get("dialer-proxy")?.asString.orEmpty() else ""
                if (dialer.isNotBlank() && targets.isNotEmpty() && dialer !in targets) Text("出站代理已不在配置中，请检查节点或策略组名称", color = MaterialTheme.colorScheme.error)
                Text("修改会自动保存。未添加的字段沿用原配置；移除或清空设置项可恢复继承。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { clear = true }, enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_reset")) { Text("清除本分区覆盖") }
                Spacer(Modifier.height(16.dp))
                }
            }
        }
    })
    if (adding) ModalBottomSheet(onDismissRequest = { if (!state.isSaving) { adding = false; addQuery = "" } }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // 作者 long：候选身份固定在本次面板，不因保存回执立即移除组合；先完成条目收起，后续条目才能连续上移。
        var shownChoices by remember(profile.id, section) { mutableStateOf(remaining) }
        val remainingKeys = remaining.map { it.key }.toSet()
        fun matchesQuery(field: MoreField) = addQuery.isBlank() || field.label.contains(addQuery.trim(), true) || field.key.contains(addQuery.trim(), true)
        LaunchedEffect(remainingKeys, addQuery) {
            // 作者 long：搜索移出条目会取消其动画协程，已保存的隐藏候选须在父层释放，避免恢复查询时出现空分组。
            shownChoices = fields.filter { candidate ->
                candidate.key in remainingKeys || matchesQuery(candidate) && shownChoices.any { it.key == candidate.key }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("添加设置项", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { adding = false; addQuery = "" }, enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_add_done")) { Text("完成") }
            }
            if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (remaining.size >= 10 || addQuery.isNotEmpty()) ProfileSearchField(addQuery, { addQuery = it }, "profile_more_add_search", enabled = !state.isSaving)
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).testTag("profile_more_available_fields")) {
                if (remaining.isEmpty()) Text("所有设置项均已添加", Modifier.padding(vertical = 24.dp))
                val matching = shownChoices.filter(::matchesQuery)
                if (matching.isEmpty() && remaining.isNotEmpty()) Text("无搜索结果", Modifier.padding(24.dp).testTag("profile_more_add_empty"))
                listOf(false, true).forEach { protocols ->
                    key(protocols) {
                        val entries = matching.filter { (it.kind == MoreFieldKind.PROTOCOL) == protocols }
                        if (entries.isNotEmpty()) {
                            // 作者 long：分组最后一条开始退场时标题同步收起，不能等条目结束后突然跳掉整块标题高度。
                            SelectionEntryTransition(entries.any { it.key in remainingKeys }, Modifier.testTag("more_add_header_$protocols")) {
                                Text(if (protocols) "嗅探协议" else "选项", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                            }
                            Surface(shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
                                Column {
                                    entries.forEachIndexed { index, field ->
                                        key(field.key) {
                                            SelectionEntryTransition(field.key in remainingKeys, Modifier.testTag("more_add_transition_${field.key}"),
                                                onHidden = { shownChoices = shownChoices.filterNot { it.key == field.key } }) {
                                                Column {
                                                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                                                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.isSaving && field.key in remainingKeys) {
                                                        // 作者 long：只在最后一个候选实际提交成功并更新revision后退出，失败仍留层重试；筛选结果为空不代表全部已添加。
                                                        change(field, field.defaultValue(), if (remaining.size == 1) "last-add" else null)
                                                    }.heightIn(min = 64.dp).padding(horizontal = 16.dp).testTag("more_add_${field.key}"), verticalAlignment = Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                                            Text(field.label)
                                                            if (field.description.isNotEmpty()) Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    fields.firstOrNull { it.key == editing }?.let { field -> moreFieldValue(root, field)?.let { value ->
        if (field.kind == MoreFieldKind.PROTOCOL) {
            MoreProtocolEditor(field, value.asJsonObject, root, state.isSaving, state.error, state.revision,
                { editing = null }) { change(field, it) }
        } else {
            MoreValueEditor(field, value, root, ntp, state.isSaving, state.error, { editing = null }) { change(field, it, "value") }
        }
    } }
    if (yaml) MoreYamlEditor(if (ntp) "NTP" else "Sniffer", root, ntp, state.isSaving, state.error, { yaml = false }) { submit(it, "yaml") }
    if (clear) AlertDialog(onDismissRequest = { if (!state.isSaving) clear = false }, title = { Text("清除本分区覆盖？") }, text = { Text("清除后所有字段将恢复继承原配置。") },
        confirmButton = { TextButton(enabled = !state.isSaving, onClick = { submit(JsonObject()); clear = false }) { Text("清除") } },
        dismissButton = { TextButton(enabled = !state.isSaving, onClick = { clear = false }) { Text("取消") } })
}

/** 作者 long：协议页没有总确认；端口子页确认和独立目标选择各自持久化，失败只保留对应子页。 */
@Composable
private fun MoreProtocolEditor(field: MoreField, value: JsonObject, root: JsonObject, saving: Boolean,
    saveError: String?, revision: Long, onDismiss: () -> Unit, onChange: (JsonObject) -> Unit) {
    var ports by rememberSaveable(field.key) { mutableStateOf(false) }
    var destination by rememberSaveable(field.key) { mutableStateOf(false) }
    var pending by remember(field.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(revision) {
        if (pending == "ports") ports = false
        if (pending == "destination") destination = false
        pending = null
    }
    val currentDestination = value.get("override-destination")?.asBoolean
    val portCount = value.get("ports")?.asJsonArray?.size() ?: 0
    val destinationSource = rememberAppMenuSource()
    ProfileEditorPage({ if (!saving) onDismiss() }, title = { Text(field.label) }, backTag = "more_protocol_back", text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Surface(shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
                Column {
                    MoreProtocolRow("端口", if (portCount == 0) "默认端口" else "$portCount 个端口或范围",
                        Modifier.testTag("more_protocol_ports"), enabled = !saving, onClick = { ports = true })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Box {
                        AppMenuSourceContent(destinationSource) {
                            Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { destination = true }
                                .heightIn(min = 64.dp).padding(16.dp).testTag("more_protocol_destination"), verticalAlignment = Alignment.CenterVertically) {
                                Text("覆盖目标地址", Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                // 作者 long：源快照只接管行尾picker；菜单打开后标签仍是页面的稳定定位信息，不能把整行一并隐藏。
                                Row(Modifier.appMenuSource(destinationSource, dashboardCardColor()).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(when (currentDestination) { true -> "启用"; false -> "禁用"; null -> "默认，跟随 Sniffer" },
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppGlyphIcon(AppGlyphs.chevronDown, Modifier.padding(start = 8.dp).size(20.dp))
                                }
                            }
                        }
                        AppDropdownMenu(destination, { if (!saving) destination = false }, source = destinationSource, dismissEnabled = !saving) {
                            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp).testTag("more_protocol_error")) }
                            listOf(null to "默认，跟随 Sniffer", true to "启用", false to "禁用").forEach { (selected, label) ->
                                AppDropdownMenuItem(text = { Text(label) }, enabled = !saving, dismissOnClick = false,
                                    modifier = Modifier.testTag("more_protocol_destination_${selected ?: "default"}"),
                                    trailingIcon = if (selected == currentDestination) ({ AppGlyphIcon(AppGlyphs.check) }) else null,
                                    onClick = {
                                        // 作者 long：菜单选择先等持久化回执，失败继续显示原选择并留层；不能在回源后才发现保存失败却丢失重试入口。
                                        if (selected == currentDestination) destination = false else {
                                            val next = value.deepCopy()
                                            if (selected == null) next.remove("override-destination") else next.addProperty("override-destination", selected)
                                            pending = "destination"; onChange(next)
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    })
    if (ports) MoreValueEditor(field, value, root, false, saving, saveError, { ports = false }) { next ->
        if (next == value) ports = false else { pending = "ports"; onChange(checkNotNull(next).asJsonObject) }
    }
}

@Composable
private fun MoreProtocolRow(title: String, detail: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 72.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null)
    }
}

@Composable
private fun MoreSettingRow(field: MoreField, value: JsonElement, enabled: Boolean, onRemove: () -> Unit, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val toggle = field.kind == MoreFieldKind.TOGGLE
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("more_field_${field.key}"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("more_remove_${field.key}")) { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) }
        Column(Modifier.weight(1f).clickable(enabled) { if (toggle) onToggle(!value.asBoolean) else onEdit() }.padding(vertical = 10.dp).testTag("more_edit_${field.key}")) {
            Text(field.label)
            val detail = when {
                toggle -> ""
                field.kind == MoreFieldKind.PROTOCOL -> value.asJsonObject.get("ports")?.asJsonArray?.let { if (it.size() == 0) "默认端口" else "${it.size()} 个端口或范围" } ?: "默认端口"
                value.isJsonArray -> "${value.asJsonArray.size()} 项" + if (value.asJsonArray.size() == 0) " · 空列表" else ""
                value.asString.isEmpty() -> "空值"
                else -> value.asString
            }
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (toggle) Switch(value.asBoolean, onToggle, enabled = enabled, modifier = Modifier.padding(end = 16.dp).testTag("more_${field.key}_value"))
        else IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.testTag("more_open_${field.key}")) { Icon(Icons.Outlined.ChevronRight, null) }
    }
}

@Composable
private fun MoreValueEditor(field: MoreField, initial: JsonElement, root: JsonObject, ntp: Boolean, saving: Boolean, saveError: String?, onDismiss: () -> Unit, onConfirm: (JsonElement?) -> Unit) {
    val protocol = field.kind == MoreFieldKind.PROTOCOL
    val list = field.kind in setOf(MoreFieldKind.LIST, MoreFieldKind.ADDRESSES)
    val original = if (protocol) initial.asJsonObject.get("ports")?.asJsonArray?.joinToString("\n") { it.asString }.orEmpty() else if (list) initial.asJsonArray.joinToString("\n") { it.asString } else initial.asString
    var text by rememberSaveable(field.key) { mutableStateOf(original) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    fun exit() { if (!saving) { if (text != original) discard = true else onDismiss() } }
    ProfileEditorPage(::exit, title = { Text(field.label) }, backTag = "more_value_back", confirmButton = {
        // 作者 long：字段页有自己的Window；确认前结束本页输入，校验失败时错误也能完整露出，不能操作父页IME控制器。
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        TextButton(enabled = !saving, modifier = Modifier.testTag("more_value_confirm"), onClick = {
            focus.clearFocus(force = true); keyboard?.hide()
            runCatching {
                val value = if (protocol) initial.asJsonObject.deepCopy().apply {
                    add("ports", Gson().toJsonTree(splitMoreList(text)))
                } else if (list) splitMoreList(text).takeIf { it.isNotEmpty() }?.let { Gson().toJsonTree(it) }
                else text.trim().takeIf(String::isNotEmpty)?.let { Gson().toJsonTree(it) }
                val next = root.deepCopy(); setMoreField(next, field, value); validateMoreFields(next, ntp)
                onConfirm(moreFieldValue(next, field))
            }.onFailure { error = it.message ?: "字段无效，请检查后重试" }
        }) { Text("确认") }
    }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("more_value_error")) }
            if (field.description.isNotEmpty()) Text(field.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { text = it; error = null }, label = { Text(if (protocol) "端口或范围（每行一项）" else field.label) },
                enabled = !saving, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(if (protocol) "sniff_${field.protocol}_ports" else "more_${field.key}_value"),
                singleLine = !list && !protocol, minLines = if (list || protocol) 6 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = if (field.kind in setOf(MoreFieldKind.PORT, MoreFieldKind.NUMBER)) KeyboardType.Number else KeyboardType.Text),
                supportingText = if (protocol) ({ Text("留空使用协议默认端口，例如 80 或 8000-9000") })
                else if (field.kind == MoreFieldKind.ADDRESSES) ({ Text("每行一个 IP、CIDR、geoip: 或 rule-set: 匹配项；留空恢复继承") })
                else if (list) ({ Text("每行一项；留空恢复继承原配置") }) else null)
        }
    })
    if (discard) MoreDiscardDialog("放弃未保存的修改？", { discard = false }, onDismiss)
}

@Composable
private fun MoreYamlEditor(title: String, initial: JsonObject, ntp: Boolean, saving: Boolean, saveError: String?, onDismiss: () -> Unit, onConfirm: (JsonObject) -> Unit) {
    val original = remember { moreFieldsYaml(initial) }
    var content by rememberSaveable { mutableStateOf(original) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun submit(fromBack: Boolean) { if (checking || saving) return; scope.launch {
            checking = true
            try {
                val next = withContext(Dispatchers.Default) {
                    applyMoreFieldsYaml(initial, content, ntp)
                }
                onConfirm(next)
            } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: IllegalArgumentException) { error = failure.message ?: "YAML 字段无效"; if (fromBack) discard = true }
            finally { checking = false }
        } }
    fun exit() { if (!checking && !saving) { if (content == original) onDismiss() else submit(true) } }
    ProfileEditorPage(::exit, title = { Text("$title · YAML") }, backTag = "more_yaml_back", confirmButton = {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        TextButton(enabled = !checking && !saving, modifier = Modifier.testTag("more_yaml_confirm"), onClick = {
            focus.clearFocus(force = true); keyboard?.hide(); submit(false)
        }) {
            if (checking || saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("确认")
        }
    }, text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("只填写本分区内部字段，不包含 ${if (ntp) "ntp" else "sniffer"}: 外层。删除字段或留空文本、列表及协议映射可恢复继承；false会保留。有效修改在返回时自动保存。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("more_yaml_error")) }
            OutlinedTextField(content, { content = it; error = null }, enabled = !checking && !saving, modifier = Modifier.fillMaxWidth().weight(1f).testTag("more_yaml_content"), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), label = { Text("YAML") })
        }
    })
    if (discard) MoreDiscardDialog("放弃 YAML 修改？", { discard = false }, onDismiss)
}

@Composable
private fun MoreDiscardDialog(title: String, onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, confirmButton = { TextButton(onClick = onDiscard) { Text("放弃") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("继续编辑") } })
}
