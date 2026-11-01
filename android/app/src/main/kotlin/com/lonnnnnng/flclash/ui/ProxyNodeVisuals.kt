package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.ui.theme.proxyDelaySuccessColor
import com.lonnnnnng.flclash.ui.theme.proxyDelayWarningColor

// 作者 long：节点卡片沿用 Flutter standard 的两行名称和紧凑详情层级；独立测速区域保留 Android 48dp 触控高度。
private object ProxyNodeStyle {
    val shape = RoundedCornerShape(20.dp)
    val name = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
    val type = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
    val delay = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun ProxyNodeCard(
    name: String,
    type: String?,
    cardSize: ProxyNodeCardSize = ProxyNodeCardSize.STANDARD,
    inUse: Boolean,
    preferred: Boolean,
    enabled: Boolean,
    delay: ProxyDelayUiState?,
    selectionTag: String,
    delayTag: String,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onTestDelay: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val nameLines = if (cardSize == ProxyNodeCardSize.COMPACT) 1 else 2
    val minimumHeight = when (cardSize) {
        ProxyNodeCardSize.COMPACT -> 66.dp
        ProxyNodeCardSize.STANDARD -> 86.dp
        ProxyNodeCardSize.LOOSE -> 108.dp
    }
    val selectionDescription = when {
        inUse && preferred -> "当前使用，优选节点"
        preferred -> "优选节点"
        inUse -> "当前使用"
        else -> "未使用"
    }
    Surface(
        onClick = onSelect,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().then(modifier).testTag(selectionTag).semantics {
            role = Role.RadioButton
            selected = inUse
            stateDescription = selectionDescription
        },
        shape = ProxyNodeStyle.shape,
        // 作者 long：Flutter 节点选中态不填主色、不加描边，当前使用与优选分别交给徽标表达。
        color = colors.surfaceContainerHighest,
    ) {
        Box(Modifier.heightIn(min = minimumHeight)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        name,
                        Modifier.weight(1f),
                        style = ProxyNodeStyle.name,
                        color = colors.onSurface,
                        minLines = nameLines,
                        maxLines = nameLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (inUse || preferred) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = if (inUse) colors.primary else colors.primary.copy(alpha = 0.14f),
                            contentColor = if (inUse) colors.onPrimary else colors.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (preferred) Icons.Filled.PushPin else Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(if (cardSize == ProxyNodeCardSize.LOOSE) 6.dp else 8.dp))
                if (cardSize == ProxyNodeCardSize.LOOSE) {
                    ProxyNodeType(type, Modifier.fillMaxWidth(), colors.onSurface.copy(alpha = 0.8f))
                    Spacer(Modifier.height(2.dp))
                    ProxyDelayAction(
                        name = name,
                        delay = delay,
                        enabled = enabled,
                        delayTag = delayTag,
                        modifier = Modifier.align(Alignment.End),
                        colors = colors,
                        onTestDelay = onTestDelay,
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ProxyNodeType(type, Modifier.weight(1f), colors.onSurface.copy(alpha = 0.8f))
                        ProxyDelayAction(
                            name = name,
                            delay = delay,
                            enabled = enabled,
                            delayTag = delayTag,
                            modifier = Modifier,
                            colors = colors,
                            onTestDelay = onTestDelay,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyNodeType(type: String?, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    if (!type.isNullOrBlank()) {
        Text(
            type,
            modifier,
            style = ProxyNodeStyle.type,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Spacer(modifier.height(18.dp))
    }
}

@Composable
private fun ProxyDelayAction(
    name: String,
    delay: ProxyDelayUiState?,
    enabled: Boolean,
    delayTag: String,
    modifier: Modifier,
    colors: androidx.compose.material3.ColorScheme,
    onTestDelay: () -> Unit,
) {
    TextButton(
        onClick = onTestDelay,
        enabled = enabled && delay?.isTesting != true,
        modifier = modifier.size(width = 72.dp, height = 48.dp).testTag(delayTag).semantics {
            contentDescription = "$name，${proxyDelayLabel(delay)}"
        },
    ) {
        when {
            delay?.isTesting == true -> CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
            delay == null -> Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
            else -> Text(
                proxyDelayLabel(delay),
                style = ProxyNodeStyle.delay,
                color = when {
                    !enabled -> colors.onSurface.copy(alpha = 0.38f)
                    delay.value == null -> colors.onSurfaceVariant
                    delay.value <= 0 -> colors.error
                    delay.value < 600 -> proxyDelaySuccessColor()
                    else -> proxyDelayWarningColor()
                },
            )
        }
    }
}

private fun proxyDelayLabel(delay: ProxyDelayUiState?): String = when {
    delay == null -> "测速"
    delay.isTesting -> "测速中"
    delay.value == null -> "无结果"
    delay.value > 0 -> "${delay.value} ms"
    // 作者 long：保留 Mihomo 的超时/失败语义，用户重测前仍可区分错误原因。
    delay.value == CoreBridge.DELAY_TIMED_OUT -> "超时"
    else -> "失败"
}
