package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.ui.theme.proxyDelaySuccessColor
import com.lonnnnnng.flclash.ui.theme.proxyDelayWarningColor

// 作者 long：节点几何由文字行高决定，测速只扩展命中范围，不把按钮默认高度计入详情行。
private object ProxyNodeStyle {
    val shape = AppSuperellipseShape(20.dp)
    val name = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false))
    val type = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
    val delay = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun ProxyNodeCard(
    name: String,
    type: String?,
    cardSize: ProxyNodeCardSize = ProxyNodeCardSize.STANDARD,
    insideGroup: Boolean = false,
    inUse: Boolean,
    preferred: Boolean,
    enabled: Boolean,
    delayEnabled: Boolean = enabled,
    delay: ProxyDelayUiState?,
    selectionTag: String,
    delayTag: String,
    modifier: Modifier = Modifier,
    groupType: String = "Selector",
    addable: Boolean = false,
    profileId: Long? = null,
    menuEnabled: Boolean = enabled,
    busy: Boolean = false,
    onSelect: () -> Unit,
    onTestDelay: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val addLocal = LocalAddToLocalProxy.current
    var menu by remember(profileId, selectionTag, groupType) { mutableStateOf(false) }
    val computed = groupType.equals("URLTest", true) || groupType.equals("Fallback", true)
    val selectable = computed || groupType.equals("Selector", true)
    val canAdd = addable && addLocal != null
    val hasMenu = selectable || canAdd
    LaunchedEffect(menuEnabled, hasMenu) { if (!menuEnabled || !hasMenu) menu = false }
    val nameLines = if (cardSize == ProxyNodeCardSize.COMPACT) 1 else 2
    val minimumHeight = when (cardSize) {
        ProxyNodeCardSize.COMPACT -> 66.dp
        ProxyNodeCardSize.STANDARD -> 86.dp
        ProxyNodeCardSize.LOOSE -> 105.dp
    }
    val selectionDescription = when {
        inUse && preferred -> "当前使用，优选节点"
        preferred -> "优选节点"
        inUse -> "当前使用"
        else -> "未使用"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().then(modifier).testTag(selectionTag).appFocusRing(ProxyNodeStyle.shape).combinedClickable(
            enabled = enabled || menuEnabled && hasMenu, onClick = { if (enabled) onSelect() },
            onLongClick = if (menuEnabled && hasMenu) ({ menu = true }) else null,
            onLongClickLabel = "节点操作",
        ).semantics {
            role = Role.RadioButton
            selected = inUse
            stateDescription = if (busy) "$selectionDescription，正在刷新，操作暂不可用" else selectionDescription
        },
        shape = ProxyNodeStyle.shape,
        // 作者 long：Flutter 节点选中态不填主色、不加描边，当前使用与优选分别交给徽标表达。
        color = proxyNodeColor(insideGroup),
    ) {
        Box(Modifier.heightIn(min = minimumHeight)) {
            DropdownMenu(menu, { menu = false }) {
                // 作者 long：菜单复用卡片选择回调，computed组再次优选仍清除固定，内建/组节点也可选择但不能导入。
                if (selectable) DropdownMenuItem(text = { Text(if (computed) "优选" else "选择") },
                    leadingIcon = { Icon(if (computed) Icons.Outlined.PushPin else Icons.Outlined.RadioButtonChecked, null) },
                    trailingIcon = { if (if (computed) preferred else inUse) Icon(Icons.Filled.Check, "已选中") },
                    enabled = enabled,
                    onClick = { menu = false; onSelect() }, modifier = Modifier.testTag("${selectionTag}_menu_select"))
                if (canAdd) DropdownMenuItem(text = { Text("添加到本地代理") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) },
                    onClick = { menu = false; addLocal.invoke(profileId, name) }, modifier = Modifier.testTag("${selectionTag}_menu_add_local"))
            }
            Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp, vertical = 10.dp)) {
                // 作者 long：中文回退字体的实际字高可能略大于标称行高，名称只设最小高度，
                // 不能把最大高度锁死为 40dp，否则 Compose 会把第二行裁成单行省略。
                Row(Modifier.heightIn(min = (20 * nameLines).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        name,
                        Modifier.weight(1f),
                        style = ProxyNodeStyle.name,
                        color = colors.onSurface.copy(alpha = if (busy) .38f else 1f),
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
                Spacer(Modifier.height(8.dp))
                if (cardSize == ProxyNodeCardSize.LOOSE) {
                    ProxyNodeType(type, Modifier.fillMaxWidth(), colors.onSurface.copy(alpha = 0.8f))
                    Spacer(Modifier.height(6.dp))
                    ProxyDelayAction(
                        name = name,
                        delay = delay,
                        enabled = delayEnabled,
                        delayTag = delayTag,
                        modifier = Modifier.align(Alignment.Start),
                        colors = colors,
                        onTestDelay = onTestDelay,
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ProxyNodeType(type, Modifier.weight(1f), colors.onSurface.copy(alpha = 0.8f))
                        ProxyDelayAction(
                            name = name,
                            delay = delay,
                            enabled = delayEnabled,
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
    // 作者 long：测速按 caption 行高绘制，移除 Material 布局占位后仍由 Compose 扩展触控命中；
    // 避免两列节点被单个测速按钮撑到 120dp。
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(
            onClick = onTestDelay,
            enabled = enabled && delay?.isTesting != true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = modifier.height(13.dp).width(58.dp).testTag(delayTag).semantics {
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
