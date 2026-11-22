package com.lonnnnnng.flclash.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutboundIpPanel(
    state: OutboundIpUiState,
    active: Boolean,
    onRefresh: () -> Unit,
    onRefreshQuality: () -> Unit,
    onCopyText: ((String, String) -> Unit)? = null,
) {
    var detailIp by rememberSaveable { mutableStateOf<String?>(null) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    val info = state.info
    val context = LocalContext.current
    val copy: (String, String) -> Unit = onCopyText ?: { label, value ->
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(label, value))
    }
    LaunchedEffect(active, info?.ip) {
        if (detailIp != info?.ip) detailIp = null
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onRefresh, enabled = !state.isLoading,
            modifier = Modifier.testTag("dashboard_ip_refresh"),
        ) {
            if (state.isLoading && info == null) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(
                info?.countryCode?.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT) ?: "—",
                style = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp).height(44.dp)) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    info != null -> Text(
                        if (hidden) "*****" else info.ip,
                        Modifier.fillMaxWidth().testTag("dashboard_outbound_ip")
                            .clickable { detailIp = info.ip },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    state.isLoading -> DashboardSkeletonBar(Modifier.testTag("dashboard_outbound_ip_loading"), 136.dp, 16.dp)
                    state.error != null -> Text(
                        tr("查询失败"), Modifier.fillMaxWidth().testTag("dashboard_outbound_ip"),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    else -> Text("—", Modifier.testTag("dashboard_outbound_ip"), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    info == null && state.isLoading -> DashboardSkeletonBar(width = 82.dp, height = 9.dp)
                    state.error != null -> Text(tr("点击国家简称刷新"), Modifier.testTag("dashboard_outbound_ip_retry"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                    info == null -> Text(tr("出口 IP"), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    state.quality != null -> Text("${state.quality.type.label} · ${tr(state.quality.levelLabel)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    state.qualityLoading -> DashboardSkeletonBar(width = 112.dp, height = 9.dp)
                    else -> Text(tr("IP 类型查询失败"), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
    // 作者 long：详情只跟随点击时的 IP；停止或出口已变化时关闭，禁止把新出口数据填进旧 IP 面板。
    if (info != null && detailIp != null && detailIp == info.ip) {
        DashboardModalSheet(title = tr("IP 质量"), sheetTag = "ip_quality_sheet", closeTag = "ip_quality_close",
            onDismiss = { detailIp = null }, actions = {
                IconButton(onClick = onRefreshQuality, enabled = !state.qualityLoading && !state.isLoading,
                    modifier = Modifier.testTag("ip_quality_refresh")) {
                    Icon(Icons.Outlined.Refresh, null)
                }
            }) {
            IpQualityContent(state, hidden, { hidden = it }, copy)
        }
    }
}


// 作者 long：首页与服务出口使用相同质量字段和复制规则，避免同一个 IP 在两个入口显示不同含义。
@Composable
internal fun IpQualityContent(
    state: OutboundIpUiState,
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
    onCopyText: ((String, String) -> Unit)? = null,
) {
    val info = state.info ?: return
    val context = LocalContext.current
    val copy: (String, String) -> Unit = onCopyText ?: { label, value ->
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(label, value))
    }
    Text(tr("检测结果仅供参考"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val quality = state.quality
    DashboardDetailSection(tr("基本信息")) {
        IpDetailRow(tr("IP 地址"), if (hidden) "*****" else info.ip, copyText = info.ip, tag = "ip_quality_address", onCopyText = copy)
        if (quality != null || state.qualityLoading) {
            // 作者 long：加载占位只替换值，不插入额外进度行；旧结果刷新保留原值，面板不会因spinner增删而跳动。
            IpDetailRow(tr("质量等级"), tr(quality?.levelLabel.orEmpty()), tag = "ip_quality_level", loading = quality == null, placeholderWidth = 40,
                loadingTag = "ip_quality_loading")
            IpDetailRow(tr("IP 类型"), quality?.type?.label.orEmpty(), tag = "ip_quality_type", loading = quality == null, placeholderWidth = 64)
            IpDetailRow(tr("风险标记"), quality?.flags?.joinToString(" · ") { tr(it) }?.ifBlank { tr("无") }.orEmpty(), tag = "ip_quality_flags", loading = quality == null)
        } else Text(tr("IP 类型查询失败"), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error)
    }
    if (quality != null || state.qualityLoading) DashboardDetailSection(tr("网络")) {
            IpDetailRow(tr("组织"), quality?.organization ?: "—", quality?.organization, tag = "ip_quality_organization", loading = quality == null, placeholderWidth = 120, onCopyText = copy)
        IpDetailRow("ASN", quality?.asn?.let { "AS$it" } ?: "—", quality?.asn?.let { "AS$it" }, tag = "ip_quality_asn", loading = quality == null, placeholderWidth = 56, onCopyText = copy)
        IpDetailRow(tr("查询来源"), quality?.source?.label.orEmpty(), tag = "ip_quality_source", loading = quality == null, placeholderWidth = 72)
    }
    if (state.qualityFailures.isNotEmpty()) {
        DashboardDetailSection(tr("查询来源")) {
            state.qualityFailures.forEach { (source, failure) -> IpDetailRow(source.label, failure.label) }
        }
    }
    state.qualityError?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
    DashboardDetailSection(tr("其他")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(hidden, onValueChange = onHiddenChange)
            .testTag("ip_quality_hide"), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("隐藏 IP"), Modifier.weight(1f))
            Switch(checked = hidden, onCheckedChange = null)
        }
    }
}

@Composable
private fun IpDetailRow(label: String, value: String, copyText: String? = null, tag: String? = null,
    loading: Boolean = false, placeholderWidth: Int = 40, loadingTag: String? = null,
    onCopyText: (String, String) -> Unit = { _, _ -> }) {
    Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .then(if (copyText != null) Modifier.clickable {
                    onCopyText(label, copyText)
                } else Modifier).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(0.6f).heightIn(min = 22.dp), contentAlignment = Alignment.CenterStart) {
                if (loading) Box(Modifier.width(placeholderWidth.dp).height(14.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), RoundedCornerShape(4.dp))
                    .then(if (loadingTag != null) Modifier.testTag(loadingTag) else Modifier))
                else Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            // 作者 long：首页地址行同时支持点文字和点图标复制，独立48dp入口避免辅助工具点击空白区域无效。
            if (copyText != null) {
                IconButton(
                    onClick = { onCopyText(label, copyText) },
                    modifier = Modifier.size(32.dp).testTag("${tag ?: label}_copy"),
                ) { Icon(Icons.Outlined.ContentCopy, tr("复制") + label, Modifier.size(18.dp)) }
            } else Box(Modifier.size(18.dp))
        }
    }
}
