package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*

@Composable
internal fun ProfileProxyResourceBindings(section: String, source: String, profiles: List<Profile>, enabled: Boolean, onChange: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var alias by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val definitions = remember(section) { runCatching { ProfileProxyResources.definitions(section) }.getOrNull() }
    val referenced = remember(source, section) {
        runCatching { ProfileProxyResources.referenced(ProxyGroupDrafts.root(source)) }.getOrDefault(emptySet()) + definitions?.keySet().orEmpty()
    }
    Text(tr("代理资源引用"), style = MaterialTheme.typography.titleMedium)
    if (referenced.isEmpty()) Text(tr("可选择订阅或文件配置作为代理资源"), style = MaterialTheme.typography.bodySmall)
    referenced.forEach { name ->
        val definition = definitions?.get(name)
        val id = ProfileProxyResources.idOf(definition)
        val resource = profiles.firstOrNull { it.id == id && it.type != ProfileType.CUSTOM }
        val missing = definition == null || ProfileProxyResources.isReference(definition) && resource == null
        Row(Modifier.fillMaxWidth().testTag("proxy_resource_binding_$name"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name)
                Text(if (missing) tr("资源缺失，请重新选择") else if (resource != null) tr("配置资源 · {0}", resource.realLabel) else tr("配置自有资源"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (missing) Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            TextButton(onClick = { alias = name; choosing = true }, enabled = enabled) { Text(tr("选择")) }
        }
    }
    TextButton(onClick = { alias = null; choosing = true }, enabled = enabled, modifier = Modifier.testTag("proxy_resource_binding_add")) { Text(tr("选择代理资源")) }
    error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
    if (choosing) ProfileEditorPage(onDismissRequest = { choosing = false; query = "" }, backTag = "proxy_resource_picker_back", title = { Text(tr("选择代理资源")) }, bottomBar = { DockedProfileSearch(query, { query = it }, "proxy_resource_search", enabled = enabled) }, text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val available = profiles.filter { it.type != ProfileType.CUSTOM }
            val choices = available.filter { it.realLabel.contains(query.trim(), true) }
            val listState = rememberResourcePickerListState(choices.map { it.id },
                alias?.let { ProfileProxyResources.idOf(definitions?.get(it)) })
            if (choices.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                // 作者 long：搜索无结果不代表资源目录为空，保留查询并给出清除入口，不能误导用户重新导入配置。
                ProxyPageEmptyState(if (available.isEmpty()) tr("暂无可选配置") else tr("无搜索结果"),
                    if (available.isEmpty()) tr("请先在配置页添加订阅或文件。") else tr("试试其他关键词，或清除搜索查看全部配置。"),
                    "proxy_resource_empty", searching = available.isNotEmpty(),
                    actionLabel = if (available.isNotEmpty()) tr("清除搜索") else null,
                    actionTag = "proxy_resource_clear_search", onAction = { query = "" })
            } else Surface(Modifier.fillMaxWidth().weight(1f, fill = false), shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
            LazyColumn(state = listState) {
                items(choices, key = { it.id }) { profile ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        runCatching { ProfileProxyResources.bind(section, profile, alias) }.onSuccess {
                            onChange(it); choosing = false; query = ""; error = null
                        }.onFailure { error = it.message; choosing = false }
                    }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp).testTag("proxy_resource_pick_${profile.id}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.realLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (profile.type == ProfileType.URL) tr("订阅配置") else tr("文件配置"), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (alias?.let { ProfileProxyResources.idOf(definitions?.get(it)) } == profile.id)
                            AppGlyphIcon(AppGlyphs.check, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            }
        }
    })
}
