package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 作者 long：概览沿用 Flutter 的四分区与策略组层级，数量只来自读取完成的配置快照。 */
@Composable
internal fun CustomProfileOverview(
    summary: CustomProfileSummary?,
    enabled: Boolean,
    onRules: () -> Unit,
    onResources: () -> Unit,
    onDns: () -> Unit,
    onMore: () -> Unit,
    onGroups: () -> Unit,
    onGroup: ((Int) -> Unit)? = null,
) {
    val readable = summary?.takeUnless { it.unreadable }
    fun count(value: Int?) = value?.toString() ?: "—"
    if (summary?.unreadable == true) {
        Text(tr("无法读取配置概览，请在原始 YAML 中修正配置。"), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 8.dp).testTag("custom_profile_summary_error"))
    }
    Surface(shape = RoundedCornerShape(24.dp), color = dashboardCardColor(), modifier = Modifier.padding(top = 8.dp)) {
        Column {
            CustomOverviewRow(tr("规则"), count(readable?.rules), Icons.AutoMirrored.Outlined.Rule, enabled,
                "custom_profile_rules", onClick = onRules)
            val resources = readable?.let { if (it.missingProviders > 0) tr("{0} · 缺失 {1}", it.referencedProviders, it.missingProviders) else it.referencedProviders.toString() } ?: "—"
            CustomOverviewRow(tr("资源"), resources, Icons.Outlined.Cloud, enabled, "custom_profile_resources",
                error = (readable?.missingProviders ?: 0) > 0, onClick = onResources)
            CustomOverviewRow("DNS", readable?.let { if (it.dnsEnabled) tr("已启用") else tr("未启用") } ?: "—",
                Icons.Outlined.Dns, enabled, "custom_profile_dns", active = readable?.dnsEnabled == true, onClick = onDns)
            CustomOverviewRow(tr("更多"), readable?.let { if (it.moreInEffect > 0) tr("{0} 项生效", it.moreInEffect) else tr("默认") } ?: "—",
                Icons.Outlined.Tune, enabled, "custom_profile_more", active = (readable?.moreInEffect ?: 0) > 0, onClick = onMore)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("策略组"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (!readable?.groups.isNullOrEmpty()) IconButton(onClick = onGroups, enabled = enabled,
            modifier = Modifier.testTag("custom_profile_manage_groups")) { Icon(Icons.Outlined.Settings, null) }
        IconButton(onClick = { onGroup?.invoke(-1) ?: onGroups() }, enabled = enabled, modifier = Modifier.testTag("custom_profile_add_group")) {
            Icon(Icons.Outlined.Add, null)
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = dashboardCardColor()) {
        Column {
            if (readable?.groups.isNullOrEmpty()) {
                ProfileSectionRow(tr("添加策略组"), modifier = Modifier.testTag("custom_profile_groups"),
                    detail = tr("将代理和资源组合为可切换的出站策略"), icon = Icons.Outlined.Hub, enabled = enabled,
                    onClick = { onGroup?.invoke(-1) ?: onGroups() })
            } else {
                readable.groups.forEachIndexed { index, group ->
                    ProfileSectionRow(group.name, modifier = Modifier.testTag("custom_profile_group_$index"),
                        detail = tr("{0} · {1} 个成员 · {2} 个资源", group.type, group.members, group.resources),
                        icon = Icons.Outlined.Hub, enabled = enabled, onClick = { onGroup?.invoke(index) ?: onGroups() })
                }
            }
        }
    }
}

@Composable
private fun CustomOverviewRow(title: String, status: String, icon: ImageVector, enabled: Boolean,
    tag: String, error: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = when {
                error -> MaterialTheme.colorScheme.error
                // 作者 long：只有已生效配置使用强调色，默认和未读取状态继续弱化，保持概览状态层级与 Flutter 一致。
                active -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            })
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
