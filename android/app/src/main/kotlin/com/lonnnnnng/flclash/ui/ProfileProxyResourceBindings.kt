package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    Text("代理资源引用", style = MaterialTheme.typography.titleMedium)
    if (referenced.isEmpty()) Text("可选择订阅或文件配置作为代理资源", style = MaterialTheme.typography.bodySmall)
    referenced.forEach { name ->
        val definition = definitions?.get(name)
        val id = ProfileProxyResources.idOf(definition)
        val resource = profiles.firstOrNull { it.id == id && it.type != ProfileType.CUSTOM }
        val missing = definition == null || ProfileProxyResources.isReference(definition) && resource == null
        Row(Modifier.fillMaxWidth().testTag("proxy_resource_binding_$name"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name)
                Text(if (missing) "资源缺失，请重新选择" else if (resource != null) "配置资源 · ${resource.realLabel}" else "配置自有资源",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (missing) Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            TextButton(onClick = { alias = name; choosing = true }, enabled = enabled) { Text("选择") }
        }
    }
    TextButton(onClick = { alias = null; choosing = true }, enabled = enabled, modifier = Modifier.testTag("proxy_resource_binding_add")) { Text("选择代理资源") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (choosing) ProfileEditorPage(onDismissRequest = { choosing = false; query = "" }, backTag = "proxy_resource_picker_back", title = { Text("选择代理资源") }, text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索") }, modifier = Modifier.fillMaxWidth().testTag("proxy_resource_search"))
            val choices = profiles.filter { it.type != ProfileType.CUSTOM && it.realLabel.contains(query, true) }
            if (choices.isEmpty()) Text("暂无可选配置，请先在配置页添加订阅或文件。")
            LazyColumn(Modifier.weight(1f)) {
                items(choices, key = { it.id }) { profile ->
                    TextButton(onClick = {
                        runCatching { ProfileProxyResources.bind(section, profile, alias) }.onSuccess {
                            onChange(it); choosing = false; query = ""; error = null
                        }.onFailure { error = it.message; choosing = false }
                    }, modifier = Modifier.fillMaxWidth().testTag("proxy_resource_pick_${profile.id}")) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(profile.realLabel)
                            Text(if (profile.type == ProfileType.URL) "订阅配置" else "文件配置", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    })
}
