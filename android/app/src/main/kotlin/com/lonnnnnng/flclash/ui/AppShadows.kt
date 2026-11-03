package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme

/** 作者 long：Flutter的卡片投影由多层偏移和负扩展组成，Material单一elevation无法表达相同层次。 */
@Composable
internal fun Modifier.appDashboardShadow(): Modifier {
    fun layer(alpha: Int, y: Float, blur: Float, spread: Float = 0f) = Shadow(
        radius = (if (blur <= 1f) 0f else (blur / 2f - .5f) / .57735f).dp,
        color = Color.Black.copy(alpha = alpha / 255f), offset = DpOffset(0.dp, y.dp), spread = spread.dp)
    val layers = if (LocalKClashDarkTheme.current) listOf(layer(0x80, 1f, 2f), layer(0x8C, 10f, 28f, -6f))
        else listOf(layer(0x0D, .5f, 1f), layer(0x0A, 2f, 6f), layer(0x17, 12f, 32f, -6f))
    return layers.fold(this) { modifier, shadow -> modifier.dropShadow(AppSuperellipseShape(34.dp), shadow) }
}

@Composable
internal fun Modifier.appDockShadow(shape: Shape): Modifier {
    val strength = if (LocalKClashDarkTheme.current) 3f else 1f
    val color = Color.Black
    return dropShadow(shape, Shadow(radius = 24.dp, color = color.copy(alpha = .08f * strength), offset = DpOffset(0.dp, 8.dp)))
        .dropShadow(shape, Shadow(radius = 3.dp, color = color.copy(alpha = .04f * strength), offset = DpOffset(0.dp, 1.dp)))
}
