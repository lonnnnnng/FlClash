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
    onProxies: () -> Unit,
    onGroup: ((Int) -> Unit)? = null,
) {
    val readable = summary?.takeUnless { it.unreadable }
    fun count(value: Int?) = value?.toString() ?: "—"
    if (summary?.unreadable == true) {
        Text("无法读取配置概览，请在原始 YAML 中修正配置。", color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 8.dp).testTag("custom_profile_summary_error"))
    }
    Surface(shape = RoundedCornerShape(24.dp), color = dashboardCardColor(), modifier = Modifier.padding(top = 8.dp)) {
        Column {
            CustomOverviewRow("规则", count(readable?.rules), Icons.AutoMirrored.Outlined.Rule, enabled,
                "custom_profile_rules", onClick = onRules)
            val resources = readable?.let { if (it.missingProviders > 0) "${it.referencedProviders} · 缺失 ${it.missingProviders}" else it.referencedProviders.toString() } ?: "—"
            CustomOverviewRow("资源", resources, Icons.Outlined.Cloud, enabled, "custom_profile_resources",
                error = (readable?.missingProviders ?: 0) > 0, onClick = onResources)
            CustomOverviewRow("DNS", readable?.let { if (it.dnsEnabled) "已启用" else "未启用" } ?: "—",
                Icons.Outlined.Dns, enabled, "custom_profile_dns", onClick = onDns)
            CustomOverviewRow("更多", readable?.let { if (it.moreInEffect > 0) "${it.moreInEffect} 项生效" else "默认" } ?: "—",
                Icons.Outlined.Tune, enabled, "custom_profile_more", onClick = onMore)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("策略组", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (!readable?.groups.isNullOrEmpty()) IconButton(onClick = onGroups, enabled = enabled,
            modifier = Modifier.testTag("custom_profile_manage_groups")) { Icon(Icons.Outlined.Settings, null) }
        IconButton(onClick = { onGroup?.invoke(-1) ?: onGroups() }, enabled = enabled, modifier = Modifier.testTag("custom_profile_add_group")) {
            Icon(Icons.Outlined.Add, null)
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = dashboardCardColor()) {
        Column {
            if (readable?.groups.isNullOrEmpty()) {
                ProfileSectionRow("添加策略组", "将代理和资源组合为可切换的出站策略", Icons.Outlined.Hub, enabled,
                    Modifier.testTag("custom_profile_groups"), { onGroup?.invoke(-1) ?: onGroups() })
            } else {
                readable.groups.forEachIndexed { index, group ->
                    ProfileSectionRow(group.name, "${group.type} · ${group.members} 个成员 · ${group.resources} 个资源", Icons.Outlined.Hub,
                        enabled, Modifier.testTag("custom_profile_group_$index"), { onGroup?.invoke(index) ?: onGroups() })
                }
            }
        }
    }
    ProfileSectionRow("代理", readable?.let { "${it.proxies} 个本地代理" }, Icons.Outlined.Link, enabled,
        Modifier.testTag("custom_profile_proxies"), onProxies)
}

@Composable
private fun CustomOverviewRow(title: String, status: String, icon: ImageVector, enabled: Boolean,
    tag: String, error: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
