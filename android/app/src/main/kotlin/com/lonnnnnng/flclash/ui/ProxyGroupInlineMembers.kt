package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonnnnnng.flclash.data.*
import kotlin.math.abs

private data class InlineGroupMember(val key: String, val member: GroupMemberEntry)

/** 作者 long：整张表单只用一个滚动视口，成员长按拖动时沿边缘持续滚动；取消/后台不把预览顺序写入草稿。 */
@Composable
internal fun ProxyGroupInlineMembers(group: ProxyGroupDraft, groups: List<ProxyGroupDraft>, source: String,
    list: LazyListState, onChange: (ProxyGroupDraft) -> Unit, onAdd: () -> Unit, onEdit: (GroupMemberEntry) -> Unit,
    before: @Composable () -> Unit, after: @Composable () -> Unit) {
    val members = remember(group) { ProxyGroupMembers.read(group) }
    val rows = remember(members) {
        val occurrences = mutableMapOf<String, Int>()
        // 作者 long：导入/快捷YAML可能保留重复引用；每个出现位置独立拖动与删除，不能让重复key使列表崩溃。
        members.map { member ->
            val occurrence = occurrences.getOrDefault(member.id, 0)
            occurrences[member.id] = occurrence + 1
            InlineGroupMember("${member.id.length}:${member.id}:$occurrence", member)
        }
    }
    val current by rememberUpdatedState(rows)
    val currentGroup by rememberUpdatedState(group)
    val change by rememberUpdatedState(onChange)
    val density = LocalDensity.current.density
    val owner = LocalLifecycleOwner.current
    val reportHeight by rememberUpdatedState(LocalEmbeddedProfilePage.current?.reportBodyHeight)
    LaunchedEffect(list, group.json) {
        var measured: Int? = null
        snapshotFlow {
            val info = list.layoutInfo
            if (info.totalItemsCount > 0 && info.visibleItemsInfo.size == info.totalItemsCount)
                info.visibleItemsInfo.sumOf { it.size } + info.beforeContentPadding + info.afterContentPadding else null
        }.collect { height ->
            // 作者 long：已测全表后滚动会回收离屏行，不能据此清掉自然高度并触发展开/收回反馈循环。
            if (height != null) measured = height
            reportHeight?.invoke(measured)
        }
    }
    var preview by remember { mutableStateOf(rows.map { it.key }) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var top by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    fun cancel() { dragged = null; preview = current.map { it.key } }
    fun reorder() {
        val id = dragged ?: return
        val visible = list.layoutInfo.visibleItemsInfo.filter { it.key in preview }
        val shown = visible.map { it.key }
        if (shown != preview.filter { it in shown }) return
        val nearest = visible.minByOrNull { abs(it.offset + it.size / 2f - top - height / 2f) }?.key ?: return
        val from = preview.indexOf(id); val to = preview.indexOf(nearest)
        if (from >= 0 && to >= 0 && from != to) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            preview = preview.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    LaunchedEffect(members) { cancel() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(dragged) {
        if (dragged == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }; val info = list.layoutInfo
            val delta = dashboardDragScrollDelta(pointerY, info.viewportStartOffset, info.viewportEndOffset, density, (frame - previous) / 1_000_000f)
            previous = frame
            if (delta != 0f) { list.scrollBy(delta); reorder() }
        }
    }
    val types = remember(source, groups) {
        val nodes = ProxyGroupDrafts.root(source).get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
            .filter { it.isJsonObject }.mapNotNull {
                val name = it.asJsonObject.get("name")?.takeIf { value -> value.isJsonPrimitive }?.asString ?: return@mapNotNull null
                name to it.asJsonObject.get("type")?.takeIf { value -> value.isJsonPrimitive }?.asString.orEmpty()
            }.toMap()
        nodes + groups.associate { it.name to it.text("type") }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("group_form_list").pointerInput(list, owner) {
        detectDragGesturesAfterLongPress(onDragStart = { point ->
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }
                dragged = current.firstOrNull { it.key == item?.key }?.key
                preview = current.map { it.key }; top = item?.offset?.toFloat() ?: 0f; height = item?.size ?: 0; pointerY = point.y
            }
        }, onDrag = { event, delta -> if (dragged != null) { event.consume(); top += delta.y; pointerY += delta.y; reorder() } },
            onDragCancel = ::cancel, onDragEnd = {
                val order = preview
                val commit = dragged != null && order != current.map { it.key } && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                cancel()
                if (commit) change(ProxyGroupMembers.replace(currentGroup, order.mapNotNull { id -> current.firstOrNull { it.key == id }?.member }))
            })
    }, state = list, userScrollEnabled = dragged == null, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item("identity") { Column { before() } }
        item("members-heading") { Text("节点", Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(preview, key = { _, id -> id }) { index, id ->
            val member = rows.firstOrNull { it.key == id }?.member ?: return@itemsIndexed
            val lifted = dragged == id
            Surface(Modifier.fillMaxWidth().animateItem().zIndex(if (lifted) 1f else 0f).graphicsLayer {
                translationY = if (lifted) top - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: top.toInt()) else 0f
                scaleX = if (lifted) 1.02f else 1f; scaleY = scaleX
            }.testTag("group_member_row_${member.id}"), color = dashboardCardColor(),
                shape = AppSuperellipseShape(topStart = if (index == 0) 24.dp else 0.dp, topEnd = if (index == 0) 24.dp else 0.dp,
                    bottomStart = 0.dp, bottomEnd = 0.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onChange(ProxyGroupMembers.replace(group, rows.filter { it.key != id }.map { it.member })) },
                            Modifier.testTag("group_member_remove_$index")) { Icon(Icons.Outlined.RemoveCircleOutline, null) }
                        Column(Modifier.weight(1f).then(if (member.kind.isFilter) Modifier.clickable { onEdit(member) } else Modifier)
                            .padding(start = 8.dp, top = 12.dp, bottom = 12.dp)) {
                            Text(ProxyGroupMembers.title(member, LocalFilterPresets.current), Modifier.testTag("group_member_label_$index"),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val label = if (member.kind == GroupMemberKind.PROXY) types[member.name] ?: "基础策略" else ProxyGroupMembers.subtitle(member)
                            GroupMemberProtocolBadge(label, member.kind, Modifier.padding(top = 4.dp, bottom = 2.dp).testTag("group_member_protocol_$index"))
                        }
                        Icon(Icons.Outlined.DragHandle, null, Modifier.size(48.dp).padding(12.dp).testTag("group_member_handle_${member.id}"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                }
            }
        }
        item("add-members") {
            Surface(color = dashboardCardColor(), shape = AppSuperellipseShape(topStart = if (members.isEmpty()) 24.dp else 0.dp,
                topEnd = if (members.isEmpty()) 24.dp else 0.dp, bottomStart = 24.dp, bottomEnd = 24.dp)) {
                TextButton(onAdd, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("group_members_add"), contentPadding = PaddingValues(horizontal = 20.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline, null); Text("添加节点", Modifier.weight(1f).padding(start = 20.dp))
                }
            }
        }
        item("options") { Column { after() } }
    }
}

@Composable
private fun GroupMemberProtocolBadge(label: String, kind: GroupMemberKind, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when (kind) { GroupMemberKind.FILTER -> colors.tertiaryContainer; GroupMemberKind.EXCLUDE -> colors.errorContainer; else -> colors.secondaryContainer }
    val foreground = when (kind) { GroupMemberKind.FILTER -> colors.onTertiaryContainer; GroupMemberKind.EXCLUDE -> colors.onErrorContainer; else -> colors.onSecondaryContainer }
    Surface(modifier, shape = AppSuperellipseShape(8.dp), color = background) {
        Text(label, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), color = foreground, fontSize = 12.sp,
            lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
