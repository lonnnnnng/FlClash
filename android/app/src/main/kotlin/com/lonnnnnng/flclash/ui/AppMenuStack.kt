package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*

internal data class AppMenuOwner(val identity: Any, val bounds: Rect, val text: @Composable () -> Unit,
    val leading: (@Composable () -> Unit)?, val trailing: (@Composable () -> Unit)?)
internal class AppMenuLevel(val key: Any?, val owner: AppMenuOwner? = null, val top: Float = 0f, val fromWidth: Float = 0f,
    val ownerTop: Float = 0f) {
    val scroll = ScrollState(0)
    val initialFocusRequester = FocusRequester()
    var bounds = Rect.Zero
    var coordinates: LayoutCoordinates? = null
    private var initialFocusTargetAssigned = false

    fun claimInitialFocusTarget(enabled: Boolean): Boolean {
        if (!enabled || initialFocusTargetAssigned) return false
        initialFocusTargetAssigned = true
        return true
    }
}

/** 作者 long：回退完成才移除顶卡，父卡的滚动/组合身份始终保留；同一个弹簧可在收回途中反向，不重置速度。 */
@Stable
internal class AppMenuStackState(private val scope: CoroutineScope, root: Any?) {
    val levels = mutableStateListOf(AppMenuLevel(root))
    val unfold = Animatable(1f)
    var folding by mutableStateOf(false)
    var navigatingOwner: AppMenuOwner? = null
    var unit = 1f
    var onBack: () -> Unit = {}
    var hitBounds = Rect.Zero
    var topCardBounds = Rect.Zero
    var outerBounds by mutableStateOf<Rect?>(null)
    var hitRevision by mutableIntStateOf(0)
    private var externalFold = false
    private var motion: Job? = null
    fun reset(key: Any?) {
        motion?.cancel(); levels.clear(); levels += AppMenuLevel(key); folding = false; externalFold = false; navigatingOwner = null
        motion = scope.launch { unfold.snapTo(1f) }
    }
    fun sync(key: Any?) {
        if (levels.last().key == key) {
            if (externalFold) {
                motion?.cancel(); externalFold = false; folding = false
                motion = scope.launch { unfold.animateTo(1f, spring(.66f, 240f)) }
            }
            return
        }
        val old = levels.indexOfFirst { it.key == key }
        if (old >= 0) { externalFold = true; foldTo(old) {}; return }
        val parent = levels.last()
        val owner = navigatingOwner
        val ownerTop = (owner?.bounds?.top ?: parent.bounds.top) - parent.bounds.top + parent.top
        val top = (ownerTop - 8f * unit).coerceAtLeast(0f)
        levels += AppMenuLevel(key, owner, top, parent.bounds.width, ownerTop)
        navigatingOwner = null; folding = false; externalFold = false
        motion?.cancel(); motion = scope.launch { unfold.snapTo(0f); unfold.animateTo(1f, spring(.66f, 240f)) }
    }
    fun back(action: () -> Unit) {
        if (externalFold) return
        if (levels.size < 2) { action(); return }
        if (folding) {
            folding = false; motion?.cancel()
            motion = scope.launch { unfold.animateTo(1f, spring(.66f, 240f)) }
        } else foldTo(levels.lastIndex - 1, action)
    }
    private fun foldTo(index: Int, action: () -> Unit) {
        motion?.cancel(); folding = true
        motion = scope.launch {
            unfold.animateTo(0f, spring(.86f, 330f))
            while (levels.lastIndex > index) levels.removeAt(levels.lastIndex)
            unfold.snapTo(1f); folding = false; externalFold = false; action()
        }
    }
}

internal val LocalAppMenuStack = staticCompositionLocalOf<AppMenuStackState?> { null }
internal val LocalAppMenuLevel = staticCompositionLocalOf<AppMenuLevel?> { null }
internal val LocalAppMenuHeaderEnabled = staticCompositionLocalOf { true }

internal fun appMenuStackRect(size: Size, top: Float, ownerTop: Float, ownerHeight: Float, progress: Float, unit: Float): Rect {
    val start = Rect(8f * unit, ownerTop - top, size.width - 8f * unit, ownerTop - top + ownerHeight)
    return androidx.compose.ui.geometry.lerp(start, Rect(Offset.Zero, size), progress)
}

