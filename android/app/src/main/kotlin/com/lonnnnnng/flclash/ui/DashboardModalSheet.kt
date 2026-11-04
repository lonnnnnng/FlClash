package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

// 作者 long：退场层只保留绘制，统一消费触摸和按键，避免关闭途中仍触发回收、检测或第二次保存。
internal fun Modifier.blockSheetInput(blocked: Boolean): Modifier = if (!blocked) this else
    clearAndSetSemantics {}.onPreviewKeyEvent { true }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

private class DashboardSheetPage(
    val key: String, val depth: Int, val title: String, val scroll: ScrollState,
    val scrollable: Boolean, val fitsContent: Boolean, val back: (() -> Unit)?,
    val actions: @Composable RowScope.() -> Unit,
    val content: @Composable ColumnScope.(() -> Unit) -> Unit,
)

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
    pageKey: String = "root",
    pageDepth: Int = 0,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit,
) {
    var bodyHeight by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val chromeHeight = with(density) { 48.dp.roundToPx() } + WindowInsets.navigationBars.getBottom(density)
    val pages = rememberSaveableStateHolder()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val page = DashboardSheetPage(pageKey, pageDepth, title, scrollState, scrollable, fitsContent, onNavigateBack, actions, content)
    ConnectionModalSheet(
        onDismiss = onDismiss,
        contentScrollState = scrollState,

        isContentAtTop = isContentAtTop,
        naturalContentHeight = if (fitsContent && bodyHeight > 0 && headerHeight > 0) bodyHeight + headerHeight + chromeHeight else null,
        sheetTag = sheetTag,
        sheetShape = AppSuperellipseShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        onBackRequest = { onNavigateBack?.let { it(); true } ?: false },
    ) { requestDismiss ->
        // 作者 long：子页沿 Flutter SharedX 的 450ms、四分之一页宽过渡，标题和正文同向移动；外层停靠面板保持原实例。
        AnimatedContent(page, modifier = Modifier.fillMaxWidth().weight(1f, fill = !fitsContent).clipToBounds(),
            contentKey = { it.key }, label = "dashboardSheetPage", transitionSpec = {
                val sign = if (targetState.depth >= initialState.depth) direction else -direction
                ((slideInHorizontally(tween(450, easing = FastOutSlowInEasing)) { it * sign / 4 } + fadeIn(tween(180, delayMillis = 68))) togetherWith
                    (slideOutHorizontally(tween(450, easing = FastOutSlowInEasing)) { -it * sign / 4 } + fadeOut(tween(135)))).using(null)
            }) { displayed ->
            val active = displayed.key == pageKey
            pages.SaveableStateProvider(displayed.key) {
                Column(Modifier.fillMaxWidth().blockSheetInput(!active).testTag("${sheetTag}_page_${displayed.key}")) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).onSizeChanged { if (active) headerHeight = it.height }
                        .padding(start = if (displayed.back == null) 20.dp else 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (displayed.back != null) IconButton(displayed.back, Modifier.testTag(backTag)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                        }
                        Text(displayed.title, Modifier.weight(1f).testTag("dashboard_sheet_title"), style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        displayed.actions(this)
                        IconButton(requestDismiss, Modifier.testTag(closeTag)) { Icon(Icons.Outlined.Close, null) }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f, fill = !displayed.fitsContent)
                        .then(if (displayed.scrollable) Modifier.verticalScroll(displayed.scroll) else Modifier)
                        // 作者 long：仅活动页回报正文自然高度，避免离场页的旧高度抢回停靠位置。
                        .onSizeChanged { if (active) bodyHeight = it.height }.testTag(scrollTag)
                        .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        displayed.content(this, requestDismiss)
                    }
                }
            }
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
