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
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.ui.platform.LocalContext
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
    onOpenObservation: () -> Unit,
    onOpenAccessControl: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenTheme: () -> Unit,
    onShowUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDeveloper: (() -> Unit)? = null,
    onOpenBackup: () -> Unit = { onShowUnavailable(tr("备份与恢复")) },
    onOpenScripts: () -> Unit = { onShowUnavailable(tr("自定义资源")) },
    onOpenResources: () -> Unit = { onShowUnavailable(tr("资源")) },
    onOpenAdvanced: () -> Unit = { onShowUnavailable(tr("高级配置")) },
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolSection(title = tr("导航")) {
            ToolMenuRow(
                title = tr("网络活动"),
                icon = Icons.Outlined.Hub,
                tag = "tools_item_observations",
                onClick = onOpenObservation,
            )
            ToolMenuRow(
                title = tr("资源"),
                icon = Icons.Outlined.Storage,
                tag = "tools_item_resources",
                onClick = onOpenResources,
            )
        }

        ToolSection(title = tr("设置")) {
            ToolMenuRow(tr("语言"), Icons.Outlined.Language, "tools_item_language") {
                onOpenLanguage()
            }
            ToolMenuRow(tr("主题"), Icons.Outlined.Palette, "tools_item_theme") {
                onOpenTheme()
            }
            ToolMenuRow(tr("备份与恢复"), Icons.Outlined.Backup, "tools_item_backup") {
                onOpenBackup()
            }
            ToolMenuRow(tr("按应用代理"), Icons.Outlined.Apps, "tools_item_access") {
                onOpenAccessControl()
            }
            ToolMenuRow(tr("高级配置"), Icons.Outlined.Tune, "tools_item_advanced") {
                onOpenAdvanced()
            }
            ToolMenuRow(tr("自定义资源"), Icons.Outlined.Layers, "tools_item_resources_settings") {
                onOpenScripts()
            }
            ToolMenuRow(tr("常规"), Icons.Outlined.Settings, "tools_item_general") {
                onOpenGeneral()
            }
            ToolMenuRow(tr("应用更新"), Icons.Outlined.SystemUpdate, "tools_item_app_update") {
                context.startActivity(android.content.Intent(context, com.lonnnnnng.flclash.update.AppUpdateActivity::class.java))
            }
        }

        if (onOpenDeveloper != null) ToolSection(title = tr("其他")) {
            // 作者 long：额外验收工具仅在 Debug 显式入口下出现，普通启动保持 Flutter Tools 的页面清单。
            ToolMenuRow(tr("开发者模式"), Icons.Outlined.Code, "tools_item_developer", onOpenDeveloper)
        }
    }
}

@Composable
internal fun AdvancedSettingsPanel(onOpenNetwork: () -> Unit, onOpenScripts: () -> Unit,
    onOpenDns: () -> Unit = {}, onOpenRules: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().testTag("page_advanced")) {
        ToolSection(title = tr("高级配置")) {
            ToolMenuRow(tr("网络"), Icons.Outlined.Hub, "advanced_item_network", onOpenNetwork)
            ToolMenuRow("DNS", Icons.Outlined.Dns, "advanced_item_dns", onOpenDns)
            ToolMenuRow(tr("附加规则"), Icons.Outlined.Description, "advanced_item_rules", onOpenRules)
            ToolMenuRow(tr("脚本"), Icons.Outlined.Code, "advanced_item_scripts", onOpenScripts)
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
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
