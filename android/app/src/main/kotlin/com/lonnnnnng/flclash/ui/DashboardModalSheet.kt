package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// 作者 long：退场层只保留绘制，统一消费触摸和按键，避免关闭途中仍触发回收、检测或第二次保存。
internal fun Modifier.blockSheetInput(blocked: Boolean): Modifier = if (!blocked) this else
    clearAndSetSemantics {}.onPreviewKeyEvent { true }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

internal class DashboardSheetPage(
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
    backPage: DashboardSheetPage? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit,
) {
    var bodyHeight by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val chromeHeight = with(density) { 48.dp.roundToPx() } + WindowInsets.navigationBars.getBottom(density)
    val pages = rememberSaveableStateHolder()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val scope = rememberCoroutineScope()
    val backGesture = remember { SheetBackGesture(scope) }
    val entrance = remember { Animatable(0f) }
    val latestBack by rememberUpdatedState(onNavigateBack)
    val currentKey by rememberUpdatedState(pageKey)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canDrag by rememberUpdatedState(backPage != null && onNavigateBack != null)
    val completedGesture = remember(pageKey) { backGesture.committedTransition?.takeIf { it.second == pageKey } }
    LaunchedEffect(pageKey) {
        // 作者 long：只抑制刚完成的拖回切换；再次进入同一子页后的普通返回仍须播放完整过渡。
        backGesture.reset(); backGesture.forgetTransition(); entrance.snapTo(0f)
        entrance.animateTo(1f, tween(450, easing = LinearEasing))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { backGesture.reset(); backGesture.forgetTransition() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); backGesture.reset() }
    }
    val page = DashboardSheetPage(pageKey, pageDepth, title, scrollState, scrollable, fitsContent, onNavigateBack, actions, content)
    val pageSnapshots = remember { mutableMapOf<String, DashboardSheetPage>() }
    // 作者 long：动画身份只取路由key；测量或加载状态重组时创建的新页面对象不能重启动画并造成空白帧。离场页保留最后一份页面快照。
    pageSnapshots[pageKey] = page
    ConnectionModalSheet(
        onDismiss = onDismiss,
        contentScrollState = scrollState,

        isContentAtTop = isContentAtTop,
        naturalContentHeight = if (fitsContent && bodyHeight > 0 && headerHeight > 0) bodyHeight + headerHeight + chromeHeight else null,
        sheetTag = sheetTag,
        sheetShape = AppSuperellipseShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        onBackRequest = { if (backGesture.active) true else onNavigateBack?.let { it(); true } ?: false },
    ) { requestDismiss ->
        Box(Modifier.fillMaxWidth().weight(1f, fill = !fitsContent).clipToBounds()
            .sheetBackGesture(pageKey, backPage?.key, direction, backGesture,
                { canDrag && entrance.value == 1f && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }, {
                    // 作者 long：后台中断或外部切页后不得执行原页面迟到的返回。
                    if (currentKey == pageKey && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) latestBack?.invoke()
                })) {
            if (backGesture.active && backPage != null) {
                pages.SaveableStateProvider(backPage.key) {
                    Box(Modifier.matchParentSize().graphicsLayer {
                        translationX = -size.width * direction * .25f * (1f - backGesture.progress)
                        alpha = backGesture.progress
                    }.blockSheetInput(true)) {
                        DashboardSheetPageContent(backPage, false, sheetTag, closeTag, backTag, scrollTag, requestDismiss, {}, {})
                    }
                }
            }
            // 作者 long：拖回中当前页跟随手指整页平移，父页按四分之一宽揭露；松手完成后直接接管父页，避免再播一次普通返回动画。
            AnimatedContent(pageKey, modifier = Modifier.fillMaxWidth().graphicsLayer {
                translationX = if (backGesture.active) size.width * direction * backGesture.progress else 0f
            }, label = "dashboardSheetPage", transitionSpec = {
                if (completedGesture == (initialState to targetState)) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                else {
                    val sign = if (pageSnapshots.getValue(targetState).depth >= pageSnapshots.getValue(initialState).depth) direction else -direction
                    ((slideInHorizontally(tween(450, easing = SheetPageEasing)) { it * sign / 4 } + fadeIn(tween(180, delayMillis = 68,
                        easing = androidx.compose.animation.core.CubicBezierEasing(.215f, .61f, .355f, 1f)))) togetherWith
                        (slideOutHorizontally(tween(450, easing = SheetPageEasing)) { -it * sign / 4 } + fadeOut(tween(135, easing = LinearEasing)))).using(null)
                }
            }) { displayedKey ->
                val displayed = pageSnapshots.getValue(displayedKey)
                val active = displayed.key == pageKey
                pages.SaveableStateProvider(displayed.key) {
                    Box(Modifier.background(dashboardPageColor()).blockSheetInput(backGesture.settling)) {
                        DashboardSheetPageContent(displayed, active, sheetTag, closeTag, backTag, scrollTag, requestDismiss,
                            { if (active) headerHeight = it }, { if (active) bodyHeight = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSheetPageContent(displayed: DashboardSheetPage, active: Boolean,
    sheetTag: String, closeTag: String, backTag: String, scrollTag: String, requestDismiss: () -> Unit,
    onHeaderHeight: (Int) -> Unit, onBodyHeight: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().blockSheetInput(!active).testTag("${sheetTag}_page_${displayed.key}")) {
        DashboardSheetHeader(displayed.title, closeTag, requestDismiss, displayed.back, backTag, onHeaderHeight, displayed.actions)
        Column(Modifier.fillMaxWidth().weight(1f, fill = !displayed.fitsContent)
            .then(if (displayed.scrollable) Modifier.verticalScroll(displayed.scroll) else Modifier)
            .onSizeChanged { onBodyHeight(it.height) }.testTag(scrollTag)
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            displayed.content(this, requestDismiss)
        }
    }
}

/** 作者 long：代理选择与详情面板共用同一个标题栏，避免独立入口继续保留右侧关闭和不同标题基线。 */
@Composable
internal fun DashboardSheetHeader(title: String, closeTag: String, requestDismiss: () -> Unit,
    onBack: (() -> Unit)? = null, backTag: String = "dashboard_sheet_back", onHeight: (Int) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}) {
    var actionsWidth by remember { mutableIntStateOf(0) }
    val titleInset = with(LocalDensity.current) { actionsWidth.toDp() }.coerceAtLeast(48.dp)
    // 作者 long：固定Flutter compact sheet采用48dp标题栏、左侧关闭/返回；两侧等宽留白让标题真正居中，操作增减不推移标题。
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).onSizeChanged { onHeight(it.height) }.testTag("dashboard_sheet_header")) {
        IconButton(onBack ?: requestDismiss, Modifier.align(Alignment.CenterStart).testTag(if (onBack == null) closeTag else backTag)) {
            Icon(if (onBack == null) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, null)
        }
        Text(title, Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = titleInset).testTag("dashboard_sheet_title"),
            style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.align(Alignment.CenterEnd).onSizeChanged { actionsWidth = it.width }, verticalAlignment = Alignment.CenterVertically) {
            actions(this)
            // 作者 long：已有多层面板保留直接关闭入口，左侧返回仍只退一层，避免改变既有退出契约。
            if (onBack != null) IconButton(requestDismiss, Modifier.testTag(closeTag)) { Icon(Icons.Outlined.Close, null) }
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
