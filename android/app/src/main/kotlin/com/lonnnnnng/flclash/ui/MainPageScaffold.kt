package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class MainDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("首页", Icons.Outlined.Home),
    PROXY("代理", Icons.Outlined.SwapVert),
    PROFILES("配置", Icons.Outlined.Description),
    TOOLS("工具", Icons.Outlined.Build),
}

/** 作者 long：Flutter 手机导航在没有可用 Profile/代理组时隐藏代理入口，空配置页不展示不可操作的 Tab。 */
internal fun mainDestinations(hasProxyDestination: Boolean): List<MainDestination> = buildList {
    add(MainDestination.DASHBOARD)
    if (hasProxyDestination) add(MainDestination.PROXY)
    add(MainDestination.PROFILES)
    add(MainDestination.TOOLS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainPageScaffold(
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
    destinations: List<MainDestination> = MainDestination.entries,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    topBarTitle: String? = null,
    topBarNavigationIcon: @Composable (() -> Unit)? = null,
    showTopBar: Boolean = true,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val pageColor = when (selectedDestination) {
        MainDestination.DASHBOARD -> dashboardPageColor()
        MainDestination.PROXY -> proxyPageColor()
        MainDestination.PROFILES -> dashboardPageColor()
        else -> MaterialTheme.colorScheme.surface
    }
    Scaffold(
        modifier = modifier,
        containerColor = pageColor,
        topBar = {
            if (showTopBar) TopAppBar(
                title = { Text(topBarTitle ?: if (selectedDestination == MainDestination.DASHBOARD) "仪表盘" else selectedDestination.label) },
                navigationIcon = topBarNavigationIcon ?: {},
                actions = topBarActions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pageColor),
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = { MainNavigationDock(selectedDestination, destinations, onDestinationSelected) },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

// 作者 long：正式页面和固定截图宿主共用宽度策略，横屏保持 Flutter 手机 Dashboard 的 560dp 内容宽度。
@Composable
internal fun DashboardWidth(content: @Composable (horizontalInset: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        content(maxOf(16.dp, (maxWidth - 560.dp) / 2))
    }
}

@Composable
private fun MainNavigationDock(
    selected: MainDestination,
    destinations: List<MainDestination>,
    onSelected: (MainDestination) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fontScale = LocalDensity.current.fontScale
    // 作者 long：保留参考 Dock 的 62dp、21dp 边距与 28dp 图标；字体增大时增加高度，防止标签侵入系统手势区。
    val height = maxOf(62.dp, (28 + 2 + 14.5f * fontScale + 8).dp)
    Box(
        Modifier.fillMaxWidth().padding(start = 21.dp, end = 21.dp, top = 8.dp, bottom = maxOf(21.dp, bottomInset)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("main_navigation_dock"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(4.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                destinations.forEach { destination ->
                    val active = destination == selected
                    Surface(
                        Modifier.weight(1f).clip(CircleShape).selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSelected(destination) },
                        ).testTag("nav_${destination.name.lowercase()}"),
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Column(
                            Modifier.heightIn(min = height - 8.dp).padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Icon(destination.icon, contentDescription = null, Modifier.size(28.dp))
                            Text(destination.label, style = TextStyle(fontSize = 10.sp, lineHeight = 14.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}
