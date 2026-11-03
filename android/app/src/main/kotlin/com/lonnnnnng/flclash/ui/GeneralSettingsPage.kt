package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.service.GeneralSettings

/** 作者 long：常规设置逐项保存并接真实系统/核心行为；输入失败保持草稿，不把未落盘值显示成已生效。 */
@Composable
internal fun GeneralSettingsPanel(onOpenOnDemand: () -> Unit, settings: GeneralSettings = GeneralSettings(), busy: Boolean = false,
    allowLan: Boolean = false, lanBusy: Boolean = false, onAllowLan: (Boolean) -> Unit = {}, onShowLogs: () -> Unit = {},
    onSave: (GeneralSettings, (Boolean) -> Unit) -> Unit = { _, done -> done(false) }) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var ua by rememberSaveable { mutableStateOf(false) }
    var hosts by rememberSaveable { mutableStateOf(false) }
    fun save(value: GeneralSettings) { onSave(value) { if (!it) error = "设置未能应用，请检查当前状态后重试" else error = null } }
    Column(Modifier.fillMaxWidth().testTag("page_general"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("general_error")) }
        GeneralSection("启动和后台") {
            GeneralToggle("自动运行", settings.autoRun, busy, "general_auto_run") { save(settings.copy(autoRun = it)) }
            GeneralOpenRow("按需连接", "电池优化与系统授权", "general_item_on_demand", busy, onOpenOnDemand)
            GeneralToggle("退出时最小化", settings.minimizeOnExit, busy, "general_minimize") { save(settings.copy(minimizeOnExit = it)) }
            GeneralToggle("从最近任务中隐藏", settings.hidden, busy, "general_hidden") { save(settings.copy(hidden = it)) }
            GeneralToggle("显示通知停止按钮", settings.showNotificationStopAction, busy, "general_notification_stop") { save(settings.copy(showNotificationStopAction = it)) }
        }
        Text("自动运行在打开应用时启动服务。最小化保留后台连接，关闭最小化后退出会停止服务。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GeneralSection("请求与更新") {
            GeneralOpenRow("User-Agent", settings.userAgent ?: "默认", "general_user_agent", busy) { ua = true }
            GeneralToggle("校验证书", settings.checkCertificate, busy, "general_check_certificate") { save(settings.copy(checkCertificate = it)) }
        }
        GeneralSection("入站") {
            GeneralOpenRow("端口", settings.mixedPort.toString(), "general_ports", busy) { editing = "ports" }
            GeneralToggle("允许局域网访问", allowLan, busy || lanBusy, "general_allow_lan", onAllowLan)
            GeneralToggle("外部控制器", settings.externalController, busy, "general_controller") { save(settings.copy(externalController = it)) }
            if (settings.externalController) GeneralOpenRow("控制器密钥", "••••••••", "general_secret", busy) { editing = "secret" }
            GeneralToggle("入站认证", settings.authentication, busy, "general_authentication") { save(settings.copy(authentication = it)) }
            if (settings.authentication) {
                GeneralOpenRow("账号", settings.username, "general_username", busy) { editing = "username" }
                GeneralOpenRow("密码", "••••••••", "general_password", busy) { editing = "password" }
            }
        }
        Text("外部控制器监听 127.0.0.1:9090。启用入站认证后，系统 HTTP 代理不会自动下发。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GeneralSection("连接") {
            GeneralOpenRow("测速超时", "${settings.delayTestTimeout} ms", "general_timeout", busy) { editing = "timeout" }
            GeneralToggle("查找进程", settings.findProcess, busy, "general_find_process") { save(settings.copy(findProcess = it)) }
            GeneralToggle("切换时关闭旧连接", settings.closeConnections, busy, "general_close_connections") { save(settings.copy(closeConnections = it)) }
            GeneralToggle("仅统计代理流量", settings.onlyStatisticsProxy, busy, "general_only_proxy") { save(settings.copy(onlyStatisticsProxy = it)) }
        }
        GeneralSection("核心") {
            GeneralOpenRow("Hosts", "${settings.hosts.size} 项", "general_hosts", busy) { hosts = true }
            GeneralToggle("追加系统 DNS", settings.appendSystemDns, busy, "general_system_dns") { save(settings.copy(appendSystemDns = it)) }
        }
        GeneralSection("日志") {
            GeneralOpenRow("日志等级", settings.logLevel, "general_log_level", busy) { editing = "logLevel" }
            GeneralToggle("收集核心日志", settings.openLogs, busy, "general_logs") { save(settings.copy(openLogs = it)) }
            GeneralOpenRow("查看日志", null, "general_show_logs", busy, onShowLogs)
        }
    }
    if (ua) GeneralUserAgentsPage(settings, busy, { ua = false }, onSave)
    if (hosts) GeneralHostsPage(settings, busy, { hosts = false }, onSave)
    editing?.let { field ->
        if (field == "ports") GeneralPortsEditor(settings, busy, { editing = null }, onSave)
        else if (field == "timeout" || field == "logLevel") {
            val options = if (field == "timeout") listOf("500", "1000", "2000", "5000", "8000") else listOf("debug", "info", "warning", "error", "silent")
            GeneralOptionsEditor(if (field == "timeout") "测速超时" else "日志等级", options,
                if (field == "timeout") settings.delayTestTimeout.toString() else settings.logLevel, busy, { editing = null }) { value, done ->
                onSave(if (field == "timeout") settings.copy(delayTestTimeout = value.toInt()) else settings.copy(logLevel = value), done)
            }
        } else GeneralTextEditor(field, when (field) { "secret" -> settings.controllerSecret; "username" -> settings.username; else -> settings.password }, busy,
            onDismiss = { editing = null }) { value, done -> onSave(when (field) {
                "secret" -> settings.copy(controllerSecret = value); "username" -> settings.copy(username = value); else -> settings.copy(password = value)
            }, done) }
    }
}

