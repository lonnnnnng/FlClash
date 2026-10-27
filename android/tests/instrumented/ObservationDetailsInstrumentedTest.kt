package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：观测页详情必须从真实页签宿主打开，再验证筛选和关闭回调，避免只测试独立过滤函数而漏掉
 * 对话框入口、页签状态和详情视图之间的组合交互。
 */
@RunWith(AndroidJUnit4::class)
class ObservationDetailsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requestsDnsAndLogsDetailsSupportSearchAndLevelFilter() {
        val requests = RequestsUiState(
            requests = listOf(
                connection("request-1", "api.example.test"),
                connection("request-2", "other.example.test"),
            ),
        )
        val dns = DnsUiState(
            queries = listOf(
                dnsQuery("api.example.test", "1.1.1.1"),
                dnsQuery("other.example.test", "8.8.8.8"),
            ),
        )
        val logs = LogsUiState(
            entries = listOf(
                CoreEvent.Log("error", "连接失败", 1L),
                CoreEvent.Log("info", "连接恢复", 2L),
            ),
        )
        var dialog by mutableStateOf<ObservationDialog?>(null)

        composeRule.setContent {
            var selectedTabIndex by remember { mutableStateOf(0) }
            KClashTheme {
                ObservationPage(
                    requests = requests,
                    dns = dns,
                    logs = logs,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    selectedTabIndex = selectedTabIndex,
                    onSelectedTabChange = { selectedTabIndex = it },
                    onShowRequests = { dialog = ObservationDialog.REQUESTS },
                    onShowDns = { dialog = ObservationDialog.DNS },
                    onClearDns = {},
                    onShowLogs = { dialog = ObservationDialog.LOGS },
                    onClearLogs = {},
                    onExportLogs = {},
                )
                when (dialog) {
                    ObservationDialog.REQUESTS -> RequestsDialog(requests) { dialog = null }
                    ObservationDialog.DNS -> DnsDialog(dns, { dialog = null }, {})
                    ObservationDialog.LOGS -> LogsDialog(logs, { dialog = null }, {}, {})
                    null -> Unit
                }
            }
        }

        composeRule.onNodeWithText("查看全部").performClick()
        composeRule.onNodeWithText("搜索目标、进程、代理链或规则").performTextInput("api.example.test")
        composeRule.onNodeWithText("显示 1/2").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()

        composeRule.onNodeWithText("DNS").performClick()
        composeRule.onNodeWithText("查看全部").performClick()
        composeRule.onNodeWithText("搜索域名、上游、答案或错误").performTextInput("1.1.1.1")
        composeRule.onNodeWithText("显示 1/2").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()

        composeRule.onNodeWithText("日志").performClick()
        composeRule.onNodeWithText("查看全部").performClick()
        composeRule.onNodeWithText("错误").performClick()
        composeRule.onNodeWithText("显示 1/2").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
    }

    private fun connection(id: String, host: String) = CoreConnection(
        id = id,
        upload = 10L,
        download = 20L,
        start = "2026-10-28T00:00:00Z",
        metadata = CoreConnectionMetadata(
            network = "tcp",
            sourceIp = "10.0.0.2",
            sourcePort = "1234",
            destinationIp = "192.0.2.10",
            destinationPort = "443",
            host = host,
            process = "com.example.client",
            processPath = "/data/app/client",
            remoteDestination = "$host:443",
            uid = 10000L,
            specialProxy = "",
            specialRules = "",
        ),
        chains = listOf("GLOBAL", "DIRECT"),
        rule = "MATCH",
        rulePayload = "",
    )

    private fun dnsQuery(domain: String, upstream: String) = CoreDnsQuery(
        domain = domain,
        type = "A",
        initiator = "com.example.client",
        upstream = upstream,
        cached = false,
        answers = listOf("192.0.2.10"),
        rcode = "NOERROR",
        error = "",
        delay = 24L,
        time = "2026-10-28T00:00:00Z",
    )

    private enum class ObservationDialog {
        REQUESTS,
        DNS,
        LOGS,
    }
}
