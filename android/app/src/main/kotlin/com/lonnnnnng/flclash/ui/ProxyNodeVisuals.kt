package com.lonnnnnng.flclash.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
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
    val menuAnchor = remember(profileId, selectionTag) { AppContextMenuAnchor() }
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
    Surface(
        modifier = Modifier.fillMaxWidth().then(modifier).testTag(selectionTag).appFocusRing(ProxyNodeStyle.shape).clip(ProxyNodeStyle.shape)
            .appContextMenuAnchor(menuAnchor, menuEnabled && hasMenu) { menu = true }.combinedClickable(
            enabled = enabled || menuEnabled && hasMenu, onClick = { if (enabled) onSelect() },
            onLongClick = if (menuEnabled && hasMenu) ({ menuAnchor.open(); menu = true }) else null,

        ),
        shape = ProxyNodeStyle.shape,
        // 作者 long：Flutter 节点选中态不填主色、不加描边，当前使用与优选分别交给徽标表达。
        color = proxyNodeColor(insideGroup),
    ) {
        Box(Modifier.heightIn(min = minimumHeight)) {
            AppDropdownMenu(menu && menuEnabled && hasMenu, { menu = false }, anchor = menuAnchor.point, carriedPointer = menuAnchor.carry,
                placement = AppMenuPlacement.BELOW_POINT, avoid = menuAnchor.bounds) {
                // 作者 long：菜单复用卡片选择回调，computed组再次优选仍清除固定，内建/组节点也可选择但不能导入。
                if (selectable) AppDropdownMenuItem(text = { Text(if (computed) "优选" else "选择") },
                    leadingIcon = { Icon(if (computed) Icons.Outlined.PushPin else Icons.Outlined.RadioButtonChecked, null) },
                    trailingIcon = { if (if (computed) preferred else inUse) Icon(Icons.Filled.Check, null, modifier = Modifier.testTag("action:已选中")) },
                    enabled = enabled,
                    onClick = { menu = false; onSelect() }, modifier = Modifier.testTag("${selectionTag}_menu_select"))
                if (canAdd) AppDropdownMenuItem(text = { Text("添加到本地代理") },
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
                            modifier = Modifier.size(22.dp).testTag(if (inUse) "${selectionTag}_in_use" else "${selectionTag}_preferred"),
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
            modifier = modifier
                .height(13.dp)
                .width(58.dp)
                // 作者 long：Flutter 将测速触点作为卡片内的独立焦点目标；局部焦点环只提示当前键盘/遥控落点，不能改变节点卡片的选中语义。
                .appFocusRing(AppSuperellipseShape(4.dp), standsOff = true)
                .testTag(delayTag),
        ) {
            // 作者 long：Flutter 的 FadeBox 会在测速图标、运行中、结果和失败文案之间交叉淡入淡出；
            // 保持固定的 13dp 行高，只替换内容，避免状态切换把相邻节点的卡片高度推开。
            AnimatedContent(
                targetState = proxyDelayAnimationKey(delay),
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "proxyDelayFade",
            ) { state ->
                when (state.kind) {
                    ProxyDelayAnimationKey.TESTING -> CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                    ProxyDelayAnimationKey.ACTION -> Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    else -> Text(
                        state.label,
                        style = ProxyNodeStyle.delay,
                        color = when {
                            !enabled -> colors.onSurface.copy(alpha = 0.38f)
                            delay?.value == null -> colors.onSurfaceVariant
                            delay.value <= 0 -> colors.error
                            delay.value < 600 -> proxyDelaySuccessColor()
                            else -> proxyDelayWarningColor()
                        },
                    )
                }
            }
        }
    }
}

private enum class ProxyDelayAnimationKey { ACTION, TESTING, RESULT }

private data class ProxyDelayAnimationState(
    val kind: ProxyDelayAnimationKey,
    val label: String,
)

private fun proxyDelayAnimationKey(delay: ProxyDelayUiState?): ProxyDelayAnimationState = when {
    delay == null -> ProxyDelayAnimationState(ProxyDelayAnimationKey.ACTION, "")
    delay.isTesting -> ProxyDelayAnimationState(ProxyDelayAnimationKey.TESTING, "")
    else -> ProxyDelayAnimationState(ProxyDelayAnimationKey.RESULT, proxyDelayLabel(delay))
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
