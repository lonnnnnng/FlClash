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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.snapshotFlow
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
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticKind
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KClashApp(
    runtimeViewModel: MainViewModel,
    profilesViewModel: ProfilesViewModel,
    proxyGroupsViewModel: ProxyGroupsViewModel,
    onStartRuntime: () -> Unit,
    onStopRuntime: () -> Unit,
    batteryOptimizationIgnored: Boolean,
    onRequestBatteryOptimization: () -> Unit,
    onExportLogs: (List<CoreEvent.Log>) -> Unit,
    onExportDiagnostic: (DiagnosticSessionUiState) -> Unit,
    onExportDiagnosticHistory: (List<RuntimeDiagnosticEvent>) -> Unit,
) {
    val runtimeState by runtimeViewModel.runtimeState.collectAsState()
    val runtimeStats by runtimeViewModel.stats.collectAsState()
    val runtimeConnections by runtimeViewModel.connections.collectAsState()
    val runtimeRequests by runtimeViewModel.requests.collectAsState()
    val runtimeDns by runtimeViewModel.dns.collectAsState()
    val runtimeLogs by runtimeViewModel.logs.collectAsState()
    val runtimeProbe by runtimeViewModel.probeState.collectAsState()
    val diagnosticState by runtimeViewModel.diagnosticState.collectAsState()
    val diagnosticHistory by runtimeViewModel.diagnosticHistory.collectAsState()
    val profilesState by profilesViewModel.state.collectAsState()
    val editorState by profilesViewModel.editorState.collectAsState()
    val proxyGroupsState by proxyGroupsViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedProfile = profilesState.profiles.firstOrNull { it.id == profilesState.currentProfileId }
    val canMutateProfiles = !profilesState.isBusy && !runtimeState.isRunning
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showRequests by rememberSaveable { mutableStateOf(false) }
    var showDns by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticHistory by rememberSaveable { mutableStateOf(false) }
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

    val qrCodePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                val values = com.lonnnnnng.flclash.data.QrCodeImageImporter.decode(context, uri)
                values.asSequence()
                    .mapNotNull(com.lonnnnnng.flclash.data.DeepLinkProfileParser::parseProfileUrl)
                    .firstOrNull()
                    ?: error("二维码中没有可导入的配置链接")
            }.onSuccess { url ->
                profilesViewModel.addRemote(url, "")
            }.onFailure { error ->
                snackbarHostState.showSnackbar(error.message ?: "二维码解析失败")
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
                BatteryOptimizationCard(
                    ignored = batteryOptimizationIgnored,
                    onRequest = onRequestBatteryOptimization,
                )
            }
            item {
                ProbeCard(
                    state = runtimeProbe,
                    diagnostic = diagnosticState,
                    diagnosticHistory = diagnosticHistory,
                    runtimeState = runtimeState,
                    profile = selectedProfile,
                    onProbe = { selectedProfile?.url?.let(runtimeViewModel::probe) },
                    onExportDiagnostic = onExportDiagnostic,
                    onShowDiagnosticHistory = { showDiagnosticHistory = true },
                    onExportDiagnosticHistory = onExportDiagnosticHistory,
                )
            }
            item {
                ConnectionsCard(
                    state = runtimeConnections,
                    runtimeState = runtimeState,
                )
            }
            item {
                RequestsCard(
                    state = runtimeRequests,
                    runtimeState = runtimeState,
                    onShowAll = { showRequests = true },
                )
            }
            item {
                DnsCard(
                    state = runtimeDns,
                    runtimeState = runtimeState,
                    onShowAll = { showDns = true },
                    onClear = runtimeViewModel::clearDns,
                )
            }
            item {
                LogsCard(
                    state = runtimeLogs,
                    runtimeState = runtimeState,
                    onShowAll = { showLogs = true },
                    onClear = runtimeViewModel::clearLogs,
                    onExport = { onExportLogs(runtimeLogs.entries) },
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
                        canMutate = canMutateProfiles,
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
            onPickQrCode = {
                showAddDialog = false
                qrCodePicker.launch("image/*")
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
                    enabled = canMutateProfiles,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDeletion = null }) { Text("取消") }
            },
        )
    }

    if (showRequests) {
        RequestsDialog(
            state = runtimeRequests,
            onDismiss = { showRequests = false },
        )
    }

    if (showDns) {
        DnsDialog(
            state = runtimeDns,
            onDismiss = { showDns = false },
            onClear = runtimeViewModel::clearDns,
        )
    }

    if (showLogs) {
        LogsDialog(
            state = runtimeLogs,
            onDismiss = { showLogs = false },
            onClear = runtimeViewModel::clearLogs,
            onExport = { onExportLogs(runtimeLogs.entries) },
        )
    }

    if (showDiagnosticHistory) {
        RuntimeDiagnosticHistoryDialog(
            events = diagnosticHistory,
            onDismiss = { showDiagnosticHistory = false },
            onExport = { onExportDiagnosticHistory(diagnosticHistory) },
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
private fun BatteryOptimizationCard(
    ignored: Boolean,
    onRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("后台运行", style = MaterialTheme.typography.titleMedium)
            Text(
                if (ignored) {
                    "电池优化已忽略，系统不会主动限制 KClash 的后台 VPN 服务。"
                } else {
                    "建议关闭电池优化，避免系统在后台暂停 KClash 的 VPN 服务。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ignored) {
                OutlinedButton(onClick = onRequest) {
                    Text("打开电池优化设置")
                }
            }
        }
    }
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
private fun ProbeCard(
    state: ProbeUiState,
    diagnostic: DiagnosticSessionUiState,
    diagnosticHistory: List<RuntimeDiagnosticEvent>,
    runtimeState: CoreRuntimeStatus,
    profile: Profile?,
    onProbe: () -> Unit,
    onExportDiagnostic: (DiagnosticSessionUiState) -> Unit,
    onShowDiagnosticHistory: () -> Unit,
    onExportDiagnosticHistory: (List<RuntimeDiagnosticEvent>) -> Unit,
) {
    val hasRemoteTarget = profile?.type == ProfileType.URL && profile.url.isNotBlank()
    val canProbe = runtimeState.isRunning && hasRemoteTarget && !state.isLoading
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "真实请求诊断",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            when {
                !runtimeState.isRunning -> Text(
                    "启动核心后，可使用当前远程订阅地址发起一次可控请求。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                !hasRemoteTarget -> Text(
                    "请选择远程订阅配置；自定义配置没有授权诊断地址。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasRemoteTarget) {
                Text(
                    "目标：${profile.realLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onProbe, enabled = canProbe) {
                Text(if (state.isLoading) "诊断中…" else "测试当前路由")
            }
            when (diagnostic.status) {
                DiagnosticSessionStatus.RUNNING -> {
                    Text(
                        "诊断会话：采集中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DiagnosticEventFlowSummary(diagnostic.eventFlowStats)
                }

                DiagnosticSessionStatus.PASS,
                DiagnosticSessionStatus.FAIL -> {
                    val statusLabel = if (diagnostic.status == DiagnosticSessionStatus.PASS) "通过" else "失败"
                    Text(
                        "诊断会话：$statusLabel · 运行 ${diagnostic.runtimeEvents.size} · 请求 ${diagnostic.requests.size} · DNS ${diagnostic.dns.size} · 日志 ${diagnostic.logs.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (diagnostic.status == DiagnosticSessionStatus.PASS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    diagnostic.assertion?.let { assertion ->
                        Text(
                            assertion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DiagnosticFindingSummary(diagnostic)
                    DiagnosticEventFlowSummary(diagnostic.eventFlowStats)
                    TextButton(onClick = { onExportDiagnostic(diagnostic) }) {
                        Text("导出诊断证据")
                    }
                }

                DiagnosticSessionStatus.IDLE -> RuntimeDiagnosticHistorySummary(
                    events = diagnosticHistory,
                    onShowAll = onShowDiagnosticHistory,
                    onExport = { onExportDiagnosticHistory(diagnosticHistory) },
                )
            }
            if (diagnostic.status != DiagnosticSessionStatus.IDLE && diagnosticHistory.isNotEmpty()) {
                RuntimeDiagnosticHistoryActions(
                    events = diagnosticHistory,
                    onShowAll = onShowDiagnosticHistory,
                    onExport = { onExportDiagnosticHistory(diagnosticHistory) },
                )
            }
            state.error?.let { error ->
                Text(
                    "失败：$error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.result?.let { result ->
                val status = if (result.statusCode > 0) "HTTP ${result.statusCode}" else "未收到 HTTP 状态"
                val route = result.chains.joinToString(" → ").ifBlank { "未返回路由链" }
                Text(
                    "$status · ${result.delay} ms",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "路由：$route",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.rule.isNotBlank()) {
                    Text(
                        "规则：${result.rule}${result.rulePayload.takeIf { it.isNotBlank() }?.let { " / $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticEventFlowSummary(stats: CoreEventFlowStats) {
    // 作者 long：把事件管线的丢弃边界直接放在诊断结果旁，避免用户把不完整证据误认为真实网络没有事件。
    val hasLoss = diagnosticEventFlowHasLoss(stats)
    if (stats.parsedEvents <= 0L && !hasLoss) return
    Text(
        formatDiagnosticEventFlowStats(stats),
        style = MaterialTheme.typography.bodySmall,
        color = if (hasLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (hasLoss) {
        Text(
            "事件流存在丢弃或截断，诊断证据可能不完整。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun DiagnosticFindingSummary(session: DiagnosticSessionUiState) {
    val finding = diagnosticFinding(session)
    val isError = when (finding.code) {
        DiagnosticFindingCode.CORE_ERROR,
        DiagnosticFindingCode.NO_RESULT,
        DiagnosticFindingCode.NO_HTTP_STATUS,
        DiagnosticFindingCode.NO_ROUTE,
        DiagnosticFindingCode.EVENT_EVIDENCE_LOSS,
        -> true
        DiagnosticFindingCode.IDLE,
        DiagnosticFindingCode.RUNNING,
        DiagnosticFindingCode.HTTP_RESPONSE,
        -> false
    }
    var showRawDetail by rememberSaveable(session.sessionId, finding.rawDetail) {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "诊断归因：${finding.title}（${finding.code}）",
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            "下一步建议：${finding.suggestion}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        finding.rawDetail?.takeIf { it.isNotBlank() }?.let { rawDetail ->
            TextButton(onClick = { showRawDetail = !showRawDetail }) {
                Text(if (showRawDetail) "收起原始详情" else "查看原始详情")
            }
            if (showRawDetail) {
                Text(
                    "原始详情：${redactDiagnosticDetail(rawDetail)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RuntimeDiagnosticHistorySummary(
    events: List<RuntimeDiagnosticEvent>,
    onShowAll: () -> Unit,
    onExport: () -> Unit,
) {
    if (events.isEmpty()) {
        Text(
            "尚未记录运行诊断事件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        "恢复历史：${events.size} 条",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    RuntimeDiagnosticHistoryActions(events, onShowAll, onExport)
    runtimeDiagnosticHistoryRows(events).forEach { event ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                runtimeDiagnosticLabel(event.kind) +
                    if (event.kind == RuntimeDiagnosticKind.CORE_RESTARTED) {
                        "（CORE_RESTARTED）"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                event.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                DateUtils.getRelativeTimeSpanString(event.timestampMillis).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuntimeDiagnosticHistoryActions(
    events: List<RuntimeDiagnosticEvent>,
    onShowAll: () -> Unit,
    onExport: () -> Unit,
) {
    if (events.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onShowAll) { Text("查看全部") }
        TextButton(onClick = onExport) { Text("导出") }
    }
}

@Composable
private fun RuntimeDiagnosticHistoryDialog(
    events: List<RuntimeDiagnosticEvent>,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行诊断历史（${events.size}）") },
        text = {
            if (events.isEmpty()) {
                Text(
                    "尚未记录运行诊断事件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = events.asReversed(),
                        key = { "${it.timestampMillis}|${it.kind}|${it.detail}" },
                    ) { event ->
                        RuntimeDiagnosticHistoryRow(event)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onExport, enabled = events.isNotEmpty()) { Text("导出") }
        },
    )
}

@Composable
private fun RuntimeDiagnosticHistoryRow(event: RuntimeDiagnosticEvent) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            runtimeDiagnosticLabel(event.kind) + "（${event.kind}）",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            event.detail.ifBlank { "（无详细信息）" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            DateUtils.getRelativeTimeSpanString(event.timestampMillis).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun runtimeDiagnosticHistoryRows(events: List<RuntimeDiagnosticEvent>): List<RuntimeDiagnosticEvent> =
    events.takeLast(MAX_RUNTIME_HISTORY_ROWS).asReversed()

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

@Composable
private fun RequestsCard(
    state: RequestsUiState,
    runtimeState: CoreRuntimeStatus,
    onShowAll: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "请求记录${if (runtimeState.isRunning) "（${state.requests.size}）" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.requests.isNotEmpty()) {
                    TextButton(onClick = onShowAll) { Text("查看全部") }
                }
            }
            when {
                !runtimeState.isRunning -> Text(
                    "启动核心后显示请求、目标和规则命中信息。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.requests.isEmpty() -> Text(
                    "等待核心上报请求事件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.requests.takeLast(MAX_REQUESTS_ON_HOME).asReversed().forEach { request ->
                    ConnectionRow(request)
                }
            }
        }
    }
}

@Composable
private fun RequestsDialog(
    state: RequestsUiState,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = filterRequests(state.requests, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请求记录（${state.requests.size}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ObservationSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索目标、进程、代理链或规则",
                )
                Text(
                    "显示 ${filtered.size}/${state.requests.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ObservationList(
                    items = filtered.asReversed(),
                    filterKey = query,
                    emptyText = "没有匹配的请求记录。",
                    itemKey = { it.id },
                    row = ::ConnectionRow,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DnsCard(
    state: DnsUiState,
    runtimeState: CoreRuntimeStatus,
    onShowAll: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "DNS 查询${if (runtimeState.isRunning) "（${state.queries.size}）" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.queries.isNotEmpty()) {
                    TextButton(onClick = onShowAll) { Text("查看全部") }
                    TextButton(onClick = onClear) { Text("清空") }
                }
            }
            when {
                !runtimeState.isRunning -> Text(
                    "启动核心后显示 DNS 查询记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.queries.isEmpty() -> Text(
                    "等待核心上报 DNS 查询事件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.queries.takeLast(MAX_OBSERVATION_ROWS).asReversed().forEach { query ->
                    DnsQueryRow(query)
                }
            }
        }
    }
}

@Composable
private fun DnsQueryRow(query: CoreDnsQuery) {
    val domain = query.domain.ifBlank { "未知域名" }
    val details = listOf(query.type, query.rcode, query.upstream)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val result = when {
        query.error.isNotBlank() -> "失败：${query.error}"
        query.answers.isNotEmpty() -> query.answers.joinToString(", ")
        else -> "无结果"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(domain, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(details, result).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (query.error.isNotBlank()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (query.delay > 0L) {
            Text(
                "耗时 ${query.delay} ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DnsDialog(
    state: DnsUiState,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = filterDnsQueries(state.queries, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DNS 查询（${state.queries.size}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ObservationSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索域名、上游、答案或错误",
                )
                Text(
                    "显示 ${filtered.size}/${state.queries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ObservationList(
                    items = filtered.asReversed(),
                    filterKey = query,
                    emptyText = "没有匹配的 DNS 查询。",
                    itemKey = { "${it.time}|${it.domain}|${it.initiator}" },
                    row = ::DnsQueryRow,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onClear, enabled = state.queries.isNotEmpty()) { Text("清空") }
        },
    )
}

@Composable
private fun LogsCard(
    state: LogsUiState,
    runtimeState: CoreRuntimeStatus,
    onShowAll: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "核心日志${if (runtimeState.isRunning) "（${state.entries.size}）" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.entries.isNotEmpty()) {
                    TextButton(onClick = onShowAll) { Text("查看全部") }
                    TextButton(onClick = onClear) { Text("清空") }
                    TextButton(onClick = onExport) { Text("导出") }
                }
            }
            when {
                !runtimeState.isRunning -> Text(
                    "启动核心后显示 Mihomo 日志。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.entries.isEmpty() -> Text(
                    "等待核心上报日志事件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.entries.takeLast(MAX_OBSERVATION_ROWS).asReversed().forEach { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: CoreEvent.Log) {
    val level = entry.level.ifBlank { "info" }.uppercase()
    val color = if (entry.level.equals("error", ignoreCase = true)) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(level, style = MaterialTheme.typography.labelMedium, color = color)
        Text(
            entry.payload.ifBlank { "（空日志）" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            DateUtils.getRelativeTimeSpanString(entry.timestampMillis).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogsDialog(
    state: LogsUiState,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var levelFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = filterLogs(state.entries, query, levelFilter)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("核心日志（${state.entries.size}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ObservationSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索日志级别或内容",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "全部" to null,
                        "错误" to "error",
                        "警告" to "warn",
                    ).forEach { (label, value) ->
                        TextButton(onClick = { levelFilter = value }) {
                            Text(if (levelFilter == value) "✓ $label" else label)
                        }
                    }
                }
                Text(
                    "显示 ${filtered.size}/${state.entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ObservationList(
                    items = filtered.asReversed(),
                    filterKey = "$query|${levelFilter.orEmpty()}",
                    emptyText = "没有匹配的核心日志。",
                    itemKey = { "${it.timestampMillis}|${it.level}|${it.payload}" },
                    row = ::LogRow,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onClear, enabled = state.entries.isNotEmpty()) { Text("清空") }
                TextButton(onClick = onExport, enabled = state.entries.isNotEmpty()) { Text("导出") }
            }
        },
    )
}

@Composable
private fun ObservationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

/**
 * 作者 long：详情页默认跟随最新事件，但用户向上翻阅历史后必须保持当前位置，避免高频日志把阅读位置抢回顶部。
 */
@Composable
private fun <T> ObservationList(
    items: List<T>,
    filterKey: Any?,
    emptyText: String,
    itemKey: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    var followLatest by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { firstVisibleItem ->
                followLatest = firstVisibleItem <= 1
            }
    }
    LaunchedEffect(filterKey) {
        followLatest = true
        listState.scrollToItem(0)
    }
    val newestKey = items.firstOrNull()?.let(itemKey)
    LaunchedEffect(newestKey) {
        if (followLatest && items.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (items.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = itemKey) { item -> row(item) }
        }
    }
}

private const val MAX_CONNECTIONS_ON_HOME = 5
private const val MAX_REQUESTS_ON_HOME = 5
private const val MAX_OBSERVATION_ROWS = 5
private const val MAX_RUNTIME_HISTORY_ROWS = 3

@Composable
internal fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    canMutate: Boolean,
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
                    TextButton(onClick = onEdit, enabled = canMutate) { Text("编辑") }
                }
                if (profile.type == ProfileType.URL) {
                    TextButton(onClick = onUpdate, enabled = canMutate) { Text("同步") }
                }
                TextButton(onClick = onDelete, enabled = canMutate) { Text("删除") }
            }
        }
    }
}

@Composable
internal fun ProfileEditorDialog(
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
internal fun AddProfileDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onPickQrCode: () -> Unit,
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
                OutlinedButton(onClick = onPickQrCode, enabled = !busy) {
                    Text("从二维码图片导入")
                }
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
