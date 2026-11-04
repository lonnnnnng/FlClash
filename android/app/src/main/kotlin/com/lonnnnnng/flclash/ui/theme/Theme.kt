package com.lonnnnnng.flclash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 作者 long：对应上游withAppTypography的语义槽；继续使用Android系统字族，避免借排版对齐引入非Android字体。
private fun appText(size: Int, leading: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, lineHeight = leading.sp, fontWeight = weight, letterSpacing = 0.sp)
private val FlutterTypography = Typography(
    displayLarge = appText(34, 41), displayMedium = appText(34, 41), displaySmall = appText(34, 41),
    headlineLarge = appText(34, 41), headlineMedium = appText(28, 34), headlineSmall = appText(22, 28),
    titleLarge = appText(20, 25), titleMedium = appText(17, 22, FontWeight.SemiBold), titleSmall = appText(15, 20, FontWeight.SemiBold),
    bodyLarge = appText(17, 22), bodyMedium = appText(15, 20), bodySmall = appText(13, 18),
    labelLarge = appText(15, 20, FontWeight.SemiBold), labelMedium = appText(12, 16, FontWeight.SemiBold),
    labelSmall = appText(11, 13, FontWeight.SemiBold),
)
// 作者 long：组件与截图宿主必须使用同一个主题事实来源，不能在浅色主题内再次读取系统深色设置。
internal val LocalKClashDarkTheme = staticCompositionLocalOf { false }

// 作者 long：与 Flutter lib/ui/color.dart 一致，以 green/orange 的 HCT 色相、彩度生成亮色 tone40 / 暗色 tone80。
@Composable
internal fun proxyDelaySuccessColor(): Color =
    if (LocalKClashDarkTheme.current) Color(0xFF78DC77) else Color(0xFF006E1C)

@Composable
internal fun proxyDelayWarningColor(): Color =
    if (LocalKClashDarkTheme.current) Color(0xFFFFB870) else Color(0xFF8B5000)

// 作者 long：记录 warning 标签沿用 Flutter 橙色色板的 tone90 / tone30，保证深浅色都能与错误标签区分。
@Composable
internal fun recordWarningContainerColor(): Color =
    if (LocalKClashDarkTheme.current) Color(0xFF693C00) else Color(0xFFFFDCBE)

@Composable
internal fun recordOnWarningContainerColor(): Color =
    if (LocalKClashDarkTheme.current) Color(0xFFFFDCBE) else Color(0xFF693C00)

@Composable
fun KClashTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKClashDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) FlutterDarkColors else FlutterLightColors,
            typography = FlutterTypography,
            content = content,
        )
    }
}
