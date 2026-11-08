package com.lonnnnnng.flclash.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.*

internal data class AppMenuMorphFrame(val platter: Rect, val radius: Float, val progress: Float,
    val droplet: Rect? = null, val blend: Float = 0f, val blur: Float = 0f, val sourceAlpha: Float = 0f,
    val materialProgress: Float = 1f, val frostProgress: Float = 1f,
    val returning: Boolean = false, val shadowProgress: Float = 1f, val contentBounds: Rect? = null,
    val sourceBlur: Float = 0f, val opening: Boolean = false) {
    // 作者 long：一份不可变帧同时供阴影、裁剪和材质读取；只算一次轮廓，读者不能修改返回的Path。
    private val platterPath by lazy(LazyThreadSafetyMode.NONE) {
        superellipsePath(platter.size, List(4) { radius }).apply { translate(platter.topLeft) }
    }
    private val outlinePath by lazy(LazyThreadSafetyMode.NONE) { droplet?.let { appMenuGooPath(platter, radius, it, blend) } ?: platterPath }
    fun path() = platterPath
    fun outline() = outlinePath
}

// 作者 long：Flutter的29dp是blurRadius而非sigma；Android在物理像素上再换算，补偿常数项才能跨密度保留同一模糊宽度。
internal const val APP_MENU_SHADOW_SIGMA_DP = 29f * .57735f + .5f
internal fun appMenuShadowRadiusPx(density: Float) = ((APP_MENU_SHADOW_SIGMA_DP * density - .5f) / .57735f).coerceAtLeast(0f)

internal fun AppMenuMorphFrame.outerShadowStrength(glass: Boolean) =
    shadowProgress * if (returning && !glass) 1f - progress else 1f

internal fun AppMenuMorphFrame.contentScale(size: Size): Float {
    val source = contentBounds?.size ?: size
    val widthScale = platter.width / source.width.coerceAtLeast(1f)
    return if (returning) min(widthScale, platter.height / source.height.coerceAtLeast(1f)) else widthScale
}

/** 作者 long：外层只投影platter，液滴内部不染黑；普通回源由正文阴影渐交给外层，玻璃则由外层贯穿承担。 */
@Composable
internal fun AppMenuPlatterShadow(modifier: Modifier, size: State<IntSize>, frame: (Size) -> AppMenuMorphFrame,
    visible: () -> Boolean, dark: Boolean, glass: Boolean) {
    if (!visible()) return
    val measured = size.value
    if (measured.width <= 0 || measured.height <= 0) return
    val current = frame(Size(measured.width.toFloat(), measured.height.toFloat()))
    val strength = current.outerShadowStrength(glass)
    if (strength <= 0f) return
    val shape = remember(current.platter, current.radius) { object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Generic(current.path())
    } }
    val radius = with(LocalDensity.current) { appMenuShadowRadiusPx(density).toDp() }
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.ModulateAlpha; alpha = strength }
        .drawWithContent {
            // 作者 long：Flutter的evenOdd裁剪先限定三倍sigma加偏移的外界，再扣内部；不让反向路径裁剪扩展到整个透明Window。
            val bounds = current.outline().getBounds().inflate((3 * APP_MENU_SHADOW_SIGMA_DP + 8f) * density)
            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                clipPath(current.outline(), clipOp = ClipOp.Difference) { this@drawWithContent.drawContent() }
            }
        }
        .dropShadow(shape, Shadow(radius = radius,
            color = Color.Black.copy(alpha = .118f * if (dark) 1.5f else 1f), offset = DpOffset(0.dp, 8.dp))))
}

/** 作者 long：只保留最后一份几何，不把每帧路径长期塞进缓存；同一时刻的多次绘制共享路径。 */
internal class AppMenuFrameCache {
    private var previous: AppMenuMorphFrame? = null
    fun resolve(frame: AppMenuMorphFrame): AppMenuMorphFrame = previous?.takeIf { it == frame } ?: frame.also { previous = it }
}

