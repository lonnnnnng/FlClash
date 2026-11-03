package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.ProfileDialerCatalog
import com.lonnnnnng.flclash.data.ProfileDialerEditor

/** 作者 long：列表只列配置实际引用的节点；目标逐项保存到本配置，不修改全局节点或其他配置的链路。 */
@Composable
internal fun ProfileDialersPage(state: ProfileDialersEditorUiState, onDismiss: () -> Unit,
    onSetTarget: (String, String?) -> Unit, onSaveText: (String) -> Unit) {
    val profile = state.profile ?: return
    val catalog = remember(state.sourceContent) { ProfileDialerCatalog(state.sourceContent) }
    val mapping = remember(state.dialers) { ProfileDialerEditor.parseDialers(state.dialers) }
    var selecting by remember(profile.id) { mutableStateOf<String?>(null) }
    var quick by remember(profile.id) { mutableStateOf(false) }
    ProfileEditorPage(onDismissRequest = { if (!state.isSaving) onDismiss() }, backTag = "profile_dialers_back",
        title = { Text("链式代理") }, confirmButton = {
            IconButton(onClick = { quick = true }, enabled = !state.isSaving, modifier = Modifier.testTag("profile_dialers_quick")) {
                Icon(Icons.Outlined.EditNote, "快捷编辑映射")
            }
        }, text = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (catalog.nodes.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无引用的本地代理，请先在策略组中添加节点。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.weight(1f).testTag("profile_dialers_list")) {
                    items(catalog.nodes, key = { it.name }) { node ->
                        val target = mapping[node.name]
                        val issue = catalog.issue(node.name, target, mapping)
                        ListItem(headlineContent = { Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(issue ?: target ?: "无", color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = { Icon(if (issue == null) Icons.Outlined.ChevronRight else Icons.Outlined.ErrorOutline, if (issue != null) issue else null) },
                            modifier = Modifier.testTag("profile_dialer_${node.name}").clickable(!state.isSaving) { selecting = node.name })
                    }
                }
            }
            selecting?.let { proxy ->
                var query by remember(proxy) { mutableStateOf("") }
                ProfileEditorPage(onDismissRequest = { selecting = null }, title = { Text("链式代理 · $proxy") }, backTag = "profile_dialer_targets_back", text = {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("profile_dialer_search"), singleLine = true,
                            label = { Text("搜索节点或策略组") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                        LazyColumn {
                            item { DialerTarget("无", null, mapping[proxy] == null, "profile_dialer_none") {
                                onSetTarget(proxy, null); selecting = null
                            } }
                            val nodes = catalog.nodes.filter { it.name.contains(query, true) && catalog.selectable(proxy, it.name, mapping) }
                            if (nodes.isNotEmpty()) item { Text("本地代理", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                            items(nodes, key = { "node:${it.name}" }) { node -> DialerTarget(node.name, node.type, mapping[proxy] == node.name, "profile_dialer_target_${node.name}") {
                                onSetTarget(proxy, node.name); selecting = null
                            } }
                            val groups = catalog.groups.filter { it.name.contains(query, true) && catalog.selectable(proxy, it.name, mapping) }
                            if (groups.isNotEmpty()) item { Text("策略组", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                            items(groups, key = { "group:${it.name}" }) { group -> DialerTarget(group.name, group.text("type"), mapping[proxy] == group.name, "profile_dialer_target_${group.name}") {
                                onSetTarget(proxy, group.name); selecting = null
                            } }
                        }
                    }
                })
            }
            if (quick) ProfileDialersEditorDialog(profile, state.dialers, state.isSaving, { quick = false }, onSaveText)
        })
}

@Composable
private fun DialerTarget(name: String, detail: String?, selected: Boolean, tag: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = detail?.let { { Text(it) } }, trailingContent = { RadioButton(selected, null) },
        modifier = Modifier.testTag(tag).clickable(onClick = onClick))
}
