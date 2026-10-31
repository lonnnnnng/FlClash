package com.lonnnnnng.flclash.ui

import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileProvidersEditor
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.data.BackupRestoreScope
import com.lonnnnnng.flclash.data.BackupRestoreStrategy
import com.lonnnnnng.flclash.data.WebDavCredentials
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticKind
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.common.AccessSortType
import com.lonnnnnng.flclash.AlwaysOnVpnState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll

private enum class MainDestination(
    val label: String,
    val symbol: String,
) {
    DASHBOARD("首页", "⌂"),
    PROFILES("配置", "▤"),
    PROXY("代理", "↔"),
    CONNECTIONS("连接", "⇄"),
    OBSERVATIONS("观测", "◌"),
}

private enum class ObservationTab(val label: String) {
    REQUESTS("请求"),
    DNS("DNS"),
    LOGS("日志"),
}

private enum class AccessMenuLayer {
    NONE,
    MAIN,
    SETTINGS,
    ACTIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KClashApp(
    runtimeViewModel: MainViewModel,
    profilesViewModel: ProfilesViewModel,
    accessControlViewModel: AccessControlViewModel,
    proxyGroupsViewModel: ProxyGroupsViewModel,
    scriptsViewModel: ScriptsViewModel,
    onStartRuntime: () -> Unit,
    onStopRuntime: () -> Unit,
    batteryOptimizationIgnored: Boolean,
    alwaysOnVpnState: AlwaysOnVpnState,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAlwaysOnVpn: () -> Unit,
    onExportLogs: (List<CoreEvent.Log>) -> Unit,
    onExportDiagnostic: (DiagnosticSessionUiState) -> Unit,
    onExportDiagnosticHistory: (List<RuntimeDiagnosticEvent>) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (BackupRestoreScope, BackupRestoreStrategy) -> Unit,
    webDavCredentials: WebDavCredentials? = null,
    webDavBusy: Boolean = false,
    onSaveWebDavCredentials: (WebDavCredentials) -> Unit = {},
    onClearWebDavCredentials: () -> Unit = {},
    onProbeWebDav: (WebDavCredentials) -> Unit = {},
    onUploadWebDav: (WebDavCredentials, BackupRestoreScope) -> Unit = { _, _ -> },
    onDownloadWebDav: (WebDavCredentials, BackupRestoreScope, BackupRestoreStrategy) -> Unit = { _, _, _ -> },
    onExitApp: () -> Unit = {},
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
    val rulesEditorState by profilesViewModel.rulesEditorState.collectAsState()
    val proxiesEditorState by profilesViewModel.proxiesEditorState.collectAsState()
    val proxyGroupsEditorState by profilesViewModel.proxyGroupsEditorState.collectAsState()
    val providersEditorState by profilesViewModel.providersEditorState.collectAsState()
    val dnsEditorState by profilesViewModel.dnsEditorState.collectAsState()
    val dialersEditorState by profilesViewModel.dialersEditorState.collectAsState()
    val proxyGroupsState by proxyGroupsViewModel.state.collectAsState()
    val scriptsState by scriptsViewModel.state.collectAsState()
    val scriptEditorState by scriptsViewModel.editorState.collectAsState()
    val accessControlState by accessControlViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedProfile = profilesState.profiles.firstOrNull { it.id == profilesState.currentProfileId }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showRequests by rememberSaveable { mutableStateOf(false) }
    var showDns by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticHistory by rememberSaveable { mutableStateOf(false) }
    var showScriptsDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoteScriptDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDestinationIndex by rememberSaveable { mutableStateOf(MainDestination.DASHBOARD.ordinal) }
    var showAccessUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    var pendingAccessExitDestination by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingAccessExitFinishes by rememberSaveable { mutableStateOf(false) }
    // 作者 long：观测页会随顶层导航离开组合，页签状态必须提升到主页面保存，避免用户返回观测时丢失当前页签。
    var selectedObservationTabIndex by rememberSaveable { mutableStateOf(ObservationTab.REQUESTS.ordinal) }
    val selectedDestination = MainDestination.values()[selectedDestinationIndex]

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

    LaunchedEffect(scriptsViewModel) {
        scriptsViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(accessControlViewModel) {
        accessControlViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun requestAccessExit(destination: MainDestination? = null) {
        pendingAccessExitDestination = destination?.ordinal
        pendingAccessExitFinishes = destination == null
        showAccessUnsavedChanges = true
    }

    fun completeAccessExit() {
        val destination = pendingAccessExitDestination
        val finishes = pendingAccessExitFinishes
        pendingAccessExitDestination = null
        pendingAccessExitFinishes = false
        showAccessUnsavedChanges = false
        if (destination != null) selectedDestinationIndex = destination
        if (finishes) onExitApp()
    }

    fun cancelAccessExit() {
        pendingAccessExitDestination = null
        pendingAccessExitFinishes = false
        showAccessUnsavedChanges = false
    }

    AccessControlUnsavedChangesDialog(
        visible = showAccessUnsavedChanges,
        isSaving = accessControlState.isSaving,
        onCancel = ::cancelAccessExit,
        onDiscard = {
            accessControlViewModel.discardUnsavedChanges()
            completeAccessExit()
        },
        onSaveAndExit = {
            accessControlViewModel.save {
                completeAccessExit()
            }
        },
    )

    BackHandler(enabled = selectedDestination == MainDestination.DASHBOARD && accessControlState.hasUnsavedChanges) {
        requestAccessExit()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedDestination == MainDestination.DASHBOARD) "KClash" else selectedDestination.label)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainNavigationBar(
                selectedDestination = selectedDestination,
                onDestinationSelected = { destination ->
                    if (
                        destination != selectedDestination &&
                        selectedDestination == MainDestination.DASHBOARD &&
                        accessControlState.hasUnsavedChanges
                    ) {
                        requestAccessExit(destination)
                    } else {
                        selectedDestinationIndex = destination.ordinal
                        if (destination != MainDestination.PROFILES) {
                            // 作者 long：离开配置页时关闭添加弹窗，避免弹窗覆盖首页并让返回后的页面状态不明确。
                            showAddDialog = false
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("添加配置") },
                icon = {},
                onClick = {
                    selectedDestinationIndex = MainDestination.PROFILES.ordinal
                    showAddDialog = true
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .testTag("page_${selectedDestination.name.lowercase()}"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedDestination) {
                MainDestination.DASHBOARD -> {
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
                        AlwaysOnVpnCard(
                            state = alwaysOnVpnState,
                            onRequest = onRequestAlwaysOnVpn,
                        )
                    }
                    item {
                        AccessControlCard(
                            state = accessControlState,
                            runtimeState = runtimeState,
                            onEnabledChange = accessControlViewModel::setEnabled,
                            onModeChange = accessControlViewModel::setMode,
                            onSortChange = accessControlViewModel::setSort,
                            onFilterSystemAppsChange = accessControlViewModel::setFilterSystemApps,
                            onFilterNonInternetAppsChange = accessControlViewModel::setFilterNonInternetApps,
                            onSearchChange = accessControlViewModel::setSearchQuery,
                            onTogglePackage = accessControlViewModel::togglePackage,
                            onSelectVisible = accessControlViewModel::selectVisibleApps,
                            onClearVisible = accessControlViewModel::clearVisibleApps,
                            onToggleVisible = accessControlViewModel::toggleVisibleApps,
                            onSmartSelect = accessControlViewModel::smartSelectApps,
                            onExportToClipboard = accessControlViewModel::exportToClipboard,
                            onImportFromClipboard = accessControlViewModel::importFromClipboard,
                            onSave = { accessControlViewModel.save() },
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
                }

                MainDestination.PROFILES -> item {
                    ProfilesPanel(
                        state = profilesState,
                        editorState = editorState,
                        runtimeState = runtimeState,
                        showAddDialog = showAddDialog,
                        onShowAddDialog = { showAddDialog = true },
                        onDismissAddDialog = { showAddDialog = false },
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
                        onSelect = profilesViewModel::select,
                        onEdit = profilesViewModel::openEditor,
                        onRename = profilesViewModel::rename,
                        onUpdate = profilesViewModel::update,
                        onSetAutoUpdate = profilesViewModel::setAutoUpdate,
                        onCloseEditor = profilesViewModel::closeEditor,
                        onSaveEditor = profilesViewModel::saveEditor,
                        onDelete = profilesViewModel::delete,
                        rulesEditorState = rulesEditorState,
                        onOpenRulesEditor = profilesViewModel::openRulesEditor,
                        onCloseRulesEditor = profilesViewModel::closeRulesEditor,
                        onSaveRules = profilesViewModel::saveRules,
                        proxiesEditorState = proxiesEditorState,
                        onOpenProxiesEditor = profilesViewModel::openProxiesEditor,
                        onCloseProxiesEditor = profilesViewModel::closeProxiesEditor,
                        onSaveProxies = profilesViewModel::saveProxies,
                        proxyGroupsEditorState = proxyGroupsEditorState,
                        onOpenProxyGroupsEditor = profilesViewModel::openProxyGroupsEditor,
                        onCloseProxyGroupsEditor = profilesViewModel::closeProxyGroupsEditor,
                        onSaveProxyGroups = profilesViewModel::saveProxyGroups,
                        providersEditorState = providersEditorState,
                        onOpenProvidersEditor = profilesViewModel::openProvidersEditor,
                        onCloseProvidersEditor = profilesViewModel::closeProvidersEditor,
                        onSaveProviders = profilesViewModel::saveProviders,
                        onSaveProvidersWithOverrides = profilesViewModel::saveProviders,
                        dnsEditorState = dnsEditorState,
                        onOpenDnsEditor = profilesViewModel::openDnsEditor,
                        onCloseDnsEditor = profilesViewModel::closeDnsEditor,
                        onSaveDns = profilesViewModel::saveDns,
                        dialersEditorState = dialersEditorState,
                        onOpenDialersEditor = profilesViewModel::openDialersEditor,
                        onCloseDialersEditor = profilesViewModel::closeDialersEditor,
                        onSaveDialers = profilesViewModel::saveDialers,
                        onReorderProfiles = profilesViewModel::reorder,
                        scripts = scriptsState.scripts,
                        onBindScript = profilesViewModel::bindScript,
                        onOpenScripts = { showScriptsDialog = true },
                        onExportBackup = onExportBackup,
                        onImportBackup = onImportBackup,
                        webDavCredentials = webDavCredentials,
                        webDavBusy = webDavBusy,
                        onSaveWebDavCredentials = onSaveWebDavCredentials,
                        onClearWebDavCredentials = onClearWebDavCredentials,
                        onProbeWebDav = onProbeWebDav,
                        onUploadWebDav = onUploadWebDav,
                        onDownloadWebDav = onDownloadWebDav,
                    )
                }

                MainDestination.PROXY -> item {
                    ProxyGroupsCard(
                        state = proxyGroupsState,
                        runtimeState = runtimeState,
                        onRefresh = proxyGroupsViewModel::refresh,
                        onSelect = proxyGroupsViewModel::selectProxy,
                        onTestDelay = proxyGroupsViewModel::testDelay,
                        onTestGroupDelay = proxyGroupsViewModel::testDelayGroup,
                    )
                }

                MainDestination.CONNECTIONS -> item {
                    ConnectionsCard(
                        state = runtimeConnections,
                        runtimeState = runtimeState,
                        onCopyText = { value ->
                            checkNotNull(context.getSystemService(ClipboardManager::class.java))
                                .setPrimaryClip(ClipData.newPlainText("KClash 连接详情", value))
                        },
                    )
                }

                MainDestination.OBSERVATIONS -> item {
                    ObservationPage(
                        requests = runtimeRequests,
                        dns = runtimeDns,
                        logs = runtimeLogs,
                        runtimeState = runtimeState,
                        selectedTabIndex = selectedObservationTabIndex,
                        onSelectedTabChange = { selectedObservationTabIndex = it },
                        onShowRequests = { showRequests = true },
                        onShowDns = { showDns = true },
                        onClearDns = runtimeViewModel::clearDns,
                        onShowLogs = { showLogs = true },
                        onClearLogs = runtimeViewModel::clearLogs,
                        onExportLogs = { onExportLogs(runtimeLogs.entries) },
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
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

    if (showScriptsDialog) {
        ScriptsLibraryDialog(
            state = scriptsState,
            onDismiss = { showScriptsDialog = false },
            onCreate = {
                showScriptsDialog = false
                scriptsViewModel.openNewEditor()
            },
            onEdit = { script ->
                showScriptsDialog = false
                scriptsViewModel.openEditor(script)
            },
            onDelete = scriptsViewModel::delete,
            onReorder = scriptsViewModel::reorder,
            onImportRemote = {
                showScriptsDialog = false
                showRemoteScriptDialog = true
            },
            onSync = scriptsViewModel::sync,
        )
    }

    if (showRemoteScriptDialog) {
        ScriptRemoteDialog(
            busy = scriptsState.isBusy,
            onDismiss = { if (!scriptsState.isBusy) showRemoteScriptDialog = false },
            onImport = { label, url ->
                showRemoteScriptDialog = false
                scriptsViewModel.importRemote(label, url)
            },
        )
    }

    scriptEditorState.let { editor ->
        if (editor.isOpen) {
            if (editor.isLoading) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("读取脚本") },
                    text = { CircularProgressIndicator() },
                    confirmButton = {},
                )
            } else {
                ScriptEditorDialog(
                    state = editor,
                    onDismiss = scriptsViewModel::closeEditor,
                    onSave = scriptsViewModel::save,
                )
            }
        }
    }

}

@Composable
internal fun AccessControlUnsavedChangesDialog(
    visible: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onSaveAndExit: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("按应用代理有未保存的修改") },
        text = { Text("保存后才会应用新的白名单、黑名单和筛选设置。") },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier.testTag("access_control_discard_changes"),
                ) { Text("放弃更改") }
                Button(
                    onClick = onSaveAndExit,
                    enabled = !isSaving,
                    modifier = Modifier.testTag("access_control_save_and_exit"),
                ) { Text("保存并退出") }
            }
        },
    )
}

/**
 * 作者 long：先把首页和配置页建立成稳定的手机入口，后续新增代理组、请求和日志独立页面时只扩展目的地，
 * 不让每个页面自行创建核心或观测状态。
 */
@Composable
private fun MainNavigationBar(
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
) {
    NavigationBar {
        MainDestination.values().forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = { Text(destination.symbol) },
                label = { Text(destination.label) },
            )
        }
    }
}

