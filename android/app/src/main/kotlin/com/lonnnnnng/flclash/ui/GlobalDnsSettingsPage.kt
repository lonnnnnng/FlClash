package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.lonnnnnng.flclash.service.GlobalDnsSettings
import com.lonnnnnng.flclash.service.ProfileDnsOverrides

private val dnsLabels get() = mapOf("listen" to tr("监听地址"), "ipv6" to "IPv6", "enhanced-mode" to tr("DNS 模式"),
    "fake-ip-filter" to tr("Fake-IP 过滤"), "fake-ip-filter-mode" to tr("Fake-IP 过滤模式"),
    "default-nameserver" to "Default Nameserver", "nameserver" to "Nameserver", "fallback" to "Fallback",
    "proxy-server-nameserver" to "Proxy Server Nameserver", "direct-nameserver" to "Direct Nameserver", "use-hosts" to "Use Hosts", "use-system-hosts" to "Use System Hosts",
    "respect-rules" to "Respect Rules", "prefer-h3" to "Prefer H3", "cache-algorithm" to tr("缓存算法"))
private fun dnsDefault(key: String): String = when (key) {
    "ipv6", "prefer-h3", "respect-rules" -> "false"
    "use-hosts", "use-system-hosts" -> "true"
    "cache-algorithm" -> "lru"
    "enhanced-mode" -> "redir-host"
    "fake-ip-filter-mode" -> "blacklist"
    "default-nameserver" -> "114.114.114.114\n223.5.5.5\n8.8.8.8\n1.0.0.1"
    "nameserver" -> "https://doh.pub/dns-query\ntls://223.5.5.5:853"
    "fake-ip-filter" -> "dns.msftnsci.com\nwww.msftnsci.com\nwww.msftconnecttest.com"
    else -> ""
}
internal fun JsonElement.dnsText(): String = if (isJsonArray) asJsonArray.joinToString("\n") { it.asString } else asString
private fun ProfileDnsOverrides.changed(key: String, value: JsonElement?): ProfileDnsOverrides = ProfileDnsOverrides.fromJson(toJson().apply {
    if (value == null) remove(key) else add(key, value)
})

/** 作者 long：添加按钮在非空页顶栏，状态由同一页面持有；选择器与编辑草稿不因容器切换丢失。 */
@Composable
internal fun GlobalDnsSettingsPage(settings: ProfileDnsOverrides, busy: Boolean, saving: Boolean,
    onBack: () -> Unit, onSave: (ProfileDnsOverrides, (Boolean) -> Unit) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    ProfileEditorPage(onBack, { Text("DNS", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleMedium) }, {
        GlobalDnsSettingsPanel(settings, busy, saving, adding, { adding = it }, true, onSave)
    }, backTag = "global_dns_back", contentPadding = PaddingValues(0.dp),
        navigationButtonSurface = true, navigationButtonSize = 40.dp, toolbarHeight = 72.dp,
        bodyBehindToolbar = settings.toJson().size() == 0, confirmButton = {
            if (!settings.isEmpty) IconButton({ adding = true }, enabled = !busy && settings.toJson().size() < GlobalDnsSettings.keys.size,
                modifier = Modifier.padding(end = 16.dp).size(44.dp).background(dashboardCardColor(), CircleShape).testTag("global_dns_add")) { AppGlyphIcon(AppGlyphs.addCircle) }
        })
}

