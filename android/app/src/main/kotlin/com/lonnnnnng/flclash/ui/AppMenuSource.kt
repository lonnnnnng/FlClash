package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 作者 long：仅抓取源按钮的内存图像，成功后才隐藏原绘制；没有快照时保留可见按钮并采用普通退场。 */
@Stable
internal class AppMenuSource {
    var bounds by mutableStateOf(IntRect.Zero)
    var hidden by mutableStateOf(false)
    var color by mutableStateOf(Color.Unspecified)
    var layer: GraphicsLayer? = null
    suspend fun capture(): ImageBitmap? = try { layer?.toImageBitmap() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
}

@Composable
internal fun rememberAppMenuSource() = remember { AppMenuSource() }

/** 作者 long：液滴接管按钮快照后取消不可见涟漪，避免背景Window持续重绘；按钮回源后恢复原交互反馈。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppMenuSourceContent(source: AppMenuSource?, content: @Composable () -> Unit) {
    val ripple = LocalRippleConfiguration.current
    CompositionLocalProvider(LocalRippleConfiguration provides ripple.takeUnless { source?.hidden == true }, content = content)
}

internal fun Modifier.appMenuSource(source: AppMenuSource, color: Color = Color.Unspecified): Modifier = composed {
    val layer = rememberGraphicsLayer()
    // 作者 long：底色由真实控件提供，不从图标像素猜测；同一来源切换空态/顶栏或主题后使用新底色。
    SideEffect { source.color = color }
    DisposableEffect(source, layer) { source.layer = layer; onDispose { source.hidden = false; source.layer = null } }
    onGloballyPositioned { source.bounds = it.boundsInWindow().roundToIntRect() }
        .drawWithContent {
            if (!source.hidden) layer.record { this@drawWithContent.drawContent() }
            if (!source.hidden) drawLayer(layer)
        }
}

// 作者 long：上游玻璃默认关闭；材质入口独立于主题设置，开启时只持有当前菜单的一帧背景，不保存或导出页面图像。
internal val LocalAppMenuGlass = staticCompositionLocalOf { false }
internal val LocalAppGlassQuality = staticCompositionLocalOf { AppGlassQuality.ULTRA }

/** 作者 long：背景取宿主Window而不是Popup自身，避免折射层重复拍到菜单；取消后丢弃回调，图像从不进入存储。 */
internal suspend fun captureAppMenuBackdrop(view: View, density: Float): AppMenuBackdrop? {
    if (Build.VERSION.SDK_INT < 26 || !view.isAttachedToWindow) return null
    var context: Context = view.context
    while (context is ContextWrapper && context !is Activity) context = context.baseContext
    val dialog = generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    val window = dialog ?: (context as? Activity)?.window ?: return null
    val decor = window.decorView
    if (decor.width <= 0 || decor.height <= 0) return null
    val captured: Bitmap? = suspendCancellableCoroutine { continuation ->
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        runCatching { PixelCopy.request(window, bitmap, { result ->
            if (continuation.isActive) continuation.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
        }, Handler(Looper.getMainLooper())) }.onFailure { if (continuation.isActive) continuation.resume(null) }
    }
    return captured?.let { prepareAppMenuBackdrop(it, density) }
}