/**
 * 作者 long：Requests、DNS 和日志都属于运行观测，但各自有独立的筛选和空态；用页签分组可以在手机上保留
 * 清晰入口，避免把七个目的地同时压缩到底部导航栏，同时继续复用同一份 StateFlow 观测数据。
 */
@Composable
internal fun ObservationPage(
    requests: RequestsUiState,
    dns: DnsUiState,
    logs: LogsUiState,
    runtimeState: CoreRuntimeStatus,
    selectedTabIndex: Int,
    onSelectedTabChange: (Int) -> Unit,
    onShowRequests: () -> Unit,
    onShowDns: () -> Unit,
    onClearDns: () -> Unit,
    onShowLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit,
) {
    val safeSelectedTabIndex = selectedTabIndex.coerceIn(0, ObservationTab.values().lastIndex)
    val selectedTab = ObservationTab.values()[safeSelectedTabIndex]

    Column(modifier = Modifier.testTag("observation_page")) {
        PrimaryTabRow(selectedTabIndex = safeSelectedTabIndex) {
            ObservationTab.values().forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectedTabChange(tab.ordinal) },
                    text = { Text(tab.label) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("page_${selectedTab.name.lowercase()}"),
        ) {
            when (selectedTab) {
                ObservationTab.REQUESTS -> RequestsCard(
                    state = requests,
                    runtimeState = runtimeState,
                    onShowAll = onShowRequests,
                )

                ObservationTab.DNS -> DnsCard(
                    state = dns,
                    runtimeState = runtimeState,
                    onShowAll = onShowDns,
                    onClear = onClearDns,
                )

                ObservationTab.LOGS -> LogsCard(
                    state = logs,
                    runtimeState = runtimeState,
                    onShowAll = onShowLogs,
                    onClear = onClearLogs,
                    onExport = onExportLogs,
                )
            }
        }
    }
}

/**
 * 作者 long：把 Profiles 的状态、确认弹窗和编辑弹窗收口到独立宿主，
 * 让页面可以用替换的数据源验证创建、编辑和删除之间的组合状态，而不启动真实核心。
 */
