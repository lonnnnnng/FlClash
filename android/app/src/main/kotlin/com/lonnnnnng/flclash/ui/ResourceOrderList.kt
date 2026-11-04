package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

internal data class ResourceOrderItem(val id: String, val title: String, val subtitle: String, val icon: String? = null)

/** 作者 long：规则资源和图库共用视口手势；整行被回收或换位后仍可拖动，只有松手才提交完整顺序。 */
@Composable
internal fun ResourceOrderList(rows: List<ResourceOrderItem>, saving: Boolean, tag: String, onReorder: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val density = LocalDensity.current.density
    val owner = LocalLifecycleOwner.current
    val latestRows by rememberUpdatedState(rows)
    val latestSaving by rememberUpdatedState(saving)
    val commit by rememberUpdatedState(onReorder)
    var preview by remember { mutableStateOf(rows.map { it.id }) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var top by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    fun cancel() { dragged = null; preview = latestRows.map { it.id } }
    fun reorder() {
        val target = dragged ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val shown = items.map { it.key }
        // 作者 long：换位后先等真实布局与预览对齐，避免用上一帧坐标反复交换同两项。
        if (shown != preview.filter { it in shown }) return
        val nearest = items.minByOrNull { abs(it.offset + it.size / 2f - (top + height / 2f)) }?.key ?: return
        val from = preview.indexOf(target); val to = preview.indexOf(nearest)
        if (from >= 0 && to >= 0 && from != to) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            preview = preview.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    LaunchedEffect(rows.map { it.id }, saving) {
        // 作者 long：回读新顺序时保留视口索引；按旧首项的key锚定会把新首项推到屏幕外，看起来像排序没有生效。
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        cancel()
    }
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
    LazyColumn(modifier.testTag("${tag}_order_list").pointerInput(list, owner) {
        detectDragGesturesAfterLongPress(onDragStart = { point ->
            if (!latestSaving && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }
                dragged = latestRows.firstOrNull { it.id == item?.key }?.id
                preview = latestRows.map { it.id }; top = item?.offset?.toFloat() ?: 0f; height = item?.size ?: 0; pointerY = point.y
            }
        }, onDrag = { change, amount -> if (dragged != null) { change.consume(); top += amount.y; pointerY += amount.y; reorder() } },
            onDragCancel = ::cancel, onDragEnd = {
                val order = preview
                val changed = dragged != null && order != latestRows.map { it.id } && !latestSaving && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                cancel()
                if (changed) commit(order)
            })
    }, state = list, userScrollEnabled = dragged == null, contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(preview, key = { _, id -> id }) { index, id ->
            val row = rows.firstOrNull { it.id == id } ?: return@itemsIndexed
            val picked = dragged == id
            Surface(Modifier.fillMaxWidth().zIndex(if (picked) 1f else 0f).graphicsLayer {
                translationY = if (picked) top - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: top.toInt()) else 0f
            }.testTag("${tag}_order_$id"),
                color = if (picked) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.icon?.let { ProfileGroupIcon(it, row.title, Modifier.padding(end = 12.dp).size(32.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Outlined.DragHandle, null, Modifier.padding(start = 16.dp).size(24.dp))
                }
            }
        }
    }
}
