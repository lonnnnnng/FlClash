package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import kotlin.math.*

/** 作者 long：尺寸和位置直接对应 Flutter _dockLayout；三入口仍居中，窄屏下滑块不缩到84dp以下。 */
internal class DockGeometry(val width: Float, val height: Float, val count: Int, val unit: Float = 1f) {
    val padding = 4f * unit
    private val span = (width - padding * 2).coerceAtLeast(0f)
    val pitch: Float
    val lensWidth: Float
    val inset: Float
    init {
        require(count > 0 && width > 0 && height > 0)
        val basic = (span - padding * 2).coerceAtLeast(0f) / count
        if (count == 1) { pitch = (span - padding * 2).coerceAtLeast(1f); lensWidth = span; inset = padding }
        else if (basic + padding * 2 >= 84f * unit) { pitch = basic; lensWidth = basic + padding * 2; inset = padding }
        else { lensWidth = min(84f * unit, span); pitch = ((span - lensWidth) / (count - 1)).coerceAtLeast(1f); inset = (lensWidth - pitch) / 2 }
    }
    fun position(x: Float, rtl: Boolean): Float {
        val raw = ((if (rtl) width - x else x) - padding - inset) / pitch - .5f
        return when {
            raw < 0 -> rubberBand(raw, .35f)
            raw > count - 1 -> count - 1 + rubberBand(raw - count + 1, .35f)
            else -> raw
        }
    }
    fun index(position: Float): Int = position.roundToInt().coerceIn(0, count - 1)
    fun itemLeft(index: Int, rtl: Boolean): Float = if (rtl) width - padding - inset - (index + 1) * pitch else padding + inset + index * pitch
    fun lens(position: Float, lift: Float, jelly: Float, rtl: Boolean): Rect {
        val growth = 16f * unit * lift
        val w = (lensWidth + growth) * (1f + jelly)
        val h = (height - padding * 2 + growth) * (1f - jelly * 1.4f)
        val center = padding + inset + (position + .5f) * pitch
        return Rect((if (rtl) width - center else center) - w / 2, (height - h) / 2,
            (if (rtl) width - center else center) + w / 2, (height + h) / 2)
    }
}

@Stable
internal class DockMotion(val lens: AppSpring, val lift: AppSpring, val swell: AppSpring,
    val stretch: AppSpring, val jelly: AppSpring) {
    var pressedIndex: Int? = null; private set
    private var dropping = false
    init {
        lens.afterFrame = {
            val acceleration = lens.stiffness * (lens.target - lens.value) - 2f * lens.damping * sqrt(lens.stiffness) * lens.velocity
            val braking = max(0f, -acceleration * lens.velocity.sign)
            jelly.to(.2f * tanh(abs(lens.velocity) / 3f) - .15f * tanh(braking / 25f))
            dropLift()
        }
        lift.afterFrame = { dropLift() }
    }
    private fun dropLift() {
        if (dropping && pressedIndex == null && lift.value >= .975f && abs(lens.target - lens.value) <= .04f) {
            dropping = false; lift.to(0f)
        }
    }
    fun press(index: Int) { pressedIndex = index; dropping = false; lift.to(1f); swell.to(1f); lens.to(index.toFloat(), springStiffness(400), .85f) }
    fun slide(position: Float, index: Int, overshoot: Float, limit: Float) {
        pressedIndex = index; lens.to(position, 1024f, .9f)
        stretch.to(rubberBand(overshoot, limit), springStiffness(120), 1f)
    }
    fun release(index: Int) {
        pressedIndex = null; dropping = true
        lens.to(index.toFloat(), springStiffness(400), .85f)
        swell.to(0f); stretch.to(0f, 311f, .593f); dropLift()
    }
    fun sync(index: Int) { if (pressedIndex == null && lens.target != index.toFloat()) lens.to(index.toFloat(), springStiffness(400), .85f) }
}

@Composable
private fun rememberDockMotion(index: Int): DockMotion {
    val lens = rememberAppSpring(index.toFloat(), springStiffness(400), .85f)
    val lift = rememberAppSpring(0f, springStiffness(250), 1f)
    val swell = rememberAppSpring(0f)
    val stretch = rememberAppSpring(0f)
    val jelly = rememberAppSpring(0f, 100f, .4f, .003f)
    return remember { DockMotion(lens, lift, swell, stretch, jelly) }
}