/** 作者 long：完整内容先布局，仅改变卡片裁剪范围；文字不做纵向缩放，后层既不暴露语义也不接收内部点击。 */
@Composable
internal fun AppMenuStack(state: AppMenuStackState, pageKey: Any?, active: Boolean, surface: Color,
    backdrop: AppMenuBackdrop?, menuPosition: IntOffset, opening: State<Float>, returning: State<Float>, sourceReady: Boolean,
    onScrollable: (Boolean) -> Unit, content: @Composable ColumnScope.(Any?) -> Unit) {
    val density = LocalDensity.current.density
    val inputModeManager = LocalInputModeManager.current
    val levels = state.levels.toList()
    LaunchedEffect(active, pageKey, levels.lastOrNull()) {
        val level = levels.lastOrNull()
        if (active && level != null && inputModeManager.inputMode == InputMode.Keyboard) {
            // 作者 long：键盘打开或进入子菜单后先聚焦首个可用项，方向键即可按顺序浏览而不误触代理组。
            withFrameNanos { }
            level.initialFocusRequester.requestFocus()
        }
    }
    Layout(content = {
        levels.forEachIndexed { index, level -> key(level) {
            val top = index == levels.lastIndex
            val distance = { if (top) 0f else levels.lastIndex - index - 1 + state.unfold.value.coerceIn(0f, 1f) }
            val scale = { max(0f, 1f - .05f * distance()) }
            val frameCache = remember { AppMenuFrameCache() }
            val frame: (Size) -> AppMenuMorphFrame = { size ->
                val p = if (top && index > 0) state.unfold.value else 1f
                val rect = if (index == 0 || !top) Rect(Offset.Zero, size) else appMenuStackRect(size, level.top, level.ownerTop,
                    level.owner?.bounds?.height ?: 48f * density, p, density)
                frameCache.resolve(AppMenuMorphFrame(rect, max(0f, (16f + 8f * p) * density), 1f))
            }
            // 作者 long：根卡/后层只有缩放和明暗变化，轮廓没有变形；稳定Shape让Compose复用阴影位图，不能每帧制造新缓存键。
            val shape = AppSuperellipseShape(24.dp)
            val measuredSize = remember { mutableStateOf(IntSize.Zero) }
            val shadowRadius = with(LocalDensity.current) { appMenuShadowRadiusPx(density).toDp() }
            val renderer = remember { AppMenuGlassRenderer() }
            val dark = com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme.current
            val tint = if (dark) Color(0xFF343537) else MaterialTheme.colorScheme.surfaceBright
            val quality = LocalAppGlassQuality.current
            val dim = MaterialTheme.colorScheme.surface
            val enabled = active && top && !state.folding && level.key == pageKey
            // 作者 long：maxValue首帧尚未测量时是Int.MAX_VALUE；在组合阶段订阅它，测量后必须撤回“可滚动”，否则短菜单会永久拒绝拖选。
            val hasScroll = level.scroll.maxValue > 0
            SideEffect { if (top) onScrollable(hasScroll) }
            val minWidth = if (index == 0) 160.dp else with(LocalDensity.current) { (level.fromWidth * 1.12f).toDp().coerceAtMost(280.dp) }
            val cardWidth = if (index == 0) Modifier.widthIn(min = minWidth, max = 280.dp).width(IntrinsicSize.Max)
                else Modifier.layout { measurable, constraints ->
                    // 作者 long：前30%展开仍按真实宽度重排，但只使布局失效，正文组合身份与焦点不跟每帧宽度重建。
                    val from = level.fromWidth.coerceIn(160.dp.toPx(), 280.dp.toPx())
                    val p = if (top) (state.unfold.value / .3f).coerceIn(0f, 1f) else 1f
                    val width = constraints.constrainWidth((from + (minWidth.toPx() - from) * p).roundToInt())
                    val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(child.width, child.height) { child.place(0, 0) }
                }
            Box(cardWidth
                .graphicsLayer {
                    // 作者 long：回源只带走最上层卡片；后层保持组合与滚动身份，但不穿过收回中的主体露出。
                    alpha = if (!top && sourceReady && (opening.value < .45f || !active)) 0f else 1f
                    scaleX = scale(); scaleY = scaleX
                    transformOrigin = TransformOrigin(1f, if (size.height > 0) ((levels.last().top - level.top) / size.height).coerceIn(0f, 1f) else 0f)
                }
                .onGloballyPositioned {
                    // 作者 long：采样原点使用未裁剪的局部原点；展开裁剪不能反过来移动背景，否则边缘会随高度游走。
                    level.coordinates = it
                    level.bounds = Rect(it.localToWindow(Offset.Zero), Size(it.size.width * scale(), it.size.height * scale()))
                }.onSizeChanged { measuredSize.value = it }.testTag("app_menu_level_$index")) {
                // 作者 long：阴影单独留在绘制层，明暗只调制该层；Compose的Shadow颜色/alpha都属于位图缓存键，不能随动画重建。
                // 作者 long：普通卡片的阴影随正文一同回源淡出；玻璃在变形阶段保持裸内容，由外层承担阴影，避免重复叠黑。
                if (top && index > 0) AppMenuFrameShadow(Modifier.matchParentSize(), measuredSize, frame,
                    visible = { backdrop == null || opening.value >= .45f && active }, opacity = { state.unfold.value.coerceIn(0f, 1f) }, dark = dark)
                else Box(Modifier.matchParentSize().graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = if (backdrop != null && (opening.value < .45f || !active)) 0f else
                        max(1f / 6, 1f - distance() / 3)
                }.dropShadow(shape, Shadow(radius = shadowRadius,
                    color = Color.Black.copy(alpha = .118f * if (dark) 1.5f else 1f), offset = DpOffset(0.dp, 8.dp))))
                Box(Modifier.graphicsLayer {
                    // 作者 long：顶卡仍按当前轮廓精确裁剪，轮廓状态在图层读取，避免把动画提升到整个菜单的组合范围。
                    this.shape = if (top && index > 0) {
                        val current = frame(size)
                        AppMenuMorphShape { current }
                    } else shape
                    clip = true
                }.drawWithContent {
                    val paintScale = scale()
                    level.coordinates?.takeIf { it.isAttached }?.let {
                        level.bounds = Rect(it.localToWindow(Offset.Zero), Size(it.size.width * paintScale, it.size.height * paintScale))
                    }
                    val motion = frame(size)
                    if (top) {
                        val rect = motion.platter.translate(level.bounds.topLeft)
                        // 作者 long：关闭几何使用实际顶卡，不复用可能仍被入场裁剪限制的触点边界。
                        state.topCardBounds = rect
                        val hit = state.outerBounds?.let(rect::intersect) ?: rect
                        if (state.hitBounds != hit) { state.hitBounds = hit; state.hitRevision++ }
                    }
                    val material = if (top && index > 0) (state.unfold.value / .35f).coerceIn(0f, 1f) else 1f
                    val style = AppGlassStyle.menu(dark, tint).resolve(quality, material)
                    // 作者 long：外层图层已按同一帧轮廓裁剪，正文不再叠加第二次路径裁剪，避免每帧重复生成裁剪遮罩。
                        if (backdrop == null || opening.value >= .45f && active) {
                            if (backdrop == null || !renderer.draw(this, motion, backdrop,
                                Offset(-level.bounds.left, -level.bounds.top), tint, dark, paintScale = paintScale.coerceAtLeast(.001f), style = style, quality = quality)) {
                                if (backdrop != null) drawAppGlassFallback(motion, backdrop, Offset(-level.bounds.left, -level.bounds.top), style, quality, paintScale.coerceAtLeast(.001f))
                                else drawPath(motion.path(), surface)
                            }
                        }
                        this@drawWithContent.drawContent()
                        if (!top) drawPath(motion.path(), dim.copy(alpha = min(.4f, .2f * distance())))
                }) {
                CompositionLocalProvider(LocalAppMenuStack provides state, LocalAppMenuLevel provides level,
                    LocalAppMenuEnabled provides enabled, LocalAppMenuHeaderEnabled provides (active && top),
                    LocalAppMenuCarry provides if (top) LocalAppMenuCarry.current else null) {
                    Column(Modifier.then(if (!top) Modifier.clearAndSetSemantics {} else Modifier)
                        .verticalScroll(level.scroll, enabled = top && !state.folding).padding(8.dp)) { content(level.key) }
                }
                if (!top) Box(Modifier.matchParentSize().clickable(enabled = active) { state.onBack() }.clearAndSetSemantics {})
                }
            }
        } }
    }) { children, constraints ->
        val measured = children.mapIndexed { index, child -> child.measure(constraints.copy(minWidth = 0, minHeight = 0,
            maxHeight = (constraints.maxHeight - levels[index].top.roundToInt()).coerceAtLeast(1))) }
        val width = measured.maxOfOrNull { it.width } ?: 0
        val height = measured.mapIndexed { i, child -> child.height + levels[i].top.roundToInt() }.maxOrNull() ?: 0
        layout(width, height.coerceAtMost(constraints.maxHeight)) {
            measured.forEachIndexed { i, child -> child.place(width - child.width, levels[i].top.roundToInt()) }
        }
    }
}
