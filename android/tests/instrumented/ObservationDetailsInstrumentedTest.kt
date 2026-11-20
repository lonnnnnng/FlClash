package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 作者 long：四类完整列表直接置于页签内，验证真实搜索、详情和清理隔离，取代旧摘要页的第二层入口。 */
@RunWith(AndroidJUnit4::class)
class ObservationDetailsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private fun connection(index: Int) = CoreConnection(id = "connection-$index", upload = 10L, download = 20L,
        start = "2026-10-28T00:00:00Z", metadata = CoreConnectionMetadata(network = "tcp", sourceIp = "10.0.0.2",
            sourcePort = "1234", destinationIp = "192.0.2.10", destinationPort = "443", host = "host$index.example.test",
            process = "client$index", processPath = "/data/app/client$index", remoteDestination = "host$index.example.test:443",
            uid = 10000L, specialProxy = "", specialRules = ""), chains = listOf("GLOBAL", "DIRECT"), rule = "MATCH", rulePayload = "")
    private fun dns() = CoreDnsQuery("api.example.test", "A", "client", "1.1.1.1", true,
        listOf("192.0.2.10"), "NOERROR", "", 24L, "2026-10-28T00:00:00Z")

    @Test fun fourTabsSearchCompleteListsAndKeepIndependentQueries() {
        composeRule.setContent {
            var tab by remember { mutableIntStateOf(0) }
            KClashTheme { ObservationPage(RequestsUiState((0..1).map(::connection)), DnsUiState(listOf(dns())),
                LogsUiState(listOf(CoreEvent.Log("error", "failure", 1), CoreEvent.Log("info", "ready", 2))),
                CoreRuntimeStatus.RUNNING, tab, { tab = it }, {}, {}, {},
                connections = ConnectionsUiState(connections = (0..11).map(::connection))) }
        }
        search("host1")
        composeRule.onNodeWithTag("request_row_0").assertIsDisplayed()
        composeRule.onNodeWithTag("request_row_1").assertDoesNotExist()
        tab("connections")
        search("client11")
        composeRule.onNodeWithText("host11.example.test:443", substring = true).assertIsDisplayed()
        tab("dns"); search("1.1.1.1")
        composeRule.onNodeWithTag("dns_row_0").assertIsDisplayed()
        tab("logs"); search("failure")
        composeRule.onNodeWithText("显示 1/2").assertIsDisplayed()
        tab("requests")
        composeRule.onNodeWithTag("observation_search_field").assertTextEquals("host1")
        tab("connections")
        composeRule.onNodeWithTag("observation_search_field").assertTextEquals("client11")
        tab("dns")
        composeRule.onNodeWithTag("observation_search_field").assertTextEquals("1.1.1.1")
        tab("logs")
        composeRule.onNodeWithTag("observation_search_field").assertTextEquals("failure")
        composeRule.onNodeWithTag("observation_search_clear").performClick()
        composeRule.onNodeWithText("显示 2/2").assertIsDisplayed()
    }

    @Test fun clearsCacheHistoryAndLogsIndependentlyAndExports() {
        var dnsState by mutableStateOf(DnsUiState(listOf(dns())))
        var logsState by mutableStateOf(LogsUiState(listOf(CoreEvent.Log("info", "ready", 1))))
        var clears = 0; var exports = 0
        composeRule.setContent {
            var tab by remember { mutableIntStateOf(ObservationTab.DNS.ordinal) }
            KClashTheme { ObservationPage(RequestsUiState(listOf(connection(0))), dnsState, logsState,
                CoreRuntimeStatus.RUNNING, tab, { tab = it }, { dnsState = DnsUiState() }, { logsState = LogsUiState() },
                { exports++ }, onClearDnsCache = { clears++; Result.success(Unit) }) }
        }
        composeRule.onNodeWithTag("observation_clear").performClick()
        composeRule.runOnIdle { assertEquals(1, clears); assertEquals(1, dnsState.queries.size) }
        composeRule.onNodeWithTag("observation_more").performClick()
        composeRule.onNodeWithTag("observation_clear_history").performClick()
        composeRule.onNodeWithTag("dns_empty_state").assertIsDisplayed()
        tab("logs")
        composeRule.onNodeWithTag("observation_export").performClick()
        composeRule.runOnIdle { assertEquals(1, exports) }
        composeRule.onNodeWithTag("observation_clear").performClick()
        composeRule.onNodeWithTag("logs_empty_state").assertIsDisplayed()
        tab("requests")
        composeRule.onNodeWithTag("request_row_0").assertIsDisplayed()
    }

    @Test fun connectionDetailsKeepSnapshotAfterPollingRemovesRow() {
        var connections by mutableStateOf(ConnectionsUiState(connections = listOf(connection(7))))
        var copied: String? = null
        composeRule.setContent { KClashTheme { ObservationPage(RequestsUiState(), DnsUiState(), LogsUiState(),
            CoreRuntimeStatus.RUNNING, ObservationTab.CONNECTIONS.ordinal, {}, {}, {}, {}, connections = connections,
            onCopyText = { copied = it }) } }
        composeRule.onNodeWithTag("connections_row_0").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, height * .2f)) }
        composeRule.runOnIdle { connections = ConnectionsUiState() }
        composeRule.onNodeWithTag("connection_detail_主机").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("host7.example.test", copied) }
        composeRule.onNodeWithTag("connections_detail_close").performClick()
        composeRule.onNodeWithTag("connections_empty_state").assertIsDisplayed()
    }
    @Test fun scrollingAndSearchSurviveTabAndSavedStateRestoration() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(composeRule)
        restoration.setContent {
            var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(ObservationTab.CONNECTIONS.ordinal) }
            KClashTheme { ObservationPage(RequestsUiState(), DnsUiState(), LogsUiState(), CoreRuntimeStatus.RUNNING,
                tab, { tab = it }, {}, {}, {}, connections = ConnectionsUiState(connections = (0..99).map(::connection))) }
        }
        composeRule.onNodeWithTag("observation_records").performScrollToIndex(50)
        val top = composeRule.onNodeWithTag("connections_row_50").fetchSemanticsNode().boundsInRoot.top
        tab("logs"); tab("connections")
        assertEquals(top, composeRule.onNodeWithTag("connections_row_50").fetchSemanticsNode().boundsInRoot.top, 1f)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(top, composeRule.onNodeWithTag("connections_row_50").fetchSemanticsNode().boundsInRoot.top, 1f)
        search("host77")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("observation_search_field").assertTextEquals("host77")
        composeRule.onNodeWithText("host77.example.test:443", substring = true).assertIsDisplayed()
    }
    private fun tab(name: String) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("observation_tab_$name").performClick()
    }
    private fun search(query: String) {
        composeRule.onNodeWithTag("observation_search_toggle").performClick()
        composeRule.onNodeWithTag("observation_search_field").performTextInput(query)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }
}