@Composable
private fun GeneralSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.fillMaxWidth(), content = content) }
    }
}

@Composable
private fun GeneralToggle(title: String, value: Boolean, busy: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, enabled = !busy, onValueChange = onChange).testTag(tag)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp)); Switch(value, null, enabled = !busy)
    }
}

@Composable
private fun GeneralOpenRow(title: String, subtitle: String?, tag: String, busy: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onOpen).testTag(tag).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) } }
        Icon(Icons.Outlined.ChevronRight, "打开$title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GeneralOptionsEditor(title: String, options: List<String>, selected: String, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, (Boolean) -> Unit) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = {
        Column { options.forEach { option -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
            onSave(option) { if (it) onDismiss() else error = "保存失败，请重试" }
        }.testTag("general_option_$option").padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == option, null, enabled = !busy); Text(option, Modifier.padding(start = 12.dp))
        } }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun GeneralPortsEditor(settings: GeneralSettings, busy: Boolean, onDismiss: () -> Unit, onSave: (GeneralSettings, (Boolean) -> Unit) -> Unit) {
    var values by rememberSaveable { mutableStateOf(listOf(settings.mixedPort, settings.port, settings.socksPort, settings.redirPort, settings.tproxyPort).map(Int::toString)) }
    var error by remember { mutableStateOf<String?>(null) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    fun save(next: GeneralSettings) {
        runCatching { next.validated() }.onFailure { error = it.message }.onSuccess {
            onSave(next) { if (it) onDismiss() else error = "保存失败，输入已保留，请重试" }
        }
    }
    // 作者 long：上游端口使用整页表单；保存固定在顶栏，数字键盘弹出时只压缩字段滚动区。
    ProfileEditorPage({ if (!busy) onDismiss() }, { Text("入站端口") }, {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("general_ports_editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1024–49151；除 Mixed 外可填 0 关闭", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("general_editor_error")) }
            listOf("Mixed", "HTTP", "SOCKS", "Redir", "TProxy").forEachIndexed { index, title ->
                OutlinedTextField(values[index], { text -> if (text.length <= 5 && text.all { it in '0'..'9' }) values = values.toMutableList().apply { this[index] = text } },
                    label = { Text(title) }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("general_port_$index"))
            }
            TextButton({ resetting = true }, enabled = !busy) { Text("恢复默认") }
        }
    }, confirmButton = { TextButton({
        val ports = values.map { it.toIntOrNull() ?: -1 }
        save(settings.copy(mixedPort = ports[0], port = ports[1], socksPort = ports[2], redirPort = ports[3], tproxyPort = ports[4]))
    }, enabled = !busy, modifier = Modifier.testTag("general_ports_save")) { Text("保存") } }, backTag = "general_ports_back")
    if (resetting) AlertDialog(onDismissRequest = { if (!busy) resetting = false }, title = { Text("恢复默认端口") },
        text = { Text("Mixed 恢复为 7890，关闭其他入站端口。") }, confirmButton = { TextButton({
            resetting = false
            save(settings.copy(mixedPort = 7890, port = 0, socksPort = 0, redirPort = 0, tproxyPort = 0))
        }, enabled = !busy) { Text("恢复默认") } }, dismissButton = { TextButton({ resetting = false }, enabled = !busy) { Text("取消") } })
}

