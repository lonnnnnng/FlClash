package com.lonnnnnng.flclash.service

import java.io.File
import com.lonnnnnng.flclash.core.ProxyGroupsSnapshot
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
        )
    }

    private class FakeBridge(
        private val validationError: Throwable? = null,
        private val proxyChangeError: Throwable? = null,
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

        override fun startListener() {
            events += "listener-start"
            listenerStarted = true
        }

        override fun stopListener() {
            events += "listener-stop"
            listenerStarted = false
        }
    }

    private class FakeServiceController : ProxyServiceController {
        val events = mutableListOf<String>()
        var started = false

        override fun start() {
            events += "service-start"
            started = true
        }

        override fun stop() {
            events += "service-stop"
            started = false
        }
    }
}
