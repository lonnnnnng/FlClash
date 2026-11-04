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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
    var bounds = Rect.Zero
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
    backdrop: AppMenuBackdrop?, menuPosition: IntOffset, opening: Float, returning: Float,
    onScrollable: (Boolean) -> Unit, content: @Composable ColumnScope.(Any?) -> Unit) {
    val density = LocalDensity.current.density
    val levels = state.levels.toList()
    Layout(content = {
        levels.forEachIndexed { index, level -> key(level) {
            val top = index == levels.lastIndex
            val distance = if (top) 0f else levels.lastIndex - index - 1 + state.unfold.value.coerceIn(0f, 1f)
            val scale = max(0f, 1f - .05f * distance)
            val frame: (Size) -> AppMenuMorphFrame = { size ->
                val p = if (top && index > 0) state.unfold.value else 1f
                val rect = if (index == 0 || !top) Rect(Offset.Zero, size) else appMenuStackRect(size, level.top, level.ownerTop,
                    level.owner?.bounds?.height ?: 48f * density, p, density)
                AppMenuMorphFrame(rect, max(0f, (16f + 8f * p) * density), 1f)
            }
            val renderer = remember { AppMenuGlassRenderer() }
            val dark = com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme.current
            val tint = if (dark) Color(0xFF343537) else MaterialTheme.colorScheme.surfaceBright
            val dim = MaterialTheme.colorScheme.surface
            val material = if (top && index > 0) (state.unfold.value / .35f).coerceIn(0f, 1f) else 1f
            val enabled = active && top && !state.folding && level.key == pageKey
            // 作者 long：maxValue首帧尚未测量时是Int.MAX_VALUE；在组合阶段订阅它，测量后必须撤回“可滚动”，否则短菜单会永久拒绝拖选。
            val hasScroll = level.scroll.maxValue > 0
            SideEffect { if (top) onScrollable(hasScroll) }
            val minWidth = if (index == 0) 160.dp else with(LocalDensity.current) { (level.fromWidth * 1.12f).toDp().coerceAtMost(280.dp) }
            val cardWidth = if (index == 0) Modifier.widthIn(min = minWidth, max = 280.dp).width(IntrinsicSize.Max)
                else Modifier.width(with(LocalDensity.current) {
                    val from = level.fromWidth.toDp().coerceIn(160.dp, 280.dp)
                    val p = if (top) (state.unfold.value / .3f).coerceIn(0f, 1f) else 1f
                    from + (minWidth - from) * p
                })
            Box(cardWidth
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(1f, if (size.height > 0) ((levels.last().top - level.top) / size.height).coerceIn(0f, 1f) else 0f)
                }
                .dropShadow(AppMenuMorphShape(frame), Shadow(radius = ((29f / 2f - .5f) / .57735f).dp,
                    color = Color.Black.copy(alpha = if (opening < .45f || !active) 0f else .118f * (if (dark) 1.5f else 1f) *
                        if (top && index > 0) state.unfold.value.coerceIn(0f, 1f) else max(1f / 6, 1f - distance / 3)), offset = DpOffset(0.dp, 8.dp)))
                .graphicsLayer { this.shape = AppMenuMorphShape(frame); clip = true }
                .onGloballyPositioned {
                    // 作者 long：采样原点使用未裁剪的局部原点；展开裁剪不能反过来移动背景，否则边缘会随高度游走。
                    level.bounds = Rect(it.localToWindow(Offset.Zero), Size(it.size.width * scale, it.size.height * scale))
                }
                .drawWithContent {
                    val motion = frame(size)
                    if (top) {
                        val rect = motion.platter.translate(level.bounds.topLeft)
                        val hit = state.outerBounds?.let(rect::intersect) ?: rect
                        if (state.hitBounds != hit) { state.hitBounds = hit; state.hitRevision++ }
                    }
                    clipPath(motion.path()) {
                        if (opening >= .45f && active) {
                            if (backdrop == null || !renderer.draw(this, motion, backdrop,
                                Offset(-level.bounds.left, -level.bounds.top), tint, dark, paintScale = scale.coerceAtLeast(.001f), amount = material)) {
                                if (backdrop != null) drawAppMenuFrost(backdrop, Offset(-level.bounds.left, -level.bounds.top), scale.coerceAtLeast(.001f))
                                drawPath(motion.path(), surface.copy(alpha = if (backdrop == null) 1f else .72f))
                            }
                        }
                        this@drawWithContent.drawContent()
                        if (!top) drawPath(motion.path(), dim.copy(alpha = min(.4f, .2f * distance)))
                    }
                }.testTag("app_menu_level_$index")) {
                CompositionLocalProvider(LocalAppMenuStack provides state, LocalAppMenuLevel provides level,
                    LocalAppMenuEnabled provides enabled, LocalAppMenuHeaderEnabled provides (active && top),
                    LocalAppMenuCarry provides if (top) LocalAppMenuCarry.current else null) {
                    Column(Modifier.then(if (!top) Modifier.clearAndSetSemantics {} else Modifier)
                        .verticalScroll(level.scroll, enabled = top && !state.folding).padding(8.dp).graphicsLayer {
                            val time = if (active) opening else returning
                            val blur = if (active) 4f * (1f - (time / .25f).coerceIn(0f, 1f)) else 8f * (time / .1f).coerceIn(0f, 1f)
                            renderEffect = if (blur > .1f && android.os.Build.VERSION.SDK_INT >= 31) BlurEffect(blur * density, blur * density) else null
                        }) { content(level.key) }
                }
                if (!top) Box(Modifier.matchParentSize().clickable(enabled = active) { state.onBack() }.clearAndSetSemantics {})
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
