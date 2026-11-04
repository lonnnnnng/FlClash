package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonnnnnng.flclash.core.ServiceTarget
import com.lonnnnnng.flclash.data.ServiceSettings
import kotlin.math.abs

@Composable
internal fun ServiceOrderEditor(
    settings: ServiceSettings,
    saving: Boolean,
    onToggle: (ServiceTarget, Boolean) -> Unit,
    onMove: (ServiceTarget, Int) -> Unit,
    onReorder: (List<ServiceTarget>) -> Unit,
    modifier: Modifier = Modifier,
    onContentAtTopChanged: (Boolean) -> Unit = {},
) {
    val list = rememberLazyListState()
    val density = LocalDensity.current.density
    val owner = LocalLifecycleOwner.current
    val latestSettings by rememberUpdatedState(settings)
    val latestSaving by rememberUpdatedState(saving)
    val commit by rememberUpdatedState(onReorder)
    var preview by remember { mutableStateOf(settings.order) }
    var dragged by remember { mutableStateOf<ServiceTarget?>(null) }
    var top by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val reportContentAtTop by rememberUpdatedState(onContentAtTopChanged)
    LaunchedEffect(list) {
        // 作者 long：服务排序页嵌入双停靠面板后，只有列表顶部且未拿起条目时，外层才能接管下拉关闭。
        snapshotFlow { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 && dragged == null }
            .collect { reportContentAtTop(it) }
    }

    fun cancel() { dragged = null; preview = latestSettings.order }
    fun reorder() {
        val target = dragged ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val shown = items.map { it.key }
        // 作者 long：预览交换与下一帧布局并不同步，旧坐标必须等到 key 顺序对齐后才能再次参与排序。
        if (shown != preview.filter { it.id in shown }.map { it.id }) return
        val nearest = items.minByOrNull { abs(it.offset + it.size / 2f - (top + height / 2f)) }?.key ?: return
        val from = preview.indexOf(target)
        val to = preview.indexOfFirst { it.id == nearest }
        if (from >= 0 && to >= 0 && from != to) {
            // 作者 long：交换首个可见项时保留当前视口，避免 LazyColumn 按旧 key 跳动并打断跨屏拿起。
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            preview = preview.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    LaunchedEffect(settings, saving) { cancel() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(dragged) {
        if (dragged == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val info = list.layoutInfo
            val delta = dashboardDragScrollDelta(pointerY, info.viewportStartOffset, info.viewportEndOffset, density, (frame - previous) / 1_000_000f)
            previous = frame
            if (delta != 0f) { list.scrollBy(delta); reorder() }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("长按服务调整顺序；至少启用一项，前两项显示在仪表盘", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("service_order_saving"))
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("service_order_list")
            .pointerInput(list, owner) {
                // 作者 long：手势放在不随排序移动的视口，整行长按不会因 key 换位或跨屏回收而丢失按住状态。
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        if (!latestSaving && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }
                            dragged = latestSettings.order.firstOrNull { it.id == item?.key }
                            preview = latestSettings.order
                            top = item?.offset?.toFloat() ?: 0f; height = item?.size ?: 0; pointerY = point.y
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragged != null) { change.consume(); top += amount.y; pointerY += amount.y; reorder() }
                    },
                    onDragCancel = { cancel() },
                    onDragEnd = {
                        val changed = preview
                        val shouldSave = dragged != null && changed != latestSettings.order && !latestSaving && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        cancel()
                        // 作者 long：只在松手后提交完整排列，取消、后台与无位移长按都不能写偏好。
                        if (shouldSave) commit(changed)
                    },
                )
            }, state = list, userScrollEnabled = dragged == null) {
            itemsIndexed(preview, key = { _, target -> target.id }) { index, target ->
                val picked = dragged == target
                val enabled = target !in settings.disabled
                val locked = saving || dragged != null
                Surface(Modifier.fillMaxWidth().zIndex(if (picked) 1f else 0f)
                    .graphicsLayer { translationY = if (picked) top - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == target.id }?.offset ?: top.toInt()) else 0f }
                    .testTag("service_order_" + target.id),
                    color = if (picked) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = if (picked) 4.dp else 0.dp,
                ) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DragHandle, null, Modifier.padding(end = 8.dp).size(24.dp).testTag("service_drag_" + target.id), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.weight(1f).toggleable(enabled, enabled = !locked && !(enabled && settings.enabled.size == 1), onValueChange = { onToggle(target, it) })
                            .testTag("service_toggle_" + target.id).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(target.label, style = MaterialTheme.typography.bodyLarge)
                                if (target in preview.filterNot(settings.disabled::contains).take(2)) Text("显示在仪表盘", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(enabled, onCheckedChange = null, enabled = !locked && !(enabled && settings.enabled.size == 1))
                        }
                        IconButton(onClick = { onMove(target, -1) }, enabled = !locked && index > 0, modifier = Modifier.testTag("service_up_" + target.id)) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
                        IconButton(onClick = { onMove(target, 1) }, enabled = !locked && index < preview.lastIndex, modifier = Modifier.testTag("service_down_" + target.id)) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
                    }
                }
            }
        }
    }
}