/** 作者 long：变形轮廓仍需重新生成阴影，但逐帧状态只重组阴影本身，不能把菜单行、测量和焦点树一起重建。 */
@Composable
internal fun AppMenuFrameShadow(modifier: Modifier, size: State<IntSize>, frame: (Size) -> AppMenuMorphFrame,
    visible: () -> Boolean, opacity: () -> Float, dark: Boolean) {
    if (!visible()) return
    val measured = size.value
    if (measured.width <= 0 || measured.height <= 0) return
    val current = frame(Size(measured.width.toFloat(), measured.height.toFloat()))
    val shape = remember(current) { AppMenuMorphShape { current } }
    val radius = with(LocalDensity.current) { appMenuShadowRadiusPx(density).toDp() }
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.ModulateAlpha; alpha = opacity() }
        .dropShadow(shape, Shadow(radius = radius,
            color = Color.Black.copy(alpha = .118f * if (dark) 1.5f else 1f), offset = DpOffset(0.dp, 8.dp))))
}

private fun menuSpring(stiffness: Float, damping: Float, time: Float): Float {
    val omega = sqrt(stiffness); val damped = omega * sqrt(1f - damping * damping)
    return 1f - exp(-damping * omega * time) * (cos(damped * time) + damping * omega / damped * sin(damped * time))
}

private fun riseAndFall(time: Float, peak: Float, shape: Float): Float {
    val x = (time / peak).coerceAtLeast(0f)
    return x.pow(shape) * exp(shape * (1f - x))
}

private val menuEaseIn = CubicBezierEasing(.42f, 0f, 1f, 1f)
private val menuEaseOut = CubicBezierEasing(0f, 0f, .58f, 1f)
private val menuEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val menuGlyphCurve = CubicBezierEasing(.25f, .1f, .25f, 1f)

// 作者 long：胶囊的长边在交接按钮时保留，不能让横向按钮先缩成圆再突然恢复原宽。
private fun beyondRound(anchor: Rect) = Size(max(0f, anchor.width - anchor.height), max(0f, anchor.height - anchor.width))

/** 作者 long：参数来自Flutter popup.dart，位置、等比内容缩放和延迟70ms的角部弹簧分别计算，文字不会随卡片高度被拉扁。 */
internal fun appMenuMorphFrame(anchor: Rect, card: Size, seconds: Float, unit: Float, source: Boolean = false): AppMenuMorphFrame {
    val progress = if (seconds >= .45f) 1f else menuSpring(400f, .754f, seconds.coerceAtLeast(0f))
    val scale = (.41f * anchor.size.minDimension / card.width).let { it + (1f - it) * progress }.coerceAtLeast(0f)
    val height = card.width + (card.height - card.width) * progress
    val distance = card.height / 2f - anchor.center.y
    val dip = if (source) sign(distance) * (min(1f, abs(distance) / (30f * unit)) * 23f * unit + .124f * abs(distance)) * riseAndFall(seconds, .098f, 3.3f) else 0f
    val center = anchor.center + (Offset(card.width / 2f, card.height / 2f) - anchor.center) * progress + Offset(0f, dip)
    val beyond = beyondRound(anchor) * (1f - progress).coerceIn(0f, 1f)
    val size = Size((card.width * scale + beyond.width).coerceAtLeast(0f), (height * scale + beyond.height).coerceAtLeast(0f))
    val corners = if (seconds >= .45f) 0f else 1f - menuSpring(324f, .75f, (seconds - .07f).coerceAtLeast(0f))
    val radius = (min(height / 2f, 24f * unit + card.width * .48f * corners) * scale).coerceAtLeast(0f)
    val droplet = if (!source) null else {
        val extent = anchor.size * (1f - .75f * progress)
        val inner = Rect(Offset.Zero, card).deflate(anchor.size.maxDimension * .125f + 36f * unit)
        val sunk = anchor.center + (Offset(card.width / 2f, card.height / 2f) - anchor.center) * .25f
        val target = if (inner.isEmpty) Offset(card.width / 2f, card.height / 2f) else Offset(sunk.x.coerceIn(inner.left, inner.right), sunk.y.coerceIn(inner.top, inner.bottom))
        val at = anchor.center + (target - anchor.center) * progress
        Rect(at - Offset(extent.width / 2, extent.height / 2), extent)
    }
    val settled = progress.coerceIn(0f, 1f)
    return AppMenuMorphFrame(Rect(center - Offset(size.width / 2f, size.height / 2f), size), radius, settled,
        droplet, 28f * unit, sqrt((4f * (1f - settled)).pow(2) + (1.9f * riseAndFall(seconds, .069f, 2.1f)).pow(2)) * unit,
        1f - min(1f, settled / .25f), materialProgress = settled, shadowProgress = settled,
        sourceBlur = 4f * min(1f, settled / .25f) * unit, opening = seconds < .45f)
}

