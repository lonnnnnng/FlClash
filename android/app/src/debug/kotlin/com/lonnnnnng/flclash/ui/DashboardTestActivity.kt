package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.AlwaysOnVpnState
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticKind
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/**
 * 作者 long：Dashboard 截图需要固定系统卡片和按应用代理数据，Debug 宿主只渲染正式卡片组合，
 * 不读取 Room、系统电池/Always-on 状态、已安装应用或 Mihomo 核心，避免把设备环境差异写入视觉基线。
 */
class DashboardTestActivity : ComponentActivity() {
    private var scenario by mutableStateOf(DashboardScenario.DEFAULT)
    private var fontScaleValue by mutableStateOf(1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val accessEnabled = scenario == DashboardScenario.DEFAULT
            val running = scenario == DashboardScenario.RUNNING
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                // 作者 long：字体矩阵只改变文字缩放，不改变设备密度，确保页面高度变化来自可达性档位本身。
                LocalDensity provides Density(baseDensity.density, fontScale = fontScaleValue),
            ) {
                KClashTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .testTag("dashboard_page"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RuntimeCard(
                            state = if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED,
                            onStart = {},
                            onStop = {},
                        )
                        RuntimeStatsCard(
                            state = if (running) runningStats() else RuntimeStatsUiState(),
                            runtimeState = if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED,
                        )
                        BatteryOptimizationCard(
                            ignored = scenario == DashboardScenario.SYSTEM_READY,
                            onRequest = {},
                        )
                        AlwaysOnVpnCard(
                            state = if (scenario == DashboardScenario.SYSTEM_READY) {
                                AlwaysOnVpnState.KCLASH_ENABLED
                            } else {
                                AlwaysOnVpnState.UNKNOWN
                            },
                            onRequest = {},
                        )
                        AccessControlCard(
                            state = AccessControlUiState(
                                settings = AccessControlProps(
                                    enable = accessEnabled,
                                    mode = AccessControlMode.REJECT_SELECTED,
                                    rejectList = emptyList(),
                                ),
                                persistedSettings = AccessControlProps(
                                    enable = accessEnabled,
                                    mode = AccessControlMode.REJECT_SELECTED,
                                    rejectList = emptyList(),
                                ),
                                apps = listOf(InstalledAppInfo("com.example.mail", "示例邮件")),
                                isLoading = false,
                            ),
                            runtimeState = if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED,
                            onEnabledChange = {},
                            onModeChange = {},
                            onSortChange = {},
                            onFilterSystemAppsChange = {},
                            onFilterNonInternetAppsChange = {},
                            onSearchChange = {},
                            onTogglePackage = {},
                            onSelectVisible = {},
                            onClearVisible = {},
                            onToggleVisible = {},
                            onSmartSelect = {},
                            onExportToClipboard = {},
                            onImportFromClipboard = {},
                            onSave = {},
                        )
                        ProbeCard(
                            state = if (running) runningProbeState() else ProbeUiState(),
                            diagnostic = if (running) runningDiagnosticState() else DiagnosticSessionUiState(),
                            diagnosticHistory = if (running) listOf(runningDiagnosticEvent()) else emptyList(),
                            runtimeState = if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED,
                            profile = if (running) runningProfile() else null,
                            onProbe = {},
                            onExportDiagnostic = {},
                            onShowDiagnosticHistory = {},
                            onExportDiagnosticHistory = {},
                        )
                    }
                }
            }
        }
    }

    fun showSystemReadyScenario() {
        scenario = DashboardScenario.SYSTEM_READY
    }

    fun showRunningScenario() {
        scenario = DashboardScenario.RUNNING
    }

    fun setFontScale(value: Float) {
        fontScaleValue = value
    }
}

private enum class DashboardScenario {
    DEFAULT,
    SYSTEM_READY,
    RUNNING,
}

// 作者 long：运行态视觉夹具使用固定的安全样例数据，验证统计和诊断卡片的排版，不触发真实网络或核心调用。
private fun runningStats() = RuntimeStatsUiState(
    traffic = CoreTraffic(up = 12_345L, down = 67_890L),
    totalTraffic = CoreTraffic(up = 1_234_567L, down = 7_654_321L),
    connectionCount = 3,
)

private fun runningProfile() = Profile.remote(
    id = 99L,
    url = "https://example.invalid/kclash-dashboard-fixture",
    label = "固定诊断样例",
)

private fun runningProbeResult() = CoreProbeResult(
    statusCode = 200,
    delay = 123L,
    url = "https://example.invalid/kclash-dashboard-fixture",
    chains = listOf("固定节点"),
    rule = "Match",
    rulePayload = "DashboardFixture",
    error = "",
    message = "fixture response",
    coreEpoch = 7L,
    routeVersion = 3L,
)

private fun runningConnection() = CoreConnection(
    id = "dashboard-fixture-connection",
    upload = 1_024L,
    download = 2_048L,
    start = "2026-10-29T10:00:00Z",
    metadata = CoreConnectionMetadata(
        network = "tcp",
        sourceIp = "192.0.2.10",
        sourcePort = "42000",
        destinationIp = "192.0.2.20",
        destinationPort = "443",
        host = "example.invalid",
        process = "mihomo",
        processPath = "/data/local/tmp/mihomo",
        remoteDestination = "192.0.2.20:443",
        uid = 1000L,
        specialProxy = "",
        specialRules = "",
    ),
    chains = listOf("固定节点"),
    rule = "Match",
    rulePayload = "DashboardFixture",
)

private fun runningDns() = CoreDnsQuery(
    domain = "example.invalid",
    type = "A",
    initiator = "mihomo",
    upstream = "https://dns.example.invalid/dns-query",
    cached = false,
    answers = listOf("192.0.2.20"),
    rcode = "NOERROR",
    error = "",
    delay = 12L,
    time = "2026-10-29T10:00:00Z",
)

private fun runningLog() = CoreEvent.Log(
    level = "info",
    payload = "dashboard fixture event",
    timestampMillis = 0L,
)

private fun runningDiagnosticEvent() = RuntimeDiagnosticEvent(
    kind = RuntimeDiagnosticKind.NETWORK_AVAILABLE,
    detail = "fixture network available",
    timestampMillis = 0L,
)

private fun runningProbeState() = ProbeUiState(
    targetUrl = "https://example.invalid/kclash-dashboard-fixture",
    result = runningProbeResult(),
)

private fun runningDiagnosticState(): DiagnosticSessionUiState {
    val result = runningProbeResult()
    val flowStats = CoreEventFlowStats(
        parsedBatches = 1L,
        parsedEvents = 3L,
        emittedEvents = 3L,
        lastEventAtMillis = 0L,
    )
    return DiagnosticSessionUiState(
        status = DiagnosticSessionStatus.PASS,
        sessionId = 1L,
        targetUrl = result.url,
        startedAtMillis = 0L,
        finishedAtMillis = 1L,
        result = result,
        assertion = "固定运行态诊断证据完整",
        requests = listOf(runningConnection()),
        dns = listOf(runningDns()),
        logs = listOf(runningLog()),
        runtimeEvents = listOf(runningDiagnosticEvent()),
        eventFlowStats = flowStats,
    )
}
