package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

internal enum class MainDestination(val label: String) {
    DASHBOARD("首页"), PROXY("代理"), PROFILES("配置"), TOOLS("工具");

    // 作者 long：路由枚举也用于纯JVM业务校验，只有实际绘制入口才创建依赖Android的图形路径。
    val icon: AppGlyph get() = when (this) {
        DASHBOARD -> AppGlyphs.dashboard
        PROXY -> AppGlyphs.proxies
        PROFILES -> AppGlyphs.profiles
        TOOLS -> AppGlyphs.tools
    }
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
    showNavigation: Boolean = true,
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
                title = { Text(topBarTitle ?: if (selectedDestination == MainDestination.DASHBOARD) "仪表盘" else selectedDestination.label,
                    style = if (selectedDestination == MainDestination.PROFILES) TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                    else MaterialTheme.typography.titleLarge) },
                expandedHeight = if (selectedDestination == MainDestination.PROFILES) 72.dp else 64.dp,
                navigationIcon = topBarNavigationIcon ?: {},
                actions = topBarActions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pageColor),
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = { if (showNavigation) MainNavigationDock(selectedDestination, destinations, onDestinationSelected) },
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
