package com.lonnnnnng.flclash

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import com.lonnnnnng.flclash.ui.KClashApp
import com.lonnnnnng.flclash.ui.DiagnosticSessionUiState
import com.lonnnnnng.flclash.ui.DiagnosticSessionStatus
import com.lonnnnnng.flclash.ui.MainViewModel
import com.lonnnnnng.flclash.ui.OutboundIpViewModel
import com.lonnnnnng.flclash.ui.ProfilesViewModel
import com.lonnnnnng.flclash.ui.AccessControlViewModel
import com.lonnnnnng.flclash.ui.ProxyGroupsViewModel
import com.lonnnnnng.flclash.ui.ScriptsViewModel
import com.lonnnnnng.flclash.ui.formatDiagnosticForExport
import com.lonnnnnng.flclash.ui.formatLogsForExport
import com.lonnnnnng.flclash.ui.formatRuntimeDiagnosticHistoryForExport
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import com.lonnnnnng.flclash.data.DeepLinkProfileParser
import com.lonnnnnng.flclash.data.BackupRepository
import com.lonnnnnng.flclash.data.BackupRestoreScope
import com.lonnnnnng.flclash.data.BackupRestoreStrategy
import com.lonnnnnng.flclash.data.WebDavBackupRepository
import com.lonnnnnng.flclash.data.WebDavCredentials
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        internal const val EXTRA_DEVELOPER_TOOLS = "kclash_developer_tools"
    }

    @Inject lateinit var onDemandCoordinator: com.lonnnnnng.flclash.data.OnDemandCoordinator
    private val onDemandViewModel: com.lonnnnnng.flclash.ui.OnDemandViewModel by viewModels()
    private var showBackgroundLocationGuide by androidx.compose.runtime.mutableStateOf(false)
    @Inject lateinit var backupRepository: BackupRepository
    @Inject lateinit var webDavBackupRepository: WebDavBackupRepository
    @Inject lateinit var backupUiPreferences: com.lonnnnnng.flclash.data.BackupUiPreferences
    private val viewModel: MainViewModel by viewModels()
    private val intranetIpViewModel: com.lonnnnnng.flclash.ui.IntranetIpViewModel by viewModels()
    private val serviceStatusViewModel: com.lonnnnnng.flclash.ui.ServiceStatusViewModel by viewModels()
    private val profileStatisticsViewModel: com.lonnnnnng.flclash.ui.ProfileStatisticsViewModel by viewModels()
    private val memoryViewModel: com.lonnnnnng.flclash.ui.MemoryViewModel by viewModels()
    private val outboundIpViewModel: OutboundIpViewModel by viewModels()
    private val runtimeConfigPreviewViewModel: com.lonnnnnng.flclash.ui.RuntimeConfigPreviewViewModel by viewModels()
    private val profilesViewModel: ProfilesViewModel by viewModels()
    private val profileSharingViewModel: com.lonnnnnng.flclash.ui.ProfileSharingViewModel by viewModels()
    private val accessControlViewModel: AccessControlViewModel by viewModels()
    private val proxyGroupsViewModel: ProxyGroupsViewModel by viewModels()
    private val ruleResourcesViewModel: com.lonnnnnng.flclash.ui.RuleResourcesViewModel by viewModels()
    private val localProxiesViewModel: com.lonnnnnng.flclash.ui.LocalProxiesViewModel by viewModels()
    private val resourcesViewModel: com.lonnnnnng.flclash.ui.ResourcesViewModel by viewModels()
    private val filterPresetsViewModel: com.lonnnnnng.flclash.ui.FilterPresetsViewModel by viewModels()
    private val scriptsViewModel: ScriptsViewModel by viewModels()
    private val ipNetworkProperties = mutableMapOf<android.net.Network, String>()
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onLost(network: android.net.Network) = synchronized(ipNetworkProperties) {
            if (ipNetworkProperties.remove(network) != null) outboundIpViewModel.networkChanged()
        }
        override fun onLinkPropertiesChanged(network: android.net.Network, properties: android.net.LinkProperties) {
            synchronized(ipNetworkProperties) {
                // 作者 long：注册回调/回到前台会重发同一链路，只有地址、DNS、路由等属性真变了才失效出口。
                val snapshot = properties.toString()
                if (ipNetworkProperties.put(network, snapshot) != snapshot) {
                    outboundIpViewModel.networkChanged()
                }
            }
        }
    }
    private var observingNetwork = false
    private var pendingVpnRestart = false
    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            completeRuntimeRequest()
        } else {
            pendingVpnRestart = false
            viewModel.reportError("VPN 权限未授予")
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onDemandCoordinator.refresh()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showBackgroundLocationGuide = true
        }
    }
    private val backgroundLocationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { onDemandCoordinator.refresh() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val logExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val entries = pendingLogExport
        pendingLogExport = emptyList()
        if (uri == null || entries.isEmpty()) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(formatLogsForExport(entries).toByteArray(Charsets.UTF_8))
            } ?: error("无法打开导出文件")
        }.onFailure { error ->
            viewModel.reportError(error.message ?: "导出核心日志失败")
        }
    }

    private val diagnosticExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val session = pendingDiagnosticExport
        pendingDiagnosticExport = null
        if (uri == null || session == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(formatDiagnosticForExport(session).toByteArray(Charsets.UTF_8))
            } ?: error("无法打开诊断导出文件")
        }.onFailure { error ->
            viewModel.reportError(error.message ?: "导出诊断证据失败")
        }
    }

    private val diagnosticHistoryExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val events = pendingDiagnosticHistoryExport
        pendingDiagnosticHistoryExport = emptyList()
        if (uri == null || events.isEmpty()) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(formatRuntimeDiagnosticHistoryForExport(events).toByteArray(Charsets.UTF_8))
            } ?: error("无法打开运行诊断历史导出文件")
        }.onFailure { error ->
            viewModel.reportError(error.message ?: "导出运行诊断历史失败")
        }
    }

    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val archive = pendingBackupExport
        pendingBackupExport = null
        if (uri == null || archive == null) { webDavBusy = false; return@registerForActivityResult }
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { output -> output.write(archive) }
                        ?: error("无法打开备份导出文件")
                }
                viewModel.reportError("备份导出完成")
            } catch (error: Exception) { viewModel.reportError(error.message ?: "导出备份失败") }
            finally { webDavBusy = false }
        }
    }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val request = pendingBackupRestore
        pendingBackupRestore = null
        if (uri == null || request == null) { webDavBusy = false; return@registerForActivityResult }
        lifecycleScope.launch {
            runCatching {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { com.lonnnnnng.flclash.data.readBackupArchive(it) }
                        ?: error("无法读取备份文件")
                }
                backupRepository.restore(bytes, request.first, request.second)
                backupStrategy = backupUiPreferences.read()
                profilesViewModel.refreshSelection()
            }.onSuccess {
                viewModel.reportError("备份恢复完成")
            }.onFailure { error ->
                viewModel.reportError(error.message ?: "恢复备份失败")
            }
            webDavBusy = false
        }
    }

    private var batteryOptimizationIgnored by mutableStateOf(false)
    private var alwaysOnVpnState by mutableStateOf(AlwaysOnVpnState.UNKNOWN)
    private var pendingLogExport: List<CoreEvent.Log> = emptyList()
    private var pendingDiagnosticExport: DiagnosticSessionUiState? = null
    private var pendingDiagnosticHistoryExport: List<RuntimeDiagnosticEvent> = emptyList()
    private var pendingBackupExport: ByteArray? = null
    private var pendingBackupRestore: Pair<BackupRestoreScope, BackupRestoreStrategy>? = null
    private var webDavCredentials by mutableStateOf<WebDavCredentials?>(null)
    private var webDavBusy by mutableStateOf(false)
    private var backupStrategy by mutableStateOf(BackupRestoreStrategy.COMPATIBLE)
    private var webDavConnection by mutableStateOf(com.lonnnnnng.flclash.ui.WebDavConnectionState.UNKNOWN)
    private var initialDeepLinkHandled = false
    private var developerToolsEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingVpnRestart = savedInstanceState?.getBoolean("pending_vpn_restart") ?: false
        showBackgroundLocationGuide = savedInstanceState?.getBoolean("background_location_guide") ?: false
        // 作者 long：订阅请求诊断和系统 VPN 引导只供显式 Debug 验收使用，普通启动及 Release 不增加原版没有的入口。
        developerToolsEnabled = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEVELOPER_TOOLS, false)
        onDemandCoordinator.refresh()
        batteryOptimizationIgnored = BatteryOptimization.isIgnored(this)
        alwaysOnVpnState = AlwaysOnVpn.state(this)
        backupStrategy = backupUiPreferences.read()
        webDavCredentials = runCatching { webDavBackupRepository.readCredentials() }
            .onFailure { error -> viewModel.reportError(error.message ?: "无法读取 WebDAV 凭据") }
            .getOrNull()
        setContent {
            KClashTheme {
                val resources by resourcesViewModel.state.collectAsState()
                val localProxies by localProxiesViewModel.state.collectAsState()
                val filters by filterPresetsViewModel.state.collectAsState()
                CompositionLocalProvider(com.lonnnnnng.flclash.ui.LocalFilterPresets provides filters.rows, com.lonnnnnng.flclash.ui.LocalAddToLocalProxy provides localProxiesViewModel::addFromRuntime, com.lonnnnnng.flclash.ui.LocalGlobalProxies provides localProxies.rows, com.lonnnnnng.flclash.ui.LocalResourceIcons provides resources.iconSets.flatMap { it.icons }, com.lonnnnnng.flclash.ui.LocalResourceIconSets provides resources.iconSets) {
                KClashApp(
                    runtimeViewModel = viewModel,
                    filterPresetsViewModel = filterPresetsViewModel,
                    outboundIpViewModel = outboundIpViewModel,
                    memoryViewModel = memoryViewModel,
                    serviceStatusViewModel = serviceStatusViewModel,
                    intranetIpViewModel = intranetIpViewModel,
                    profilesViewModel = profilesViewModel,
                    profileSharingViewModel = profileSharingViewModel,
                    accessControlViewModel = accessControlViewModel,
                    proxyGroupsViewModel = proxyGroupsViewModel,
                    scriptsViewModel = scriptsViewModel,
                    onDemandViewModel = onDemandViewModel,
                    onRequestLocation = ::requestLocationAccess,
                    ruleResourcesViewModel = ruleResourcesViewModel,
                    resourcesViewModel = resourcesViewModel,
                    localProxiesViewModel = localProxiesViewModel,
                    onStartRuntime = { requestRuntimeStart() },
                    onRestartVpn = { requestRuntimeStart(restart = true) },
                    onStopRuntime = viewModel::stopRuntime,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    alwaysOnVpnState = alwaysOnVpnState,
                    onRequestBatteryOptimization = ::requestBatteryOptimization,
                    onRequestAlwaysOnVpn = ::requestAlwaysOnVpn,
                    onExportLogs = ::requestLogExport,
                    onExportDiagnostic = ::requestDiagnosticExport,
                    onExportDiagnosticHistory = ::requestDiagnosticHistoryExport,
                    onExportBackup = ::requestBackupExport,
                    onImportBackup = ::requestBackupImport,
                    webDavCredentials = webDavCredentials,
                    webDavBusy = webDavBusy,
                    backupStrategy = backupStrategy,
                    onBackupStrategy = ::saveBackupStrategy,
                    webDavConnection = webDavConnection,
                    onSaveWebDavCredentials = ::saveWebDavCredentials,
                    onClearWebDavCredentials = ::clearWebDavCredentials,
                    onProbeWebDav = ::probeWebDav,
                    onUploadWebDav = ::uploadWebDav,
                    onDownloadWebDav = ::downloadWebDav,
                    onExitApp = ::exitOrMinimize,
                    developerToolsEnabled = developerToolsEnabled,
                    runtimeConfigPreviewViewModel = runtimeConfigPreviewViewModel,
                    profileStatisticsViewModel = profileStatisticsViewModel,
                )
                }
                if (showBackgroundLocationGuide) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showBackgroundLocationGuide = false },
                        title = { androidx.compose.material3.Text("允许后台读取 Wi-Fi 名称") },
                        text = { androidx.compose.material3.Text("按需连接需要精确定位来读取 Wi-Fi 名称。请在定位权限中选择“始终允许”，以便应用退到后台后继续匹配排除 SSID；暂不授权时仍可手动启动和停止。") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = {
                            showBackgroundLocationGuide = false
                            // 作者 long：Android 10单独请求后台权限；Android 11起由系统设置提供“始终允许”。
                            if (Build.VERSION.SDK_INT == 29) backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else openLocationAppSettings()
                        }) { androidx.compose.material3.Text("前往授权") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { showBackgroundLocationGuide = false }) { androidx.compose.material3.Text("暂不授权") } },
                    )
                }
            }
        }
        requestNotificationPermissionIfNeeded()
        lifecycleScope.launch {
            viewModel.generalSettings.collect { settings ->
                // 作者 long：隐藏只影响本应用任务，不删除其他任务；回读当前任务ID后更新，恢复开关也能重新出现在最近任务。
                runCatching { getSystemService(android.app.ActivityManager::class.java).appTasks
                    .firstOrNull { it.taskInfo?.id == taskId }?.setExcludeFromRecents(settings.hidden) }
                    .onFailure { viewModel.reportError("无法更新最近任务显示，请重新打开应用后重试") }
            }
        }
        window.decorView.post { if (viewModel.consumeAutoRunRequest()) requestRuntimeStart() }
    }

    private fun exitOrMinimize() {
        if (viewModel.generalSettings.value.minimizeOnExit) moveTaskToBack(true)
        else lifecycleScope.launch {
            // 作者 long：完整退出先停止统一核心和系统VPN，不能仅移除界面而留下仍在接管网络的服务。
            viewModel.stopForExit { finishAndRemoveTask() }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // 作者 long：Android 13 以后通知是运行时权限；前台服务仍可启动，但没有授权时用户看不到 STOP 入口。
        window.decorView.post {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // 作者 long：只观察底层互联网链路，VPN 自身的出现不应造成反复探测；重连和地址变化使旧出口失效。
        if (!observingNetwork) {
            observingNetwork = runCatching {
                val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
                synchronized(ipNetworkProperties) {
                    // 作者 long：后台可能错过断网通知，恢复时先移除已消失的链路，不能无条件清掉仍有效的出口。
                    if (ipNetworkProperties.keys.retainAll(connectivity.allNetworks.toSet())) outboundIpViewModel.networkChanged()
                }
                connectivity.registerNetworkCallback(
                    android.net.NetworkRequest.Builder()
                        .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),
                    networkCallback,
                )
            }.isSuccess
        }
        onDemandCoordinator.refresh()
        batteryOptimizationIgnored = BatteryOptimization.isIgnored(this)
        alwaysOnVpnState = AlwaysOnVpn.state(this)
        if (!initialDeepLinkHandled) {
            initialDeepLinkHandled = true
            // 作者 long：等 Compose 建立错误收集器后再消费首个链接，避免无效 Deep Link 的提示在首帧前丢失。
            window.decorView.post { handleDeepLink(intent) }
        }
    }

    override fun onStart() {
        super.onStart()
        onDemandCoordinator.setForeground(true)
        // 作者 long：只有首页 Activity 对用户可见时才读取运行统计和连接，后台运行的核心不应被 UI 轮询拖住。
        viewModel.setObservationVisible(true)
        outboundIpViewModel.setForeground(true)
        memoryViewModel.setForeground(true)
        serviceStatusViewModel.setForeground(true)
        profileStatisticsViewModel.setForeground(true)
        intranetIpViewModel.setForeground(true)
    }

    override fun onStop() {
        onDemandCoordinator.setForeground(false)
        if (observingNetwork) {
            runCatching { getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
            observingNetwork = false
        }
        // 作者 long：先关闭轮询闸门再进入后台，避免生命周期切换期间继续启动下一轮 JNI 读取。
        viewModel.setObservationVisible(false)
        outboundIpViewModel.setForeground(false)
        memoryViewModel.setForeground(false)
        serviceStatusViewModel.setForeground(false)
        profileStatisticsViewModel.setForeground(false)
        intranetIpViewModel.setForeground(false)
        super.onStop()
    }

    private fun requestLocationAccess() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                showBackgroundLocationGuide = true
            return
        }
        val preferences = getSharedPreferences("on_demand_permissions", MODE_PRIVATE)
        if (preferences.getBoolean("requested", false) && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            openLocationAppSettings()
        } else {
            preferences.edit().putBoolean("requested", true).apply()
            // 作者 long：精确与大致定位同批请求；后台授权必须在前台授权之后单独处理，Android 11起不能合并请求。
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun openLocationAppSettings() {
        runCatching { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName"))) }.onFailure { viewModel.reportError("无法打开应用定位权限设置") }
    }

    private fun requestBatteryOptimization() {
        if (!BatteryOptimization.openSettings(this)) {
            viewModel.reportError("无法打开电池优化设置")
        }
    }

    private fun requestAlwaysOnVpn() {
        if (!AlwaysOnVpn.openSettings(this)) {
            viewModel.reportError("无法打开系统 VPN 设置")
        }
    }

    private fun requestLogExport(entries: List<CoreEvent.Log>) {
        if (entries.isEmpty()) {
            viewModel.reportError("没有可导出的核心日志")
            return
        }
        // 作者 long：先保存本次用户打开导出器时的快照，避免文件选择期间新日志改变导出内容。
        pendingLogExport = entries
        logExportLauncher.launch("kclash-logs-${System.currentTimeMillis()}.txt")
    }

    private fun requestDiagnosticExport(session: DiagnosticSessionUiState) {
        if (session.status == DiagnosticSessionStatus.IDLE) {
            viewModel.reportError("没有可导出的诊断会话")
            return
        }
        // 作者 long：保存一次诊断会话快照，文件选择期间继续到达的事件属于下一次会话，避免证据包边界漂移。
        pendingDiagnosticExport = session
        diagnosticExportLauncher.launch("kclash-diagnostic-${session.sessionId}.txt")
    }

    private fun requestDiagnosticHistoryExport(events: List<RuntimeDiagnosticEvent>) {
        if (events.isEmpty()) {
            viewModel.reportError("没有可导出的运行诊断历史")
            return
        }
        // 作者 long：先冻结历史快照，文件选择期间新事件属于后续时间线，避免导出内容前后不一致。
        pendingDiagnosticHistoryExport = events.toList()
        diagnosticHistoryExportLauncher.launch("kclash-runtime-diagnostics-${System.currentTimeMillis()}.txt")
    }

    private fun requestBackupExport() {
        if (webDavBusy) return
        webDavBusy = true
        lifecycleScope.launch {
            runCatching { backupRepository.createArchive() }
                .onSuccess { archive ->
                    pendingBackupExport = archive
                    backupExportLauncher.launch("kclash-backup-${System.currentTimeMillis()}.zip")
                }
                .onFailure { error -> webDavBusy = false; viewModel.reportError(error.message ?: "导出备份失败") }
        }
    }

    private fun saveBackupStrategy(value: BackupRestoreStrategy) {
        if (webDavBusy) return
        webDavBusy = true
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { backupUiPreferences.write(value) }
                backupStrategy = value
            } catch (_: Exception) { viewModel.reportError("恢复策略保存失败，请重试") }
            finally { webDavBusy = false }
        }
    }

    private fun requestBackupImport(scope: BackupRestoreScope, strategy: BackupRestoreStrategy) {
        if (webDavBusy) return
        webDavBusy = true
        pendingBackupRestore = scope to strategy
        backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    private fun saveWebDavCredentials(credentials: WebDavCredentials) {
        launchWebDavOperation("WebDAV 凭据已保存") {
            webDavBackupRepository.saveCredentials(credentials)
            webDavCredentials = credentials
            webDavConnection = com.lonnnnnng.flclash.ui.WebDavConnectionState.UNKNOWN
        }
    }

    private fun clearWebDavCredentials() {
        launchWebDavOperation("WebDAV 凭据已清除") {
            webDavBackupRepository.clearCredentials()
            webDavCredentials = null
            webDavConnection = com.lonnnnnng.flclash.ui.WebDavConnectionState.UNKNOWN
        }
    }

    private fun probeWebDav(credentials: WebDavCredentials) {
        if (webDavBusy) return
        webDavConnection = com.lonnnnnng.flclash.ui.WebDavConnectionState.CHECKING
        launchWebDavOperation("WebDAV 连接成功") {
            try {
                webDavBackupRepository.probe(credentials)
                webDavConnection = com.lonnnnnng.flclash.ui.WebDavConnectionState.CONNECTED
            } catch (failure: Exception) {
                webDavConnection = com.lonnnnnng.flclash.ui.WebDavConnectionState.FAILED
                throw failure
            }
            // 作者 long：先完成认证和目录探测，再替换 Keystore 中的凭据；失败时保留上一份可用配置。
            webDavBackupRepository.saveCredentials(credentials)
            webDavCredentials = credentials
        }
    }

    private fun uploadWebDav(credentials: WebDavCredentials, scope: BackupRestoreScope) {
        // 作者 long：上传只读取本地快照，不更换核心输入；与本地导出一致，运行时仍可备份。
        launchWebDavOperation("WebDAV 备份已上传并覆盖远程文件") {
            webDavBackupRepository.upload(credentials, scope)
            // 作者 long：远程覆盖成功后才保存新账号，避免认证失败把原本可用的凭据替换掉。
            webDavBackupRepository.saveCredentials(credentials)
            webDavCredentials = credentials
        }
    }

    private fun downloadWebDav(
        credentials: WebDavCredentials,
        scope: BackupRestoreScope,
        strategy: BackupRestoreStrategy,
    ) {
        if (viewModel.runtimeState.value.isRunning) {
            viewModel.reportError("核心运行中，请先停止核心后再恢复备份")
            return
        }
        launchWebDavOperation("WebDAV 备份下载并恢复完成") {
            webDavBackupRepository.downloadAndRestore(credentials, scope, strategy)
            backupStrategy = backupUiPreferences.read()
            // 作者 long：下载和本地恢复都成功后才提交新凭据，远程归档损坏时不改变本地登录配置。
            webDavBackupRepository.saveCredentials(credentials)
            webDavCredentials = credentials
            profilesViewModel.refreshSelection()
        }
    }

    private fun launchWebDavOperation(successMessage: String, action: suspend () -> Unit) {
        if (webDavBusy) return
        webDavBusy = true
        lifecycleScope.launch {
            runCatching { action() }
                .onSuccess { viewModel.reportError(successMessage) }
                .onFailure { error -> viewModel.reportError(error.message ?: "WebDAV 操作失败") }
                .also { webDavBusy = false }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        developerToolsEnabled = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEVELOPER_TOOLS, false)
        window.decorView.post { handleDeepLink(intent) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("background_location_guide", showBackgroundLocationGuide)
        outState.putBoolean("pending_vpn_restart", pendingVpnRestart)
        super.onSaveInstanceState(outState)
    }

    private fun completeRuntimeRequest() {
        val restart = pendingVpnRestart
        pendingVpnRestart = false
        if (restart) viewModel.restartForVpnSettings() else viewModel.startRuntime()
    }

    private fun requestRuntimeStart(restart: Boolean = false) {
        pendingVpnRestart = restart
        // 作者 long：关闭 VPN 的本地代理不申请系统接管权限；重启前先授权，拒绝时保留现有会话。
        if (!viewModel.vpnSettings.value.enabled) {
            completeRuntimeRequest()
            return
        }
        val consentIntent = runCatching { VpnService.prepare(this) }.getOrElse { error ->
            pendingVpnRestart = false
            viewModel.reportError(error.message ?: "无法申请 VPN 权限")
            return
        }
        if (consentIntent == null) completeRuntimeRequest() else vpnConsentLauncher.launch(consentIntent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val raw = intent?.dataString?.trim().orEmpty()
        if (raw.isEmpty() || !DeepLinkProfileParser.isSupported(raw)) return
        val imported = DeepLinkProfileParser.parse(raw)
        if (imported == null) {
            profilesViewModel.reportError("配置 Deep Link 无效")
            return
        }
        profilesViewModel.addRemote(imported.url, imported.label.orEmpty())
    }
}
