package com.lonnnnnng.flclash.ui

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import kotlinx.coroutines.delay

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

/** 作者 long：长按前只记录落点；越过拖选阈值后才认领，避免菜单释放后又点击卡片。 */
@Stable
internal class AppContextMenuAnchor {
    var bounds by mutableStateOf(IntRect.Zero)
    var point by mutableStateOf<IntRect?>(null)
    private var pointerAt = 0L
    private var pointer = Offset.Zero
    private var pointerPressed = false
    val carry = AppMenuCarry()
    fun record(position: Offset) { pointer = position; pointerAt = SystemClock.uptimeMillis(); pointerPressed = true }
    fun finishPointer(cancelled: Boolean) { pointerPressed = false; if (cancelled) carry.cancel() else carry.finish() }
    fun open(fromKeyboard: Boolean = false) {
        // 作者 long：只有真实按住时才保留宿主焦点并携带指针；键盘入口由菜单接管系统返回，避免等待不存在的抬手。
        val touchHeld = !fromKeyboard && pointerPressed
        val p = if (touchHeld && SystemClock.uptimeMillis() - pointerAt < 1_500) pointer
            else Offset(0f, bounds.height.toFloat())
        val x = bounds.left + p.x.toInt(); val y = bounds.top + p.y.toInt()
        point = IntRect(x, y, x, y)
        if (touchHeld) carry.start(Offset(x.toFloat(), y.toFloat())) else carry.cancel()
    }
}

