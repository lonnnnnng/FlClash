package com.lonnnnnng.flclash.ui

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme

internal enum class AppMenuPlacement { OVER_ANCHOR_END, BELOW_POINT }
internal data class AppMenuInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** 作者 long：布局沿用Flutter的安全边距、避让优先级与右上角固定，子菜单变宽时不能突然翻到另一边。 */
internal class AppMenuPositioner(private val margin: Int, private val gap: Int) {
    private var identity: List<Any?>? = null
    private var pinned: IntOffset? = null

    fun position(anchor: IntRect, window: IntSize, content: IntSize, insets: AppMenuInsets,
        placement: AppMenuPlacement, avoid: IntRect? = null): IntOffset {
        val key = listOf(anchor, window, insets, placement, avoid)
        if (identity != key) { identity = key; pinned = null }
        val left = insets.left + margin
        val top = insets.top + margin
        val maxX = (window.width - insets.right - margin - content.width).coerceAtLeast(left)
        val maxY = window.height - insets.bottom - margin - content.height
        val held = pinned
        val x = held?.let { it.x - content.width } ?: when (placement) {
            AppMenuPlacement.OVER_ANCHOR_END -> anchor.right - content.width
            AppMenuPlacement.BELOW_POINT -> anchor.left
        }
        val y = held?.y ?: when (placement) {
            AppMenuPlacement.OVER_ANCHOR_END -> anchor.top - gap
            AppMenuPlacement.BELOW_POINT -> when {
                avoid != null && avoid.top - gap - content.height >= top -> avoid.top - gap - content.height
                avoid != null && avoid.bottom + gap <= maxY -> avoid.bottom + gap
                anchor.bottom > maxY && anchor.top - content.height >= top -> anchor.top - content.height
                else -> anchor.bottom
            }
        }
        val minY = if (placement == AppMenuPlacement.BELOW_POINT) top else maxOf(insets.top, minOf(top, y))
        val result = IntOffset(x.coerceIn(left, maxX), y.coerceIn(minY, maxOf(minY, maxY)))
        if (held == null) pinned = IntOffset(result.x + content.width, result.y)
        return result
    }
}

/** 作者 long：只旁听最初落点，不认领触摸；卡片原有点击、长按和滚动仍由combinedClickable处理。 */
@Stable
internal class AppContextMenuAnchor {
    var bounds by mutableStateOf(IntRect.Zero)
    var point by mutableStateOf<IntRect?>(null)
    private var pointerAt = 0L
    private var pointer = Offset.Zero
    fun record(position: Offset) { pointer = position; pointerAt = SystemClock.uptimeMillis() }
    fun open(fromKeyboard: Boolean = false) {
        val p = if (!fromKeyboard && SystemClock.uptimeMillis() - pointerAt < 1_500) pointer
            else Offset(0f, bounds.height.toFloat())
        val x = bounds.left + p.x.toInt(); val y = bounds.top + p.y.toInt()
        point = IntRect(x, y, x, y)
    }
}

internal fun Modifier.appContextMenuAnchor(anchor: AppContextMenuAnchor, enabled: Boolean = true, onOpen: () -> Unit): Modifier =
    onGloballyPositioned { anchor.bounds = it.boundsInWindow().roundToIntRect() }
        .pointerInput(anchor) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed && !it.previousPressed }
                        ?.let { anchor.record(it.position) }
                }
            }
        }.onPreviewKeyEvent { event ->
            val menuKey = event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MENU ||
                event.key == Key.F10 && event.isShiftPressed
            if (enabled && menuKey) {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    anchor.open(fromKeyboard = true); onOpen()
                }
                true
            } else false
        }

private val LocalAppMenuEnabled = staticCompositionLocalOf { true }

@Composable
internal fun AppMenuSubmenuArrow() {
    AppGlyphIcon(AppGlyphs.chevronForward, null)
}

