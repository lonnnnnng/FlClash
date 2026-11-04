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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import java.util.Date
import java.util.Locale
import kotlin.math.PI
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
    ACCESS_CONTROL,
    GENERAL,
    ADVANCED,
    NETWORK,
    GLOBAL_DNS,
    ON_DEMAND,
    DEVELOPER,
}

private enum class DashboardFeedTarget {
    CONNECTIONS,
    REQUESTS,
    DNS,
}

private enum class AccessMenuLayer {
    NONE,
    MAIN,
    SETTINGS,
    ACTIONS,
}

internal enum class ProfilesToolbarAction {
    SYNC_ALL,
    SORT,
    SCRIPTS,
    WEBDAV,
    BACKUP_EXPORT,
    BACKUP_RESTORE,
}

@Composable
internal fun ProfilesTopBarActions(
    profiles: List<Profile>,
    busy: Boolean,
    canMutate: Boolean,
    onAdd: () -> Unit,
    onAction: (ProfilesToolbarAction) -> Unit,
    onAddAnchorChanged: (IntRect) -> Unit = {},
    addSource: AppMenuSource? = null,
) {
    // 作者 long：配置工具采用上游同一胶囊分组，添加按钮独立；保持各自语义和禁用状态，不把整组变成一个点击目标。
    if (profiles.isNotEmpty()) {
        Surface(shape = CircleShape, color = dashboardCardColor()) {
            Row {
                run {
                    IconButton(
                        onClick = { onAction(ProfilesToolbarAction.SYNC_ALL) },
                        enabled = canMutate,
                        modifier = Modifier.size(44.dp).testTag("profiles_sync_all"),
                    ) { AppGlyphIcon(AppGlyphs.sync) }
                }
                IconButton(
                    onClick = { onAction(ProfilesToolbarAction.SORT) },
                    enabled = canMutate,
                    modifier = Modifier.size(44.dp).testTag("profiles_sort"),
                ) { AppGlyphIcon(AppGlyphs.sort) }
            }
        }
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = onAdd,
            enabled = !busy,
            modifier = (if (addSource != null) Modifier.appMenuSource(addSource) else Modifier).size(44.dp).background(dashboardCardColor(), CircleShape).testTag("profiles_add_fab")
                .onGloballyPositioned { onAddAnchorChanged(it.boundsInWindow().roundToIntRect()) },
        ) { AppGlyphIcon(AppGlyphs.addCircle) }
        Spacer(Modifier.size(12.dp))
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
    backupStrategy: BackupRestoreStrategy = BackupRestoreStrategy.COMPATIBLE,
    onBackupStrategy: (BackupRestoreStrategy) -> Unit = {},
    webDavConnection: WebDavConnectionState = WebDavConnectionState.UNKNOWN,
    onSaveWebDavCredentials: (WebDavCredentials) -> Unit = {},
    onClearWebDavCredentials: () -> Unit = {},
    onProbeWebDav: (WebDavCredentials) -> Unit = {},
    onUploadWebDav: (WebDavCredentials, BackupRestoreScope) -> Unit = { _, _ -> },
    onDownloadWebDav: (WebDavCredentials, BackupRestoreScope, BackupRestoreStrategy) -> Unit = { _, _, _ -> },
    onExitApp: () -> Unit = {},
    developerToolsEnabled: Boolean = false,
    memoryViewModel: MemoryViewModel? = null,
    serviceStatusViewModel: ServiceStatusViewModel? = null,
    intranetIpViewModel: IntranetIpViewModel? = null,
    onRestartVpn: () -> Unit = {},
    outboundIpViewModel: OutboundIpViewModel? = null,
    runtimeConfigPreviewViewModel: RuntimeConfigPreviewViewModel? = null,
    profileSharingViewModel: ProfileSharingViewModel? = null,
    profileStatisticsViewModel: ProfileStatisticsViewModel? = null,
    ruleResourcesViewModel: RuleResourcesViewModel? = null,
    resourcesViewModel: ResourcesViewModel? = null,
    localProxiesViewModel: LocalProxiesViewModel? = null,
    filterPresetsViewModel: FilterPresetsViewModel? = null,
    onDemandViewModel: OnDemandViewModel? = null,
    onRequestLocation: () -> Unit = {},
) {
    val demandSsids = onDemandViewModel?.ssids?.collectAsState()?.value ?: emptyList()
    val demandNetwork = onDemandViewModel?.network?.collectAsState()?.value ?: com.lonnnnnng.flclash.data.OnDemandNetworkState()
    val demandBusy = onDemandViewModel?.busy?.collectAsState()?.value ?: false
    val demandSavedVersion = onDemandViewModel?.savedVersion?.collectAsState()?.value ?: 0L
    val demandError = onDemandViewModel?.error?.collectAsState()?.value
    val demandRuntimeError = onDemandViewModel?.runtimeError?.collectAsState()?.value
    val demandSuspended = runtimeViewModel.onDemandSuspended.collectAsState().value
    val filters = filterPresetsViewModel?.state?.collectAsState()?.value ?: FilterPresetsUiState(loading = false)
    var showFilterPresets by rememberSaveable { mutableStateOf(false) }
    var showRuleUrls by rememberSaveable { mutableStateOf(false) }
    var showIconUrls by rememberSaveable { mutableStateOf(false) }
    val resources = resourcesViewModel?.state?.collectAsState()?.value ?: com.lonnnnnng.flclash.data.ResourceUpdatesState(loading = false)
    val iconBusy = resourcesViewModel?.iconBusy?.collectAsState()?.value ?: false
    val iconError = resourcesViewModel?.iconError?.collectAsState()?.value
    val iconCompleted = resourcesViewModel?.iconCompleted?.collectAsState()?.value ?: 0L
    val localProxiesState = localProxiesViewModel?.state?.collectAsState()?.value ?: LocalProxiesUiState(loading = false)
    var showLocalProxies by rememberSaveable { mutableStateOf(false) }
    fun openLocalProxies() { localProxiesViewModel?.open(); showLocalProxies = true }
    var showResources by rememberSaveable { mutableStateOf(false) }
    var showIconSets by rememberSaveable { mutableStateOf(false) }
    val iconSetFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { resourcesViewModel?.importIconsFile(it.toString()) }
    }
    val ruleResources = ruleResourcesViewModel?.state?.collectAsState()?.value ?: RuleResourcesUiState(loading = false)
    var showRuleResources by rememberSaveable { mutableStateOf(false) }
    var showCustomResources by rememberSaveable { mutableStateOf(false) }
    val ruleFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { ruleResourcesViewModel?.importFile(it.toString()) }
    }
    val proxyScrollStateHolder = rememberSaveableStateHolder()
    val serviceStatusState = serviceStatusViewModel?.state?.collectAsState()?.value ?: ServiceStatusUiState()
    val memoryState = memoryViewModel?.state?.collectAsState()?.value ?: MemoryUiState()
    val vpnSettings = runtimeViewModel.vpnSettings.collectAsState().value
    val networkSettings = runtimeViewModel.networkSettings.collectAsState().value
    val networkSaving = runtimeViewModel.networkSaving.collectAsState().value
    val globalDns = runtimeViewModel.globalDns.collectAsState().value
    val dnsSaving = runtimeViewModel.dnsSaving.collectAsState().value
    val generalSettings = runtimeViewModel.generalSettings.collectAsState().value
    val generalSaving = runtimeViewModel.generalSaving.collectAsState().value
    val generalLan = runtimeViewModel.generalLanSharing.collectAsState().value
    val lanSaving = runtimeViewModel.lanSaving.collectAsState().value
    val vpnSaving = runtimeViewModel.vpnSaving.collectAsState().value
    val intranetState = intranetIpViewModel?.state?.collectAsState()?.value ?: IntranetIpUiState()
    val profileStatistics = profileStatisticsViewModel?.state?.collectAsState()?.value ?: ProfileStatisticsUiState()
    val profileProviders = profileStatisticsViewModel?.providers?.collectAsState()?.value ?: ProfileProvidersUiState()
    val configPreview = runtimeConfigPreviewViewModel?.state?.collectAsState()?.value ?: RuntimeConfigPreviewUiState()
    val outboundIpState = outboundIpViewModel?.state?.collectAsState()?.value ?: OutboundIpUiState()
    val runtimeState by runtimeViewModel.runtimeState.collectAsState()
    val outboundMode by runtimeViewModel.outboundMode.collectAsState()
    val runtimeStats by runtimeViewModel.stats.collectAsState()
    val runtimeUptimeMillis by runtimeViewModel.uptimeMillis.collectAsState()
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
    val documentEditorState by profilesViewModel.documentEditorState.collectAsState()
    val rulesEditorState by profilesViewModel.rulesEditorState.collectAsState()
    val proxiesEditorState by profilesViewModel.proxiesEditorState.collectAsState()
    val proxyGroupsEditorState by profilesViewModel.proxyGroupsEditorState.collectAsState()
    val providersEditorState by profilesViewModel.providersEditorState.collectAsState()
    val dnsEditorState by profilesViewModel.dnsEditorState.collectAsState()
    val dialersEditorState by profilesViewModel.dialersEditorState.collectAsState()
    val moreEditorState by profilesViewModel.moreEditorState.collectAsState()
    val customImportState by profilesViewModel.customImportState.collectAsState()
    var customImportPickToken by rememberSaveable { mutableStateOf<Long?>(null) }
    val customImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = customImportPickToken
        customImportPickToken = null
        profilesViewModel.acceptCustomImportFile(token, uri?.toString())
    }
    val proxyGroupsState by proxyGroupsViewModel.state.collectAsState()
    val renderedProxyProfileId = proxyGroupsState.profileId
    val scriptsState by scriptsViewModel.state.collectAsState()
    val scriptEditorState by scriptsViewModel.editorState.collectAsState()
    val remoteScriptState by scriptsViewModel.remoteState.collectAsState()
    val accessControlState by accessControlViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(localProxiesState.error, localProxiesState.message, showLocalProxies) {
        if (!showLocalProxies) (localProxiesState.error ?: localProxiesState.message)?.let {
            snackbarHostState.showSnackbar(it); localProxiesViewModel?.clearFeedback()
        }
    }
    profileSharingViewModel?.let { sharing ->
        ProfileSharingHost(sharing) { snackbarHostState.showSnackbar(it) }
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dashboardLayout by remember(context) { mutableStateOf(DashboardLayoutStore.load(context)) }
    val dashboardListState = rememberLazyListState()
    val pageListState = rememberLazyListState()
    val density = LocalDensity.current
    var isDashboardEditing by rememberSaveable { mutableStateOf(false) }
    var dashboardDraggingSection by remember { mutableStateOf<DashboardSection?>(null) }
    var dashboardDragY by remember { mutableStateOf(0f) }
    var dashboardDragTop by remember { mutableStateOf(0f) }
    var dashboardDragHeight by remember { mutableStateOf(0) }
    val dashboardSwayProgress = rememberDashboardEditSwayProgress(isDashboardEditing)
    val selectedProfile = profilesState.profiles.firstOrNull { it.id == profilesState.currentProfileId }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var scannedProfileUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var scannedProfileLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var profileAddAnchor by remember { mutableStateOf<IntRect?>(null) }
    val profileAddSource = rememberAppMenuSource()
    var showToolsBackup by rememberSaveable { mutableStateOf(false) }
    var showRequests by rememberSaveable { mutableStateOf(false) }
    var showDns by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var profileDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDiagnosticHistory by rememberSaveable { mutableStateOf(false) }
    var showGlobalRules by rememberSaveable { mutableStateOf(false) }
    var showScriptsDialog by rememberSaveable { mutableStateOf(false) }
    val launchFileReplacement = rememberProfileFilePicker(profilesViewModel, remote = false)
    val launchRemoteReplacement = rememberProfileFilePicker(profilesViewModel, remote = true)
    var scriptPickToken by rememberSaveable { mutableStateOf<Long?>(null) }
    val restoredScriptPickToken = remember { scriptPickToken }
    LaunchedEffect(Unit) {
        if (!scriptsViewModel.reconcileFilePicker(restoredScriptPickToken) && scriptPickToken == restoredScriptPickToken) scriptPickToken = null
    }
    val scriptFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = scriptPickToken
        scriptPickToken = null
        if (uri == null) scriptsViewModel.receiveFilePick(token, null)
        else scriptsViewModel.receiveFilePick(token) {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "脚本.js"
                val bytes = resolver.openInputStream(uri)?.use { com.lonnnnnng.flclash.data.readScriptBytes(it) }
                    ?: error("无法读取脚本文件")
                name to bytes
            }
        }
    }
    var selectedDestinationIndex by rememberSaveable { mutableStateOf(MainDestination.DASHBOARD.ordinal) }
    LaunchedEffect(editorState.profile?.id, remoteEditorState.profile?.id) {
        // 作者 long：冷启动恢复的编辑器仍属于配置页，不能只恢复内存而让用户在首页找不到草稿。
        if (editorState.profile != null || remoteEditorState.profile != null) selectedDestinationIndex = MainDestination.PROFILES.ordinal
    }
    var toolsSubpage by rememberSaveable { mutableStateOf(ToolsSubpage.MENU) }
    var showAccessUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    var pendingAccessExitDestination by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingAccessExitFinishes by rememberSaveable { mutableStateOf(false) }
    var pendingAccessExitOpenAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAccessExitToolsSubpage by rememberSaveable { mutableStateOf<ToolsSubpage?>(null) }
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
    LaunchedEffect(serviceStatusViewModel, selectedDestination, isDashboardEditing) {
        try {
            snapshotFlow {
                selectedDestination == MainDestination.DASHBOARD && !isDashboardEditing &&
                    dashboardListState.layoutInfo.visibleItemsInfo.any { it.key == DashboardSection.SERVICE_STATUS.name }
            }.collect { serviceStatusViewModel?.setVisible(it) }
        } finally { serviceStatusViewModel?.setVisible(false); serviceStatusViewModel?.setDetailVisible(false) }
    }
    LaunchedEffect(intranetIpViewModel, selectedDestination, isDashboardEditing) {
        try {
            snapshotFlow {
                selectedDestination == MainDestination.DASHBOARD && !isDashboardEditing &&
                    dashboardListState.layoutInfo.visibleItemsInfo.any { it.key == DashboardSection.INTRANET_IP.name }
            }.collect { intranetIpViewModel?.setVisible(it) }
        } finally { intranetIpViewModel?.setVisible(false) }
    }
    LaunchedEffect(memoryViewModel, selectedDestination, isDashboardEditing) {
        try {
            // 作者 long：列表会预组合离屏卡片，必须按实际可见项采样；编辑与其他页面不刷新内存。
            snapshotFlow {
                selectedDestination == MainDestination.DASHBOARD && !isDashboardEditing &&
                    dashboardListState.layoutInfo.visibleItemsInfo.any { it.key == DashboardSection.MEMORY_INFO.name }
            }.collect { memoryViewModel?.setVisible(it) }
        } finally { memoryViewModel?.setVisible(false) }
    }
    val previewProfile = profilesState.profiles.firstOrNull { it.id == configPreview.profileId }
    // 作者 long：下载可能在页面退到后台后才报告缺钥，作废补输会话，避免恢复时弹出已离开的导入请求。
    ClearAgeInputOnBackground(profilesViewModel::cancelAgeImport)
    if (profilesViewModel.ageKeyRequired.collectAsState().value) {
        AgeSecretKeyDialog(profilesViewModel::submitAgeKey, profilesViewModel::cancelAgeImport)
    }
    fun reorderDashboardDrag() {
        val dragged = dashboardDraggingSection ?: return
        val items = dashboardListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            DashboardSection.entries.firstOrNull { it.name == item.key }?.let {
                DashboardDragItem(it, item.offset, item.size)
            }
        }
        val target = dashboardDragTarget(
            items, dashboardLayout.visibleInOrder(), dashboardDragTop + dashboardDragHeight / 2f,
        )
        if (target != null && target != dragged) {
            val changed = dashboardLayout.reorderVisible(dragged, target)
            if (changed != dashboardLayout) {
                // 作者 long：拿起首个可见卡片时不能让列表按旧 key 追到新位置，否则视口跳动会中断跨屏拖动。
                dashboardListState.requestScrollToItem(
                    dashboardListState.firstVisibleItemIndex, dashboardListState.firstVisibleItemScrollOffset,
                )
                dashboardLayout = changed
                DashboardLayoutStore.save(context, changed)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // 作者 long：进入后台即结束这次拿起，回来不能在没有手指按住时继续滚动或改排序。
            if (event == Lifecycle.Event.ON_STOP) dashboardDraggingSection = null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isDashboardEditing, dashboardDraggingSection, selectedDestination) {
        if (!isDashboardEditing || dashboardDraggingSection == null || selectedDestination != MainDestination.DASHBOARD) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val info = dashboardListState.layoutInfo
            val delta = dashboardDragScrollDelta(
                dashboardDragY, info.viewportStartOffset, info.viewportEndOffset,
                density.density, (frame - previousFrame) / 1_000_000f,
            )
            previousFrame = frame
            if (delta != 0f) {
                // 作者 long：一个受拖动生命周期约束的滚动循环取代逐事件 launch，松手或退出时整段取消。
                dashboardListState.scrollBy(delta)
                reorderDashboardDrag()
            }
        }
    }
    LaunchedEffect(selectedDestination, outboundIpViewModel) {
        outboundIpViewModel?.setDashboardVisible(selectedDestination == MainDestination.DASHBOARD)
    }
    androidx.compose.runtime.DisposableEffect(outboundIpViewModel) {
        onDispose { outboundIpViewModel?.setDashboardVisible(false) }
    }
    LaunchedEffect(selectedDestination, toolsSubpage) {
        if (selectedDestination == MainDestination.TOOLS) pageListState.scrollToItem(0)
    }

    LaunchedEffect(developerToolsEnabled) {
        if (!developerToolsEnabled && toolsSubpage == ToolsSubpage.DEVELOPER) toolsSubpage = ToolsSubpage.MENU
    }

    LaunchedEffect(showProxyDestination) {
        if (!showProxyDestination && selectedDestinationIndex == MainDestination.PROXY.ordinal) {
            selectedDestinationIndex = MainDestination.DASHBOARD.ordinal
        }
    }

    val pickProfileFile = rememberProfileFilePicker(profilesViewModel.fileImport)

    if (showQrScanner) {
        ProfileQrScannerPage(
            onClose = { showQrScanner = false },
            onImport = { result ->
                showQrScanner = false
                scannedProfileUrl = result.url
                scannedProfileLabel = result.label
                showAddDialog = true
            },
        )
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

    fun requestAccessExit(
        destination: MainDestination? = null,
        openAddDialog: Boolean = false,
        toolsPage: ToolsSubpage? = null,
    ) {
        pendingAccessExitDestination = destination?.ordinal
        pendingAccessExitFinishes = destination == null && toolsPage == null
        pendingAccessExitOpenAddDialog = openAddDialog
        pendingAccessExitToolsSubpage = toolsPage
        showAccessUnsavedChanges = true
    }

    fun openDashboardFeed(feed: DashboardFeedTarget) {
        isDashboardEditing = false
        dashboardDraggingSection = null
        selectedDestinationIndex = MainDestination.TOOLS.ordinal
        when (feed) {
            DashboardFeedTarget.CONNECTIONS -> toolsSubpage = ToolsSubpage.CONNECTIONS
            DashboardFeedTarget.REQUESTS -> {
                selectedObservationTabIndex = ObservationTab.REQUESTS.ordinal
                toolsSubpage = ToolsSubpage.OBSERVATIONS
                showRequests = true
            }
            DashboardFeedTarget.DNS -> {
                selectedObservationTabIndex = ObservationTab.DNS.ordinal
                toolsSubpage = ToolsSubpage.OBSERVATIONS
                showDns = true
            }
        }
    }

    fun completeAccessExit() {
        val destination = pendingAccessExitDestination
        val finishes = pendingAccessExitFinishes
        val openAddDialog = pendingAccessExitOpenAddDialog
        val toolsPage = pendingAccessExitToolsSubpage
        pendingAccessExitDestination = null
        pendingAccessExitFinishes = false
        pendingAccessExitOpenAddDialog = false
        pendingAccessExitToolsSubpage = null
        showAccessUnsavedChanges = false
        showAddDialog = openAddDialog
        if (destination != null) selectedDestinationIndex = destination
        if (toolsPage != null) toolsSubpage = toolsPage
        if (finishes) onExitApp()
    }

    fun cancelAccessExit() {
        pendingAccessExitDestination = null
        pendingAccessExitFinishes = false
        pendingAccessExitOpenAddDialog = false
        pendingAccessExitToolsSubpage = null
        showAccessUnsavedChanges = false
    }

    fun requestDashboardFeed(feed: DashboardFeedTarget) {
        openDashboardFeed(feed)
    }

    fun hasUnsavedAccessChanges(): Boolean =
        selectedDestinationIndex == MainDestination.TOOLS.ordinal &&
            toolsSubpage == ToolsSubpage.ACCESS_CONTROL &&
            accessControlState.hasUnsavedChanges

    fun navigateTo(destination: MainDestination, openAddDialog: Boolean = false) {
        // 作者 long：工具子页变化不会切换 Dock 的选中项，保留的导航回调必须在点击时读取当前路由和草稿，
        // 避免使用进入应用代理之前的页面快照而绕过未保存确认。
        if (hasUnsavedAccessChanges()) {
            requestAccessExit(destination, openAddDialog, if (destination == MainDestination.TOOLS) ToolsSubpage.MENU else null)
        } else {
            if (destination == MainDestination.TOOLS) toolsSubpage = ToolsSubpage.MENU
            if (selectedDestinationIndex == MainDestination.DASHBOARD.ordinal && destination != MainDestination.DASHBOARD) {
                isDashboardEditing = false
                dashboardDraggingSection = null
            }
            selectedDestinationIndex = destination.ordinal
            showAddDialog = openAddDialog && destination == MainDestination.PROFILES
        }
    }

    LaunchedEffect(toolsSubpage) { if (toolsSubpage == ToolsSubpage.GENERAL) runtimeViewModel.refreshGeneralLan() }

    fun returnFromToolsSubpage() {
        val parent = when (toolsSubpage) {
            ToolsSubpage.ON_DEMAND -> ToolsSubpage.GENERAL
            ToolsSubpage.NETWORK, ToolsSubpage.GLOBAL_DNS -> ToolsSubpage.ADVANCED
            else -> ToolsSubpage.MENU
        }
        if (hasUnsavedAccessChanges()) {
            requestAccessExit(MainDestination.TOOLS, toolsPage = parent)
        } else {
            toolsSubpage = parent
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

    BackHandler { if (hasUnsavedAccessChanges()) requestAccessExit() else onExitApp() }

    BackHandler(enabled = selectedDestination == MainDestination.DASHBOARD && isDashboardEditing) {
        isDashboardEditing = false
        dashboardDraggingSection = null
    }
    BackHandler(enabled = selectedDestination == MainDestination.TOOLS && toolsSubpage != ToolsSubpage.MENU) {
        returnFromToolsSubpage()
    }

    // 作者 long：详情窗口由页面持有，不能随 LazyColumn 卡片被回收；启停改变前一张卡片高度也应保留正在查看的列表。
    val detailProfile = selectedProfile?.takeIf { it.id == profileDetailId && selectedDestination == MainDestination.DASHBOARD }
    LaunchedEffect(detailProfile?.id) {
        profileStatisticsViewModel?.show(detailProfile?.id)
    }
    LaunchedEffect(selectedProfile?.id, selectedDestination) {
        // 作者 long：冷进程先显示空仓库快照；等当前配置真正到达后再判定切换，否则选择器返回时会丢失已恢复的详情入口。
        if (selectedDestination != MainDestination.DASHBOARD || selectedProfile != null && profileDetailId != selectedProfile.id) profileDetailId = null
    }
    if (selectedDestination != MainDestination.PROFILES) ProfileSyncResults(profilesState.sync,
        profilesViewModel::dismissSyncResult, profilesViewModel::retrySyncFailures)
    if (detailProfile != null) {
        DashboardProfileDetailSheet(
            profile = detailProfile,
            canMutate = !profilesState.isBusy && !isProfileTransitioning(runtimeState),
            isBusy = profilesState.isBusy,
            onDismiss = { profileDetailId = null },
            onShowProfiles = { profileDetailId = null; navigateTo(MainDestination.PROFILES) },
            onUpdate = { profilesViewModel.update(detailProfile) },
            onPreview = { profileDetailId = null; runtimeConfigPreviewViewModel?.open(detailProfile.id) },
            statistics = profileStatistics.takeIf { it.profileId == detailProfile.id } ?: ProfileStatisticsUiState(detailProfile.id, isLoading = true),
            onRetryStatistics = { profileStatisticsViewModel?.retry() },
            providers = profileProviders.takeIf { it.profileId == detailProfile.id } ?: ProfileProvidersUiState(detailProfile.id, isLoading = true),
            onRetryProviders = { profileStatisticsViewModel?.retryProviders() },
            onUpdateProvider = { profileStatisticsViewModel?.updateProvider(it) },
            onUpdateAllProviders = { profileStatisticsViewModel?.updateAllProviders() },
            onRetryFailedProviders = { profileStatisticsViewModel?.updateAllProviders(onlyFailed = true) },
            onPreviewProvider = { profileStatisticsViewModel?.openProvider(it) },
            onEditProvider = { profileStatisticsViewModel?.openProvider(it, edit = true) },
            onUploadProvider = { profileStatisticsViewModel?.openProvider(it, pickFile = true) },
        )
    }
    profileStatisticsViewModel?.let { ProfileProviderDocumentHost(it) }

    MainNavigationHost(
        destination = selectedDestination,
        destinations = navigationDestinations,
        onDestinationSelected = ::navigateTo,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { pageDestination ->
        MainPageScaffold(
            selectedDestination = pageDestination,
            showTopBar = pageDestination != MainDestination.PROXY,
            showNavigation = false,
            onDestinationSelected = ::navigateTo,
            destinations = navigationDestinations,
            topBarTitle = if (pageDestination == MainDestination.DASHBOARD && isDashboardEditing) {
                "编辑仪表盘"
            } else if (pageDestination == MainDestination.TOOLS && toolsSubpage != ToolsSubpage.MENU) {
                when (toolsSubpage) {
                    ToolsSubpage.CONNECTIONS -> "连接"
                    ToolsSubpage.OBSERVATIONS -> "观测"
                    ToolsSubpage.ACCESS_CONTROL -> "按应用代理"
                    ToolsSubpage.GENERAL -> "常规"
                    ToolsSubpage.ADVANCED -> "高级配置"
                    ToolsSubpage.NETWORK -> "网络"
                    ToolsSubpage.GLOBAL_DNS -> "DNS"
                    ToolsSubpage.ON_DEMAND -> "按需连接"
                    ToolsSubpage.DEVELOPER -> "开发者模式"
                    ToolsSubpage.MENU -> "工具"
                }
            } else {
                null
            },
            topBarNavigationIcon = if (pageDestination == MainDestination.TOOLS && toolsSubpage != ToolsSubpage.MENU) {
                {
                    IconButton(onClick = ::returnFromToolsSubpage, modifier = Modifier.testTag("tools_subpage_back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            } else {
                null
            },
            topBarActions = {
                if (pageDestination == MainDestination.DASHBOARD) {
                    IconButton(
                        onClick = {
                            isDashboardEditing = !isDashboardEditing
                            dashboardDraggingSection = null
                        },
                        modifier = Modifier.testTag("dashboard_edit_layout"),
                    ) {
                        Icon(imageVector = if (isDashboardEditing) Icons.Outlined.Check else Icons.Outlined.Edit, contentDescription = null)
                    }
                } else if (pageDestination == MainDestination.PROFILES) {
                    ProfilesTopBarActions(
                        profiles = profilesState.profiles,
                        busy = profilesState.isBusy,
                        canMutate = !profilesState.isBusy && !isProfileTransitioning(runtimeState),
                        onAdd = { showAddDialog = true },
                        onAction = { pendingProfilesToolbarAction = it },
                        onAddAnchorChanged = { profileAddAnchor = it },
                        addSource = profileAddSource,
                    )
                }
            },
        ) { paddingValues ->
            DashboardWidth { dashboardInset ->
                if (pageDestination == MainDestination.PROXY) {
                    // 作者 long：代理页占用 Scaffold 的剩余高度，让组内网格独立滚动，避免套入其他页面的 LazyColumn。
                    ProxyGroupsCard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .testTag("page_proxy"),
                        state = proxyGroupsState,
                        scrollStateHolder = proxyScrollStateHolder,
                        runtimeState = runtimeState,
                        mode = outboundMode,
                        currentProfileId = selectedProfile?.id,
                        currentGroupName = selectedProfile?.currentGroupName,
                        onRefresh = proxyGroupsViewModel::refresh,
                        onSelectGroup = { name -> proxyGroupsViewModel.selectGroup(renderedProxyProfileId, name) },
                        onToggleGroup = { name -> proxyGroupsViewModel.toggleGroupExpanded(renderedProxyProfileId, name) },
                        onSelect = { group, node -> proxyGroupsViewModel.selectProxy(renderedProxyProfileId, group, node) },
                        onTestDelay = { group, node -> proxyGroupsViewModel.testDelay(renderedProxyProfileId, group, node) },
                        onTestGroupDelay = { group -> proxyGroupsViewModel.testDelayGroup(renderedProxyProfileId, group) },
                    )
                } else if (pageDestination == MainDestination.PROFILES) {
                    ProfilesPanel(
                        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp).testTag("page_profiles"),
                        state = profilesState,
                        editorState = editorState,
                        remoteEditorState = remoteEditorState,
                        documentEditorState = documentEditorState,
                        onDocumentChange = profilesViewModel::changeProfileDocument,
                        onCustomDraftChange = profilesViewModel::changeCustomDraft,
                        onPersistProfilePick = profilesViewModel::persistPickerBeforeLaunch,
                        onLaunchFilePick = launchFileReplacement,
                        onLaunchRemotePick = launchRemoteReplacement,
                        onDocumentSave = profilesViewModel::acceptProfileDocument,
                        onDocumentClose = profilesViewModel::closeProfileDocument,
                        onOpenDocument = profilesViewModel::openProfileDocument,
                        onRemoteDraftChange = profilesViewModel::updateRemoteDraft,
                        onBeginRemotePick = profilesViewModel::beginRemoteFilePick,
                        onRemoteFilePicked = profilesViewModel::acceptRemoteFilePick,
                        onClearRemoteReplacement = profilesViewModel::clearRemoteReplacement,
                        onRetryRemoteRead = profilesViewModel::retryRemoteDocument,
                        runtimeState = runtimeState,
                        showAddDialog = showAddDialog,
                        toolbarAction = pendingProfilesToolbarAction,
                        onToolbarActionHandled = { pendingProfilesToolbarAction = null },
                        showInlineToolbar = false,
                        onShowAddDialog = { showAddDialog = true },
                        onDismissAddDialog = { showAddDialog = false; scannedProfileUrl = null; scannedProfileLabel = null },
                        onPickFile = {
                            showAddDialog = false
                            scannedProfileUrl = null
                            scannedProfileLabel = null
                            pickProfileFile()
                        },
                        onPickQrCode = {
                            showAddDialog = false
                            scannedProfileUrl = null
                            scannedProfileLabel = null
                            showQrScanner = true
                        },
                        onAddRemote = { label, url ->
                            showAddDialog = false
                            scannedProfileUrl = null
                            scannedProfileLabel = null
                            profilesViewModel.addRemote(url, label)
                        },
                        onAddRemoteWithAgeKey = { label, url, key ->
                            showAddDialog = false
                            scannedProfileUrl = null
                            scannedProfileLabel = null
                            profilesViewModel.addRemote(url, label, key)
                        },
                        onAddRemoteBatch = { urls ->
                            showAddDialog = false
                            scannedProfileUrl = null
                            scannedProfileLabel = null
                            profilesViewModel.addRemoteBatch(urls)
                        },
                        onAddCustom = { label ->
                            showAddDialog = false
                            scannedProfileUrl = null
                            scannedProfileLabel = null
                            profilesViewModel.addCustom(label)
                        },
                        addMenuAnchor = profileAddAnchor,
                        addMenuSource = profileAddSource,
                        initialRemoteUrl = scannedProfileUrl,
                        initialRemoteLabel = scannedProfileLabel,
                        onSelect = profilesViewModel::select,
                        onPreviewProfile = { runtimeConfigPreviewViewModel?.open(it) },
                        onCopyLink = { profileSharingViewModel?.copyLink(it) },
                        onShowQr = { profileSharingViewModel?.showQr(it) },
                        onExportProfile = { profileSharingViewModel?.beginExport(it) },
                        exportBusy = profileSharingViewModel?.state?.collectAsState()?.value?.exporting == true,
                        onEdit = profilesViewModel::openEditor,
                        onRename = profilesViewModel::rename,
                        onUpdate = profilesViewModel::update,
                        onSyncAll = profilesViewModel::updateAll,
                        onRetrySync = profilesViewModel::retrySyncFailures,
                        onDismissSync = profilesViewModel::dismissSyncResult,
                        onSetAutoUpdate = profilesViewModel::setAutoUpdate,
                        onCloseEditor = profilesViewModel::closeEditor,
                        onDiscardEditor = profilesViewModel::discardEditor,
                        onFinishNewDraftEditor = profilesViewModel::finishNewDraftEditor,
                        onSaveEditor = profilesViewModel::saveEditor,
                        onSaveFileEditor = profilesViewModel::saveFileEditor,
                        onSaveFileEditorWithLabel = { content, key, label -> profilesViewModel.saveFileEditor(content, key, label) },
                        onBeginFileReplacementPick = profilesViewModel::beginFileReplacementPick,
                        onFileMetadataChange = profilesViewModel::updateFileMetadataDraft,
                        onFileReplacementPicked = profilesViewModel::acceptFileReplacement,
                        onClearFileReplacement = profilesViewModel::clearFileReplacement,
                        onSaveRemoteEditor = { label, url, auto, interval -> profilesViewModel.saveRemoteEditor(label, url, auto, interval) },
                        onSaveRemoteEditorWithAgeKey = profilesViewModel::saveRemoteEditor,
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
                        onSaveProxyGroupsDraft = profilesViewModel::saveProxyGroupsDraft,
                        onValidateGroupFilters = profilesViewModel::validateGroupFilters,
                        recentGroupIcons = profilesViewModel.recentGroupIcons.collectAsState().value,
                        onRecordGroupIcon = profilesViewModel::recordGroupIcon,
                        onReadGroupIcon = profilesViewModel::readGroupIcon,
                        onOpenSingleProxyGroupEditor = profilesViewModel::openSingleProxyGroupEditor,
                        providersEditorState = providersEditorState,
                        ruleResources = ruleResources.rows,
                        onManageRuleResources = { showRuleResources = true },
                        onOpenProvidersEditor = profilesViewModel::openProvidersEditor,
                        onCloseProvidersEditor = profilesViewModel::closeProvidersEditor,
                        onSaveProviders = profilesViewModel::saveProviders,
                        onSaveProvidersWithOverrides = profilesViewModel::saveProvidersInPlace,
                        dnsEditorState = dnsEditorState,
                        onOpenDnsEditor = profilesViewModel::openDnsEditor,
                        onCloseDnsEditor = profilesViewModel::closeDnsEditor,
                        onSaveDns = profilesViewModel::saveDns,
                        dialersEditorState = dialersEditorState,
                        moreEditorState = moreEditorState,
                        onOpenMoreEditor = profilesViewModel::openMoreEditor,
                        onCloseMoreEditor = profilesViewModel::closeMoreEditor,
                        onSaveMoreEditor = profilesViewModel::saveMoreEditor,
                        customImportState = customImportState,
                        onPickCustomImportFile = {
                            profilesViewModel.beginCustomImportPick()?.let { token ->
                                customImportPickToken = token
                                try { customImportPicker.launch(arrayOf("*/*")) }
                                catch (_: Exception) { customImportPickToken = null; profilesViewModel.cancelCustomImport() }
                            }
                        },
                        onImportCustomUrl = profilesViewModel::importCustomFromUrl,
                        onConfirmCustomImport = profilesViewModel::confirmCustomImport,
                        onCancelCustomImport = profilesViewModel::cancelCustomImport,
                        onOpenDialersEditor = profilesViewModel::openDialersEditor,
                        onCloseDialersEditor = profilesViewModel::closeDialersEditor,
                        onSaveDialers = profilesViewModel::saveDialers,
                        onSetDialerTarget = profilesViewModel::setDialerTarget,
                        onReorderProfiles = profilesViewModel::reorder,
                        extensionController = profilesViewModel.extension,
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
                } else {
                    LazyColumn(
                        state = if (pageDestination == MainDestination.DASHBOARD) dashboardListState else pageListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = if (pageDestination == MainDestination.DASHBOARD) dashboardInset else 16.dp)
                            .testTag("page_${pageDestination.name.lowercase()}"),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            if (pageDestination == MainDestination.DASHBOARD && isDashboardEditing) 0.dp else 12.dp,
                        ),
                    ) {
                        when (pageDestination) {
                        MainDestination.DASHBOARD -> {
                            items(
                                items = dashboardLayout.visibleInOrder(),
                                key = { it.name },
                            ) { section ->
                                val content: @Composable () -> Unit = {
                                    when (section) {
                                    DashboardSection.CURRENT_OUTBOUND -> RuntimeCard(
                                        state = runtimeState,
                                        onDemandSuspended = demandSuspended,
                                        uptimeMillis = runtimeUptimeMillis,
                                        outboundIp = outboundIpState,
                                        onRefreshIp = { outboundIpViewModel?.refresh() },
                                        onRefreshIpQuality = { outboundIpViewModel?.refreshQuality() },
                                        onStart = onStartRuntime,
                                        onStop = onStopRuntime,
                                    )
                                    DashboardSection.SERVICE_STATUS -> DashboardServiceStatusCard(
                                        serviceStatusState, { serviceStatusViewModel?.check(it) },
                                        { target, value -> serviceStatusViewModel?.toggle(target, value) },
                                        { target, offset -> serviceStatusViewModel?.move(target, offset) },
                                        { serviceStatusViewModel?.setDetailVisible(it) }, { serviceStatusViewModel?.reloadSettings() },
                                        { serviceStatusViewModel?.refreshExit(it) }, { serviceStatusViewModel?.openQuality(it) },
                                        { serviceStatusViewModel?.closeQuality() }, { serviceStatusViewModel?.refreshQuality(force = true) },
                                        onReorder = { serviceStatusViewModel?.reorder(it) },
                                    )
                                    DashboardSection.VPN -> DashboardVpnCard(
                                        vpnSettings, vpnSaving || runtimeState == CoreRuntimeStatus.STARTING || runtimeState == CoreRuntimeStatus.STOPPING,
                                        runtimeViewModel::setVpnEnabled, onRestartVpn,
                                    )
                                    DashboardSection.INTRANET_IP -> DashboardIntranetIpCard(
                                        intranetState, { intranetIpViewModel?.setAllowLan(it) },
                                        { intranetIpViewModel?.copyAddress() }, { intranetIpViewModel?.retry() },
                                    )
                                    DashboardSection.MEMORY_INFO -> {
                                        DashboardMemoryCard(state = memoryState, onRelease = { memoryViewModel?.release() })
                                    }
                                    DashboardSection.NETWORK_STATS -> RuntimeStatsCard(
                                        state = runtimeStats,
                                        runtimeState = runtimeState,
                                    )
                                    DashboardSection.CONNECTION_STATS -> DashboardConnectionStatsCard(
                                        connectionCount = if (runtimeState.isRunning) runtimeStats.connectionCount else 0,
                                        requestCount = runtimeRequests.requests.size,
                                        dnsCount = runtimeDns.queries.size,
                                        onConnections = { requestDashboardFeed(DashboardFeedTarget.CONNECTIONS) },
                                        onRequests = { requestDashboardFeed(DashboardFeedTarget.REQUESTS) },
                                        onDns = { requestDashboardFeed(DashboardFeedTarget.DNS) },
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
                                        onPreviewConfig = { runtimeConfigPreviewViewModel?.open(it.id) },
                                        statistics = profileStatistics,
                                        onOpenDetail = { profileDetailId = it.id },
                                        onRetryStatistics = { profileStatisticsViewModel?.retry() },
                                        providers = profileProviders,
                                        onRetryProviders = { profileStatisticsViewModel?.retryProviders() },
                                        onUpdateProvider = { profileStatisticsViewModel?.updateProvider(it) },
                                    )
                                    DashboardSection.PROXY_GROUPS -> DashboardProxyGroupsCard(
                                        state = proxyGroupsState,
                                        runtimeState = runtimeState,
                                        mode = outboundMode,
                                        currentProfileId = selectedProfile?.id,
                                        currentGroupName = selectedProfile?.currentGroupName,
                                        onRefresh = proxyGroupsViewModel::refresh,
                                        onSelectGroup = { name -> proxyGroupsViewModel.selectGroup(renderedProxyProfileId, name) },
                                        onToggleGroup = { name -> proxyGroupsViewModel.toggleGroupExpanded(renderedProxyProfileId, name) },
                                        onSelect = { group, node -> proxyGroupsViewModel.selectProxy(renderedProxyProfileId, group, node) },
                                        onTestDelay = { group, node -> proxyGroupsViewModel.testDelay(renderedProxyProfileId, group, node) },
                                        onTestGroupDelay = { group -> proxyGroupsViewModel.testDelayGroup(renderedProxyProfileId, group) },
                                    )
                                    }
                                }
                                if (isDashboardEditing) {
                                    DashboardEditableSection(
                                        section = section,
                                        swayProgress = dashboardSwayProgress,
                                        isDragging = dashboardDraggingSection == section,
                                        dragOffsetPx = {
                                            val item = dashboardListState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == section.name }
                                            if (item != null) dashboardDragTop - item.offset else 0f
                                        },
                                        onRemove = if (section.removable) ({
                                            dashboardLayout = dashboardLayout.setVisible(section, false)
                                            DashboardLayoutStore.save(context, dashboardLayout)
                                        }) else null,
                                        onDragStart = { localY ->
                                            val item = dashboardListState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == section.name }
                                            if (item != null) {
                                                dashboardDraggingSection = section
                                                dashboardDragY = item.offset + localY
                                                dashboardDragTop = item.offset.toFloat()
                                                dashboardDragHeight = item.size
                                            }
                                        },
                                        onDragBy = { deltaY ->
                                            if (dashboardDraggingSection == section) {
                                                dashboardDragY += deltaY
                                                dashboardDragTop += deltaY
                                                reorderDashboardDrag()
                                            }
                                        },
                                        onDragEnd = { dashboardDraggingSection = null },
                                        modifier = (if (dashboardDraggingSection == section) Modifier else Modifier.animateItem())
                                            .zIndex(if (dashboardDraggingSection == section) 1f else 0f)
                                            .padding(bottom = 12.dp),
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
                                            modifier = Modifier.animateItem().padding(bottom = 8.dp),
                                        ) {
                                            dashboardLayout = dashboardLayout.setVisible(section, true)
                                            DashboardLayoutStore.save(context, dashboardLayout)
                                        }
                                    }
                                }
                            }
                        }

                        MainDestination.PROFILES -> Unit

                        MainDestination.PROXY -> Unit

                        MainDestination.TOOLS -> when (toolsSubpage) {
                            ToolsSubpage.MENU -> item {
                                ToolsPanel(
                                    onOpenConnections = { toolsSubpage = ToolsSubpage.CONNECTIONS },
                                    onOpenObservation = { tab ->
                                        selectedObservationTabIndex = tab.ordinal
                                        toolsSubpage = ToolsSubpage.OBSERVATIONS
                                    },
                                    onOpenAccessControl = {
                                        // 作者 long：重新进入独立应用代理页时清空上次搜索，避免隐藏的查询继续筛掉完整应用列表。
                                        accessControlViewModel.setSearchQuery("")
                                        toolsSubpage = ToolsSubpage.ACCESS_CONTROL
                                    },
                                    onOpenGeneral = { toolsSubpage = ToolsSubpage.GENERAL },
                                    onOpenAdvanced = { toolsSubpage = ToolsSubpage.ADVANCED },
                                    onOpenBackup = { showToolsBackup = true },
                                    onOpenScripts = { showCustomResources = true },
                                    onOpenResources = { showResources = true },
                                    onOpenDeveloper = if (developerToolsEnabled) ({ toolsSubpage = ToolsSubpage.DEVELOPER }) else null,
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
                            ToolsSubpage.ACCESS_CONTROL -> item {
                                Box(Modifier.fillMaxWidth().testTag("page_access_control")) {
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
                            }
                            ToolsSubpage.GENERAL -> item {
                                GeneralSettingsPanel(onOpenOnDemand = { toolsSubpage = ToolsSubpage.ON_DEMAND }, settings = generalSettings,
                                    busy = generalSaving || runtimeState == CoreRuntimeStatus.STARTING || runtimeState == CoreRuntimeStatus.STOPPING,
                                    allowLan = generalLan.config.allowLan, lanBusy = lanSaving, onAllowLan = runtimeViewModel::saveGeneralLan,
                                    onShowLogs = { showLogs = true }, onSave = runtimeViewModel::saveGeneralSettings)
                            }
                            ToolsSubpage.ADVANCED -> item {
                                AdvancedSettingsPanel(onOpenNetwork = { toolsSubpage = ToolsSubpage.NETWORK },
                                    onOpenScripts = { showScriptsDialog = true },
                                    onOpenDns = { toolsSubpage = ToolsSubpage.GLOBAL_DNS }, onOpenRules = { showGlobalRules = true })
                            }
                            ToolsSubpage.NETWORK -> item {
                                NetworkSettingsPanel(networkSettings, vpnSettings,
                                    networkSaving || vpnSaving || runtimeState == CoreRuntimeStatus.STARTING || runtimeState == CoreRuntimeStatus.STOPPING,
                                    runtimeViewModel::setVpnEnabled, onRestartVpn, runtimeViewModel::saveNetworkSettings)
                            }
                            ToolsSubpage.GLOBAL_DNS -> item {
                                GlobalDnsSettingsPanel(globalDns, dnsSaving || runtimeState == CoreRuntimeStatus.STARTING || runtimeState == CoreRuntimeStatus.STOPPING,
                                    runtimeViewModel::saveGlobalDns)
                            }
                            ToolsSubpage.ON_DEMAND -> item {
                                OnDemandSettingsPanel(
                                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                                    network = demandNetwork, onRequestLocation = onRequestLocation, ssids = demandSsids,
                                    busy = demandBusy, error = demandError ?: demandRuntimeError, suspended = demandSuspended,
                                    savedVersion = demandSavedVersion,
                                    onSave = { expected, next -> onDemandViewModel?.save(expected, next) },
                                )
                            }
                            ToolsSubpage.DEVELOPER -> if (developerToolsEnabled) {
                                // 作者 long：系统 Always-on 引导和订阅请求证据是 Kotlin 验收扩展，不伪装成 Flutter 仪表盘功能。
                                item {
                                    AlwaysOnVpnCard(state = alwaysOnVpnState, onRequest = onRequestAlwaysOnVpn)
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
                        }
                    }
                        item { Spacer(Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    // 作者 long：预览最后挂载，才能覆盖仍打开的整页配置概览，返回后继续原编辑路径。
    RuntimeConfigPreviewHost(
        state = configPreview,
        profile = previewProfile,
        boundScript = scriptsState.scripts.firstOrNull { it.id == previewProfile?.scriptId },
        currentProfileId = profilesState.currentProfileId,
        runtimeState = runtimeState,
        destination = selectedDestination.name,
        onRetry = { configPreview.profileId?.let { runtimeConfigPreviewViewModel?.open(it) } },
        onClose = { runtimeConfigPreviewViewModel?.close() },
    )

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

    if (showToolsBackup) ProfileMaintenancePage(
        canRestore = !profilesState.isBusy && runtimeState in setOf(CoreRuntimeStatus.STOPPED, CoreRuntimeStatus.FAILED),
        busy = webDavBusy, credentials = webDavCredentials, onDismiss = { showToolsBackup = false },
        onExport = onExportBackup, onImport = onImportBackup,
        onSaveCredentials = onSaveWebDavCredentials, onClearCredentials = onClearWebDavCredentials,
        onProbe = onProbeWebDav, onUpload = onUploadWebDav, onDownload = onDownloadWebDav,
        strategy = backupStrategy, onStrategy = onBackupStrategy, connection = webDavConnection,
    )

    if (showResources) ResourcesPage(resources, { showResources = false }, { resourcesViewModel?.refresh() },
        { resourcesViewModel?.sync(it) }, { resourcesViewModel?.syncAll() }, { resourcesViewModel?.settings(it) },
        { resourcesViewModel?.clearFeedback() })
    if (showCustomResources) {
        ProfileEditorPage(onDismissRequest = { showCustomResources = false }, title = { Text("自定义资源") }, text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = ::openLocalProxies, modifier = Modifier.fillMaxWidth()) { Text("本地代理") }
                TextButton(onClick = { showRuleResources = true }, modifier = Modifier.fillMaxWidth()) { Text("规则资源") }
                TextButton(onClick = { showIconSets = true }, modifier = Modifier.fillMaxWidth()) { Text("图标集") }
                TextButton(onClick = { showFilterPresets = true }, modifier = Modifier.fillMaxWidth().testTag("custom_resources_filters")) { Text("过滤预设") }
                TextButton(onClick = { showScriptsDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("脚本库") }
            }
        })
    }
    if (showLocalProxies && localProxiesViewModel != null) LocalProxiesPage(localProxiesState,
        { localProxiesViewModel.close(); showLocalProxies = false }, localProxiesViewModel::edit, localProxiesViewModel::save,
        localProxiesViewModel::dismissEditor, localProxiesViewModel::links, localProxiesViewModel::applyLinks,
        localProxiesViewModel::cancelLinks, localProxiesViewModel::reorder, localProxiesViewModel::askDelete,
        localProxiesViewModel::delete, localProxiesViewModel::cancelDelete, localProxiesViewModel::confirmReplacement)
    if (showIconSets) IconSetsPage(resources.iconSets, iconBusy, iconError, iconCompleted,
        { showIconSets = false }, { name, url -> resourcesViewModel?.importIcons(name, url) }, { iconSetFilePicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        { item, name, url -> resourcesViewModel?.iconOptions(item, name, url) },
        { item -> resources.rows.firstOrNull { it.kind == com.lonnnnnng.flclash.data.ResourceKind.ICONS && it.id == item.id }?.let { resourcesViewModel?.sync(it) } },
        { resourcesViewModel?.deleteIcons(it) }, { id, offset -> resourcesViewModel?.moveIcons(id, offset) },
        onImportUrls = { resourcesViewModel?.iconUrlImports?.reset(); showIconUrls = true },
        onReorder = { resourcesViewModel?.reorderIcons(it) })
    if (showRuleResources && ruleResourcesViewModel != null) {
        RuleResourcesPage(ruleResources, { showRuleResources = false }, ruleResourcesViewModel::create,
            { ruleFilePicker.launch(arrayOf("*/*")) }, ruleResourcesViewModel::edit, ruleResourcesViewModel::sync,
            ruleResourcesViewModel::syncAll, ruleResourcesViewModel::askDelete, ruleResourcesViewModel::delete,
            ruleResourcesViewModel::cancelDelete, ruleResourcesViewModel::move, ruleResourcesViewModel::closeEditor,
            ruleResourcesViewModel::save, onImportUrls = { ruleResourcesViewModel.urlImports.reset(); showRuleUrls = true },
            onReorder = ruleResourcesViewModel::reorder)
    }
    if (showFilterPresets && filterPresetsViewModel != null) FilterPresetsPage(filters, { showFilterPresets = false },
        filterPresetsViewModel::edit, filterPresetsViewModel::closeEditor, filterPresetsViewModel::save,
        filterPresetsViewModel::delete, filterPresetsViewModel::reorder)
    if (showRuleUrls && ruleResourcesViewModel != null) {
        val batch by ruleResourcesViewModel.urlImports.state.collectAsState()
        ResourceUrlImportPage("导入规则资源", batch, { showRuleUrls = false }, ruleResourcesViewModel.urlImports::start,
            ruleResourcesViewModel.urlImports::cancel, ruleResourcesViewModel.urlImports::retry, ruleResourcesViewModel.urlImports::reset)
    }
    if (showIconUrls && resourcesViewModel != null) {
        val batch by resourcesViewModel.iconUrlImports.state.collectAsState()
        ResourceUrlImportPage("导入图标集", batch, { showIconUrls = false }, resourcesViewModel.iconUrlImports::start,
            resourcesViewModel.iconUrlImports::cancel, resourcesViewModel.iconUrlImports::retry, resourcesViewModel.iconUrlImports::reset)
    }
    if (showGlobalRules) GlobalRulesRoute(profilesViewModel.globalRules) { showGlobalRules = false }

    if (showScriptsDialog) {
        ScriptsLibraryDialog(
            state = scriptsState,
            onDismiss = { showScriptsDialog = false },
            onCreate = scriptsViewModel::openNewEditor,
            onEdit = scriptsViewModel::openEditor,
            onDelete = scriptsViewModel::delete,
            onReorder = scriptsViewModel::reorder,
            onImportRemote = { scriptsViewModel.openRemote() },
            onImportFile = {
                scriptsViewModel.beginFilePick()?.let { token ->
                    coroutineScope.launch {
                        try {
                            if (scriptsViewModel.persistFilePick(token)) {
                                scriptPickToken = token
                                scriptFilePicker.launch(arrayOf("*/*"))
                            }
                            else { scriptPickToken = null; scriptsViewModel.receiveFilePick(token, null) }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            scriptsViewModel.receiveFilePick(token, null)
                            throw cancelled
                        }
                        catch (_: Exception) { scriptPickToken = null; scriptsViewModel.receiveFilePick(token) { error("无法打开文件选择器") } }
                    }
                }
            },
            onEditUrl = scriptsViewModel::openRemote,
            onSync = scriptsViewModel::sync,
            onRetryApply = scriptsViewModel::retryApply,
        )
    }

    if (remoteScriptState.isOpen) {
        ScriptRemoteDialog(
            busy = scriptsState.isBusy,
            script = remoteScriptState.script,
            error = scriptsState.error,
            initialLabel = remoteScriptState.label,
            initialUrl = remoteScriptState.url,
            onDraftChange = scriptsViewModel::editRemoteDraft,
            onDismiss = scriptsViewModel::closeRemote,
            onImport = scriptsViewModel::submitRemote,
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
                    onDraftChange = scriptsViewModel::editDraft,
                    onRetry = { editor.script?.let(scriptsViewModel::openEditor) },
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
    documentEditorState: ProfileDocumentEditorState = ProfileDocumentEditorState(),
    onDocumentChange: (String) -> Unit = {},
    onCustomDraftChange: (String) -> Unit = {},
    onPersistProfilePick: suspend (Long) -> Boolean = { true },
    onLaunchFilePick: ((Long) -> Unit)? = null,
    onLaunchRemotePick: ((Long) -> Unit)? = null,
    onDocumentSave: () -> Unit = {},
    onDocumentClose: () -> Unit = {},
    onOpenDocument: ((Boolean) -> Unit)? = null,
    onRemoteDraftChange: (RemoteProfileDraft) -> Unit = {},
    onBeginRemotePick: () -> Long? = { null },
    onRemoteFilePicked: (Long?, String?) -> Unit = { _, _ -> },
    onClearRemoteReplacement: () -> Unit = {},
    onRetryRemoteRead: () -> Unit = {},
    runtimeState: CoreRuntimeStatus,
    showAddDialog: Boolean,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onPickFile: () -> Unit,
    onPickQrCode: () -> Unit,
    onAddRemote: (label: String, url: String) -> Unit,
    onAddRemoteWithAgeKey: ((String, String, String) -> Unit)? = null,
    onAddRemoteBatch: ((List<String>) -> Unit)? = null,
    onAddCustom: (label: String) -> Unit,
    addMenuAnchor: IntRect? = null,
    addMenuSource: AppMenuSource? = null,
    initialRemoteUrl: String? = null,
    initialRemoteLabel: String? = null,
    onSelect: (id: Long) -> Unit,
    onPreviewProfile: (Long) -> Unit = {},
    onCopyLink: (Long) -> Unit = {},
    onShowQr: (Long) -> Unit = {},
    onExportProfile: (Long) -> Unit = {},
    exportBusy: Boolean = false,
    onEdit: (profile: Profile) -> Unit,
    onRename: (profile: Profile, label: String) -> Unit = { _, _ -> },
    onUpdate: (profile: Profile) -> Unit,
    onSyncAll: (() -> Unit)? = null,
    onRetrySync: () -> Unit = {},
    onDismissSync: () -> Unit = {},
    onSetAutoUpdate: (profile: Profile, enabled: Boolean) -> Unit = { _, _ -> },
    onCloseEditor: () -> Unit,
    onDiscardEditor: () -> Unit = onCloseEditor,
    onFinishNewDraftEditor: (String?) -> Unit = { onDiscardEditor() },
    onSaveEditor: (content: String) -> Unit,
    onSaveFileEditor: (String?, String) -> Unit = { _, _ -> },
    onSaveFileEditorWithLabel: ((String?, String, String) -> Unit)? = null,
    onBeginFileReplacementPick: () -> Long? = { null },
    onFileMetadataChange: (String, String) -> Unit = { _, _ -> },
    onFileReplacementPicked: (Long?, String?) -> Unit = { _, _ -> },
    onClearFileReplacement: () -> Unit = {},
    onSaveRemoteEditor: (label: String, url: String, autoUpdate: Boolean, intervalMinutes: Long) -> Unit = { _, _, _, _ -> },
    onSaveRemoteEditorWithAgeKey: ((String, String, Boolean, Long, String) -> Unit)? = null,
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
    onSaveProxyGroupsDraft: ((com.lonnnnnng.flclash.data.ProxyGroupsSave) -> Unit)? = null,
    onValidateGroupFilters: suspend (List<String>) -> List<String> = { error("过滤表达式校验不可用") },
    recentGroupIcons: List<String> = emptyList(),
    onRecordGroupIcon: (String, Boolean) -> Unit = { _, _ -> },
    onReadGroupIcon: suspend (String) -> String = { error("图片读取不可用") },
    onOpenSingleProxyGroupEditor: ((Profile, Int) -> Unit)? = null,
    providersEditorState: ProfileProvidersEditorUiState = ProfileProvidersEditorUiState(),
    ruleResources: List<com.lonnnnnng.flclash.data.RuleResource> = emptyList(),
    onManageRuleResources: () -> Unit = {},
    onOpenProvidersEditor: (profile: Profile) -> Unit = {},
    onCloseProvidersEditor: () -> Unit = {},
    onSaveProviders: (proxyProviders: String, ruleProviders: String) -> Unit = { _, _ -> },
    onSaveProvidersWithOverrides: ((String, String, Map<String, ProxyProviderOverride>) -> Unit)? = null,
    dnsEditorState: ProfileDnsEditorUiState = ProfileDnsEditorUiState(),
    onOpenDnsEditor: (profile: Profile) -> Unit = {},
    onCloseDnsEditor: () -> Unit = {},
    onSaveDns: (ProfileDnsOverrides) -> Unit = {},
    dialersEditorState: ProfileDialersEditorUiState = ProfileDialersEditorUiState(),
    moreEditorState: ProfileMoreEditorUiState = ProfileMoreEditorUiState(),
    onOpenMoreEditor: (Profile) -> Unit = {},
    onCloseMoreEditor: () -> Unit = {},
    onSaveMoreEditor: (com.lonnnnnng.flclash.service.ProfileNtpOverrides, com.lonnnnnng.flclash.service.ProfileSnifferOverrides) -> Unit = { _, _ -> },
    customImportState: CustomImportUiState = CustomImportUiState(),
    onPickCustomImportFile: () -> Unit = {},
    onImportCustomUrl: (String, String) -> Unit = { _, _ -> },
    onConfirmCustomImport: () -> Unit = {},
    onCancelCustomImport: () -> Unit = {},
    onOpenDialersEditor: (Profile) -> Unit = {},
    onCloseDialersEditor: () -> Unit = {},
    onSaveDialers: (String) -> Unit = {},
    onSetDialerTarget: (String, String?) -> Unit = { _, _ -> },
    onReorderProfiles: (List<Long>) -> Unit = {},
    extensionController: ProfileExtensionController? = null,
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
    val canMutateProfiles = !state.isBusy && !isProfileTransitioning(runtimeState)
    val canChangeFiles = !state.isBusy && !isProfileMutationBlocked(runtimeState)
    var profilePendingDeletion by remember { mutableStateOf<Profile?>(null) }
    var profilePendingRename by remember { mutableStateOf<Profile?>(null) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var scriptBindingProfileId by remember { mutableStateOf<Long?>(null) }
    var extensionProfileId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var showWebDavRestoreDialog by remember { mutableStateOf(false) }
    var pendingWebDavCredentials by remember { mutableStateOf<WebDavCredentials?>(null) }
    var inlineToolbarAction by remember { mutableStateOf<ProfilesToolbarAction?>(null) }
    var localAddAnchor by remember { mutableStateOf<IntRect?>(null) }
    val localAddSource = rememberAppMenuSource()

    val actionToHandle = toolbarAction ?: inlineToolbarAction
    LaunchedEffect(actionToHandle) {
        when (actionToHandle) {
            ProfilesToolbarAction.SYNC_ALL -> if (onSyncAll != null) onSyncAll() else state.profiles.filter { it.type == ProfileType.URL }.forEach(onUpdate)
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
        modifier = modifier.fillMaxSize().testTag("profiles_page"),
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
                    onAddAnchorChanged = { localAddAnchor = it },
                    addSource = localAddSource,
                )
            }
        }
        if (state.profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 32.dp, vertical = 24.dp).testTag("profiles_empty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfileEmptyIllustration()
                    Spacer(Modifier.height(16.dp))
                    Text("暂无配置", Modifier, color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "添加一个配置文件后即可开始使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    // 作者 long：空态和顶栏共享同一受理条件，启停/导入期间不能通过第二个入口重复开启配置操作。
                    androidx.compose.material3.FilledTonalButton(onClick = onShowAddDialog, enabled = canMutateProfiles,
                        modifier = Modifier.testTag("profiles_empty_add").appMenuSource(localAddSource).onGloballyPositioned { localAddAnchor = it.boundsInWindow().roundToIntRect() }) {
                        AppGlyphIcon(AppGlyphs.addCircle)
                        Spacer(Modifier.size(8.dp))
                        Text("添加配置")
                    }
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("profiles_grid")) {
                // 作者 long：Flutter 配置页按 300dp 最小卡宽自适应 1~3 列，避免手机上被强行压成细长列表。
                val spacing = 8.dp
                val columns = ((maxWidth + spacing) / (300.dp + spacing)).toInt().coerceIn(1, 3)
                val list = rememberLazyListState()
                var revealed by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(state.profiles.map { it.id }, columns) {
                    // 作者 long：只在页面首次拿到配置时定位选中项，后续点击不抢走用户正在查看的位置。
                    if (!revealed && state.profiles.isNotEmpty()) {
                        val index = state.profiles.indexOfFirst { it.id == state.currentProfileId }
                        if (index >= 0) list.scrollToItem(index / columns)
                        revealed = true
                    }
                }
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing)) {
                    items(state.profiles.chunked(columns), key = { it.first().id }) { row ->
                        Row(Modifier.animateItem().fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            row.forEach { profile ->
                                ProfileCard(
                                    modifier = Modifier.weight(1f),
                                    profile = profile,
                                    selected = profile.id == state.currentProfileId,
                                    canMutate = canMutateProfiles,
                                    canEdit = canMutateProfiles,
                                    canDelete = canMutateProfiles,
                                    onSelect = { onSelect(profile.id) },
                                    onPreview = { onPreviewProfile(profile.id) },
                                    onCopyLink = { onCopyLink(profile.id) },
                                    onShowQr = { onShowQr(profile.id) },
                                    onExport = { onExportProfile(profile.id) },
                                    exportBusy = exportBusy,
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
                                    onExtend = { extensionProfileId = profile.id },
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
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
            onAddRemoteWithAgeKey = onAddRemoteWithAgeKey,
            onAddRemoteBatch = onAddRemoteBatch ?: { urls -> urls.forEach { onAddRemote("", it) } },
            existingUrls = state.profiles
                .asSequence()
                .filter { it.type == ProfileType.URL }
                .map(Profile::url)
                .toSet(),
            onAddCustom = onAddCustom,
            sourceAnchor = localAddAnchor ?: addMenuAnchor,
            menuSource = if (localAddAnchor != null) localAddSource else addMenuSource,
            initialRemoteUrl = initialRemoteUrl,
            initialRemoteLabel = initialRemoteLabel,
        )
    }

    ProfileSyncResults(state.sync, onDismissSync, onRetrySync)

    profilePendingDeletion?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDeletion = null },
            title = { Text("删除配置") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("确定删除“${profile.realLabel}”吗？本地配置文件也会一并删除。")
                state.resourceUsers[profile.id]?.takeIf { it.isNotEmpty() }?.let { users ->
                    Text("以下配置仍引用此代理资源：${users.joinToString("、")}。删除后引用将显示缺失，可重新选择资源。", color = MaterialTheme.colorScheme.error)
                }
            } },
            confirmButton = {
                TextButton(
                    onClick = {
                        profilePendingDeletion = null
                        onDelete(profile)
                    },
                    enabled = canChangeFiles,
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
            onSave = onReorderProfiles,
            error = state.orderError,
            revision = state.orderRevision,
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

    extensionProfileId?.let { id ->
        state.profiles.firstOrNull { it.id == id }?.let { profile ->
            if (extensionController != null) ProfileExtensionRoute(id, extensionController, scripts, canChangeFiles,
                { extensionProfileId = null }, onOpenScripts, { onOpenDnsEditor(profile) }, { onOpenProvidersEditor(profile) })
            else ProfileExtensionPage(profile.realLabel, canChangeFiles, { extensionProfileId = null },
                { onOpenDnsEditor(profile) }, { onOpenProvidersEditor(profile) }, { scriptBindingProfileId = id })
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
        } else if (profile.type == ProfileType.FILE) {
            FileProfileEditorDialog(editorState, readOnly = isProfileTransitioning(runtimeState), onDismiss = onCloseEditor,
                onBeginPick = onBeginFileReplacementPick, onFilePicked = onFileReplacementPicked,
                onClearReplacement = onClearFileReplacement, onSave = onSaveFileEditor,
                onSaveWithLabel = onSaveFileEditorWithLabel, onOpenDocument = onOpenDocument?.let { open -> { open(false) } }, onMetadataChange = onFileMetadataChange, onPersistPick = onPersistProfilePick,
                onLaunchPick = onLaunchFilePick)
        } else {
            ProfileEditorDialog(
                profile = profile,
                initialContent = editorState.content,
                draftContent = editorState.draftContent,
                onContentChange = onCustomDraftChange,
                saving = editorState.isSaving,
                isNewDraft = editorState.isNew,
                hasPersistedChanges = editorState.hasPersistedChanges,
                overview = editorState.overview,
                moreEditorState = moreEditorState,
                onOpenMoreEditor = onOpenMoreEditor,
                onCloseMoreEditor = onCloseMoreEditor,
                onSaveMoreEditor = onSaveMoreEditor,
                customImportState = customImportState,
                onPickCustomImportFile = onPickCustomImportFile,
                onImportCustomUrl = onImportCustomUrl,
                onConfirmCustomImport = onConfirmCustomImport,
                onCancelCustomImport = onCancelCustomImport,
                onRename = onRename,
                onPreview = onPreviewProfile,
                onDismiss = onCloseEditor,
                onDiscard = onDiscardEditor,
                onFinishNewDraft = onFinishNewDraftEditor,
                onSave = onSaveEditor,
                onOpenRulesEditor = onOpenRulesEditor,
                onOpenProxiesEditor = onOpenProxiesEditor,
                onOpenProxyGroupsEditor = onOpenProxyGroupsEditor,
                onOpenSingleProxyGroupEditor = onOpenSingleProxyGroupEditor,
                onOpenProvidersEditor = onOpenProvidersEditor,
                onOpenDnsEditor = onOpenDnsEditor,
                onOpenDialersEditor = onOpenDialersEditor,
                onOpenScriptBinding = { scriptBindingProfileId = it.id },
            )
        }
    }

    remoteEditorState.profile?.let { profile ->
        RemoteProfileEditorDialog(
            profile = profile,
            saving = remoteEditorState.isSaving,
            onDismiss = onCloseEditor,
            onSave = onSaveRemoteEditor,
            onSaveWithAgeKey = onSaveRemoteEditorWithAgeKey,
            state = remoteEditorState,
            onDraftChange = onRemoteDraftChange,
            onOpenDocument = { onOpenDocument?.invoke(true) },
            onBeginPick = onBeginRemotePick, onFilePicked = onRemoteFilePicked,
            onClearReplacement = onClearRemoteReplacement, onRetryRead = onRetryRemoteRead, onPersistPick = onPersistProfilePick,
            onLaunchPick = onLaunchRemotePick,
        )
    }

    ProfileDocumentEditor(documentEditorState, onDocumentChange, onDocumentSave, onDocumentClose)

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
                catalog = rulesEditorState.catalog,
                saveError = rulesEditorState.error,
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
                sourceContent = proxyGroupsEditorState.sourceContent,
                resourceProfiles = state.profiles,
                initialGroupIndex = proxyGroupsEditorState.initialGroupIndex,
                onSaveDraft = onSaveProxyGroupsDraft,
                onValidateFilters = onValidateGroupFilters,
                recentIcons = recentGroupIcons, onRecordIcon = onRecordGroupIcon, onReadIcon = onReadGroupIcon,
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
                sourceContent = providersEditorState.sourceContent,
                resourceProfiles = state.profiles,
                ruleResources = ruleResources,
                onManageRuleResources = onManageRuleResources,
                initialProxyProviderOverrides = providersEditorState.proxyProviderOverrides,
                saveRevision = providersEditorState.revision,
                saveError = providersEditorState.error,
                onValidateFilters = onValidateGroupFilters,
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
            ProfileDialersPage(
                state = dialersEditorState,
                onDismiss = onCloseDialersEditor,
                onSetTarget = onSetDialerTarget,
                onSaveText = onSaveDialers,
            )
        }
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
                            ,
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
                            ,
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
                                ,
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
                                ,
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                    { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                IconButton(onClick = requestDismiss, modifier = Modifier.testTag("connections_detail_close")) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.testTag("action:关闭连接详情"))
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
                    .clickable { onCopyText(value) }
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
        modifier = Modifier.fillMaxWidth().testTag("connection_detail_chains"),
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

    ) { requestDismiss ->
        // 作者 long：Flutter DNS 详情列表两侧留白为 16dp，保持基本信息、错误和答案共用该对齐线。
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("DNS 详情", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = requestDismiss, modifier = Modifier.testTag("dns_detail_close")) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.testTag("action:关闭 DNS 详情"))
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
                                .clickable { copyValue(query.error) }
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
                                    .clickable { copyValue(answer) }
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

/** 作者 long：长按只调整排序草稿；取消、后台中断手势都不落库，保存失败保留顺序供重试。 */
@Composable
internal fun ProfileReorderDialog(
    profiles: List<Profile>, saving: Boolean, onDismiss: () -> Unit, onSave: (List<Long>) -> Unit,
    error: String? = null, revision: Long = 0,
) {
    var orderedIds by rememberSaveable { mutableStateOf(profiles.map(Profile::id)) }
    val openedRevision = remember { revision }
    LaunchedEffect(revision) { if (revision > openedRevision) onDismiss() }
    LaunchedEffect(profiles.map(Profile::id)) {
        val latest = profiles.map(Profile::id)
        orderedIds = orderedIds.filter { it in latest } + latest.filterNot { it in orderedIds }
    }
    val latest = profiles.associateBy(Profile::id)
    val ordered = orderedIds.mapNotNull(latest::get)
    var discard by remember { mutableStateOf(false) }
    fun exit() { if (!saving) { if (orderedIds != profiles.map(Profile::id)) discard = true else onDismiss() } }
    ProfileEditorPage(::exit, { Text("排序配置") }, {
        Column(Modifier.fillMaxSize().testTag("profiles_order_page"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("长按拖动调整顺序，保存后生效。", style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profiles_order_error")) }
            ResourceOrderList(ordered.map { ResourceOrderItem(it.id.toString(), it.realLabel, it.type.name) },
                saving, "profiles", { ids -> orderedIds = ids.map(String::toLong) }, Modifier.weight(1f))
        }
    }, confirmButton = {
        TextButton({ onSave(orderedIds) }, enabled = !saving && ordered.isNotEmpty(), modifier = Modifier.testTag("profiles_order_save")) { Text(if (saving) "正在保存" else "保存排序") }
    }, dismissButton = { TextButton(::exit, enabled = !saving) { Text("取消") } }, backTag = "profiles_order_back")
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃排序修改？") },
        confirmButton = { TextButton({ discard = false; onDismiss() }) { Text("放弃") } },
        dismissButton = { TextButton({ discard = false }) { Text("继续排序") } })
}

/**
 * 作者 long：脚本正文、元数据和 Profile 绑定都通过 ViewModel 回写；核心启动时再由服务层
 * 在暂存配置上执行绑定脚本，原始 Profile 文件始终保持可编辑和可回退。
 */
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
    onCopyLink: () -> Unit = {},
    onShowQr: () -> Unit = {},
    onExport: () -> Unit = {},
    exportBusy: Boolean = false,
    onPreview: () -> Unit = {},
    onExtend: () -> Unit = {},
    modifier: Modifier = Modifier,
    canEdit: Boolean = canMutate,
    canDelete: Boolean = canMutate,
) {
    var showActions by remember(profile.id, canMutate, canEdit, canDelete, exportBusy) { mutableStateOf(false) }
    var showMore by remember(profile.id, canMutate, canEdit, canDelete, exportBusy) { mutableStateOf(false) }
    var showUpdateError by remember(profile.id) { mutableStateOf(false) }
    val menuAnchor = remember(profile.id) { AppContextMenuAnchor() }
    val expiryDate = profile.subscriptionInfo?.expireAtMillis?.let { millis ->
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(millis))
    }

    Box(modifier.testTag(if (selected) "profile_actions_selected" else "profile_actions_${profile.id}")) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .appFocusRing(AppSuperellipseShape(24.dp))
                .clip(AppSuperellipseShape(24.dp))
                .appContextMenuAnchor(menuAnchor) { showMore = false; showActions = true }
                .combinedClickable(onClick = onSelect,
                    onLongClick = { menuAnchor.open(); showMore = false; showActions = true })
                .testTag("profile_card_${profile.id}"),
            shape = AppSuperellipseShape(24.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = dashboardCardColor()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.realLabel,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        expiryDate?.let { date ->
                            Surface(
                                modifier = Modifier.padding(start = 8.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    date,
                                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 1.dp)
                                        .testTag("profile_expiry_${profile.id}"),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = profileSubtitle(profile),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (profile.type != ProfileType.CUSTOM) {
                    Text(
                        text = when {
                            profile.lastUpdateError != null -> "失败"
                            profile.lastUpdateAt != null -> DateUtils.getRelativeTimeSpanString(profile.lastUpdateAt).toString()
                            else -> "未同步"
                        },
                        modifier = Modifier.padding(start = 8.dp).widthIn(max = 80.dp)
                            .clickable(enabled = profile.lastUpdateError != null) { showUpdateError = true }
                            .testTag("profile_update_status_${profile.id}"),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        color = if (profile.lastUpdateError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
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
                            AppGlyphIcon(AppGlyphs.check, modifier = Modifier.size(18.dp).testTag("action:已选中"), filled = true)
                        }
                    }
                }

                Box {
                    AppDropdownMenu(expanded = showActions, onDismissRequest = { showActions = false },
                        anchor = menuAnchor.point, carriedPointer = menuAnchor.carry, placement = AppMenuPlacement.BELOW_POINT, avoid = menuAnchor.bounds,
                        pageKey = showMore,
                        onNavigateBack = if (showMore) ({ showMore = false }) else null) { level ->
                        if (level as Boolean) {
                            AppDropdownMenuItem(
                                text = { Text("更多") },
                                onClick = { showMore = false }, dismissOnClick = false, isBack = true,
                            )
                            AppDropdownMenuItem(
                                text = { Text("运行配置") },
                                leadingIcon = { AppGlyphIcon(AppGlyphs.eye) },
                                onClick = { showActions = false; onPreview() },
                                modifier = Modifier.testTag("profile_preview_${profile.id}"),
                            )
                            if (profile.type == ProfileType.URL) {
                                AppDropdownMenuItem(
                                    text = { Text("复制链接") },
                                    leadingIcon = { AppGlyphIcon(AppGlyphs.copy) },
                                    onClick = { showActions = false; onCopyLink() },
                                    modifier = Modifier.testTag("profile_copy_link_${profile.id}"),
                                )
                                AppDropdownMenuItem(
                                    text = { Text("二维码") },
                                    leadingIcon = { AppGlyphIcon(AppGlyphs.qrCode) },
                                    onClick = { showActions = false; onShowQr() },
                                    modifier = Modifier.testTag("profile_show_qr_${profile.id}"),
                                )
                            }
                            AppDropdownMenuItem(
                                text = { Text(if (exportBusy) "正在导出…" else "导出") },
                                leadingIcon = { AppGlyphIcon(AppGlyphs.export) },
                                onClick = { showActions = false; onExport() },
                                enabled = !exportBusy,
                                modifier = Modifier.testTag("profile_export_${profile.id}"),
                            )
                        } else {
                            AppDropdownMenuItem(
                                text = { Text("编辑") },
                                leadingIcon = { AppGlyphIcon(AppGlyphs.edit) },
                                onClick = { showActions = false; onEdit() }, enabled = canEdit,
                                modifier = Modifier.testTag("profile_edit_${profile.id}"),
                            )
                            if (profile.type == ProfileType.CUSTOM) {
                                AppDropdownMenuItem(
                                    text = { Text("重命名") },
                                    leadingIcon = { AppGlyphIcon(AppGlyphs.textShort) },
                                    onClick = { showActions = false; onRename() },
                                    modifier = Modifier.testTag("profile_rename_${profile.id}"),
                                    enabled = canMutate,
                                )
                                AppDropdownMenuItem(
                                    text = { Text("运行配置") },
                                    leadingIcon = { AppGlyphIcon(AppGlyphs.eye) },
                                    onClick = { showActions = false; onPreview() },
                                    modifier = Modifier.testTag("profile_preview_${profile.id}"),
                                )
                            } else {
                                AppDropdownMenuItem(text = { Text("扩展") },
                                    leadingIcon = { AppGlyphIcon(AppGlyphs.puzzle) },
                                    onClick = { showActions = false; onExtend() },
                                    modifier = Modifier.testTag("profile_extend_${profile.id}"))
                            }
                            if (profile.type == ProfileType.URL) {
                                AppDropdownMenuItem(text = { Text("同步") }, leadingIcon = { AppGlyphIcon(AppGlyphs.sync) },
                                    onClick = { showActions = false; onUpdate() }, enabled = canMutate)
                            }
                            if (profile.type != ProfileType.CUSTOM) {
                                AppDropdownMenuItem(
                                    text = { Text("更多") },
                                    trailingIcon = { AppMenuSubmenuArrow() },
                                    leadingIcon = { AppGlyphIcon(AppGlyphs.moreCircle) },
                                    onClick = { showMore = true }, dismissOnClick = false, opensSubmenu = true,
                                    modifier = Modifier.testTag("profile_more_${profile.id}"),
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f))
                            AppDropdownMenuItem(
                                text = { Text("删除") }, danger = true,
                                leadingIcon = { AppGlyphIcon(AppGlyphs.delete) },
                                onClick = { showActions = false; onDelete() }, enabled = canDelete,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showUpdateError) AlertDialog(
        onDismissRequest = { showUpdateError = false },
        title = { Text("同步失败") },
        text = { Text(profile.lastUpdateError.orEmpty()) },
        confirmButton = { TextButton(onClick = { showUpdateError = false; onUpdate() }, enabled = canMutate) { Text("重试") } },
        dismissButton = { TextButton(onClick = { showUpdateError = false }) { Text("关闭") } },
    )
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
                modifier = Modifier.fillMaxWidth().testTag("profile_rename_label"),
                label = { Text("名称") },
                supportingText = { Text("名称不能为空，最多 64 个字符") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(normalized) },
                enabled = !saving && normalized.isNotEmpty() && normalized != profile.label,
                modifier = Modifier.testTag("profile_rename_save"),
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

private fun isValidRemoteProfileUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value.trim())
    uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

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
    ProfileEditorPage(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("代理 · ${profile.realLabel}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                modifier = Modifier.testTag("profile_proxies_save"),
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
    sourceContent: String = "",
    initialGroupIndex: Int? = null,
    onSaveDraft: ((com.lonnnnnng.flclash.data.ProxyGroupsSave) -> Unit)? = null,
    onValidateFilters: suspend (List<String>) -> List<String> = { error("过滤表达式校验不可用") },
    recentIcons: List<String> = emptyList(),
    onRecordIcon: (String, Boolean) -> Unit = { _, _ -> },
    onReadIcon: suspend (String) -> String = { error("图片读取不可用") },
    resourceProfiles: List<Profile> = emptyList(),
) {
    VisualProxyGroupsEditor(profile, initialGroups, sourceContent, saving, initialGroupIndex, onDismiss, onSave, onSaveDraft, onValidateFilters, recentIcons, onRecordIcon, onReadIcon, resourceProfiles)
}

/** 作者 long：Provider 入口只做页面投影，正式保存由 ViewModel 回读版本确认，不在 UI 里提前关闭子页。 */
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
    sourceContent: String = "",
    ruleResources: List<com.lonnnnnng.flclash.data.RuleResource> = emptyList(),
    onManageRuleResources: () -> Unit = {},
    resourceProfiles: List<Profile> = emptyList(),
    saveRevision: Long = 0,
    saveError: String? = null,
    onValidateFilters: suspend (List<String>) -> List<String> = { values -> values.map { "" } },
) {
    ProfileProvidersPage(profile, initialProxyProviders, initialRuleProviders, sourceContent, initialProxyProviderOverrides,
        resourceProfiles, ruleResources, saving, saveRevision, saveError, onDismiss,
        onSave = { proxy, rule, fields -> if (onSaveWithOverrides != null) onSaveWithOverrides(proxy, rule, fields) else onSave(proxy, rule) },
        onManageRules = onManageRuleResources, validateFilters = onValidateFilters)
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

    ProfileEditorPage(
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
    ProfileEditorPage(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Dialers · ${profile.realLabel}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    ProviderOptionsPage(providerName, initial, saving, null, onDismiss, onSave)
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

@Composable
internal fun ProfileFileImportPreviewDialog(
    fileName: String,
    fileSizeBytes: Int,
    isValidYaml: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isEncrypted: Boolean = false,
    error: String? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("导入配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fileName, maxLines = 2)
                Text("文件大小：$fileSizeBytes 字节")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_file_import_error")) }
                Text(
                    if (isEncrypted) "age 加密配置，导入时输入密钥并校验"
                    else if (isValidYaml) "YAML 格式校验通过" else "无法读取 YAML 配置，文件未导入",
                    color = if (isValidYaml || isEncrypted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = (isValidYaml || isEncrypted) && !busy,
                modifier = Modifier.testTag("profile_file_import_confirm"),
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("profile_file_import_cancel"),
            ) { Text("取消") }
        },
    )
}

@Composable
internal fun AddProfileDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onPickQrCode: () -> Unit,
    onAddRemote: (label: String, url: String) -> Unit,
    onAddRemoteWithAgeKey: ((String, String, String) -> Unit)? = null,
    onAddRemoteBatch: (List<String>) -> Unit = { urls -> urls.forEach { onAddRemote("", it) } },
    existingUrls: Set<String> = emptySet(),
    onAddCustom: (label: String) -> Unit,
    sourceAnchor: IntRect? = null,
    menuSource: AppMenuSource? = null,
    initialRemoteUrl: String? = null,
    initialRemoteLabel: String? = null,
) {
    var label by rememberSaveable { mutableStateOf(initialRemoteLabel.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(initialRemoteUrl.orEmpty()) }
    var ageKey by remember { mutableStateOf("") }
    // 作者 long：清理监听属于整个导入表单，切到批量或来源页后隐藏字段也不能留下密钥。
    ClearAgeInputOnBackground { ageKey = "" }
    var batchMode by rememberSaveable { mutableStateOf(false) }
    var batchInput by rememberSaveable { mutableStateOf("") }
    // 作者 long：来源与表单分步呈现，避免订阅字段和自定义配置字段同时挤在添加入口。
    var step by rememberSaveable { mutableStateOf(if (initialRemoteUrl != null) AddProfileStep.REMOTE else AddProfileStep.SOURCE) }
    val busyDismiss = { if (!busy) onDismiss() }
    if (step == AddProfileStep.SOURCE) {
        ProfileSourceMenu(sourceAnchor, busy, busyDismiss, onPickQrCode, onPickFile,
            { step = AddProfileStep.REMOTE }, { onAddCustom("") }, menuSource)
        return
    }
    AlertDialog(
        onDismissRequest = busyDismiss,
        title = { Text("从链接导入") },
        text = {
            val preview = parseProfileUrlImport(batchInput, existingUrls)
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryTabRow(selectedTabIndex = if (batchMode) 1 else 0) {
                    Tab(
                        selected = !batchMode,
                        onClick = { batchMode = false },
                        modifier = Modifier.testTag("profile_add_mode_single"),
                        text = { Text("单条") },
                    )
                    Tab(
                        selected = batchMode,
                        onClick = { ageKey = ""; batchMode = true },
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
                    AgeSecretKeyField(ageKey, { ageKey = it }, !busy, "profile_add_remote_age_key")
                }
            }
        },
        confirmButton = {
            val preview = parseProfileUrlImport(batchInput, existingUrls)
            TextButton(
                onClick = {
                    if (batchMode) onAddRemoteBatch(preview.urls)
                    else if (onAddRemoteWithAgeKey != null) onAddRemoteWithAgeKey(label, url, ageKey.trim())
                    else onAddRemote(label, url)
                },
                enabled = !busy && if (batchMode) {
                    preview.urls.isNotEmpty() && preview.invalidCount == 0
                } else {
                    url.isNotBlank()
                },
                modifier = Modifier.testTag("profile_add_submit"),
            ) { Text(if (batchMode) "导入 ${preview.urls.size} 条" else "导入") }
        },
        dismissButton = {
            TextButton(onClick = { step = AddProfileStep.SOURCE }, enabled = !busy) { Text("返回") }
        },
    )
}

private enum class AddProfileStep {
    SOURCE,
    REMOTE,
}
