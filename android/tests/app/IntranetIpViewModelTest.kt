package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreLanConfig
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import java.net.InetAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntranetIpViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun addressSelectionPrefersWifiIpv4AndRejectsVpnAndNonRoutableAddresses() {
        fun net(id: String, priority: Int, vararg addresses: String, vpn: Boolean = false) =
            LocalNetworkCandidate(id, priority, addresses.map(InetAddress::getByName), vpn)
        assertEquals("192.168.1.10", selectLocalAddress(listOf(
            net("cell", 3, "10.0.0.2"), net("vpn", -1, "198.18.0.1", vpn = true),
            net("wifi", 0, "2001:db8::1", "192.168.1.10", "127.0.0.1", "fe80::1"),
        )))
        assertNull(selectLocalAddress(listOf(net("empty", 0, "0.0.0.0", "::1", "fe80::1", "224.0.0.1"))))
        assertNotNull(selectLocalAddress(listOf(net("v6", 0, "2001:db8::1"))))
    }

    @Test fun copiedEndpointUsesIpv6BracketsAndRejectsDisabledPort() {
        val state = IntranetIpUiState(LocalAddressState("2001:db8::1", false),
            LanSharingState(CoreLanConfig(true, 7890), true), settingsLoading = false)
        assertEquals("[2001:db8::1]:7890", state.copyText)
        assertNull(state.copy(sharing = LanSharingState(CoreLanConfig(true, 0), true)).copyText)
        assertNull(state.copy(local = LocalAddressState()).copyText)
        assertEquals("2001:db8::1", state.copy(sharing = LanSharingState(CoreLanConfig(false, 0), true)).copyText)
    }

    @Test fun observationRequiresVisibilityAndForegroundAndClearsLostOrBackgroundAddress() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val networks = FakeLocalNetwork()
        val model = IntranetIpViewModel(networks, FakeLanRuntime(), FakeLanClipboard())
        try {
            model.setForeground(true); runCurrent(); assertEquals(0, networks.starts)
            model.setVisible(true); runCurrent(); assertEquals(1, networks.starts)
            networks.updates.value = LocalAddressState("192.168.1.10", false); runCurrent()
            assertEquals("192.168.1.10", model.state.value.copyText)
            networks.updates.value = LocalAddressState(isLoading = false); runCurrent()
            assertNull(model.state.value.copyText)
            model.setForeground(false); runCurrent(); assertEquals(1, networks.stops)
            networks.updates.value = LocalAddressState("192.168.2.10", false); runCurrent()
            assertNull(model.state.value.copyText)
            model.setForeground(true); runCurrent()
            assertEquals("192.168.2.10", model.state.value.copyText)
            model.setVisible(false); runCurrent(); assertEquals(2, networks.stops)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun saveIsConfirmedDeduplicatedRetainsOldStateOnFailureAndCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeLanRuntime()
        val clipboard = FakeLanClipboard()
        val networks = FakeLocalNetwork()
        val model = IntranetIpViewModel(networks, runtime, clipboard)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent()
            val answer = CompletableDeferred<Unit>()
            runtime.save = { answer.await() }
            model.setAllowLan(true); model.setAllowLan(true); runCurrent()
            assertEquals(1, runtime.saves)
            assertTrue(model.state.value.saving)
            assertFalse(model.state.value.sharing.config.allowLan)
            answer.completeExceptionally(IllegalStateException("失败")); runCurrent()
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.sharing.config.allowLan)
            runtime.save = { value -> runtime.lanSharing.value = LanSharingState(CoreLanConfig(value ?: false, 7890), true) }
            model.setAllowLan(true); runCurrent()
            assertTrue(model.state.value.sharing.config.allowLan)
            networks.updates.value = LocalAddressState("192.168.1.10", false); runCurrent()
            model.copyAddress()
            assertEquals("192.168.1.10:7890", clipboard.text)
            assertEquals("已复制", model.state.value.feedback)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun timedOutReadsAndSavesLeaveBusyStateAndAllowRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeLanRuntime()
        runtime.read = { withTimeout(10) { delay(100) } }
        val model = IntranetIpViewModel(FakeLocalNetwork(), runtime, FakeLanClipboard())
        try {
            model.setVisible(true); model.setForeground(true); runCurrent()
            advanceTimeBy(20); runCurrent()
            assertFalse(model.state.value.settingsLoading)
            assertNotNull(model.state.value.settingsError)
            runtime.read = {}
            model.retry(); runCurrent()
            assertNull(model.state.value.settingsError)
            runtime.save = { withTimeout(10) { delay(100) } }
            model.setAllowLan(true); runCurrent()
            advanceTimeBy(20); runCurrent()
            assertFalse(model.state.value.saving)
            assertNotNull(model.state.value.error)
            runtime.save = {}
            model.setAllowLan(true); runCurrent()
            assertFalse(model.state.value.saving)
            assertNull(model.state.value.error)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun settingsFailureAndLocalErrorCanBeRetriedWithoutStartingRuntime() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = FakeLanRuntime()
        runtime.read = { error("读取失败") }
        val source = FakeLocalNetwork()
        source.updates.value = LocalAddressState(isLoading = false, error = "地址读取失败")
        val model = IntranetIpViewModel(source, runtime, FakeLanClipboard())
        try {
            model.setVisible(true); model.setForeground(true); runCurrent()
            assertNotNull(model.state.value.settingsError)
            runtime.read = {}
            source.updates.value = LocalAddressState("192.168.1.20", false)
            model.retry(); runCurrent()
            assertNull(model.state.value.error)
            assertEquals("192.168.1.20", model.state.value.copyText)
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }
}

private class FakeLocalNetwork : LocalNetworkSource {
    val updates = MutableStateFlow(LocalAddressState(isLoading = false))
    var starts = 0
    var stops = 0
    override fun observe(): Flow<LocalAddressState> = flow {
        starts++
        try { emitAll(updates) } finally { stops++ }
    }
}
private class FakeLanRuntime : LanSharingRuntime {
    override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    override val lanSharing = MutableStateFlow(LanSharingState(CoreLanConfig(false, 7890), true))
    override val lanRevision = MutableStateFlow(0L)
    var saves = 0
    var read: suspend () -> Unit = {}
    var save: suspend (Boolean?) -> Unit = {}
    override suspend fun refreshLanSharing() = read()
    override suspend fun updateLanSharing(allowLan: Boolean?) { saves++; save(allowLan) }
}
private class FakeLanClipboard : ClipboardGateway {
    var text: String? = null
    override fun readText() = text
    override fun writeText(text: String) { this.text = text }
}
