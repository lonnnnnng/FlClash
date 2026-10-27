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
import com.lonnnnnng.flclash.ui.KClashApp
import com.lonnnnnng.flclash.ui.DiagnosticSessionUiState
import com.lonnnnnng.flclash.ui.DiagnosticSessionStatus
import com.lonnnnnng.flclash.ui.MainViewModel
import com.lonnnnnng.flclash.ui.ProfilesViewModel
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var backupRepository: BackupRepository
    @Inject lateinit var webDavBackupRepository: WebDavBackupRepository
    private val viewModel: MainViewModel by viewModels()
    private val profilesViewModel: ProfilesViewModel by viewModels()
    private val proxyGroupsViewModel: ProxyGroupsViewModel by viewModels()
    private val scriptsViewModel: ScriptsViewModel by viewModels()
    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startRuntime()
        } else {
            viewModel.reportError("VPN 权限未授予")
        }
    }

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
        if (uri == null || archive == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output -> output.write(archive) }
                ?: error("无法打开备份导出文件")
        }.onFailure { error -> viewModel.reportError(error.message ?: "导出备份失败") }
    }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val request = pendingBackupRestore
        pendingBackupRestore = null
        if (uri == null || request == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取备份文件")
                backupRepository.restore(bytes, request.first, request.second)
                profilesViewModel.refreshSelection()
            }.onSuccess {
                viewModel.reportError("备份恢复完成")
            }.onFailure { error ->
                viewModel.reportError(error.message ?: "恢复备份失败")
            }
        }
    }

    private var batteryOptimizationIgnored by mutableStateOf(false)
    private var pendingLogExport: List<CoreEvent.Log> = emptyList()
    private var pendingDiagnosticExport: DiagnosticSessionUiState? = null
    private var pendingDiagnosticHistoryExport: List<RuntimeDiagnosticEvent> = emptyList()
    private var pendingBackupExport: ByteArray? = null
    private var pendingBackupRestore: Pair<BackupRestoreScope, BackupRestoreStrategy>? = null
    private var webDavCredentials by mutableStateOf<WebDavCredentials?>(null)
    private var webDavBusy by mutableStateOf(false)
    private var initialDeepLinkHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        batteryOptimizationIgnored = BatteryOptimization.isIgnored(this)
        webDavCredentials = runCatching { webDavBackupRepository.readCredentials() }
            .onFailure { error -> viewModel.reportError(error.message ?: "无法读取 WebDAV 凭据") }
            .getOrNull()
        setContent {
            KClashTheme {
                KClashApp(
                    runtimeViewModel = viewModel,
                    profilesViewModel = profilesViewModel,
                    proxyGroupsViewModel = proxyGroupsViewModel,
                    scriptsViewModel = scriptsViewModel,
                    onStartRuntime = ::requestRuntimeStart,
                    onStopRuntime = viewModel::stopRuntime,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    onRequestBatteryOptimization = ::requestBatteryOptimization,
                    onExportLogs = ::requestLogExport,
                    onExportDiagnostic = ::requestDiagnosticExport,
                    onExportDiagnosticHistory = ::requestDiagnosticHistoryExport,
                    onExportBackup = ::requestBackupExport,
                    onImportBackup = ::requestBackupImport,
                    webDavCredentials = webDavCredentials,
                    webDavBusy = webDavBusy,
                    onSaveWebDavCredentials = ::saveWebDavCredentials,
                    onClearWebDavCredentials = ::clearWebDavCredentials,
                    onProbeWebDav = ::probeWebDav,
                    onUploadWebDav = ::uploadWebDav,
                    onDownloadWebDav = ::downloadWebDav,
                )
            }
        }
        requestNotificationPermissionIfNeeded()
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
        batteryOptimizationIgnored = BatteryOptimization.isIgnored(this)
        if (!initialDeepLinkHandled) {
            initialDeepLinkHandled = true
            // 作者 long：等 Compose 建立错误收集器后再消费首个链接，避免无效 Deep Link 的提示在首帧前丢失。
            window.decorView.post { handleDeepLink(intent) }
        }
    }

    override fun onStart() {
        super.onStart()
        // 作者 long：只有首页 Activity 对用户可见时才读取运行统计和连接，后台运行的核心不应被 UI 轮询拖住。
        viewModel.setObservationVisible(true)
    }

    override fun onStop() {
        // 作者 long：先关闭轮询闸门再进入后台，避免生命周期切换期间继续启动下一轮 JNI 读取。
        viewModel.setObservationVisible(false)
        super.onStop()
    }

    private fun requestBatteryOptimization() {
        if (!BatteryOptimization.openSettings(this)) {
            viewModel.reportError("无法打开电池优化设置")
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
        lifecycleScope.launch {
            runCatching { backupRepository.createArchive() }
                .onSuccess { archive ->
                    pendingBackupExport = archive
                    backupExportLauncher.launch("kclash-backup-${System.currentTimeMillis()}.zip")
                }
                .onFailure { error -> viewModel.reportError(error.message ?: "导出备份失败") }
        }
    }

    private fun requestBackupImport(scope: BackupRestoreScope, strategy: BackupRestoreStrategy) {
        pendingBackupRestore = scope to strategy
        backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    private fun saveWebDavCredentials(credentials: WebDavCredentials) {
        launchWebDavOperation("WebDAV 凭据已保存") {
            webDavBackupRepository.saveCredentials(credentials)
            webDavCredentials = credentials
        }
    }

    private fun clearWebDavCredentials() {
        launchWebDavOperation("WebDAV 凭据已清除") {
            webDavBackupRepository.clearCredentials()
            webDavCredentials = null
        }
    }

    private fun probeWebDav(credentials: WebDavCredentials) {
        launchWebDavOperation("WebDAV 连接成功") {
            webDavBackupRepository.probe(credentials)
            // 作者 long：先完成认证和目录探测，再替换 Keystore 中的凭据；失败时保留上一份可用配置。
            webDavBackupRepository.saveCredentials(credentials)
            webDavCredentials = credentials
        }
    }

    private fun uploadWebDav(credentials: WebDavCredentials, scope: BackupRestoreScope) {
        if (viewModel.runtimeState.value.isRunning) {
            viewModel.reportError("核心运行中，请先停止核心后再备份")
            return
        }
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
        window.decorView.post { handleDeepLink(intent) }
    }

    private fun requestRuntimeStart() {
        // 作者 long：Android 要求每次启动前重新检查授权，不能把上一次同意结果当成永久权限。
        val consentIntent = runCatching { VpnService.prepare(this) }.getOrElse { error ->
            viewModel.reportError(error.message ?: "无法申请 VPN 权限")
            return
        }
        if (consentIntent == null) {
            viewModel.startRuntime()
        } else {
            vpnConsentLauncher.launch(consentIntent)
        }
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
