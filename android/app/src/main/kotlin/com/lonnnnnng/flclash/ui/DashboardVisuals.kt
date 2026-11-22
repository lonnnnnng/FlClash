package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
internal fun dashboardPageColor(): Color = if (LocalEmbeddedProfilePage.current != null) {
    MaterialTheme.colorScheme.surfaceContainer
} else if (LocalKClashDarkTheme.current) {
    MaterialTheme.colorScheme.surface
} else {
    MaterialTheme.colorScheme.surfaceContainer
}

@Composable
internal fun dashboardCardColor(): Color = if (LocalEmbeddedProfilePage.current != null && LocalKClashDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainerHigh
} else if (LocalKClashDarkTheme.current) {
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
internal fun DashboardSkeletonBar(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 12.dp,
    tag: String? = null,
) {
    Box(
        modifier = Modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f))
            .then(modifier)
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
    )
}

@Composable
internal fun DashboardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .appDashboardShadow()
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .appFocusRing(AppSuperellipseShape(34.dp))
                        // 作者 long：首页卡片本身就是 Flutter 手机端的进入详情入口，
                        // 保留内部同步、切换等按钮的独立点击语义。
                        .clickable(onClick = onClick)

                },
            ),
        shape = AppSuperellipseShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = dashboardCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), content = content)
    }
}

