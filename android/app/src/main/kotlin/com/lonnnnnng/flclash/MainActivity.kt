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
import com.lonnnnnng.flclash.ui.formatDiagnosticForExport
import com.lonnnnnng.flclash.ui.formatLogsForExport
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import com.lonnnnnng.flclash.data.DeepLinkProfileParser
import com.lonnnnnng.flclash.core.CoreEvent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val profilesViewModel: ProfilesViewModel by viewModels()
    private val proxyGroupsViewModel: ProxyGroupsViewModel by viewModels()
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

    private var batteryOptimizationIgnored by mutableStateOf(false)
    private var pendingLogExport: List<CoreEvent.Log> = emptyList()
    private var pendingDiagnosticExport: DiagnosticSessionUiState? = null
    private var initialDeepLinkHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        batteryOptimizationIgnored = BatteryOptimization.isIgnored(this)
        setContent {
            KClashTheme {
                KClashApp(
                    runtimeViewModel = viewModel,
                    profilesViewModel = profilesViewModel,
                    proxyGroupsViewModel = proxyGroupsViewModel,
                    onStartRuntime = ::requestRuntimeStart,
                    onStopRuntime = viewModel::stopRuntime,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    onRequestBatteryOptimization = ::requestBatteryOptimization,
                    onExportLogs = ::requestLogExport,
                    onExportDiagnostic = ::requestDiagnosticExport,
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
