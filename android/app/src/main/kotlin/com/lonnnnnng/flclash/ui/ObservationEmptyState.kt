package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class ObservationEmptyIllustration {
    REQUESTS,
    CONNECTIONS,
    DNS,
    LOGS,
    SEARCH,
}

@Composable
internal fun ObservationEmptyState(
    title: String,
    illustration: ObservationEmptyIllustration,
    testTag: String,
) {
    val colors = MaterialTheme.colorScheme
    val compact = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() < 560.dp
    }
    val dimension = if (compact) 120.dp else 160.dp
    val icon = when (illustration) {
        ObservationEmptyIllustration.REQUESTS -> Icons.Outlined.AccessTime
        ObservationEmptyIllustration.CONNECTIONS -> null
        ObservationEmptyIllustration.DNS -> Icons.Outlined.Dns
        ObservationEmptyIllustration.LOGS -> Icons.Outlined.Terminal
        ObservationEmptyIllustration.SEARCH -> Icons.Outlined.Search
    }

    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(dimension).then(
                if (illustration == ObservationEmptyIllustration.CONNECTIONS) {
                    Modifier.testTag("connections_empty_illustration")
                } else Modifier
            ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(dimension)) {
                val side = size.minDimension
                val center = Offset(size.width / 2f, size.height / 2f)
                if (illustration == ObservationEmptyIllustration.CONNECTIONS) {
                    // 作者 long：连接空态沿用 Flutter 的四边 cookie 轮廓，避免把网络连接误认成请求时钟。
                    drawPath(connectionCookiePath(side, 0.84f), colors.primary.copy(alpha = 0.14f))
                    drawPath(connectionCookiePath(side), colors.primaryContainer)
                    drawCircle(
                        colors.tertiaryContainer,
                        radius = side * 0.07f,
                        center = center + Offset(-side * 0.32f, side * 0.3f),
                    )
                    drawCircle(
                        colors.tertiary,
                        radius = side * 0.025f,
                        center = center + Offset(-side * 0.4f, -side * 0.18f),
                    )
                    drawCircle(
                        colors.primary,
                        radius = side * 0.0375f,
                        center = center + Offset(side * 0.42f, side * 0.15f),
                        style = Stroke(width = side * 0.021f),
                    )
                } else {
                    val (petalCount, centerRadius, petalRadius, orbitRadius) = when (illustration) {
                        ObservationEmptyIllustration.DNS -> ShapeMetrics(8, 0.24f, 0.13f, 0.24f)
                        ObservationEmptyIllustration.REQUESTS -> ShapeMetrics(12, 0.22f, 0.13f, 0.23f)
                        ObservationEmptyIllustration.LOGS -> ShapeMetrics(9, 0.22f, 0.13f, 0.23f)
                        ObservationEmptyIllustration.SEARCH -> ShapeMetrics(10, 0.22f, 0.13f, 0.23f)
                        ObservationEmptyIllustration.CONNECTIONS -> error("Handled above")
                    }

                    drawCircle(colors.primary.copy(alpha = 0.14f), radius = side * 0.42f, center = center)
                    // 作者 long：圆瓣组合让请求、DNS 与日志空态保持各自语义，避免不同页面共用同一图形。
                    repeat(petalCount) { index ->
                        val angle = 2.0 * PI * index / petalCount
                        val petalCenter = center + Offset(
                            x = cos(angle).toFloat() * side * orbitRadius,
                            y = sin(angle).toFloat() * side * orbitRadius,
                        )
                        drawCircle(colors.primaryContainer, radius = side * petalRadius, center = petalCenter)
                    }
                    drawCircle(colors.primaryContainer, radius = side * centerRadius, center = center)

                    drawCircle(
                        colors.tertiaryContainer,
                        radius = side * 0.07f,
                        center = center + Offset(-side * 0.32f, side * 0.3f),
                    )
                    drawCircle(
                        colors.primary,
                        radius = side * 0.025f,
                        center = center + Offset(-side * 0.4f, -side * 0.18f),
                    )
                    drawCircle(
                        colors.primary,
                        radius = side * 0.0375f,
                        center = center + Offset(side * 0.42f, side * 0.15f),
                        style = Stroke(width = side * 0.021f),
                    )
                }
            }
            if (illustration == ObservationEmptyIllustration.CONNECTIONS) {
                Canvas(Modifier.size(dimension * 0.4f)) {
                    val scale = size.minDimension / 24f
                    val nodes = listOf(
                        Offset(12f, 5.2f),
                        Offset(5.2f, 17.6f),
                        Offset(18.8f, 17.6f),
                    ).map { Offset(it.x * scale, it.y * scale) }
                    listOf(0 to 1, 0 to 2, 1 to 2).forEach { (start, end) ->
                        drawLine(
                            colors.onPrimaryContainer,
                            nodes[start],
                            nodes[end],
                            strokeWidth = 2.4f * scale,
                            cap = StrokeCap.Round,
                        )
                    }
                    nodes.forEach { drawCircle(colors.onPrimaryContainer, radius = 2.4f * scale, center = it) }
                }
            } else {
                Icon(
                    imageVector = requireNotNull(icon),
                    contentDescription = null,
                    modifier = Modifier.size(dimension * 0.4f),
                    tint = colors.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        Text(
            title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            color = colors.onSurface,
        )
    }
}

private fun connectionCookiePath(side: Float, extent: Float = 0.72f): Path {
    val shapeSide = side * extent
    val inset = (side - shapeSide) / 2f
    val vertices = listOf(
        Offset(0.50f, 0.22f), Offset(0.97f, 0.03f),
        Offset(0.78f, 0.50f), Offset(0.97f, 0.97f),
        Offset(0.50f, 0.78f), Offset(0.03f, 0.97f),
        Offset(0.22f, 0.50f), Offset(0.03f, 0.03f),
    ).map { Offset(inset + it.x * shapeSide, inset + it.y * shapeSide) }
    val cornerFraction = 0.34f
    fun toward(from: Offset, to: Offset) = from + (to - from) * cornerFraction

    return Path().apply {
        val first = vertices.first()
        val start = toward(first, vertices.last())
        moveTo(start.x, start.y)
        vertices.indices.forEach { index ->
            val vertex = vertices[index]
            val next = vertices[(index + 1) % vertices.size]
            val outgoing = toward(vertex, next)
            quadraticTo(vertex.x, vertex.y, outgoing.x, outgoing.y)
        }
        close()
    }
}

private data class ShapeMetrics(
    val petals: Int,
    val centerRadius: Float,
    val petalRadius: Float,
    val orbitRadius: Float,
)