@Composable
internal fun MainNavigationDock(selected: MainDestination, destinations: List<MainDestination>, onSelected: (MainDestination) -> Unit) {
    if (destinations.isEmpty()) return
    val index = destinations.indexOf(selected).coerceAtLeast(0)
    val motion = rememberDockMotion(index)
    val latestIndex by rememberUpdatedState(index)
    val latestSelection by rememberUpdatedState(onSelected)
    val density = LocalDensity.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
    val feedback = LocalHapticFeedback.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val height = (62 + 10f * (density.fontScale - 1f)).dp
    val colors = MaterialTheme.colorScheme
    val dark = LocalKClashDarkTheme.current
    val surface = if (dark) colors.surfaceContainerHigh else colors.surfaceBright
    val platter = if (dark) colors.surfaceContainerHighest else colors.surfaceContainer
    val shape = AppSuperellipseShape(1000.dp)
    SideEffect { motion.sync(index) }
    // 作者 long：外扩8dp只增加触摸范围；相应扣除外边距，保持绘制位置和页面占位不变。
    val hitSlop = 8.dp
    Box(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, bottom = maxOf(21.dp, bottomInset) - hitSlop),
        contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = if (destinations.size < 5) (destinations.size * 86 + 32).dp else 576.dp)
            .fillMaxWidth().height(height + hitSlop * 2)) {
            val geometry = remember(maxWidth, height, destinations.size, density.density) {
                with(density) { DockGeometry((maxWidth - hitSlop * 2).toPx(), height.toPx(), destinations.size, density.density) }
            }
            var request by remember { mutableIntStateOf(0) }
            LaunchedEffect(request, destinations) {
                if (request > 0) { withFrameNanos { }; motion.sync(latestIndex) }
            }
            val hitInset = with(density) { hitSlop.toPx() }
            Box(Modifier.fillMaxSize().pointerInput(destinations, geometry, rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down.consume()
                        val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis, down.position)
                        var drag = false; var committed = false
                        motion.press(geometry.index(geometry.position(down.position.x - hitInset, rtl)))
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(pointer.uptimeMillis, pointer.position)
                                if (!pointer.pressed) {
                                    // 作者 long：系统取消会合成已消费的抬手；只有真实释放才提交导航，取消须退回当前入口。
                                    if (pointer.isConsumed) break
                                    var chosen = motion.pressedIndex ?: latestIndex
                                    if (drag) {
                                        val lead = tracker.calculateVelocity().x / geometry.pitch * (if (rtl) -1f else 1f) * .1f
                                        chosen = (motion.lens.target + lead).roundToInt().coerceIn(chosen - 1, chosen + 1).coerceIn(0, destinations.lastIndex)
                                    }
                                    motion.release(chosen); pointer.consume(); committed = true
                                    // 作者 long：再次轻点当前入口也需交给路由处理，工具子页才能回根菜单并执行未保存确认；原地拖动不重复导航。
                                    if (!drag || chosen != latestIndex) latestSelection(destinations[chosen])
                                    request++
                                    break
                                }
                                if (!drag && abs(pointer.position.x - down.position.x) >= viewConfiguration.touchSlop) drag = true
                                if (drag) {
                                    val position = geometry.position(pointer.position.x - hitInset, rtl)
                                    val next = geometry.index(position)
                                    if (next != motion.pressedIndex) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    motion.slide(position, next, (pointer.position.x - hitInset).let { it - it.coerceIn(0f, geometry.width) }, geometry.height * 7f / 32f)
                                }
                                pointer.consume()
                            }
                        } finally { if (!committed) motion.release(latestIndex) }
                    }
                }) {
              Box(Modifier.fillMaxSize().padding(hitSlop).testTag("main_navigation_dock")
                  .dockPaintTransform(motion).appDockShadow(shape)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOutline(shape.createOutline(size, layoutDirection, this), surface)
                    val rect = geometry.lens(motion.lens.value, motion.lift.value, motion.jelly.value, rtl)
                    val tone = lerp(platter, colors.onSurface, .08f * motion.lift.value.coerceIn(0f, 1f))
                    withTransform({ translate(rect.left, rect.top) }) {
                        drawOutline(shape.createOutline(rect.size, layoutDirection, this), tone)
                    }
                }
                Row(Modifier.fillMaxSize().padding(horizontal = with(density) { (geometry.padding + geometry.inset).toDp() }, vertical = 4.dp)
                    .selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                    destinations.forEachIndexed { item, destination ->
                        val active = item == index
                        Column(Modifier.weight(1f).fillMaxHeight().appFocusRing(shape)
                            .selectable(active, role = Role.Tab, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                onClick = { latestSelection(destination) }).testTag("nav_${destination.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Column(Modifier.fillMaxWidth().dockLensTint(motion, geometry, item, rtl, colors.primary),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AppGlyphIcon(destination.icon, null, Modifier.size(28.dp), tint = colors.onSurfaceVariant, filled = true)
                                Text(destination.label, style = TextStyle(fontSize = 10.sp, lineHeight = 14.5.sp, fontWeight = FontWeight.Medium),
                                    color = colors.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
              }
            }
        }
    }
}

private fun Modifier.dockPaintTransform(motion: DockMotion) = drawWithContent {
    val growth = min(.25f, 16.dp.toPx() / size.maxDimension.coerceAtLeast(1f))
    val scale = 1f + motion.swell.value * growth
    withTransform({ translate(motion.stretch.value, 0f)
        scale(scale * (1f + abs(motion.stretch.value) / size.width * .5f), scale, center) }) { this@drawWithContent.drawContent() }
}

private fun Modifier.dockLensTint(motion: DockMotion, geometry: DockGeometry, index: Int, rtl: Boolean, tint: Color) = drawWithContent {
    val rect = geometry.lens(motion.lens.value, motion.lift.value, motion.jelly.value, rtl)
    val left = geometry.itemLeft(index, rtl)
    val top = (geometry.height - size.height) / 2f
    val path = superellipsePath(rect.size, List(4) { rect.height / 2f }).apply { translate(Offset(rect.left - left, rect.top - top)) }
    clipPath(path, ClipOp.Difference) { this@drawWithContent.drawContent() }
    clipPath(path) {
        val scale = 1f + .16f * motion.lift.value
        withTransform({ scale(scale, scale, Offset(rect.center.x - left, rect.center.y - top)) }) {
            // 作者 long：滑块逐部分染色和放大图文，布局与无障碍仍只有原来的一份入口。
            val paint = Paint().apply { colorFilter = ColorFilter.tint(tint) }
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
            this@drawWithContent.drawContent()
            drawContext.canvas.restore()
        }
    }
}
