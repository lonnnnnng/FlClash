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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.AlwaysOnVpnState
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/**
 * 作者 long：Dashboard 截图需要固定系统卡片和按应用代理数据，Debug 宿主只渲染正式卡片组合，
 * 不读取 Room、系统电池/Always-on 状态、已安装应用或 Mihomo 核心，避免把设备环境差异写入视觉基线。
 */
class DashboardTestActivity : ComponentActivity() {
    private var scenario by mutableStateOf(DashboardScenario.DEFAULT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val accessEnabled = scenario == DashboardScenario.DEFAULT
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
                        state = CoreRuntimeStatus.STOPPED,
                        onStart = {},
                        onStop = {},
                    )
                    RuntimeStatsCard(
                        state = RuntimeStatsUiState(),
                        runtimeState = CoreRuntimeStatus.STOPPED,
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
                        runtimeState = CoreRuntimeStatus.STOPPED,
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
                        state = ProbeUiState(),
                        diagnostic = DiagnosticSessionUiState(),
                        diagnosticHistory = emptyList(),
                        runtimeState = CoreRuntimeStatus.STOPPED,
                        profile = null,
                        onProbe = {},
                        onExportDiagnostic = {},
                        onShowDiagnosticHistory = {},
                        onExportDiagnosticHistory = {},
                    )
                }
            }
        }
    }

    fun showSystemReadyScenario() {
        scenario = DashboardScenario.SYSTEM_READY
    }
}

private enum class DashboardScenario {
    DEFAULT,
    SYSTEM_READY,
}
