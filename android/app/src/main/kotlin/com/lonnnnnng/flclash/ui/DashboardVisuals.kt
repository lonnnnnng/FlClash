package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import kotlin.math.cos
import kotlin.math.sin

// 作者 long：首页单独使用 Flutter Dashboard 的层级和尺寸，避免改变其他尚未逐页还原的组件基线。
@Composable
internal fun dashboardPageColor(): Color = if (LocalKClashDarkTheme.current) {
    MaterialTheme.colorScheme.surface
} else {
    MaterialTheme.colorScheme.surfaceContainer
}

@Composable
private fun dashboardCardColor(): Color = if (LocalKClashDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainer
} else {
    MaterialTheme.colorScheme.surfaceBright
}

@Composable
private fun dashboardBlockColor(): Color = if (LocalKClashDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainerHigh
} else {
    MaterialTheme.colorScheme.surfaceContainer
}

@Composable
internal fun DashboardSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = dashboardCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), content = content)
    }
}

@Composable
private fun DashboardHeader(title: String, icon: ImageVector, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.weight(1f), style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold))
        trailing()
    }
}

@Composable
internal fun RuntimeCard(
    state: CoreRuntimeStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val busy = state == CoreRuntimeStatus.STARTING || state == CoreRuntimeStatus.STOPPING
    val colors = MaterialTheme.colorScheme
    val tone = dashboardBlockColor()
    val actionLabel = when (state) {
        CoreRuntimeStatus.STARTING -> "正在启动核心"
        CoreRuntimeStatus.STOPPING -> "正在停止核心"
        else -> if (state.isRunning) "停止核心" else "启动核心"
    }
    val largeText = LocalDensity.current.fontScale > 1.4f
    Card(
        Modifier.fillMaxWidth().testTag("dashboard_runtime_card"),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = dashboardCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 作者 long：只装饰主控背景，不赋予装饰图形语义；出口 IP 尚无数据源，不能伪造探测结果。
            Canvas(Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-66).dp).size(240.dp)) {
                val path = Path()
                repeat(241) { index ->
                    val angle = index * Math.PI * 2 / 240
                    val radius = size.minDimension / 2 * (0.94 + 0.06 * cos(12 * angle))
                    val x = (center.x + radius * cos(angle)).toFloat()
                    val y = (center.y + radius * sin(angle)).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, tone)
            }
            Column(Modifier.padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 18.dp)) {
                DashboardHeader("核心状态", Icons.Outlined.Tune)
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = when {
                                state == CoreRuntimeStatus.FAILED -> colors.errorContainer
                                state.isRunning -> colors.primaryContainer
                                else -> tone
                            },
                            shape = CircleShape,
                        ) {
                            Text(
                                when {
                                    state.isRunning -> "运行中"
                                    state == CoreRuntimeStatus.STOPPED -> "未运行"
                                    state == CoreRuntimeStatus.FAILED -> "启动失败"
                                    state == CoreRuntimeStatus.STOPPING -> "停止中"
                                    else -> "启动中"
                                },
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                        Text(state.label, style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold))
                        Text(
                            if (state.isRunning) "代理已开启" else "连接由你掌控",
                            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Surface(
                        onClick = if (state.isRunning) onStop else onStart,
                        enabled = !busy,
                        modifier = Modifier.size(88.dp).testTag("dashboard_runtime_toggle").semantics {
                            role = Role.Button
                            contentDescription = actionLabel
                        },
                        shape = CircleShape,
                        color = if (state.isRunning) colors.primary else colors.primaryContainer,
                        contentColor = if (state.isRunning) colors.onPrimary else colors.onPrimaryContainer,
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(32.dp))
                            if (!largeText) {
                                Text(
                                    actionLabel,
                                    Modifier.padding(top = 4.dp),
                                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RuntimeStatsCard(state: RuntimeStatsUiState, runtimeState: CoreRuntimeStatus) {
    val active = runtimeState.isRunning
    DashboardSurface(Modifier.testTag("dashboard_stats_card")) {
        DashboardHeader("网络速度", Icons.Outlined.Speed) {
            Text(
                "${if (active) state.connectionCount else 0} 个连接",
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            // 作者 long：大字号时将速度块纵排，保持数值与单位完整，避免两栏压缩后只能读到省略号。
            if (fontScale > 1.4f || maxWidth < 260.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardSpeedTile("上传", Icons.Outlined.ArrowUpward, if (active) state.traffic.up else 0, state.totalTraffic.up, Modifier.fillMaxWidth())
                    DashboardSpeedTile("下载", Icons.Outlined.ArrowDownward, if (active) state.traffic.down else 0, state.totalTraffic.down, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardSpeedTile("上传", Icons.Outlined.ArrowUpward, if (active) state.traffic.up else 0, state.totalTraffic.up, Modifier.weight(1f))
                    DashboardSpeedTile("下载", Icons.Outlined.ArrowDownward, if (active) state.traffic.down else 0, state.totalTraffic.down, Modifier.weight(1f))
                }
            }
        }
        if (state.isLoading && active) CircularProgressIndicator(Modifier.padding(top = 10.dp).size(18.dp), strokeWidth = 2.dp)
        state.error?.let { Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DashboardSpeedTile(label: String, icon: ImageVector, speed: Long, total: Long, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = dashboardBlockColor()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(label, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val formatted = formatBytes(speed)
            val numericEnd = formatted.indexOfFirst { it.isLetter() }.takeIf { it >= 0 } ?: formatted.length
            Text(formatted.take(numericEnd), style = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            Text("${formatted.drop(numericEnd)}/s", style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("累计 ${formatBytes(total)}", style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DashboardProfileCard(profile: Profile?, onShowProfiles: () -> Unit, onAddProfile: () -> Unit) {
    DashboardSurface(Modifier.testTag("dashboard_profile_card")) {
        DashboardHeader("当前配置", Icons.Outlined.Description)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile?.label ?: "还没有配置", style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (profile == null) "添加一个配置，开始连接" else "已选择", style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(
                onClick = if (profile == null) onAddProfile else onShowProfiles,
                modifier = Modifier.testTag("dashboard_profile_action"),
            ) {
                Text(if (profile == null) "添加配置" else "查看配置")
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
            }
        }
    }
}