internal fun Modifier.appContextMenuAnchor(anchor: AppContextMenuAnchor, enabled: Boolean = true, onOpen: () -> Unit): Modifier =
    onGloballyPositioned { anchor.bounds = it.boundsInWindow().roundToIntRect() }
        .pointerInput(anchor) {
            awaitPointerEventScope {
                try { while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull()?.let { change ->
                        if (change.pressed && !change.previousPressed) anchor.record(change.position)
                        if (change.pressed) {
                            anchor.carry.move(change.position + Offset(anchor.bounds.left.toFloat(), anchor.bounds.top.toFloat()), viewConfiguration.touchSlop)
                            if (anchor.carry.dragging) change.consume()
                        }
                        if (!change.pressed && change.previousPressed) anchor.finishPointer(change.isConsumed || event.type != PointerEventType.Release)
                    }
                } } finally { anchor.finishPointer(cancelled = true) }
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

internal val LocalAppMenuEnabled = staticCompositionLocalOf { true }
private val LocalAppMenuAction = staticCompositionLocalOf<(((()->Unit)) -> Unit)?> { null }

@Composable
internal fun AppMenuSubmenuArrow() {
    AppGlyphIcon(AppGlyphs.chevronForward)
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
    source: AppMenuSource? = null,
    carriedPointer: AppMenuCarry? = null,
    content: @Composable ColumnScope.(Any?) -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    var present by remember { mutableStateOf(false) }
    var requestedClose by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val expandedLatest by rememberUpdatedState(expanded)
    val active = expanded && !requestedClose
    visible.targetState = active
    if (!expanded && !present && !requestedClose) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dismiss by rememberUpdatedState(onDismissRequest)
    val back by rememberUpdatedState(onNavigateBack)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { pendingAction = null; source?.hidden = false; dismiss() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val glass = LocalAppMenuGlass.current
    val hostView = LocalView.current
    var backdrop by remember { mutableStateOf<AppMenuBackdrop?>(null) }
    val safe = WindowInsets.safeDrawing
    val insets = AppMenuInsets(safe.getLeft(density, direction), safe.getTop(density),
        safe.getRight(density, direction), safe.getBottom(density))
    val margin = with(density) { 16.dp.roundToPx() }; val gap = with(density) { 8.dp.roundToPx() }
    val positioner = remember(margin, gap) { AppMenuPositioner(margin, gap) }
    var parentBounds by remember { mutableStateOf(IntRect.Zero) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    var sourceImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val opening = remember { Animatable(0f) }
    val returning = remember { Animatable(0f) }
    var returnFrom by remember { mutableFloatStateOf(.45f) }
    val sourceReady = sourceImage != null
    val close: () -> Unit = { if (active) requestedClose = true }
    val carry = carriedPointer ?: remember { AppMenuCarry() }
    var sourceHeld by remember(expanded) { mutableStateOf(expanded && carriedPointer?.armed == true) }
    LaunchedEffect(carry.armed) { if (!carry.armed) sourceHeld = false }
    val registry = remember { AppMenuCarryRegistry() }
    val stackScope = rememberCoroutineScope()
    val stack = remember { AppMenuStackState(stackScope, pageKey) }
    stack.unit = density.density
    val navigateBack: () -> Unit = { if (stack.levels.size > 1) stack.back { back?.invoke() } else (back ?: close)() }
    stack.onBack = navigateBack
    LaunchedEffect(pageKey) { stack.sync(pageKey) }
    var scrollable by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    fun hitItem(position: Offset? = carry.position): Any? = position?.takeIf(stack.hitBounds::contains)?.let { point ->
        registry.items.entries.firstOrNull { it.value.enabled && it.value.bounds.contains(point) }?.key
    }
    // 作者 long：只有按住拖选才需要随形变重新命中；普通展开不能因绘制回写命中框而每帧重组整个Popup。
    val movingHitRevision = if (carry.dragging) stack.hitRevision else 0
    LaunchedEffect(carry.position, carry.dragging, active, pageKey, movingHitRevision) {
        val hovered = if (active && carry.dragging) hitItem() else null
        if (hovered != registry.hovered && hovered != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        registry.hovered = hovered
        val item = registry.items[hovered]
        if (item?.submenu == true) { delay(400); if (active && carry.armed) { item.action(); registry.hovered = null; carry.dragging = false } }
    }
    LaunchedEffect(carry.release) {
        if (carry.release > 0 && active) {
            registry.items[hitItem(carry.releasedPosition)]?.takeIf { it.enabled && !it.submenu }?.action?.invoke()
            registry.hovered = null
        }
    }
    LaunchedEffect(active) {
        if (active) {
            stack.reset(pageKey)
            present = true
            opening.snapTo(0f)
            returning.snapTo(0f)
            sourceImage = source?.capture()
            source?.hidden = sourceImage != null
            // 作者 long：原按钮隐藏后的宿主帧才可作为磨砂背景，否则玻璃会留下按钮的蓝色残影。
            if (glass) withFrameNanos { }
            backdrop = if (glass) captureAppMenuBackdrop(hostView, density.density) else null
            opening.animateTo(.45f, tween(450, easing = LinearEasing))
        } else {
            returnFrom = opening.value
            returning.snapTo(0f)
            val duration = if (sourceReady) 500 else 200
            returning.animateTo(duration / 1000f, tween(duration, easing = LinearEasing))
            source?.hidden = false
            // 作者 long：源按钮重新接管绘制后才打开目标子页，避免新Window先出现导致液滴被截断；外部状态撤销不执行旧操作。
            val action = pendingAction.takeIf { expandedLatest && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
            pendingAction = null
            if (requestedClose) { if (action == null) dismiss() else action() }
            sourceImage = null; backdrop = null
            carry.cancel(); registry.hovered = null
            present = false
            requestedClose = false
        }
    }
    DisposableEffect(source) { onDispose { source?.hidden = false } }
    // 作者 long：菜单窗口始终以宿主Window原点布局，锚点使用boundsInWindow，避免把状态栏偏移重复计算。
    val windowOrigin = remember { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            parentBounds = anchorBounds
            return IntOffset.Zero
        }
    } }
    Popup(popupPositionProvider = windowOrigin, onDismissRequest = { navigateBack() },
        // 作者 long：源长按尚未抬手时不抢Window焦点，原指针才能持续送到源控件；释放后恢复键盘和系统返回接管。
        properties = PopupProperties(focusable = !sourceHeld, clippingEnabled = false)) {
        val focus = LocalFocusManager.current
        Box(Modifier.fillMaxSize().focusGroup().testTag("app_popup_window").pointerInput(carry, scrollable) {
            try { awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (scrollable) { carry.cancel(); return@awaitEachGesture }
                carry.start(down.position)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) carry.move(change.position, viewConfiguration.touchSlop)
                    if (carry.dragging && change.pressed) change.consume()
                    if (!change.pressed) { if (change.isConsumed || event.type != PointerEventType.Release) carry.cancel() else carry.finish(); break }
                } while (true)
            } } finally { carry.cancel() }
        }.onPreviewKeyEvent { event ->
            when {
                event.key == Key.Escape -> { if (event.type == KeyEventType.KeyUp) navigateBack(); true }
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> focus.moveFocus(FocusDirection.Next)
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> focus.moveFocus(FocusDirection.Previous)
                else -> false
            }
        }) {
            Box(Modifier.matchParentSize().testTag("app_popup_outside").pointerInput(active) { detectTapGestures { close() } })
            Layout(content = {
                AppMenuVisibility(sourceReady, visible) {
                    val dark = LocalKClashDarkTheme.current
                    val surface = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceBright
                    val frameCache = remember { AppMenuFrameCache() }
                    val measuredSize = remember { mutableStateOf(IntSize.Zero) }
                    val morph: (androidx.compose.ui.geometry.Size) -> AppMenuMorphFrame = { size ->
                        val origin = source?.bounds?.takeUnless { it == IntRect.Zero } ?: anchor ?: parentBounds
                        val localAnchor = Rect((origin.left - menuPosition.x).toFloat(), (origin.top - menuPosition.y).toFloat(),
                            (origin.right - menuPosition.x).toFloat(), (origin.bottom - menuPosition.y).toFloat())
                        val motion = if (!active && sourceReady) appMenuReturnFrame(localAnchor,
                            appMenuMorphFrame(localAnchor, size, returnFrom, density.density, true), returning.value, density.density)
                        else appMenuMorphFrame(localAnchor, size, opening.value, density.density, sourceReady)
                        stack.outerBounds = if (active && opening.value < .45f) motion.platter.translate(Offset(menuPosition.x.toFloat(), menuPosition.y.toFloat())) else null
                        frameCache.resolve(motion)
                    }
                    Box(modifier.widthIn(min = 160.dp, max = 280.dp).width(IntrinsicSize.Max)
                        .onSizeChanged { measuredSize.value = it }.testTag("app_popup_menu")) {
                        // 作者 long：只有入场/回源绘制外层变形阴影，展开完成交给各层；零透明度阴影也会产生缓存和路径工作。
                        AppMenuFrameShadow(Modifier.matchParentSize(), measuredSize, morph,
                            visible = { opening.value < .45f || !active }, opacity = { 1f }, dark = dark)
                        Box(Modifier.appMenuMorph(surface, morph, sourceImage, backdrop,
                            Offset(-menuPosition.x.toFloat(), -menuPosition.y.toFloat()),
                            drawSurface = { opening.value < .45f || !active })) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                            LocalAppMenuEnabled provides active, LocalAppMenuCarry provides registry,
                            LocalAppMenuAction provides { action -> if (active && pendingAction == null) { pendingAction = action; requestedClose = true } }) {
                            AppMenuStack(stack, pageKey, active, surface, backdrop, menuPosition, opening.asState(), returning.asState(),
                                onScrollable = { scrollable = it }, content = content)
                        }
                        }
                    }
                }
            }) { children, constraints ->
                // 作者 long：可见性动画移除子树与回源回调完成可能相差一帧，空帧仍保留窗口，不能解引用不存在的菜单。
                if (children.isEmpty()) return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
                val available = constraints.copy(minWidth = 0, minHeight = 0,
                    maxWidth = (constraints.maxWidth - insets.left - insets.right - margin * 2).coerceAtLeast(0),
                    maxHeight = (constraints.maxHeight - insets.top - insets.bottom - margin * 2).coerceAtLeast(0))
                val child = children.single().measure(available)
                val point = positioner.position(source?.bounds?.takeUnless { it == IntRect.Zero } ?: anchor ?: parentBounds, IntSize(constraints.maxWidth, constraints.maxHeight),
                    IntSize(child.width, child.height), insets, placement, avoid)
                menuPosition = point
                layout(constraints.maxWidth, constraints.maxHeight) { child.place(point.x, point.y) }
            }
        }
    }
}

