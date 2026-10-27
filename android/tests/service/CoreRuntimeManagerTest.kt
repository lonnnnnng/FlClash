package com.lonnnnnng.flclash.service

import java.io.File
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreBridgeException
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreEventFlowStats
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.service.models.VpnOptions
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CoreRuntimeManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun clearDiagnosticHistory() {
        RuntimeDiagnosticHistory.clear()
    }

    @Test
    fun `valid profile is staged and applied before listener starts`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply {
            writeText(
                """
                    mixed-port: 7890
                    proxy-providers:
                      cloud:
                        type: http
                        url: https://example.com/cloud
                """.trimIndent() + "\n",
            )
        }
        val homeDir = temporaryFolder.newFolder("core")
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(
                id = 7,
                label = "测试配置",
                filePath = source.path,
                overridesJson = ProfileProviderOverrides(
                    mapOf("cloud" to ProxyProviderOverride(timeout = 2_000)),
                ).toJsonString(),
            ),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        manager.startProxy().getOrThrow()

        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(
            """
                mixed-port: 7890
                proxy-providers:
                  cloud:
                    type: http
                    url: https://example.com/cloud
                    health-check:
                      enable: true
                      timeout: 2000
            """.trimIndent() + "\n",
            File(homeDir, "config.yaml").readText(),
        )
        assertEquals(listOf("initialize", "validate", "setup", "log-start", "listener-start"), bridge.calls)
        assertEquals(listOf("service-start"), controller.events)
        assertTrue(controller.started)
        assertEquals("mips", controller.receivedOptions?.stack)
        assertTrue(resumeStore.shouldResume)
    }

    @Test
    fun `core preparation runs before native initialization`() = runTest {
        val source = temporaryFolder.newFile("prepare-profile.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        var prepared = false
        val manager = manager(
            profile = ActiveProfile(24, "离线核心资源", source.path),
            homeDir = temporaryFolder.newFolder("prepared-core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
            prepareCore = { prepared = true },
        )

        manager.startProxy().getOrThrow()

        assertTrue(prepared)
    }

    @Test
    fun `dialer overrides are included in staged config before validation`() = runTest {
        val source = temporaryFolder.newFile("dialer-profile.yaml").apply {
            writeText(
                """
                    mixed-port: 7890
                    proxies:
                      - name: node-a
                        type: vmess
                      - name: node-b
                        type: trojan
                    proxy-groups:
                      - name: Relay
                        type: select
                        proxies:
                          - node-b
                """.trimIndent() + "\n",
            )
        }
        val homeDir = temporaryFolder.newFolder("dialer-core")
        val bridge = FakeBridge()
        val manager = manager(
            profile = ActiveProfile(
                id = 8,
                label = "Dialers 配置",
                filePath = source.path,
                overridesJson = ProfileProviderOverrides(
                    dialers = mapOf("node-a" to "Relay"),
                ).toJsonString(),
            ),
            homeDir = homeDir,
            bridge = bridge,
            controller = FakeServiceController(),
        )

        manager.startProxy().getOrThrow()

        val staged = File(homeDir, "config.yaml").readText()
        assertTrue(staged.contains("dialer-proxy: Relay"))
        assertEquals(listOf("initialize", "validate", "setup", "log-start", "listener-start"), bridge.calls)
    }

    @Test
    fun `bound profile script transforms staged config before provider overrides`() = runTest {
        val source = temporaryFolder.newFile("profile-script.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val script = temporaryFolder.newFile("profile.js").apply {
            writeText("function main(config) { return config }")
        }
        val homeDir = temporaryFolder.newFolder("script-core")
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(
                id = 21,
                label = "脚本配置",
                filePath = source.path,
                scriptPath = script.path,
            ),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String =
                    "{\"mixed-port\":7891,\"proxy-providers\":{}}"
            },
        )

        manager.startProxy().getOrThrow()

        assertTrue(File(homeDir, "config.yaml").readText().contains("mixed-port: 7891"))
        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
    }

    @Test
    fun `script failure prevents core setup and service start`() = runTest {
        val source = temporaryFolder.newFile("script-failure.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val script = temporaryFolder.newFile("broken.js").apply {
            writeText("function main(config) { throw new Error('broken') }")
        }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(
                id = 22,
                label = "失败脚本",
                filePath = source.path,
                scriptPath = script.path,
            ),
            homeDir = temporaryFolder.newFolder("failure-core"),
            bridge = bridge,
            controller = controller,
            profileScriptEvaluator = object : ProfileScriptEvaluator {
                override suspend fun evaluate(script: String, configJson: String): String =
                    error("脚本执行失败")
            },
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertFalse(controller.started)
        assertEquals(listOf("initialize"), bridge.calls)
    }

    @Test
    fun `invalid profile never starts service or reports running`() = runTest {
        val source = temporaryFolder.newFile("invalid.yaml").apply { writeText("broken") }
        val homeDir = temporaryFolder.newFolder("core")
        val bridge = FakeBridge(validationError = IllegalArgumentException("配置格式错误"))
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(8, "错误配置", source.path),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertFalse(controller.started)
        assertFalse(bridge.listenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `core failure diagnostics keep code and redact raw core message`() = runTest {
        val source = temporaryFolder.newFile("invalid-with-detail.yaml").apply {
            writeText("broken\n")
        }
        val diagnosticStore = FakeRuntimeDiagnosticStore()
        val bridge = FakeBridge(
            validationError = CoreBridgeException(
                code = "invalid_config",
                message = "line 7: token=secret is not allowed",
            ),
        )
        val manager = manager(
            profile = ActiveProfile(23, "错误配置详情", source.path),
            homeDir = temporaryFolder.newFolder("detail-core"),
            bridge = bridge,
            controller = FakeServiceController(),
            runtimeDiagnosticStore = diagnosticStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(
            "line 7: token=secret is not allowed",
            result.exceptionOrNull()?.message,
        )
        assertEquals(
            "invalid_config: line 7: [REDACTED_SECRET] is not allowed",
            diagnosticStore.events.single().detail,
        )
        assertEquals(listOf("initialize", "validate"), bridge.calls)
    }

    @Test
    fun `missing profile fails before core initialization`() = runTest {
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(emptyList<String>(), bridge.calls)
        assertFalse(controller.started)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `proxy switch failure is returned without hiding the core error`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge(proxyChangeError = IllegalStateException("节点不可用"))
        val manager = manager(
            profile = ActiveProfile(9, "可用配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val result = manager.changeProxy("Proxy", "香港-01")

        assertTrue(result.isFailure)
        assertEquals("节点不可用", result.exceptionOrNull()?.message)
        assertEquals(0, bridge.proxyGroupsReads)
    }

    @Test
    fun `delay test fails while core is stopped`() = runTest {
        val bridge = FakeBridge()
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )

        val result = manager.testDelay("香港-01", "https://example.com", 8_000)

        assertTrue(result.isFailure)
        assertEquals("请先启动核心", result.exceptionOrNull()?.message)
    }

    @Test
    fun `delay bridge failure is returned without fabricating a result`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge(delayError = IllegalStateException("测速失败"))
        val manager = manager(
            profile = ActiveProfile(10, "可用配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val result = manager.testDelay("香港-01", "https://example.com", 8_000)

        assertTrue(result.isFailure)
        assertEquals("测速失败", result.exceptionOrNull()?.message)
    }

    @Test
    fun `batch delay keeps successful values and individual failures`() = runTest {
        val source = temporaryFolder.newFile("batch-delay-profile.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val bridge = FakeBridge(delayErrors = setOf("node-b"))
        val manager = manager(
            profile = ActiveProfile(11, "批量测速", source.path),
            homeDir = temporaryFolder.newFolder("batch-delay-core"),
            bridge = bridge,
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val results = manager.testDelayBatch(
            requests = listOf(
                ProxyDelayRequest("node-a", "https://one"),
                ProxyDelayRequest("node-b", "https://one"),
            ),
            maxConcurrent = 2,
        ).getOrThrow()

        assertEquals(123, results[0].result?.value)
        assertEquals("测速失败", results[1].error?.message)
    }

    @Test
    fun `batch delay propagates cancellation instead of returning failure`() = runTest {
        val source = temporaryFolder.newFile("batch-delay-cancel-profile.yaml").apply {
            writeText("mixed-port: 7890\n")
        }
        val manager = manager(
            profile = ActiveProfile(12, "批量测速取消", source.path),
            homeDir = temporaryFolder.newFolder("batch-delay-cancel-core"),
            bridge = FakeBridge(delayError = CancellationException("已取消")),
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val error = assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                manager.testDelayBatch(listOf(ProxyDelayRequest("node-a", "https://one"))).getOrThrow()
            }
        }
        assertEquals("已取消", error.message)
    }

    @Test
    fun `probe result keeps the core route attribution`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val manager = manager(
            profile = ActiveProfile(14, "诊断配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
        )
        manager.startProxy().getOrThrow()

        val result = manager.probe("https://example.com").getOrThrow()

        assertEquals(listOf("GLOBAL", "FR-1-01"), result?.chains)
        assertEquals("DOMAIN-SUFFIX", result?.rule)
    }

    @Test
    fun `probe fails while core is stopped`() = runTest {
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
        )

        val result = manager.probe("https://example.com")

        assertTrue(result.isFailure)
        assertEquals("请先启动核心", result.exceptionOrNull()?.message)
    }

    @Test
    fun `runtime stats are read from the core only while running`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val manager = manager(
            profile = ActiveProfile(12, "统计配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
        )

        assertTrue(manager.getStats().isFailure)
        manager.startProxy().getOrThrow()

        assertEquals(
            CoreRuntimeStats(
                traffic = CoreTraffic(12, 34),
                totalTraffic = CoreTraffic(120, 340),
                connectionCount = 2,
            ),
            manager.getStats().getOrThrow(),
        )
        assertTrue(manager.getConnections().isSuccess)
    }

    @Test
    fun `core event listener follows runtime lifecycle`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(13, "事件配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = FakeServiceController(),
            runtimeResumeStore = resumeStore,
        )

        manager.startProxy().getOrThrow()
        assertTrue(bridge.eventListenerStarted)
        assertTrue(bridge.logListenerStarted)

        manager.stop().getOrThrow()
        assertFalse(bridge.eventListenerStarted)
        assertFalse(bridge.logListenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `core lifecycle records start stop and restart evidence`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val store = FakeRuntimeDiagnosticStore()
        val manager = manager(
            profile = ActiveProfile(18, "生命周期配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
            runtimeDiagnosticStore = store,
        )

        manager.startProxy().getOrThrow()
        manager.stop().getOrThrow()
        manager.startProxy().getOrThrow()

        assertEquals(
            listOf(
                RuntimeDiagnosticKind.CORE_STARTED,
                RuntimeDiagnosticKind.CORE_STOPPED,
                RuntimeDiagnosticKind.CORE_RESTARTED,
            ),
            RuntimeDiagnosticHistory.events.value.map { it.kind },
        )
        assertEquals(
            RuntimeDiagnosticHistory.events.value.map { it.kind },
            store.events.map { it.kind },
        )
    }

    @Test
    fun `restored running history marks the next automatic start as restart`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val store = FakeRuntimeDiagnosticStore(
            mutableListOf(
                RuntimeDiagnosticEvent(RuntimeDiagnosticKind.CORE_STARTED, "旧进程启动", 1L),
            ),
        )
        val manager = manager(
            profile = ActiveProfile(19, "重建配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = FakeServiceController(),
            runtimeDiagnosticStore = store,
        )

        manager.restoreDiagnosticHistory(markNextStartAsRestart = true)
        manager.startProxy().getOrThrow()

        assertEquals(
            listOf(RuntimeDiagnosticKind.CORE_STARTED, RuntimeDiagnosticKind.CORE_RESTARTED),
            RuntimeDiagnosticHistory.events.value.map { it.kind },
        )
    }

    @Test
    fun `vpn start failure never reports running and cleans the service`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController(
            startError = IllegalStateException("VPN 权限未授予"),
        )
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(11, "VPN 失败", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(listOf("service-start", "service-stop"), controller.events)
        assertFalse(bridge.listenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `vpn revoke stops services and marks a running runtime failed`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val resumeStore = FakeRuntimeResumeStore()
        val manager = manager(
            profile = ActiveProfile(15, "撤销配置", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
            runtimeResumeStore = resumeStore,
        )

        manager.startProxy().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()

        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(listOf("service-start", "service-stop"), controller.events)
        assertFalse(bridge.listenerStarted)
        assertFalse(bridge.eventListenerStarted)
        assertFalse(bridge.logListenerStarted)
        assertFalse(resumeStore.shouldResume)
    }

    @Test
    fun `repeated vpn revoke is idempotent`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(16, "重复撤销", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = FakeBridge(),
            controller = controller,
        )

        manager.startProxy().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()

        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(listOf("service-start", "service-stop"), controller.events)
    }

    @Test
    fun `runtime can restart after vpn revoke`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(17, "撤销后重启", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
        )

        manager.startProxy().getOrThrow()
        manager.handleVpnRevoked().getOrThrow()
        manager.startProxy().getOrThrow()

        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals(2, controller.events.count { it == "service-start" })
        assertTrue(bridge.listenerStarted)
        assertTrue(bridge.eventListenerStarted)
        assertTrue(bridge.logListenerStarted)
    }

    private fun manager(
        profile: ActiveProfile?,
        homeDir: File,
        bridge: FakeBridge,
        controller: FakeServiceController,
        runtimeResumeStore: FakeRuntimeResumeStore = FakeRuntimeResumeStore(),
        runtimeDiagnosticStore: RuntimeDiagnosticStore = NoopRuntimeDiagnosticStore,
        profileScriptEvaluator: ProfileScriptEvaluator = MissingProfileScriptEvaluator,
        prepareCore: suspend () -> Unit = {},
    ): CoreRuntimeManager {
        return CoreRuntimeManager(
            paths = CoreRuntimePaths(homeDir, prepare = prepareCore),
            profileProvider = object : ActiveProfileProvider {
                override suspend fun activeProfile(): ActiveProfile? = profile
            },
            bridge = bridge,
            serviceController = controller,
            vpnOptionsProvider = DefaultVpnOptionsProvider(),
            runtimeResumeStore = runtimeResumeStore,
            runtimeDiagnosticStore = runtimeDiagnosticStore,
            profileScriptEvaluator = profileScriptEvaluator,
        )
    }

    private class FakeRuntimeResumeStore : RuntimeResumeStore {
        var shouldResume = false

        override suspend fun shouldResume(): Boolean = shouldResume

        override suspend fun setShouldResume(value: Boolean) {
            shouldResume = value
        }
    }

    private class FakeRuntimeDiagnosticStore(
        var events: MutableList<RuntimeDiagnosticEvent> = mutableListOf(),
    ) : RuntimeDiagnosticStore {
        override suspend fun loadRecent(limit: Int): List<RuntimeDiagnosticEvent> = events.takeLast(limit)

        override suspend fun append(event: RuntimeDiagnosticEvent, limit: Int) {
            events = (events + event).takeLast(limit).toMutableList()
        }

        override suspend fun clear() {
            events.clear()
        }
    }

    private class FakeBridge(
        private val validationError: Throwable? = null,
        private val proxyChangeError: Throwable? = null,
        private val delayError: Throwable? = null,
        private val delayErrors: Set<String> = emptySet(),
    ) : CoreRuntimeBridge {
        override val events = emptyFlow<CoreEvent>()
        private val mutableEventFlowStats = MutableStateFlow(CoreEventFlowStats())
        override val eventFlowStats = mutableEventFlowStats.asStateFlow()
        val calls = mutableListOf<String>()
        var listenerStarted = false
        var eventListenerStarted = false
        var logListenerStarted = false
        var proxyGroupsReads = 0

        override suspend fun initialize(homeDir: String, sdkVersion: Int) {
            calls += "initialize"
        }

        override suspend fun validateConfig(path: String) {
            calls += "validate"
            validationError?.let { throw it }
        }

        override suspend fun setupConfig() {
            calls += "setup"
        }

        override suspend fun getProxyGroups(): ProxyGroupsSnapshot {
            proxyGroupsReads++
            return ProxyGroupsSnapshot(emptyList())
        }

        override suspend fun changeProxy(groupName: String, proxyName: String): Boolean {
            proxyChangeError?.let { throw it }
            return true
        }

        override suspend fun testDelay(proxyName: String, testUrl: String, timeoutMs: Long): ProxyDelay? {
            delayError?.let { throw it }
            if (proxyName in delayErrors) throw IllegalStateException("测速失败")
            return ProxyDelay(testUrl, proxyName, 123)
        }

        override suspend fun probe(url: String, proxyName: String, timeoutMs: Long): CoreProbeResult =
            CoreProbeResult(
                statusCode = 200,
                delay = 123,
                url = url,
                chains = listOf("GLOBAL", "FR-1-01"),
                rule = "DOMAIN-SUFFIX",
                rulePayload = "example.com",
                error = "",
                message = "",
                coreEpoch = 1,
                routeVersion = 2,
            )

        override suspend fun getTraffic(): CoreTraffic = CoreTraffic(up = 12, down = 34)

        override suspend fun getTotalTraffic(): CoreTraffic = CoreTraffic(up = 120, down = 340)

        override suspend fun getConnectionCount(): Int = 2

        override suspend fun getConnections(): List<CoreConnection> = emptyList()

        override fun startEventListener() {
            eventListenerStarted = true
        }

        override fun stopEventListener() {
            eventListenerStarted = false
        }

        override suspend fun startLog() {
            calls += "log-start"
            logListenerStarted = true
        }

        override suspend fun stopLog() {
            calls += "log-stop"
            logListenerStarted = false
        }

        override fun startListener() {
            calls += "listener-start"
            listenerStarted = true
        }

        override fun stopListener() {
            calls += "listener-stop"
            listenerStarted = false
        }
    }

    private class FakeServiceController(
        private val startError: Throwable? = null,
    ) : ProxyServiceController {
        val events = mutableListOf<String>()
        var started = false
        var receivedOptions: VpnOptions? = null

        override suspend fun start(options: VpnOptions) {
            events += "service-start"
            receivedOptions = options
            startError?.let { throw it }
            started = true
            ServiceConfig.updateVpnState(VpnRuntimeState.CONNECTED)
        }

        override fun stop() {
            events += "service-stop"
            started = false
        }
    }
}