/** 作者 long：未添加字段继续继承配置；添加后的空列表是明确清空，删除字段才撤销覆盖。 */
@Composable
internal fun GlobalDnsSettingsPanel(settings: ProfileDnsOverrides, busy: Boolean, saving: Boolean = false,
    requestedAdding: Boolean = false, onAddingChange: (Boolean) -> Unit = {}, topAdd: Boolean = false,
    onSave: (ProfileDnsOverrides, (Boolean) -> Unit) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var addQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(requestedAdding) { if (requestedAdding) adding = true }
    var submitting by remember { mutableStateOf(false) }
    val locked = busy || submitting
    var enumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val fields = settings.toJson()
    fun commit(key: String, value: JsonElement?, done: (Boolean) -> Unit = {}) {
        if (busy || submitting) return
        submitting = true; error = null
        onSave(settings.changed(key, value)) { success ->
            submitting = false
            if (!success) error = "保存失败，请重试"
            done(success)
        }
    }
    fun remove(key: String) = commit(key, null)
    val sections = listOf(
        tr("选项") to GlobalDnsSettings.keys.filter { it !in GlobalDnsSettings.listKeys && it != "fake-ip-filter-mode" },
        "Fake-IP" to listOf("fake-ip-filter", "fake-ip-filter-mode"),
        "Nameserver" to GlobalDnsSettings.listKeys.filter { it != "fake-ip-filter" },
    )
    Column(Modifier.fillMaxSize().then(if (fields.size() > 0) Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp) else Modifier).testTag("page_global_dns"), verticalArrangement = Arrangement.Top) {
        if (saving) Row(Modifier.fillMaxWidth().testTag("global_dns_saving"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(tr("正在保存并应用 DNS 设置…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
        if (fields.size() == 0) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("global_dns_empty"),
                contentAlignment = androidx.compose.ui.BiasAlignment(0f, -.2f)) {
                // 作者 long：沿Flutter按剩余视口定位空态，不能把固定520dp内容放入长列表；短视口仍可滚动触达按钮。
                val compact = maxHeight < 560.dp
                Column(Modifier.padding(horizontal = 32.dp).widthIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()).padding(vertical = 24.dp).testTag("global_dns_empty_content"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.testTag("global_dns_illustration")) {
                        ProfileEmptyIllustration(AppGlyphs.dns, extent = if (compact) 120.dp else 160.dp, flutterPalette = true)
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                    Text(tr("暂无覆写项"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("添加的项会覆写所有 URL 和文件配置的 DNS，未添加的项沿用配置自身的设置。自定义配置的 DNS 在其内容中设置。"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    FilledTonalButton({ addQuery = ""; adding = true }, enabled = !locked,
                        modifier = Modifier.heightIn(min = 44.dp).testTag("global_dns_add"), shape = AppSuperellipseShape(1000.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = dashboardCardColor(), contentColor = MaterialTheme.colorScheme.onSurface)) {
                        AppGlyphIcon(AppGlyphs.addCircle, Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("添加覆写项"))
                    }
                }
            }
        } else {
            sections.forEach { (section, keys) ->
                val existing = keys.filter(fields::has)
                if (existing.isNotEmpty()) {
                    Text(section, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 13.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                    ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                        existing.forEachIndexed { index, key ->
                            val boolean = key in GlobalDnsSettings.booleanKeys
                            val options = dnsOptions(key)
                            fun open() {
                                when { boolean -> commit(key, JsonPrimitive(!fields[key].asBoolean))
                                    options.isNotEmpty() -> enumKey = key
                                    else -> editing = key }
                            }
                            val menuSource = rememberAppMenuSource()
                            Box {
                              AppMenuSourceContent(menuSource) {
                                val subtitle = when {
                                    key in GlobalDnsSettings.listKeys -> fields[key].asJsonArray.size().let { if (it == 0) tr("无") else tr("{0} 项", it) }
                                    key == "listen" -> fields[key].asString.takeIf(String::isNotEmpty)
                                    else -> null
                                }
                                Row(Modifier.then(if (options.isNotEmpty()) Modifier.appMenuSource(menuSource, dashboardCardColor()) else Modifier)
                                    .fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !locked, onClick = ::open)
                                    .padding(start = 16.dp, end = 12.dp).testTag("global_dns_$key"), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton({ remove(key) }, enabled = !locked,
                                        modifier = Modifier.size(32.dp).testTag("global_dns_remove_$key")) {
                                        Icon(Icons.Outlined.RemoveCircleOutline, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                        Text(dnsLabels.getValue(key), style = MaterialTheme.typography.bodyLarge)
                                        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                                    }
                                    when {
                                        boolean -> Switch(fields[key].asBoolean, { commit(key, JsonPrimitive(it)) }, enabled = !locked,
                                            modifier = Modifier.testTag("global_dns_switch_$key"))
                                        key in GlobalDnsSettings.listKeys -> Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        options.isNotEmpty() -> {
                                            Text(fields[key].asString, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Icon(Icons.Outlined.UnfoldMore, null, Modifier.padding(start = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                              }
                                AppDropdownMenu(enumKey == key, { enumKey = null }, source = menuSource) {
                                    options.forEach { option -> AppDropdownMenuItem(text = { Text(option) },
                                        trailingIcon = if (fields[key].asString == option) ({ AppGlyphIcon(AppGlyphs.check) }) else null,
                                        onClick = { enumKey = null; commit(key, JsonPrimitive(option)) }, enabled = !locked,
                                        modifier = Modifier.testTag("global_dns_option_$option")) }
                                }
                            }
                            if (index < existing.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp, end = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        }
                    }
                }
            }
            if (fields.has("respect-rules")) Text(tr("遵守规则使 DNS 连接遵守规则，需配置 Proxy Server Nameserver。"),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("添加的项会覆写所有 URL 和文件配置的 DNS，未添加的项沿用配置自身的设置。自定义配置的 DNS 在其内容中设置。"),
                Modifier.padding(horizontal = 16.dp, vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (!topAdd) OutlinedButton({ addQuery = ""; adding = true }, enabled = !locked && fields.size() < GlobalDnsSettings.keys.size,
                modifier = Modifier.testTag("global_dns_add")) { Icon(Icons.Outlined.Add, null); Text(tr("添加覆写项")) }
        }
    }
    if (adding) {
        val scroll = rememberScrollState()
        val remaining = GlobalDnsSettings.keys.filterNot(fields::has)
        val terms = remember(addQuery) { addQuery.trim().split(Regex("\\s+")).filter(String::isNotEmpty) }
        fun matches(key: String) = terms.all { (dnsLabels.getValue(key) + " " + key).contains(it, ignoreCase = true) }
        // 作者 long：Flutter 在底部停靠可搜索的字段选择器；这里只统一选择层，不改变 DNS 校验与保存回执。
        ConnectionModalSheet(
            onDismiss = {
                adding = false
                addQuery = ""
                onAddingChange(false)
            },
            contentScrollState = scroll, isContentAtTop = { scroll.value == 0 }, avoidIme = true, restoreDetentAfterIme = true,
            dismissAllowed = { !locked }, sheetTag = "global_dns_add_sheet",
            handleHeight = 10.dp, handleWidth = 28.dp, handleVerticalOffset = 3.dp, floatingForm = true,
        ) { requestDismiss ->
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                ProfileFormHeader(tr("添加覆写项"), requestDismiss, !locked, "global_dns_add_close") {}
                if (locked) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("global_dns_adding"))
                error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_dns_add_error")) }
                val visibleSections = sections.mapNotNull { (section, keys) ->
                    section to keys.filter { it in remaining && matches(it) }
                }.filter { it.second.isNotEmpty() }
                if (visibleSections.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(tr("没有匹配的字段"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                    visibleSections.forEachIndexed { sectionIndex, (section, keys) ->
                        if (sectionIndex > 0) Spacer(Modifier.height(16.dp))
                        Text(section, Modifier.padding(start = 16.dp, top = 10.dp, bottom = 13.dp),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = AppSuperellipseShape(24.dp),
                            color = if (com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme.current)
                                MaterialTheme.colorScheme.surfaceContainerHigh else dashboardCardColor()) {
                            Column {
                                keys.forEachIndexed { index, key ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !locked) {
                                        // 作者 long：只有保存成功才移除候选；失败保留当前选择器和可重试项，不提前跳到编辑窗口。
                                        commit(key, dnsValue(key, dnsDefault(key))) { success ->
                                            if (success && remaining.size == 1) requestDismiss()
                                        }
                                    }.padding(horizontal = 16.dp).testTag("global_dns_add_$key"),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(dnsLabels.getValue(key), Modifier.weight(1f))
                                    }
                                    if (index < keys.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                                }
                            }
                        }
                    }
                }
                DockedProfileSearch(addQuery, { addQuery = it }, "global_dns_add_search", enabled = !locked,
                    modifier = Modifier.padding(top = 12.dp),
                    containerColor = if (com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme.current)
                        MaterialTheme.colorScheme.surfaceContainerHighest else dashboardCardColor())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    editing?.let { key ->
        val onCommit: (JsonElement, (Boolean) -> Unit) -> Unit = { value, done -> commit(key, value, done) }
        if (key in GlobalDnsSettings.listKeys) GlobalDnsListEditor(key, dnsLabels.getValue(key),
            fields[key]?.asJsonArray?.map { it.asString }, locked, { editing = null }, onCommit)
        else GlobalDnsFieldEditor(key, fields[key]?.dnsText() ?: dnsDefault(key), locked,
            { editing = null }, onCommit)
    }
}

private fun dnsOptions(key: String) = when (key) {
    "enhanced-mode" -> listOf("normal", "fake-ip", "redir-host")
    "fake-ip-filter-mode" -> listOf("blacklist", "whitelist", "rule")
    "cache-algorithm" -> listOf("lru", "arc")
    else -> emptyList()
}
internal fun dnsValue(key: String, text: String): JsonElement = when (key) {
    in GlobalDnsSettings.listKeys -> JsonArray().apply { text.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach(::add) }
    in GlobalDnsSettings.booleanKeys -> JsonPrimitive(text.toBooleanStrict())
    else -> JsonPrimitive(text.trim())
}
internal fun validateDnsField(key: String, value: JsonElement) {
    GlobalDnsSettings.validate(ProfileDnsOverrides.fromJson(com.google.gson.JsonObject().apply { add(key, value) }))
}

@Composable
private fun GlobalDnsFieldEditor(key: String, initial: String, busy: Boolean, onDismiss: () -> Unit,
    onSave: (JsonElement, (Boolean) -> Unit) -> Unit) {
    var text by rememberSaveable(key, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial)) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var closing by rememberSaveable(key) { mutableStateOf(false) }
    fun save() {
        if (busy || closing) return
        val value = dnsValue(key, text.text)
        runCatching { validateDnsField(key, value) }.onFailure { error = it.message }.onSuccess {
            onSave(value) { success -> if (success) closing = true else error = "保存失败，输入已保留，请重试" }
        }
    }
    FloatingInputDialog(dnsLabels.getValue(key), "global_dns_editor", busy, true, onDismiss, ::save, tr("保存"),
        error = error, errorTag = "global_dns_error", confirmTag = "global_dns_save", externallyClosing = closing, onExitComplete = onDismiss) { enabled, submit ->
        FloatingInputField(text, { if (it.text.length <= 256) text = it }, "global_dns_input", enabled, submit,
            placeholder = "127.0.0.1:1053", invalid = error != null, keyboardType = KeyboardType.Uri)
    }
}
