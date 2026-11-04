package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.core.*

internal fun ServiceStatus.label(): String = when (this) {
    ServiceStatus.AVAILABLE -> "可用"
    ServiceStatus.UNAVAILABLE -> "不可用"
    ServiceStatus.RESTRICTED -> "访问受限"
    ServiceStatus.DISALLOWED_ISP -> "运营商不受支持"
    ServiceStatus.BLOCKED -> "已阻止"
    ServiceStatus.UNSUPPORTED_REGION -> "地区不受支持"
    ServiceStatus.ORIGINALS_ONLY -> "仅限原创内容"
    ServiceStatus.COMING_SOON -> "即将推出"
    ServiceStatus.TIMEOUT -> "超时"
    ServiceStatus.FAILED -> "检测失败"
}

internal fun ServiceEntry.statusText(): String = when {
    loading -> "检测中…"
    result != null -> if (result.status == ServiceStatus.AVAILABLE && result.delayMillis != null) "${result.delayMillis} ms" else result.status.label()
    failed -> "检测失败"
    else -> "待检测"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardServiceStatusCard(
    state: ServiceStatusUiState,
    onCheck: (List<ServiceTarget>) -> Unit,
    onToggle: (ServiceTarget, Boolean) -> Unit,
    onMove: (ServiceTarget, Int) -> Unit,
    onDetailVisible: (Boolean) -> Unit,
    onRetrySettings: () -> Unit,
    onRefreshExit: (String) -> Unit = {},
    onOpenQuality: (String) -> Unit = {},
    onCloseQuality: () -> Unit = {},
    onRefreshQuality: () -> Unit = {},
    onReorder: (List<ServiceTarget>) -> Unit = {},
) {
    var detail by rememberSaveable { mutableStateOf(false) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var hiddenIp by rememberSaveable { mutableStateOf(false) }
    val detailScroll = rememberScrollState()
    val qualityScroll = rememberScrollState()
    var managementAtTop by remember { mutableStateOf(true) }
    val quality = state.qualityNode?.let { state.exits[it] }
    // 作者 long：弹层打开后仍需前台/路由保护，但展示全部目标不会自动触发一轮全量外部请求。
    DisposableEffect(detail) { onDetailVisible(detail); onDispose { onDetailVisible(false) } }
    DashboardSurface(modifier = Modifier.testTag("dashboard_service_card"), onClick = { detail = true }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Verified, null)
            Text("服务状态", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, "查看服务状态")
        }
        Spacer(Modifier.height(12.dp))
        val shown = state.settings.enabled.take(2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            shown.forEach { target ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(target.label, style = MaterialTheme.typography.bodyLarge)
                    ServiceResultText(state.entries[target] ?: ServiceEntry(), "service_card_${target.id}")
                }
            }
        }
        val entries = shown.mapNotNull { state.entries[it] }
        val abnormal = entries.count { it.failed || it.result?.status?.let { value -> value != ServiceStatus.AVAILABLE } == true }
        val verdict = when {
            abnormal > 0 -> "$abnormal 项异常"
            entries.size == shown.size && entries.all { it.result != null && !it.loading } -> "全部可用"
            else -> null
        }
        verdict?.let { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp)) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (detail) DashboardModalSheet(
        title = if (quality != null) "IP 质量" else if (manage) "管理服务" else "服务状态",
        sheetTag = "service_detail_sheet", closeTag = "service_detail_close", scrollTag = "service_detail_scroll",
        onDismiss = { onCloseQuality(); detail = false; manage = false },
        scrollState = if (quality != null) qualityScroll else detailScroll,
        scrollable = !manage || quality != null, fitsContent = !manage || quality != null,
        isContentAtTop = { if (quality != null) qualityScroll.value == 0 else if (manage) managementAtTop else detailScroll.value == 0 },
        // 作者 long：系统返回与标题返回遵循同一层级，质量详情和管理页先回服务列表，不直接丢弃整个面板。
        onNavigateBack = if (quality != null || manage) ({ if (quality != null) onCloseQuality() else manage = false }) else null,
        backTag = "service_manage_back", actions = {
            if (quality != null) IconButton(onClick = onRefreshQuality, enabled = !quality.qualityLoading,
                modifier = Modifier.testTag("service_quality_refresh")) { Icon(Icons.Outlined.Refresh, "刷新 IP 质量") }
        },
    ) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("service_error")) }
            if (quality != null) {
                Column(Modifier.testTag("service_quality_details"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IpQualityContent(quality, hiddenIp, { hiddenIp = it })
                }
            } else if (state.settingsLoading) TextButton(onClick = onRetrySettings) { Text("重新读取设置") }
            else if (manage) {
                ServiceOrderEditor(state.settings, state.saving, onToggle, onMove, onReorder, Modifier.weight(1f),
                    onContentAtTopChanged = { managementAtTop = it })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { onCheck(state.settings.enabled) }, enabled = state.entries.values.none { it.loading }, modifier = Modifier.testTag("service_check_all")) { Text("检测全部") }
                    TextButton(onClick = { manage = true }, modifier = Modifier.testTag("service_manage")) { Text("管理服务") }
                }
                state.settings.enabled.forEach { target ->
                    val entry = state.entries[target] ?: ServiceEntry()
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(target.label, style = MaterialTheme.typography.titleMedium)
                            ServiceResultText(entry, "service_result_${target.id}")
                            entry.result?.let { result ->
                                val node = result.chains.firstOrNull()
                                val exit = node?.let { state.exits[it] }
                                val country = exit?.info?.countryCode ?: result.region
                                val value = when {
                                    exit?.info != null -> if (hiddenIp) "*****" else exit.info.ip
                                    exit?.isLoading == true -> "正在查询出口…"
                                    else -> node ?: "—"
                                }
                                Text(listOfNotNull(country, value).joinToString(" · "),
                                    modifier = Modifier.testTag("service_exit_${target.id}").then(if (exit?.info != null)
                                        Modifier.clickable(role = Role.Button, onClickLabel = "查看 ${target.label} 出口 IP 质量") { onOpenQuality(checkNotNull(node)) } else Modifier),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (exit?.error != null) TextButton(onClick = { onRefreshExit(checkNotNull(node)) }, modifier = Modifier.testTag("service_exit_retry_${target.id}")) { Text("出口 IP 查询失败，重试") }
                            }
                        }
                        TextButton(onClick = { onCheck(listOf(target)) }, enabled = !entry.loading, modifier = Modifier.testTag("service_check_${target.id}")) { Text(if (entry.loading) "检测中…" else "检测") }
                    }
                }
            }
    }
}

@Composable
private fun ServiceResultText(entry: ServiceEntry, tag: String) {
    val failed = entry.failed || entry.result?.status in listOf(ServiceStatus.FAILED, ServiceStatus.TIMEOUT, ServiceStatus.UNAVAILABLE)
    Text(entry.statusText(), style = MaterialTheme.typography.bodyMedium,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(tag))
}
