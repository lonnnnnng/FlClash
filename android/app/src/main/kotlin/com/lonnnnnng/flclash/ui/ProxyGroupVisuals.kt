package com.lonnnnnng.flclash.ui

import androidx.core.net.toUri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lonnnnnng.flclash.data.ProxyGroupIssue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GroupIssueButton(issues: List<ProxyGroupIssue>, tag: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) {
        Icon(Icons.Outlined.ErrorOutline, "${issues.size} 个策略组问题", tint = MaterialTheme.colorScheme.error)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("策略组问题") },
        text = { GroupIssuesBanner(issues, "${tag}_details") },
        confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } })
}

@Composable
internal fun GroupIssuesBanner(issues: List<ProxyGroupIssue>, tag: String) {
    if (issues.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            issues.forEach { issue ->
                Text(issue.message + if (issue.blocking) "" else "（可保留，运行前请修复）",
                    color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal fun profileGroupIconModel(value: String): Any? = when {
    value.startsWith("data:image/") && value.length <= 2 * 1024 * 1024 -> runCatching {
        val header = value.substringBefore(',')
        require(header.endsWith(";base64"))
        Base64.decode(value.substringAfter(','), Base64.DEFAULT)
    }.getOrNull()
    value.startsWith("https://") || value.startsWith("http://") -> value
    else -> null
}

@Composable
internal fun ProfileGroupIcon(value: String, name: String, modifier: Modifier = Modifier) {
    val model = remember(value) { profileGroupIconModel(value) }
    var loaded by remember(value) { mutableStateOf(false) }
    var failed by remember(value) { mutableStateOf(false) }
    Box(modifier.semantics { stateDescription = if (loaded) "图标已加载" else if (failed) "图标加载失败" else if (model == null) "默认图标" else "图标加载中" }, contentAlignment = Alignment.Center) {
        if (!loaded) {
            if (failed) Icon(Icons.Outlined.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(remember(name) { name.takeIf(String::isNotBlank)?.let { String(Character.toChars(it.codePointAt(0))) } ?: "?" },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (model != null) AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
            onSuccess = { loaded = true; failed = false }, onError = { loaded = false; failed = true })
    }
}

@Composable
internal fun ProfileGroupIconPicker(value: String, groupName: String, candidates: List<String>, recent: List<String>,
    onRecord: (String, Boolean) -> Unit, onReadIcon: suspend (String) -> String = { error("图片读取不可用") },
    onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var link by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(value.takeIf { it.startsWith("http") }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var deleteRecord by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun choose(icon: String) { if (icon.isNotBlank()) onRecord(icon, false); onSelect(icon) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            reading = true; error = null
            try {
                val encoded = onReadIcon(uri.toString())
                choose(encoded)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "无法读取图片，请选择不超过 128 KiB 的图片" }
            finally { reading = false }
        }
    }
    val icons = (listOf(value) + recent + candidates).filter(String::isNotBlank).distinct()
    fun label(icon: String): String = if (icon.startsWith("data:")) "本地图片" else icon.toUri().lastPathSegment?.substringBeforeLast('.')?.ifBlank { null } ?: "图标"
    val shown = icons.filter { query.isBlank() || label(it).contains(query, true) }
    ProfileEditorPage(onDismissRequest = onDismiss, title = { Text("图标") }, backTag = "group_icon_back",
        confirmButton = {
            IconButton(onClick = { try { picker.launch("image/*") } catch (_: Exception) { error = "无法打开图片选择器，请重试" } }, enabled = !reading, modifier = Modifier.testTag("group_icon_local")) { Icon(Icons.Outlined.Image, "本地图片") }
            IconButton(onClick = { link = true }, enabled = !reading, modifier = Modifier.testTag("group_icon_link")) { Icon(Icons.Outlined.Link, "图标地址") }
        }, text = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("group_icon_search"), label = { Text("搜索图标") }, singleLine = true)
                error?.takeUnless { link }?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("group_icon_error")) }
                if (reading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (value.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProfileGroupIcon(value, groupName, Modifier.size(40.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("当前图标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onSelect("") }, modifier = Modifier.testTag("group_icon_remove")) { Icon(Icons.Outlined.RemoveCircleOutline, "移除图标") }
                    }
                }
                if (shown.isEmpty()) Text(if (query.isBlank()) "暂无图标，可添加图标地址或选择本地图片" else "没有匹配的图标",
                    Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text("最近使用与配置图标", style = MaterialTheme.typography.titleSmall)
                    LazyVerticalGrid(GridCells.Adaptive(72.dp), modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shown, key = { it }) { icon ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(onClick = { choose(icon) }, shape = MaterialTheme.shapes.medium,
                                    color = if (icon == value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(56.dp).testTag("group_icon_choice_${icons.indexOf(icon)}")) {
                                    Box(contentAlignment = Alignment.Center) { ProfileGroupIcon(icon, groupName, Modifier.size(40.dp)) }
                                }
                                Text(label(icon), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                if (icon in recent) IconButton(onClick = { deleteRecord = icon }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Close, "删除最近图标记录", Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
        })
    if (link) AlertDialog(onDismissRequest = { link = false }, title = { Text("图标地址") }, text = {
        OutlinedTextField(draft, { draft = it; error = null }, Modifier.fillMaxWidth().testTag("group_icon_url"), singleLine = true,
            isError = error != null, supportingText = { error?.let { Text(it) } })
    }, confirmButton = { TextButton(onClick = {
        val url = draft.trim(); val uri = url.toUri()
        if (url.length <= 8192 && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) choose(url)
        else error = "请输入有效的 HTTP 或 HTTPS 图标地址"
    }, modifier = Modifier.testTag("group_icon_url_confirm")) { Text("确认") } }, dismissButton = { TextButton(onClick = { link = false; error = null }) { Text("取消") } })
    deleteRecord?.let { icon -> AlertDialog(onDismissRequest = { deleteRecord = null }, title = { Text("删除最近图标记录？") },
        text = { Text("已应用到配置的图标会保留。") }, confirmButton = { TextButton(onClick = { onRecord(icon, true); deleteRecord = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteRecord = null }) { Text("取消") } }) }
}
