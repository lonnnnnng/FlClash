package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import java.text.NumberFormat
import java.util.Locale
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.ProxyGroupInfo
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
internal fun dashboardCardColor(): Color = if (LocalKClashDarkTheme.current) {
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
internal fun DashboardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        // 作者 long：首页卡片本身就是 Flutter 手机端的进入详情入口，
                        // 保留内部同步、切换等按钮的独立点击语义。
                        .clickable(onClick = onClick)
                        .semantics { role = Role.Button }
                },
            ),
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
internal fun DashboardProxyGroupsCard(
    state: ProxyGroupsUiState,
    runtimeState: CoreRuntimeStatus,
    mode: CoreOutboundMode,
    currentProfileId: Long?,
    currentGroupName: String?,
    onRefresh: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
    onSelect: (ProxyGroupInfo, String) -> Unit,
    onTestDelay: (ProxyGroupInfo, String) -> Unit,
    onTestGroupDelay: (ProxyGroupInfo) -> Unit,
) {
    val groups = visibleProxyGroups(state.groups, mode)
    var showPicker by rememberSaveable(currentProfileId) { mutableStateOf(false) }
    DashboardSurface(
        modifier = Modifier.testTag("dashboard_proxy_groups_card"),
        onClick = if (groups.isEmpty()) null else ({ showPicker = true }),
    ) {
        DashboardHeader("策略组", Icons.Outlined.Layers) {
            if (groups.isNotEmpty()) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        state.error?.let { message ->
            Row(Modifier.fillMaxWidth().testTag("dashboard_proxy_groups_error"), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onRefresh, enabled = !state.isLoading, modifier = Modifier.testTag("dashboard_proxy_groups_retry")) { Text("重试") }
            }
        }
        if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 36.dp)
                    .testTag("dashboard_proxy_groups_empty"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(64.dp).background(dashboardBlockColor(), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Layers, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (state.isLoading) "正在加载代理组" else if (state.error != null) "代理组加载失败" else "没有代理组",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (currentProfileId == null) Text(
                    "添加有效配置后显示代理组",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        groups.size.toString(),
                        Modifier.testTag("dashboard_proxy_groups_count"),
                        style = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Text(" 组", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall)
                }
                val shown = groups.take(4)
                val rest = groups.size - shown.size
                val badgeCount = shown.size + if (rest > 0) 1 else 0
                Box(Modifier.width((badgeCount * 40 - (badgeCount - 1) * 8).dp).height(40.dp)) {
                    repeat(badgeCount) { index ->
                        Box(
                            Modifier.offset(x = (index * 32).dp).size(40.dp)
                                .border(3.dp, dashboardCardColor(), RoundedCornerShape(14.dp))
                                .padding(3.dp).background(dashboardBlockColor(), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 作者 long：核心暂未提供自定义 icon，使用上游同样的组名首字回退，不伪造远程图标数据。
                            Text(
                                if (index < shown.size) shown[index].name.take(1) else "+$rest",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showPicker) {
        var contentAtTop by remember { mutableStateOf(true) }
        ConnectionModalSheet(
            onDismiss = { showPicker = false },
            contentScrollState = rememberScrollState(),
            accessibilityTitle = "代理",
            isContentAtTop = { contentAtTop },
            avoidIme = true,
        ) { requestDismiss ->
            Column(Modifier.fillMaxSize().testTag("dashboard_proxy_picker")) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("代理", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = requestDismiss, modifier = Modifier.testTag("dashboard_proxy_picker_close")) {
                        Icon(Icons.Outlined.Close, "关闭代理")
                    }
                }
                // 作者 long：弹层和完整代理页共用真实操作入口；选择节点不关闭弹层，当前分组继续写回 Profile。
                ProxyGroupsCard(
                    state = state,
                    runtimeState = runtimeState,
                    currentProfileId = currentProfileId,
                    currentGroupName = currentGroupName,
                    mode = mode,
                    picker = true,
                    onContentAtTopChanged = { contentAtTop = it },
                    onRefresh = onRefresh,
                    onSelectGroup = onSelectGroup,
                    onToggleGroup = onToggleGroup,
                    onSelect = onSelect,
                    onTestDelay = onTestDelay,
                    onTestGroupDelay = onTestGroupDelay,
                )
            }
        }
    }
}

@Composable
internal fun RuntimeCard(
    state: CoreRuntimeStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    uptimeMillis: Long? = null,
    outboundIp: OutboundIpUiState = OutboundIpUiState(),
    onRefreshIp: () -> Unit = {},
    onRefreshIpQuality: () -> Unit = {},
    onDemandSuspended: Boolean = false,
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
            // 作者 long：主控背景只作装饰，出口信息由独立状态提供，不能从运行状态推测国家或质量。
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
                DashboardHeader("状态", Icons.Outlined.Tune)
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
                                    state.isRunning && onDemandSuspended -> "按需暂停"
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
                        OutboundIpPanel(outboundIp, state.isRunning, onRefreshIp, onRefreshIpQuality)
                        Column {
                            Text("运行时间", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            Text(
                                formatRuntimeDuration(uptimeMillis.takeIf { state.isRunning }),
                                Modifier.testTag("dashboard_runtime_duration"),
                                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            )
                        }
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
        // 作者 long：Flutter 手机网络速度卡只展示上下行，连接数量属于默认隐藏的连接统计卡。
        DashboardHeader("网络速度", Icons.Outlined.Speed)
        val fontScale = LocalDensity.current.fontScale
        val history = if (active) state.trafficHistory else emptyList()
        val upload = history.map { it.up }
        val download = history.map { it.down }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            // 作者 long：大字号时将速度块纵排，保持数值与单位完整，避免两栏压缩后只能读到省略号。
            if (fontScale > 1.4f || maxWidth < 260.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardSpeedTile("上传", Icons.Outlined.ArrowUpward, if (active) state.traffic.up else 0, if (active) state.totalTraffic.up else 0, upload, "dashboard_upload_history", Modifier.fillMaxWidth())
                    DashboardSpeedTile("下载", Icons.Outlined.ArrowDownward, if (active) state.traffic.down else 0, if (active) state.totalTraffic.down else 0, download, "dashboard_download_history", Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardSpeedTile("上传", Icons.Outlined.ArrowUpward, if (active) state.traffic.up else 0, if (active) state.totalTraffic.up else 0, upload, "dashboard_upload_history", Modifier.weight(1f))
                    DashboardSpeedTile("下载", Icons.Outlined.ArrowDownward, if (active) state.traffic.down else 0, if (active) state.totalTraffic.down else 0, download, "dashboard_download_history", Modifier.weight(1f))
                }
            }
        }
        if (state.isLoading && active) CircularProgressIndicator(Modifier.padding(top = 10.dp).size(18.dp), strokeWidth = 2.dp)
        state.error?.let { Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun DashboardConnectionStatsCard(
    connectionCount: Int,
    requestCount: Int,
    dnsCount: Int,
    onConnections: () -> Unit,
    onRequests: () -> Unit,
    onDns: () -> Unit,
) {
    val entries = listOf(
        Triple("连接", connectionCount, onConnections),
        Triple("请求", requestCount, onRequests),
        Triple("DNS", dnsCount, onDns),
    )
    Card(
        modifier = Modifier.fillMaxWidth().testTag("dashboard_connection_stats_card"),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = dashboardCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
        ) {
            entries.forEachIndexed { index, (label, count, onClick) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 66.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(onClick = onClick)
                        .semantics {
                            role = Role.Button
                            contentDescription = label + "，" + dashboardCount(count) + " 条"
                        }
                        .testTag(
                            when (index) {
                                0 -> "dashboard_connection_stats_connections"
                                1 -> "dashboard_connection_stats_requests"
                                else -> "dashboard_connection_stats_dns"
                            },
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        dashboardCount(count),
                        style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                    Text(
                        label,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun dashboardCount(count: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(count)

@Composable
internal fun OutboundModeCard(
    mode: CoreOutboundMode,
    onModeChange: (CoreOutboundMode) -> Unit,
) {
    val options = listOf(
        Triple(CoreOutboundMode.RULE, "规则", DashboardModeGlyphs.rules),
        Triple(CoreOutboundMode.GLOBAL, "全局", DashboardModeGlyphs.proxies),
        Triple(CoreOutboundMode.DIRECT, "直连", DashboardModeGlyphs.target),
    )
    DashboardSurface(Modifier.testTag("dashboard_outbound_mode_card")) {
        DashboardHeader("出站模式", DashboardModeGlyphs.split)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEachIndexed { index, (option, label, icon) ->
                val selected = mode == option
                // 作者 long：三个模式各自着色，端部保留大圆角；选中时内侧也展开成胶囊，避免额外底轨挤压按钮。
                val easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
                val startRadius by animateDpAsState(
                    if (selected || index == 0) 26.dp else 12.dp, tween(240, easing = easing), label = "modeStartRadius",
                )
                val endRadius by animateDpAsState(
                    if (selected || index == options.lastIndex) 26.dp else 12.dp, tween(240, easing = easing), label = "modeEndRadius",
                )
                val background by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else dashboardBlockColor(),
                    tween(240, easing = easing), label = "modeBackground",
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { if (!selected) onModeChange(option) },
                        )
                        .semantics { contentDescription = label }
                        .testTag("dashboard_mode_${option.wireValue}"),
                    shape = RoundedCornerShape(startRadius, endRadius, endRadius, startRadius),
                    color = background,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Icon(icon, contentDescription = null, Modifier.size(18.dp))
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSpeedTile(label: String, icon: ImageVector, speed: Long, total: Long, samples: List<Long>, chartTag: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = dashboardBlockColor()) {
        Column {
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
            DashboardTrafficChart(label, samples, chartTag)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DashboardProfileCard(
    profile: Profile?,
    profiles: List<Profile> = listOfNotNull(profile),
    currentProfileId: Long? = profile?.id,
    isBusy: Boolean = false,
    runtimeState: CoreRuntimeStatus = CoreRuntimeStatus.STOPPED,
    onShowProfiles: () -> Unit,
    onAddProfile: () -> Unit,
    onSelectProfile: (Long) -> Unit = {},
    onUpdateProfile: (Profile) -> Unit = {},
    onStopRuntime: () -> Unit = {},
    onPreviewConfig: (Profile) -> Unit = {},
    statistics: ProfileStatisticsUiState = ProfileStatisticsUiState(),
    onDetailVisible: (Long?) -> Unit = {},
    onRetryStatistics: () -> Unit = {},
    providers: ProfileProvidersUiState = ProfileProvidersUiState(),
    onRetryProviders: () -> Unit = {},
    onUpdateProvider: (String) -> Unit = {},
    onOpenDetail: ((Profile) -> Unit)? = null,
) {
    var showProfilePicker by remember { mutableStateOf(false) }
    var showProfileDetail by rememberSaveable(profile?.id) { mutableStateOf(false) }
    DisposableEffect(showProfileDetail, profile?.id) {
        onDetailVisible(profile?.id?.takeIf { showProfileDetail })
        onDispose { onDetailVisible(null) }
    }
    val hasProfiles = profiles.isNotEmpty()
    // 作者 long：选择与同步后的应用交给管理器串行处理，稳定运行态也可以更新订阅。
    val runtimeTransitioning = runtimeState == CoreRuntimeStatus.STARTING ||
        runtimeState == CoreRuntimeStatus.STOPPING
    val canSelectProfile = !isBusy
    val canSyncProfile = !isBusy && !runtimeTransitioning

    DashboardSurface(
        modifier = Modifier.testTag("dashboard_profile_card"),
        onClick = {
            when {
                profile != null -> if (onOpenDetail != null) onOpenDetail(profile) else { showProfileDetail = true }
                hasProfiles -> showProfilePicker = true
                else -> onAddProfile()
            }
        },
    ) {
        DashboardHeader("配置", Icons.Outlined.Description)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile?.realLabel ?: "还没有配置", style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = profile?.let(::dashboardProfileSummary) ?: if (hasProfiles) "请选择配置" else "添加一个配置，开始连接",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (profile?.type == ProfileType.URL) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).testTag("dashboard_profile_sync_loading"),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = { onUpdateProfile(profile) },
                        enabled = canSyncProfile,
                        modifier = Modifier.testTag("dashboard_profile_sync"),
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = "同步当前配置")
                    }
                }
            }
            if (profiles.size > 1) {
                IconButton(
                    onClick = { showProfilePicker = true },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("dashboard_profile_switch"),
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "切换配置")
                }
            }
            TextButton(
                onClick = when {
                    profile != null -> onShowProfiles
                    hasProfiles -> ({ showProfilePicker = true })
                    else -> onAddProfile
                },
                enabled = profile != null || hasProfiles && canSelectProfile || !hasProfiles,
                modifier = Modifier.testTag("dashboard_profile_action"),
            ) {
                Text(if (profile == null && !hasProfiles) "添加配置" else if (profile == null) "选择配置" else "查看配置")
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
            }
        }
        profile?.subscriptionInfo?.takeIf { it.usage != null }?.let { info ->
            val used = info.upload + info.download
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已用 ${formatBytes(used)}", style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("共 ${formatBytes(info.total)}", style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .testTag("dashboard_profile_usage_track"),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(info.usage ?: 0f)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        profile?.subscriptionInfo?.expireAtMillis?.let { expiry ->
            Text(
                "到期 ${DateUtils.getRelativeTimeSpanString(expiry)}",
                modifier = Modifier.padding(top = 6.dp).testTag("dashboard_profile_expiry"),
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profile?.lastUpdateAt?.let { updatedAt ->
            Text(
                "更新于 ${DateUtils.getRelativeTimeSpanString(updatedAt)}",
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showProfilePicker) {
        ModalBottomSheet(
            onDismissRequest = { showProfilePicker = false },
            modifier = Modifier.testTag("dashboard_profile_picker"),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("切换配置", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                if (runtimeState.isRunning) Text(
                    "切换配置会立即应用到当前连接",
                    modifier = Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 560.dp)) {
                    items(profiles, key = Profile::id) { item ->
                        val selected = item.id == currentProfileId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable(enabled = canSelectProfile) {
                                    // 作者 long：切换立即关闭面板，配置应用与运行态校验统一由 ProfilesViewModel 处理。
                                    if (item.id != currentProfileId) onSelectProfile(item.id)
                                    showProfilePicker = false
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                .testTag("dashboard_profile_option_${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (selected) Icon(Icons.Outlined.Check, contentDescription = "当前配置", tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.realLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(dashboardProfileSummary(item), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDetail && profile != null) {
        DashboardProfileDetailSheet(
            profile = profile,
            canMutate = canSyncProfile,
            isBusy = isBusy,
            onDismiss = { showProfileDetail = false },
            onShowProfiles = {
                showProfileDetail = false
                onShowProfiles()
            },
            onUpdate = { onUpdateProfile(profile) },
            statistics = statistics.takeIf { it.profileId == profile.id } ?: ProfileStatisticsUiState(profile.id, isLoading = true),
            onRetryStatistics = onRetryStatistics,
            providers = providers.takeIf { it.profileId == profile.id } ?: ProfileProvidersUiState(profile.id, isLoading = true),
            onRetryProviders = onRetryProviders,
            onUpdateProvider = onUpdateProvider,
            onPreview = {
                showProfileDetail = false
                onPreviewConfig(profile)
            },
        )
    }
}

/**
 * 作者 long：配置详情沿Flutter使用完整页面承载统计、订阅与外部资源，长列表和嵌套编辑共享完整手机视口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardProfileDetailSheet(
    profile: Profile,
    canMutate: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onShowProfiles: () -> Unit,
    onUpdate: () -> Unit,
    onPreview: () -> Unit,
    statistics: ProfileStatisticsUiState,
    onRetryStatistics: () -> Unit,
    providers: ProfileProvidersUiState,
    onRetryProviders: () -> Unit,
    onUpdateProvider: (String) -> Unit,
    onUpdateAllProviders: () -> Unit = {},
    onRetryFailedProviders: () -> Unit = {},
    onPreviewProvider: (String) -> Unit = {},
    onEditProvider: (String) -> Unit = {},
    onUploadProvider: (String) -> Unit = {},
) {
    ProfileEditorPage(
        onDismissRequest = onDismiss,
        title = { Text(profile.realLabel, modifier = Modifier.testTag("dashboard_profile_detail_name")) },
        backTag = "dashboard_profile_detail_back",
        confirmButton = {
            IconButton(onPreview, modifier = Modifier.testTag("dashboard_profile_detail_preview")) {
                Icon(Icons.Outlined.Description, "运行配置")
            }
            if (profile.type == ProfileType.URL) {
                if (isBusy) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp).testTag("dashboard_profile_detail_sync_loading"), strokeWidth = 2.dp)
                else IconButton(onUpdate, enabled = canMutate, modifier = Modifier.testTag("dashboard_profile_detail_sync")) { Icon(Icons.Outlined.Sync, "同步") }
            }
        }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("dashboard_profile_detail_sheet")
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ProfileStatisticsPanel(statistics, onRetryStatistics)
            profile.subscriptionInfo?.takeIf { it.total > 0 }?.let { info ->
                Text("订阅用量", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val upload = info.upload.coerceAtLeast(0)
                        val download = info.download.coerceAtLeast(0)
                        val used = if (upload > Long.MAX_VALUE - download) Long.MAX_VALUE else upload + download
                        Text("已用 ${formatBytes(used)} / ${formatBytes(info.total)}")
                        androidx.compose.material3.LinearProgressIndicator(progress = { ((upload.toDouble() + download) / info.total).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                        Text(info.expireAtMillis?.let { "到期 ${DateUtils.getRelativeTimeSpanString(it)}" } ?: "不限到期时间",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("配置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("来源", modifier = Modifier.weight(1f))
                        Text(dashboardProfileSourceLabel(profile), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("dashboard_profile_detail_source"))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("更新时间", modifier = Modifier.weight(1f))
                        Text(profile.lastUpdateAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "尚未更新", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (profile.type != ProfileType.CUSTOM) Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("扩展模式", modifier = Modifier.weight(1f))
                        Text(if (profile.extendType == "script") "脚本" else "标准", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("dashboard_profile_detail_extension"))
                    }
                }
            }
            ProfileProvidersPanel(providers, onRetryProviders, onUpdateProvider, onUpdateAllProviders, onRetryFailedProviders,
                onPreviewProvider, onEditProvider, onUploadProvider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onShowProfiles,
                    modifier = Modifier.testTag("dashboard_profile_detail_open_profiles"),
                ) { Text("查看全部配置") }
            }
        }
    })
}

private fun dashboardProfileSourceLabel(profile: Profile): String = when (profile.type) {
    ProfileType.URL -> "远程订阅"
    ProfileType.FILE -> "本地文件"
    ProfileType.CUSTOM -> "自定义配置"
}

private fun dashboardProfileSummary(profile: Profile): String {
    val source = when (profile.type) {
        ProfileType.URL -> "远程订阅"
        ProfileType.FILE -> "本地文件"
        ProfileType.CUSTOM -> "自定义配置"
    }
    val info = profile.subscriptionInfo
    val usage = info?.takeIf { it.usage != null }?.let {
        "${formatBytes(it.upload + it.download)} / ${formatBytes(it.total)}"
    }
    val updated = profile.lastUpdateAt?.let { "更新于 ${DateUtils.getRelativeTimeSpanString(it)}" }
    return listOfNotNull(source, usage, updated).joinToString(" · ")
}
