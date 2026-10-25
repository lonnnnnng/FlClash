package com.lonnnnnng.flclash.ui

import android.content.ContentResolver
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KClashApp(
    runtimeViewModel: MainViewModel,
    profilesViewModel: ProfilesViewModel,
    proxyGroupsViewModel: ProxyGroupsViewModel,
    onStartRuntime: () -> Unit,
    onStopRuntime: () -> Unit,
) {
    val runtimeState by runtimeViewModel.runtimeState.collectAsState()
    val runtimeStats by runtimeViewModel.stats.collectAsState()
    val runtimeConnections by runtimeViewModel.connections.collectAsState()
    val profilesState by profilesViewModel.state.collectAsState()
    val editorState by profilesViewModel.editorState.collectAsState()
    val proxyGroupsState by proxyGroupsViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var profilePendingDeletion by remember { mutableStateOf<Profile?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取所选配置文件")
            require(bytes.isNotEmpty()) { "配置文件为空" }
            val displayName = queryDisplayName(context.contentResolver, uri)
            val label = displayName
                ?.substringBeforeLast('.', displayName)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "本地配置"
            label to bytes
        }
        result.onSuccess { (label, bytes) ->
            // 作者 long：导入后立即复制到应用目录，避免外部文档提供者撤回 URI 后配置无法再次启动。
            profilesViewModel.addFile(label, bytes)
        }.onFailure { error ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(error.message ?: "导入配置失败")
            }
        }
    }

    LaunchedEffect(profilesViewModel) {
        profilesViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(runtimeViewModel) {
        runtimeViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(proxyGroupsViewModel) {
        proxyGroupsViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("KClash") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("添加配置") },
                icon = {},
                onClick = { showAddDialog = true },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RuntimeCard(
                    state = runtimeState,
                    onStart = onStartRuntime,
                    onStop = onStopRuntime,
                )
            }
            item {
                RuntimeStatsCard(
                    state = runtimeStats,
                    runtimeState = runtimeState,
                )
            }
            item {
                ConnectionsCard(
                    state = runtimeConnections,
                    runtimeState = runtimeState,
                )
            }
            item {
                ProxyGroupsCard(
                    state = proxyGroupsState,
                    runtimeState = runtimeState,
                    onRefresh = proxyGroupsViewModel::refresh,
                    onSelect = proxyGroupsViewModel::selectProxy,
                    onTestDelay = proxyGroupsViewModel::testDelay,
                )
            }
            item {
                Text(
                    text = "配置",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (profilesState.profiles.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("还没有配置")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "添加订阅或创建自定义配置后，配置会保存在 KClash 独立数据目录中。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { showAddDialog = true }) {
                                Text("添加配置")
                            }
                        }
                    }
                }
            } else {
                items(profilesState.profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        selected = profile.id == profilesState.currentProfileId,
                        busy = profilesState.isBusy,
                        onSelect = { profilesViewModel.select(profile.id) },
                        onEdit = { profilesViewModel.openEditor(profile) },
                        onUpdate = { profilesViewModel.update(profile) },
                        onDelete = { profilePendingDeletion = profile },
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            busy = profilesState.isBusy,
            onDismiss = { showAddDialog = false },
            onPickFile = {
                showAddDialog = false
                filePicker.launch(arrayOf("*/*"))
            },
            onAddRemote = { label, url ->
                showAddDialog = false
                profilesViewModel.addRemote(url, label)
            },
            onAddCustom = { label ->
                showAddDialog = false
                profilesViewModel.addCustom(label)
            },
        )
    }

    profilePendingDeletion?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDeletion = null },
            title = { Text("删除配置") },
            text = { Text("确定删除“${profile.realLabel}”吗？本地配置文件也会一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        profilePendingDeletion = null
                        profilesViewModel.delete(profile)
                    },
                    enabled = !profilesState.isBusy,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDeletion = null }) { Text("取消") }
            },
        )
    }

    editorState.profile?.let { profile ->
        if (editorState.isLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("读取配置") },
                text = { CircularProgressIndicator() },
                confirmButton = {},
            )
        } else {
            ProfileEditorDialog(
                profile = profile,
                initialContent = editorState.content,
                saving = editorState.isSaving,
                onDismiss = profilesViewModel::closeEditor,
                onSave = profilesViewModel::saveEditor,
            )
        }
    }
}

