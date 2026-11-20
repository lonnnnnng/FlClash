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

/** 作者 long：仅重放已存在的背景RenderNode；内部滚动/位移会随依赖层更新，静止和触点退光不启动采样协程。 */
internal fun Modifier.appLiveMaterial(shape: Shape, tint: Color, blurDp: Float = 2.5f,
    refractionDp: Float = 0f): Modifier = composed {
    val source = LocalAppLiveBackdrop.current
    if (!LocalAppMenuGlass.current || source == null) return@composed this
    val pane = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val optics = remember { if (Build.VERSION.SDK_INT >= 33) LivePaneOptics() else null }
    this.onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        val offset = origin - source.origin
        clipPath(path) {
            pane.record {
                translate(-offset.x, -offset.y) { drawLayer(source.layer) }
            }
            pane.renderEffect = if (Build.VERSION.SDK_INT >= 33) {
                optics?.effect(size.width, size.height, refractionDp * density, blurDp * density)
            } else if (Build.VERSION.SDK_INT >= 31 && blurDp > 0f) {
                BlurEffect(blurDp * density, blurDp * density, TileMode.Clamp)
            } else null
            drawLayer(pane)
            drawPath(path, tint)
            drawPath(path, Color.White.copy(alpha = .12f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        drawContent()
    }
}

@androidx.annotation.RequiresApi(33)
private class LivePaneOptics {
    private var effectKey: List<Float>? = null
    private var cachedEffect: RenderEffect? = null
    private val shader = android.graphics.RuntimeShader("""
        uniform shader content;
        uniform float2 extent;
        uniform float bend;
        half4 main(float2 p) {
            float2 q=(p-extent*.5)/max(extent*.5,float2(1));
            float edge=pow(max(abs(q.x),abs(q.y)),8.0);
            return content.eval(clamp(p-q*bend*edge,float2(0),extent));
        }
    """.trimIndent())
    fun effect(width: Float, height: Float, refraction: Float, blur: Float): RenderEffect {
        val key = listOf(width, height, refraction, blur)
        if (effectKey == key) return checkNotNull(cachedEffect)
        shader.setFloatUniform("extent", width, height)
        shader.setFloatUniform("bend", refraction)
        val optical = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val result = (if (blur > 0f) android.graphics.RenderEffect.createChainEffect(optical,
            android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP)) else optical).asComposeRenderEffect()
        effectKey = key; cachedEffect = result
        return result
    }
}

@Stable
internal class AppTouchLight(val intensity: AppSpring, val spread: AppSpring) {
    var point by mutableStateOf<Offset?>(null)
    fun down(at: Offset) { point = at; spread.snap(0f); intensity.to(1f, springStiffness(100), 1f) }
    fun move(at: Offset) { point = at }
    fun release() { intensity.to(0f, springStiffness(500), 1f); spread.to(1f, springStiffness(500), 1f) }
    fun reset() { intensity.snap(0f); spread.snap(0f); point = null }
}
@Composable
internal fun rememberAppTouchLight(): AppTouchLight {
    val intensity = rememberAppSpring(0f)
    val spread = rememberAppSpring(0f)
    return remember { AppTouchLight(intensity, spread) }
}

@Composable
internal fun Modifier.appGlassTouch(shape: Shape, enabled: Boolean = true): Modifier {
    if (!LocalAppMenuGlass.current) return this
    val light = rememberAppTouchLight()
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
    }.appTouchLight(light, shape)
}

internal fun Modifier.appTouchLight(light: AppTouchLight, shape: Shape, dock: Boolean = false): Modifier = drawWithContent {
    drawContent()
    val amount = light.intensity.value.coerceIn(0f, 1f)
    if (amount <= .001f) return@drawWithContent
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    val strength = if (dock) .06f * amount * amount else .16f * amount
    val reach = (if (dock) 28.dp.toPx() else size.height / sqrt(2f)) * (1f + 3f * light.spread.value)
    // 作者 long：Plus只增加材质亮度，取消与抬手同样退光；不使用SrcOver白色涟漪盖住控件颜色。
    clipPath(path) {
        if (dock) drawRect(Color.White.copy(alpha = strength * .7f), blendMode = BlendMode.Plus)
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = strength * if (dock) .3f else 1f), Color.Transparent),
            center = light.point ?: center, radius = reach.coerceAtLeast(1f)), blendMode = BlendMode.Plus)
    }
}
