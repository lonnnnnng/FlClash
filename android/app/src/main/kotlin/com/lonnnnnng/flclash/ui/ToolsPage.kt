package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** 作者 long：工具页沿用 Flutter ToolsView 的三段列表信息层级，二级页面只从这里进入。 */
@Composable
internal fun ToolsPanel(
    onOpenConnections: () -> Unit,
    onOpenObservation: (ObservationTab) -> Unit,
    onShowUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolSection(title = "导航") {
            ToolMenuRow(
                title = "最近请求",
                icon = Icons.Outlined.Description,
                tag = "tools_item_requests",
                onClick = { onOpenObservation(ObservationTab.REQUESTS) },
            )
            ToolMenuRow(
                title = "活动连接",
                icon = Icons.Outlined.Hub,
                tag = "tools_item_connections",
                onClick = onOpenConnections,
            )
            ToolMenuRow(
                title = "DNS 查询",
                icon = Icons.Outlined.Dns,
                tag = "tools_item_dns",
                onClick = { onOpenObservation(ObservationTab.DNS) },
            )
            ToolMenuRow(
                title = "资源",
                icon = Icons.Outlined.Storage,
                tag = "tools_item_resources",
                onClick = { onShowUnavailable("资源") },
            )
        }

        ToolSection(title = "设置") {
            ToolMenuRow("语言", Icons.Outlined.Language, "tools_item_language") {
                onShowUnavailable("语言")
            }
            ToolMenuRow("主题", Icons.Outlined.Palette, "tools_item_theme") {
                onShowUnavailable("主题")
            }
            ToolMenuRow("备份与恢复", Icons.Outlined.Backup, "tools_item_backup") {
                onShowUnavailable("备份与恢复")
            }
            ToolMenuRow("按应用代理", Icons.Outlined.Apps, "tools_item_access") {
                onShowUnavailable("按应用代理")
            }
            ToolMenuRow("高级配置", Icons.Outlined.Tune, "tools_item_advanced") {
                onShowUnavailable("高级配置")
            }
            ToolMenuRow("自定义资源", Icons.Outlined.Layers, "tools_item_resources_settings") {
                onShowUnavailable("自定义资源")
            }
            ToolMenuRow("通用设置", Icons.Outlined.Settings, "tools_item_general") {
                onShowUnavailable("通用设置")
            }
        }

        ToolSection(title = "其他") {
            ToolMenuRow("免责声明", Icons.Outlined.Gavel, "tools_item_disclaimer") {
                onShowUnavailable("免责声明")
            }
            ToolMenuRow("关于", Icons.Outlined.Info, "tools_item_about") {
                onShowUnavailable("关于")
            }
        }
    }
}

@Composable
private fun ToolSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ToolMenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "打开$title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
