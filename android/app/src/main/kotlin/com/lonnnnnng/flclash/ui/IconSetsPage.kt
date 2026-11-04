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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*

val LocalResourceIcons = staticCompositionLocalOf<List<ResourceIcon>> { emptyList() }
val LocalResourceIconSets = staticCompositionLocalOf<List<ResourceIconSet>> { emptyList() }

@Composable
internal fun IconSetsPage(sets: List<ResourceIconSet>, busy: Boolean, error: String?, completed: Long,
    onDismiss: () -> Unit, onImportUrl: (String, String) -> Unit, onPickFile: () -> Unit,
    onOptions: (ResourceIconSet, String, String) -> Unit, onSync: (ResourceIconSet) -> Unit,
    onDelete: (String) -> Unit, onMove: (String, Int) -> Unit, onImportUrls: (() -> Unit)? = null,
    onReorder: ((List<String>) -> Unit)? = null) {
    var addMenu by remember { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ResourceIconSet?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ResourceIconSet?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sorting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sets.size) { if (sets.size < 2) sorting = false }
    LaunchedEffect(completed) { if (completed > 0 && error == null) { importing = false; editing = null } }
    ProfileEditorPage(onDismissRequest = { if (!busy) { if (sorting) sorting = false else onDismiss() } }, title = { Text("图标集") }, backTag = "icon_sets_back",
        confirmButton = {
        if (sorting) TextButton(onClick = { sorting = false }, enabled = !busy, modifier = Modifier.testTag("icon_sets_sort_done")) { Text("完成") }
        else {
        if (sets.size > 1 && onReorder != null) IconButton(onClick = { sorting = true }, enabled = !busy, modifier = Modifier.testTag("icon_sets_sort")) { Icon(Icons.Outlined.Sort, null) }
        Box {
            IconButton(onClick = { addMenu = true }, enabled = !busy, modifier = Modifier.testTag("icon_sets_add")) { Icon(Icons.Outlined.AddCircleOutline, null) }
            DropdownMenu(addMenu, { addMenu = false }) {
                DropdownMenuItem(text = { Text("从 URL 导入") }, onClick = { addMenu = false; if (onImportUrls != null) onImportUrls() else importing = true })
                DropdownMenuItem(text = { Text("从文件导入") }, onClick = { addMenu = false; onPickFile() })
            }
        } } }, text = {
            Column(Modifier.fillMaxSize().testTag("icon_sets_page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (sorting && onReorder != null) ResourceOrderList(sets.map {
                    ResourceOrderItem(it.id, it.name, "${it.icons.size} 个图标", it.icons.firstOrNull()?.url.orEmpty())
                }, busy, "icon_sets", onReorder, Modifier.fillMaxWidth().weight(1f))
                else {
                if (sets.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无图标集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(sets, key = { _, item -> item.id }) { index, item ->
                        Box {
                            ListItem(headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(if (item.url.isEmpty()) "本地 · ${item.icons.size} 个图标" else "远程 · ${item.icons.size} 个图标") },
                                leadingContent = { ProfileGroupIcon(item.icons.firstOrNull()?.url.orEmpty(), item.name, Modifier.size(32.dp)) },
                                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                                modifier = Modifier.fillMaxWidth().testTag("icon_set_${item.id}").combinedClickable(enabled = !busy,
                                    onClick = { previewId = item.id; query = "" }, onLongClick = { menuId = item.id }),
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                            DropdownMenu(menuId == item.id, { menuId = null }) {
                                if (item.url.isNotEmpty()) DropdownMenuItem(text = { Text("同步") }, onClick = { menuId = null; onSync(item) })
                                DropdownMenuItem(text = { Text("选项") }, onClick = { menuId = null; editing = item })
                                if (index > 0) DropdownMenuItem(text = { Text("上移") }, onClick = { menuId = null; onMove(item.id, -1) })
                                if (index < sets.lastIndex) DropdownMenuItem(text = { Text("下移") }, onClick = { menuId = null; onMove(item.id, 1) })
                                DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menuId = null; deleting = item })
                            }
                        }
                    }
                }
                }
            }
        })
    if (importing) IconSetOptionsDialog(null, busy, error, { importing = false }, onImportUrl)
    editing?.let { original -> IconSetOptionsDialog(original, busy, error, { editing = null }, { name, url -> onOptions(original, name, url) }) }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除 ${item.name}？") },
        text = { Text("配置中已经使用的图标链接会保留。") }, confirmButton = { TextButton(onClick = { onDelete(item.id); deleting = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    previewId?.let { id ->
        val item = sets.firstOrNull { it.id == id }
        val shown = item?.icons.orEmpty().filter { it.name.contains(query, true) }
        val context = LocalContext.current
        ProfileEditorPage(onDismissRequest = { previewId = null }, title = { Text(item?.name ?: "图标集") }, backTag = "icon_set_preview_back", text = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("icon_set_search"), singleLine = true,
                    label = { Text("搜索图标") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                if (shown.isEmpty()) Text("没有匹配的图标", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(GridCells.Adaptive(80.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(shown, key = { it.url }) { icon ->
                        Column(Modifier.combinedClickable(onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(icon.name, icon.url))
                        }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileGroupIcon(icon.url, icon.name, Modifier.size(40.dp))
                            Text(icon.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        })
    }
}

@Composable
private fun IconSetOptionsDialog(original: ResourceIconSet?, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable(original?.id) { mutableStateOf(original?.name.orEmpty()) }
    var url by rememberSaveable(original?.id) { mutableStateOf(original?.url.orEmpty()) }
    var invalid by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (original == null) "从 URL 导入" else "选项") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it; invalid = null }, Modifier.fillMaxWidth().testTag("icon_set_name"), label = { Text(if (original == null) "名称（可选）" else "名称") }, enabled = !busy, singleLine = true)
            if (original == null || original.url.isNotEmpty()) OutlinedTextField(url, { url = it; invalid = null }, Modifier.fillMaxWidth().testTag("icon_set_url"), label = { Text("图标集地址") }, enabled = !busy, singleLine = true)
            (invalid ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = {
        if (original != null && (name.trim().isEmpty() || name.length > 128)) invalid = "名称须为 1 至 128 字符"
        else if ((original == null || original.url.isNotEmpty()) && !IconSetCodec.validUrl(url.trim())) invalid = "请输入有效 HTTP/HTTPS 地址"
        else onSave(name.trim(), url.trim())
    }, enabled = !busy, modifier = Modifier.testTag("icon_set_save")) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
