package com.lonnnnnng.flclash.ui

import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.*
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.service.models.VpnOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTelemetryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `slow read leaves timer ticking and hiding cancels the old sample`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<CoreTraffic>()
        val bridge = TelemetryBridge().apply { readTraffic = { pending.await() } }
        val manager = manager(bridge) { testScheduler.currentTime + 1_000 }
        manager.startProxy().getOrThrow()
        val model = MainViewModel(manager)
        try {
            model.setObservationVisible(true)
            runCurrent()
            advanceTimeBy(2_100)
            runCurrent()
            assertTrue(checkNotNull(model.uptimeMillis.value) >= 2_000)
            assertTrue(model.stats.value.trafficHistory.isEmpty())
            model.setObservationVisible(false)
            runCurrent()
            pending.complete(CoreTraffic(123, 456))
            advanceTimeBy(5_000)
            runCurrent()
            assertTrue(model.stats.value.trafficHistory.isEmpty())
            bridge.readTraffic = { CoreTraffic(7, 8) }
            model.setObservationVisible(true)
            runCurrent()
            assertEquals(listOf(CoreTraffic(7, 8)), model.stats.value.trafficHistory)
            assertTrue(checkNotNull(model.uptimeMillis.value) >= 7_000)
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
            manager.stop().getOrThrow()
        }
    }

    @Test
    fun `quick restart cannot reuse history even if stopped state was conflated`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 1_000L
        val bridge = TelemetryBridge()
        val manager = manager(bridge) { now }
        manager.startProxy().getOrThrow()
        val model = MainViewModel(manager)
        try {
            model.setObservationVisible(true)
            runCurrent()
            assertEquals(listOf(CoreTraffic(1, 2)), model.stats.value.trafficHistory)
            // 作者 long：不让调度器处理中间 STOPPED，模拟快速重启时 StateFlow 合并枚举状态。
            manager.stop().getOrThrow()
            now += 100
            bridge.readTraffic = { CoreTraffic(9, 10) }
            manager.startProxy().getOrThrow()
            runCurrent()
            assertEquals(listOf(CoreTraffic(9, 10)), model.stats.value.trafficHistory)
            assertEquals(0L, model.uptimeMillis.value)
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
            manager.stop().getOrThrow()
        }
    }

    @Test
    fun `failed reads preserve samples and a stopped session clears them`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bridge = TelemetryBridge()
        val manager = manager(bridge) { testScheduler.currentTime + 1_000 }
        manager.startProxy().getOrThrow()
        val model = MainViewModel(manager)
        try {
            model.setObservationVisible(true)
            runCurrent()
            val history = model.stats.value.trafficHistory
            bridge.readTraffic = { error("统计暂不可用") }
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(history, model.stats.value.trafficHistory)
            assertEquals("统计暂不可用", model.stats.value.error)
            manager.stop().getOrThrow()
            runCurrent()
            assertTrue(model.stats.value.trafficHistory.isEmpty())
            assertEquals(null, model.uptimeMillis.value)
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
            manager.stop().getOrThrow()
        }
    }

    @Test fun backgroundSamplingKeepsTheLastValueWithoutLoadingOrErrorFlicker() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bridge = TelemetryBridge()
        val manager = manager(bridge) { testScheduler.currentTime + 1_000 }
        manager.startProxy().getOrThrow()
        val model = MainViewModel(manager)
        try {
            model.setObservationVisible(true); runCurrent()
            val pending = CompletableDeferred<CoreTraffic>()
            bridge.readTraffic = { pending.await() }
            advanceTimeBy(1_000); runCurrent()
            assertFalse(model.stats.value.isLoading)
            assertEquals(CoreTraffic(1, 2), model.stats.value.traffic)
            pending.completeExceptionally(IllegalStateException("暂不可用")); runCurrent()
            val retry = CompletableDeferred<CoreTraffic>()
            bridge.readTraffic = { retry.await() }
            advanceTimeBy(1_000); runCurrent()
            assertFalse(model.stats.value.isLoading)
            assertEquals("暂不可用", model.stats.value.error)
            retry.complete(CoreTraffic(7, 8)); runCurrent()
            assertEquals(null, model.stats.value.error)
            assertEquals(CoreTraffic(7, 8), model.stats.value.traffic)
        } finally { model.viewModelScope.cancel(); runCurrent(); manager.stop().getOrThrow() }
    }

    private fun manager(bridge: CoreRuntimeBridge, clock: () -> Long): CoreRuntimeManager {
        val source = temporaryFolder.newFile().apply { writeText("mixed-port: 7890\n") }
        return CoreRuntimeManager(
            paths = CoreRuntimePaths(temporaryFolder.newFolder()),
            profileProvider = object : ActiveProfileProvider {
                override suspend fun activeProfile() = ActiveProfile(1, "隔离会话", source.path)
            },
            bridge = bridge,
            serviceController = object : ProxyServiceController {
                override suspend fun start(options: VpnOptions) = Unit
                override fun stop() = Unit
            },
            vpnOptionsProvider = DefaultVpnOptionsProvider(),
            runtimeResumeStore = object : RuntimeResumeStore {
                override suspend fun shouldResume() = false
                override suspend fun setShouldResume(value: Boolean) = Unit
            },
            elapsedRealtimeMillis = clock,
        )
    }
}

// 作者 long：只隔离核心 I/O 与时钟，保留正式 Manager 和 ViewModel 的协程与会话切换逻辑。
private class TelemetryBridge : CoreRuntimeBridge {
    var readTraffic: suspend () -> CoreTraffic = { CoreTraffic(1, 2) }
    override val events = emptyFlow<CoreEvent>()
    override val eventFlowStats = MutableStateFlow(CoreEventFlowStats())
    override suspend fun initialize(homeDir: String, sdkVersion: Int) = Unit
    override suspend fun validateConfig(path: String) = Unit
    override suspend fun setupConfig(selectedMap: Map<String, String>) = Unit
    override suspend fun getConfiguredMode(path: String) = CoreOutboundMode.RULE
    override suspend fun updateMode(mode: CoreOutboundMode) = Unit
    override suspend fun getProxyGroups() = ProxyGroupsSnapshot(emptyList())
    override suspend fun changeProxy(groupName: String, proxyName: String) = true
    override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): ProxyDelay? = null
    override suspend fun probe(url: String, proxyName: String, timeoutMs: Long): CoreProbeResult? = null
    override suspend fun getTraffic() = readTraffic()
    override suspend fun getTotalTraffic() = CoreTraffic(10, 20)
    override suspend fun getConnectionCount() = 0
    override suspend fun getConnections() = emptyList<CoreConnection>()
    override suspend fun clearDnsCache() = true
    override fun startEventListener() = Unit
    override fun stopEventListener() = Unit
    override suspend fun startLog() = Unit
    override suspend fun stopLog() = Unit
    override fun startListener() = Unit
    override fun stopListener() = Unit
}
