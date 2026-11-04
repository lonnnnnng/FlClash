package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 作者 long：主页详情共用 Flutter showSheet 的停靠与内容适配，标题操作固定在正文之外，长内容滚动不会丢失返回和关闭入口。 */
@Composable
internal fun DashboardModalSheet(
    title: String,
    sheetTag: String,
    closeTag: String,
    onDismiss: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    scrollable: Boolean = true,
    fitsContent: Boolean = true,
    isContentAtTop: () -> Boolean = { scrollState.value == 0 },
    onNavigateBack: (() -> Unit)? = null,
    backTag: String = "dashboard_sheet_back",
    scrollTag: String = "dashboard_sheet_scroll",
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit,
) {
    var bodyHeight by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val chromeHeight = with(density) { 48.dp.roundToPx() } + WindowInsets.navigationBars.getBottom(density)
    ConnectionModalSheet(
        onDismiss = onDismiss,
        contentScrollState = scrollState,

        isContentAtTop = isContentAtTop,
        naturalContentHeight = if (fitsContent && bodyHeight > 0 && headerHeight > 0) bodyHeight + headerHeight + chromeHeight else null,
        sheetTag = sheetTag,
        sheetShape = AppSuperellipseShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        onBackRequest = { onNavigateBack?.let { it(); true } ?: false },
    ) { requestDismiss ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).onSizeChanged { headerHeight = it.height }
            .padding(start = if (onNavigateBack == null) 20.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onNavigateBack != null) IconButton(onNavigateBack, Modifier.testTag(backTag)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
            Text(title, Modifier.weight(1f).testTag("dashboard_sheet_title"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
            IconButton(requestDismiss, Modifier.testTag(closeTag)) { Icon(Icons.Outlined.Close, null) }
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = !fitsContent)
            .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
            // 作者 long：在滚动修饰符内测量正文自然高度；测量视口会把长内容误认成短内容，导致无法展开。
            .onSizeChanged { bodyHeight = it.height }.testTag(scrollTag)
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content(requestDismiss)
        }
    }
}

@Composable
internal fun DashboardDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 5.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = AppSuperellipseShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
        }
    }
}