/** 作者 long：回源从实际中断帧起算，500ms内消除弹簧余振；100ms隐去文字，470ms重建按钮图像。 */
internal fun appMenuReturnFrame(anchor: Rect, from: AppMenuMorphFrame, seconds: Float, unit: Float): AppMenuMorphFrame {
    if (seconds >= .5f) return AppMenuMorphFrame(anchor, anchor.size.minDimension / 2f, 0f, anchor, 0f, 0f, 1f,
        materialProgress = 0f, frostProgress = 0f, returning = true, shadowProgress = 0f)
    val ringing = 1f - menuEaseIn.transform(((seconds - .4f) / .1f).coerceIn(0f, 1f))
    val p = 1f + (menuSpring(165f, .8f, seconds) - 1f) * ringing
    val distance = from.platter.center.y - anchor.center.y
    val dip = min(.21f * abs(distance), 22f * unit) * riseAndFall(seconds, .072f, 2.4f)
    val lift = min(.25f * abs(distance), 19f * unit) * riseAndFall(seconds, .176f, 4.9f)
    val scale = 1f + (.41f * anchor.size.minDimension / from.platter.size.maxDimension.coerceAtLeast(1f) - 1f) * p
    val beyond = beyondRound(anchor) * p.coerceIn(0f, 1f)
    val size = Size(max(0f, from.platter.width * scale + beyond.width), max(0f, (from.platter.height + (from.platter.width - from.platter.height) * p) * scale + beyond.height))
    val center = from.platter.center + (anchor.center - from.platter.center) * p + Offset(0f, sign(distance) * dip * ringing)
    val origin = from.droplet ?: Rect(anchor.center + (from.platter.center - anchor.center) * .25f - Offset(anchor.width / 8, anchor.height / 8), anchor.size * .25f)
    val dropSize = Size(max(0f, origin.width + (anchor.width - origin.width) * p), max(0f, origin.height + (anchor.height - origin.height) * p))
    val dropCenter = origin.center + (anchor.center - origin.center) * p - Offset(0f, sign(distance) * lift * ringing)
    val absorbed = menuEaseInOut.transform(((seconds - .3f) / .15f).coerceIn(0f, 1f))
    val reveal = from.progress * (1f - menuEaseOut.transform((seconds / .1f).coerceIn(0f, 1f)))
    // 作者 long：稳定菜单遵循Flutter的20%源图与50ms桥接；快速撤销开场仍从已显示的光学帧接续，避免按钮闪跳。
    val bridge = if (from.opening) from.blend else 28f * unit * min(1f, seconds / .05f)
    val blur = 8f * unit * (1f - reveal)
    val sourceAlpha = if (from.opening) from.sourceAlpha else .2f
    return AppMenuMorphFrame(Rect(center - Offset(size.width / 2, size.height / 2), size),
        max(0f, (from.radius + (from.platter.width / 2 - from.radius) * p) * scale), reveal,
        Rect(dropCenter - Offset(dropSize.width / 2, dropSize.height / 2), dropSize), bridge * (1f - absorbed),
        if (from.opening) from.blur + (blur - from.blur) * min(1f, seconds / .1f) else blur,
        sourceAlpha + (1f - sourceAlpha) * menuGlyphCurve.transform((seconds / .47f).coerceIn(0f, 1f)),
        materialProgress = from.materialProgress * (1f - p).coerceIn(0f, 1f),
        frostProgress = from.frostProgress * (1f - absorbed), returning = true, shadowProgress = (1f - p).coerceIn(0f, 1f),
        contentBounds = from.contentBounds,
        sourceBlur = if (from.opening) from.sourceBlur * (1f - min(1f, seconds / .1f)) else 0f)
}

// 作者 long：Android RenderEffect内部会把radius换成sigma；Flutter直接给sigma，需反算才能保留相同屏幕模糊宽度。
internal fun appMenuBlurRadiusPx(sigma: Float) = ((sigma - .5f) / .57735f).coerceAtLeast(0f)

