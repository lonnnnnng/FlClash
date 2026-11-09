package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

internal val SelectionEntrySizeCurve = CubicBezierEasing(.645f, .045f, .355f, 1f)

/** 作者 long：固定Prism EntryTransition用300ms线性时钟分别驱动尺寸和后半段文字透明度；正文只裁剪不压扁，退场项不再提交。 */
@Composable
internal fun SelectionEntryTransition(visible: Boolean, modifier: Modifier = Modifier, onHidden: () -> Unit = {}, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val hidden by rememberUpdatedState(onHidden)
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, tween(300, easing = LinearEasing))
        if (!visible) hidden()
    }
    // 作者 long：每帧只重排裁剪高度和更新图层；父层收到完成回执再移除条目，避免文字随动画反复重组。
    Box(modifier.clipToBounds().layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minHeight = 0))
        val height = (child.height * SelectionEntrySizeCurve.transform(progress.value)).roundToInt()
        layout(child.width, height) { child.place(0, 0) }
    }.graphicsLayer { alpha = ((progress.value - .5f) * 2f).coerceIn(0f, 1f) }) { content() }
}
