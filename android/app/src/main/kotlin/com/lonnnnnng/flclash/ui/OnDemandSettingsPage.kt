package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.OnDemandNetworkState
import com.lonnnnnng.flclash.service.OnDemandSettings

private val SsidSnapshotSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })

/** 作者 long：按需列表只改精确SSID规则；定位与电池授权分开回读，不能把已授权按钮当成系统实际状态。 */
@Composable
internal fun OnDemandSettingsPanel(
    batteryOptimizationIgnored: Boolean,
    onRequestBatteryOptimization: () -> Unit,
    network: OnDemandNetworkState = OnDemandNetworkState(),
    onRequestLocation: () -> Unit = {},
    ssids: List<String> = emptyList(),
    busy: Boolean = false,
    error: String? = null,
    suspended: Boolean = false,
    savedVersion: Long = 0L,
    onSave: (List<String>, List<String>) -> Unit = { _, _ -> },
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf<String?>(null) }
    var value by rememberSaveable { mutableStateOf("") }
    // 作者 long：比较基线和草稿一起恢复，不能重建后用空目录或最新目录悄悄替代打开时快照。
    var expected by rememberSaveable(stateSaver = SsidSnapshotSaver) { mutableStateOf(emptyList<String>()) }
    var sortExpected by rememberSaveable(stateSaver = SsidSnapshotSaver) { mutableStateOf(emptyList<String>()) }
    var deleteExpected by rememberSaveable(stateSaver = SsidSnapshotSaver) { mutableStateOf(emptyList<String>()) }
    var seenSavedVersion by rememberSaveable { mutableLongStateOf(savedVersion) }
    var formError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(savedVersion) {
        if (seenSavedVersion != savedVersion) {
            editing = false; sorting = false; deleting = false; selecting = false; selected = emptySet()
            seenSavedVersion = savedVersion
        }
    }
    LaunchedEffect(ssids) { selected = selected.intersect(ssids.toSet()) }
    fun edit(ssid: String?) { original = ssid; value = ssid.orEmpty(); expected = ssids.toList(); formError = null; editing = true }
    Column(Modifier.fillMaxWidth().testTag("page_on_demand"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("前置条件", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.large, color = dashboardCardColor()) {
            Column {
                OnDemandAuthorizationRow("忽略电池优化", batteryOptimizationIgnored, "on_demand_battery_authorize", onRequestBatteryOptimization)
                OnDemandAuthorizationRow("定位权限", network.authorized, "on_demand_location_authorize", onRequestLocation)
            }
        }
        Text("忽略电池优化可保证应用在后台运行。读取 Wi-Fi 名称需要精确定位；后台匹配还需选择“始终允许”。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!network.locationEnabled) Text("系统定位已关闭，暂时无法识别当前 SSID。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("on_demand_location_disabled"))
        if (suspended) Text("已命中排除 SSID，转发暂停；离开该 Wi-Fi 后继续本次运行。", Modifier.testTag("on_demand_suspended"), color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("排除 SSID", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selecting) {
                IconButton(onClick = { selected = if (selected.containsAll(ssids)) emptySet() else ssids.toSet() }, enabled = !busy,
                    modifier = Modifier.testTag("on_demand_select_all")) { Icon(Icons.Outlined.SelectAll, null) }
                IconButton(onClick = { deleteExpected = ssids.toList(); deleting = true }, enabled = !busy && selected.isNotEmpty(), modifier = Modifier.testTag("on_demand_delete")) { Icon(Icons.Outlined.Delete, null) }
                TextButton(onClick = { selecting = false; selected = emptySet() }, enabled = !busy) { Text("完成") }
            } else {
                if (ssids.isNotEmpty()) {
                    IconButton(onClick = { selecting = true }, enabled = !busy, modifier = Modifier.testTag("on_demand_select")) { Icon(Icons.Outlined.Checklist, null) }
                    if (ssids.size > 1) IconButton(onClick = { sortExpected = ssids.toList(); sorting = true }, enabled = !busy, modifier = Modifier.testTag("on_demand_sort")) { Icon(Icons.Outlined.SwapVert, null) }
                }
                IconButton(onClick = { edit(null) }, enabled = !busy, modifier = Modifier.testTag("on_demand_add")) { Icon(Icons.Outlined.AddCircleOutline, null) }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("on_demand_error")) }
        if (ssids.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
            Text("尚未添加排除 SSID", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Surface(color = dashboardCardColor(), shape = MaterialTheme.shapes.large) {
            Column {
                ssids.forEachIndexed { index, ssid ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !busy) {
                        if (selecting) selected = if (ssid in selected) selected - ssid else selected + ssid else edit(ssid)
                    }.padding(horizontal = 16.dp).testTag("on_demand_ssid_$index"), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Checkbox(ssid in selected, null, modifier = Modifier.padding(end = 12.dp))
                        Text(ssid, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!selecting) Icon(Icons.Outlined.ChevronRight, null)
                    }
                }
            }
        }
        Text("连接这些 Wi-Fi 时暂停服务和监听，保留运行意图；离开后恢复。手动停止后不会自动恢复。SSID 区分大小写和空格，无法读取时按未知网络处理。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editing) ProfileEditorPage(onDismissRequest = { if (!busy) editing = false }, title = { Text(if (original == null) "添加 SSID" else "编辑 SSID") },
        backTag = "on_demand_editor_back", confirmButton = {
            TextButton(onClick = {
                val next = if (original == null) expected + value else expected.map { if (it == original) value else it }
                val problem = runCatching { OnDemandSettings.validate(next) }.exceptionOrNull()
                if (problem != null) formError = problem.message
                else onSave(expected, next)
            }, enabled = !busy, modifier = Modifier.testTag("on_demand_save")) { Text("保存") }
        }, text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value, { value = it; formError = null }, Modifier.fillMaxWidth().testTag("on_demand_input"), label = { Text("SSID") },
                    enabled = !busy, singleLine = true, isError = formError != null,
                    supportingText = { Text("${value.codePointCount(0, value.length)} / 32，保留名称中的空格") })
                (formError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("on_demand_editor_error")) }
            }
        })
    if (sorting) {
        ProfileEditorPage(onDismissRequest = { if (!busy) sorting = false }, title = { Text("SSID 排序") }, backTag = "on_demand_sort_back", text = {
            Column(Modifier.fillMaxSize()) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ResourceOrderList(sortExpected.map { ResourceOrderItem(it, it, "排除 SSID") }, busy, "on_demand", { order -> onSave(sortExpected, order) }, Modifier.weight(1f))
            }
        })
    }
    if (deleting) {
        AlertDialog(onDismissRequest = { if (!busy) deleting = false }, title = { Text("删除所选 SSID？") }, text = { Text("将删除 ${selected.size} 项排除规则。") },
            confirmButton = { TextButton(onClick = { onSave(deleteExpected, deleteExpected.filterNot { it in selected }) },
                enabled = !busy, modifier = Modifier.testTag("on_demand_delete_confirm")) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = false }, enabled = !busy) { Text("取消") } })
    }
}

@Composable
private fun OnDemandAuthorizationRow(title: String, authorized: Boolean, tag: String, onRequest: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        Button(onClick = { if (!authorized) onRequest() }, modifier = Modifier.widthIn(min = 80.dp).heightIn(min = 40.dp).testTag(tag),
            contentPadding = PaddingValues(horizontal = 12.dp), colors = ButtonDefaults.buttonColors(
                containerColor = if (authorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (authorized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer)) { Text(if (authorized) "已授权" else "点击授权") }
    }
}
