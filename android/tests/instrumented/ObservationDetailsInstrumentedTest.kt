package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

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

    @Test
    fun observationPageClearsDnsAndLogsIndependentlyAndExportsLogs() {
        val request = connection("request-actions", "api.example.test")
        val dnsQuery = dnsQuery("api.example.test", "1.1.1.1")
        val log = CoreEvent.Log("warning", "上游响应较慢", 0L)
        var dnsState by mutableStateOf(DnsUiState(listOf(dnsQuery)))
        var logsState by mutableStateOf(LogsUiState(listOf(log)))
        var exportedLogs: List<CoreEvent.Log>? = null

        composeRule.setContent {
            var selectedTabIndex by remember { mutableStateOf(0) }
            KClashTheme {
                ObservationPage(
                    requests = RequestsUiState(listOf(request)),
                    dns = dnsState,
                    logs = logsState,
                    runtimeState = CoreRuntimeStatus.RUNNING,
                    selectedTabIndex = selectedTabIndex,
                    onSelectedTabChange = { selectedTabIndex = it },
                    onShowRequests = {},
                    onShowDns = {},
                    onClearDns = { dnsState = DnsUiState() },
                    onShowLogs = {},
                    onClearLogs = { logsState = LogsUiState() },
                    onExportLogs = { exportedLogs = logsState.entries },
                )
            }
        }

        composeRule.onNodeWithText("DNS").performClick()
        composeRule.onNodeWithText("api.example.test").assertIsDisplayed()
        composeRule.onNodeWithText("清空").performClick()
        composeRule.onNodeWithText("等待核心上报 DNS 查询事件。").assertIsDisplayed()

        composeRule.onNodeWithText("日志").performClick()
        composeRule.onNodeWithText("上游响应较慢").assertIsDisplayed()
        composeRule.onNodeWithText("导出").performClick()
        assertEquals(listOf(log), exportedLogs)
        composeRule.onNodeWithText("清空").performClick()
        composeRule.onNodeWithText("等待核心上报日志事件。").assertIsDisplayed()

        // 作者 long：清空 DNS/日志只能影响各自 StateFlow，Requests 记录仍应保持可见，避免一次清理误删其他观测数据。
        composeRule.onNodeWithText("请求").performClick()
        composeRule.onNodeWithText("api.example.test").assertIsDisplayed()
    }

    @Test
    fun observationPageScreenshotMatrixMatchesBaseline() {
        val request = connection("request-visual", "api.example.test")
        val dnsQuery = dnsQuery("api.example.test", "1.1.1.1")
        // 作者 long：固定历史时间让相对时间文案在每次回归中保持一致，避免基线随测试运行时钟漂移。
        val log = CoreEvent.Log("warning", "上游响应较慢", 0L)
        var selectedTabIndex by mutableIntStateOf(0)
        var runtimeState by mutableStateOf(CoreRuntimeStatus.STOPPED)

        composeRule.setContent {
            // 作者 long：固定三种用户可见状态，避免把网络时序、设备安装包或 Room 数据混入视觉基线。
            KClashTheme {
                ObservationPage(
                    requests = if (runtimeState.isRunning) RequestsUiState(listOf(request)) else RequestsUiState(),
                    dns = if (runtimeState.isRunning) DnsUiState(listOf(dnsQuery)) else DnsUiState(),
                    logs = if (runtimeState.isRunning) LogsUiState(listOf(log)) else LogsUiState(),
                    runtimeState = runtimeState,
                    selectedTabIndex = selectedTabIndex,
                    onSelectedTabChange = { selectedTabIndex = it },
                    onShowRequests = {},
                    onShowDns = {},
                    onClearDns = {},
                    onShowLogs = {},
                    onClearLogs = {},
                    onExportLogs = {},
                )
            }
        }

        fun captureHash(tag: String): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag(tag)
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordPng("tab$selectedTabIndex-running${runtimeState.isRunning}", png)
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val expected = mapOf(
            "requests_stopped_empty" to "2c301cde81cb2132e7460d041ed0ec81df0d28812fe0b418ebc99eafc9bbef41",
            "requests_running_non_empty" to "39458f3ca452ebc589e350ae59f27982f64b03058b3ff73ae0736ec20ebef7b0",
            "dns_stopped_empty" to "f181db667a12f9db0c1cf764816b7c56b139d96792d08d80695ee5b158fdf7e4",
            "dns_running_non_empty" to "3a544d24ed1a6924f9c902f1cbb85be8fa7415bee5610b990951ffff40abce44",
            "logs_stopped_empty" to "8ef07b0b6abd266258b74000eafa2b252426a5b999af49a5cf704866ea1624d3",
            "logs_running_non_empty" to "c5094b6641af25dcf7aac38c1c4d6461321ea7d2a0dbe568daab5fb06f87f495",
        )
        val actual = buildMap {
            put("requests_stopped_empty", captureHash("observation_page"))
            runtimeState = CoreRuntimeStatus.RUNNING
            put("requests_running_non_empty", captureHash("observation_page"))
            runtimeState = CoreRuntimeStatus.STOPPED
            selectedTabIndex = 1
            put("dns_stopped_empty", captureHash("observation_page"))
            runtimeState = CoreRuntimeStatus.RUNNING
            put("dns_running_non_empty", captureHash("observation_page"))
            runtimeState = CoreRuntimeStatus.STOPPED
            selectedTabIndex = 2
            put("logs_stopped_empty", captureHash("observation_page"))
            runtimeState = CoreRuntimeStatus.RUNNING
            put("logs_running_non_empty", captureHash("observation_page"))
        }

        val mismatches = expected.mapNotNull { (state, expectedHash) ->
            val actualHash = actual.getValue(state)
            if (actualHash == expectedHash) null else "$state expected=$expectedHash actual=$actualHash"
        }
        if (mismatches.isNotEmpty()) {
            error("observation screenshot baseline mismatch: ${mismatches.joinToString("; ")}")
        }
    }

    @Test
    fun observationRunningStatesFontScaleMatrixMatchesBaseline() {
        val request = connection("request-font-scale", "api.example.test")
        val dnsQuery = dnsQuery("api.example.test", "1.1.1.1")
        // 作者 long：固定历史时间让大字号基线只反映布局和文字缩放，不随当前时间变化。
        val log = CoreEvent.Log("warning", "上游响应较慢", 0L)
        var selectedTabIndex by mutableIntStateOf(0)
        var fontScaleValue by mutableStateOf(1.3f)

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                // 作者 long：只改变字体缩放，不改变设备密度，确保页签间的差异可复现。
                LocalDensity provides Density(baseDensity.density, fontScale = fontScaleValue),
            ) {
                KClashTheme {
                    ObservationPage(
                        requests = RequestsUiState(listOf(request)),
                        dns = DnsUiState(listOf(dnsQuery)),
                        logs = LogsUiState(listOf(log)),
                        runtimeState = CoreRuntimeStatus.RUNNING,
                        selectedTabIndex = selectedTabIndex,
                        onSelectedTabChange = { selectedTabIndex = it },
                        onShowRequests = {},
                        onShowDns = {},
                        onClearDns = {},
                        onShowLogs = {},
                        onClearLogs = {},
                        onExportLogs = {},
                    )
                }
            }
        }

        fun captureHash(): String {
            composeRule.waitForIdle()
            val bitmap = composeRule
                .onNodeWithTag("observation_page")
                .captureToImage()
                .asAndroidBitmap()
            val png = ByteArrayOutputStream().also { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }.toByteArray()
            recordPng("font$fontScaleValue-tab$selectedTabIndex", png)
            return MessageDigest.getInstance("SHA-256").digest(png)
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        val actual = buildMap {
            put("fontScale=1.3 requests", captureHash())
            selectedTabIndex = 1
            put("fontScale=1.3 dns", captureHash())
            selectedTabIndex = 2
            put("fontScale=1.3 logs", captureHash())
            fontScaleValue = 2.0f
            selectedTabIndex = 0
            put("fontScale=2.0 requests", captureHash())
            selectedTabIndex = 1
            put("fontScale=2.0 dns", captureHash())
            selectedTabIndex = 2
            put("fontScale=2.0 logs", captureHash())
        }

        assertEquals(
            "fontScale=1.3 requests sha256=0f89025061b8d9fd35fd4daca0cf0291777f5c9aa3a28ef5907b19fe6a45b80e\n" +
                "fontScale=1.3 dns sha256=cabbe76d6f3400a7b48f11c4d2503b60e87599ffb777a1c6870c9eabf8ae81e0\n" +
                "fontScale=1.3 logs sha256=a1c76e593d770e2e98d1ed313fb3e37c0c48c11fe5a8877037d0d82348feba39\n" +
                "fontScale=2.0 requests sha256=99716ddfdfba334a6a4fc0ebdaa646c200229114d5681ec7f491099ad465b1ec\n" +
                "fontScale=2.0 dns sha256=6da0e5f4c9cb5add1227b90f3373f5dde5b57b7417a8f1743589fce58855e25f\n" +
                "fontScale=2.0 logs sha256=503bf138c393346bccf59833a1a2905a59f1605f651c83426d0133b9d025d692",
            actual.entries.joinToString("\n") { (state, hash) -> "$state sha256=$hash" },
        )
    }

    private fun recordPng(name: String, png: ByteArray) {
        // 作者 long：共享连接摘要的链路顺序变化需要实际图片复核，不能只根据失败日志替换预期哈希。
        recordVisualPng("observation-$name", png)
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
