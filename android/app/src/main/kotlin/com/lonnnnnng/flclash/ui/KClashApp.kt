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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.InputChip
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreOutboundMode
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

internal enum class ObservationTab(val label: String) {
    REQUESTS("请求"),
    DNS("DNS"),
    LOGS("日志"),
}

private enum class ToolsSubpage {
    MENU,
    CONNECTIONS,
    OBSERVATIONS,
}

private enum class AccessMenuLayer {
    NONE,
    MAIN,
    SETTINGS,
    ACTIONS,
}

private enum class ProxyToolsMenuPage {
    ROOT,
    DISPLAY_MODE,
    SORT,
    DENSITY,
    CARD_SIZE,
    ICON_STYLE,
}

internal enum class ProfilesToolbarAction {
    SYNC_ALL,
    SORT,
    SCRIPTS,
    WEBDAV,
    BACKUP_EXPORT,
    BACKUP_RESTORE,
}

private val ProfileIllustrationShape = GenericShape { size, _ ->
    val radius = minOf(size.width, size.height) * 0.48f
    repeat(7) { index ->
        val angle = -PI / 2 + index * 2 * PI / 7
        val x = size.width / 2 + cos(angle).toFloat() * radius
        val y = size.height / 2 + sin(angle).toFloat() * radius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
internal fun ProfilesTopBarActions(
    profiles: List<Profile>,
    busy: Boolean,
    canMutate: Boolean,
    onAdd: () -> Unit,
    onAction: (ProfilesToolbarAction) -> Unit,
) {
    if (profiles.any { it.type == ProfileType.URL }) {
        IconButton(
            onClick = { onAction(ProfilesToolbarAction.SYNC_ALL) },
            enabled = canMutate,
            modifier = Modifier.testTag("profiles_sync_all"),
        ) {
            Icon(Icons.Outlined.Sync, contentDescription = "同步全部配置")
        }
    }
    if (profiles.isNotEmpty()) {
        IconButton(
            onClick = { onAction(ProfilesToolbarAction.SORT) },
            enabled = canMutate,
            modifier = Modifier.testTag("profiles_sort"),
        ) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "排序") }
    }
    IconButton(
        onClick = onAdd,
        enabled = !busy,
        modifier = Modifier.testTag("profiles_add_fab"),
    ) { Icon(Icons.Outlined.Add, contentDescription = "添加配置") }
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = !busy,
            modifier = Modifier.testTag("profiles_tools"),
        ) { Icon(Icons.Outlined.MoreVert, contentDescription = "配置工具") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("脚本") },
                leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                onClick = { expanded = false; onAction(ProfilesToolbarAction.SCRIPTS) },
            )
            DropdownMenuItem(
                text = { Text("WebDAV") },
                leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                onClick = { expanded = false; onAction(ProfilesToolbarAction.WEBDAV) },
                modifier = Modifier.testTag("webdav_open"),
            )
            DropdownMenuItem(
                text = { Text("备份") },
                leadingIcon = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                onClick = { expanded = false; onAction(ProfilesToolbarAction.BACKUP_EXPORT) },
                modifier = Modifier.testTag("backup_export"),
            )
            DropdownMenuItem(
                text = { Text("恢复") },
                leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                onClick = { expanded = false; onAction(ProfilesToolbarAction.BACKUP_RESTORE) },
                modifier = Modifier.testTag("backup_restore"),
                enabled = canMutate,
            )
        }
    }
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
    val outboundMode by runtimeViewModel.outboundMode.collectAsState()
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
    val remoteEditorState by profilesViewModel.remoteEditorState.collectAsState()
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
    var dashboardLayout by remember(context) { mutableStateOf(DashboardLayoutStore.load(context)) }
    val dashboardListState = rememberLazyListState()
    val pageListState = rememberLazyListState()
    val density = LocalDensity.current
    var isDashboardEditing by rememberSaveable { mutableStateOf(false) }
    var dashboardDraggingSection by remember { mutableStateOf<DashboardSection?>(null) }
    var dashboardDragY by remember { mutableStateOf(0f) }
    val dashboardSwayProgress = rememberDashboardEditSwayProgress(isDashboardEditing)
    val selectedProfile = profilesState.profiles.firstOrNull { it.id == profilesState.currentProfileId }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showRequests by rememberSaveable { mutableStateOf(false) }
    var showDns by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticHistory by rememberSaveable { mutableStateOf(false) }
    var showScriptsDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoteScriptDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDestinationIndex by rememberSaveable { mutableStateOf(MainDestination.DASHBOARD.ordinal) }
    var toolsSubpage by rememberSaveable { mutableStateOf(ToolsSubpage.MENU) }
    var showAccessUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    var pendingAccessExitDestination by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingAccessExitFinishes by rememberSaveable { mutableStateOf(false) }
    var pendingAccessExitOpenAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingProfilesToolbarAction by remember { mutableStateOf<ProfilesToolbarAction?>(null) }
    // 作者 long：观测页会随顶层导航离开组合，页签状态必须提升到主页面保存，避免用户返回观测时丢失当前页签。
    var selectedObservationTabIndex by rememberSaveable { mutableStateOf(ObservationTab.REQUESTS.ordinal) }
    val showProxyDestination = profilesState.profiles.isNotEmpty() || proxyGroupsState.groups.isNotEmpty()
    val navigationDestinations = mainDestinations(showProxyDestination)
    val requestedDestination = MainDestination.values()[
        selectedDestinationIndex.coerceIn(0, MainDestination.values().lastIndex)
    ]
    val selectedDestination = if (!showProxyDestination && requestedDestination == MainDestination.PROXY) {
        MainDestination.DASHBOARD
    } else {
        requestedDestination
    }

    LaunchedEffect(showProxyDestination) {
        if (!showProxyDestination && selectedDestinationIndex == MainDestination.PROXY.ordinal) {
            selectedDestinationIndex = MainDestination.DASHBOARD.ordinal
        }
    }

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

    fun requestAccessExit(destination: MainDestination? = null, openAddDialog: Boolean = false) {
        pendingAccessExitDestination = destination?.ordinal
        pendingAccessExitFinishes = destination == null
        pendingAccessExitOpenAddDialog = openAddDialog
        showAccessUnsavedChanges = true
    }

    fun completeAccessExit() {
        val destination = pendingAccessExitDestination
        val finishes = pendingAccessExitFinishes
        val openAddDialog = pendingAccessExitOpenAddDialog
        pendingAccessExitDestination = null
        pendingAccessExitFinishes = false
        pendingAccessExitOpenAddDialog = false
        showAccessUnsavedChanges = false
        showAddDialog = openAddDialog
        if (destination != null) selectedDestinationIndex = destination
        if (finishes) onExitApp()
    }

    fun cancelAccessExit() {
        pendingAccessExitDestination = null
        pendingAccessExitFinishes = false
        pendingAccessExitOpenAddDialog = false
        showAccessUnsavedChanges = false
    }

    fun navigateTo(destination: MainDestination, openAddDialog: Boolean = false) {
        if (destination == MainDestination.TOOLS) toolsSubpage = ToolsSubpage.MENU
        if (destination != selectedDestination &&
            selectedDestination == MainDestination.DASHBOARD &&
            accessControlState.hasUnsavedChanges
        ) {
            // 作者 long：卡片和 Dock 离开首页共用未保存保护，避免新增快捷入口绕过 Access 的草稿确认。
            requestAccessExit(destination, openAddDialog)
        } else {
            if (selectedDestination == MainDestination.DASHBOARD && destination != MainDestination.DASHBOARD) {
                isDashboardEditing = false
                dashboardDraggingSection = null
            }
            selectedDestinationIndex = destination.ordinal
            showAddDialog = openAddDialog && destination == MainDestination.PROFILES
        }
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
    BackHandler(enabled = selectedDestination == MainDestination.DASHBOARD && isDashboardEditing) {
        isDashboardEditing = false
        dashboardDraggingSection = null
    }
    BackHandler(enabled = selectedDestination == MainDestination.TOOLS && toolsSubpage != ToolsSubpage.MENU) {
        toolsSubpage = ToolsSubpage.MENU
    }

    MainPageScaffold(
        selectedDestination = selectedDestination,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        onDestinationSelected = ::navigateTo,
        destinations = navigationDestinations,
        topBarTitle = if (selectedDestination == MainDestination.TOOLS && toolsSubpage != ToolsSubpage.MENU) {
            when (toolsSubpage) {
                ToolsSubpage.CONNECTIONS -> "连接"
                ToolsSubpage.OBSERVATIONS -> "观测"
                ToolsSubpage.MENU -> "工具"
            }
        } else {
            null
        },
        topBarNavigationIcon = if (selectedDestination == MainDestination.TOOLS && toolsSubpage != ToolsSubpage.MENU) {
            {
                IconButton(onClick = { toolsSubpage = ToolsSubpage.MENU }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回工具")
                }
            }
        } else {
            null
        },
        topBarActions = {
            if (selectedDestination == MainDestination.DASHBOARD) {
                IconButton(
                    onClick = {
                        isDashboardEditing = !isDashboardEditing
                        dashboardDraggingSection = null
                    },
                    modifier = Modifier.testTag("dashboard_edit_layout"),
                ) {
                    Icon(
                        imageVector = if (isDashboardEditing) Icons.Outlined.Check else Icons.Outlined.Edit,
                        contentDescription = if (isDashboardEditing) "完成编辑" else "编辑首页",
                    )
                }
            } else if (selectedDestination == MainDestination.PROFILES) {
                ProfilesTopBarActions(
                    profiles = profilesState.profiles,
                    busy = profilesState.isBusy,
                    canMutate = !profilesState.isBusy && !runtimeState.isRunning,
                    onAdd = { showAddDialog = true },
                    onAction = { pendingProfilesToolbarAction = it },
                )
            }
        },
    ) { paddingValues ->
        DashboardWidth { dashboardInset ->
            if (selectedDestination == MainDestination.PROXY) {
                // 作者 long：代理页占用 Scaffold 的剩余高度，让组内网格独立滚动，避免套入其他页面的 LazyColumn。
                ProxyGroupsCard(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = dashboardInset)
                        .testTag("page_proxy"),
                    state = proxyGroupsState,
                    runtimeState = runtimeState,
                    mode = outboundMode,
                    currentProfileId = selectedProfile?.id,
                    currentGroupName = selectedProfile?.currentGroupName,
                    onRefresh = proxyGroupsViewModel::refresh,
                    onSelectGroup = proxyGroupsViewModel::selectGroup,
                    onToggleGroup = proxyGroupsViewModel::toggleGroupExpanded,
                    onSelect = proxyGroupsViewModel::selectProxy,
                    onTestDelay = proxyGroupsViewModel::testDelay,
                    onTestGroupDelay = proxyGroupsViewModel::testDelayGroup,
                )
            } else {
                LazyColumn(
                    state = if (selectedDestination == MainDestination.DASHBOARD) dashboardListState else pageListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = if (selectedDestination == MainDestination.DASHBOARD) dashboardInset else 16.dp)
                        .testTag("page_${selectedDestination.name.lowercase()}"),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
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
                        items(
                            items = dashboardLayout.visibleInOrder(),
                            key = { it.name },
                        ) { section ->
                            val content: @Composable () -> Unit = {
                                when (section) {
                                DashboardSection.NETWORK_STATS -> RuntimeStatsCard(
                                    state = runtimeStats,
                                    runtimeState = runtimeState,
                                )
                                DashboardSection.OUTBOUND_MODE -> OutboundModeCard(
                                    mode = outboundMode,
                                    onModeChange = { mode ->
                                        runtimeViewModel.setOutboundMode(mode)
                                        if (mode == CoreOutboundMode.GLOBAL) {
                                            proxyGroupsViewModel.selectGlobalGroup()
                                        }
                                    },
                                )
                                DashboardSection.PROFILES -> DashboardProfileCard(
                                    profile = selectedProfile,
                                    profiles = profilesState.profiles,
                                    currentProfileId = profilesState.currentProfileId,
                                    isBusy = profilesState.isBusy,
                                    runtimeState = runtimeState,
                                    onShowProfiles = { navigateTo(MainDestination.PROFILES) },
                                    onAddProfile = {
                                        navigateTo(MainDestination.PROFILES, openAddDialog = true)
                                    },
                                    onSelectProfile = profilesViewModel::select,
                                    onUpdateProfile = profilesViewModel::update,
                                    onStopRuntime = onStopRuntime,
                                )
                                DashboardSection.PROXY_GROUPS -> DashboardProxyGroupsCard(
                                    state = proxyGroupsState,
                                    runtimeState = runtimeState,
                                    mode = outboundMode,
                                    currentProfileId = selectedProfile?.id,
                                    currentGroupName = selectedProfile?.currentGroupName,
                                    onRefresh = proxyGroupsViewModel::refresh,
                                    onSelectGroup = proxyGroupsViewModel::selectGroup,
                                    onToggleGroup = proxyGroupsViewModel::toggleGroupExpanded,
                                    onSelect = proxyGroupsViewModel::selectProxy,
                                    onTestDelay = proxyGroupsViewModel::testDelay,
                                    onTestGroupDelay = proxyGroupsViewModel::testDelayGroup,
                                )
                                DashboardSection.BATTERY_OPTIMIZATION -> BatteryOptimizationCard(
                                    ignored = batteryOptimizationIgnored,
                                    onRequest = onRequestBatteryOptimization,
                                )
                                DashboardSection.ALWAYS_ON_VPN -> AlwaysOnVpnCard(
                                    state = alwaysOnVpnState,
                                    onRequest = onRequestAlwaysOnVpn,
                                )
                                DashboardSection.ACCESS_CONTROL -> AccessControlCard(
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
                                DashboardSection.PROBE -> ProbeCard(
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
                            if (isDashboardEditing) {
                                DashboardEditableSection(
                                    section = section,
                                    swayProgress = dashboardSwayProgress,
                                    onRemove = {
                                        dashboardLayout = dashboardLayout.setVisible(section, false)
                                        DashboardLayoutStore.save(context, dashboardLayout)
                                    },
                                    onDragStart = { localY ->
                                        val item = dashboardListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == section.name }
                                        if (item != null) {
                                            dashboardDraggingSection = section
                                            dashboardDragY = item.offset + with(density) { 4.dp.toPx() } + localY
                                        }
                                    },
                                    onDragBy = { deltaY ->
                                        if (dashboardDraggingSection == section) {
                                            dashboardDragY += deltaY
                                            val listInfo = dashboardListState.layoutInfo
                                            val sectionItems = listInfo.visibleItemsInfo.filter { item ->
                                                DashboardSection.entries.any { it.name == item.key }
                                            }
                                            val target = sectionItems.minByOrNull { item ->
                                                abs(item.offset + item.size / 2f - dashboardDragY)
                                            }?.key?.let { key ->
                                                DashboardSection.entries.firstOrNull { it.name == key }
                                            }
                                            if (target != null && target != section) {
                                                val changed = dashboardLayout.reorderVisible(section, target)
                                                if (changed != dashboardLayout) {
                                                    dashboardLayout = changed
                                                    DashboardLayoutStore.save(context, changed)
                                                }
                                            }
                                            // 作者 long：拖拽接近视口边缘时持续滚动，长列表仍可把卡片放到屏幕外的目标位置。
                                            val edge = with(density) { 56.dp.toPx() }
                                            when {
                                                dashboardDragY < listInfo.viewportStartOffset + edge ->
                                                    coroutineScope.launch { dashboardListState.scrollBy(-18f) }
                                                dashboardDragY > listInfo.viewportEndOffset - edge ->
                                                    coroutineScope.launch { dashboardListState.scrollBy(18f) }
                                            }
                                        }
                                    },
                                    onDragEnd = { dashboardDraggingSection = null },
                                    modifier = Modifier.animateItem(),
                                    content = content,
                                )
                            } else {
                                content()
                            }
                        }
                        if (isDashboardEditing) {
                            val addable = DashboardSection.entries.filterNot { it in dashboardLayout.visible }
                            if (addable.isNotEmpty()) {
                                item(key = "dashboard_addable_header") { DashboardAddableSectionHeader() }
                                items(addable, key = { "dashboard_add_${it.name}" }) { section ->
                                    DashboardAddableSectionRow(
                                        section = section,
                                        modifier = Modifier.animateItem(),
                                    ) {
                                        dashboardLayout = dashboardLayout.setVisible(section, true)
                                        DashboardLayoutStore.save(context, dashboardLayout)
                                    }
                                }
                            }
                        }
                    }

                    MainDestination.PROFILES -> item {
                        ProfilesPanel(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
                            state = profilesState,
                            editorState = editorState,
                            remoteEditorState = remoteEditorState,
                            runtimeState = runtimeState,
                            showAddDialog = showAddDialog,
                            toolbarAction = pendingProfilesToolbarAction,
                            onToolbarActionHandled = { pendingProfilesToolbarAction = null },
                            showInlineToolbar = false,
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
                            onAddRemoteBatch = { urls ->
                                showAddDialog = false
                                profilesViewModel.addRemoteBatch(urls)
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
                            onDiscardEditor = profilesViewModel::discardEditor,
                    onFinishNewDraftEditor = profilesViewModel::finishNewDraftEditor,
                            onSaveEditor = profilesViewModel::saveEditor,
                            onSaveRemoteEditor = profilesViewModel::saveRemoteEditor,
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

                    MainDestination.PROXY -> Unit

                    MainDestination.TOOLS -> when (toolsSubpage) {
                        ToolsSubpage.MENU -> item {
                            ToolsPanel(
                                onOpenConnections = { toolsSubpage = ToolsSubpage.CONNECTIONS },
                                onOpenObservation = { tab ->
                                    selectedObservationTabIndex = tab.ordinal
                                    toolsSubpage = ToolsSubpage.OBSERVATIONS
                                },
                                onShowUnavailable = { label ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar("${label}页面正在按 Flutter 参考实现补齐") }
                                },
                            )
                        }
                        ToolsSubpage.CONNECTIONS -> item {
                            Box(Modifier.fillMaxWidth().testTag("page_connections")) {
                                ConnectionsCard(
                                    state = runtimeConnections,
                                    runtimeState = runtimeState,
                                    onCopyText = { value ->
                                        checkNotNull(context.getSystemService(ClipboardManager::class.java))
                                            .setPrimaryClip(ClipData.newPlainText("KClash 连接详情", value))
                                    },
                                )
                            }
                        }
                        ToolsSubpage.OBSERVATIONS -> item {
                            Box(Modifier.fillMaxWidth().testTag("page_observations")) {
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
                    }
                }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    if (showRequests) {
        RequestsDialog(
            state = runtimeRequests,
            onDismiss = { showRequests = false },
            onCopyText = { value ->
                checkNotNull(context.getSystemService(ClipboardManager::class.java))
                    .setPrimaryClip(ClipData.newPlainText("KClash 请求详情", value))
            },
        )
    }

    if (showDns) {
        DnsDialog(
            state = runtimeDns,
            onDismiss = { showDns = false },
            onClearDnsCache = runtimeViewModel::clearDnsCache,
            onCopyText = { value ->
                checkNotNull(context.getSystemService(ClipboardManager::class.java))
                    .setPrimaryClip(ClipData.newPlainText("KClash DNS 详情", value))
            },
        )
    }

    if (showLogs) {
        LogsDialog(
            state = runtimeLogs,
            onDismiss = { showLogs = false },
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

    Column(modifier = Modifier.background(recordPageColor()).testTag("observation_page")) {
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
    modifier: Modifier = Modifier,
    state: ProfilesUiState,
    editorState: ProfileEditorUiState,
    remoteEditorState: RemoteProfileEditorUiState = RemoteProfileEditorUiState(),
    runtimeState: CoreRuntimeStatus,
    showAddDialog: Boolean,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onPickFile: () -> Unit,
    onPickQrCode: () -> Unit,
    onAddRemote: (label: String, url: String) -> Unit,
    onAddRemoteBatch: ((List<String>) -> Unit)? = null,
    onAddCustom: (label: String) -> Unit,
    onSelect: (id: Long) -> Unit,
    onEdit: (profile: Profile) -> Unit,
    onRename: (profile: Profile, label: String) -> Unit = { _, _ -> },
    onUpdate: (profile: Profile) -> Unit,
    onSetAutoUpdate: (profile: Profile, enabled: Boolean) -> Unit = { _, _ -> },
    onCloseEditor: () -> Unit,
    onDiscardEditor: () -> Unit = onCloseEditor,
    onFinishNewDraftEditor: (String?) -> Unit = { onDiscardEditor() },
    onSaveEditor: (content: String) -> Unit,
    onSaveRemoteEditor: (label: String, url: String, autoUpdate: Boolean, intervalMinutes: Long) -> Unit = { _, _, _, _ -> },
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
    toolbarAction: ProfilesToolbarAction? = null,
    onToolbarActionHandled: () -> Unit = {},
    showInlineToolbar: Boolean = true,
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
    var inlineToolbarAction by remember { mutableStateOf<ProfilesToolbarAction?>(null) }

    val actionToHandle = toolbarAction ?: inlineToolbarAction
    LaunchedEffect(actionToHandle) {
        when (actionToHandle) {
            ProfilesToolbarAction.SYNC_ALL -> state.profiles.filter { it.type == ProfileType.URL }.forEach(onUpdate)
            ProfilesToolbarAction.SORT -> showReorderDialog = true
            ProfilesToolbarAction.SCRIPTS -> onOpenScripts()
            ProfilesToolbarAction.WEBDAV -> showWebDavDialog = true
            ProfilesToolbarAction.BACKUP_EXPORT -> onExportBackup()
            ProfilesToolbarAction.BACKUP_RESTORE -> showBackupRestoreDialog = true
            null -> Unit
        }
        if (toolbarAction != null) onToolbarActionHandled()
        else if (inlineToolbarAction != null) inlineToolbarAction = null
    }

    Column(
        modifier = modifier.testTag("profiles_page"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 作者 long：Debug 面板单独渲染时保留同一组操作，正式页面则由 MainPageScaffold 顶栏承载。
        if (showInlineToolbar) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ProfilesTopBarActions(
                    profiles = state.profiles,
                    busy = state.isBusy,
                    canMutate = canMutateProfiles,
                    onAdd = onShowAddDialog,
                    onAction = { inlineToolbarAction = it },
                )
            }
        }
        if (state.profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = ProfileIllustrationShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {}
                        Surface(
                            modifier = Modifier.size(96.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 18.dp).size(14.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                        ) {}
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 30.dp).size(9.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {}
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("暂无配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "添加一个配置文件后即可开始使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.FilledTonalButton(onClick = onShowAddDialog) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("添加配置")
                    }
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
            onAddRemoteBatch = onAddRemoteBatch ?: { urls -> urls.forEach { onAddRemote("", it) } },
            existingUrls = state.profiles
                .asSequence()
                .filter { it.type == ProfileType.URL }
                .map(Profile::url)
                .toSet(),
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
                isNewDraft = editorState.isNew,
                hasPersistedChanges = editorState.hasPersistedChanges,
                onDismiss = onCloseEditor,
                onDiscard = onDiscardEditor,
                onFinishNewDraft = onFinishNewDraftEditor,
                onSave = onSaveEditor,
                onOpenRulesEditor = onOpenRulesEditor,
                onOpenProxiesEditor = onOpenProxiesEditor,
                onOpenProxyGroupsEditor = onOpenProxyGroupsEditor,
                onOpenProvidersEditor = onOpenProvidersEditor,
                onOpenDnsEditor = onOpenDnsEditor,
                onOpenDialersEditor = onOpenDialersEditor,
            )
        }
    }

    remoteEditorState.profile?.let { profile ->
        RemoteProfileEditorDialog(
            profile = profile,
            saving = remoteEditorState.isSaving,
            onDismiss = onCloseEditor,
            onSave = onSaveRemoteEditor,
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxyGroupsCard(
    modifier: Modifier = Modifier,
    state: ProxyGroupsUiState,
    runtimeState: CoreRuntimeStatus,
    currentProfileId: Long? = null,
    currentGroupName: String? = null,
    mode: CoreOutboundMode = CoreOutboundMode.GLOBAL,
    picker: Boolean = false,
    onContentAtTopChanged: (Boolean) -> Unit = {},
    onRefresh: () -> Unit,
    onSelectGroup: (String) -> Unit = {},
    onToggleGroup: (String) -> Unit = {},
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestGroupDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo) -> Unit,
) {
    val optionsContext = LocalContext.current.applicationContext
    var pageOptions by remember(optionsContext) {
        mutableStateOf(ProxyPageOptionsStore.load(optionsContext))
    }
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }
    var toolsMenuPage by rememberSaveable { mutableStateOf(ProxyToolsMenuPage.ROOT) }
    var locateSelectedRequest by rememberSaveable(currentProfileId) { mutableStateOf(0) }
    val groups = visibleProxyGroups(state.groups, mode)
    val groupNames = groups.map { it.name }
    val initialPage = groupNames.indexOf(currentGroupName).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { groups.size }
    val groupTabsState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var searchExpanded by rememberSaveable(currentProfileId) { mutableStateOf(picker) }
    var proxyQuery by rememberSaveable(currentProfileId) { mutableStateOf("") }

    LaunchedEffect(currentProfileId, currentGroupName, groupNames) {
        if (groupNames.isNotEmpty()) {
            val savedIndex = groupNames.indexOf(currentGroupName)
            val page = if (savedIndex >= 0) savedIndex else pagerState.currentPage.coerceIn(groupNames.indices)
            if (pagerState.currentPage != page) pagerState.scrollToPage(page)
        }
    }
    LaunchedEffect(pagerState, groupNames) {
        // 作者 long：只在组切换稳定后写入 Profile，取消中的滑动不应改变下次恢复的分组。
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> groups.getOrNull(page)?.name?.let(onSelectGroup) }
    }
    val selectedGroupIndex = if (groupNames.isEmpty()) 0 else pagerState.currentPage.coerceIn(groupNames.indices)
    val selectedGroup = groups.getOrNull(selectedGroupIndex)
    val canRefresh = runtimeState == CoreRuntimeStatus.RUNNING &&
        !state.isLoading &&
        state.switchingGroup == null &&
        state.batchTestingGroup == null
    fun updatePageOptions(updated: ProxyPageOptions) {
        pageOptions = updated
        ProxyPageOptionsStore.save(optionsContext, updated)
    }
    Column(
        modifier = modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp)
            .testTag("proxy_groups_page"),
        verticalArrangement = Arrangement.spacedBy(if (picker) 8.dp else 12.dp),
    ) {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        if (runtimeState == CoreRuntimeStatus.RUNNING) {
                            "当前配置没有可展示的代理组。"
                        } else {
                            "启动核心后加载代理组。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            if (pageOptions.displayMode == ProxyDisplayMode.TAB) {
                LazyRow(
                    state = groupTabsState,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("proxy_group_tabs"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(groups, key = { _, group -> group.name }) { index, group ->
                        val selected = index == selectedGroupIndex
                        Surface(
                            modifier = Modifier.widthIn(min = 64.dp).height(48.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                )
                                .testTag("proxy_group_tab_${group.name}"),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            selectedGroup?.let { group ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val displayedProxy = resolveSelectedProxy(
                        group = group,
                        groups = state.groups,
                        selectedMap = state.selectedMap,
                    ).leafName ?: selectedProxyName(group, state.selectedMap)
                    Text(
                        "${group.type} · 当前：${displayedProxy ?: "未选择"}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (picker && pageOptions.displayMode == ProxyDisplayMode.TAB) {
                        // 作者 long：弹层高度有限，测速复用摘要行，避免键盘出现后独立工具行挤掉可操作的节点。
                        IconButton(
                            onClick = { onTestGroupDelay(group) },
                            enabled = runtimeState == CoreRuntimeStatus.RUNNING &&
                                state.switchingGroup == null && state.batchTestingGroup == null && group.all.isNotEmpty(),
                            modifier = Modifier.testTag("proxy_group_batch_${group.name}"),
                        ) {
                            if (state.batchTestingGroup == group.name) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Bolt, contentDescription = "批量测速")
                        }
                    }
                    if (!picker) {
                        IconButton(
                            onClick = {
                                if (searchExpanded) proxyQuery = ""
                                searchExpanded = !searchExpanded
                            },
                            modifier = Modifier.testTag("proxy_group_search_toggle"),
                        ) {
                            Icon(
                                if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = if (searchExpanded) "关闭节点搜索" else "搜索节点",
                            )
                        }
                        IconButton(
                            onClick = { locateSelectedRequest++ },
                            enabled = group.all.isNotEmpty(),
                            modifier = Modifier.testTag("proxy_scroll_to_selected"),
                        ) {
                            Icon(Icons.Outlined.MyLocation, contentDescription = "定位当前节点")
                        }
                        Box {
                            IconButton(
                                onClick = {
                                    toolsMenuPage = ProxyToolsMenuPage.ROOT
                                    toolsExpanded = true
                                },
                                modifier = Modifier.testTag("proxy_tools"),
                            ) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "代理组工具")
                            }
                            DropdownMenu(
                                expanded = toolsExpanded,
                                onDismissRequest = { toolsExpanded = false },
                            ) {
                                when (toolsMenuPage) {
                                    ProxyToolsMenuPage.ROOT -> {
                                        DropdownMenuItem(
                                            text = { Text("布局：${pageOptions.displayMode.label}") },
                                            onClick = { toolsMenuPage = ProxyToolsMenuPage.DISPLAY_MODE },
                                            modifier = Modifier.testTag("proxy_tools_display_mode"),
                                        )
                                        DropdownMenuItem(
                                            text = { Text("排序：${pageOptions.sort.label}") },
                                            onClick = { toolsMenuPage = ProxyToolsMenuPage.SORT },
                                            modifier = Modifier.testTag("proxy_tools_sort"),
                                        )
                                        DropdownMenuItem(
                                            text = { Text("网格密度：${pageOptions.gridDensity.label}") },
                                            onClick = { toolsMenuPage = ProxyToolsMenuPage.DENSITY },
                                            modifier = Modifier.testTag("proxy_tools_density"),
                                        )
                                        DropdownMenuItem(
                                            text = { Text("卡片尺寸：${pageOptions.cardSize.label}") },
                                            onClick = { toolsMenuPage = ProxyToolsMenuPage.CARD_SIZE },
                                            modifier = Modifier.testTag("proxy_tools_card_size"),
                                        )
                                        if (pageOptions.displayMode == ProxyDisplayMode.LIST) {
                                            DropdownMenuItem(
                                                text = { Text("列表图标：${pageOptions.iconStyle.label}") },
                                                onClick = { toolsMenuPage = ProxyToolsMenuPage.ICON_STYLE },
                                                modifier = Modifier.testTag("proxy_tools_icon_style"),
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("隐藏超时节点") },
                                            onClick = { updatePageOptions(pageOptions.copy(hideTimeout = !pageOptions.hideTimeout)) },
                                            trailingIcon = {
                                                if (pageOptions.hideTimeout) Icon(Icons.Outlined.Check, contentDescription = "已启用")
                                            },
                                            modifier = Modifier.testTag("proxy_tools_hide_timeout"),
                                        )
                                    }
                                    ProxyToolsMenuPage.DISPLAY_MODE -> {
                                        ProxyMenuBackItem("布局") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                        ProxyDisplayMode.entries.forEach { option ->
                                            ProxyMenuOptionItem(option.label, option == pageOptions.displayMode, "proxy_display_mode_${option.name}") {
                                                updatePageOptions(pageOptions.copy(displayMode = option))
                                                toolsExpanded = false
                                            }
                                        }
                                    }
                                    ProxyToolsMenuPage.SORT -> {
                                        ProxyMenuBackItem("排序") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                        ProxySortOption.entries.forEach { option ->
                                            ProxyMenuOptionItem(option.label, option == pageOptions.sort, "proxy_sort_option_${option.name}") {
                                                updatePageOptions(pageOptions.copy(sort = option))
                                                toolsExpanded = false
                                            }
                                        }
                                    }
                                    ProxyToolsMenuPage.DENSITY -> {
                                        ProxyMenuBackItem("网格密度") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                        ProxyGridDensity.entries.forEach { option ->
                                            ProxyMenuOptionItem(option.label, option == pageOptions.gridDensity, "proxy_density_option_${option.name}") {
                                                updatePageOptions(pageOptions.copy(gridDensity = option))
                                                toolsExpanded = false
                                            }
                                        }
                                    }
                                    ProxyToolsMenuPage.CARD_SIZE -> {
                                        ProxyMenuBackItem("卡片尺寸") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                        ProxyNodeCardSize.entries.forEach { option ->
                                            ProxyMenuOptionItem(option.label, option == pageOptions.cardSize, "proxy_card_size_option_${option.name}") {
                                                updatePageOptions(pageOptions.copy(cardSize = option))
                                                toolsExpanded = false
                                            }
                                        }
                                    }
                                    ProxyToolsMenuPage.ICON_STYLE -> {
                                        ProxyMenuBackItem("列表图标") { toolsMenuPage = ProxyToolsMenuPage.ROOT }
                                        ProxyGroupIconStyle.entries.forEach { option ->
                                            ProxyMenuOptionItem(option.label, option == pageOptions.iconStyle, "proxy_icon_style_${option.name}") {
                                                updatePageOptions(pageOptions.copy(iconStyle = option))
                                                toolsExpanded = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (searchExpanded && !picker) {
                    OutlinedTextField(
                        value = proxyQuery,
                        onValueChange = { proxyQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .testTag("proxy_group_search"),
                        label = { Text("搜索节点") },
                        singleLine = true,
                    )
                }
                if (!picker) Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!picker) TextButton(
                        onClick = onRefresh,
                        enabled = canRefresh,
                        modifier = Modifier.testTag("proxy_groups_refresh"),
                    ) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("刷新")
                    }
                    TextButton(
                        onClick = { onTestGroupDelay(group) },
                        enabled = runtimeState == CoreRuntimeStatus.RUNNING &&
                            state.switchingGroup == null && state.batchTestingGroup == null && group.all.isNotEmpty(),
                        modifier = Modifier.testTag("proxy_group_batch_${group.name}"),
                    ) {
                        if (state.batchTestingGroup == group.name) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("批量测速")
                    }
                }
            }

            if (pageOptions.displayMode == ProxyDisplayMode.TAB) {
                HorizontalPager(
                    state = pagerState,
                    key = { page -> groupNames[page] },
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("proxy_groups_pager"),
                ) { page ->
                    val group = groups.getOrNull(page) ?: return@HorizontalPager
                    ProxyGroupNodesPage(
                        group = group,
                        groups = state.groups,
                        state = state,
                        query = proxyQuery,
                        options = pageOptions,
                        locateRequest = locateSelectedRequest,
                        isCurrentPage = page == pagerState.currentPage,
                        onSelect = onSelect,
                        onTestDelay = onTestDelay,
                        onContentAtTopChanged = onContentAtTopChanged,
                    )
                }
            } else {
                ProxyGroupsListPage(
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("proxy_groups_list"),
                    groups = groups,
                    state = state,
                    runtimeState = runtimeState,
                    query = proxyQuery,
                    options = pageOptions,
                    onToggleGroup = onToggleGroup,
                    onSelect = onSelect,
                    onTestDelay = onTestDelay,
                    onTestGroupDelay = onTestGroupDelay,
                    onContentAtTopChanged = onContentAtTopChanged,
                )
            }
            if (picker) {
                // 作者 long：弹层搜索单独留在当前组合中，关闭即释放，避免覆盖完整代理页原有查询。
                OutlinedTextField(
                    value = proxyQuery,
                    onValueChange = { proxyQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .testTag("proxy_group_search"),
                    label = { Text("搜索节点") },
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun ProxyGroupsListPage(
    modifier: Modifier,
    groups: List<com.lonnnnnng.flclash.core.ProxyGroupInfo>,
    state: ProxyGroupsUiState,
    runtimeState: CoreRuntimeStatus,
    query: String,
    options: ProxyPageOptions,
    onToggleGroup: (String) -> Unit,
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestGroupDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo) -> Unit,
    onContentAtTopChanged: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged().collect { onContentAtTopChanged(it) }
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(groups, key = { it.name }) { group ->
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("proxy_list_group_${group.name}")) {
                // 作者 long：隐藏组只影响可见分组，递归叶子、排序和延迟仍需完整核心组图。
                val searched = filterProxyMembers(group, query)
                val sorted = sortProxyMembers(group, state.groups, state, searched, options.sort)
                val members = if (options.hideTimeout) {
                    hideTimedOutProxyMembers(group, state.groups, state, sorted)
                } else {
                    sorted
                }
                val columns = proxyGridColumnCount(maxWidth.value, options.gridDensity)
                val selected = selectedProxyName(group, state.selectedMap)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggleGroup(group.name) }
                            .testTag("proxy_list_group_header_${group.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProxyGroupIcon(group.name, options.iconStyle)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${group.type} · 当前：${resolveSelectedProxy(group, state.groups, state.selectedMap).leafName ?: selected ?: "未选择"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = { onTestGroupDelay(group) },
                            enabled = runtimeState == CoreRuntimeStatus.RUNNING &&
                                state.switchingGroup == null && state.batchTestingGroup == null && group.all.isNotEmpty(),
                            modifier = Modifier.testTag("proxy_list_group_batch_${group.name}"),
                        ) { Text("批量测速") }
                        IconButton(
                            onClick = { onToggleGroup(group.name) },
                            modifier = Modifier.testTag("proxy_list_group_toggle_${group.name}"),
                        ) {
                            val expanded = group.name in state.unfoldSet
                            Icon(
                                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (expanded) "收起 ${group.name}" else "展开 ${group.name}",
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = group.name in state.unfoldSet,
                        enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(120)),
                        exit = shrinkVertically(animationSpec = tween(240)) + fadeOut(animationSpec = tween(120)),
                    ) {
                        if (members.isEmpty()) {
                            Text(
                                if (query.isBlank()) "该组没有可用节点" else "没有匹配的节点",
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            members.chunked(columns).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    row.forEach { proxyName ->
                                        ProxyNodeCard(
                                            name = proxyName,
                                            type = state.groups.firstOrNull { it.name == proxyName }?.type,
                                            cardSize = options.cardSize,
                                            inUse = proxyName == selected,
                                            preferred = group.isComputed && proxyName == state.selectedMap[group.name],
                                            enabled = runtimeState == CoreRuntimeStatus.RUNNING &&
                                                group.isSelectable && state.switchingGroup == null && state.batchTestingGroup == null,
                                            delay = state.delayState(group, proxyName),
                                            selectionTag = "proxy_list_node_${group.name}_$proxyName",
                                            delayTag = "proxy_list_node_delay_${group.name}_$proxyName",
                                            onSelect = { onSelect(group, proxyName) },
                                            onTestDelay = { onTestDelay(group, proxyName) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyGroupIcon(name: String, style: ProxyGroupIconStyle) {
    when (style) {
        ProxyGroupIconStyle.HIDDEN -> Spacer(Modifier.size(32.dp))
        ProxyGroupIconStyle.PLAIN -> Text(
            name.firstOrNull()?.uppercase() ?: "?",
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        ProxyGroupIconStyle.FILLED -> Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ProxyMenuBackItem(label: String, onBack: () -> Unit) {
    DropdownMenuItem(
        text = { Text("返回：$label") },
        onClick = onBack,
        modifier = Modifier.testTag("proxy_tools_back"),
    )
}

@Composable
private fun ProxyMenuOptionItem(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (isSelected) Icon(Icons.Outlined.Check, contentDescription = "已选择")
        },
        modifier = Modifier.testTag(testTag).semantics { selected = isSelected },
    )
}

@Composable
private fun ProxyGroupNodesPage(
    group: com.lonnnnnng.flclash.core.ProxyGroupInfo,
    groups: List<com.lonnnnnng.flclash.core.ProxyGroupInfo>,
    state: ProxyGroupsUiState,
    query: String,
    options: ProxyPageOptions,
    locateRequest: Int,
    isCurrentPage: Boolean,
    onSelect: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onTestDelay: (com.lonnnnnng.flclash.core.ProxyGroupInfo, String) -> Unit,
    onContentAtTopChanged: (Boolean) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, isCurrentPage) {
        if (isCurrentPage) snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }.distinctUntilChanged().collect { onContentAtTopChanged(it) }
    }
    val searchedProxies = filterProxyMembers(group, query)
    val sortedProxies = sortProxyMembers(group, groups, state, searchedProxies, options.sort)
    val visibleProxies = if (options.hideTimeout) {
        hideTimedOutProxyMembers(group, groups, state, sortedProxies)
    } else {
        sortedProxies
    }
    val savedSelection = selectedProxyName(group, state.selectedMap)
    val inUseName = if (group.isComputed) {
        group.now?.takeIf { it in group.all } ?: savedSelection
    } else {
        savedSelection
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().testTag("proxy_group_${group.name}"),
    ) {
        // 作者 long：按密度选项调整 Flutter 同源的 250dp 网格列宽，宽松模式允许单列卡片。
        val columnCount = proxyGridColumnCount(maxWidth.value, options.gridDensity)
        LaunchedEffect(isCurrentPage, inUseName, query, visibleProxies, locateRequest) {
            val selectedIndex = if (query.isBlank()) visibleProxies.indexOf(inUseName) else -1
            if (isCurrentPage && selectedIndex > 0) gridState.animateScrollToItem(selectedIndex)
        }
        if (visibleProxies.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("proxy_group_members_${group.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (query.isBlank()) "该组没有可用节点" else "没有匹配的节点",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("proxy_group_members_${group.name}"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(visibleProxies, key = { it }) { proxyName ->
                    val delay = state.delayState(group, proxyName)
                    ProxyNodeCard(
                        name = proxyName,
                        type = groups.firstOrNull { it.name == proxyName }?.type,
                        cardSize = options.cardSize,
                        inUse = proxyName == inUseName,
                        preferred = group.isComputed && proxyName == state.selectedMap[group.name],
                        enabled = isCurrentPage && group.isSelectable &&
                            state.switchingGroup == null && state.batchTestingGroup == null,
                        delay = delay,
                        selectionTag = "proxy_node_${group.name}_$proxyName",
                        delayTag = "proxy_node_delay_${group.name}_$proxyName",
                        onSelect = { onSelect(group, proxyName) },
                        onTestDelay = { onTestDelay(group, proxyName) },
                    )
                }
            }
        }
    }
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
    RecordSection(
        hasRecords = runtimeState.isRunning && state.error == null && state.connections.isNotEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connections_card"),
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
    selectedConnection?.let { connection ->
        ConnectionDetailSheet(
            connection = connection,
            onDismiss = { selectedConnection = null },
            onCopyText = onCopyText,
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
    ConnectionModalSheet(onDismiss = onDismiss, contentScrollState = contentScrollState) { requestDismiss ->
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
                IconButton(onClick = requestDismiss) {
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

internal fun connectionEndpoint(ip: String, port: String): String = when {
    ip.isBlank() -> ""
    port.isBlank() -> ip
    else -> "$ip:$port"
}

internal fun connectionRuleText(connection: CoreConnection): String = buildString {
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
    RecordSection(hasRecords = runtimeState.isRunning && state.requests.isNotEmpty()) {
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

@Composable
internal fun RequestsDialog(
    state: RequestsUiState,
    onCopyText: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var keywords by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedConnection by rememberSaveable(stateSaver = ConnectionDetailSaver) { mutableStateOf<CoreConnection?>(null) }
    val filtered = filterRequests(state.requests, query, keywords)
    ObservationListPage(
        title = "最近请求",
        searchLabel = "搜索目标、进程、代理链或规则",
        searchQuery = query,
        searchExpanded = searchExpanded,
        keywords = keywords,
        onDismiss = onDismiss,
        onSearchQueryChange = { query = it },
        onSearchExpandedChange = { searchExpanded = it },
        onRemoveKeyword = { keywords = keywords - it },
    ) {
        ObservationList(
            items = filtered.asReversed(),
            filterKey = "$query|${keywords.joinToString("|")}",
            emptyContent = {
                val noResults = query.isNotBlank() || keywords.isNotEmpty()
                ObservationEmptyState(
                    title = if (noResults) "没有匹配的结果" else "暂无请求",
                    illustration = if (noResults) {
                        ObservationEmptyIllustration.SEARCH
                    } else {
                        ObservationEmptyIllustration.REQUESTS
                    },
                    testTag = if (noResults) "requests_no_results_state" else "requests_empty_state",
                )
            },
            itemKey = { it.id },
            itemTag = { index -> "request_row_$index" },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) { connection ->
            ConnectionRow(
                connection = connection,
                onClick = { selectedConnection = connection },
                onClickKeyword = { keyword -> if (keyword !in keywords) keywords = keywords + keyword },
            )
        }
    }
    if (selectedConnection != null) {
        ConnectionDetailSheet(
            connection = selectedConnection!!,
            onDismiss = { selectedConnection = null },
            onCopyText = onCopyText,
        )
    }
}

@Composable
private fun DnsCard(
    state: DnsUiState,
    runtimeState: CoreRuntimeStatus,
    onShowAll: () -> Unit,
    onClear: () -> Unit,
) {
    RecordSection(hasRecords = runtimeState.isRunning && state.queries.isNotEmpty()) {
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

@Composable
internal fun DnsDialog(
    state: DnsUiState,
    onDismiss: () -> Unit,
    onClearDnsCache: suspend () -> Result<Unit>,
    onCopyText: (String) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var keywords by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedQuery by rememberSaveable(stateSaver = DnsQueryDetailSaver) {
        mutableStateOf<CoreDnsQuery?>(null)
    }
    val clearFeedbackHostState = remember { SnackbarHostState() }
    val clearScope = rememberCoroutineScope()
    var clearingDnsCache by remember { mutableStateOf(false) }
    var clearSucceeded by remember { mutableStateOf(false) }
    // 作者 long：核心返回的底层异常可能包含运行上下文，弹窗仅展示稳定的成功/失败文案。
    val clearDnsCache: () -> Unit = {
        if (!clearingDnsCache) {
            clearScope.launch {
                clearingDnsCache = true
                val succeeded = try {
                    onClearDnsCache().isSuccess
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                } finally {
                    clearingDnsCache = false
                }
                clearSucceeded = succeeded
                clearFeedbackHostState.showSnackbar(
                    if (succeeded) "DNS 缓存已清除" else "清除 DNS 缓存失败",
                )
            }
        }
    }
    val filtered = filterDnsQueries(state.queries, query, keywords)
    ObservationListPage(
        title = "DNS查询",
        searchLabel = "搜索域名、上游、答案或错误",
        searchQuery = query,
        searchExpanded = searchExpanded,
        keywords = keywords,
        onDismiss = onDismiss,
        onSearchQueryChange = { query = it },
        onSearchExpandedChange = { searchExpanded = it },
        onRemoveKeyword = { keywords = keywords - it },
        onClear = clearDnsCache,
        clearEnabled = !clearingDnsCache,
        pageOverlay = {
            SnackbarHost(clearFeedbackHostState) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = if (clearSucceeded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (clearSucceeded) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )
            }
        },
    ) {
        ObservationList(
            items = filtered.asReversed(),
            filterKey = "$query|${keywords.joinToString("|")}",
            emptyContent = {
                val noResults = query.isNotBlank() || keywords.isNotEmpty()
                ObservationEmptyState(
                    title = if (noResults) "没有匹配的结果" else "DNS查询为空",
                    illustration = if (noResults) {
                        ObservationEmptyIllustration.SEARCH
                    } else {
                        ObservationEmptyIllustration.DNS
                    },
                    testTag = if (noResults) "dns_no_results_state" else "dns_empty_state",
                )
            },
            itemKey = { "${it.time}|${it.domain}|${it.initiator}" },
            itemTag = { index -> "dns_row_$index" },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) { dns ->
            DnsQueryRow(
                query = dns,
                onClick = { selectedQuery = dns },
                onClickKeyword = { keyword -> if (keyword !in keywords) keywords = keywords + keyword },
            )
        }
    }
    if (selectedQuery != null) {
        DnsQueryDetailSheet(
            query = selectedQuery!!,
            onDismiss = { selectedQuery = null },
            onCopyText = onCopyText,
        )
    }
}

@Composable
private fun LogsCard(
    state: LogsUiState,
    runtimeState: CoreRuntimeStatus,
    onShowAll: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    RecordSection(hasRecords = runtimeState.isRunning && state.entries.isNotEmpty()) {
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

@Composable
internal fun LogsDialog(
    state: LogsUiState,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var keywords by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val filteredBySearch = filterLogs(state.entries, query, null)
    val filtered = keywords.fold(filteredBySearch) { entries, keyword ->
        filterLogs(entries, "", keyword)
    }
    ObservationListPage(
        title = "日志",
        searchLabel = "搜索日志级别或内容",
        searchQuery = query,
        searchExpanded = searchExpanded,
        keywords = keywords,
        onDismiss = onDismiss,
        onSearchQueryChange = { query = it },
        onSearchExpandedChange = { searchExpanded = it },
        onRemoveKeyword = { keyword -> keywords = keywords - keyword },
        onExport = onExport,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "显示 ${filtered.size}/${state.entries.size}",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                    .testTag("log_result_count"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ObservationList(
                items = filtered.asReversed(),
                filterKey = "$query|${keywords.joinToString("|")}",
                emptyContent = {
                    val isSearching = query.isNotBlank() || keywords.isNotEmpty()
                    ObservationEmptyState(
                        title = if (isSearching) "没有匹配的结果" else "暂无日志",
                        illustration = if (isSearching) {
                            ObservationEmptyIllustration.SEARCH
                        } else {
                            ObservationEmptyIllustration.LOGS
                        },
                        testTag = if (isSearching) "logs_no_results_state" else "logs_empty_state",
                    )
                },
                itemKey = { "${it.timestampMillis}|${it.level}|${it.payload}" },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            ) { entry ->
                LogRow(entry) { keyword ->
                    if (keyword !in keywords) keywords = keywords + keyword
                }
            }
        }
    }
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

@Composable
internal fun DnsQueryDetailSheet(
    query: CoreDnsQuery,
    onDismiss: () -> Unit,
    onCopyText: (String) -> Unit,
) {
    val contentScrollState = rememberScrollState()
    var copyMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copyMessage) {
        if (copyMessage != null) {
            kotlinx.coroutines.delay(2_000)
            copyMessage = null
        }
    }
    val copyValue: (String) -> Unit = { value ->
        copyMessage = runCatching { onCopyText(value) }.fold({ "已复制" }, { "复制失败" })
    }
    ConnectionModalSheet(
        onDismiss = onDismiss,
        contentScrollState = contentScrollState,
        accessibilityTitle = "DNS 详情",
    ) { requestDismiss ->
        // 作者 long：Flutter DNS 详情列表两侧留白为 16dp，保持基本信息、错误和答案共用该对齐线。
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("DNS 详情", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = requestDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭 DNS 详情")
                }
            }
            copyMessage?.let { Text(it, modifier = Modifier.testTag("dns_copy_feedback")) }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(contentScrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ConnectionDetailSection(
                    title = "基本信息",
                    rows = listOf(
                        "时间" to formatConnectionStart(query.time),
                        "域名" to query.domain,
                        "记录类型" to query.type,
                        "发起方" to query.initiator,
                        "上游" to query.upstream,
                        "缓存" to if (query.cached) "是" else "否",
                        "响应码" to query.rcode,
                        "耗时" to "${query.delay} ms",
                    ),
                    onCopyText = copyValue,
                )
                // 作者 long：解析错误独立成组并使用错误色，避免长错误挤占基本信息的值列；答案整行复制便于复用 IPv6 等完整记录。
                if (query.error.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("错误", style = MaterialTheme.typography.titleSmall)
                        Text(
                            query.error,
                            modifier = Modifier.fillMaxWidth().testTag("connection_detail_错误")
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClickLabel = "复制错误") { copyValue(query.error) }
                                .padding(vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (query.answers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("答案", style = MaterialTheme.typography.titleSmall)
                        query.answers.forEachIndexed { index, answer ->
                            Text(
                                answer,
                                modifier = Modifier.fillMaxWidth().testTag("dns_detail_answer_$index")
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button, onClickLabel = "复制答案") { copyValue(answer) }
                                    .padding(vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 作者 long：详情页默认跟随最新事件，但用户向上翻阅历史后必须保持当前位置，避免高频日志把阅读位置抢回顶部。
 */
@Composable
private fun <T> ObservationList(
    items: List<T>,
    filterKey: Any?,
    emptyContent: @Composable () -> Unit,
    itemKey: (T) -> Any,
    itemTag: ((Int) -> String)? = null,
    modifier: Modifier = Modifier.heightIn(max = 480.dp),
    contentPadding: PaddingValues = PaddingValues(),
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
        Box(modifier.fillMaxWidth(), contentAlignment = BiasAlignment(0f, -0.2f)) {
            emptyContent()
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                Box(itemTag?.let { Modifier.testTag(it(index)) } ?: Modifier) { row(item) }
            }
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
    var showActions by remember(profile.id) { mutableStateOf(false) }
    val expiryDate = profile.subscriptionInfo?.expireAtMillis?.let { millis ->
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(millis))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}"),
        onClick = onSelect,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.realLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    expiryDate?.let { date ->
                        Surface(
                            modifier = Modifier.padding(start = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Text(
                                date,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("profile_expiry_${profile.id}"),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profileSubtitle(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                profile.lastUpdateError?.let { error ->
                    Text(
                        text = "同步失败：$error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } ?: profile.lastUpdateAt?.let { updatedAt ->
                    Text(
                        text = "更新于 ${DateUtils.getRelativeTimeSpanString(updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (selected) {
                Surface(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                        .testTag("profile_selected_${profile.id}"),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, contentDescription = "已选中", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { showActions = true },
                    enabled = canMutate,
                    modifier = Modifier.testTag(
                        if (selected) "profile_actions_selected" else "profile_actions_${profile.id}",
                    ),
                ) { Icon(Icons.Outlined.MoreVert, contentDescription = "${profile.realLabel}的配置操作") }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    if (profile.type == ProfileType.URL) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { showActions = false; onEdit() },
                            enabled = canMutate,
                            modifier = Modifier.testTag("profile_edit_${profile.id}"),
                        )
                        DropdownMenuItem(
                            text = { Text("同步") },
                            leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                            onClick = { showActions = false; onUpdate() },
                            enabled = canMutate,
                        )
                        DropdownMenuItem(
                            text = { Text(if (profile.autoUpdate) "自动更新（开启）" else "自动更新（关闭）") },
                            leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                            onClick = { showActions = false; onSetAutoUpdate(!profile.autoUpdate) },
                            modifier = Modifier.testTag("profile_auto_update_${profile.id}"),
                            enabled = canMutate,
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { showActions = false; onEdit() },
                            enabled = canMutate,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { showActions = false; onRename() },
                        modifier = Modifier.testTag("profile_rename_${profile.id}"),
                        enabled = canMutate,
                    )
                    if (profile.type == ProfileType.CUSTOM) {
                        DropdownMenuItem(
                            text = { Text("高级") },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                            onClick = { showActions = false; onOpenRulesEditor() },
                            enabled = canMutate,
                        )
                        DropdownMenuItem(
                            text = { Text("代理") },
                            leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                            onClick = { showActions = false; onOpenProxiesEditor() },
                            enabled = canMutate,
                            modifier = Modifier.testTag("profile_action_proxies"),
                        )
                        DropdownMenuItem(
                            text = { Text("代理组") },
                            leadingIcon = { Icon(Icons.Outlined.Hub, contentDescription = null) },
                            onClick = { showActions = false; onOpenProxyGroupsEditor() },
                            enabled = canMutate,
                        )
                        DropdownMenuItem(
                            text = { Text("Provider") },
                            leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                            onClick = { showActions = false; onOpenProvidersEditor() },
                            enabled = canMutate,
                        )
                        DropdownMenuItem(
                            text = { Text("DNS") },
                            leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                            onClick = { showActions = false; onOpenDnsEditor() },
                            enabled = canMutate,
                        )
                        DropdownMenuItem(
                            text = { Text("Dialers") },
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            onClick = { showActions = false; onOpenDialersEditor() },
                            enabled = canMutate,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("脚本") },
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                        onClick = { showActions = false; onOpenScriptBinding() },
                        enabled = canMutate,
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { showActions = false; onDelete() },
                        enabled = canMutate,
                    )
                }
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
    isNewDraft: Boolean = false,
    hasPersistedChanges: Boolean = false,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit = onDismiss,
    onFinishNewDraft: (String?) -> Unit = { onDiscard() },
    onSave: (String) -> Unit,
    onOpenRulesEditor: (Profile) -> Unit = {},
    onOpenProxiesEditor: (Profile) -> Unit = {},
    onOpenProxyGroupsEditor: (Profile) -> Unit = {},
    onOpenProvidersEditor: (Profile) -> Unit = {},
    onOpenDnsEditor: (Profile) -> Unit = {},
    onOpenDialersEditor: (Profile) -> Unit = {},
) {
    var content by rememberSaveable(profile.id, initialContent) { mutableStateOf(initialContent) }
    var showDiscardConfirmation by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showSectionDiscardConfirmation by rememberSaveable(profile.id) { mutableStateOf(false) }
    var showDraftNameDialog by rememberSaveable(profile.id) { mutableStateOf(false) }
    var draftName by rememberSaveable(profile.id) { mutableStateOf(profile.label) }
    var pendingSectionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isDirty = content != initialContent
    val keyboardController = LocalSoftwareKeyboardController.current

    // 作者 long：分区编辑即时持久化；退出时区分空草稿与已有内容，防止误删已保存配置。
    fun finishExit() {
        if (isNewDraft && hasPersistedChanges) {
            draftName = profile.label
            showDraftNameDialog = true
        } else {
            onDiscard()
        }
    }

    fun requestExit() {
        if (saving) return
        keyboardController?.hide()
        if (isDirty) showDiscardConfirmation = true else finishExit()
    }

    fun openSection(action: () -> Unit) {
        if (isDirty) {
            pendingSectionAction = action
            showSectionDiscardConfirmation = true
        } else {
            action()
        }
    }

    AlertDialog(
        onDismissRequest = { requestExit() },
        title = { Text("编辑 ${profile.realLabel}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .testTag("profile_editor_scroll")
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (profile.type == ProfileType.CUSTOM) {
                    Text("配置概览", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "按分区编辑自定义配置；原始 YAML 仍可用于导入或处理未覆盖的字段。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProfileEditorSectionButton(
                        title = "规则",
                        summary = "Rules",
                        icon = Icons.Outlined.Edit,
                        onClick = { openSection { onOpenRulesEditor(profile) } },
                    )
                    ProfileEditorSectionButton(
                        title = "代理",
                        summary = "Proxies",
                        icon = Icons.Outlined.Link,
                        onClick = { openSection { onOpenProxiesEditor(profile) } },
                    )
                    ProfileEditorSectionButton(
                        title = "代理组",
                        summary = "Proxy Groups",
                        icon = Icons.Outlined.Hub,
                        onClick = { openSection { onOpenProxyGroupsEditor(profile) } },
                    )
                    ProfileEditorSectionButton(
                        title = "Provider",
                        summary = "Proxy Providers / Rule Providers",
                        icon = Icons.Outlined.Cloud,
                        onClick = { openSection { onOpenProvidersEditor(profile) } },
                    )
                    ProfileEditorSectionButton(
                        title = "DNS",
                        summary = "DNS overrides",
                        icon = Icons.Outlined.Dns,
                        onClick = { openSection { onOpenDnsEditor(profile) } },
                    )
                    ProfileEditorSectionButton(
                        title = "Dialers",
                        summary = "出站链路映射",
                        icon = Icons.Outlined.SwapVert,
                        onClick = { openSection { onOpenDialersEditor(profile) } },
                    )
                    Text("原始 YAML", style = MaterialTheme.typography.titleSmall)
                } else {
                    Text(
                        "保存后会替换 KClash 本地配置文件，核心启动时仍会再次校验 YAML。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().testTag("profile_editor_content"),
                    minLines = if (profile.type == ProfileType.CUSTOM) 3 else 12,
                    maxLines = if (profile.type == ProfileType.CUSTOM) 6 else 20,
                    enabled = !saving,
                    label = { Text("YAML") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onSave(content)
                },
                enabled = !saving && content.isNotBlank(),
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    requestExit()
                },
                enabled = !saving,
            ) { Text("取消") }
        },
    )

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("退出后，本次编辑内容不会写入配置。") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirmation = false; keyboardController?.hide(); finishExit() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("继续编辑") }
            },
        )
    }

    if (showSectionDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showSectionDiscardConfirmation = false
                pendingSectionAction = null
            },
            title = { Text("原始 YAML 尚未保存") },
            text = { Text("进入分区编辑会放弃当前 YAML 修改，继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        content = initialContent
                        showSectionDiscardConfirmation = false
                        val action = pendingSectionAction
                        pendingSectionAction = null
                        action?.invoke()
                    },
                ) { Text("放弃并继续") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSectionDiscardConfirmation = false
                        pendingSectionAction = null
                    },
                ) { Text("继续编辑") }
            },
        )
    }

    if (showDraftNameDialog) {
        AlertDialog(
            onDismissRequest = { onFinishNewDraft(null) },
            title = { Text("为配置命名") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    enabled = !saving,
                    label = { Text("配置名称") },
                    modifier = Modifier.testTag("new_profile_name"),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onFinishNewDraft(draftName) },
                    enabled = !saving && draftName.isNotBlank(),
                ) {
                    if (saving) CircularProgressIndicator() else Text("保存名称")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onFinishNewDraft(null) },
                    enabled = !saving,
                ) { Text("保留默认名称") }
            },
        )
    }
}

/**
 * 作者 long：自定义配置的分区入口统一放在编辑器内，保证用户从 Profiles 进入后能沿 Flutter
 * 的“概览 → 分区”层级继续编辑；每个入口仍由 ViewModel 负责读取和保存，避免 UI 直接改写配置。
 */
@Composable
private fun ProfileEditorSectionButton(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Outlined.Edit, contentDescription = "编辑$title")
    }
}

@Composable
internal fun RemoteProfileEditorDialog(
    profile: Profile,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (label: String, url: String, autoUpdate: Boolean, intervalMinutes: Long) -> Unit,
) {
    var label by rememberSaveable(profile.id, profile.url) { mutableStateOf(profile.label) }
    var url by rememberSaveable(profile.id, profile.url) { mutableStateOf(profile.url) }
    var autoUpdate by rememberSaveable(profile.id, profile.url) { mutableStateOf(profile.autoUpdate) }
    var intervalMinutes by rememberSaveable(profile.id, profile.url) {
        mutableStateOf((profile.autoUpdateIntervalMillis / 60_000L).coerceAtLeast(1L).toString())
    }
    val parsedInterval = intervalMinutes.toLongOrNull()
    val canSave = !saving && label.length <= 64 && isValidRemoteProfileUrl(url) &&
        (!autoUpdate || parsedInterval != null && parsedInterval > 0L)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("编辑订阅") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(64) },
                    enabled = !saving,
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("remote_profile_editor_label"),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    enabled = !saving,
                    label = { Text("订阅链接") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("remote_profile_editor_url"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("自动更新")
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = { autoUpdate = it },
                        enabled = !saving,
                        modifier = Modifier.testTag("remote_profile_editor_auto_update"),
                    )
                }
                OutlinedTextField(
                    value = intervalMinutes,
                    onValueChange = { intervalMinutes = it.filter(Char::isDigit).take(7) },
                    enabled = !saving && autoUpdate,
                    label = { Text("自动更新间隔（分钟）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("remote_profile_editor_interval"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val interval = parsedInterval
                        ?: (profile.autoUpdateIntervalMillis / 60_000L).coerceAtLeast(1L)
                    onSave(label.trim(), url.trim(), autoUpdate, interval)
                },
                enabled = canSave,
            ) {
                if (saving) CircularProgressIndicator() else Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

private fun isValidRemoteProfileUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value.trim())
    uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

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
                    modifier = Modifier.fillMaxWidth().testTag("profile_rules_yaml"),
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
                modifier = Modifier.testTag("profile_rules_save"),
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

internal fun formatBytes(bytes: Long): String {
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
    onAddRemoteBatch: (List<String>) -> Unit = { urls -> urls.forEach { onAddRemote("", it) } },
    existingUrls: Set<String> = emptySet(),
    onAddCustom: (label: String) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var batchMode by rememberSaveable { mutableStateOf(false) }
    var batchInput by rememberSaveable { mutableStateOf("") }
    // 作者 long：来源与表单分步呈现，避免订阅字段和自定义配置字段同时挤在添加入口。
    var step by rememberSaveable { mutableStateOf(AddProfileStep.SOURCE) }
    val busyDismiss = { if (!busy) onDismiss() }
    AlertDialog(
        onDismissRequest = busyDismiss,
        title = {
            Text(
                when (step) {
                    AddProfileStep.SOURCE -> "添加配置"
                    AddProfileStep.REMOTE -> "从链接导入"
                    AddProfileStep.CUSTOM -> "创建自定义配置"
                },
            )
        },
        text = {
            when (step) {
                AddProfileStep.SOURCE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPickQrCode,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("profile_add_source_qr"),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Text("从二维码图片导入")
                    }
                    OutlinedButton(
                        onClick = onPickFile,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("profile_add_source_file"),
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null)
                        Text("从文件导入 YAML")
                    }
                    OutlinedButton(
                        onClick = { step = AddProfileStep.REMOTE },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("profile_add_source_remote"),
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = null)
                        Text("添加订阅链接")
                    }
                    OutlinedButton(
                        onClick = { step = AddProfileStep.CUSTOM },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("profile_add_source_custom"),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Text("创建自定义配置")
                    }
                }
                AddProfileStep.REMOTE -> {
                    val preview = parseProfileUrlImport(batchInput, existingUrls)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryTabRow(selectedTabIndex = if (batchMode) 1 else 0) {
                            Tab(
                                selected = !batchMode,
                                onClick = { batchMode = false },
                                modifier = Modifier.testTag("profile_add_mode_single"),
                                text = { Text("单条") },
                            )
                            Tab(
                                selected = batchMode,
                                onClick = { batchMode = true },
                                modifier = Modifier.testTag("profile_add_mode_batch"),
                                text = { Text("批量") },
                            )
                        }
                        if (batchMode) {
                            OutlinedTextField(
                                value = batchInput,
                                onValueChange = { batchInput = it },
                                label = { Text("粘贴订阅链接") },
                                minLines = 4,
                                maxLines = 7,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("profile_add_remote_batch"),
                            )
                            Text("识别到 ${preview.urls.size} 个链接")
                            if (preview.skippedExisting > 0) {
                                Text("跳过已添加的重复项 ${preview.skippedExisting} 个")
                            }
                            if (preview.invalidCount > 0) {
                                Text(
                                    "有 ${preview.invalidCount} 条链接格式无效，修正后才能导入",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                label = { Text("名称（可选）") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.testTag("profile_add_remote_label"),
                            )
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("订阅链接") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.testTag("profile_add_remote_url"),
                            )
                        }
                    }
                }
                AddProfileStep.CUSTOM -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("名称（可选）") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.testTag("profile_add_custom_label"),
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                AddProfileStep.SOURCE -> Unit
                AddProfileStep.REMOTE -> {
                    val preview = parseProfileUrlImport(batchInput, existingUrls)
                    TextButton(
                        onClick = {
                            if (batchMode) onAddRemoteBatch(preview.urls)
                            else onAddRemote(label, url)
                        },
                        enabled = !busy && if (batchMode) {
                            preview.urls.isNotEmpty() && preview.invalidCount == 0
                        } else {
                            url.isNotBlank()
                        },
                        modifier = Modifier.testTag("profile_add_submit"),
                    ) { Text(if (batchMode) "导入 ${preview.urls.size} 条" else "导入") }
                }
                AddProfileStep.CUSTOM -> TextButton(
                    onClick = { onAddCustom(label) },
                    enabled = !busy,
                ) { Text("创建") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (step == AddProfileStep.SOURCE) onDismiss() else step = AddProfileStep.SOURCE
                },
                enabled = !busy,
            ) {
                Text(if (step == AddProfileStep.SOURCE) "取消" else "返回")
            }
        },
    )
}

private enum class AddProfileStep {
    SOURCE,
    REMOTE,
    CUSTOM,
}
