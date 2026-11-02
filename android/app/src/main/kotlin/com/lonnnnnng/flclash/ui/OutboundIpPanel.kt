package com.lonnnnnng.flclash.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutboundIpPanel(
    state: OutboundIpUiState,
    active: Boolean,
    onRefresh: () -> Unit,
    onRefreshQuality: () -> Unit,
) {
    var detailIp by rememberSaveable { mutableStateOf<String?>(null) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    val info = state.info
    LaunchedEffect(active, info?.ip) {
        if (detailIp != info?.ip) detailIp = null
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onRefresh, enabled = !state.isLoading,
            modifier = Modifier.testTag("dashboard_ip_refresh"),
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(info?.countryCode ?: "—", style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Outlined.Refresh, "刷新出口 IP", Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                when {
                    info != null -> if (hidden) "*****" else info.ip
                    state.error != null -> "查询失败"
                    else -> "正在查询出口…"
                },
                Modifier.testTag("dashboard_outbound_ip").then(
                    if (info != null) Modifier.clickable(role = Role.Button, onClickLabel = "出口 IP 详情") { detailIp = info.ip }
                    else Modifier,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.error != null && info == null && active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    state.error != null -> "点击图标重试"
                    info == null -> "出口 IP"
                    state.qualityLoading -> "正在查询 IP 类型…"
                    state.quality != null -> "${state.quality.type.label} · ${state.quality.levelLabel}"
                    else -> "IP 类型查询失败"
                },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info != null) Text(
                state.quality?.organization ?: state.quality?.asn?.let { "AS$it" } ?: "—",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // 作者 long：详情只跟随点击时的 IP；停止或出口已变化时关闭，禁止把新出口数据填进旧 IP 面板。
    if (info != null && detailIp != null && detailIp == info.ip) {
        ModalBottomSheet(onDismissRequest = { detailIp = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.fillMaxWidth().testTag("ip_quality_sheet").verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("IP 质量", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onRefreshQuality, enabled = !state.qualityLoading && !state.isLoading,
                        modifier = Modifier.testTag("ip_quality_refresh")) {
                        Icon(Icons.Outlined.Refresh, "刷新 IP 质量")
                    }
                }
                IpQualityContent(state, hidden, { hidden = it })
            }
        }
    }
}


// 作者 long：首页与服务出口使用相同质量字段和复制规则，避免同一个 IP 在两个入口显示不同含义。
@Composable
internal fun IpQualityContent(state: OutboundIpUiState, hidden: Boolean, onHiddenChange: (Boolean) -> Unit) {
    val info = state.info ?: return
    Text("检测结果仅供参考", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    IpDetailRow("IP 地址", if (hidden) "*****" else info.ip, copyText = info.ip, tag = "ip_quality_address")
    if (state.qualityLoading) CircularProgressIndicator(Modifier.size(24.dp).testTag("ip_quality_loading"), strokeWidth = 2.dp)
    val quality = state.quality
    if (quality != null) {
        IpDetailRow("质量等级", quality.levelLabel)
        IpDetailRow("IP 类型", quality.type.label)
        IpDetailRow("风险标记", quality.flags.joinToString(" · ").ifBlank { "无" })
        IpDetailRow("组织", quality.organization ?: "—", quality.organization)
        IpDetailRow("ASN", quality.asn?.let { "AS$it" } ?: "—", quality.asn?.let { "AS$it" })
        IpDetailRow("查询来源", quality.source.label)
    }
    if (state.qualityFailures.isNotEmpty()) {
        Text("IP 类型查询失败", color = MaterialTheme.colorScheme.error)
        state.qualityFailures.forEach { (source, failure) -> IpDetailRow(source.label, failure.label) }
    }
    state.qualityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("隐藏 IP", Modifier.weight(1f))
        Switch(checked = hidden, onCheckedChange = onHiddenChange, modifier = Modifier.testTag("ip_quality_hide"))
    }
}

@Composable
private fun IpDetailRow(label: String, value: String, copyText: String? = null, tag: String? = null) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .then(if (copyText != null) Modifier.clickable(role = Role.Button, onClickLabel = "复制$label") {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, copyText))
                } else Modifier).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
            if (copyText != null) Icon(Icons.Outlined.ContentCopy, "复制$label", Modifier.size(18.dp))
        }
    }
}