@Composable
internal fun ProfilesPanel(
    state: ProfilesUiState,
    editorState: ProfileEditorUiState,
    runtimeState: CoreRuntimeStatus,
    showAddDialog: Boolean,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onPickFile: () -> Unit,
    onPickQrCode: () -> Unit,
    onAddRemote: (label: String, url: String) -> Unit,
    onAddCustom: (label: String) -> Unit,
    onSelect: (id: Long) -> Unit,
    onEdit: (profile: Profile) -> Unit,
    onRename: (profile: Profile, label: String) -> Unit = { _, _ -> },
    onUpdate: (profile: Profile) -> Unit,
    onSetAutoUpdate: (profile: Profile, enabled: Boolean) -> Unit = { _, _ -> },
    onCloseEditor: () -> Unit,
    onSaveEditor: (content: String) -> Unit,
    onDelete: (profile: Profile) -> Unit,
    rulesEditorState: ProfileRulesEditorUiState = ProfileRulesEditorUiState(),
    onOpenRulesEditor: (profile: Profile) -> Unit = {},
    onCloseRulesEditor: () -> Unit = {},
    onSaveRules: (rules: String) -> Unit = {},
    proxiesEditorState: ProfileProxiesEditorUiState = ProfileProxiesEditorUiState(),
    onOpenProxiesEditor: (profile: Profile) -> Unit = {},
    onCloseProxiesEditor: () -> Unit = {},
    onSaveProxies: (proxies: String) -> Unit = {},
    proxyGroupsEditorState: ProfileProxyGroupsEditorUiState = ProfileProxyGroupsEditorUiState(),
    onOpenProxyGroupsEditor: (profile: Profile) -> Unit = {},
    onCloseProxyGroupsEditor: () -> Unit = {},
    onSaveProxyGroups: (groups: String) -> Unit = {},
    providersEditorState: ProfileProvidersEditorUiState = ProfileProvidersEditorUiState(),
    onOpenProvidersEditor: (profile: Profile) -> Unit = {},
    onCloseProvidersEditor: () -> Unit = {},
    onSaveProviders: (proxyProviders: String, ruleProviders: String) -> Unit = { _, _ -> },
    onSaveProvidersWithOverrides: ((String, String, Map<String, ProxyProviderOverride>) -> Unit)? = null,
    dnsEditorState: ProfileDnsEditorUiState = ProfileDnsEditorUiState(),
    onOpenDnsEditor: (profile: Profile) -> Unit = {},
    onCloseDnsEditor: () -> Unit = {},
    onSaveDns: (ProfileDnsOverrides) -> Unit = {},
    dialersEditorState: ProfileDialersEditorUiState = ProfileDialersEditorUiState(),
    onOpenDialersEditor: (Profile) -> Unit = {},
    onCloseDialersEditor: () -> Unit = {},
    onSaveDialers: (String) -> Unit = {},
    onReorderProfiles: (List<Long>) -> Unit = {},
    scripts: List<Script> = emptyList(),
    onBindScript: (Profile, Long?) -> Unit = { _, _ -> },
    onOpenScripts: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: (BackupRestoreScope, BackupRestoreStrategy) -> Unit = { _, _ -> },
    webDavCredentials: WebDavCredentials? = null,
    webDavBusy: Boolean = false,
    onSaveWebDavCredentials: (WebDavCredentials) -> Unit = {},
    onClearWebDavCredentials: () -> Unit = {},
    onProbeWebDav: (WebDavCredentials) -> Unit = {},
    onUploadWebDav: (WebDavCredentials, BackupRestoreScope) -> Unit = { _, _ -> },
    onDownloadWebDav: (WebDavCredentials, BackupRestoreScope, BackupRestoreStrategy) -> Unit = { _, _, _ -> },
) {
    val canMutateProfiles = !state.isBusy && !runtimeState.isRunning
    var profilePendingDeletion by remember { mutableStateOf<Profile?>(null) }
    var profilePendingRename by remember { mutableStateOf<Profile?>(null) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var scriptBindingProfileId by remember { mutableStateOf<Long?>(null) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var showWebDavRestoreDialog by remember { mutableStateOf(false) }
    var pendingWebDavCredentials by remember { mutableStateOf<WebDavCredentials?>(null) }

    Column(
        modifier = Modifier.testTag("profiles_page"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "配置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .weight(1f),
            )
            if (state.profiles.isNotEmpty()) {
                TextButton(
                    onClick = { showReorderDialog = true },
                    enabled = canMutateProfiles,
                ) { Text("排序") }
            }
            TextButton(onClick = onOpenScripts, enabled = !state.isBusy) { Text("脚本") }
            TextButton(
                onClick = { showWebDavDialog = true },
                enabled = !state.isBusy,
                modifier = Modifier.testTag("webdav_open"),
            ) { Text("WebDAV") }
            TextButton(
                onClick = onExportBackup,
                enabled = !state.isBusy,
                modifier = Modifier.testTag("backup_export"),
            ) { Text("备份") }
            TextButton(
                onClick = { showBackupRestoreDialog = true },
                enabled = canMutateProfiles,
                modifier = Modifier.testTag("backup_restore"),
            ) { Text("恢复") }
        }
        if (state.profiles.isEmpty()) {
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
                    Button(onClick = onShowAddDialog) { Text("添加配置") }
                }
            }
        } else {
            state.profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    selected = profile.id == state.currentProfileId,
                    canMutate = canMutateProfiles,
                    onSelect = { onSelect(profile.id) },
                    onEdit = { onEdit(profile) },
                    onRename = { profilePendingRename = profile },
                    onUpdate = { onUpdate(profile) },
                    onSetAutoUpdate = { enabled -> onSetAutoUpdate(profile, enabled) },
                    onDelete = { profilePendingDeletion = profile },
                    onOpenRulesEditor = { onOpenRulesEditor(profile) },
                    onOpenProxiesEditor = { onOpenProxiesEditor(profile) },
                    onOpenProxyGroupsEditor = { onOpenProxyGroupsEditor(profile) },
                    onOpenProvidersEditor = { onOpenProvidersEditor(profile) },
                    onOpenDnsEditor = { onOpenDnsEditor(profile) },
                    onOpenDialersEditor = { onOpenDialersEditor(profile) },
                    onOpenScriptBinding = { scriptBindingProfileId = profile.id },
                )
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            busy = state.isBusy,
            onDismiss = onDismissAddDialog,
            onPickFile = onPickFile,
            onPickQrCode = onPickQrCode,
            onAddRemote = onAddRemote,
            onAddCustom = onAddCustom,
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
                        onDelete(profile)
                    },
                    enabled = canMutateProfiles,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDeletion = null }) { Text("取消") }
            },
        )
    }

    profilePendingRename?.let { profile ->
        ProfileRenameDialog(
            profile = profile,
            saving = state.isBusy,
            onDismiss = { if (!state.isBusy) profilePendingRename = null },
            onSave = { label ->
                profilePendingRename = null
                onRename(profile, label)
            },
        )
    }

    if (showReorderDialog) {
        ProfileReorderDialog(
            profiles = state.profiles,
            saving = state.isBusy,
            onDismiss = { if (!state.isBusy) showReorderDialog = false },
            onSave = { ids ->
                showReorderDialog = false
                onReorderProfiles(ids)
            },
        )
    }

    if (showBackupRestoreDialog) {
        BackupRestoreOptionsDialog(
            onDismiss = { showBackupRestoreDialog = false },
            onConfirm = { scope, strategy ->
                showBackupRestoreDialog = false
                onImportBackup(scope, strategy)
            },
        )
    }

    if (showWebDavDialog) {
        WebDavBackupDialog(
            initial = webDavCredentials,
            busy = webDavBusy,
            onDismiss = { if (!webDavBusy) showWebDavDialog = false },
            onSave = onSaveWebDavCredentials,
            onClear = {
                onClearWebDavCredentials()
                showWebDavDialog = false
            },
            onProbe = onProbeWebDav,
            onUpload = onUploadWebDav,
            onDownload = { credentials ->
                pendingWebDavCredentials = credentials
                showWebDavDialog = false
                showWebDavRestoreDialog = true
            },
        )
    }

    if (showWebDavRestoreDialog) {
        BackupRestoreOptionsDialog(
            title = "从 WebDAV 恢复",
            confirmLabel = "下载并恢复",
            onDismiss = {
                showWebDavRestoreDialog = false
                pendingWebDavCredentials = null
            },
            onConfirm = { scope, strategy ->
                val credentials = pendingWebDavCredentials
                showWebDavRestoreDialog = false
                pendingWebDavCredentials = null
                if (credentials != null) onDownloadWebDav(credentials, scope, strategy)
            },
        )
    }

    scriptBindingProfileId?.let { profileId ->
        state.profiles.firstOrNull { it.id == profileId }?.let { profile ->
            ProfileScriptBindingDialog(
                profile = profile,
                scripts = scripts,
                saving = state.isBusy,
                onDismiss = { if (!state.isBusy) scriptBindingProfileId = null },
                onSelect = { scriptId -> onBindScript(profile, scriptId) },
                onOpenScripts = {
                    scriptBindingProfileId = null
                    onOpenScripts()
                },
            )
        }
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
                onDismiss = onCloseEditor,
                onSave = onSaveEditor,
            )
        }
    }

    rulesEditorState.profile?.let { profile ->
        if (rulesEditorState.isLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("读取规则") },
                text = { CircularProgressIndicator() },
                confirmButton = {},
            )
        } else {
            ProfileRulesEditorDialog(
                profile = profile,
                initialRules = rulesEditorState.rules,
                saving = rulesEditorState.isSaving,
                onDismiss = onCloseRulesEditor,
                onSave = onSaveRules,
            )
        }
    }

    proxiesEditorState.profile?.let { profile ->
        if (proxiesEditorState.isLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("读取代理") },
                text = { CircularProgressIndicator() },
                confirmButton = {},
            )
        } else {
            ProfileProxiesEditorDialog(
                profile = profile,
                initialProxies = proxiesEditorState.proxies,
                saving = proxiesEditorState.isSaving,
                onDismiss = onCloseProxiesEditor,
                onSave = onSaveProxies,
            )
        }
    }

    proxyGroupsEditorState.profile?.let { profile ->
        if (proxyGroupsEditorState.isLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("读取代理组") },
                text = { CircularProgressIndicator() },
                confirmButton = {},
            )
        } else {
            ProfileProxyGroupsEditorDialog(
                profile = profile,
                initialGroups = proxyGroupsEditorState.groups,
                saving = proxyGroupsEditorState.isSaving,
                onDismiss = onCloseProxyGroupsEditor,
                onSave = onSaveProxyGroups,
            )
        }
    }

    providersEditorState.profile?.let { profile ->
        if (providersEditorState.isLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("读取 Provider") },
                text = { CircularProgressIndicator() },
                confirmButton = {},
            )
        } else {
            ProfileProvidersEditorDialog(
                profile = profile,
                initialProxyProviders = providersEditorState.proxyProviders,
                initialRuleProviders = providersEditorState.ruleProviders,
                initialProxyProviderOverrides = providersEditorState.proxyProviderOverrides,
                saving = providersEditorState.isSaving,
                onDismiss = onCloseProvidersEditor,
                onSave = onSaveProviders,
                onSaveWithOverrides = onSaveProvidersWithOverrides,
            )
        }
    }

    dnsEditorState.profile?.let { profile ->
        ProfileDnsOverrideDialog(
            profile = profile,
            initial = dnsEditorState.overrides,
            saving = dnsEditorState.isSaving,
            onDismiss = onCloseDnsEditor,
            onSave = onSaveDns,
        )
    }

    dialersEditorState.profile?.let { profile ->
        if (dialersEditorState.isLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("读取 Dialers") },
                text = { CircularProgressIndicator() },
                confirmButton = {},
            )
        } else {
            ProfileDialersEditorDialog(
                profile = profile,
                initialDialers = dialersEditorState.dialers,
                saving = dialersEditorState.isSaving,
                onDismiss = onCloseDialersEditor,
                onSave = onSaveDialers,
            )
        }
    }
}

@Composable
internal fun BackupRestoreOptionsDialog(
    title: String = "恢复备份",
    confirmLabel: String = "选择备份文件",
    onDismiss: () -> Unit,
    onConfirm: (BackupRestoreScope, BackupRestoreStrategy) -> Unit,
) {
    var scope by remember { mutableStateOf(BackupRestoreScope.ALL_DATA) }
    var strategy by remember { mutableStateOf(BackupRestoreStrategy.COMPATIBLE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("恢复范围", style = MaterialTheme.typography.titleSmall)
                BackupOptionRow(
                    selected = scope == BackupRestoreScope.PROFILES_ONLY,
                    label = "仅恢复配置文件",
                    onClick = { scope = BackupRestoreScope.PROFILES_ONLY },
                )
                BackupOptionRow(
                    selected = scope == BackupRestoreScope.ALL_DATA,
                    label = "恢复全部数据",
                    onClick = { scope = BackupRestoreScope.ALL_DATA },
                )
                Spacer(Modifier.height(8.dp))
                Text("已有数据", style = MaterialTheme.typography.titleSmall)
                BackupOptionRow(
                    selected = strategy == BackupRestoreStrategy.COMPATIBLE,
                    label = "兼容：保留未包含的数据",
                    onClick = { strategy = BackupRestoreStrategy.COMPATIBLE },
                )
                BackupOptionRow(
                    selected = strategy == BackupRestoreStrategy.OVERRIDE,
                    label = "覆盖：替换当前数据",
                    onClick = { strategy = BackupRestoreStrategy.OVERRIDE },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(scope, strategy) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BackupOptionRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_option_$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        TextButton(onClick = onClick) { Text(label) }
    }
}

/**
 * 作者 long：WebDAV 配置把手动覆盖、下载恢复和本地凭据保存放在同一个明确入口，
 * 用户可以先测试连接再上传；下载恢复仍需选择兼容或覆盖策略，不自动改写本地数据。
 */
@Composable
internal fun WebDavBackupDialog(
    initial: WebDavCredentials?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (WebDavCredentials) -> Unit,
    onClear: () -> Unit,
    onProbe: (WebDavCredentials) -> Unit,
    onUpload: (WebDavCredentials, BackupRestoreScope) -> Unit,
    onDownload: (WebDavCredentials) -> Unit,
) {
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var username by remember(initial) { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember(initial) { mutableStateOf(initial?.password.orEmpty()) }
    var fileName by remember(initial) {
        mutableStateOf(initial?.fileName ?: com.lonnnnnng.flclash.data.DEFAULT_WEBDAV_FILE_NAME)
    }
    var uploadScope by remember { mutableStateOf(BackupRestoreScope.ALL_DATA) }
    var confirmUpload by remember { mutableStateOf(false) }

    fun currentCredentials() = WebDavCredentials(
        baseUrl = baseUrl.trim(),
        username = username.trim(),
        password = password,
        fileName = fileName.trim(),
    )

    val hasRequiredInput = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("WebDAV 备份") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "凭据使用 Android Keystore 加密保存。上传会覆盖远程同名文件，下载后仍需确认恢复策略。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webdav_base_url"),
                    label = { Text("WebDAV 地址") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webdav_username"),
                    label = { Text("账号") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webdav_password"),
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webdav_file_name"),
                    label = { Text("远程文件名") },
                    singleLine = true,
                )
                Text("上传范围", style = MaterialTheme.typography.titleSmall)
                BackupOptionRow(
                    selected = uploadScope == BackupRestoreScope.PROFILES_ONLY,
                    label = "仅配置文件",
                    onClick = { if (!busy) uploadScope = BackupRestoreScope.PROFILES_ONLY },
                )
                BackupOptionRow(
                    selected = uploadScope == BackupRestoreScope.ALL_DATA,
                    label = "全部数据",
                    onClick = { if (!busy) uploadScope = BackupRestoreScope.ALL_DATA },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(
                        onClick = { onProbe(currentCredentials()) },
                        enabled = !busy && hasRequiredInput,
                        modifier = Modifier.testTag("webdav_probe"),
                    ) { Text("测试连接") }
                    OutlinedButton(
                        onClick = { confirmUpload = true },
                        enabled = !busy && hasRequiredInput,
                        modifier = Modifier.testTag("webdav_upload"),
                    ) { Text("上传") }
                    OutlinedButton(
                        onClick = { onDownload(currentCredentials()) },
                        enabled = !busy && hasRequiredInput,
                        modifier = Modifier.testTag("webdav_download"),
                    ) { Text("下载恢复") }
                }
                if (initial != null) {
                    TextButton(
                        onClick = onClear,
                        enabled = !busy,
                        modifier = Modifier.testTag("webdav_clear"),
                    ) { Text("清除已保存凭据") }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("正在处理 WebDAV 请求…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(currentCredentials()) },
                enabled = !busy && hasRequiredInput,
                modifier = Modifier.testTag("webdav_save"),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("webdav_cancel"),
            ) { Text("取消") }
        },
    )

    if (confirmUpload) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmUpload = false },
            title = { Text("覆盖远程备份？") },
            text = { Text("上传会替换远程文件“${fileName.trim()}”，不会与远程内容合并。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUpload = false
                        onUpload(currentCredentials(), uploadScope)
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("webdav_upload_confirm"),
                ) { Text("确认覆盖") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmUpload = false },
                    enabled = !busy,
                ) { Text("取消") }
            },
        )
    }

}

