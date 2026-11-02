package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceStatusViewModelTest {
    @Test fun manualChecksDuringSettlingWaitForRouteAndBackgroundCancelsQueuedIntent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            runCurrent(); model.setForeground(true); model.setDetailVisible(true); runCurrent()
            model.check(listOf(ServiceTarget.CLAUDE)); runCurrent(); assertTrue(runtime.calls.isEmpty())
            advanceTimeBy(1_500); runCurrent(); assertEquals(listOf(ServiceTarget.CLAUDE), runtime.calls)
            runtime.stamp = CoreRouteStamp(1, 2); runtime.ipRouteRevision.value++; runCurrent()
            model.check(listOf(ServiceTarget.GEMINI)); model.setForeground(false); runCurrent()
            model.setForeground(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertEquals(listOf(ServiceTarget.CLAUDE), runtime.calls)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun automaticCoreMoveWithoutLocalRevisionReusesServiceButRefreshesIpAndClearsQuality() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime(); val exits = FakeServiceExits()
        runtime.read = { result(it, runtime.stamp).copy(hits = listOf(CoreRouteHit("example.com:443", "", "fixture-exit"))) }
        runtime.reuse = { rows, stamp -> rows.map { it.copy(stamp = stamp) } }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher, exits)
        try {
            model.setForeground(true); model.setVisible(true); model.setDetailVisible(true)
            runCurrent(); advanceTimeBy(1_500); runCurrent(); model.openQuality("DIRECT"); runCurrent()
            val lookups = exits.lookups
            assertNotNull(model.state.value.exits["DIRECT"]?.quality)
            runtime.stamp = CoreRouteStamp(1, 2)
            advanceTimeBy(1_000); runCurrent(); assertTrue(model.state.value.exits.isEmpty()); assertNull(model.state.value.qualityNode)
            advanceTimeBy(1_500); runCurrent()
            assertEquals(2, runtime.calls.size); assertEquals(1, runtime.reuseCalls)
            assertEquals(lookups + 1, exits.lookups)
            assertNull(model.state.value.exits["DIRECT"]?.quality)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun softRouteMoveReusesOnlyHeldServicesAndKeepsCheckedAt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        runtime.read = { result(it, runtime.stamp).copy(checkedAt = testScheduler.currentTime,
            hits = listOf(CoreRouteHit("example.com:443", "", "fixture-exit"))) }
        runtime.reuse = { rows, stamp -> rows.filter { it.target == ServiceTarget.GITHUB }.map { it.copy(stamp = stamp) } }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            val old = model.state.value.entries.getValue(ServiceTarget.GITHUB).result!!
            runtime.stamp = CoreRouteStamp(1, 2); runtime.ipRouteRevision.value++; runCurrent()
            assertTrue(model.state.value.entries.isEmpty())
            advanceTimeBy(1_500); runCurrent()
            assertEquals(1, runtime.reuseCalls)
            assertEquals(2, runtime.calls.count { it == ServiceTarget.GOOGLE })
            assertEquals(1, runtime.calls.count { it == ServiceTarget.GITHUB })
            assertEquals(old.copy(stamp = runtime.stamp), model.state.value.entries.getValue(ServiceTarget.GITHUB).result)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun networkSessionAndEpochChangesNeverReuseEvenWhenCoreWouldHoldAll() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        runtime.read = { result(it, runtime.stamp).copy(hits = listOf(CoreRouteHit("example.com:443", "", "fixture-exit"))) }
        runtime.reuse = { rows, stamp -> rows.map { it.copy(stamp = stamp) } }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            runtime.serviceCacheRevision.value++; runtime.ipRouteRevision.value++; runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertEquals(4, runtime.calls.size); assertEquals(0, runtime.reuseCalls)
            runtime.stamp = CoreRouteStamp(2, 1); advanceTimeBy(1_000); runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertEquals(6, runtime.calls.size); assertEquals(0, runtime.reuseCalls)
            runtime.runningSinceElapsedMillis.value = 100L; runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertEquals(8, runtime.calls.size); assertEquals(0, runtime.reuseCalls)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun backgroundDefersValidationAndLateOldVerificationCannotLandOnNewRoute() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        runtime.read = { result(it, runtime.stamp).copy(hits = listOf(CoreRouteHit("example.com:443", "", "fixture-exit"))) }
        val pending = CompletableDeferred<Unit>()
        runtime.reuse = { rows, stamp -> withContext(NonCancellable) { pending.await(); rows.map { it.copy(stamp = stamp) } } }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            model.setForeground(false); runtime.ipRouteRevision.value++; runtime.stamp = CoreRouteStamp(1, 2); runCurrent()
            advanceTimeBy(5_000); runCurrent(); assertEquals(0, runtime.reuseCalls)
            model.setForeground(true); runCurrent(); advanceTimeBy(1_500); runCurrent(); assertEquals(1, runtime.reuseCalls)
            runtime.stamp = CoreRouteStamp(1, 3); runtime.ipRouteRevision.value++; runCurrent()
            pending.complete(Unit); runCurrent()
            assertTrue(model.state.value.entries.isEmpty())
            runtime.reuse = { _, _ -> emptyList() }
            advanceTimeBy(1_500); runCurrent()
            assertEquals(4, runtime.calls.size)
            assertTrue(model.state.value.entries.values.all { it.result?.stamp == runtime.stamp })
        } finally { pending.complete(Unit); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun failedOrIncompleteValidationFallsBackToFreshChecksAndNeverReusesExitQuality() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime(); val exits = FakeServiceExits()
        runtime.read = { result(it, runtime.stamp).copy(hits = listOf(CoreRouteHit("example.com:443", "", "fixture-exit"))) }
        runtime.reuse = { _, _ -> error("核验失败") }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher, exits)
        try {
            model.setForeground(true); model.setVisible(true); model.setDetailVisible(true)
            runCurrent(); advanceTimeBy(1_500); runCurrent(); model.openQuality("DIRECT"); runCurrent()
            assertNotNull(model.state.value.exits["DIRECT"]?.quality)
            runtime.ipRouteRevision.value++; runtime.stamp = CoreRouteStamp(1, 2); runCurrent()
            assertNull(model.state.value.qualityNode); assertTrue(model.state.value.exits.isEmpty())
            advanceTimeBy(1_500); runCurrent(); assertEquals(4, runtime.calls.size)
            assertNull(model.state.value.exits["DIRECT"]?.quality)
            runtime.reuse = { rows, stamp -> rows.map { it.copy(stamp = stamp, checkedAt = 999L) } }
            runtime.ipRouteRevision.value++; runtime.stamp = CoreRouteStamp(1, 3); runCurrent()
            advanceTimeBy(1_500); runCurrent(); assertEquals(6, runtime.calls.size)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun reorderRejectsMissingDuplicateAndUnchangedTargetsWithoutWriting() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store = FakeServiceStore()
        val model = ServiceStatusViewModel(FakeServiceRuntime(), store, dispatcher)
        try {
            runCurrent()
            val order = store.value.order
            model.reorder(order); model.reorder(order.drop(1)); model.reorder(order.dropLast(1) + order.first())
            runCurrent()
            assertEquals(0, store.writes)
            assertEquals(order, model.state.value.settings.order)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun reorderFailureKeepsSavedOrderThenRetryPreservesDisabledTargetsAndRebuild() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store = FakeServiceStore().apply { value = ServiceSettings(disabled = setOf(ServiceTarget.GOOGLE)) }
        val model = ServiceStatusViewModel(FakeServiceRuntime(), store, dispatcher)
        try {
            runCurrent()
            val old = store.value
            val changed = old.order.reversed()
            store.fail = true; model.reorder(changed); runCurrent()
            assertEquals(old, model.state.value.settings); assertEquals(old, store.value)
            assertNotNull(model.state.value.error)
            store.fail = false; model.reorder(changed); runCurrent()
            assertEquals(changed, store.value.order); assertEquals(old.disabled, store.value.disabled)
            assertEquals(store.value, model.state.value.settings); assertNull(model.state.value.error)
            val rebuilt = ServiceStatusViewModel(FakeServiceRuntime(), store, dispatcher)
            runCurrent(); assertEquals(store.value, rebuilt.state.value.settings); rebuilt.viewModelScope.cancel()
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun reorderDoesNotOverwriteAnInFlightSave() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store = FakeServiceStore()
        val model = ServiceStatusViewModel(FakeServiceRuntime(), store, dispatcher)
        try {
            runCurrent()
            val order = store.value.order.reversed()
            model.reorder(order); assertTrue(model.state.value.saving)
            model.reorder(order.drop(1) + order.first()); model.toggle(ServiceTarget.GOOGLE, false)
            runCurrent()
            assertEquals(1, store.writes); assertEquals(order, store.value.order)
            assertTrue(store.value.disabled.isEmpty())
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun serviceExitIsDetailOnlyDeduplicatesSharedNodeAndManualCheckRefreshesAddress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        val exits = FakeServiceExits()
        exits.lookup = { delay(100); com.lonnnnnng.flclash.data.OutboundIpInfo("8.8.8.8", "US") }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher, exits)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertEquals(0, exits.lookups)
            model.setDetailVisible(true); runCurrent()
            assertEquals(1, exits.lookups)
            advanceTimeBy(100); runCurrent()
            assertEquals("8.8.8.8", model.state.value.exits["DIRECT"]?.info?.ip)
            advanceTimeBy(10_000); runCurrent(); assertEquals(1, exits.lookups)
            exits.lookup = { com.lonnnnnng.flclash.data.OutboundIpInfo("1.1.1.1", "AU") }
            model.check(listOf(ServiceTarget.GITHUB)); runCurrent()
            assertEquals("1.1.1.1", model.state.value.exits["DIRECT"]?.info?.ip)
            assertEquals(2, exits.lookups)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun exitFailureKeepsServiceResultAndCanRetryWithoutRecheckingService() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime(); val exits = FakeServiceExits()
        exits.lookup = { error("出口不可达") }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher, exits)
        try {
            model.setForeground(true); model.setVisible(true); model.setDetailVisible(true)
            runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertEquals(ServiceStatus.AVAILABLE, model.state.value.entries[ServiceTarget.GITHUB]?.result?.status)
            assertNotNull(model.state.value.exits["DIRECT"]?.error)
            val checks = runtime.calls.size
            exits.lookup = { com.lonnnnnng.flclash.data.OutboundIpInfo("8.8.8.8", "US") }
            model.refreshExit("DIRECT"); model.refreshExit("DIRECT"); runCurrent()
            assertEquals(checks, runtime.calls.size)
            assertEquals("8.8.8.8", model.state.value.exits["DIRECT"]?.info?.ip)
            assertEquals(2, exits.lookups)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun lateExitCannotWriteAfterBackgroundOrRouteChange() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime(); val exits = FakeServiceExits()
        val pending = CompletableDeferred<com.lonnnnnng.flclash.data.OutboundIpInfo>()
        exits.lookup = { withContext(NonCancellable) { pending.await() } }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher, exits)
        try {
            model.setForeground(true); model.setVisible(true); model.setDetailVisible(true)
            runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertTrue(model.state.value.exits.getValue("DIRECT").isLoading)
            model.setForeground(false); runtime.ipRouteRevision.value++; runtime.stamp = CoreRouteStamp(2, 1); runCurrent()
            pending.complete(com.lonnnnnng.flclash.data.OutboundIpInfo("8.8.8.8", "US")); runCurrent()
            assertTrue(model.state.value.exits.isEmpty())
            assertNull(model.state.value.qualityNode)
        } finally { pending.complete(com.lonnnnnng.flclash.data.OutboundIpInfo("8.8.8.8", "US")); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun qualityUsesClickedAddressAndLateClosedSheetCannotOverwriteReopenedSheet() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime(); val exits = FakeServiceExits()
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher, exits)
        val pending = CompletableDeferred<com.lonnnnnng.flclash.data.IpQuality>()
        try {
            model.setForeground(true); model.setVisible(true); model.setDetailVisible(true)
            runCurrent(); advanceTimeBy(1_500); runCurrent()
            exits.describe = { withContext(NonCancellable) { pending.await() } }
            model.openQuality("DIRECT"); runCurrent()
            assertEquals("8.8.8.8", exits.qualityIp)
            model.closeQuality()
            exits.describe = { qualitySample.copy(type = com.lonnnnnng.flclash.data.IpType.MOBILE) }
            model.openQuality("DIRECT"); runCurrent()
            pending.complete(qualitySample); runCurrent()
            assertEquals(com.lonnnnnng.flclash.data.IpType.MOBILE, model.state.value.exits["DIRECT"]?.quality?.type)
            model.refreshQuality(force = true); runCurrent(); assertTrue(exits.forced)
            runtime.ipRouteRevision.value++; runCurrent()
            assertNull(model.state.value.qualityNode)
            assertTrue(model.state.value.exits.isEmpty())
        } finally { pending.complete(qualitySample); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun qualityFailureKeepsAddressAndForceRetryCanRecover() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val exits = FakeServiceExits()
        val model = ServiceStatusViewModel(FakeServiceRuntime(), FakeServiceStore(), dispatcher, exits)
        try {
            model.setForeground(true); model.setVisible(true); model.setDetailVisible(true)
            runCurrent(); advanceTimeBy(1_500); runCurrent()
            exits.describe = { throw IpQualityException(mapOf(IpQualitySource.IP_QUERY to IpSourceFailure.RATE_LIMITED)) }
            model.openQuality("DIRECT"); runCurrent()
            assertEquals("8.8.8.8", model.state.value.exits["DIRECT"]?.info?.ip)
            assertEquals(IpSourceFailure.RATE_LIMITED, model.state.value.exits["DIRECT"]?.qualityFailures?.get(IpQualitySource.IP_QUERY))
            exits.describe = { qualitySample }; model.refreshQuality(force = true); runCurrent()
            assertEquals(qualitySample, model.state.value.exits["DIRECT"]?.quality)
            assertTrue(model.state.value.exits.getValue("DIRECT").qualityFailures.isEmpty())
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun settingsRepairOrderAndKeepAtLeastOneEnabledTarget() {
        val first = ServiceTarget.CLAUDE
        val settings = ServiceSettings(listOf(first, first), ServiceTarget.entries.toSet()).normalized()
        assertEquals(first, settings.order.first())
        assertEquals(13, settings.order.size)
        assertEquals(13, settings.enabled.size)
        assertEquals(ServiceTarget.entries.toList(), ServiceSettings(emptyList()).normalized().order)
    }

    @Test fun onlyVisibleForegroundCardChecksFirstTwoAfterRouteSettlesAndKeepsCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); runCurrent()
            advanceTimeBy(5_000); runCurrent(); assertTrue(runtime.calls.isEmpty())
            model.setVisible(true); runCurrent()
            advanceTimeBy(1_499); runCurrent(); assertTrue(runtime.calls.isEmpty())
            advanceTimeBy(1); runCurrent()
            assertEquals(listOf(ServiceTarget.GOOGLE, ServiceTarget.GITHUB), runtime.calls)
            advanceTimeBy(20_000); runCurrent(); assertEquals(2, runtime.calls.size)
            model.setForeground(false); runCurrent()
            model.check(); advanceTimeBy(10_000); runCurrent(); assertEquals(2, runtime.calls.size)
            model.setForeground(true); runCurrent(); assertEquals(2, runtime.calls.size)
            model.setVisible(false); model.setDetailVisible(true); runCurrent()
            model.check(listOf(ServiceTarget.CLAUDE)); runCurrent()
            assertEquals(ServiceTarget.CLAUDE, runtime.calls.last())
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun routeRevisionCancelsPendingRequestsAndLateAnswerCannotReplaceNewRoute() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        val pending = CompletableDeferred<CoreServiceCheck>()
        val old = runtime.stamp
        runtime.read = { target -> withContext(NonCancellable) { pending.await().copy(target = target, stamp = old) } }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertEquals(2, runtime.calls.size)
            runtime.stamp = CoreRouteStamp(1, 2); runtime.ipRouteRevision.value++
            runtime.read = { result(it, runtime.stamp) }; runCurrent()
            pending.complete(result(ServiceTarget.GOOGLE, old)); runCurrent()
            advanceTimeBy(1_500); runCurrent()
            assertEquals(runtime.stamp, model.state.value.entries[ServiceTarget.GOOGLE]?.result?.stamp)
            assertEquals(4, runtime.calls.size)
        } finally { pending.complete(result(ServiceTarget.GOOGLE, old)); model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun checksDeduplicateTimeoutRetryAndBackgroundCancelsLoading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        runtime.read = { delay(100_000); result(it, runtime.stamp) }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); model.setDetailVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            model.check(listOf(ServiceTarget.GOOGLE)); model.check(listOf(ServiceTarget.GOOGLE)); runCurrent()
            assertEquals(1, runtime.calls.size)
            advanceTimeBy(42_000); runCurrent()
            assertTrue(model.state.value.entries.getValue(ServiceTarget.GOOGLE).failed)
            model.check(listOf(ServiceTarget.GOOGLE)); runCurrent(); assertEquals(2, runtime.calls.size)
            model.setForeground(false); runCurrent()
            assertTrue(model.state.value.entries.values.none { it.loading })
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun cardRetriesFailedTargetsButNeverPeriodicallyRechecksSuccessfulTargets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        runtime.read = { if (it == ServiceTarget.GOOGLE) error("失败") else result(it, runtime.stamp) }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            advanceTimeBy(5_000); runCurrent()
            assertEquals(2, runtime.calls.count { it == ServiceTarget.GOOGLE })
            assertEquals(1, runtime.calls.count { it == ServiceTarget.GITHUB })
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun settingsCommitFailureKeepsPreviousSelectionAndRetryPersistsOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val store = FakeServiceStore()
        val model = ServiceStatusViewModel(FakeServiceRuntime(), store, dispatcher)
        try {
            runCurrent(); store.fail = true
            model.toggle(ServiceTarget.GOOGLE, false); runCurrent()
            assertTrue(ServiceTarget.GOOGLE in model.state.value.settings.enabled)
            assertNotNull(model.state.value.error)
            store.fail = false; model.toggle(ServiceTarget.GOOGLE, false); runCurrent()
            assertFalse(ServiceTarget.GOOGLE in model.state.value.settings.enabled)
            model.move(ServiceTarget.CLAUDE, -1); runCurrent()
            assertEquals(store.value, model.state.value.settings)
            for (target in model.state.value.settings.enabled.drop(1)) { model.toggle(target, false); runCurrent() }
            val last = model.state.value.settings.enabled.single()
            model.toggle(last, false); runCurrent(); assertEquals(listOf(last), model.state.value.settings.enabled)
            val rebuilt = ServiceStatusViewModel(FakeServiceRuntime(), store, dispatcher)
            runCurrent(); assertEquals(store.value, rebuilt.state.value.settings); rebuilt.viewModelScope.cancel()
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }

    @Test fun routeChangeDuringProbeIsDiscardedAndCardWaitsBeforeRetrying() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val runtime = FakeServiceRuntime()
        runtime.read = { throw ServiceRouteChangedException() }
        val model = ServiceStatusViewModel(runtime, FakeServiceStore(), dispatcher)
        try {
            model.setForeground(true); model.setVisible(true); runCurrent(); advanceTimeBy(1_500); runCurrent()
            assertTrue(model.state.value.entries.values.none { it.failed })
            runtime.read = { result(it, runtime.stamp) }
            advanceTimeBy(999); runCurrent(); assertEquals(2, runtime.calls.size)
            advanceTimeBy(1); runCurrent()
            advanceTimeBy(1_500); runCurrent()
            assertEquals(4, runtime.calls.size)
            assertEquals(ServiceStatus.AVAILABLE, model.state.value.entries[ServiceTarget.GOOGLE]?.result?.status)
        } finally { model.viewModelScope.cancel(); runCurrent() }
    }
}

private fun result(target: ServiceTarget, stamp: CoreRouteStamp) = CoreServiceCheck(target, ServiceStatus.AVAILABLE, 18, "US", listOf("DIRECT"), 1, stamp)
private class FakeServiceStore : ServiceSettingsStore {
    var value = ServiceSettings()
    var fail = false
    var writes = 0
    override fun read() = value
    override fun write(settings: ServiceSettings) { writes++; check(!fail); value = settings }
}
private class FakeServiceRuntime : ServiceStatusRuntime {
    override val state = MutableStateFlow(CoreRuntimeStatus.STOPPED)
    override val runningSinceElapsedMillis = MutableStateFlow<Long?>(null)
    override val ipRouteRevision = MutableStateFlow(0L)
    override val serviceCacheRevision = MutableStateFlow(0L)
    var reuseCalls = 0
    var reuse: suspend (List<CoreServiceCheck>, CoreRouteStamp) -> List<CoreServiceCheck> = { _, _ -> emptyList() }
    override suspend fun reuseServiceChecks(results: List<CoreServiceCheck>, expected: CoreRouteStamp, cacheRevision: Long): List<CoreServiceCheck> {
        reuseCalls++
        return reuse(results, expected)
    }
    var stamp = CoreRouteStamp(1, 1)
    val calls = mutableListOf<ServiceTarget>()
    var read: suspend (ServiceTarget) -> CoreServiceCheck = { result(it, stamp) }
    override suspend fun ipRouteStamp() = stamp
    override suspend fun checkServices(targets: List<ServiceTarget>) = targets.map { calls += it; read(it) }
}

private val qualitySample = IpQuality("8.8.8.8", IpQualitySource.IP_QUERY, IpType.HOSTING)
private class FakeServiceExits : ServiceExitStore {
    var lookups = 0
    var qualityIp: String? = null
    var forced = false
    var lookup: suspend () -> OutboundIpInfo = { OutboundIpInfo("8.8.8.8", "US") }
    var describe: suspend () -> IpQuality = { qualitySample }
    override suspend fun outbound(node: String, stamp: CoreRouteStamp): OutboundIpInfo { lookups++; return lookup() }
    override suspend fun quality(ip: String, force: Boolean): IpQuality { qualityIp = ip; forced = force; return describe() }
}
