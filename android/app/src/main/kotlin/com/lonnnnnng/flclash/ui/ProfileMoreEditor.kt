package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.unit.dp
import com.google.gson.JsonElement
import com.google.gson.Gson
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
        // 作者 long：清空字段成功会先移除子弹框，父页必须回收编辑标记，避免重新添加同字段时自动打开旧入口。
        if (editing != null && fields.firstOrNull { it.key == editing }?.let { moreFieldValue(root, it) } == null) editing = null
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
    ProfileEditorPage({ if (!state.isSaving) onDismiss() }, title = { Text(if (ntp) "NTP" else "域名嗅探") }, backTag = "profile_more_editor_back", navigationButtonSurface = true, confirmButton = {
        Surface(Modifier.padding(end = 12.dp), shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
            Row {
                if (listed.isNotEmpty()) IconButton(onClick = { adding = true }, enabled = !state.isSaving && remaining.isNotEmpty(), modifier = Modifier.size(44.dp).testTag("profile_more_add")) { AppGlyphIcon(AppGlyphs.addCircle) }
                IconButton(onClick = { yaml = true }, enabled = !state.isSaving, modifier = Modifier.size(44.dp).testTag("profile_more_yaml")) { AppGlyphIcon(AppGlyphs.compose) }
            }
        }
    }, text = {
        Column(Modifier.fillMaxSize().testTag("profile_more_editor_scroll")) {
            if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("profile_more_saving"))
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_more_editor_error")) }
            AnimatedContent(
                targetState = root,
                contentKey = { snapshot -> fields.none { moreFieldValue(snapshot, it) != null } },
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("profile_more_content_transition"),
                transitionSpec = {
                    (fadeIn(tween(300, easing = CubicBezierEasing(.05f, .7f, .1f, 1f))) +
                        scaleIn(tween(300, easing = CubicBezierEasing(.05f, .7f, .1f, 1f)), initialScale = .92f)) togetherWith
                        (fadeOut(tween(150, easing = FastOutLinearInEasing)) +
                            scaleOut(tween(150, easing = FastOutLinearInEasing), targetScale = .92f))
                },
                label = "profileMoreEmptyContent",
            ) { snapshot ->
                // 作者 long：退场期间绘制旧字段和值，只允许当前页面处理操作，以免重复提交。
                val visibleFields = fields.filter { moreFieldValue(snapshot, it) != null }
                val currentContent = visibleFields.isEmpty() == listed.isEmpty()
                if (visibleFields.isEmpty()) {
                    // 作者 long：Flutter空态在窄屏收紧标题间距，并让添加按钮与说明文字保持独立呼吸距离。
                    val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() < 560.dp }
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(Modifier.offset(y = -(maxHeight * .14f)).padding(horizontal = 16.dp, vertical = 24.dp).widthIn(max = 320.dp).verticalScroll(rememberScrollState()).testTag("profile_more_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                            ProfileEmptyIllustration(if (ntp) AppGlyphs.clock else AppGlyphs.eye,
                                extent = if (compact) 120.dp else 160.dp, flutterPalette = true)
                            Text("暂无设置项", Modifier.padding(top = if (compact) 12.dp else 16.dp), style = MaterialTheme.typography.titleMedium)
                            Text("仅作用于当前配置，未添加的项使用默认值。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = { adding = true }, enabled = !state.isSaving && currentContent,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = dashboardCardColor(), contentColor = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.padding(top = 24.dp).testTag("profile_more_empty_add")) {
                                AppGlyphIcon(AppGlyphs.addCircle); Spacer(Modifier.width(8.dp)); Text("添加设置项")
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(false, true).forEach { protocols ->
                            val entries = visibleFields.filter { (it.kind == MoreFieldKind.PROTOCOL) == protocols }
                            if (entries.isNotEmpty()) {
                                Text(if (protocols) "协议" else "选项", Modifier.padding(start = 16.dp, top = 12.dp),
                                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
                                    Column {
                                        entries.forEachIndexed { index, field ->
                                            if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                            MoreSettingRow(field, checkNotNull(moreFieldValue(snapshot, field)), !state.isSaving && currentContent,
                                                onRemove = { change(field, null) }, onEdit = { editing = field.key }, onToggle = { change(field, Gson().toJsonTree(it)) })
                                        }
                                    }
                                }
                                val description = entries.map { it.description }.filter(String::isNotEmpty).joinToString("\n")
                                if (description.isNotEmpty()) Text(description, Modifier.padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val dialer = if (ntp) snapshot.get("dialer-proxy")?.asString.orEmpty() else ""
                        if (dialer.isNotBlank() && targets.isNotEmpty() && dialer !in targets) Text("出站代理已不在配置中，请检查节点或策略组名称", color = MaterialTheme.colorScheme.error)
                        Text("仅作用于当前配置，未添加的项使用默认值。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { clear = true }, enabled = !state.isSaving && currentContent, modifier = Modifier.testTag("profile_more_reset")) { Text("清除本分区覆盖") }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    })
    if (adding) {
        val addScrollState = rememberScrollState()
        // 作者 long：上游添加面板可在半屏与展开态间拖动，键盘出现后也要把候选区抬到可视区域。
        ConnectionModalSheet(
            onDismiss = { if (!state.isSaving) { adding = false; addQuery = "" } },
            contentScrollState = addScrollState,
            isContentAtTop = { addScrollState.value == 0 },
            avoidIme = true,
            restoreDetentAfterIme = true,
            dismissAllowed = { !state.isSaving },
            sheetTag = "profile_more_add_sheet",
            handleHeight = 10.dp,
            handleWidth = 28.dp,
            handleVerticalOffset = 3.dp,
            floatingForm = true,
        ) { requestDismiss ->
            // 作者 long：候选身份固定在本次面板，不因保存回执立即移除组合；先完成条目收起，后续条目才能连续上移。
            var shownChoices by remember(profile.id, section) { mutableStateOf(remaining) }
            val offeredSearch = remember(profile.id, section) { remaining.size >= 10 }
            val remainingKeys = remaining.map { it.key }.toSet()
            val terms = remember(addQuery) { addQuery.trim().split(Regex("\\s+")).filter(String::isNotEmpty) }
            fun matchesQuery(field: MoreField) = terms.all { (field.label + " " + field.description + " " + field.key).contains(it, true) }
            LaunchedEffect(remainingKeys, addQuery) {
                // 作者 long：搜索移出条目会取消其动画协程，已保存的隐藏候选须在父层释放，避免恢复查询时出现空分组。
                shownChoices = fields.filter { candidate ->
                    candidate.key in remainingKeys || matchesQuery(candidate) && shownChoices.any { it.key == candidate.key }
                }
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    // 作者 long：上游添加页使用居中标题和左侧关闭；关闭只结束选项面板，不回滚已逐项保存的字段。
                    Surface(Modifier.align(Alignment.CenterStart).size(44.dp), shape = AppSuperellipseShape(22.dp),
                        color = if (LocalKClashDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceBright) {
                        IconButton(onClick = requestDismiss, enabled = !state.isSaving, modifier = Modifier.testTag("profile_more_add_done")) {
                            AppGlyphIcon(AppGlyphs.close, Modifier.size(22.dp))
                        }
                    }
                    Text("添加设置项", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
                }
                if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val matching = shownChoices.filter(::matchesQuery)
                if (matching.isEmpty() && remaining.isNotEmpty()) {
                    MoreSearchEmptyState(Modifier.weight(1f).fillMaxWidth().testTag("profile_more_available_fields"), addScrollState)
                } else Column(Modifier.weight(1f).verticalScroll(addScrollState).testTag("profile_more_available_fields")) {
                    if (remaining.isEmpty()) Text("所有设置项均已添加", Modifier.padding(vertical = 24.dp))
                    listOf(false, true).forEach { protocols ->
                        key(protocols) {
                            val entries = matching.filter { (it.kind == MoreFieldKind.PROTOCOL) == protocols }
                            if (entries.isNotEmpty()) {
                                // 作者 long：分组最后一条开始退场时标题同步收起，不能等条目结束后突然跳掉整块标题高度。
                                SelectionEntryTransition(entries.any { it.key in remainingKeys }, Modifier.testTag("more_add_header_$protocols")) {
                                    // 作者 long：紧凑面板的标题和分组共享顶部留白，不能再次叠加整页分组的间距。
                                    Text(if (protocols) "协议" else "选项", Modifier.padding(start = 16.dp,
                                        top = 10.dp, bottom = 13.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = AppSuperellipseShape(24.dp), color = if (LocalKClashDarkTheme.current)
                                    MaterialTheme.colorScheme.surfaceContainerHigh else dashboardCardColor()) {
                                    Column {
                                        entries.forEachIndexed { index, field ->
                                            key(field.key) {
                                                SelectionEntryTransition(field.key in remainingKeys, Modifier.testTag("more_add_transition_${field.key}"),
                                                    onHidden = { shownChoices = shownChoices.filterNot { it.key == field.key } }) {
                                                    Box {
                                                        Column {
                                                            Row(Modifier.fillMaxWidth().clickable(enabled = !state.isSaving && field.key in remainingKeys) {
                                                                // 作者 long：只在最后一个候选实际提交成功并更新revision后退出，失败仍留层重试；筛选结果为空不代表全部已添加。
                                                                change(field, field.defaultValue(), if (remaining.size == 1) "last-add" else null)
                                                            }.heightIn(min = 56.dp).padding(horizontal = 16.dp).testTag("more_add_${field.key}"), verticalAlignment = Alignment.CenterVertically) {
                                                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                                                    Text(field.label)
                                                                    if (field.description.isNotEmpty()) Text(field.description,
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                }
                                                            }
                                                        }
                                                        // 作者 long：Flutter分隔线覆盖绘制，不额外撑高候选行；长列表不能逐行累积间距。
                                                        if (index > 0) HorizontalDivider(Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp),
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
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
                if (offeredSearch || addQuery.isNotEmpty()) DockedProfileSearch(addQuery, { addQuery = it },
                    "profile_more_add_search", enabled = !state.isSaving, modifier = Modifier.padding(top = 12.dp),
                    containerColor = if (LocalKClashDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHighest else dashboardCardColor())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    fields.firstOrNull { it.key == editing }?.let { field -> moreFieldValue(root, field)?.let { value ->
        if (field.kind == MoreFieldKind.PROTOCOL) {
            MoreProtocolEditor(field, value.asJsonObject, root, state.isSaving, state.error, state.revision,
                { editing = null }) { change(field, it) }
        } else if (field.kind in setOf(MoreFieldKind.LIST, MoreFieldKind.ADDRESSES)) {
            MoreListEditor(field, value, root, state.isSaving, state.error, { editing = null }) { change(field, it, "value") }
        } else {
            MoreScalarDialog(field, value, root, ntp, state.isSaving, state.error, state.revision, { editing = null }) { change(field, it) }
        }
    } }
    if (yaml) MoreYamlEditor(if (ntp) "NTP" else "域名嗅探", root, ntp, state.isSaving, state.error, state.revision, { yaml = false }) { submit(it, "yaml") }
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
    var pending by rememberSaveable(field.key) { mutableStateOf<String?>(null) }
    var pendingRevision by rememberSaveable(field.key) { mutableLongStateOf(revision) }
    LaunchedEffect(revision) {
        // 作者 long：保存中重建也要等新回执；仅恢复页面不能把尚未完成或失败的菜单误当成提交成功。
        if (revision > pendingRevision) {
            if (pending == "ports") ports = false
            if (pending == "destination") destination = false
            pending = null
        }
    }
    val currentDestination = value.get("override-destination")?.asBoolean
    val portCount = value.get("ports")?.asJsonArray?.size() ?: 0
    val destinationSource = rememberAppMenuSource()
    ProfileEditorPage({ if (!saving) onDismiss() }, title = { Text(field.label) }, backTag = "more_protocol_back", navigationButtonSurface = true, text = {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Surface(shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
                Column {
                    Box {
                        MoreProtocolRow("端口", if (portCount == 0) "默认" else "$portCount 项",
                            Modifier.testTag("more_protocol_ports"), enabled = !saving, onClick = { ports = true })
                        // 作者 long：分隔线覆盖行底，不额外增加整页高度；右端沿Flutter列表装饰留14dp。
                        HorizontalDivider(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    }
                    Box {
                        AppMenuSourceContent(destinationSource) {
                            Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { destination = true }
                                .heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).testTag("more_protocol_destination"), verticalAlignment = Alignment.CenterVertically) {
                                Text("覆盖目标地址", Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                // 作者 long：源快照只接管行尾picker；菜单打开后标签仍是页面的稳定定位信息，不能把整行一并隐藏。
                                Row(Modifier.appMenuSource(destinationSource, dashboardCardColor()).height(40.dp)
                                    .padding(horizontal = 8.dp).testTag("more_protocol_destination_picker"), verticalAlignment = Alignment.CenterVertically) {
                                Text(when (currentDestination) { true -> "已开启"; false -> "已关闭"; null -> "默认" },
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AppGlyphIcon(AppGlyphs.picker, Modifier.padding(start = 4.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        AppDropdownMenu(destination, { if (!saving) destination = false }, source = destinationSource, dismissEnabled = !saving) {
                            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp).testTag("more_protocol_error")) }
                            listOf(null to "默认", true to "已开启", false to "已关闭").forEach { (selected, label) ->
                                AppDropdownMenuItem(text = { Text(label) }, enabled = !saving, dismissOnClick = false, compact = true,
                                    modifier = Modifier.testTag("more_protocol_destination_${selected ?: "default"}"),
                                    trailingIcon = if (selected == currentDestination) ({ AppGlyphIcon(AppGlyphs.check) }) else null,
                                    onClick = {
                                        // 作者 long：菜单选择先等持久化回执，失败继续显示原选择并留层；不能在回源后才发现保存失败却丢失重试入口。
                                        if (selected == currentDestination) destination = false else {
                                            val next = value.deepCopy()
                                            if (selected == null) next.remove("override-destination") else next.addProperty("override-destination", selected)
                                            pendingRevision = revision; pending = "destination"; onChange(next)
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    })
    if (ports) MoreListEditor(field, value, root, saving, saveError, { ports = false }) { next ->
        if (next == value) ports = false else { pendingRevision = revision; pending = "ports"; onChange(checkNotNull(next).asJsonObject) }
    }
}

@Composable
private fun MoreProtocolRow(title: String, detail: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppGlyphIcon(AppGlyphs.chevronForward)
    }
}

@Composable
private fun MoreSettingRow(field: MoreField, value: JsonElement, enabled: Boolean, onRemove: () -> Unit, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val toggle = field.kind == MoreFieldKind.TOGGLE
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("more_field_${field.key}"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("more_remove_${field.key}")) { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(Modifier.weight(1f).clickable(enabled) { if (toggle) onToggle(!value.asBoolean) else onEdit() }.padding(vertical = 10.dp).testTag("more_edit_${field.key}")) {
            Text(field.label)
            val detail = when {
                toggle -> ""
                field.kind == MoreFieldKind.PROTOCOL -> value.asJsonObject.get("ports")?.asJsonArray?.let { if (it.size() == 0) "默认" else "${it.size()} 项" } ?: "默认"
                value.isJsonArray -> "${value.asJsonArray.size()} 项" + if (value.asJsonArray.size() == 0) " · 空列表" else ""
                value.asString.isEmpty() -> "空值"
                else -> value.asString
            }
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (toggle) Switch(value.asBoolean, onToggle, enabled = enabled, modifier = Modifier.padding(end = 16.dp).testTag("more_${field.key}_value"))
        else IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.testTag("more_open_${field.key}")) { Icon(Icons.Outlined.ChevronRight, null) }
    }
}

@Composable
private fun MoreYamlEditor(title: String, initial: JsonObject, ntp: Boolean, saving: Boolean, saveError: String?, revision: Long,
    onDismiss: () -> Unit, onConfirm: (JsonObject) -> Unit) {
    val original by rememberSaveable { mutableStateOf(moreFieldsYaml(initial)) }
    var content by rememberSaveable { mutableStateOf(original) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var awaitingReceipt by rememberSaveable { mutableStateOf(false) }
    var submittedRevision by rememberSaveable { mutableLongStateOf(revision) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(revision, saving, saveError, awaitingReceipt) {
        // 作者 long：保存中重建不能丢失关闭意图；只接受本次提交之后的版本回执，失败仍留正文重试。
        if (awaitingReceipt && revision > submittedRevision) { awaitingReceipt = false; onDismiss() }
        else if (awaitingReceipt && !saving && saveError != null) awaitingReceipt = false
    }
    fun exit() {
        if (checking || saving) return
        if (content == original) { onDismiss(); return }
        val draft = content
        checking = true
        scope.launch {
            try {
                val next = withContext(Dispatchers.Default) { applyMoreFieldsYaml(initial, draft, ntp) }
                if (next == initial) onDismiss() else {
                    submittedRevision = revision; awaitingReceipt = true; error = null; onConfirm(next)
                }
            } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: IllegalArgumentException) { error = failure.message ?: "YAML 字段无效"; discard = true; awaitingReceipt = false }
            finally { checking = false }
        }
    }
    // 作者 long：未受理的保存可能不产生新状态；回执标记只控制关闭，禁用仅跟随实际校验/保存，编辑会作废旧关闭意图。
    MoreCodeEditor(title, content, "more_yaml_content", { content = it; error = null; awaitingReceipt = false }, ::exit,
        enabled = !checking && !saving, highlightYaml = true, backTag = "more_yaml_back", header = {
            if (checking || saving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("more_yaml_saving"))
            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("more_yaml_error")) }
        })
    if (discard) MoreDiscardDialog("放弃 YAML 修改？", { discard = false }, onDismiss)
}

@Composable
private fun MoreDiscardDialog(title: String, onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, confirmButton = { TextButton(onClick = onDiscard) { Text("放弃") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("继续编辑") } })
}

/** 作者 long：无结果占据候选视口，不挤走下沿搜索；键盘缩小高度时仍可滚动查看插图和说明。 */
@Composable
private fun MoreSearchEmptyState(modifier: Modifier, scroll: androidx.compose.foundation.ScrollState) {
    val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() < 560.dp }
    BoxWithConstraints(modifier) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min = maxHeight).padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = BiasAlignment(0f, -.2f)) {
            Column(Modifier.widthIn(max = 320.dp).testTag("profile_more_add_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileEmptyIllustration(AppGlyphs.search, extent = if (compact) 120.dp else 160.dp, flutterPalette = true)
                Text("没有匹配的结果", Modifier.padding(top = if (compact) 12.dp else 16.dp),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}
