package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreRouteStamp
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutboundIpViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun transientFailureRetriesOnlyWhileHomepageIsVisible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = IpTestRuntime()
        val store = FakeIpStore(runtime)
        store.outboundRead = { error("短暂断网") }
        val model = OutboundIpViewModel(runtime, store)
        try {
            model.setForeground(true); model.setDashboardVisible(true); runCurrent()
            assertNotNull(model.state.value.error)
            advanceTimeBy(4_000); runCurrent()
            assertEquals(1, store.outboundCalls)
            store.outboundRead = { RoutedIpInfo(OutboundIpInfo("1.1.1.1", "US"), runtime.stamp) }
            advanceTimeBy(1_000); runCurrent()
            assertEquals(2, store.outboundCalls)
            assertEquals("1.1.1.1", model.state.value.info?.ip)
            model.setDashboardVisible(false); runCurrent()
            advanceTimeBy(20_000); runCurrent()
            assertEquals(2, store.outboundCalls)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun successfulExitIsRecheckedWhileTheRouteStaysTheSame() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = IpTestRuntime()
        val store = FakeIpStore(runtime)
        val model = OutboundIpViewModel(runtime, store)
        try {
            model.setForeground(true); model.setDashboardVisible(true); runCurrent()
            advanceTimeBy(59_000); runCurrent()
            assertEquals(1, store.outboundCalls)
            store.outboundRead = { RoutedIpInfo(OutboundIpInfo("1.1.1.1", "US"), runtime.stamp) }
            advanceTimeBy(1_000); runCurrent()
            assertEquals(2, store.outboundCalls)
            assertEquals("1.1.1.1", model.state.value.info?.ip)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun foregroundAndDashboardBothGateRequestsAndResumeCancelledQuality() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = IpTestRuntime()
        val store = FakeIpStore(runtime)
        store.qualityRead = { awaitCancellation() }
        val model = OutboundIpViewModel(runtime, store)
        try {
            model.setDashboardVisible(true)
            runCurrent()
            assertEquals(0, store.outboundCalls)
            model.setForeground(true)
            runCurrent()
            assertEquals(1, store.outboundCalls)
            assertTrue(model.state.value.qualityLoading)
            model.setForeground(false)
            runCurrent()
            assertFalse(model.state.value.qualityLoading)
            store.qualityRead = { quality() }
            model.setForeground(true)
            runCurrent()
            assertEquals(1, store.outboundCalls)
            assertEquals(quality(), model.state.value.quality)
            model.setDashboardVisible(false)
            runCurrent()
            model.refresh()
            assertEquals(1, store.outboundCalls)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun routeSwitchAndStopClearOldIpAndRejectStaleResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = IpTestRuntime()
        val store = FakeIpStore(runtime)
        val model = OutboundIpViewModel(runtime, store)
        try {
            model.setForeground(true); model.setDashboardVisible(true); runCurrent()
            assertEquals("8.8.8.8", model.state.value.info?.ip)
            val oldStamp = runtime.stamp
            store.outboundRead = { RoutedIpInfo(OutboundIpInfo("1.1.1.1", "US"), oldStamp) }
            runtime.stamp = CoreRouteStamp(1, 2)
            runtime.ipRouteRevision.value++
            runCurrent()
            assertNull(model.state.value.info)
            store.outboundRead = { RoutedIpInfo(OutboundIpInfo("1.1.1.1", "US"), runtime.stamp) }
            advanceTimeBy(1_000); runCurrent()
            assertEquals("1.1.1.1", model.state.value.info?.ip)
            runtime.state.value = CoreRuntimeStatus.STOPPED
            runtime.runningSinceElapsedMillis.value = null
            runCurrent()
            assertEquals("1.1.1.1", model.state.value.info?.ip)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun manualRefreshDeduplicatesKeepsOldIpAndQualityRefreshOnlyForcesQuality() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = IpTestRuntime()
        val store = FakeIpStore(runtime)
        val model = OutboundIpViewModel(runtime, store)
        try {
            model.setForeground(true); model.setDashboardVisible(true); runCurrent()
            model.refreshQuality(); runCurrent()
            assertTrue(store.forced)
            assertEquals(1, store.outboundCalls)
            val answer = CompletableDeferred<RoutedIpInfo>()
            store.outboundRead = { answer.await() }
            model.refresh(); runCurrent()
            model.refresh(); runCurrent()
            assertEquals(2, store.outboundCalls)
            assertEquals("8.8.8.8", model.state.value.info?.ip)
            answer.completeExceptionally(IllegalStateException("离线")); runCurrent()
            assertEquals("8.8.8.8", model.state.value.info?.ip)
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.isLoading)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun qualityCompletionCannotPublishAfterUnobservedRouteChange() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = IpTestRuntime()
        val store = FakeIpStore(runtime)
        val answer = CompletableDeferred<IpQuality>()
        store.qualityRead = { answer.await() }
        val model = OutboundIpViewModel(runtime, store)
        try {
            model.setForeground(true); model.setDashboardVisible(true); runCurrent()
            runtime.stamp = CoreRouteStamp(1, 2)
            answer.complete(quality()); runCurrent()
            assertNull(model.state.value.info)
            assertNull(model.state.value.quality)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }
}

private class FakeIpStore(private val runtime: IpTestRuntime) : IpInformationStore {
    var outboundCalls = 0
    var forced = false
    var outboundRead: suspend () -> RoutedIpInfo = { RoutedIpInfo(OutboundIpInfo("8.8.8.8", "US"), runtime.stamp) }
    var qualityRead: suspend () -> IpQuality = { quality() }
    override suspend fun outbound(): RoutedIpInfo { outboundCalls++; return outboundRead() }
    override suspend fun quality(ip: String, force: Boolean): IpQuality { forced = force; return qualityRead().copy(ip = ip) }
}

private fun quality() = IpQuality("8.8.8.8", IpQualitySource.IDENT_ME, IpType.RESIDENTIAL)
