package com.lonnnnnng.flclash.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private const val DASHBOARD_EDIT_SWAY_DURATION_MILLIS = 3_600
private const val DASHBOARD_EDIT_SWAY_TURN_DEGREES = 0.7f
private const val DASHBOARD_EDIT_SWAY_BOB_DP = 1.25f
private const val DASHBOARD_EDIT_SWAY_PHASE_STEP = 1.7f
private val DASHBOARD_EDIT_SWAY_CYCLES = intArrayOf(6, 7, 5)

internal data class DashboardEditSway(
    val rotationDegrees: Float,
    val translationYDp: Float,
)

internal fun dashboardEditSway(section: DashboardSection, progress: Float): DashboardEditSway {
    val cycleCount = DASHBOARD_EDIT_SWAY_CYCLES[
        section.ordinal % DASHBOARD_EDIT_SWAY_CYCLES.size
    ]
    val angle = 2f * PI.toFloat() * (progress * cycleCount) +
        section.ordinal * DASHBOARD_EDIT_SWAY_PHASE_STEP
    return DashboardEditSway(
        rotationDegrees = sin(angle) * DASHBOARD_EDIT_SWAY_TURN_DEGREES,
        translationYDp = sin(angle + PI.toFloat() / 2f) * DASHBOARD_EDIT_SWAY_BOB_DP,
    )
}

@Composable
internal fun rememberDashboardEditSwayProgress(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "dashboard_edit_sway")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = DASHBOARD_EDIT_SWAY_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        ),
        label = "dashboard_edit_sway_progress",
    )
    return progress
}

@Composable
internal fun DashboardEditableSection(
    section: DashboardSection,
    swayProgress: Float,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragOffsetPx: () -> Float = { 0f },
    onRemove: (() -> Unit)?,
    onDragStart: (Float) -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sway = dashboardEditSway(section, swayProgress)
    val density = LocalDensity.current.density

    Box(
        Modifier.fillMaxWidth()
            .then(modifier)
            .testTag("dashboard_edit_section_${section.name.lowercase()}"),
    ) {
        Box(
            Modifier.fillMaxWidth().graphicsLayer {
                // 作者 long：整卡轻摆提示可排序，拿起后停止轻摆并跟随手指，原位置保留占位供其他卡片移动。
                rotationZ = if (isDragging) 0f else sway.rotationDegrees
                translationY = if (isDragging) dragOffsetPx() else sway.translationYDp * density
                scaleX = if (isDragging) 1.03f else 1f
                scaleY = if (isDragging) 1.03f else 1f
            },
        ) {
            Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) { content() }
            // 作者 long：编辑态屏蔽卡片原有点击和语义，整卡长按只排序，避免误启停核心、切换配置或改变模式。
            Box(
                Modifier.matchParentSize()
                    .testTag("dashboard_drag_${section.name.lowercase()}")
                    .semantics {
                        contentDescription = "长按${section.title}卡片调整顺序"
                        role = Role.Button
                    }
                    .pointerInput(section) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(it.y) },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragBy(dragAmount.y)
                            },
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
            if (onRemove != null && !isDragging) {
                DashboardEditorRoundButton(
                    icon = DashboardModeGlyphs.remove,
                    label = "移除${section.title}",
                    background = MaterialTheme.colorScheme.errorContainer,
                    foreground = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .testTag("dashboard_remove_${section.name.lowercase()}"),
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
internal fun DashboardAddableSectionHeader() {
    Text(
        text = "可添加",
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun DashboardAddableSectionRow(
    section: DashboardSection,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .then(modifier)
            .height(64.dp)
            .testTag("dashboard_add_row_${section.name.lowercase()}"),
        shape = RoundedCornerShape(22.dp),
        color = dashboardCardColor().copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardEditorRoundButton(
                icon = DashboardModeGlyphs.add,
                label = "添加${section.title}",
                background = MaterialTheme.colorScheme.primary,
                foreground = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("dashboard_add_${section.name.lowercase()}"),
                onClick = onAdd,
            )
            Box(
                Modifier.size(40.dp).background(dashboardCardColor(), RoundedCornerShape(14.dp))
                    .testTag("dashboard_add_icon_${section.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(section.editorIcon(), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    section.title,
                    modifier = Modifier.testTag("dashboard_add_label_${section.name.lowercase()}"),
                    style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                section.description?.let { description ->
                    Text(
                        description,
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardEditorRoundButton(
    icon: ImageVector,
    label: String,
    background: Color,
    foreground: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // 作者 long：Flutter 编辑器使用 28dp 增删圆按钮，徽标叠在卡片上，不占用额外的顶端操作行。
    Box(
        modifier.size(28.dp).clip(CircleShape).background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(16.dp), tint = foreground)
    }
}

private fun DashboardSection.editorIcon(): ImageVector = when (this) {
    DashboardSection.CURRENT_OUTBOUND -> Icons.Outlined.Tune
    DashboardSection.NETWORK_STATS -> Icons.Outlined.Speed
    DashboardSection.OUTBOUND_MODE -> DashboardModeGlyphs.split
    DashboardSection.PROFILES -> Icons.Outlined.Description
    DashboardSection.PROXY_GROUPS -> Icons.Outlined.Layers
    DashboardSection.MEMORY_INFO -> Icons.Outlined.Memory
    DashboardSection.SERVICE_STATUS -> Icons.Outlined.Verified
    DashboardSection.VPN -> Icons.Outlined.VpnKey
    DashboardSection.INTRANET_IP -> Icons.Outlined.Devices
    DashboardSection.CONNECTION_STATS -> Icons.Outlined.Lan
}
