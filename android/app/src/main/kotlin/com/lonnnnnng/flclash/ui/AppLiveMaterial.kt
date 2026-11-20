package com.lonnnnnng.flclash.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 作者 long：背景和前景是不同绘制子树；消费者只重放前面的GPU层，不截图、不把自己的文字/光效再采回背景。 */
@Stable
internal class AppLiveBackdrop(val layer: GraphicsLayer) {
    var origin = Offset.Zero
    var recordings = 0; internal set
}
internal val LocalAppLiveBackdrop = staticCompositionLocalOf<AppLiveBackdrop?> { null }

@Composable
internal fun AppLiveMaterialHost(modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit, foreground: @Composable BoxScope.() -> Unit) {
    val enabled = LocalAppMenuGlass.current
    val source = if (enabled) {
        val layer = rememberGraphicsLayer()
        remember(layer) { AppLiveBackdrop(layer) }
    } else null
    val recording = if (source != null) Modifier.graphicsLayer()
        .onGloballyPositioned { source.origin = it.positionInRoot() }.drawWithContent {
            source.layer.record { this@drawWithContent.drawContent() }
            source.recordings++
            drawLayer(source.layer)
        } else Modifier
    // 作者 long：开关只替换绘制修饰符，背景与前景保留同一组合位置；重建子树会丢掉仍活跃的表单和滚动状态。
    Box(modifier) {
        Box(Modifier.matchParentSize().then(recording), content = background)
        CompositionLocalProvider(LocalAppLiveBackdrop provides source) { foreground() }
    }
}

/** 作者 long：调用者声明真实材质，输入保留边缘外采样余量；默认关闭直接跳过，背景和前景从不互相采回。 */
internal fun Modifier.appLiveMaterial(shape: Shape, tint: Color, blurDp: Float = 2.5f,
    refractionDp: Float = 0f): Modifier = appLiveMaterial(shape,
        AppGlassStyle(tint, blur = blurDp, refraction = refractionDp, saturation = 1f, rim = 0f))

internal fun Modifier.appLiveMaterial(shape: Shape, style: AppGlassStyle,
    light: AppTouchLight? = null, dock: Boolean = false): Modifier = appLiveMaterialRegion(shape, { style }, light, dock)

internal fun Modifier.appLiveMaterialRegion(shape: Shape, material: () -> AppGlassStyle,
    light: AppTouchLight? = null, dock: Boolean = false,
    region: (androidx.compose.ui.geometry.Size) -> Rect = { Rect(Offset.Zero,it) }): Modifier = composed {
    val source = LocalAppLiveBackdrop.current
    if (!LocalAppMenuGlass.current || source == null) return@composed this
    val raw = rememberGraphicsLayer()
    val frost = rememberGraphicsLayer()
    val rimFrost = rememberGraphicsLayer()
    val haze = rememberGraphicsLayer()
    val atlas = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val optics = remember { if (Build.VERSION.SDK_INT >= 33) LivePaneOptics() else null }
    this.onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        val bounds = region(size)
        val paneSize = bounds.size
        if (bounds.isEmpty) { drawContent(); return@drawWithContent }
        val style = material()
        val outline = shape.createOutline(paneSize, layoutDirection, this)
        val path = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        val radius = when (shape) {
            is AppSuperellipseShape -> shape.topStart.toPx().coerceAtMost(paneSize.minDimension / 2)
            else -> (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: paneSize.minDimension / 2
        }
        val padding = kotlin.math.ceil((3f * maxOf(style.blur + style.rimBlur, style.hazeBlur) +
            maxOf(0f, -style.refraction) + style.dispersion + 2f) * density)
        val tile = androidx.compose.ui.unit.IntSize(kotlin.math.ceil(paneSize.width + 2 * padding).toInt(),
            kotlin.math.ceil(paneSize.height + 2 * padding).toInt())
        val offset = origin + bounds.topLeft - source.origin
        // 作者 long：record尺寸不是裁剪；零模糊槽若重放完整页面，会覆盖旁边的采样槽。
        raw.clip = true; frost.clip = true; rimFrost.clip = true; haze.clip = true
        raw.record(size = tile) { translate(padding - offset.x, padding - offset.y) { drawLayer(source.layer) } }
        if (Build.VERSION.SDK_INT >= 33 && optics != null) {
            // 作者 long：一个局部atlas同时保留raw、frost、rim与haze，避免把整块背景先模糊而丢掉上游pill的28%清晰输入。
            val layers = listOf(raw, frost, rimFrost, haze)
            val sigmas = listOf(0f, style.blur, style.blur + style.rimBlur, style.hazeBlur)
            for (i in 1..3) {
                layers[i].record(size = tile) { drawLayer(raw) }
                layers[i].renderEffect = optics.blur(sigmas[i] * density)
            }
            atlas.record(size = androidx.compose.ui.unit.IntSize(tile.width * 4, tile.height)) {
                for (i in 0..3) translate(i * tile.width.toFloat(), 0f) { drawLayer(layers[i]) }
            }
            atlas.renderEffect = optics.effect(paneSize.width, paneSize.height, tile.width.toFloat(), padding, radius,
                density, style, light, dock, shape is AppSuperellipseShape)
            translate(bounds.left,bounds.top) { clipPath(path) { translate(-padding, -padding) { drawLayer(atlas) } } }
        } else {
            val sigma = style.effective(AppGlassQuality.STANDARD).blur
            raw.renderEffect = if (Build.VERSION.SDK_INT >= 31 && sigma > 0f)
                BlurEffect(sigma * density, sigma * density, TileMode.Clamp) else null
            translate(bounds.left,bounds.top) { clipPath(path) {
                translate(-padding, -padding) { drawLayer(raw) }
                drawPath(path, style.tint)
            } }
        }
        drawContent()
    }
}

