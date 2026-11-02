package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

@Composable
internal fun DashboardTrafficChart(label: String, samples: List<Long>, tag: String) {
    val heights = remember(samples) { dashboardTrafficHeights(samples) }
    val slopes = remember(heights) { dashboardTrafficSlopes(heights) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().height(36.dp).testTag(tag).semantics {
            contentDescription = "${label}速度历史，最近 ${samples.size} 次采样"
        },
    ) {
        val stroke = 2.dp.toPx()
        val step = size.width / (DASHBOARD_TRAFFIC_CAPACITY - 1)
        // 作者 long：低流量至少使用 8 KiB/s 的纵轴；把控制点峰值也纳入边界，避免突发流量曲线被顶部裁断。
        val peak = max(8 * 1024.0, heights.indices.maxOf { heights[it] + abs(slopes[it]) / 3 })
        val scale = (size.height - stroke).coerceAtLeast(0f) / peak
        fun y(value: Double) = (size.height - value * scale).toFloat()
        val line = Path().apply {
            moveTo(0f, y(heights.first()))
            for (index in 1..heights.lastIndex) {
                val previousX = (index - 1) * step
                val x = index * step
                cubicTo(
                    previousX + step / 3, y(heights[index - 1] + slopes[index - 1] / 3),
                    x - step / 3, y(heights[index] - slopes[index] / 3),
                    x, y(heights[index]),
                )
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        clipRect {
            drawPath(area, color.copy(alpha = 0.14f))
            drawPath(line, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
