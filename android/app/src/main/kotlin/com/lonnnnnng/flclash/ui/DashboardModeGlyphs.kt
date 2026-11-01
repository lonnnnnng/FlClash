package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 作者 long：出站模式沿用 Flutter AppGlyphs 的 24 单位坐标和 1.8 单位线宽，避免 Material 图标改变卡片语义与轮廓。 */
internal object DashboardModeGlyphs {
    val proxies = glyph("DashboardProxies", listOf(
        "M20.8 12A8.8 8.8 0 1 1 3.2 12A8.8 8.8 0 1 1 20.8 12Z",
        "M15.8 12A3.8 8.8 0 1 1 8.2 12A3.8 8.8 0 1 1 15.8 12Z",
        "M3.2 12H20.8",
    ))

    val target = glyph("DashboardTarget", listOf(
        "M3.412 10.08A8.8 8.8 0 0 1 20.588 10.08",
        "M20.588 13.92A8.8 8.8 0 0 1 3.412 13.92",
        "M14 12A2 2 0 1 1 10 12A2 2 0 1 1 14 12Z",
    ))

    val rules = glyph("DashboardRules", listOf(
        "M6 4H18Q21 4 21 7V17Q21 20 18 20H6Q3 20 3 17V7Q3 4 6 4Z",
        "M10.8 8H17.2M10.8 12H17.2M10.8 16H17.2",
    ), dots = listOf(8, 12, 16).map { y ->
        "M8.1 $y A1.1 1.1 0 1 1 5.9 $y A1.1 1.1 0 1 1 8.1 $y Z"
    }, autoMirror = true)

    val split = glyph("DashboardSplit", listOf(
        "M4.5 4L10.232 9.732Q12 11.5 12 14V20.5",
        "M19.5 4L13.768 9.732Q12 11.5 12 14V20.5",
        "M4.5 9.5V4.5Q4.5 4 5 4H10",
        "M14 4H19Q19.5 4 19.5 4.5V9.5",
    ))

    private fun glyph(
        name: String,
        strokes: List<String>,
        dots: List<String> = emptyList(),
        autoMirror: Boolean = false,
    ): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f, autoMirror = autoMirror,
    ).apply {
        strokes.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        dots.forEach { data ->
            addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
        }
    }.build()
}
