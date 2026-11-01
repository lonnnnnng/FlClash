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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
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
    onRemove: () -> Unit,
    onDragStart: (Float) -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sway = dashboardEditSway(section, swayProgress)
    val density = LocalDensity.current.density

    Column(
        Modifier.fillMaxWidth()
            .then(modifier)
            .testTag("dashboard_edit_section_${section.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("dashboard_drag_${section.name.lowercase()}")
                    .semantics {
                        contentDescription = "长按拖动以调整${section.title}顺序"
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
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.DragIndicator, contentDescription = null)
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("dashboard_remove_${section.name.lowercase()}")
                    .semantics {
                        contentDescription = "移除${section.title}"
                    },
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null)
            }
        }
        Box(
            Modifier.fillMaxWidth().graphicsLayer {
                // 作者 long：首页编辑态轻摆卡片以提示可拖动，弱幅度不影响内容识读。
                rotationZ = sway.rotationDegrees
                translationY = sway.translationYDp * density
            },
        ) {
            content()
            // 作者 long：编辑遮罩禁用卡片原有业务按钮，拖动和移除只在独立操作行生效。
            Box(
                Modifier.matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
internal fun DashboardAddableSectionHeader() {
    Text(
        text = "添加卡片",
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
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
            .heightIn(min = 68.dp)
            .testTag("dashboard_add_row_${section.name.lowercase()}"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(section.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    section.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onAdd,
                modifier = Modifier.testTag("dashboard_add_${section.name.lowercase()}"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加${section.title}")
            }
        }
    }
}
