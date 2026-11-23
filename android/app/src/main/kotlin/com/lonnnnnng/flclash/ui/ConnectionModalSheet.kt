package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt

internal enum class ConnectionSheetDetent { EXPANDED, PARTIAL, HIDDEN }

internal object ConnectionSheetMotion {
    const val projectionTimeSeconds = 0.099f
    const val cornerRadiusDp = 28f
    const val springStiffness = 1000f / 3f
    const val sideEnterDurationMillis = 300
    const val sideExitDurationMillis = 200

    // 作者 long：Flutter 用临界阻尼弹簧在停靠间移动，不让详情面板出现会遮挡内容的过冲。
    fun settleSpec(): SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = springStiffness,
    )

    // 作者 long：路由进出场与遮罩共用 Flutter 的临界弹簧，并以 0.001 进度误差结束过渡。
    fun presentationSpec(): SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = springStiffness,
        visibilityThreshold = 0.001f,
    )

    // 作者 long：Flutter 侧栏使用 Material 标准减速曲线，并为进出场保留不同时间。
    fun sidePresentationSpec(durationMillis: Int): AnimationSpec<Float> = tween(
        durationMillis = durationMillis,
        easing = CubicBezierEasing(0f, 0f, 0f, 1f),
    )
}

internal fun superellipseQuarterPoints(radius: Float, exponent: Float = 4f, samples: Int = 20): List<Offset> {
    require(radius > 0f && radius.isFinite())
    require(exponent >= 2f && exponent.isFinite())
    require(samples >= 2)
    return (0..samples).map { index ->
        val angle = (Math.PI / 2.0) * index / samples
        Offset(
            x = radius - radius * cos(angle).pow(2.0 / exponent.toDouble()).toFloat(),
            y = radius - radius * sin(angle).pow(2.0 / exponent.toDouble()).toFloat(),
        )
    }
}

/**
 * 作者 long：Flutter 使用 RoundedSuperellipseBorder，普通圆角在 28dp 顶角处会显得更尖。
 * 顶部两角用四次超椭圆采样近似连续曲线，底边保持直角；精确曲率仍需与 Flutter 引擎逐像素对照。
 */
