package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Code
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    onOpenAccessControl: () -> Unit,
    onOpenGeneral: () -> Unit,
    onShowUnavailable: (String) -> Unit,
    onOpenDeveloper: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenBackup: () -> Unit = { onShowUnavailable("备份与恢复") },
    onOpenScripts: () -> Unit = { onShowUnavailable("自定义资源") },
    onOpenResources: () -> Unit = { onShowUnavailable("资源") },
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
                onClick = onOpenResources,
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
                onOpenBackup()
            }
            ToolMenuRow("按应用代理", Icons.Outlined.Apps, "tools_item_access") {
                onOpenAccessControl()
            }
            ToolMenuRow("高级配置", Icons.Outlined.Tune, "tools_item_advanced") {
                onShowUnavailable("高级配置")
            }
            ToolMenuRow("自定义资源", Icons.Outlined.Layers, "tools_item_resources_settings") {
                onOpenScripts()
            }
            ToolMenuRow("常规", Icons.Outlined.Settings, "tools_item_general") {
                onOpenGeneral()
            }
        }

        ToolSection(title = "其他") {
            ToolMenuRow("免责声明", Icons.Outlined.Gavel, "tools_item_disclaimer") {
                onShowUnavailable("免责声明")
            }
            // 作者 long：额外验收工具仅在 Debug 显式入口下出现，普通启动保持 Flutter Tools 的页面清单。
            if (onOpenDeveloper != null) {
                ToolMenuRow("开发者模式", Icons.Outlined.Code, "tools_item_developer", onOpenDeveloper)
            }
            ToolMenuRow("关于", Icons.Outlined.Info, "tools_item_about") {
                onShowUnavailable("关于")
            }
        }
    }
}

/** 作者 long：电池授权沿用 Flutter 的“常规 → 按需连接 → 前置条件”层级，不再占用仪表盘卡片。 */
@Composable
internal fun GeneralSettingsPanel(onOpenOnDemand: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("page_general")) {
        ToolSection(title = "启动") {
            ToolMenuRow("按需连接", null, "general_item_on_demand", onOpenOnDemand)
        }
    }
}

@Composable
internal fun OnDemandSettingsPanel(
    batteryOptimizationIgnored: Boolean,
    onRequestBatteryOptimization: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("page_on_demand"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolSection(title = "前置条件") {
            Card(Modifier.fillMaxWidth().testTag("on_demand_battery_optimization")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "忽略电池优化",
                        Modifier.weight(1f).padding(end = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        // 作者 long：已授权时只呈现系统回读状态，重复点击不会再次发起相同授权。
                        onClick = { if (!batteryOptimizationIgnored) onRequestBatteryOptimization() },
                        modifier = Modifier.widthIn(min = 80.dp).heightIn(min = 40.dp)
                            .testTag("on_demand_battery_authorize"),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (batteryOptimizationIgnored) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (batteryOptimizationIgnored) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Text(if (batteryOptimizationIgnored) "已授权" else "点击授权") }
                }
            }
        }
        Text(
            "忽略电池优化可保证应用在后台运行。",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
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
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(start = if (icon == null) 0.dp else 16.dp),
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
