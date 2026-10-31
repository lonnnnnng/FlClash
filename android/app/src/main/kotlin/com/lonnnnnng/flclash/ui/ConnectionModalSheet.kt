package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class ConnectionSheetDetent { EXPANDED, PARTIAL, HIDDEN }

internal class ConnectionSheetGeometry(val availableHeight: Float) {
    init { require(availableHeight > 0f && availableHeight.isFinite()) }

    fun offset(detent: ConnectionSheetDetent): Float = availableHeight * when (detent) {
        ConnectionSheetDetent.EXPANDED -> 0.1f
        ConnectionSheetDetent.PARTIAL -> 7f / 16f
        ConnectionSheetDetent.HIDDEN -> 1f
    }

    fun projectedTarget(position: Float, velocity: Float): ConnectionSheetDetent {
        // 作者 long：松手按 Flutter 的 99ms 投影选择最近停靠点，慢拖可停在两档，快速下甩仍能关闭。
        val projected = (position + velocity * 0.099f).coerceIn(offset(ConnectionSheetDetent.EXPANDED), availableHeight)
        return ConnectionSheetDetent.entries.minBy { abs(offset(it) - projected) }
    }
}

@Composable
internal fun ConnectionModalSheet(onDismiss: () -> Unit, contentScrollState: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    var savedDetent by rememberSaveable { mutableStateOf(ConnectionSheetDetent.PARTIAL) }
    val state = remember { AnchoredDraggableState(savedDetent) }
    val scope = rememberCoroutineScope()
    val dismissSheet by rememberUpdatedState(onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            // 作者 long：遮罩由 Compose 按主题绘制，关闭系统 Dialog 的额外调暗，避免双层遮罩改变浅色对比度。
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().testTag("connection_detail_scrim")
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                    .semantics { contentDescription = "关闭连接详情遮罩" },
            )
            // 作者 long：停靠比例以顶部及左右安全区后的窗口为分母；底部安全区属于面板，再由内容避让导航栏。
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .testTag("connection_sheet_viewport"),
            ) {
                val density = LocalDensity.current
                val geometry = remember(constraints.maxHeight) { ConnectionSheetGeometry(constraints.maxHeight.toFloat()) }
                val sidePanel = maxWidth > 600.dp
                if (sidePanel) {
                    // 作者 long：Flutter 手机横屏超过 600dp 时改用侧面板，正文继续滚动，不能把高度比例误套在侧栏上。
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .width((maxWidth * 0.4f).coerceIn(360.dp, 560.dp)).fillMaxHeight()
                            .testTag("connections_detail_sheet").semantics {
                                paneTitle = "连接详情"; stateDescription = "侧面板"
                                dismiss { dismissSheet(); true }
                            },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)), content = content)
                    }
                } else {
                    var dragStartedAtTop by remember { mutableStateOf(true) }
                    val anchors = remember(geometry) {
                        DraggableAnchors { ConnectionSheetDetent.entries.forEach { it at geometry.offset(it) } }
                    }
                    SideEffect { state.updateAnchors(anchors, savedDetent) }
                    LaunchedEffect(state) {
                        snapshotFlow { state.settledValue }.collect { detent ->
                            if (detent == ConnectionSheetDetent.HIDDEN) dismissSheet() else savedDetent = detent
                        }
                    }
                    suspend fun settle(velocity: Float) {
                        state.animateTo(geometry.projectedTarget(state.requireOffset(), velocity), tween(220))
                    }
                    val fling = remember(state) {
                        object : FlingBehavior {
                            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                                // 作者 long：控件已持有 anchoredDrag 锁；在此再调用 animateTo 会取消自己，必须通过当前滚动作用域归档。
                                // 作者 long：控件可能保留初始 fling 对象，安全区或窗口变化后必须读当前锚点，不能捕获首次布局高度。
                                val currentGeometry = ConnectionSheetGeometry(state.anchors.positionOf(ConnectionSheetDetent.HIDDEN))
                                val start = state.requireOffset()
                                val target = currentGeometry.offset(currentGeometry.projectedTarget(start, initialVelocity))
                                var previous = start
                                animate(start, target, animationSpec = tween(220)) { value, _ ->
                                    scrollBy(value - previous)
                                    previous = value
                                }
                                return 0f
                            }
                        }
                    }
                    val nestedScroll = remember(state, geometry) {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                                if (source == NestedScrollSource.UserInput && available.y < 0f && contentScrollState.value == 0)
                                    Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero

                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                                if (source == NestedScrollSource.UserInput && dragStartedAtTop)
                                    Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero

                            override suspend fun onPreFling(available: Velocity): Velocity {
                                if (available.y < 0f && state.requireOffset() > geometry.offset(ConnectionSheetDetent.EXPANDED)) {
                                    settle(available.y)
                                    return available
                                }
                                return Velocity.Zero
                            }

                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                // 作者 long：中部起手滚回顶部后，剩余惯性也不能收起面板；否则位移保护会在松手时失效。
                                settle(if (dragStartedAtTop) available.y else 0f)
                                return available
                            }
                        }
                    }
                    val offset = state.offset.takeIf { it.isFinite() } ?: geometry.offset(savedDetent)
                    val visibleHeight = with(density) { (geometry.availableHeight - offset).coerceAtLeast(1f).toDp() }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(visibleHeight)
                            .offset { IntOffset(0, offset.roundToInt()) }
                            .pointerInput(contentScrollState) {
                                // 作者 long：从列表中部开始下滑只滚回顶部，同一手势不能继续关闭面板，避免长详情误退出。
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    dragStartedAtTop = contentScrollState.value == 0
                                    do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                                    while (event.changes.any { it.pressed })
                                }
                            }
                            .nestedScroll(nestedScroll)
                            .anchoredDraggable(state, Orientation.Vertical, flingBehavior = fling)
                            .testTag("connections_detail_sheet").semantics {
                                paneTitle = "连接详情"
                                stateDescription = if (savedDetent == ConnectionSheetDetent.EXPANDED) "已展开" else "半展开"
                                dismiss { dismissSheet(); true }
                                if (savedDetent != ConnectionSheetDetent.EXPANDED) expand {
                                    scope.launch { state.animateTo(ConnectionSheetDetent.EXPANDED, tween(220)) }; true
                                } else collapse {
                                    scope.launch { state.animateTo(ConnectionSheetDetent.PARTIAL, tween(220)) }; true
                                }
                            },
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
                            Box(
                                Modifier.fillMaxWidth().height(48.dp).testTag("connection_detail_handle")
                                    .clickable(onClickLabel = if (savedDetent == ConnectionSheetDetent.EXPANDED) "收起详情" else "展开详情") {
                                        scope.launch {
                                            state.animateTo(if (savedDetent == ConnectionSheetDetent.EXPANDED)
                                                ConnectionSheetDetent.PARTIAL else ConnectionSheetDetent.EXPANDED, tween(220))
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(32.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)))
                            }
                            content()
                        }
                    }
                }
            }
        }
    }
}