@Composable
private fun ProxyGroupsCard(
    state: ProxyGroupsUiState,
    runtimeState: CoreRuntimeStatus,
    onRefresh: () -> Unit,
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
) {
    var groupPendingSelection by remember { mutableStateOf<com.lonnnnnng.flclash.core.ProxyGroupInfo?>(null) }
    val canRefresh = runtimeState == CoreRuntimeStatus.RUNNING &&
        !state.isLoading &&
        state.switchingGroup == null
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("代理组", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    TextButton(onClick = onRefresh, enabled = canRefresh) { Text("刷新") }
                }
            }
            if (state.groups.isEmpty() && !state.isLoading) {
                Text(
                    if (runtimeState == CoreRuntimeStatus.RUNNING) {
                        "当前配置没有可展示的代理组。"
                    } else {
                        "启动核心后加载代理组。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.groups.forEach { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (group.isSelector && group.all.isNotEmpty()) groupPendingSelection = group },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${group.type} · 当前：${group.now ?: "未选择"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (group.isSelector) {
                                    TextButton(
                                        onClick = { groupPendingSelection = group },
                                        enabled = state.switchingGroup == null,
                                    ) { Text("切换") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    groupPendingSelection?.let { group ->
        AlertDialog(
            onDismissRequest = { if (state.switchingGroup == null) groupPendingSelection = null },
            title = { Text("切换 ${group.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.all.forEach { proxyName ->
                        val delay = state.delayState(group, proxyName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = proxyName == group.now,
                                onClick = {
                                    onSelect(group, proxyName)
                                    groupPendingSelection = null
                                },
                                enabled = state.switchingGroup == null,
                            )
                            Text(
                                proxyName,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { onTestDelay(group, proxyName) },
                                enabled = state.switchingGroup == null && delay?.isTesting != true,
                            ) {
                                if (delay?.isTesting == true) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(delayLabel(delay))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { groupPendingSelection = null },
                    enabled = state.switchingGroup == null,
                ) { Text("关闭") }
            },
        )
    }
}

private fun delayLabel(delay: ProxyDelayUiState?): String = when {
    delay == null -> "测速"
    delay.isTesting -> "测速中"
    delay.value == null -> "无结果"
    delay.value > 0 -> "${delay.value} ms"
    // 作者 long：保留 Mihomo 的超时/失败语义，让用户区分网络超时和普通测试失败。
    delay.value == CoreBridge.DELAY_TIMED_OUT -> "超时"
    else -> "失败"
}

@Composable
private fun RuntimeCard(
    state: CoreRuntimeStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("核心状态", style = MaterialTheme.typography.labelLarge)
                Text(state.label, style = MaterialTheme.typography.titleMedium)
            }
            if (state == CoreRuntimeStatus.STARTING || state == CoreRuntimeStatus.STOPPING) {
                CircularProgressIndicator()
            } else {
                Button(onClick = if (state.isRunning) onStop else onStart) {
                    Text(if (state.isRunning) "停止核心" else "启动核心")
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatsCard(
    state: RuntimeStatsUiState,
    runtimeState: CoreRuntimeStatus,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("运行统计", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.isLoading && runtimeState.isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            if (!runtimeState.isRunning) {
                Text(
                    "启动核心后显示实时流量和连接数。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.error != null) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatValue("上传", formatRate(state.traffic.up))
                    StatValue("下载", formatRate(state.traffic.down))
                    StatValue("连接", state.connectionCount.toString())
                }
                Text(
                    "累计：↑ ${formatBytes(state.totalTraffic.up)}  ·  ↓ ${formatBytes(state.totalTraffic.down)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private fun formatRate(bytes: Long): String = "${formatBytes(bytes)}/s"

@Composable
private fun ConnectionsCard(
    state: ConnectionsUiState,
    runtimeState: CoreRuntimeStatus,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "活动连接${if (runtimeState.isRunning) "（${state.connections.size}）" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.isLoading && runtimeState.isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            when {
                !runtimeState.isRunning -> Text(
                    "启动核心后显示当前活动连接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.error != null -> Text(
                    state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                state.connections.isEmpty() -> Text(
                    "当前没有活动连接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.connections.take(MAX_CONNECTIONS_ON_HOME).forEach { connection ->
                    ConnectionRow(connection)
                }
            }
        }
    }
}

@Composable
private fun ConnectionRow(connection: CoreConnection) {
    val metadata = connection.metadata
    val title = metadata.host.ifBlank {
        metadata.destinationIp.ifBlank { metadata.remoteDestination.ifBlank { "未知目标" } }
    }
    val endpoint = listOf(metadata.destinationIp, metadata.destinationPort)
        .filter { it.isNotBlank() }
        .joinToString(":")
    val chain = connection.chains.joinToString(" → ")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(metadata.network.uppercase(), endpoint, metadata.process, chain)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "↑ ${formatBytes(connection.upload)} · ↓ ${formatBytes(connection.download)}" +
                if (connection.rule.isNotBlank()) " · ${connection.rule}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_CONNECTIONS_ON_HOME = 5

@Composable
private fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onSelect) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.realLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profileSubtitle(profile),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            profile.lastUpdateAt?.let { updatedAt ->
                Text(
                    text = "更新于 ${DateUtils.getRelativeTimeSpanString(updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (profile.type != ProfileType.URL) {
                    TextButton(onClick = onEdit, enabled = !busy) { Text("编辑") }
                }
                if (profile.type == ProfileType.URL) {
                    TextButton(onClick = onUpdate, enabled = !busy) { Text("同步") }
                }
                TextButton(onClick = onDelete, enabled = !busy) { Text("删除") }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    profile: Profile,
    initialContent: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var content by rememberSaveable(profile.id) { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("编辑 ${profile.realLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "保存后会替换 KClash 本地配置文件，核心启动时仍会再次校验 YAML。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    maxLines = 20,
                    enabled = !saving,
                    label = { Text("YAML") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(content) },
                enabled = !saving && content.isNotBlank(),
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

private fun profileSubtitle(profile: Profile): String {
    val info = profile.subscriptionInfo
    if (info?.usage != null) {
        val used = info.upload + info.download
        return "已用 ${formatBytes(used)} / ${formatBytes(info.total)}"
    }
    return when (profile.type) {
        ProfileType.URL -> "远程订阅"
        ProfileType.FILE -> "本地文件"
        ProfileType.CUSTOM -> "自定义配置"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "%.1f%s".format(value, units[index])
}

private fun queryDisplayName(contentResolver: ContentResolver, uri: android.net.Uri): String? {
    return contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    }
}

@Composable
private fun AddProfileDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onAddRemote: (label: String, url: String) -> Unit,
    onAddCustom: (label: String) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var customMode by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (customMode) "创建自定义配置" else "添加订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickFile, enabled = !busy) {
                    Text("从文件导入 YAML")
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名称（可选）") },
                    singleLine = true,
                    enabled = !busy,
                )
                if (!customMode) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("订阅链接") },
                        singleLine = true,
                        enabled = !busy,
                    )
                    OutlinedButton(onClick = { customMode = true }, enabled = !busy) {
                        Text("创建自定义配置")
                    }
                } else {
                    TextButton(onClick = { customMode = false }, enabled = !busy) {
                        Text("改为添加订阅")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (customMode) onAddCustom(label) else onAddRemote(label, url)
                },
                enabled = !busy && (customMode || url.isNotBlank()),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