/** 作者 long：全窗口透明拦截层区分“点外部关闭全部”和“返回上层菜单”，不把返回键误当成点外部。 */
@Composable
internal fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: IntRect? = null,
    placement: AppMenuPlacement = AppMenuPlacement.OVER_ANCHOR_END,
    avoid: IntRect? = null,
    pageKey: Any? = Unit,
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dismiss by rememberUpdatedState(onDismissRequest)
    val back by rememberUpdatedState(onNavigateBack)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) dismiss() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val insets = AppMenuInsets(safe.getLeft(density, direction), safe.getTop(density),
        safe.getRight(density, direction), safe.getBottom(density))
    val margin = with(density) { 16.dp.roundToPx() }; val gap = with(density) { 8.dp.roundToPx() }
    val positioner = remember(margin, gap) { AppMenuPositioner(margin, gap) }
    var parentBounds by remember { mutableStateOf(IntRect.Zero) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    val opening = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) { opening.snapTo(0f); opening.animateTo(.45f, tween(450, easing = LinearEasing)) }
    }
    // 作者 long：菜单窗口始终以宿主Window原点布局，锚点使用boundsInWindow，避免把状态栏偏移重复计算。
    val windowOrigin = remember { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            parentBounds = anchorBounds
            return IntOffset.Zero
        }
    } }
    Popup(popupPositionProvider = windowOrigin, onDismissRequest = { (back ?: dismiss)() },
        properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        val focus = LocalFocusManager.current
        Box(Modifier.fillMaxSize().focusGroup().testTag("app_popup_window").onPreviewKeyEvent { event ->
            when {
                event.key == Key.Escape -> { if (event.type == KeyEventType.KeyUp) (back ?: dismiss)(); true }
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> focus.moveFocus(FocusDirection.Next)
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> focus.moveFocus(FocusDirection.Previous)
                else -> false
            }
        }) {
            Box(Modifier.matchParentSize().testTag("app_popup_outside").pointerInput(Unit) { detectTapGestures { dismiss() } })
            Layout(content = {
                AnimatedVisibility(visibleState = visible,
                    enter = fadeIn(tween(1)),
                    exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = .8f, transformOrigin = TransformOrigin(1f, 0f))) {
                    val shape = AppSuperellipseShape(24.dp)
                    val dark = LocalKClashDarkTheme.current
                    val surface = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceBright
                    val morph: (androidx.compose.ui.geometry.Size) -> AppMenuMorphFrame = { size ->
                        val origin = anchor ?: parentBounds
                        val localAnchor = Rect((origin.left - menuPosition.x).toFloat(), (origin.top - menuPosition.y).toFloat(),
                            (origin.right - menuPosition.x).toFloat(), (origin.bottom - menuPosition.y).toFloat())
                        appMenuMorphFrame(localAnchor, size, opening.value, density.density)
                    }
                    Surface(modifier.widthIn(min = 160.dp, max = 280.dp).width(IntrinsicSize.Max)
                        .dropShadow(AppMenuMorphShape(morph), Shadow(radius = ((29f / 2f - .5f) / .57735f).dp,
                            color = Color.Black.copy(alpha = .118f * if (dark) 1.5f else 1f), offset = DpOffset(0.dp, 8.dp)))
                        .appMenuMorph(surface, morph)
                        .testTag("app_popup_menu").semantics { paneTitle = "菜单" }, shape = shape, color = surface) {
                        CompositionLocalProvider(LocalAppMenuEnabled provides expanded) {
                            // 作者 long：子菜单内容增减时保留右上角停靠并平滑调整高度；切页另起滚动位置，避免根菜单滚到底后遮住子页的返回入口。
                            val scroll = remember(pageKey) { ScrollState(0) }
                            Column(Modifier.animateContentSize(spring(dampingRatio = .85f, stiffness = 500f), Alignment.TopEnd)
                                .verticalScroll(scroll).padding(8.dp), content = content)
                        }
                    }
                }
            }) { children, constraints ->
                val available = constraints.copy(minWidth = 0, minHeight = 0,
                    maxWidth = (constraints.maxWidth - insets.left - insets.right - margin * 2).coerceAtLeast(0),
                    maxHeight = (constraints.maxHeight - insets.top - insets.bottom - margin * 2).coerceAtLeast(0))
                val child = children.single().measure(available)
                val point = positioner.position(anchor ?: parentBounds, IntSize(constraints.maxWidth, constraints.maxHeight),
                    IntSize(child.width, child.height), insets, placement, avoid)
                menuPosition = point
                layout(constraints.maxWidth, constraints.maxHeight) { child.place(point.x, point.y) }
            }
        }
    }
}

/** 作者 long：菜单统一采用浮层底色与16dp行圆角；禁用项保留可读标签和禁用语义，退出动画期间不能再次提交。 */
@Composable
internal fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val active = enabled && LocalAppMenuEnabled.current
    val color = (if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        .copy(alpha = if (active) 1f else .3f)
    val shape = AppSuperellipseShape(16.dp)
    Row(modifier.fillMaxWidth().appFocusRing(shape).clip(shape).clickable(enabled = active, role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides color, LocalTextStyle provides TextStyle(fontSize = 15.sp, lineHeight = 20.sp)) {
            if (leadingIcon != null) { Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leadingIcon() }; Spacer(Modifier.width(12.dp)) }
            Box(Modifier.weight(1f)) { text() }
            if (trailingIcon != null) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { trailingIcon() } }
        }
    }
}