/** 作者 long：先在屏幕坐标中记录缩放后的内容，再模糊整层；留足核宽防止缩小时模糊变窄或边缘被离屏纹理裁断。 */
internal fun DrawScope.drawAppMenuFiltered(layer: GraphicsLayer, bounds: Rect, sigma: Float, alpha: Float,
    block: DrawScope.() -> Unit) {
    if (alpha <= 0f || bounds.isEmpty) return
    val radius = appMenuBlurRadiusPx(sigma)
    val blurred = android.os.Build.VERSION.SDK_INT >= 31 && sigma >= .1f * density && radius > 0f
    val reach = if (blurred) ceil(3f * sigma) else 0f
    val left = floor(bounds.left - reach)
    val top = floor(bounds.top - reach)
    layer.record(size = IntSize(ceil(bounds.right + reach - left).toInt().coerceAtLeast(1),
        ceil(bounds.bottom + reach - top).toInt().coerceAtLeast(1))) {
        withTransform({ translate(-left, -top) }, block)
    }
    layer.alpha = alpha.coerceIn(0f, 1f)
    layer.renderEffect = if (blurred) BlurEffect(radius, radius, TileMode.Decal) else null
    withTransform({ translate(left, top) }) { drawLayer(layer) }
}

internal class AppMenuMorphShape(private val frame: (Size) -> AppMenuMorphFrame) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Generic(frame(size).outline())
}

internal fun Modifier.appMenuMorph(surface: Color, frame: (Size) -> AppMenuMorphFrame, source: ImageBitmap? = null,
    backdrop: AppMenuBackdrop? = null, backdropOffset: Offset = Offset.Zero,
    drawSurface: () -> Boolean = { true }) = composed {
    val glass = remember { AppMenuGlassRenderer() }
    val dark = LocalKClashDarkTheme.current
    // 作者 long：源按钮和菜单分别采用共享regular/menu材质，深浅色参数只来自固定上游，不在动效中混用主题容器槽。
    val tint = MaterialTheme.colorScheme.surfaceBright
    val regular = AppGlassStyle.regular(dark)
    val menu = AppGlassStyle.menu(dark, tint)
    val quality = LocalAppGlassQuality.current
    val contentLayer = rememberGraphicsLayer()
    val sourceLayer = rememberGraphicsLayer()
    drawWithContent {
        if (!drawSurface()) { drawContent(); return@drawWithContent }
        val motion = frame(size)
        val scale = motion.contentScale(size)
        val contentCenter = motion.contentBounds?.center ?: Offset(size.width / 2f, size.height / 2f)
        val path = motion.path()
        val outline = motion.outline()
        // 作者 long：文字100ms先退场，材质沿回源弹簧继续；磨砂只在按钮吸收阶段减弱，三条时间线互不替代。
        val material = regular.interpolate(menu, motion.materialProgress).copy(blur = regular.blur + (menu.blur - regular.blur) * motion.frostProgress)
        val refracted = backdrop != null && glass.draw(this, motion, backdrop, backdropOffset, tint, dark, style = material, quality = quality)
        if (!refracted) {
            if (backdrop != null) clipPath(outline) { drawAppGlassFallback(motion, backdrop, backdropOffset, material, quality) }
            else drawPath(outline, surface)
        }
        motion.droplet?.let { drop -> if (source != null && drop.width > 0 && drop.height > 0) {
            drawAppMenuFiltered(sourceLayer, drop, motion.sourceBlur, motion.sourceAlpha) {
                // 作者 long：浮点变换保留亚像素轨迹，整数目标框会让小图标在吸收阶段逐像素抖动。
                withTransform({ translate(drop.left, drop.top); scale(drop.width / source.width, drop.height / source.height, Offset.Zero) }) {
                    drawImage(source, dstSize = IntSize(source.width, source.height), filterQuality = FilterQuality.Medium)
                }
            }
        } }
        val content: ContentDrawScope.() -> Unit = {
            val contentSize = motion.contentBounds?.size ?: size
            val painted = Rect(motion.platter.center - Offset(contentSize.width, contentSize.height) * (scale / 2f), contentSize * scale)
            // 作者 long：回源正文带着自身阴影淡出；阴影先随卡片缩放，之后正文模糊再按屏幕sigma外扩。
            val reach = if (motion.returning) (3 * APP_MENU_SHADOW_SIGMA_DP + 8f) * density * scale else 0f
            drawAppMenuFiltered(contentLayer, painted.inflate(reach), motion.blur, motion.progress) {
                withTransform({
                    translate(motion.platter.center.x, motion.platter.center.y)
                    scale(scale, scale, Offset.Zero)
                    translate(-contentCenter.x, -contentCenter.y)
                }) {
                    this@drawWithContent.drawContent()
                }
            }
        }
        if (motion.returning) content() else clipPath(path) { content(this@drawWithContent) }
}
}
