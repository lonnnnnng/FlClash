package com.lonnnnnng.flclash.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

val LocalResourceIcons = staticCompositionLocalOf<List<ResourceIcon>> { emptyList() }
val LocalResourceIconSets = staticCompositionLocalOf<List<ResourceIconSet>> { emptyList() }

@Composable
internal fun IconSetsPage(sets: List<ResourceIconSet>, busy: Boolean, error: String?, completed: Long,
    onDismiss: () -> Unit, onImportUrl: (String, String) -> Unit, onPickFile: () -> Unit,
    onOptions: (ResourceIconSet, String, String) -> Unit, onSync: (ResourceIconSet) -> Unit,
    onDelete: (String) -> Unit, onMove: (String, Int) -> Unit, onImportUrls: (() -> Unit)? = null,
    onReorder: ((List<String>) -> Unit)? = null, onRetryRecovery: (() -> Unit)? = null) {
    var addMenu by remember { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ResourceIconSet?>(null) }
    var optionsClosing by remember { mutableStateOf(false) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ResourceIconSet?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sorting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sets.size) { if (sets.size < 2) sorting = false }
    // 作者 long：成功回执先触发表单退场，等动画结束再移除，不能让已保存的Sheet在半空突然消失。
    LaunchedEffect(completed) { if (completed > 0 && error == null && (importing || editing != null)) optionsClosing = true }
    ProfileEditorPage(onDismissRequest = { if (!busy) { if (sorting) sorting = false else onDismiss() } }, title = { Text(tr("图标集")) }, backTag = "icon_sets_back",
        confirmButton = {
        if (sorting) TextButton(onClick = { sorting = false }, enabled = !busy, modifier = Modifier.testTag("icon_sets_sort_done")) { Text(tr("完成")) }
        else {
        if (sets.size > 1 && onReorder != null) IconButton(onClick = { sorting = true }, enabled = !busy, modifier = Modifier.testTag("icon_sets_sort")) { Icon(Icons.AutoMirrored.Outlined.Sort, null) }
        Box {
            IconButton(onClick = { addMenu = true }, enabled = !busy, modifier = Modifier.testTag("icon_sets_add")) { Icon(Icons.Outlined.AddCircleOutline, null) }
            DropdownMenu(addMenu, { addMenu = false }) {
                DropdownMenuItem(text = { Text(tr("从 URL 导入")) }, onClick = { addMenu = false; if (onImportUrls != null) onImportUrls() else importing = true })
                DropdownMenuItem(text = { Text(tr("从文件导入")) }, onClick = { addMenu = false; onPickFile() })
            }
        } } }, text = {
            Column(Modifier.fillMaxSize().testTag("icon_sets_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                onRetryRecovery?.let { retry -> TextButton(retry, modifier = Modifier.testTag("icon_file_recovery_retry")) { Text(tr("重试恢复")) } }
                if (sorting && onReorder != null) ResourceOrderList(sets.map {
                    ResourceOrderItem(it.id, it.name, tr("{0} 个图标", it.icons.size), it.icons.firstOrNull()?.url.orEmpty())
                }, busy, "icon_sets", onReorder, Modifier.fillMaxWidth().weight(1f))
                else {
                if (sets.isEmpty()) {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(Modifier.offset(y = -(maxHeight * .14f)).widthIn(max = 320.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // 作者 long：Flutter 空态把图形与标题作为一个偏上内容组，避免空目录页只剩一行字。
                            Box(Modifier.size(160.dp).testTag("icon_sets_empty_illustration"), contentAlignment = Alignment.Center) {
                                ProfileEmptyIllustration(AppGlyphs.iconTile, ProfileIllustrationPaths.cookie7, 160.dp, flutterPalette = true)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(tr("暂无图标集"), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("icon_sets_empty_label"))
                        }
                    }
                } else LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(sets, key = { _, item -> item.id }) { index, item ->
                        Box {
                            ListItem(headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(if (item.url.isEmpty()) tr("本地 · {0} 个图标", item.icons.size) else tr("远程 · {0} 个图标", item.icons.size)) },
                                leadingContent = { ProfileGroupIcon(item.icons.firstOrNull()?.url.orEmpty(), item.name, Modifier.size(32.dp)) },
                                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                                modifier = Modifier.fillMaxWidth().testTag("icon_set_${item.id}").combinedClickable(enabled = !busy,
                                    onClick = { previewId = item.id; query = "" }, onLongClick = { menuId = item.id }),
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                            DropdownMenu(menuId == item.id, { menuId = null }) {
                                if (item.url.isNotEmpty()) DropdownMenuItem(text = { Text(tr("同步")) }, onClick = { menuId = null; onSync(item) })
                                DropdownMenuItem(text = { Text(tr("选项")) }, onClick = { menuId = null; editing = item })
                                if (index > 0) DropdownMenuItem(text = { Text(tr("上移")) }, onClick = { menuId = null; onMove(item.id, -1) })
                                if (index < sets.lastIndex) DropdownMenuItem(text = { Text(tr("下移")) }, onClick = { menuId = null; onMove(item.id, 1) })
                                DropdownMenuItem(text = { Text(tr("删除"), color = MaterialTheme.colorScheme.error) }, onClick = { menuId = null; deleting = item })
                            }
                        }
                    }
                }
                }
            }
        })
    if (importing) IconSetOptionsDialog(null, busy, error, { importing = false; optionsClosing = false }, onImportUrl, optionsClosing)
    editing?.let { original -> IconSetOptionsDialog(original, busy, error, { editing = null; optionsClosing = false }, { name, url -> onOptions(original, name, url) }, optionsClosing) }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("删除 {0}？", item.name)) },
        text = { Text(tr("配置中已经使用的图标链接会保留。")) }, confirmButton = { TextButton(onClick = { onDelete(item.id); deleting = null }) { Text(tr("删除")) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("取消")) } }) }
    previewId?.let { id ->
        val item = sets.firstOrNull { it.id == id }
        val icons = item?.icons.orEmpty()
        val shown = remember(icons, query) { icons.filter { it.name.contains(query, true) } }
        val context = LocalContext.current
        val gridState = rememberLazyGridState()
        var restorePosition by remember(id) { mutableStateOf<Pair<Int, Int>?>(null) }
        LaunchedEffect(query, icons.size) {
            if (query.isNotEmpty()) {
                gridState.scrollToItem(0)
            } else {
                val anchor = restorePosition ?: return@LaunchedEffect
                if (icons.isEmpty()) {
                    restorePosition = null
                    return@LaunchedEffect
                }
                // 作者 long：恢复查询前的网格行；等待完整目录重新布局，避免短结果把原浏览位置夹到首项。
                snapshotFlow { gridState.layoutInfo.totalItemsCount }.first { it == icons.size }
                gridState.scrollToItem(anchor.first.coerceIn(0, icons.lastIndex), anchor.second)
                restorePosition = null
            }
        }
        fun changeQuery(value: String) {
            if (query.isEmpty() && value.isNotEmpty() && restorePosition == null) {
                restorePosition = gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            }
            query = value
        }
        fun backPreview() {
            if (query.isNotEmpty()) changeQuery("") else previewId = null
        }
        ProfileEditorPage(onDismissRequest = ::backPreview, title = { Text(item?.name ?: tr("图标集")) },
            backTag = "icon_set_preview_back", contentPadding = PaddingValues(0.dp), navigationButtonSurface = true,
            bottomBar = {
                DockedProfileSearch(query, ::changeQuery, "icon_set_preview_search",
                    placeholder = tr("搜索图标"), modifier = Modifier.padding(horizontal = 16.dp))
            }, text = {
            Box(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    IconSetPreviewEmpty(query, onClearQuery = { changeQuery("") })
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val availableWidth = (maxWidth - 32.dp).coerceAtLeast(0.dp)
                        // 作者 long：Flutter 把 88dp 当作网格单元最大宽度；Compose Adaptive 参数是最小宽度，按上限反算列数才能保持同态密度。
                        val columns = ceil((availableWidth.value + 8f) / 96f).toInt().coerceAtLeast(1)
                        key(query.isNotEmpty()) {
                            // 作者 long：进入或退出筛选时重建网格内容层，避免长列表旧可见项留在首帧画面。
                            LazyVerticalGrid(GridCells.Fixed(columns),
                                state = gridState,
                                modifier = Modifier.fillMaxSize().testTag("icon_set_grid"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 12.dp),
                            ) {
                                items(shown, key = { it.url.ifBlank { it.name } }) { icon ->
                                    Column(Modifier.widthIn(max = 88.dp).testTag("icon_set_icon_${icon.name}"),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(Modifier.size(56.dp), shape = AppSuperellipseShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                            Box(Modifier.fillMaxSize().combinedClickable(onClick = {
                                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(icon.name, icon.url))
                                            }), contentAlignment = Alignment.Center) {
                                                if (icon.url.isBlank()) AppGlyphIcon(AppGlyphs.photos, Modifier.size(28.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
                                                else ProfileGroupIcon(icon.url, icon.name, Modifier.size(40.dp))
                                            }
                                        }
                                        Text(icon.name, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })
    }
}

@Composable
private fun IconSetPreviewEmpty(query: String, onClearQuery: () -> Unit) {
    val searching = query.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val illustrationSize = (maxHeight - 184.dp).coerceIn(64.dp, 112.dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileEmptyIllustration(AppGlyphs.iconTile, ProfileIllustrationPaths.cookie7, illustrationSize)
            Spacer(Modifier.height(16.dp))
            Text(if (searching) tr("没有匹配的图标") else tr("暂无图标"), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("icon_set_preview_empty"))
            if (searching) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClearQuery, modifier = Modifier.testTag("icon_set_search_clear")) { Text(tr("清除搜索")) }
            }
        }
    }
}

@Composable
internal fun IconSetOptionsDialog(original: ResourceIconSet?, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (String, String) -> Unit, closing: Boolean = false) {
    var name by rememberSaveable(original?.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(original?.name.orEmpty())) }
    var url by rememberSaveable(original?.id) { mutableStateOf(original?.url.orEmpty()) }
    var invalid by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(busy, error) { if (!busy && error != null) submitted = false }
    fun save() {
        if (busy || submitted || closing) return
        if (original != null && (name.text.trim().isEmpty() || name.text.length > 128)) invalid = "名称须为 1 至 128 字符"
        else if ((original == null || original.url.isNotEmpty()) && !IconSetCodec.validUrl(url.trim())) invalid = tr("请输入有效 HTTP/HTTPS 地址")
        else { submitted = true; onSave(name.text.trim(), url.trim()) }
    }
    val locked = busy || submitted
    if (original != null && original.url.isEmpty()) {
        FloatingInputDialog(tr("选项"), "icon_set_options_dialog", locked, true, onDismiss, ::save, tr("保存"),
            error = invalid ?: error, errorTag = "icon_set_error", confirmTag = "icon_set_save", cancelTag = "icon_set_cancel",
            externallyClosing = closing, onExitComplete = { if (closing) onDismiss() }) { enabled, submit ->
            FloatingInputField(name, { name = it; invalid = null }, "icon_set_name", enabled, submit, placeholder = tr("名称"))
        }
    } else ResourceNamedUrlSheet(if (original == null) tr("从 URL 导入") else tr("选项"), name.text, url,
        { name = TextFieldValue(it); invalid = null }, { url = it; invalid = null }, locked, invalid ?: error,
        closing, onDismiss, ::save)
}
