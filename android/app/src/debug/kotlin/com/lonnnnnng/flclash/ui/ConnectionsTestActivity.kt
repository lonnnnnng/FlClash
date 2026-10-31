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
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/**
 * 作者 long：连接页截图需要固定核心状态和连接数据，Debug 宿主只渲染正式 ConnectionsCard，
 * 不读取 Room、网络、VPN、Mihomo 核心或工作区配置，避免环境差异进入视觉基线。
 */
class ConnectionsTestActivity : ComponentActivity() {
    var lastCopiedValue: String? = null
        private set
    private var scenario by mutableStateOf(ConnectionsScenario.STOPPED)
    private var fontScaleValue by mutableStateOf(1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 作者 long：重建验收必须保留相同输入，才能区分详情恢复失败和测试场景被意外重置。
        savedInstanceState?.let {
            scenario = ConnectionsScenario.valueOf(it.getString("scenario", ConnectionsScenario.STOPPED.name))
            fontScaleValue = it.getFloat("fontScale", 1f)
        }
        setContent {
            val runtimeState = when (scenario) {
                ConnectionsScenario.STOPPED -> CoreRuntimeStatus.STOPPED
                else -> CoreRuntimeStatus.RUNNING
            }
            val state = when (scenario) {
                ConnectionsScenario.STOPPED -> ConnectionsUiState()
                ConnectionsScenario.RUNNING -> ConnectionsUiState(
                    connections = listOf(sampleConnection()),
                )
                ConnectionsScenario.RUNNING_EMPTY -> ConnectionsUiState()
                ConnectionsScenario.RUNNING_MINIMAL -> ConnectionsUiState(
                    connections = listOf(sampleConnection().let { connection ->
                        connection.copy(metadata = connection.metadata.copy(
                            uid = 0L, process = "", destinationGeoIp = emptyList(),
                            destinationIpAsn = "", dnsMode = "",
                        ))
                    }),
                )
                ConnectionsScenario.RUNNING_LONG -> ConnectionsUiState(
                    connections = listOf(sampleConnection().let { connection ->
                        connection.copy(
                            metadata = connection.metadata.copy(
                                host = LONG_HOST,
                                processPath = LONG_PROCESS_PATH,
                                specialProxy = "GLOBAL",
                                specialRules = "special-rule",
                            ),
                            rulePayload = LONG_HOST,
                        )
                    }),
                )
            }
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                // 作者 long：字体矩阵只改变文字缩放，不改变设备密度，确保连接详情的换行差异可重复。
                LocalDensity provides Density(baseDensity.density, fontScale = fontScaleValue),
            ) {
                KClashTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .testTag("connections_page"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ConnectionsCard(
                            state = state,
                            runtimeState = runtimeState,
                            onCopyText = { lastCopiedValue = it },
                        )
                    }
                }
            }
        }
    }

    fun showRunningScenario() {
        scenario = ConnectionsScenario.RUNNING
    }

    fun showEmptyRunningScenario() {
        scenario = ConnectionsScenario.RUNNING_EMPTY
    }

    fun showStoppedScenario() {
        scenario = ConnectionsScenario.STOPPED
    }

    fun showLongRunningScenario() {
        scenario = ConnectionsScenario.RUNNING_LONG
    }

    fun showMinimalRunningScenario() {
        scenario = ConnectionsScenario.RUNNING_MINIMAL
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("scenario", scenario.name)
        outState.putFloat("fontScale", fontScaleValue)
        super.onSaveInstanceState(outState)
    }

    fun setFontScale(value: Float) {
        fontScaleValue = value
    }

    private fun sampleConnection() = CoreConnection(
        id = "connection-visual",
        upload = 12_345L,
        download = 67_890L,
        start = "2026-10-28T00:00:00Z",
        metadata = CoreConnectionMetadata(
            network = "tcp",
            sourceIp = "10.0.0.2",
            sourcePort = "54321",
            destinationIp = "192.0.2.10",
            destinationPort = "443",
            host = "api.example.test",
            process = "com.example.client",
            processPath = "/data/app/client",
            remoteDestination = "api.example.test:443",
            uid = 10_000L,
            specialProxy = "",
            specialRules = "",
            destinationGeoIp = listOf("US", "Example ISP"),
            destinationIpAsn = "AS64496",
            dnsMode = "fake-ip",
        ),
        chains = listOf("GLOBAL", "DIRECT"),
        rule = "MATCH",
        rulePayload = "",
    )

    companion object {
        const val LONG_HOST = "a-long-hostname-for-connection-detail-scroll-verification.api.example.test"
        const val LONG_PROCESS_PATH = "/data/app/com.example.client/long-installation-directory/native-library/arm64/libclient.so"
    }
}

private enum class ConnectionsScenario {
    STOPPED,
    RUNNING,
    RUNNING_EMPTY,
    RUNNING_LONG,
    RUNNING_MINIMAL,
}
