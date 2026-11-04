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
import com.lonnnnnng.flclash.data.RuleResource
import com.lonnnnnng.flclash.data.RuleResources

@Composable
internal fun RuleResourceBindings(section: String, source: String, resources: List<RuleResource>, enabled: Boolean, onChange: (String) -> Unit, onManage: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var alias by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val definitions = remember(section) { runCatching { RuleResources.document("rule-providers:\n" + section.lines().joinToString("\n") { "  $it" } + "\n").getAsJsonObject("rule-providers") }.getOrNull() }
    val referenced = remember(source, section) {
        val names = linkedSetOf<String>()
        Regex("(?i)RULE-SET,([^,()\\n]+)").findAll(source).forEach { names += it.groupValues[1].trim() }
        Regex("rule-set:([^'\\\"\\s:]+)").findAll(source).forEach { names += it.groupValues[1].split(',') }
        definitions?.keySet()?.let { names.addAll(it) }
        names.toList()
    }
    Text("规则资源引用", style = MaterialTheme.typography.titleMedium)
    if (referenced.isEmpty()) Text("当前配置尚未引用规则资源", style = MaterialTheme.typography.bodySmall)
    referenced.forEach { name ->
        val definition = definitions?.get(name)
        val id = RuleResources.idOf(definition)
        val resource = resources.firstOrNull { it.id == id }
        val missing = definition == null || id != null && resource == null
        Row(Modifier.fillMaxWidth().testTag("resource_binding_$name"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name)
                Text(if (missing) "资源缺失，请重新选择" else if (resource != null) "全局资源 · ${resource.label}" else "配置自有资源", style = MaterialTheme.typography.bodySmall,
                    color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (missing) Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            TextButton(onClick = { alias = name; choosing = true }, enabled = enabled) { Text("选择") }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { alias = null; choosing = true }, enabled = enabled, modifier = Modifier.testTag("resource_binding_add")) { Text("选择规则资源") }
        TextButton(onClick = onManage, enabled = enabled) { Text("管理资源") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (choosing) ProfileEditorPage(onDismissRequest = { choosing = false; search = "" }, title = { Text("选择规则资源") }, backTag = "resource_picker_back", text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text("搜索") }, modifier = Modifier.fillMaxWidth())
            if (resources.isEmpty()) { Text("暂无全局规则资源，请先添加资源。"); TextButton(onClick = { choosing = false; onManage() }) { Text("管理资源") } }
            LazyColumn(Modifier.weight(1f)) {
                items(resources.filter { it.label.contains(search, true) }, key = { it.id }) { resource ->
                    TextButton(onClick = {
                        // 作者 long：缺失引用用原别名重新绑定，不改规则目标；添加时按全局名称登记。
                        runCatching { RuleResources.bind(section, resource, alias) }.onSuccess {
                            onChange(it); error = null; choosing = false; search = ""
                        }.onFailure { error = "配置中已有同名资源；请先调整原资源名称"; choosing = false }
                    }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(resource.label); Text(resource.behavior + " · " + resource.format, style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    })
}
