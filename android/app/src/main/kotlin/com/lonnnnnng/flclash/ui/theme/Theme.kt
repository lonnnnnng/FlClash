package com.lonnnnnng.flclash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()
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
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
