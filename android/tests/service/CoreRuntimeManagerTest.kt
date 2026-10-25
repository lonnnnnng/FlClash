package com.lonnnnnng.flclash.service

import java.io.File
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.service.models.VpnOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.test.runTest

class CoreRuntimeManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `valid profile is staged and applied before listener starts`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val homeDir = temporaryFolder.newFolder("core")
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(7, "测试配置", source.path),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
        )

        manager.startProxy().getOrThrow()

        assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        assertEquals("mixed-port: 7890\n", File(homeDir, "config.yaml").readText())
        assertEquals(listOf("initialize", "validate", "setup", "listener-start"), bridge.events)
        assertEquals(listOf("service-start"), controller.events)
        assertTrue(controller.started)
        assertEquals("mips", controller.receivedOptions?.stack)
    }

    @Test
    fun `invalid profile never starts service or reports running`() = runTest {
        val source = temporaryFolder.newFile("invalid.yaml").apply { writeText("broken") }
        val homeDir = temporaryFolder.newFolder("core")
        val bridge = FakeBridge(validationError = IllegalArgumentException("配置格式错误"))
        val controller = FakeServiceController()
        val manager = manager(
            profile = ActiveProfile(8, "错误配置", source.path),
            homeDir = homeDir,
            bridge = bridge,
            controller = controller,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertFalse(controller.started)
        assertFalse(bridge.listenerStarted)
    }

    @Test
    fun `missing profile fails before core initialization`() = runTest {
        val bridge = FakeBridge()
        val controller = FakeServiceController()
        val manager = manager(
            profile = null,
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(emptyList<String>(), bridge.events)
        assertFalse(controller.started)
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
    }

    @Test
    fun `vpn start failure never reports running and cleans the service`() = runTest {
        val source = temporaryFolder.newFile("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val bridge = FakeBridge()
        val controller = FakeServiceController(
            startError = IllegalStateException("VPN 权限未授予"),
        )
        val manager = manager(
            profile = ActiveProfile(11, "VPN 失败", source.path),
            homeDir = temporaryFolder.newFolder("core"),
            bridge = bridge,
            controller = controller,
        )

        val result = manager.startProxy()

        assertTrue(result.isFailure)
        assertEquals(CoreRuntimeStatus.FAILED, manager.state.value)
        assertEquals(listOf("service-start", "service-stop"), controller.events)
        assertFalse(bridge.listenerStarted)
    }

    private fun manager(
        profile: ActiveProfile?,
        homeDir: File,
        bridge: FakeBridge,
        controller: FakeServiceController,
    ): CoreRuntimeManager {
        return CoreRuntimeManager(
            paths = CoreRuntimePaths(homeDir),
            profileProvider = object : ActiveProfileProvider {
                override suspend fun activeProfile(): ActiveProfile? = profile
            },
            bridge = bridge,
            serviceController = controller,
            vpnOptionsProvider = DefaultVpnOptionsProvider(),
        )
    }

    private class FakeBridge(
        private val validationError: Throwable? = null,
        private val proxyChangeError: Throwable? = null,
        private val delayError: Throwable? = null,
    ) : CoreRuntimeBridge {
        val events = mutableListOf<String>()
        var listenerStarted = false
        var proxyGroupsReads = 0

        override suspend fun initialize(homeDir: String, sdkVersion: Int) {
            events += "initialize"
        }

        override suspend fun validateConfig(path: String) {
            events += "validate"
            validationError?.let { throw it }
        }

        override suspend fun setupConfig() {
            events += "setup"
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
            return ProxyDelay(testUrl, proxyName, 123)
        }

        override suspend fun getTraffic(): CoreTraffic = CoreTraffic(up = 12, down = 34)

        override suspend fun getTotalTraffic(): CoreTraffic = CoreTraffic(up = 120, down = 340)

        override suspend fun getConnectionCount(): Int = 2

        override fun startListener() {
            events += "listener-start"
            listenerStarted = true
        }

        override fun stopListener() {
            events += "listener-stop"
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