@Composable
private fun DashboardHeader(
    title: String,
    icon: ImageVector,
    height: Dp = 20.dp,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(height),
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
        DashboardHeader(tr("策略组"), Icons.Outlined.Layers, height = 26.dp) {
            if (groups.isNotEmpty()) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(44.dp)
                .testTag(if (groups.isEmpty()) "dashboard_proxy_groups_empty" else "dashboard_proxy_groups_summary"),
            contentAlignment = Alignment.CenterStart,
        ) {
            when {
                state.isLoading && groups.isEmpty() -> Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DashboardSkeletonBar(Modifier.testTag("dashboard_proxy_groups_loading_count"), 44.dp, 24.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { DashboardSkeletonBar(Modifier.size(32.dp), 32.dp, 32.dp) }
                    }
                }
                state.error != null && groups.isEmpty() -> Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr(state.error), Modifier.weight(1f).testTag("dashboard_proxy_groups_error"),
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = onRefresh,
                        enabled = state.canRefresh(runtimeState),
                        modifier = Modifier.size(32.dp).testTag("dashboard_proxy_groups_retry"),
                    ) { Icon(Icons.Outlined.Sync, tr("重试"), Modifier.size(18.dp)) }
                }
                groups.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        tr("没有代理组"),
                        style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    )
                    if (currentProfileId == null) Text(
                        tr("添加有效配置后显示代理组"),
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text(
                            groups.size.toString(),
                            Modifier.testTag("dashboard_proxy_groups_count"),
                            style = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                        Text(tr(" 组"), Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    val shown = groups.take(4)
                    val rest = groups.size - shown.size
                    val badgeCount = shown.size + if (rest > 0) 1 else 0
                    Box(Modifier.width((badgeCount * 36 - (badgeCount - 1) * 8).dp).height(36.dp)) {
                        repeat(badgeCount) { index ->
                            Box(
                                Modifier.offset(x = (index * 28).dp).size(36.dp)
                                    .border(3.dp, dashboardCardColor(), AppSuperellipseShape(13.dp))
                                    .padding(3.dp).background(dashboardBlockColor(), AppSuperellipseShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 作者 long：摘要只缩放头像，不裁掉完整代理组名或改变卡片的内容高度。
                                Text(
                                    if (index < shown.size) groupInitial(shown[index].name) else "+$rest",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
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

            isContentAtTop = { contentAtTop },
            avoidIme = true,
        ) { requestDismiss ->
            Column(Modifier.fillMaxSize().testTag("dashboard_proxy_picker")) {
                DashboardSheetHeader(tr("代理"), "dashboard_proxy_picker_close", requestDismiss)
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
private fun RuntimeStateChip(state: CoreRuntimeStatus, suspended: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = when {
            state == CoreRuntimeStatus.FAILED -> colors.errorContainer
            state.isRunning -> colors.primaryContainer
            else -> dashboardBlockColor()
        },
        shape = CircleShape,
    ) {
        Text(
            when {
                state.isRunning && suspended -> tr("按需暂停")
                state.isRunning -> tr("运行中")
                state == CoreRuntimeStatus.STOPPED -> tr("未运行")
                state == CoreRuntimeStatus.FAILED -> tr("启动失败")
                state == CoreRuntimeStatus.STOPPING -> tr("停止中")
                else -> tr("启动中")
            },
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
private fun DashboardRuntimeCell(label: String, value: String, tag: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            value,
            Modifier.testTag(tag),
            style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RuntimeCard(
    state: CoreRuntimeStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    canStart: Boolean,
    uptimeMillis: Long? = null,
    outboundIp: OutboundIpUiState = OutboundIpUiState(),
    onRefreshIp: () -> Unit = {},
    onRefreshIpQuality: () -> Unit = {},
    onCopyOutboundText: ((String, String) -> Unit)? = null,
    onDemandSuspended: Boolean = false,
) {
    val busy = state == CoreRuntimeStatus.STARTING || state == CoreRuntimeStatus.STOPPING
    // 作者 long：停止动作只依赖核心状态；启动动作必须有已加载且选中的配置。
    val canToggle = !busy && (state.isRunning || canStart)
    val toggleShape = rememberRuntimeToggleShape(state.isRunning)
    val colors = MaterialTheme.colorScheme
    val tone = dashboardBlockColor()
    Card(
        Modifier.fillMaxWidth().testTag("dashboard_runtime_card").appDashboardShadow(),
        shape = AppSuperellipseShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = dashboardCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        AppLiveMaterialHost(Modifier.fillMaxWidth().heightIn(min = 188.dp), background = {
            Box(Modifier.matchParentSize().background(dashboardCardColor()))
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
        }, foreground = {
            Box(Modifier.fillMaxSize().padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 18.dp)) {
                Column(Modifier.fillMaxWidth().padding(end = 98.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(26.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("状态"), style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold))
                        RuntimeStateChip(state, onDemandSuspended)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            OutboundIpPanel(outboundIp, state.isRunning, onRefreshIp, onRefreshIpQuality, onCopyOutboundText)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DashboardRuntimeCell(
                                    label = tr("运行时长"),
                                    value = formatRuntimeDuration(uptimeMillis.takeIf { state.isRunning }),
                                    tag = "dashboard_runtime_duration",
                                    modifier = Modifier.weight(1f),
                                )
                                DashboardRuntimeCell(
                                    label = tr("组织"),
                                    value = outboundIp.info?.let { outboundIp.quality?.organization ?: outboundIp.quality?.asn?.let { asn -> "AS$asn" } } ?: "—",
                                    tag = "dashboard_runtime_organization",
                                    modifier = Modifier.weight(1.2f),
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = if (state.isRunning) onStop else onStart,
                    enabled = canToggle,
                    modifier = Modifier.align(Alignment.CenterEnd).size(88.dp).testTag("dashboard_runtime_toggle")
                        .appElasticPress(enabled = canToggle)
                        .appLiveButton(toggleShape, AppGlassStyle.tinted(LocalKClashDarkTheme.current,
                            if (state.isRunning) colors.primary else colors.primaryContainer), canToggle)
                        .appFocusRing(toggleShape, standsOff = true),
                    shape = toggleShape,
                    color = if (LocalAppMenuGlass.current && LocalAppLiveBackdrop.current != null) Color.Transparent
                        else if (!canToggle) colors.surfaceVariant
                        else if (state.isRunning) colors.primary else colors.primaryContainer,
                    contentColor = if (!canToggle) colors.onSurfaceVariant
                        else if (state.isRunning) colors.onPrimary else colors.onPrimaryContainer,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RuntimeToggleGlyph(state.isRunning, state.isRunning && onDemandSuspended, busy)
                    }
                }
            }
        })
    }
}

@Composable
internal fun RuntimeStatsCard(state: RuntimeStatsUiState, runtimeState: CoreRuntimeStatus) {
    val active = runtimeState.isRunning
    val loading = active && state.isLoading && state.trafficHistory.isEmpty()
    val error = state.error
    DashboardSurface(Modifier.testTag("dashboard_stats_card")) {
        // 作者 long：Flutter 手机网络速度卡只展示上下行，连接数量属于默认隐藏的连接统计卡。
        DashboardHeader(tr("网络速度"), Icons.Outlined.Speed, height = 26.dp)
        val fontScale = LocalDensity.current.fontScale
        val history = if (active) state.trafficHistory else emptyList()
        val upload = history.map { it.up }
        val download = history.map { it.down }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            // 作者 long：大字号时将速度块纵排，保持数值与单位完整，避免两栏压缩后只能读到省略号。
            if (fontScale > 1.4f || maxWidth < 260.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardSpeedTile(tr("上传"), Icons.Outlined.ArrowUpward, if (active) state.traffic.up else 0, if (active) state.totalTraffic.up else 0, upload, "dashboard_upload_history", Modifier.fillMaxWidth(), loading, error)
                    DashboardSpeedTile(tr("下载"), Icons.Outlined.ArrowDownward, if (active) state.traffic.down else 0, if (active) state.totalTraffic.down else 0, download, "dashboard_download_history", Modifier.fillMaxWidth(), loading, error)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardSpeedTile(tr("上传"), Icons.Outlined.ArrowUpward, if (active) state.traffic.up else 0, if (active) state.totalTraffic.up else 0, upload, "dashboard_upload_history", Modifier.weight(1f), loading, error)
                    DashboardSpeedTile(tr("下载"), Icons.Outlined.ArrowDownward, if (active) state.traffic.down else 0, if (active) state.totalTraffic.down else 0, download, "dashboard_download_history", Modifier.weight(1f), loading, error)
                }
            }
        }
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
        Triple(tr("连接"), connectionCount, onConnections),
        Triple(tr("请求"), requestCount, onRequests),
        Triple("DNS", dnsCount, onDns),
    )
    Card(
        modifier = Modifier.fillMaxWidth().testTag("dashboard_connection_stats_card").appDashboardShadow(),
        shape = AppSuperellipseShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = dashboardCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
        ) {
            entries.forEachIndexed { index, (label, count, onClick) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(66.dp)
                        .appFocusRing(AppSuperellipseShape(26.dp))
                        .clip(AppSuperellipseShape(26.dp))
                        .clickable(onClick = onClick)
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
                        softWrap = false,
                        overflow = TextOverflow.Clip,
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

private fun dashboardCount(count: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(count)

@Composable
internal fun OutboundModeCard(
    mode: CoreOutboundMode,
    onModeChange: (CoreOutboundMode) -> Unit,
) {
    val options = listOf(
        Triple(CoreOutboundMode.RULE, tr("规则"), DashboardModeGlyphs.rules),
        Triple(CoreOutboundMode.GLOBAL, tr("全局"), DashboardModeGlyphs.proxies),
        Triple(CoreOutboundMode.DIRECT, tr("直连"), DashboardModeGlyphs.target),
    )
    var lastPressed by remember { mutableStateOf(-1) }
    DashboardSurface(Modifier.testTag("dashboard_outbound_mode_card")) {
        DashboardHeader(tr("出站模式"), DashboardModeGlyphs.split)
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
                val shape = AppSuperellipseShape(startRadius, endRadius, endRadius, startRadius)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .zIndex(if (lastPressed == index) 1f else 0f)
                        .appElasticPress(onPress = { lastPressed = index })
                        .appFocusRing(shape, standsOff = selected)
                        .selectable(
                            selected = selected,
                            onClick = { if (!selected) onModeChange(option) },
                        )
                        .testTag("dashboard_mode_${option.wireValue}"),
                    shape = shape,
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
private fun DashboardSpeedTile(
    label: String,
    icon: ImageVector,
    speed: Long,
    total: Long,
    samples: List<Long>,
    chartTag: String,
    modifier: Modifier,
    loading: Boolean,
    error: String?,
) {
    val direction = if (label == tr("上传")) "upload" else "download"
    Surface(
        modifier.aspectRatio(1.45f).testTag("dashboard_speed_tile_$direction"),
        shape = AppSuperellipseShape(22.dp),
        color = dashboardBlockColor(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val chartHeight = (maxHeight - 96.dp).coerceIn(12.dp, 30.dp)
            Column(Modifier.fillMaxSize().padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(label, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
                    when {
                        loading -> DashboardSkeletonBar(Modifier.testTag("dashboard_speed_${direction}_loading"), 86.dp, 20.dp)
                        error != null && samples.isEmpty() -> Text(
                            "—", Modifier.testTag("dashboard_speed_value_$chartTag"),
                            style = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                        else -> {
                            val formatted = formatBytes(speed)
                            val numericEnd = formatted.indexOfFirst { it.isLetter() }.takeIf { it >= 0 } ?: formatted.length
                            val number = formatted.take(numericEnd).trimEnd()
                            val unit = formatted.drop(numericEnd)
                            // 作者 long：主值与速率单位沿用 Flutter 的字级分层，单位缩小后仍留在固定数值槽内。
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")) {
                                        append(number)
                                    }
                                    withStyle(SpanStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )) {
                                        append(" $unit/s")
                                    }
                                },
                                Modifier.testTag("dashboard_speed_value_$chartTag"),
                                style = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                    if (loading) DashboardSkeletonBar(width = 72.dp, height = 10.dp)
                    else Text(tr("累计 {0}", formatBytes(total)), Modifier.testTag("dashboard_speed_total_$chartTag"), style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(chartHeight), contentAlignment = Alignment.CenterStart) {
                    when {
                        loading -> DashboardSkeletonBar(Modifier.testTag("dashboard_speed_chart_loading_$chartTag"), height = 8.dp)
                        error != null && samples.isEmpty() && direction == "upload" -> Text(
                            tr(error), Modifier.fillMaxWidth().testTag("dashboard_speed_error"),
                            style = TextStyle(fontSize = 10.sp, lineHeight = 13.sp),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        else -> DashboardTrafficChart(samples, chartTag)
                    }
                }
            }
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
    val profileStatistics = statistics.counts?.takeIf { it.profileId == profile?.id }
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
        DashboardHeader(tr("配置"), Icons.Outlined.Description) {
            if (profile?.type == ProfileType.URL) {
                IconButton(onClick = { onUpdateProfile(profile) }, enabled = canSyncProfile,
                    modifier = Modifier.size(36.dp).testTag("dashboard_profile_sync")) {
                    if (isBusy) CircularProgressIndicator(Modifier.size(18.dp).testTag("dashboard_profile_sync_loading"), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Sync, null, Modifier.size(20.dp))
                }
            }
            IconButton(onClick = when {
                profile != null -> onShowProfiles
                hasProfiles -> ({ showProfilePicker = true })
                else -> onAddProfile
            }, enabled = profile != null || !isBusy, modifier = Modifier.size(36.dp).testTag("dashboard_profile_action")) {
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
            }
        }
        // 作者 long：同步与页面入口回到卡片标题栏，名称行只保留紧邻的快切，长名称不再与三个操作争宽。
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(profile?.realLabel ?: tr("还没有配置"), Modifier.weight(1f, fill = false).testTag("dashboard_profile_name"),
                style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (profiles.size > 1) IconButton(onClick = { showProfilePicker = true }, enabled = !isBusy,
                modifier = Modifier.size(32.dp).testTag("dashboard_profile_switch")) { Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(18.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(profile?.lastUpdateAt?.let { tr("更新于 {0}", DateUtils.getRelativeTimeSpanString(it)) }
                ?: if (profile != null) "" else if (hasProfiles) tr("请选择配置") else tr("添加一个配置，开始连接"),
                Modifier.weight(1f), style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            profile?.let {
                val expiry = it.subscriptionInfo?.expireAtMillis
                Text(if (expiry == null) profileSourceLabel(it) else tr("到期 {0}", DateUtils.getRelativeTimeSpanString(expiry)),
                    Modifier.then(if (expiry != null) Modifier.testTag("dashboard_profile_expiry") else Modifier),
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (profile != null) {
            Spacer(Modifier.height(8.dp))
            // 作者 long：统计槽按 Flutter 手机上游卡片留出同等高度，加载和订阅数据缺失时仍保持卡片几何稳定。
            Box(Modifier.fillMaxWidth().height(43.dp).testTag("dashboard_profile_usage_slot")) {
                val info = profile.subscriptionInfo?.takeIf { it.usage != null }
                when {
                    info != null -> Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("已用 {0}", formatBytes(info.upload + info.download)), style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(tr("共 {0}", formatBytes(info.total)), style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .testTag("dashboard_profile_usage_track"),
                        ) {
                            Box(Modifier.fillMaxWidth(info.usage ?: 0f).height(4.dp).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    profileStatistics != null -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 作者 long：无订阅用量时保留首页三项配置统计，和上游手机卡片的内容结构一致。
                        listOf(
                            Triple(dashboardCount(profileStatistics.groups), tr("策略组"), 0),
                            Triple(dashboardCount(profileStatistics.proxies), tr("代理"), 1),
                            Triple(dashboardCount(profileStatistics.rules), tr("规则"), 2),
                        ).forEach { (value, label, index) ->
                            Column {
                                Text(value, Modifier.testTag("dashboard_profile_metric_value_$index"),
                                    style = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                                Text(label, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                                    modifier = Modifier.testTag("dashboard_profile_metric_label_$index"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    profile.type == ProfileType.URL -> Column(
                        Modifier.fillMaxWidth().align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(3) { index ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DashboardSkeletonBar(Modifier.then(if (index == 0) Modifier.testTag("dashboard_profile_metric_loading") else Modifier), 32.dp, 16.dp)
                                    DashboardSkeletonBar(width = 40.dp, height = 10.dp)
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

    }

    if (showProfilePicker) {
        val pickerList = rememberLazyListState()
        var selectionSubmitted by remember { mutableStateOf(false) }
        DashboardModalSheet(title = tr("切换配置"), sheetTag = "dashboard_profile_picker", closeTag = "dashboard_profile_picker_close",
            onDismiss = { showProfilePicker = false }, scrollable = false,
            fitsContent = !pickerList.canScrollForward && !pickerList.canScrollBackward,
            isContentAtTop = { pickerList.firstVisibleItemIndex == 0 && pickerList.firstVisibleItemScrollOffset == 0 },
        ) { requestDismiss ->
                if (runtimeState.isRunning) Text(
                    tr("切换配置会立即应用到当前连接"),
                    modifier = Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(), state = pickerList,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(profiles, key = Profile::id) { item ->
                        val selected = item.id == currentProfileId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppSuperellipseShape(24.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else dashboardCardColor())
                                .selectable(selected, enabled = canSelectProfile && !selectionSubmitted, ) {
                                    // 作者 long：关闭动画期间只认领一次切换，配置应用与运行态校验仍统一由 ProfilesViewModel 处理。
                                    selectionSubmitted = true
                                    if (item.id != currentProfileId) onSelectProfile(item.id)
                                    requestDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("dashboard_profile_option_${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.realLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))
                                Text(profileSubtitle(item), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Surface(Modifier.size(24.dp).testTag("dashboard_profile_option_selected_${item.id}"),
                                shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                Box(contentAlignment = Alignment.Center) { AppGlyphIcon(AppGlyphs.check, Modifier.size(18.dp), filled = true) }
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
                Icon(Icons.Outlined.Description, null)
            }
            if (profile.type == ProfileType.URL) {
                if (isBusy) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp).testTag("dashboard_profile_detail_sync_loading"), strokeWidth = 2.dp)
                else IconButton(onUpdate, enabled = canMutate, modifier = Modifier.testTag("dashboard_profile_detail_sync")) { Icon(Icons.Outlined.Sync, null) }
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
                Text(tr("订阅用量"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Surface(shape = AppSuperellipseShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val upload = info.upload.coerceAtLeast(0)
                        val download = info.download.coerceAtLeast(0)
                        val used = if (upload > Long.MAX_VALUE - download) Long.MAX_VALUE else upload + download
                        Text(tr("已用 {0} / {1}", formatBytes(used), formatBytes(info.total)))
                        androidx.compose.material3.LinearProgressIndicator(progress = { ((upload.toDouble() + download) / info.total).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                        Text(info.expireAtMillis?.let { tr("到期 {0}", DateUtils.getRelativeTimeSpanString(it)) } ?: tr("不限到期时间"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(tr("配置"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Surface(shape = AppSuperellipseShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("来源"), modifier = Modifier.weight(1f))
                        Text(dashboardProfileSourceLabel(profile), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("dashboard_profile_detail_source"))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("更新时间"), modifier = Modifier.weight(1f))
                        Text(profile.lastUpdateAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: tr("尚未更新"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (profile.type != ProfileType.CUSTOM) Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("扩展模式"), modifier = Modifier.weight(1f))
                        Text(if (profile.extendType == "script") tr("脚本") else tr("标准"), color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ) { Text(tr("查看全部配置")) }
            }
        }
    })
}

private fun dashboardProfileSourceLabel(profile: Profile): String = when (profile.type) {
    ProfileType.URL -> tr("远程订阅")
    ProfileType.FILE -> tr("本地文件")
    ProfileType.CUSTOM -> tr("自定义配置")
}

private fun dashboardProfileSummary(profile: Profile): String {
    val source = when (profile.type) {
        ProfileType.URL -> tr("远程订阅")
        ProfileType.FILE -> tr("本地文件")
        ProfileType.CUSTOM -> tr("自定义配置")
    }
    val info = profile.subscriptionInfo
    val usage = info?.takeIf { it.usage != null }?.let {
        "${formatBytes(it.upload + it.download)} / ${formatBytes(it.total)}"
    }
    val updated = profile.lastUpdateAt?.let { tr("更新于 {0}", DateUtils.getRelativeTimeSpanString(it)) }
    return listOfNotNull(source, usage, updated).joinToString(" · ")
}