internal data class ConnectionSheetSuperellipseShape(val radius: Dp = ConnectionSheetMotion.cornerRadiusDp.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val corner = with(density) { radius.toPx() }.coerceIn(0f, min(size.width, size.height) / 2f)
        val points = superellipseQuarterPoints(corner)
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, corner)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(size.width - corner, 0f)
            points.asReversed().forEach { lineTo(size.width - it.x, it.y) }
            lineTo(size.width, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

internal class ConnectionSheetGeometry(val availableHeight: Float, private val naturalContentHeight: Float? = null, minimumPartial: Boolean = false,
    private val keyboardInset: Float = 0f, private val unit: Float = 1f, fitOverKeyboard: Boolean = false) {
    init {
        require(availableHeight > 0f && availableHeight.isFinite())
        require(naturalContentHeight == null || naturalContentHeight > 0f && naturalContentHeight.isFinite())
    }

    private val contentHeight = (naturalContentHeight ?: availableHeight).coerceAtLeast(if (minimumPartial) availableHeight * 9f / 16f else 0f)
    // 作者 long：短表单的空白先被键盘吃掉，再整体上移；不能直接对压缩后的窗口取90%，否则短表单会被拉成整页。
    private fun formHeight(fraction: Float): Float {
        val full = availableHeight + keyboardInset
        val fit = naturalContentHeight ?: full
        fun rounded(x: Float, width: Float) = when { x >= width -> x; x <= -width -> 0f; else -> (x + width) * (x + width) / (4f * width) }
        val peak = (full - 10f * unit).coerceAtLeast(0f)
        val tallest = .9f * full
        val top = peak - rounded(peak - tallest - keyboardInset, (peak - tallest).coerceIn(0f, 120f * unit))
        val rest = fit.coerceIn(full * 9f / 16f, full * fraction)
        val blank = rest - min(fit, rest)
        return (maxOf(keyboardInset, min(top, rest + rounded(keyboardInset - blank, blank / 2))) - keyboardInset).coerceAtLeast(1f)
    }
    private val partialHeight = if (fitOverKeyboard) formHeight(9f / 16f) else min(contentHeight, availableHeight * 9f / 16f)
    private val expandedHeight = if (fitOverKeyboard) formHeight(.9f) else min(contentHeight, availableHeight * .9f)
    val hasExpandedDetent = expandedHeight - partialHeight > .5f
    // 作者 long：短内容的两档高度重合时只登记一档，避免拖动归档到同坐标的另一个状态后不断跳动。
    val detents = if (hasExpandedDetent) ConnectionSheetDetent.entries.toList()
        else listOf(ConnectionSheetDetent.PARTIAL, ConnectionSheetDetent.HIDDEN)

    fun offset(detent: ConnectionSheetDetent): Float = availableHeight - when (detent) {
        ConnectionSheetDetent.EXPANDED -> expandedHeight
        ConnectionSheetDetent.PARTIAL -> partialHeight
        ConnectionSheetDetent.HIDDEN -> 0f
    }

    fun projectedTarget(position: Float, velocity: Float): ConnectionSheetDetent {
        // 作者 long：松手按 Flutter 的 99ms 投影选择最近停靠点，慢拖可停在两档，快速下甩仍能关闭。
        val projected = (position + velocity * ConnectionSheetMotion.projectionTimeSeconds)
            .coerceIn(offset(ConnectionSheetDetent.EXPANDED), availableHeight)
        return detents.minBy { abs(offset(it) - projected) }
    }
}

@Composable
internal fun ConnectionModalSheet(
    onDismiss: () -> Unit,
    contentScrollState: ScrollState,
    isContentAtTop: () -> Boolean = { contentScrollState.value == 0 },
    avoidIme: Boolean = false,
    restoreDetentAfterIme: Boolean = false,
    naturalContentHeight: Int? = null,
    sheetTag: String = "connections_detail_sheet",
    sheetShape: Shape = ConnectionSheetSuperellipseShape(),
    onBackRequest: (() -> Boolean)? = null,
    dismissAllowed: () -> Boolean = { true },
    closeRequested: Boolean = false,
    handleHeight: Dp = 48.dp,
    handleWidth: Dp = 32.dp,
    handleVerticalOffset: Dp = 0.dp,
    floatingForm: Boolean = false,
    fitFormOverKeyboard: Boolean = false,
    content: @Composable ColumnScope.(onDismissRequest: () -> Unit) -> Unit,
) {
    // 作者 long：编辑器的加密注册表只接受明确值类型，停靠档位保存名称，避免枚举对象阻断正式草稿恢复。
    var savedDetent by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver(
        save = { it: ConnectionSheetDetent -> it.name }, restore = { ConnectionSheetDetent.valueOf(it) },
    )) { mutableStateOf(ConnectionSheetDetent.PARTIAL) }
    val state = remember { AnchoredDraggableState(savedDetent) }
    val scope = rememberCoroutineScope()
    val dismissSheet by rememberUpdatedState(onDismiss)
    val contentAtTop by rememberUpdatedState(isContentAtTop)
    val backRequest by rememberUpdatedState(onBackRequest)
    val canDismiss by rememberUpdatedState(dismissAllowed)
    val presentation = remember { Animatable(0f) }
    val presentationSpec = remember { ConnectionSheetMotion.presentationSpec() }
    var hasEntered by remember { mutableStateOf(false) }
    // 作者 long：关闭决定必须跨重建保留，否则旋转或重建会取消退出协程并重新展示已关闭的快照。
    var dismissalStarted by rememberSaveable { mutableStateOf(false) }
    var dismissedByDrag by rememberSaveable { mutableStateOf(false) }
    var scrimFade by remember { mutableFloatStateOf(1f) }
    val requestDismiss: () -> Unit = { if (canDismiss()) dismissalStarted = true }
    // 作者 long：编辑器确认保存/放弃后才退出；拒绝关闭的草稿仍留在原窗口，不先播放退场再重新弹出。
    LaunchedEffect(closeRequested) { if (closeRequested) dismissalStarted = true }
    Dialog(
        onDismissRequest = { if (!dismissalStarted && backRequest?.invoke() != true) requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > .5f
        DisposableEffect(window, lightBars) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val status = controller?.isAppearanceLightStatusBars
            val navigation = controller?.isAppearanceLightNavigationBars
            // 作者 long：Sheet拥有独立Window，不能继承系统深色而把白色图标画在应用浅色背景上；关闭时归还原窗口设置。
            controller?.isAppearanceLightStatusBars = lightBars
            controller?.isAppearanceLightNavigationBars = lightBars
            onDispose {
                if (status != null) controller.isAppearanceLightStatusBars = status
                if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
            }
        }
        SideEffect {
            // 作者 long：遮罩由 Compose 按主题绘制，关闭系统 Dialog 的额外调暗，避免双层遮罩改变浅色对比度。
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().testTag("connection_detail_scrim")
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha =
                        (if (floatingForm) { if (com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme.current) .48f else .2f } else .32f) * presentation.value * scrimFade))
                    .clickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !dismissalStarted,
                        onClick = requestDismiss,
                    ),
            )
            // 作者 long：停靠比例以顶部及左右安全区后的窗口为分母；底部安全区属于面板，再由内容避让导航栏。
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    // 作者 long：带底部搜索的节点弹层在键盘上方重新计算停靠高度，保证输入框与节点都可达。
                    .then(if (avoidIme) Modifier.imePadding() else Modifier)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .testTag("connection_sheet_viewport"),
            ) {
                val density = LocalDensity.current
                val keyboardInset = WindowInsets.ime.getBottom(density)
                val imeVisible = keyboardInset > 0
                val latestImeVisible by rememberUpdatedState(imeVisible)
                var detentBeforeIme by remember { mutableStateOf<ConnectionSheetDetent?>(null) }
                val settleSpec = remember { ConnectionSheetMotion.settleSpec() }
                // 作者 long：数据到达、错误提示或子页改变内容高度时沿用面板弹簧重排，不能突然跳动或截断新增操作。
                val fitHeight by animateFloatAsState(
                    naturalContentHeight?.toFloat() ?: constraints.maxHeight.toFloat(),
                    animationSpec = settleSpec, label = "sheetContentHeight",
                )
                val geometry = remember(constraints.maxHeight, fitHeight, naturalContentHeight == null, floatingForm, fitFormOverKeyboard, keyboardInset) {
                    ConnectionSheetGeometry(constraints.maxHeight.toFloat(), if (naturalContentHeight == null) null else fitHeight, floatingForm,
                        if (avoidIme) keyboardInset.toFloat() else 0f, density.density, fitFormOverKeyboard)
                }
                val latestGeometry by rememberUpdatedState(geometry)
                val sidePanel = maxWidth > 600.dp
                // 作者 long：进出场只由弹层生命周期驱动；窗口高度变化不能重新入场并取消正在执行的退出。
                LaunchedEffect(dismissalStarted) {
                    if (dismissalStarted) {
                        if (dismissedByDrag) presentation.snapTo(0f)
                        else presentation.animateTo(
                            0f,
                            if (sidePanel) ConnectionSheetMotion.sidePresentationSpec(ConnectionSheetMotion.sideExitDurationMillis)
                            else presentationSpec,
                        )
                        dismissSheet()
                    } else {
                        presentation.animateTo(
                            1f,
                            if (sidePanel) ConnectionSheetMotion.sidePresentationSpec(ConnectionSheetMotion.sideEnterDurationMillis)
                            else presentationSpec,
                        )
                        hasEntered = true
                    }
                }
                if (sidePanel) {
                    // 作者 long：Flutter 手机横屏超过 600dp 时改用侧面板，正文继续滚动，不能把高度比例误套在侧栏上。
                    SideEffect { scrimFade = 1f }
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .width((maxWidth * 0.4f).coerceIn(360.dp, 560.dp)).fillMaxHeight()
                            .graphicsLayer { translationX = size.width * (1f - presentation.value) }
                            .testTag(sheetTag),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.fillMaxSize().blockSheetInput(dismissalStarted).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
                            content(requestDismiss)
                        }
                    }
                } else {
                    var dragStartedAtTop by remember { mutableStateOf(true) }
                    val anchors = remember(geometry) {
                        DraggableAnchors { geometry.detents.forEach { it at geometry.offset(it) } }
                    }
                    SideEffect {
                        // 作者 long：输入法动画每帧重建锚点，沿当前自动展开/恢复目标更新，不能用临时展开值覆盖原停靠意图。
                        val target = if (restoreDetentAfterIme && avoidIme && imeVisible) ConnectionSheetDetent.EXPANDED
                            else detentBeforeIme ?: savedDetent
                        state.updateAnchors(anchors, if (target in geometry.detents) target else ConnectionSheetDetent.PARTIAL)
                    }
                    LaunchedEffect(avoidIme, imeVisible, geometry, dismissalStarted, restoreDetentAfterIme) {
                        // 作者 long：键盘压缩可用高度后半展开只剩工具栏，搜索时展开到键盘上方，保留可操作的节点结果。
                        if (avoidIme && imeVisible && !dismissalStarted) {
                            if (restoreDetentAfterIme && detentBeforeIme == null) detentBeforeIme = savedDetent
                            state.animateTo(ConnectionSheetDetent.EXPANDED, settleSpec)
                        } else if (restoreDetentAfterIme && detentBeforeIme != null && !dismissalStarted) {
                            // 作者 long：搜索键盘关闭后回到打开前的停靠位，避免短暂输入把整页面板长期留在全屏。
                            val restore = detentBeforeIme ?: ConnectionSheetDetent.PARTIAL
                            state.animateTo(restore, settleSpec)
                            // 作者 long：窗口高度变化会取消并重启此协程，只有真正到达原档位才消费恢复意图。
                            savedDetent = restore
                            detentBeforeIme = null
                        }
                    }
                    LaunchedEffect(state, geometry) {
                        snapshotFlow {
                            val offset = state.offset.takeIf { it.isFinite() } ?: geometry.offset(savedDetent)
                            ((geometry.availableHeight - offset) /
                                (geometry.availableHeight - geometry.offset(ConnectionSheetDetent.PARTIAL)))
                                .coerceIn(0f, 1f)
                        }.collect { scrimFade = it }
                    }
                    LaunchedEffect(state) {
                        snapshotFlow { Triple(state.settledValue, state.currentValue,
                            restoreDetentAfterIme && (latestImeVisible || detentBeforeIme != null)) }
                            .collect { (detent, currentDetent, automaticImeMotion) ->
                            if (detent == ConnectionSheetDetent.HIDDEN) {
                                if (!dismissalStarted) {
                                    if (canDismiss()) {
                                        dismissedByDrag = true
                                        dismissalStarted = true
                                    } else state.animateTo(savedDetent, settleSpec)
                                }
                            } else {
                                // 作者 long：短子页两档重合不代表用户收起；返回长页应恢复原展开意图。
                                // 作者 long：列表语义滚动只有位移、没有fling，currentValue已展开而settledValue仍旧；键盘前须保存实际非隐藏档位。
                                // HIDDEN仍只由上方settledValue判断，避免拖过半程就提前关闭。
                                val chosen = if (restoreDetentAfterIme && currentDetent != ConnectionSheetDetent.HIDDEN) currentDetent else detent
                                if (!automaticImeMotion && (!floatingForm || latestGeometry.hasExpandedDetent || savedDetent != ConnectionSheetDetent.EXPANDED || chosen == ConnectionSheetDetent.EXPANDED))
                                    savedDetent = chosen
                            }
                        }
                    }
                    suspend fun settle(velocity: Float) {
                        state.animateTo(geometry.projectedTarget(state.requireOffset(), velocity), settleSpec)
                    }
                    val fling = remember(state) {
                        object : FlingBehavior {
                            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                                // 作者 long：控件已持有 anchoredDrag 锁；在此再调用 animateTo 会取消自己，必须通过当前滚动作用域归档。
                                // 作者 long：控件可能保留初始 fling 对象，安全区或窗口变化后必须读当前锚点，不能捕获首次布局高度。
                                val start = state.requireOffset()
                                val projected = start + initialVelocity * ConnectionSheetMotion.projectionTimeSeconds
                                val target = ConnectionSheetDetent.entries.map { state.anchors.positionOf(it) }
                                    .filter { it.isFinite() }.minBy { abs(it - projected) }
                                var previous = start
                                animate(start, target, animationSpec = settleSpec) { value, _ ->
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
                                if (source == NestedScrollSource.UserInput && available.y < 0f && contentAtTop())
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
                    val height = (geometry.availableHeight - offset).coerceAtLeast(1f)
                    // 作者 long：半停靠的表单浮起8dp，展开/键盘时归零；布局补偿缩放后的底部，避免把搜索栏挤出可见区域。
                    val reach = ((height / geometry.availableHeight - 9f / 16f) / (.9f - 9f / 16f)).coerceIn(0f, 1f)
                    val floating = if (floatingForm) (1f - reach) * (1f - WindowInsets.ime.getBottom(density) / with(density) { 120.dp.toPx() }).coerceIn(0f, 1f) *
                        (if (hasEntered) 1f else presentation.value.coerceIn(0f, 1f)) else 0f
                    val inset = with(density) { 8.dp.toPx() } * floating
                    val scale = (1f - 2f * inset / constraints.maxWidth).coerceAtLeast(.1f)
                    val visibleHeight = with(density) { ((height - inset) / scale).coerceAtLeast(1f).toDp() }
                    val presentationOffset = offset + (geometry.availableHeight - offset) * (1f - presentation.value)
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(visibleHeight)
                            .offset { IntOffset(0, presentationOffset.roundToInt()) }
                            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f) }
                            .pointerInput(contentScrollState) {
                                // 作者 long：从列表中部开始下滑只滚回顶部，同一手势不能继续关闭面板，避免长详情误退出。
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    dragStartedAtTop = contentAtTop()
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        // 作者 long：键盘恢复若被用户实际拖动接管，旧恢复意图立即失效；否则取消动画后会一直屏蔽新停靠选择。
                                        if (!latestImeVisible && event.changes.any { it.id == down.id && abs(it.position.y - down.position.y) > viewConfiguration.touchSlop })
                                            detentBeforeIme = null
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .nestedScroll(nestedScroll)
                            .anchoredDraggable(
                                state,
                                Orientation.Vertical,
                                enabled = hasEntered && !dismissalStarted,
                                flingBehavior = fling,
                            )
                            .testTag(sheetTag),
                        shape = if (floatingForm) AppSuperellipseShape(topStart = 28.dp, topEnd = 28.dp,
                            bottomStart = 28.dp * floating, bottomEnd = 28.dp * floating) else sheetShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.fillMaxSize().blockSheetInput(dismissalStarted).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
                            Box(
                                Modifier.fillMaxWidth().height(handleHeight).testTag("connection_detail_handle")
                                    .clickable(
                                        enabled = geometry.hasExpandedDetent && hasEntered && !dismissalStarted,
                                    ) {
                                        if (!latestImeVisible) detentBeforeIme = null
                                        scope.launch {
                                            state.animateTo(if (savedDetent == ConnectionSheetDetent.EXPANDED)
                                                ConnectionSheetDetent.PARTIAL else ConnectionSheetDetent.EXPANDED,
                                                ConnectionSheetMotion.settleSpec(),
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                // 作者 long：紧凑表单的拖动条需保留顶部6dp，但不能为线条定位额外推低标题。
                                Box(Modifier.offset(y = handleVerticalOffset).size(handleWidth, 4.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)))
                            }
                            content(requestDismiss)
                        }
                    }
                }
            }
        }
    }
}
