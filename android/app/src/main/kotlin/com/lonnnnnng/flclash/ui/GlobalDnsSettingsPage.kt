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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.lonnnnnng.flclash.service.GlobalDnsSettings
import com.lonnnnnng.flclash.service.ProfileDnsOverrides

private val dnsLabels get() = mapOf("listen" to tr("监听地址"), "ipv6" to "IPv6", "enhanced-mode" to tr("DNS 模式"),
    "fake-ip-filter" to tr("Fake-IP 过滤"), "fake-ip-filter-mode" to tr("Fake-IP 过滤模式"),
    "default-nameserver" to "Default Nameserver", "nameserver" to "Nameserver", "fallback" to "Fallback",
    "proxy-server-nameserver" to "Proxy Server Nameserver", "direct-nameserver" to "Direct Nameserver")
private fun dnsDefault(key: String): String = when (key) {
    "ipv6" -> "false"
    "enhanced-mode" -> "redir-host"
    "fake-ip-filter-mode" -> "blacklist"
    "default-nameserver" -> "114.114.114.114\n223.5.5.5\n8.8.8.8\n1.0.0.1"
    "nameserver" -> "https://doh.pub/dns-query\ntls://223.5.5.5:853"
    "fake-ip-filter" -> "dns.msftnsci.com\nwww.msftnsci.com\nwww.msftconnecttest.com"
    else -> ""
}
private fun JsonElement.dnsText(): String = if (isJsonArray) asJsonArray.joinToString("\n") { it.asString } else asString
private fun ProfileDnsOverrides.changed(key: String, value: JsonElement?): ProfileDnsOverrides = ProfileDnsOverrides.fromJson(toJson().apply {
    if (value == null) remove(key) else add(key, value)
})

/** 作者 long：DNS是工具中的独立整页，使用完整窗口而非Dock内的列表项；返回仍交给工具路由。 */
@Composable
internal fun GlobalDnsSettingsPage(settings: ProfileDnsOverrides, busy: Boolean, saving: Boolean,
    onBack: () -> Unit, onSave: (ProfileDnsOverrides, (Boolean) -> Unit) -> Unit) {
    ProfileEditorPage(onBack, { Text("DNS", Modifier.padding(start = 4.dp)) }, {
        GlobalDnsSettingsPanel(settings, busy, saving, onSave)
    }, backTag = "global_dns_back", contentPadding = PaddingValues(0.dp),
        navigationButtonSurface = true, navigationButtonSize = 40.dp, toolbarHeight = 72.dp,
        bodyBehindToolbar = settings.toJson().size() == 0)
}

