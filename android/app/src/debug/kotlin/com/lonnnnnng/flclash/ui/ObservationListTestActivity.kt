package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.ui.theme.KClashTheme

/** 作者 long：固定请求/DNS 列表夹具可验证完整工具栏和详情重建，不读取 Room、网络或核心事件流。 */
class ObservationListTestActivity : ComponentActivity() {
    private var page by mutableStateOf(ObservationListTestPage.REQUESTS)
    private var darkTheme by mutableStateOf(true)
    private var requestsState by mutableStateOf(RequestsUiState(requestFixtures()))
    private var dnsState by mutableStateOf(DnsUiState(dnsFixtures()))
    private var logsState by mutableStateOf(LogsUiState(logFixtures()))
    var dnsCacheClearRequests: Int = 0
        private set
    var dnsCacheClearSucceeds: Boolean = true
        private set
    var logExportRequests: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            page = ObservationListTestPage.valueOf(
                state.getString("observation_page", ObservationListTestPage.REQUESTS.name),
            )
            darkTheme = state.getBoolean("observation_dark_theme", true)
        }
        setContent {
            KClashTheme(darkTheme = darkTheme) {
                when (page) {
                    ObservationListTestPage.REQUESTS -> RequestsDialog(
                        state = requestsState,
                        onDismiss = ::finish,
                    )

                    ObservationListTestPage.DNS -> DnsDialog(
                        state = dnsState,
                        onDismiss = ::finish,
                        onClearDnsCache = {
                            dnsCacheClearRequests++
                            if (dnsCacheClearSucceeds) Result.success(Unit)
                            else Result.failure(IllegalStateException("DNS cache clear rejected"))
                        },
                    )

                    ObservationListTestPage.LOGS -> LogsDialog(
                        state = logsState,
                        onDismiss = ::finish,
                        onExport = { logExportRequests++ },
                    )
                }
            }
        }
    }

    fun showRequestsPage() {
        requestsState = RequestsUiState(requestFixtures())
        page = ObservationListTestPage.REQUESTS
    }

    fun showRequestsEmptyPage() {
        requestsState = RequestsUiState()
        page = ObservationListTestPage.REQUESTS
    }

    fun showDnsPage() {
        dnsState = DnsUiState(dnsFixtures())
        page = ObservationListTestPage.DNS
    }

    fun showDnsEmptyPage() {
        dnsState = DnsUiState()
        page = ObservationListTestPage.DNS
    }

    fun showLogsPage() {
        logsState = LogsUiState(logFixtures())
        logExportRequests = 0
        page = ObservationListTestPage.LOGS
    }

    fun showLogsEmptyPage() {
        logsState = LogsUiState()
        page = ObservationListTestPage.LOGS
    }

    fun overrideDarkTheme(enabled: Boolean) {
        darkTheme = enabled
    }

    fun setDnsCacheClearSucceeds(succeeds: Boolean) {
        dnsCacheClearSucceeds = succeeds
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("observation_page", page.name)
        outState.putBoolean("observation_dark_theme", darkTheme)
        super.onSaveInstanceState(outState)
    }
}

private enum class ObservationListTestPage {
    REQUESTS,
    DNS,
    LOGS,
}

private fun logFixtures() = listOf(
    CoreEvent.Log("info", "核心配置已加载。", 1_793_511_200_000L),
    CoreEvent.Log("warning", "DNS 上游响应较慢，将重试查询。", 1_793_511_260_000L),
    CoreEvent.Log("error", "连接失败：无法连接目标服务器。", 1_793_511_320_000L),
)

private fun requestFixtures() = listOf(
    CoreConnection(
        id = "request-first",
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
        ),
        chains = listOf("GLOBAL", "DIRECT"),
        rule = "MATCH",
        rulePayload = "",
    ),
    CoreConnection(
        id = "request-second",
        upload = 2_048L,
        download = 4_096L,
        start = "2026-10-28T00:01:00Z",
        metadata = CoreConnectionMetadata(
            network = "udp",
            sourceIp = "10.0.0.3",
            sourcePort = "53000",
            destinationIp = "198.51.100.8",
            destinationPort = "53",
            host = "resolver.example.test",
            process = "com.example.browser",
            processPath = "/data/app/browser",
            remoteDestination = "resolver.example.test:53",
            uid = 10_001L,
            specialProxy = "",
            specialRules = "",
        ),
        chains = listOf("GLOBAL", "Proxy"),
        rule = "DOMAIN-SUFFIX",
        rulePayload = "example.test",
    ),
)

private fun dnsFixtures() = listOf(
    CoreDnsQuery(
        domain = "resolver.example.test",
        type = "A",
        initiator = "com.example.browser",
        upstream = "https://dns.example.test/dns-query",
        cached = false,
        answers = listOf("192.0.2.53"),
        rcode = "NOERROR",
        error = "",
        delay = 31L,
        time = "2026-10-28T00:00:00Z",
    ),
    CoreDnsQuery(
        domain = "rebuild-me.example.test",
        type = "AAAA",
        initiator = "com.example.client",
        upstream = "https://dns.example.test/dns-query",
        cached = true,
        answers = listOf("2001:db8:1234:5678::53"),
        rcode = "NOERROR",
        error = "",
        delay = 24L,
        time = "2026-10-28T00:01:00Z",
    ),
)