/** 作者 long：镜片文字采样只包含图标/标签，背景镜片另画；普通文字在镜片外保留，避免叠画原字导致分色残影。 */
internal fun Modifier.appLensContent(shape: Shape, material: () -> AppGlassStyle,
    strength: () -> Float, region: (androidx.compose.ui.geometry.Size) -> Rect): Modifier = composed {
    if (!LocalAppMenuGlass.current || Build.VERSION.SDK_INT < 33) return@composed this
    val raw = rememberGraphicsLayer(); val frost = rememberGraphicsLayer()
    val rim = rememberGraphicsLayer(); val atlas = rememberGraphicsLayer()
    val optics = remember { LivePaneOptics() }
    drawWithContent {
        val amount = strength().coerceIn(0f,1f)
        if (amount <= .001f) { drawContent(); return@drawWithContent }
        val bounds = region(size)
        val style = material().strength(amount)
        val padding = kotlin.math.ceil((3f * (style.blur + style.rimBlur) + maxOf(0f,-style.refraction) + style.dispersion + 2f)*density)
        val tile = androidx.compose.ui.unit.IntSize(kotlin.math.ceil(bounds.width+2*padding).toInt(),kotlin.math.ceil(bounds.height+2*padding).toInt())
        val localPath = (shape.createOutline(bounds.size,layoutDirection,this) as Outline.Generic).path
        val path = Path().apply { addPath(localPath,bounds.topLeft) }
        raw.clip=true;frost.clip=true;rim.clip=true
        raw.record(size=tile) { translate(padding-bounds.left,padding-bounds.top) { this@drawWithContent.drawContent() } }
        frost.record(size=tile) { drawLayer(raw) };frost.renderEffect=optics.blur(style.blur*density)
        rim.record(size=tile) { drawLayer(raw) };rim.renderEffect=optics.blur((style.blur+style.rimBlur)*density)
        atlas.record(size=androidx.compose.ui.unit.IntSize(tile.width*4,tile.height)) {
            drawLayer(raw);translate(tile.width.toFloat(),0f){drawLayer(frost)}
            translate(tile.width*2f,0f){drawLayer(rim)};translate(tile.width*3f,0f){drawLayer(raw)}
        }
        atlas.renderEffect=optics.effect(bounds.width,bounds.height,tile.width.toFloat(),padding,bounds.size.minDimension/2,
            density,style,null,false,true)
        clipPath(path,ClipOp.Difference) { this@drawWithContent.drawContent() }
        clipPath(path) { translate(bounds.left-padding,bounds.top-padding) { drawLayer(atlas) } }
    }
}