/** 作者 long：未添加字段继续继承配置；添加后的空列表是明确清空，删除字段才撤销覆盖。 */
@Composable
internal fun GlobalDnsSettingsPanel(settings: ProfileDnsOverrides, busy: Boolean, saving: Boolean = false,
    onSave: (ProfileDnsOverrides, (Boolean) -> Unit) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var addQuery by rememberSaveable { mutableStateOf("") }
    var pendingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val fields = settings.toJson()
    fun remove(key: String) { onSave(settings.changed(key, null)) { if (!it) error = "移除失败，请重试" } }
    Column(Modifier.fillMaxSize().then(if (fields.size() > 0) Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp) else Modifier).testTag("page_global_dns"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    FilledTonalButton({ addQuery = ""; adding = true }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 44.dp).testTag("global_dns_add"), shape = AppSuperellipseShape(1000.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = dashboardCardColor(), contentColor = MaterialTheme.colorScheme.onSurface)) {
                        AppGlyphIcon(AppGlyphs.addCircle, Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("添加覆写项"))
                    }
                }
            }
        } else {
            val sections = listOf(
                tr("选项") to listOf("listen", "ipv6", "enhanced-mode"),
                "Fake-IP" to listOf("fake-ip-filter", "fake-ip-filter-mode"),
                "Nameserver" to GlobalDnsSettings.keys.filter { it in GlobalDnsSettings.listKeys && it != "fake-ip-filter" },
            )
            sections.forEach { (section, keys) ->
                val existing = keys.filter(fields::has)
                if (existing.isNotEmpty()) {
                    Text(section, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall)
                    existing.forEach { key ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { editing = key }.testTag("global_dns_$key")
                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(dnsLabels.getValue(key))
                                Text(fields.get(key).dnsText().ifEmpty { tr("空值覆盖") }, maxLines = 3,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (key == "ipv6") Switch(fields.get(key).asBoolean, { enabled ->
                                onSave(settings.changed(key, JsonPrimitive(enabled))) { if (!it) error = "保存失败，请重试" }
                            }, enabled = !busy)
                            IconButton({ remove(key) }, enabled = !busy, modifier = Modifier.testTag("global_dns_remove_$key")) {
                                Icon(Icons.Outlined.RemoveCircleOutline, null)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Text(tr("未添加的 DNS 字段沿用配置；自定义配置使用自己的 DNS。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ addQuery = ""; adding = true }, enabled = !busy && fields.size() < GlobalDnsSettings.keys.size,
                modifier = Modifier.testTag("global_dns_add")) { Icon(Icons.Outlined.Add, null); Text(tr("添加覆写项")) }
        }
    }
    if (adding) {
        val scroll = rememberScrollState()
        val remaining = GlobalDnsSettings.keys.filterNot(fields::has)
        val terms = remember(addQuery) { addQuery.trim().split(Regex("\\s+")).filter(String::isNotEmpty) }
        fun matches(key: String) = terms.all { (dnsLabels.getValue(key) + " " + key).contains(it, ignoreCase = true) }
        val sections = listOf(
            tr("选项") to listOf("listen", "ipv6", "enhanced-mode"),
            "Fake-IP" to listOf("fake-ip-filter", "fake-ip-filter-mode"),
            "Nameserver" to listOf("default-nameserver", "nameserver", "fallback", "proxy-server-nameserver", "direct-nameserver"),
        )
        // 作者 long：Flutter 在底部停靠可搜索的字段选择器；这里只统一选择层，不改变 DNS 校验与保存回执。
        ConnectionModalSheet(
            onDismiss = {
                adding = false
                addQuery = ""
                pendingKey?.let { editing = it; pendingKey = null }
            },
            contentScrollState = scroll, isContentAtTop = { scroll.value == 0 }, avoidIme = true, restoreDetentAfterIme = true,
            dismissAllowed = { !busy }, sheetTag = "global_dns_add_sheet",
            handleHeight = 10.dp, handleWidth = 28.dp, handleVerticalOffset = 3.dp, floatingForm = true,
        ) { requestDismiss ->
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                ProfileFormHeader(tr("添加覆写项"), requestDismiss, !busy, "global_dns_add_close") {}
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
                                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !busy) {
                                        pendingKey = key
                                        requestDismiss()
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
                DockedProfileSearch(addQuery, { addQuery = it }, "global_dns_add_search", enabled = !busy,
                    modifier = Modifier.padding(top = 12.dp),
                    containerColor = if (com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme.current)
                        MaterialTheme.colorScheme.surfaceContainerHighest else dashboardCardColor())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    editing?.let { key ->
        GlobalDnsFieldEditor(key, fields.get(key)?.dnsText() ?: dnsDefault(key), busy, saving,
            onDismiss = { editing = null }) { value, done ->
            onSave(settings.changed(key, value), done)
        }
    }
}

@Composable
private fun GlobalDnsFieldEditor(key: String, initial: String, busy: Boolean, saving: Boolean, onDismiss: () -> Unit,
    onSave: (JsonElement, (Boolean) -> Unit) -> Unit) {
    var text by rememberSaveable(key) { mutableStateOf(initial) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val options = when (key) { "enhanced-mode" -> listOf("normal", "fake-ip", "redir-host")
        "fake-ip-filter-mode" -> listOf("blacklist", "whitelist", "rule"); "ipv6" -> listOf("false", "true"); else -> emptyList() }
    fun save(value: String) {
        text = value
        val element = if (key in GlobalDnsSettings.listKeys) JsonArray().apply {
            value.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { add(it) }
        } else if (key == "ipv6") JsonPrimitive(value.toBoolean()) else JsonPrimitive(value.trim())
        val candidate = ProfileDnsOverrides.fromJson(com.google.gson.JsonObject().apply { add(key, element) })
        runCatching { GlobalDnsSettings.validate(candidate) }.onFailure { error = it.message }.onSuccess {
            onSave(element) { success -> if (success) onDismiss() else error = "保存失败，输入已保留，请重试" }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = Modifier.testTag("global_dns_editor"), title = { Text(dnsLabels.getValue(key)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.isNotEmpty()) options.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { save(option) }.padding(vertical = 8.dp)
                    .testTag("global_dns_option_$option"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(text == option, null, enabled = !busy); Text(option, Modifier.padding(start = 12.dp))
                }
            } else OutlinedTextField(text, { if (it.length <= 65536) text = it }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("global_dns_input"), minLines = if (key in GlobalDnsSettings.listKeys) 5 else 1,
                maxLines = 9, singleLine = key == "listen", supportingText = {
                    Text(if (key in GlobalDnsSettings.listKeys) tr("每行一项；留空保存为空列表，移除字段恢复继承") else tr("例如 127.0.0.1:1053，留空关闭监听"))
                })
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("global_dns_error")) }
            if (saving) Row(Modifier.fillMaxWidth().testTag("global_dns_editor_saving"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(tr("正在保存并应用…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }, confirmButton = { if (options.isEmpty()) TextButton({ save(text) }, enabled = !busy, modifier = Modifier.testTag("global_dns_save")) { Text(tr("保存")) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(tr("取消")) } })
}