@Composable
internal fun ProxyGroupsCard(
    state: ProxyGroupsUiState,
    runtimeState: CoreRuntimeStatus,
    onRefresh: () -> Unit,
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestGroupDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo) -> Unit,
) {
    var groupPendingSelection by remember { mutableStateOf<com.lonnnnnng.flclash.core.ProxyGroupInfo?>(null) }
    var proxyQuery by remember(groupPendingSelection?.name) { mutableStateOf("") }
    val canRefresh = runtimeState == CoreRuntimeStatus.RUNNING &&
        !state.isLoading &&
        state.switchingGroup == null &&
        state.batchTestingGroup == null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("proxy_groups_card"),
    ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_group_${group.name}"),
                        onClick = { if (group.isSelectable && group.all.isNotEmpty()) groupPendingSelection = group },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                                    // 作者 long：Flutter 在嵌套代理组卡片中展示最终叶子节点，避免用户只能看到中间组名。
                                    val displayedProxy = resolveSelectedProxy(
                                        group = group,
                                        groups = state.groups,
                                        selectedMap = state.selectedMap,
                                    ).leafName ?: selectedProxyName(group, state.selectedMap)
                                    Text(
                                        "${group.type} · 当前：${displayedProxy ?: "未选择"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (group.isSelectable) {
                                    TextButton(
                                        onClick = { groupPendingSelection = group },
                                        enabled = state.switchingGroup == null && state.batchTestingGroup == null,
                                        modifier = Modifier.testTag("proxy_group_select_${group.name}"),
                                    ) { Text(if (group.isComputed) "选择" else "切换") }
                                }
                                TextButton(
                                    onClick = { onTestGroupDelay(group) },
                                    enabled = runtimeState == CoreRuntimeStatus.RUNNING &&
                                        state.switchingGroup == null &&
                                        state.batchTestingGroup == null &&
                                        group.all.isNotEmpty(),
                                    modifier = Modifier.testTag("proxy_group_batch_${group.name}"),
                                ) {
                                    if (state.batchTestingGroup == group.name) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text("批量测速")
                                    }
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
            title = { Text("${if (group.isComputed) "选择" else "切换"} ${group.name}") },
            text = {
                Column(
                    modifier = Modifier.testTag("proxy_group_dialog_content_${group.name}"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (group.isComputed) {
                        Text(
                            "点击当前节点可恢复自动选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = proxyQuery,
                        onValueChange = { proxyQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_group_search"),
                        label = { Text("搜索节点") },
                        singleLine = true,
                    )
                    val visibleProxies = filterProxyMembers(group, proxyQuery)
                    if (visibleProxies.isEmpty()) {
                        Text(
                            "没有匹配的节点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    visibleProxies.forEach { proxyName ->
                        val delay = state.delayState(group, proxyName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = proxyName == selectedProxyName(group, state.selectedMap),
                                onClick = {
                                    onSelect(group, proxyName)
                                    groupPendingSelection = null
                                },
                                enabled = state.switchingGroup == null && state.batchTestingGroup == null,
                                modifier = Modifier.testTag("proxy_node_${group.name}_$proxyName"),
                            )
                            Text(
                                proxyName,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { onTestDelay(group, proxyName) },
                                enabled = state.switchingGroup == null &&
                                    state.batchTestingGroup == null &&
                                    delay?.isTesting != true,
                                modifier = Modifier.testTag("proxy_node_delay_${group.name}_$proxyName"),
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
                    enabled = state.switchingGroup == null && state.batchTestingGroup == null,
                    modifier = Modifier.testTag("proxy_group_close_${group.name}"),
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
internal fun BatteryOptimizationCard(
    ignored: Boolean,
    onRequest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_battery_card"),
    ) {
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
internal fun AlwaysOnVpnCard(
    state: AlwaysOnVpnState,
    onRequest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("always_on_vpn_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("系统 Always-on VPN", style = MaterialTheme.typography.titleMedium)
            Text(
                when (state) {
                    AlwaysOnVpnState.KCLASH_ENABLED -> "KClash 已被系统设置为 Always-on VPN。"
                    AlwaysOnVpnState.OTHER_APP_ENABLED -> "系统 Always-on VPN 当前由其他应用接管。"
                    AlwaysOnVpnState.UNKNOWN -> "无法读取系统 Always-on VPN 状态，请在系统 VPN 设置中确认。"
                    AlwaysOnVpnState.DISABLED -> "未启用 Always-on VPN；可在系统 VPN 设置中选择 KClash。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state != AlwaysOnVpnState.KCLASH_ENABLED) {
                OutlinedButton(
                    onClick = onRequest,
                    modifier = Modifier.testTag("always_on_vpn_open_settings"),
                ) { Text("打开系统 VPN 设置") }
            }
        }
    }
}

/**
 * 作者 long：按应用代理直接复用 Android VpnService 的 allow/deny 列表，
 * 页面只负责选择包名并显式保存，避免用户在核心运行中修改已建立的 TUN 路由。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccessControlCard(
    state: AccessControlUiState,
    runtimeState: CoreRuntimeStatus,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (AccessControlMode) -> Unit,
    onSortChange: (AccessSortType) -> Unit,
    onFilterSystemAppsChange: (Boolean) -> Unit,
    onFilterNonInternetAppsChange: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onTogglePackage: (String) -> Unit,
    onSelectVisible: () -> Unit,
    onClearVisible: () -> Unit,
    onToggleVisible: () -> Unit,
    onSmartSelect: () -> Unit,
    onExportToClipboard: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onSave: () -> Unit,
) {
    val canEdit = !runtimeState.isRunning && !state.isLoading && !state.isSaving
    val contentCanEdit = canEdit && state.settings.enable && !state.isSmartSelecting
    val visibleApps = state.visibleApps
    val hasVisibleApps = visibleApps.isNotEmpty()
    val actionsCanEdit = canEdit && !state.isSmartSelecting
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var accessMenuLayer by remember { mutableStateOf(AccessMenuLayer.NONE) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("access_control_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchExpanded) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        enabled = canEdit,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("access_control_search"),
                        singleLine = true,
                        label = { Text("搜索应用或包名") },
                    )
                } else {
                    Text(
                        "按应用代理",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (searchExpanded) onSearchChange("")
                            searchExpanded = !searchExpanded
                        },
                        enabled = canEdit,
                        modifier = Modifier
                            .testTag("access_control_search_toggle")
                            .semantics {
                                role = Role.Button
                                contentDescription = if (searchExpanded) "关闭搜索" else "搜索应用"
                            },
                    ) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = onToggleVisible,
                        enabled = contentCanEdit && hasVisibleApps,
                        modifier = Modifier
                            .testTag("access_control_toggle_visible")
                            .semantics {
                                role = Role.Button
                                contentDescription = if (state.allVisibleAppsSelected) "取消全选当前结果" else "全选当前结果"
                                stateDescription = if (state.allVisibleAppsSelected) "已全选" else "未全选"
                            },
                    ) {
                        Icon(
                            imageVector = if (state.allVisibleAppsSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                            contentDescription = null,
                        )
                    }
                    if (state.hasUnsavedChanges) {
                        IconButton(
                            onClick = onSave,
                            enabled = canEdit,
                            modifier = Modifier
                                .testTag("access_control_topbar_save")
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "保存按应用代理"
                                },
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { accessMenuLayer = AccessMenuLayer.MAIN },
                            enabled = actionsCanEdit,
                            modifier = Modifier
                                .testTag("access_control_topbar_actions")
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "更多操作"
                                },
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = accessMenuLayer == AccessMenuLayer.MAIN,
                            onDismissRequest = { accessMenuLayer = AccessMenuLayer.NONE },
                            modifier = Modifier.testTag("access_control_menu_main"),
                        ) {
                            DropdownMenuItem(
                                text = { Text("开启") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onEnabledChange(true)
                                },
                                enabled = canEdit,
                                trailingIcon = if (state.settings.enable) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "当前状态") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_enable"),
                            )
                            DropdownMenuItem(
                                text = { Text("关闭") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onEnabledChange(false)
                                },
                                enabled = canEdit,
                                trailingIcon = if (!state.settings.enable) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "当前状态") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_disable"),
                            )
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = { accessMenuLayer = AccessMenuLayer.SETTINGS },
                                enabled = canEdit,
                                modifier = Modifier.testTag("access_control_menu_settings"),
                            )
                            DropdownMenuItem(
                                text = { Text("操作") },
                                onClick = { accessMenuLayer = AccessMenuLayer.ACTIONS },
                                enabled = actionsCanEdit,
                                modifier = Modifier.testTag("access_control_menu_actions"),
                            )
                        }
                        DropdownMenu(
                            expanded = accessMenuLayer == AccessMenuLayer.SETTINGS,
                            onDismissRequest = { accessMenuLayer = AccessMenuLayer.NONE },
                            modifier = Modifier.testTag("access_control_menu_settings_panel"),
                        ) {
                            DropdownMenuItem(
                                text = { Text("返回") },
                                onClick = { accessMenuLayer = AccessMenuLayer.MAIN },
                                modifier = Modifier.testTag("access_control_menu_settings_back"),
                            )
                            DropdownMenuItem(
                                text = { Text("白名单") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onModeChange(AccessControlMode.ACCEPT_SELECTED)
                                },
                                enabled = canEdit,
                                trailingIcon = if (state.settings.mode == AccessControlMode.ACCEPT_SELECTED) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "当前模式") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_mode_allow"),
                            )
                            DropdownMenuItem(
                                text = { Text("黑名单") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onModeChange(AccessControlMode.REJECT_SELECTED)
                                },
                                enabled = canEdit,
                                trailingIcon = if (state.settings.mode == AccessControlMode.REJECT_SELECTED) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "当前模式") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_mode_deny"),
                            )
                            DropdownMenuItem(
                                text = { Text("默认排序") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onSortChange(AccessSortType.NONE)
                                },
                                enabled = canEdit,
                                trailingIcon = if (state.settings.sort == AccessSortType.NONE) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "当前排序") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_sort_none"),
                            )
                            DropdownMenuItem(
                                text = { Text("按名称") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onSortChange(AccessSortType.NAME)
                                },
                                enabled = canEdit,
                                trailingIcon = if (state.settings.sort == AccessSortType.NAME) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "当前排序") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_sort_name"),
                            )
                            DropdownMenuItem(
                                text = { Text("按更新时间") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onSortChange(AccessSortType.TIME)
                                },
                                enabled = canEdit,
                                trailingIcon = if (state.settings.sort == AccessSortType.TIME) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "当前排序") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_sort_time"),
                            )
                            DropdownMenuItem(
                                text = { Text("显示系统应用") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onFilterSystemAppsChange(!state.settings.isFilterSystemApp)
                                },
                                enabled = canEdit,
                                trailingIcon = if (!state.settings.isFilterSystemApp) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "已显示") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_system_apps"),
                            )
                            DropdownMenuItem(
                                text = { Text("显示无网络应用") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onFilterNonInternetAppsChange(!state.settings.isFilterNonInternetApp)
                                },
                                enabled = canEdit,
                                trailingIcon = if (!state.settings.isFilterNonInternetApp) {
                                    { Icon(Icons.Outlined.Check, contentDescription = "已显示") }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("access_control_menu_no_network_apps"),
                            )
                        }
                        DropdownMenu(
                            expanded = accessMenuLayer == AccessMenuLayer.ACTIONS,
                            onDismissRequest = { accessMenuLayer = AccessMenuLayer.NONE },
                            modifier = Modifier.testTag("access_control_menu_actions_panel"),
                        ) {
                            DropdownMenuItem(
                                text = { Text("返回") },
                                onClick = { accessMenuLayer = AccessMenuLayer.MAIN },
                                modifier = Modifier.testTag("access_control_menu_actions_back"),
                            )
                            DropdownMenuItem(
                                text = { Text("智能选择") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onSmartSelect()
                                },
                                enabled = contentCanEdit && state.apps.isNotEmpty(),
                                modifier = Modifier.testTag("access_control_menu_smart_select"),
                            )
                            DropdownMenuItem(
                                text = { Text("导出剪贴板") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onExportToClipboard()
                                },
                                enabled = actionsCanEdit,
                                modifier = Modifier.testTag("access_control_clipboard_export"),
                            )
                            DropdownMenuItem(
                                text = { Text("剪贴板导入") },
                                onClick = {
                                    accessMenuLayer = AccessMenuLayer.NONE
                                    onImportFromClipboard()
                                },
                                enabled = actionsCanEdit,
                                modifier = Modifier.testTag("access_control_clipboard_import"),
                            )
                        }
                    }
                    Switch(
                        checked = state.settings.enable,
                        onCheckedChange = onEnabledChange,
                        enabled = canEdit,
                        modifier = Modifier.testTag("access_control_enabled"),
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("access_control_status"),
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (state.settings.enable) "▣" else "ⓘ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (state.settings.enable) {
                        if (state.settings.mode == AccessControlMode.ACCEPT_SELECTED) {
                            "只允许选中应用进入 VPN"
                        } else {
                            "选中应用将会被排除在 VPN 之外"
                        }
                    } else {
                        "应用访问控制已关闭"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.settings.enable) {
                    Text(
                        "已选 ${state.selectedCount}",
                        modifier = Modifier.testTag("access_control_selected_count"),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Button(
                        onClick = { onEnabledChange(true) },
                        enabled = canEdit,
                        modifier = Modifier.testTag("access_control_turn_on"),
                    ) { Text("开启") }
                }
            }
            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }
            if (state.isLoading) {
                Text("正在读取已安装应用…")
            } else {
                // 作者 long：关闭按应用代理时保留配置内容但加上视觉遮罩并锁定入口，
                // 让用户能看见当前选择，避免误以为关闭会清空设置；重新开启后立即恢复编辑。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (state.settings.enable) 1f else 0.55f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("排序方式", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TextButton(
                            onClick = { onSortChange(AccessSortType.NONE) },
                            enabled = contentCanEdit,
                            modifier = Modifier.testTag("access_control_sort_none"),
                        ) { Text("默认") }
                        TextButton(
                            onClick = { onSortChange(AccessSortType.NAME) },
                            enabled = contentCanEdit,
                            modifier = Modifier.testTag("access_control_sort_name"),
                        ) { Text("名称") }
                        TextButton(
                            onClick = { onSortChange(AccessSortType.TIME) },
                            enabled = contentCanEdit,
                            modifier = Modifier.testTag("access_control_sort_time"),
                        ) { Text("更新时间") }
                    }
                    FlowRow(
                        itemVerticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Switch(
                            checked = state.settings.isFilterSystemApp,
                            onCheckedChange = onFilterSystemAppsChange,
                            enabled = contentCanEdit,
                            modifier = Modifier.testTag("access_control_filter_system"),
                        )
                        Text("过滤系统应用")
                    }
                    FlowRow(
                        itemVerticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Switch(
                            checked = state.settings.isFilterNonInternetApp,
                            onCheckedChange = onFilterNonInternetAppsChange,
                            enabled = contentCanEdit,
                            modifier = Modifier.testTag("access_control_filter_no_network"),
                        )
                        Text("过滤无网络应用")
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onModeChange(AccessControlMode.ACCEPT_SELECTED) },
                            enabled = contentCanEdit,
                            modifier = Modifier.testTag("access_control_mode_allow"),
                        ) { Text("白名单") }
                        OutlinedButton(
                            onClick = { onModeChange(AccessControlMode.REJECT_SELECTED) },
                            enabled = contentCanEdit,
                            modifier = Modifier.testTag("access_control_mode_deny"),
                        ) { Text("黑名单") }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = onSmartSelect,
                            enabled = contentCanEdit && state.apps.isNotEmpty(),
                            modifier = Modifier.testTag("access_control_smart_select"),
                        ) {
                            if (state.isSmartSelecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("智能选择")
                            }
                        }
                        Box {
                            TextButton(
                                onClick = { accessMenuLayer = AccessMenuLayer.ACTIONS },
                                enabled = actionsCanEdit,
                                modifier = Modifier.testTag("access_control_actions"),
                            ) { Text("更多操作") }
                        }
                        TextButton(
                            onClick = onSelectVisible,
                            enabled = contentCanEdit && hasVisibleApps,
                            modifier = Modifier.testTag("access_control_select_visible"),
                        ) { Text("全选当前结果") }
                        TextButton(
                            onClick = onClearVisible,
                            enabled = contentCanEdit && hasVisibleApps,
                            modifier = Modifier.testTag("access_control_clear_visible"),
                        ) { Text("清除当前结果") }
                    }
                    if (state.apps.isEmpty()) {
                        // 作者 long：空清单表示设备当前没有可识别的启动器应用，必须给出明确原因，
                        // 避免用户把一个空 LazyColumn 误认为加载失败或权限没有生效。
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("access_control_empty_state"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("没有可用于按应用代理的已安装应用")
                            Text(
                                "请先安装一个可启动的应用，再返回此页面重新读取。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (!hasVisibleApps) {
                        Text(
                            "没有匹配的应用",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("access_control_no_results"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .testTag("access_control_app_list"),
                        ) {
                            items(visibleApps, key = { it.packageName }) { app ->
                                val checked = app.packageName in currentPackageList(state.settings)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { onTogglePackage(app.packageName) },
                                        enabled = contentCanEdit,
                                        modifier = Modifier.testTag("access_control_app_${app.packageName}"),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onSave,
                        enabled = contentCanEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("access_control_save"),
                    ) {
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("保存按应用代理")
                    }
                }
            }
            if (runtimeState.isRunning) {
                Text(
                    "核心运行中，按应用代理设置已锁定；停止核心后才能修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun RuntimeCard(
    state: CoreRuntimeStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_runtime_card"),
    ) {
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
internal fun RuntimeStatsCard(
    state: RuntimeStatsUiState,
    runtimeState: CoreRuntimeStatus,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_stats_card"),
    ) {
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
internal fun ProbeCard(
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_probe_card"),
    ) {
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
internal fun ConnectionsCard(
    state: ConnectionsUiState,
    runtimeState: CoreRuntimeStatus,
    onCopyText: (String) -> Unit,
) {
    // 作者 long：活动连接可能在轮询中消失，因此保留点击时快照；重建后也无需再向核心查找已结束的连接。
    var selectedConnection by rememberSaveable(stateSaver = ConnectionDetailSaver) {
        mutableStateOf<CoreConnection?>(null)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connections_card"),
    ) {
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

                else -> state.connections.take(MAX_CONNECTIONS_ON_HOME).forEachIndexed { index, connection ->
                    ConnectionRow(
                        connection = connection,
                        modifier = Modifier.testTag("connections_row_$index"),
                        onClick = {
                            // 作者 long：摘要行会截断长地址和进程信息，点击详情让用户能完整核对所选连接。
                            selectedConnection = connection
                        },
                    )
                }
            }
        }
    }
    selectedConnection?.let { connection ->
        ConnectionDetailSheet(
            connection = connection,
            onDismiss = { selectedConnection = null },
            onCopyText = onCopyText,
        )
    }
}

@Composable
private fun ConnectionRow(
    connection: CoreConnection,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val metadata = connection.metadata
    val title = metadata.host.ifBlank {
        metadata.destinationIp.ifBlank { metadata.remoteDestination.ifBlank { "未知目标" } }
    }
    val endpoint = listOf(metadata.destinationIp, metadata.destinationPort)
        .filter { it.isNotBlank() }
        .joinToString(":")
    // 作者 long：沿用 Flutter 的代理链反序展示，让同一条连接在两端保持相同的阅读顺序。
    val chain = connection.chains.asReversed().joinToString(" → ")
    Column(
        modifier = modifier.then(
            if (onClick == null) Modifier else Modifier.clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
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
private fun ConnectionDetailSheet(
    connection: CoreConnection,
    onDismiss: () -> Unit,
    onCopyText: (String) -> Unit,
) {
    val metadata = connection.metadata
    val contentScrollState = rememberScrollState()
    var copyMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copyMessage) {
        if (copyMessage != null) {
            kotlinx.coroutines.delay(2_000)
            copyMessage = null
        }
    }
    val copyValue: (String) -> Unit = { value ->
        // 作者 long：复制反馈放在面板内，避免外层 Snackbar 被模态遮罩盖住；失败时不能显示成功。
        copyMessage = runCatching { onCopyText(value) }.fold({ "已复制" }, { "复制失败" })
    }
    ConnectionModalSheet(onDismiss = onDismiss, contentScrollState = contentScrollState) {
        Column(
            modifier = Modifier
                .testTag("connections_detail_content")
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "连接详情",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭连接详情")
                }
            }
            copyMessage?.let { Text(it, modifier = Modifier.testTag("connections_copy_feedback")) }
            Column(
                modifier = Modifier
                    .testTag("connections_detail_dialog")
                    .weight(1f, fill = false)
                    .verticalScroll(contentScrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ConnectionDetailSection(
                    title = "基本信息",
                    rows = listOf(
                        "创建时间" to formatConnectionStart(connection.start),
                        "网络类型" to metadata.network,
                        "进程" to connectionProcessText(metadata.process, metadata.uid),
                        "规则" to connectionRuleText(connection),
                        "上传" to formatBytes(connection.upload),
                        "下载" to formatBytes(connection.download),
                    ),
                    onCopyText = copyValue,
                )
                ConnectionDetailSection(
                    title = "地址",
                    rows = listOf(
                        "主机" to metadata.host,
                        "来源" to connectionEndpoint(metadata.sourceIp, metadata.sourcePort),
                        "目标" to connectionEndpoint(metadata.destinationIp, metadata.destinationPort),
                        "目标 GeoIP" to metadata.destinationGeoIp.joinToString(" "),
                        "目标 ASN" to metadata.destinationIpAsn,
                        "远端目标" to metadata.remoteDestination,
                    ),
                    onCopyText = copyValue,
                )
                ConnectionDetailSection(
                    title = "代理",
                    rows = listOf(
                        "特殊代理" to metadata.specialProxy,
                        "特殊规则" to metadata.specialRules,
                        "DNS 模式" to metadata.dnsMode,
                    ),
                    onCopyText = copyValue,
                )
                ConnectionChainRow(connection.chains)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionDetailSection(
    title: String,
    rows: List<Pair<String, String>>,
    onCopyText: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connection_detail_$label")
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "复制$label") { onCopyText(value) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(0.42f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    modifier = Modifier.weight(0.58f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionChainRow(chains: List<String>) {
    val displayed = chains.asReversed()
    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = displayed.joinToString(" → ")
        }.testTag("connection_detail_chains"),
    ) {
        Text("代理链", modifier = Modifier.weight(0.42f), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            modifier = Modifier.weight(0.58f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            displayed.forEachIndexed { index, chain ->
                if (index > 0) Text("→")
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(chain, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

private fun connectionEndpoint(ip: String, port: String): String = when {
    ip.isBlank() -> ""
    port.isBlank() -> ip
    else -> "$ip:$port"
}

private fun connectionRuleText(connection: CoreConnection): String = buildString {
    append(connection.rule)
    if (connection.rulePayload.isNotBlank()) {
        append("(")
        append(connection.rulePayload)
        append(")")
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
internal fun RequestsDialog(
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
internal fun DnsDialog(
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
internal fun LogsDialog(
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

/**
 * 作者 long：排序先在弹窗内形成草稿，只有点击保存才写入 Room，避免用户拖动或调整过程中
 * 反复改变当前 Profile 的持久化顺序；上下移动按钮同时保留了无障碍和小屏设备的可操作性。
 */
@Composable
internal fun ProfileReorderDialog(
    profiles: List<Profile>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit,
) {
    var orderedProfiles by remember(profiles.map(Profile::id)) {
        mutableStateOf(profiles)
    }

    fun move(index: Int, offset: Int) {
        val target = index + offset
        if (target !in orderedProfiles.indices) return
        val next = orderedProfiles.toMutableList()
        val item = next.removeAt(index)
        next.add(target, item)
        orderedProfiles = next
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("排序配置") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orderedProfiles, key = { it.id }) { profile ->
                    val index = orderedProfiles.indexOfFirst { it.id == profile.id }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}. ${profile.realLabel}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { move(index, -1) },
                                enabled = !saving && index > 0,
                            ) { Text("上移") }
                            TextButton(
                                onClick = { move(index, 1) },
                                enabled = !saving && index < orderedProfiles.lastIndex,
                            ) { Text("下移") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(orderedProfiles.map(Profile::id)) },
                enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator() else Text("保存排序")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

/**
 * 作者 long：脚本正文、元数据和 Profile 绑定都通过 ViewModel 回写；核心启动时再由服务层
 * 在暂存配置上执行绑定脚本，原始 Profile 文件始终保持可编辑和可回退。
 */
@Composable
internal fun ScriptsLibraryDialog(
    state: ScriptsUiState,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Script) -> Unit,
    onDelete: (Script) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onImportRemote: () -> Unit = {},
    onSync: (Script) -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<Script?>(null) }
    var orderedScripts by remember(state.scripts.map(Script::id)) {
        mutableStateOf(state.scripts)
    }

    fun move(index: Int, offset: Int) {
        val target = index + offset
        if (target !in orderedScripts.indices) return
        val next = orderedScripts.toMutableList()
        val item = next.removeAt(index)
        next.add(target, item)
        orderedScripts = next
        onReorder(next.map(Script::id))
    }

    AlertDialog(
        onDismissRequest = { if (!state.isBusy) onDismiss() },
        title = { Text("脚本库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "支持本地或远程脚本、Profile 绑定；核心启动时会在暂存配置上执行 JavaScript。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (orderedScripts.isEmpty()) {
                    Text("还没有脚本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(orderedScripts, key = { it.id }) { script ->
                            val index = orderedScripts.indexOfFirst { it.id == script.id }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(script.label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${index + 1}. ${script.fileName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.End) {
                                        TextButton(
                                            onClick = { move(index, -1) },
                                            enabled = !state.isBusy && index > 0,
                                        ) { Text("上移") }
                                        TextButton(
                                            onClick = { move(index, 1) },
                                            enabled = !state.isBusy && index < orderedScripts.lastIndex,
                                        ) { Text("下移") }
                                        TextButton(
                                            onClick = { onEdit(script) },
                                            enabled = !state.isBusy,
                                        ) { Text("编辑") }
                                        if (script.url.isNotBlank()) {
                                            TextButton(
                                                onClick = { onSync(script) },
                                                enabled = !state.isBusy,
                                            ) { Text("同步") }
                                        }
                                        TextButton(
                                            onClick = { pendingDelete = script },
                                            enabled = !state.isBusy,
                                        ) { Text("删除") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onImportRemote, enabled = !state.isBusy) { Text("导入远程脚本") }
                TextButton(onClick = onCreate, enabled = !state.isBusy) { Text("新建脚本") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isBusy) { Text("关闭") }
        },
    )

    pendingDelete?.let { script ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除脚本") },
            text = { Text("确定删除“${script.label}”及其本地文件吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(script)
                    },
                    enabled = !state.isBusy,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
internal fun ScriptRemoteDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onImport: (label: String, url: String) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("导入远程脚本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    enabled = !busy,
                    label = { Text("脚本名称（可选）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    enabled = !busy,
                    label = { Text("HTTP(S) 脚本地址") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(label, url) },
                enabled = !busy && url.isNotBlank(),
            ) {
                if (busy) CircularProgressIndicator() else Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

@Composable
internal fun ScriptEditorDialog(
    state: ScriptEditorUiState,
    onDismiss: () -> Unit,
    onSave: (label: String, content: String) -> Unit,
) {
    var label by remember(state.script?.id, state.isOpen) { mutableStateOf(state.label) }
    var content by remember(state.script?.id, state.isOpen) { mutableStateOf(state.content) }
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(if (state.script == null) "新建脚本" else "编辑脚本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    enabled = !state.isSaving,
                    label = { Text("脚本名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 14,
                    maxLines = 22,
                    label = { Text("JavaScript") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, content) },
                enabled = !state.isSaving && label.isNotBlank() && content.isNotBlank(),
            ) {
                if (state.isSaving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("取消") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    canMutate: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit = {},
    onUpdate: () -> Unit,
    onSetAutoUpdate: (Boolean) -> Unit = {},
    onDelete: () -> Unit,
    onOpenRulesEditor: () -> Unit = {},
    onOpenProxiesEditor: () -> Unit = {},
    onOpenProxyGroupsEditor: () -> Unit = {},
    onOpenProvidersEditor: () -> Unit = {},
    onOpenDnsEditor: () -> Unit = {},
    onOpenDialersEditor: () -> Unit = {},
    onOpenScriptBinding: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}"),
        onClick = onSelect,
    ) {
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
            if (profile.type == ProfileType.URL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("自动更新", modifier = Modifier.weight(1f))
                    Switch(
                        checked = profile.autoUpdate,
                        onCheckedChange = onSetAutoUpdate,
                        enabled = canMutate,
                        modifier = Modifier.testTag("profile_auto_update_${profile.id}"),
                    )
                }
                profile.lastUpdateError?.let { error ->
                    Text(
                        text = "自动更新失败：$error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 48.dp),
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                maxItemsInEachRow = 3,
            ) {
                if (profile.type != ProfileType.URL) {
                    TextButton(onClick = onEdit, enabled = canMutate) { Text("编辑") }
                }
                if (profile.type == ProfileType.URL) {
                    TextButton(onClick = onUpdate, enabled = canMutate) { Text("同步") }
                }
                TextButton(
                    onClick = onRename,
                    enabled = canMutate,
                    modifier = Modifier.testTag("profile_rename_${profile.id}"),
                ) { Text("重命名") }
                if (profile.type == ProfileType.CUSTOM) {
                    TextButton(onClick = onOpenProvidersEditor, enabled = canMutate) { Text("Provider") }
                    TextButton(onClick = onOpenDnsEditor, enabled = canMutate) { Text("DNS") }
                    TextButton(onClick = onOpenDialersEditor, enabled = canMutate) { Text("Dialers") }
                    TextButton(onClick = onOpenProxyGroupsEditor, enabled = canMutate) { Text("代理组") }
                    TextButton(onClick = onOpenProxiesEditor, enabled = canMutate) { Text("代理") }
                    TextButton(onClick = onOpenRulesEditor, enabled = canMutate) { Text("高级") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenScriptBinding, enabled = canMutate) { Text("脚本") }
                TextButton(onClick = onDelete, enabled = canMutate) { Text("删除") }
            }
        }
    }
}

@Composable
internal fun ProfileRenameDialog(
    profile: Profile,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var label by rememberSaveable(profile.id) { mutableStateOf(profile.label) }
    val normalized = label.trim()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("重命名配置") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(64) },
                enabled = !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                supportingText = { Text("名称不能为空，最多 64 个字符") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(normalized) },
                enabled = !saving && normalized.isNotEmpty() && normalized != profile.label,
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

/**
 * 作者 long：绑定弹窗只修改 Profile 与脚本资源的关系；真正执行发生在核心启动的暂存配置阶段，
 * 这样切换绑定不会改写原始 YAML，也不会在核心运行期间改变输入。
 */
@Composable
internal fun ProfileScriptBindingDialog(
    profile: Profile,
    scripts: List<Script>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
    onOpenScripts: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("脚本 · ${profile.realLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "保存绑定关系后，下一次核心启动会在暂存配置上执行该 JavaScript。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (scripts.isEmpty()) {
                    Text(
                        "还没有脚本，请先在脚本库中新建脚本。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = profile.scriptId == null,
                                    onClick = { onSelect(null) },
                                    enabled = !saving,
                                )
                                TextButton(
                                    onClick = { onSelect(null) },
                                    enabled = !saving,
                                ) { Text("不绑定脚本") }
                            }
                        }
                        items(scripts, key = { it.id }) { script ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = profile.scriptId == script.id,
                                    onClick = { onSelect(script.id) },
                                    enabled = !saving,
                                )
                                TextButton(
                                    onClick = { onSelect(script.id) },
                                    enabled = !saving,
                                ) { Text(script.label) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenScripts, enabled = !saving) { Text("脚本库") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("关闭") }
        },
    )
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

/**
 * 作者 long：规则编辑器只回写顶层规则区块，保留自定义 Profile 中未编辑的端口、代理和 DNS 字段，
 * 让用户可以逐步完善配置而不必承担整份 YAML 重排的风险。
 */
@Composable
internal fun ProfileRulesEditorDialog(
    profile: Profile,
    initialRules: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var rules by rememberSaveable(profile.id) { mutableStateOf(initialRules) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("规则 · ${profile.realLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "每行一条规则，例如 DOMAIN-SUFFIX,example.com,DIRECT；SUB-RULE 需要先展开后保存。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10,
                    maxLines = 18,
                    enabled = !saving,
                    label = { Text("规则 YAML") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(rules) },
                enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

/**
 * 作者 long：代理编辑器只负责代理列表的 YAML 片段，保存时由 ViewModel 校验并回写原始 Profile，
 * 让用户可以先调整节点而不影响同一份配置中的规则、DNS 和端口设置。
 */
@Composable
internal fun ProfileProxiesEditorDialog(
    profile: Profile,
    initialProxies: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var proxies by rememberSaveable(profile.id) { mutableStateOf(initialProxies) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("代理 · ${profile.realLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "每个代理以 - name 和 type 开始，后续字段保持两格缩进；可粘贴 proxies: 区块内容。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = proxies,
                    onValueChange = { proxies = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    maxLines = 20,
                    enabled = !saving,
                    label = { Text("代理 YAML") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(proxies) },
                enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

/**
 * 作者 long：代理组编辑器只负责代理组列表的 YAML 片段，保存时由 ViewModel 校验并回写原始 Profile，
 * 让用户可以调整选择、测速或回退组而不影响同一份配置中的节点和规则。
 */
@Composable
internal fun ProfileProxyGroupsEditorDialog(
    profile: Profile,
    initialGroups: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var groups by rememberSaveable(profile.id) { mutableStateOf(initialGroups) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("代理组 · ${profile.realLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "每个代理组以 - name 和 type 开始，支持 select、url-test、fallback 和 load-balance。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = groups,
                    onValueChange = { groups = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    maxLines = 20,
                    enabled = !saving,
                    label = { Text("代理组 YAML") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(groups) },
                enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

/**
 * 作者 long：Provider 编辑器把 proxy-providers 和 rule-providers 分成两个 map 输入区，
 * 让用户能局部调整远程资源定义，同时由 ViewModel 负责结构校验和原子回写。
 */
@Composable
internal fun ProfileProvidersEditorDialog(
    profile: Profile,
    initialProxyProviders: String,
    initialRuleProviders: String,
    initialProxyProviderOverrides: Map<String, ProxyProviderOverride> = emptyMap(),
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (proxyProviders: String, ruleProviders: String) -> Unit,
    onSaveWithOverrides: ((String, String, Map<String, ProxyProviderOverride>) -> Unit)? = null,
) {
    var proxyProviders by rememberSaveable(profile.id, "proxyProviders") {
        mutableStateOf(initialProxyProviders)
    }
    var ruleProviders by rememberSaveable(profile.id, "ruleProviders") {
        mutableStateOf(initialRuleProviders)
    }
    var proxyProviderOverrides by remember(profile.id, initialProxyProviderOverrides) {
        mutableStateOf(initialProxyProviderOverrides.toMap())
    }
    var editingProvider by remember { mutableStateOf<String?>(null) }
    val providerNames = remember(proxyProviders) {
        ProfileProvidersEditor.proxyProviderNames(proxyProviders)
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Provider · ${profile.realLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "每个条目使用 providerName: 开始，并在下一层填写 type、url、path 等字段；两个区块分别对应代理和规则 Provider。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = proxyProviders,
                    onValueChange = { proxyProviders = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                    enabled = !saving,
                    label = { Text("proxy-providers") },
                )
                OutlinedTextField(
                    value = ruleProviders,
                    onValueChange = { ruleProviders = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                    enabled = !saving,
                    label = { Text("rule-providers") },
                )
                Text(
                    "字段化代理 Provider 覆盖",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (providerNames.isEmpty()) {
                    Text(
                        "先在 proxy-providers 中定义 Provider，才能配置健康检查和节点覆盖。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    providerNames.forEach { name ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name)
                                Text(
                                    providerOverrideSummary(proxyProviderOverrides[name]),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { editingProvider = name },
                                enabled = !saving,
                            ) { Text("编辑字段") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val filtered = proxyProviderOverrides.filterKeys(providerNames::contains)
                    if (onSaveWithOverrides != null) {
                        onSaveWithOverrides(proxyProviders, ruleProviders, filtered)
                    } else {
                        onSave(proxyProviders, ruleProviders)
                    }
                },
                enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
    editingProvider?.let { name ->
        ProfileProxyProviderOverrideDialog(
            providerName = name,
            initial = proxyProviderOverrides[name] ?: ProxyProviderOverride(),
            saving = saving,
            onDismiss = { editingProvider = null },
            onSave = { updated ->
                proxyProviderOverrides = if (updated.isEmpty) {
                    proxyProviderOverrides - name
                } else {
                    proxyProviderOverrides + (name to updated)
                }
                editingProvider = null
            },
        )
    }
}

/**
 * 作者 long：DNS 覆盖使用字段化表单保存到 Profile 元数据，启动时才合并到暂存 YAML；
 * 列表字段按“一行一个服务器”编辑，避免用户在配置正文里同时维护两份 DNS 状态。
 */
@Composable
internal fun ProfileDnsOverrideDialog(
    profile: Profile,
    initial: ProfileDnsOverrides,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProfileDnsOverrides) -> Unit,
) {
    var enableSet by remember(profile.id, initial) { mutableStateOf(initial.enable != null) }
    var enableValue by remember(profile.id, initial) { mutableStateOf(initial.enable ?: true) }
    var ipv6Set by remember(profile.id, initial) { mutableStateOf(initial.ipv6 != null) }
    var ipv6Value by remember(profile.id, initial) { mutableStateOf(initial.ipv6 ?: false) }
    var respectRulesSet by remember(profile.id, initial) { mutableStateOf(initial.respectRules != null) }
    var respectRulesValue by remember(profile.id, initial) { mutableStateOf(initial.respectRules ?: false) }
    var preferH3Set by remember(profile.id, initial) { mutableStateOf(initial.preferH3 != null) }
    var preferH3Value by remember(profile.id, initial) { mutableStateOf(initial.preferH3 ?: false) }
    var useHostsSet by remember(profile.id, initial) { mutableStateOf(initial.useHosts != null) }
    var useHostsValue by remember(profile.id, initial) { mutableStateOf(initial.useHosts ?: true) }
    var useSystemHostsSet by remember(profile.id, initial) { mutableStateOf(initial.useSystemHosts != null) }
    var useSystemHostsValue by remember(profile.id, initial) { mutableStateOf(initial.useSystemHosts ?: true) }
    var listen by remember(profile.id, initial) { mutableStateOf(initial.listen.orEmpty()) }
    var enhancedMode by remember(profile.id, initial) { mutableStateOf(initial.enhancedMode.orEmpty()) }
    var fakeIpRange by remember(profile.id, initial) { mutableStateOf(initial.fakeIpRange.orEmpty()) }
    var nameserver by remember(profile.id, initial) { mutableStateOf(initial.nameserver.orEmpty().joinToString("\n")) }
    var fallback by remember(profile.id, initial) { mutableStateOf(initial.fallback.orEmpty().joinToString("\n")) }
    var defaultNameserver by remember(profile.id, initial) { mutableStateOf(initial.defaultNameserver.orEmpty().joinToString("\n")) }
    var proxyServerNameserver by remember(profile.id, initial) { mutableStateOf(initial.proxyServerNameserver.orEmpty().joinToString("\n")) }
    var directNameserver by remember(profile.id, initial) { mutableStateOf(initial.directNameserver.orEmpty().joinToString("\n")) }
    var fakeIpFilter by remember(profile.id, initial) { mutableStateOf(initial.fakeIpFilter.orEmpty().joinToString("\n")) }

    fun lines(value: String): List<String>? = value.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
        .takeIf { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("DNS 覆盖 · ${profile.realLabel}") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .testTag("profile-dns-override-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "只保存已勾选或填写的字段；未设置字段继续使用配置文件中的 DNS 值。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "启用 DNS",
                        enabled = enableSet,
                        value = enableValue,
                        onEnabledChange = { enableSet = it },
                        onValueChange = { enableValue = it },
                    )
                }
                item {
                    OutlinedTextField(
                        value = listen,
                        onValueChange = { listen = it },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("监听地址") },
                    )
                }
                item {
                    OutlinedTextField(
                        value = enhancedMode,
                        onValueChange = { enhancedMode = it },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("增强模式（redir-host / fake-ip / hosts）") },
                    )
                }
                item {
                    OutlinedTextField(
                        value = fakeIpRange,
                        onValueChange = { fakeIpRange = it },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Fake-IP 地址段") },
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "IPv6",
                        enabled = ipv6Set,
                        value = ipv6Value,
                        onEnabledChange = { ipv6Set = it },
                        onValueChange = { ipv6Value = it },
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "遵循规则",
                        enabled = respectRulesSet,
                        value = respectRulesValue,
                        onEnabledChange = { respectRulesSet = it },
                        onValueChange = { respectRulesValue = it },
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "优先 H3",
                        enabled = preferH3Set,
                        value = preferH3Value,
                        onEnabledChange = { preferH3Set = it },
                        onValueChange = { preferH3Value = it },
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "使用 hosts",
                        enabled = useHostsSet,
                        value = useHostsValue,
                        onEnabledChange = { useHostsSet = it },
                        onValueChange = { useHostsValue = it },
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "使用系统 hosts",
                        enabled = useSystemHostsSet,
                        value = useSystemHostsValue,
                        onEnabledChange = { useSystemHostsSet = it },
                        onValueChange = { useSystemHostsValue = it },
                    )
                }
                item { Text("每行一个 DNS 服务器", style = MaterialTheme.typography.titleSmall) }
                item { dnsListField("默认 DNS", defaultNameserver) { defaultNameserver = it } }
                item { dnsListField("Nameserver", nameserver) { nameserver = it } }
                item { dnsListField("Fallback", fallback) { fallback = it } }
                item { dnsListField("代理服务器 DNS", proxyServerNameserver) { proxyServerNameserver = it } }
                item { dnsListField("直连 DNS", directNameserver) { directNameserver = it } }
                item { dnsListField("Fake-IP 过滤", fakeIpFilter) { fakeIpFilter = it } }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ProfileDnsOverrides(
                            enable = enableSet.takeIf { it }?.let { enableValue },
                            listen = listen.trim().takeIf { it.isNotEmpty() },
                            ipv6 = ipv6Set.takeIf { it }?.let { ipv6Value },
                            respectRules = respectRulesSet.takeIf { it }?.let { respectRulesValue },
                            preferH3 = preferH3Set.takeIf { it }?.let { preferH3Value },
                            useHosts = useHostsSet.takeIf { it }?.let { useHostsValue },
                            useSystemHosts = useSystemHostsSet.takeIf { it }?.let { useSystemHostsValue },
                            enhancedMode = enhancedMode.trim().takeIf { it.isNotEmpty() },
                            fakeIpRange = fakeIpRange.trim().takeIf { it.isNotEmpty() },
                            nameserver = lines(nameserver),
                            fallback = lines(fallback),
                            defaultNameserver = lines(defaultNameserver),
                            proxyServerNameserver = lines(proxyServerNameserver),
                            directNameserver = lines(directNameserver),
                            fakeIpFilter = lines(fakeIpFilter),
                        ),
                    )
                },
                enabled = !saving,
            ) { if (saving) CircularProgressIndicator() else Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

/**
 * 作者 long：Dialers 对话框只维护代理到出站目标的映射；保存时写入 Profile 元数据，
 * 核心启动阶段才注入 `dialer-proxy`，因此取消编辑不会污染原始 YAML。
 */
@Composable
internal fun ProfileDialersEditorDialog(
    profile: Profile,
    initialDialers: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var dialers by remember(profile.id, initialDialers) { mutableStateOf(initialDialers) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Dialers · ${profile.realLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "每行填写一个代理到目标的映射；清空全部内容会移除暂存配置中的 dialer-proxy。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = dialers,
                    onValueChange = { dialers = it },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 14,
                    label = { Text("代理名 -> 目标名") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(dialers) }, enabled = !saving) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

@Composable
private fun dnsListField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5,
        label = { Text(label) },
    )
}

/**
 * 作者 long：每个字段保留“未设置”和“显式设置”两种状态，避免把用户清空的覆盖误写成
 * Mihomo 默认值；布尔字段通过左侧开关控制是否写入，右侧按钮切换 true/false。
 */
@Composable
internal fun ProfileProxyProviderOverrideDialog(
    providerName: String,
    initial: ProxyProviderOverride,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProxyProviderOverride) -> Unit,
) {
    var url by remember(providerName, initial) { mutableStateOf(initial.url.orEmpty()) }
    var interval by remember(providerName, initial) { mutableStateOf(initial.interval?.toString().orEmpty()) }
    var timeout by remember(providerName, initial) { mutableStateOf(initial.timeout?.toString().orEmpty()) }
    var expectedStatus by remember(providerName, initial) { mutableStateOf(initial.expectedStatus.orEmpty()) }
    var additionalPrefix by remember(providerName, initial) { mutableStateOf(initial.additionalPrefix.orEmpty()) }
    var additionalSuffix by remember(providerName, initial) { mutableStateOf(initial.additionalSuffix.orEmpty()) }
    var lazySet by remember(providerName, initial) { mutableStateOf(initial.lazy != null) }
    var lazyValue by remember(providerName, initial) { mutableStateOf(initial.lazy ?: true) }
    var udpSet by remember(providerName, initial) { mutableStateOf(initial.udp != null) }
    var udpValue by remember(providerName, initial) { mutableStateOf(initial.udp ?: true) }
    var skipCertSet by remember(providerName, initial) { mutableStateOf(initial.skipCertVerify != null) }
    var skipCertValue by remember(providerName, initial) { mutableStateOf(initial.skipCertVerify ?: true) }
    var ipVersion by remember(providerName, initial) { mutableStateOf(initial.ipVersion) }
    var error by remember(providerName) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Provider 覆盖 · $providerName") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text("健康检查", style = MaterialTheme.typography.titleSmall) }
                item {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        label = { Text("URL") },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = interval,
                            onValueChange = { interval = it },
                            modifier = Modifier.weight(1f),
                            enabled = !saving,
                            label = { Text("间隔（秒）") },
                        )
                        OutlinedTextField(
                            value = timeout,
                            onValueChange = { timeout = it },
                            modifier = Modifier.weight(1f),
                            enabled = !saving,
                            label = { Text("超时（毫秒）") },
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = expectedStatus,
                        onValueChange = { expectedStatus = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        label = { Text("期望状态") },
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "lazy",
                        enabled = lazySet,
                        value = lazyValue,
                        onEnabledChange = { lazySet = it },
                        onValueChange = { lazyValue = it },
                    )
                }
                item { Text("名称", style = MaterialTheme.typography.titleSmall) }
                item {
                    OutlinedTextField(
                        value = additionalPrefix,
                        onValueChange = { additionalPrefix = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        label = { Text("前缀") },
                    )
                }
                item {
                    OutlinedTextField(
                        value = additionalSuffix,
                        onValueChange = { additionalSuffix = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        label = { Text("后缀") },
                    )
                }
                item { Text("连接", style = MaterialTheme.typography.titleSmall) }
                item {
                    BooleanOverrideRow(
                        label = "UDP",
                        enabled = udpSet,
                        value = udpValue,
                        onEnabledChange = { udpSet = it },
                        onValueChange = { udpValue = it },
                    )
                }
                item {
                    BooleanOverrideRow(
                        label = "跳过证书校验",
                        enabled = skipCertSet,
                        value = skipCertValue,
                        onEnabledChange = { skipCertSet = it },
                        onValueChange = { skipCertValue = it },
                    )
                }
                item {
                    Text("IP 版本")
                    listOf(null to "未设置", "ipv4" to "IPv4", "ipv6" to "IPv6", "dual" to "双栈")
                        .forEach { (value, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = ipVersion == value,
                                    onClick = { ipVersion = value },
                                )
                                Text(label)
                            }
                        }
                }
                error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedInterval = interval.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    val parsedTimeout = timeout.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    if (interval.isNotBlank() && (parsedInterval == null || parsedInterval <= 0)) {
                        error = "间隔必须是正整数"
                        return@TextButton
                    }
                    if (timeout.isNotBlank() && (parsedTimeout == null || parsedTimeout <= 0)) {
                        error = "超时必须是正整数"
                        return@TextButton
                    }
                    onSave(
                        ProxyProviderOverride(
                            url = url.trim().takeIf { it.isNotEmpty() },
                            interval = parsedInterval,
                            timeout = parsedTimeout,
                            lazy = lazySet.takeIf { it }?.let { lazyValue },
                            expectedStatus = expectedStatus.trim().takeIf { it.isNotEmpty() },
                            additionalPrefix = additionalPrefix.takeIf { it.isNotEmpty() },
                            additionalSuffix = additionalSuffix.takeIf { it.isNotEmpty() },
                            udp = udpSet.takeIf { it }?.let { udpValue },
                            skipCertVerify = skipCertSet.takeIf { it }?.let { skipCertValue },
                            ipVersion = ipVersion,
                        ),
                    )
                },
                enabled = !saving,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

@Composable
private fun BooleanOverrideRow(
    label: String,
    enabled: Boolean,
    value: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
        Text(label, modifier = Modifier.weight(1f))
        if (enabled) {
            TextButton(onClick = { onValueChange(!value) }) { Text(value.toString()) }
        }
    }
}

private fun providerOverrideSummary(override: ProxyProviderOverride?): String {
    if (override == null || override.isEmpty) return "未设置覆盖"
    val fields = buildList {
        if (override.url != null || override.expectedStatus != null) add("健康检查")
        if (override.interval != null || override.timeout != null || override.lazy != null) add("周期")
        if (override.additionalPrefix != null || override.additionalSuffix != null) add("名称")
        if (override.udp != null || override.skipCertVerify != null || override.ipVersion != null) add("连接")
    }
    return "已设置：${fields.joinToString("、")}"
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