/** 作者 long：液滴由回源时钟负责保留绘制层；透明度不变的AnimatedVisibility会立即移除内容，不能承担500ms回源。 */
@Composable
private fun AppMenuVisibility(sourceReady: Boolean, visible: MutableTransitionState<Boolean>, content: @Composable () -> Unit) {
    if (sourceReady) Box { content() }
    else AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(1)),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = .8f, transformOrigin = TransformOrigin(1f, 0f))) { content() }
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
    dismissOnClick: Boolean = true,
    opensSubmenu: Boolean = false,
    isBack: Boolean = false,
) {
    val stack = LocalAppMenuStack.current
    val level = LocalAppMenuLevel.current
    val owner = if (isBack) level?.owner else null
    val active = enabled && (if (isBack) LocalAppMenuHeaderEnabled.current else LocalAppMenuEnabled.current)
    val requestInitialFocus = remember(level, active) { level?.claimInitialFocusTarget(active) == true }
    val color = (if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        // 作者 long：后层只屏蔽交互并由卡片统一染暗，不能再把每行当成业务禁用，否则父卡会被重复压暗。
        .copy(alpha = if (enabled) 1f else .3f)
    val shape = AppSuperellipseShape(16.dp)
    val menuAction = LocalAppMenuAction.current
    val registry = LocalAppMenuCarry.current
    val identity = remember { Any() }
    var itemBounds by remember { mutableStateOf(Rect.Zero) }
    val action = { if (active) {
        if (isBack && stack != null) stack.back(onClick)
        else {
            if (opensSubmenu) stack?.navigatingOwner = AppMenuOwner(identity, itemBounds, text, leadingIcon, trailingIcon)
            if (dismissOnClick && menuAction != null) menuAction(onClick) else onClick()
        }
    } }
    val latestAction by rememberUpdatedState(action)
    DisposableEffect(registry, identity) { onDispose { registry?.items?.remove(identity) } }
    SideEffect {
        registry?.items?.get(identity)?.let { item ->
            if (item.enabled != active || item.submenu != opensSubmenu) registry.items[identity] = item.copy(enabled = active, submenu = opensSubmenu)
        }
    }
    Row(modifier.fillMaxWidth().then(if (requestInitialFocus && level != null) Modifier.focusRequester(level.initialFocusRequester) else Modifier)
        .onGloballyPositioned { coordinates ->
        itemBounds = coordinates.boundsInWindow()
        registry?.items?.set(identity, AppMenuCarriedItem(coordinates.boundsInWindow(), active, opensSubmenu) { latestAction() })
    }.graphicsLayer {
        val child = stack?.levels?.firstOrNull { it.owner?.identity === identity }
        alpha = if (child != null && level !== stack?.levels?.lastOrNull()) 0f
            else if (!isBack && level?.owner != null) ((checkNotNull(stack).unfold.value - .05f) / .35f).coerceIn(0f, 1f) else 1f
    }.appFocusRing(shape).clip(shape)
        .background(if (registry?.hovered == identity) MaterialTheme.colorScheme.onSurface.copy(alpha = .08f) else Color.Transparent)
        .clickable(enabled = active, onClick = action)
        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides color, LocalTextStyle provides TextStyle(fontSize = 15.sp, lineHeight = 20.sp)) {
            val lead = owner?.leading ?: leadingIcon
            if (lead != null) { Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { lead() }; Spacer(Modifier.width(12.dp)) }
            Box(Modifier.weight(1f)) { (owner?.text ?: text)() }
            if (owner != null) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(16.dp).graphicsLayer { rotationZ = 90f * (stack?.unfold?.value ?: 1f).coerceIn(0f, 1f) }) { AppMenuSubmenuArrow() }
            } else if (trailingIcon != null) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { trailingIcon() } }
        }
    }
}