@Composable
private fun GeneralTextEditor(field: String, initial: String, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, (Boolean) -> Unit) -> Unit) {
    val label = when (field) { "secret" -> "控制器密钥"; "username" -> "账号"; "password" -> "密码"; else -> "User-Agent" }
    var value by rememberSaveable(field) { mutableStateOf(initial) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(label) }, text = {
        Column { OutlinedTextField(value, { if (it.length <= 1024 && it.none(Char::isISOControl)) value = it }, enabled = !busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (field in listOf("secret", "password")) KeyboardType.Password else KeyboardType.Text),
            visualTransformation = if (field in listOf("secret", "password") && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = { if (field in listOf("secret", "password")) IconButton({ revealed = !revealed }) { Icon(Icons.Outlined.Visibility, "显示或隐藏") } },
            modifier = Modifier.fillMaxWidth().testTag("general_text_input"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("general_editor_error")) }
        }
    }, confirmButton = { TextButton({
        if (field == "ua" && value.isBlank()) { error = "请输入 User-Agent"; return@TextButton }
        onSave(value.trim()) { if (it) onDismiss() else error = "保存失败，输入已保留，请重试" }
    }, enabled = !busy, modifier = Modifier.testTag("general_text_save")) { Text("保存") } }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun GeneralUserAgentsPage(settings: GeneralSettings, busy: Boolean, onDismiss: () -> Unit, onSave: (GeneralSettings, (Boolean) -> Unit) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val entries = (settings.userAgents + listOfNotNull(settings.userAgent)).distinct()
    fun save(next: GeneralSettings, done: (Boolean) -> Unit = {}) { onSave(next) { if (!it) error = "保存失败，请重试"; done(it) } }
    ProfileEditorPage({ if (!busy) { if (sorting) sorting = false else onDismiss() } }, { Text("User-Agent") }, {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row {
                TextButton({ editing = "" }, enabled = !busy, modifier = Modifier.testTag("general_ua_add")) { Text("添加") }
                TextButton({ sorting = !sorting }, enabled = !busy && entries.size > 1) { Text(if (sorting) "完成" else "排序") }
            }
            if (sorting) ResourceOrderList(entries.map { ResourceOrderItem(it, it, "User-Agent") }, busy, "general_ua", { save(settings.copy(userAgents = it)) }, Modifier.weight(1f))
            else Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { save(settings.copy(userAgent = null)) }.padding(vertical = 12.dp).testTag("general_ua_default"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(settings.userAgent == null, null); Text("默认", Modifier.padding(start = 12.dp))
                }
                entries.forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { save(settings.copy(userAgent = value)) }.padding(vertical = 8.dp).testTag("general_ua_$index"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(settings.userAgent == value, null); Text(value, Modifier.weight(1f).padding(start = 12.dp), maxLines = 3)
                        IconButton({ editing = value }, enabled = !busy) { Icon(Icons.Outlined.Edit, "编辑") }
                        IconButton({ save(settings.copy(userAgents = entries - value, userAgent = settings.userAgent.takeUnless { it == value })) }, enabled = !busy) { Icon(Icons.Outlined.Delete, "删除") }
                    }
                }
            }
        }
    }, backTag = "general_ua_back")
    editing?.let { original -> GeneralTextEditor("ua", original, busy, { editing = null }) { value, done ->
        if (value != original && value in entries) { error = "User-Agent 已存在"; done(false) }
        else save(settings.copy(userAgents = if (original.isEmpty()) entries + value else entries.map { if (it == original) value else it },
            userAgent = if (settings.userAgent == original) value else settings.userAgent)) { done(it); if (it) editing = null }
    } }
}

@Composable
private fun GeneralHostsPage(settings: GeneralSettings, busy: Boolean, onDismiss: () -> Unit, onSave: (GeneralSettings, (Boolean) -> Unit) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ProfileEditorPage({ if (!busy) onDismiss() }, { Text("Hosts") }, {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("搜索域名或地址") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton({ editing = "" }, enabled = !busy, modifier = Modifier.testTag("general_host_add")) { Text("添加") }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                settings.hosts.filter { (k, v) -> k.contains(query, true) || v.contains(query, true) }.forEach { (name, value) ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { editing = name }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(name); Text(value, style = MaterialTheme.typography.bodySmall) }
                        IconButton({ onSave(settings.copy(hosts = settings.hosts - name)) { if (!it) error = "删除失败，请重试" } }, enabled = !busy) { Icon(Icons.Outlined.Delete, "删除$name") }
                    }
                }
            }
        }
    }, backTag = "general_hosts_back")
    editing?.let { original ->
        var name by rememberSaveable(original) { mutableStateOf(original) }
        var value by rememberSaveable(original) { mutableStateOf(settings.hosts[original].orEmpty()) }
        var formError by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!busy) editing = null }, title = { Text(if (original.isEmpty()) "添加 Hosts" else "编辑 Hosts") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { if (it.length <= 253) name = it }, label = { Text("域名") }, singleLine = true, enabled = !busy, modifier = Modifier.testTag("general_host_name"))
                OutlinedTextField(value, { if (it.length <= 4096) value = it }, label = { Text("地址，多个地址以空格分隔") }, enabled = !busy, modifier = Modifier.testTag("general_host_value"))
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton({
            if (name.trim() != original && name.trim() in settings.hosts) { formError = "域名已存在"; return@TextButton }
            val next = settings.copy(hosts = (settings.hosts - original) + (name.trim() to value.trim()))
            runCatching { next.validated() }.onFailure { formError = it.message }.onSuccess {
                onSave(next) { if (it) editing = null else formError = "保存失败，输入已保留，请重试" }
            }
        }, enabled = !busy, modifier = Modifier.testTag("general_host_save")) { Text("保存") } }, dismissButton = { TextButton({ editing = null }, enabled = !busy) { Text("取消") } })
    }
}