@Stable
internal class AppTouchLight(private val intensity: AppSpring? = null, private val spread: AppSpring? = null,
    private val animation: Animatable<Float, AnimationVector1D>? = null, private val scope: CoroutineScope? = null) {
    var point by mutableStateOf<Offset?>(null)
    private var pressed = false
    private var job: Job? = null
    val amount get() = animation?.value ?: checkNotNull(intensity).value
    val spreadAmount get() = if (animation != null) { if (pressed) 0f else 1f-animation.value } else checkNotNull(spread).value
    fun down(at: Offset) {
        point = at; pressed = true
        if (animation == null) { spread?.snap(0f); intensity?.to(1f, springStiffness(100), 1f) }
        else { job?.cancel(); job = scope?.launch { animation.animateTo(1f, tween(120,easing = CubicBezierEasing(0f,0f,.58f,1f))) } }
    }
    fun move(at: Offset) { point = at }
    fun release() {
        pressed = false
        if (animation == null) { intensity?.to(0f,springStiffness(500),1f); spread?.to(1f,springStiffness(500),1f) }
        else { job?.cancel(); job = scope?.launch { animation.animateTo(0f,tween(360,easing = CubicBezierEasing(0f,0f,.58f,1f))) } }
    }
    fun reset() { point = null; pressed = false; job?.cancel(); intensity?.snap(0f); spread?.snap(0f)
        if (animation != null) job = scope?.launch { animation.snapTo(0f) } }
}
@Composable
internal fun rememberAppTouchLight(button: Boolean = false): AppTouchLight {
    if (button) {
        val animation = remember { Animatable(0f) }; val scope = rememberCoroutineScope()
        return remember { AppTouchLight(animation = animation, scope = scope) }
    }
    val intensity = rememberAppSpring(0f); val spread = rememberAppSpring(0f)
    return remember { AppTouchLight(intensity,spread) }
}

@Composable
internal fun Modifier.appLiveButton(shape: Shape, style: AppGlassStyle, enabled: Boolean): Modifier {
    if (!LocalAppMenuGlass.current) return this
    val light = rememberAppTouchLight(button = true)
    return appLiveMaterial(shape,style,light).appGlassTouch(shape,enabled,light,drawLight = false)
}

@Composable
internal fun Modifier.appGlassTouch(shape: Shape, enabled: Boolean = true, sharedLight: AppTouchLight? = null, drawLight: Boolean = true): Modifier {
    if (!LocalAppMenuGlass.current) return this
    val light = sharedLight ?: rememberAppTouchLight(button = true)
    LaunchedEffect(enabled) { if (!enabled) light.reset() }
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            light.down(down.position)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val point = event.changes.firstOrNull { it.id == down.id } ?: break
                    light.move(point.position)
                    if (point.isConsumed && point.pressed && point.position != point.previousPosition) break
                } while (point.pressed)
            } finally { light.release() }
        }
    }.then(if (drawLight) Modifier.appTouchLight(light, shape) else Modifier)
}

internal fun Modifier.appTouchLight(light: AppTouchLight, shape: Shape, dock: Boolean = false): Modifier = drawWithContent {
    val amount = light.amount.coerceIn(0f, 1f)
    if (amount <= .001f) { drawContent(); return@drawWithContent }
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    val strength = if (dock) .06f * amount * amount else .16f * amount
    val reach = (if (dock) 28.dp.toPx() else size.height / sqrt(2f)) * (1f + 3f * light.spreadAmount)
    // 作者 long：Plus只增加材质亮度，取消与抬手同样退光；不使用SrcOver白色涟漪盖住控件颜色。
    clipPath(path) {
        if (dock) drawRect(Color.White.copy(alpha = strength * .7f), blendMode = BlendMode.Plus)
        // 作者 long：高斯退光保留reach之外的柔尾；先画材质光再画前景，不能照白文字和图标。
        val stops = (0..24).map { i ->
            val distance = i / 8f
            i / 24f to Color.White.copy(alpha = strength * (if (dock) .3f else 1f) * kotlin.math.exp(-distance*distance))
        }.toTypedArray()
        drawRect(Brush.radialGradient(*stops, center = light.point ?: center, radius = 3f * reach.coerceAtLeast(1f)), blendMode = BlendMode.Plus)
    }
    drawContent()
}
