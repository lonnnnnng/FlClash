package com.lonnnnnng.flclash.service

import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.models.AccessControlProps
import com.lonnnnnng.flclash.service.models.NotificationParams
import com.lonnnnnng.flclash.service.models.VpnOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

private fun vpnOptions(port: Int) = VpnOptions(
    enable = true,
    port = port,
    ipv6 = false,
    dnsHijacking = true,
    accessControlProps = AccessControlProps(
        enable = true,
        mode = AccessControlMode.REJECT_SELECTED,
        acceptList = listOf("com.example.accepted"),
        rejectList = listOf("com.example.rejected"),
    ),
    allowBypass = true,
    systemProxy = false,
    bypassDomain = listOf("example.test"),
    stack = "system",
    routeAddress = listOf("0.0.0.0/0"),
)

class ServiceConfigTest {
    @Test
    fun `notification params default to the app name and stop label`() {
        val defaults = NotificationParams()

        assertEquals("KClash", defaults.title)
        assertEquals("STOP", defaults.stopText)
        assertEquals(false, defaults.onlyStatisticsProxy)
        assertEquals(true, defaults.showStopAction)
    }

    @Test
    fun `updateVpnOptions publishes the latest options`() {
        ServiceConfig.updateVpnOptions(vpnOptions(7890))
        assertEquals(7890, ServiceConfig.vpnOptions?.port)

        val latest = vpnOptions(7891)
        ServiceConfig.updateVpnOptions(latest)

        assertSame(latest, ServiceConfig.vpnOptions)
    }

    @Test
    fun `vpn state starts stopped and publishes failure reason`() {
        ServiceConfig.updateVpnState(VpnRuntimeState.STOPPED)
        assertEquals(VpnRuntimeState.STOPPED, ServiceConfig.vpnState.value)

        ServiceConfig.updateVpnState(VpnRuntimeState.FAILED, "建立 TUN 失败")

        assertEquals(VpnRuntimeState.FAILED, ServiceConfig.vpnState.value)
        assertEquals("建立 TUN 失败", ServiceConfig.vpnError.value)
    }

    @Test
    fun `updateNotificationParams emits through the state flow`() = runTest {
        val params = NotificationParams(
            title = "Profile",
            stopText = "Halt",
            onlyStatisticsProxy = true,
        )

        ServiceConfig.updateNotificationParams(params)

        assertSame(params, ServiceConfig.notificationParams.value)
    }

    @Test
    fun `notification params state flow keeps the newest value`() = runTest {
        val first = NotificationParams(title = "first")
        val second = NotificationParams(title = "second")

        ServiceConfig.updateNotificationParams(first)
        ServiceConfig.updateNotificationParams(second)

        assertSame(second, ServiceConfig.notificationParams.value)
        assertNotSame(first, ServiceConfig.notificationParams.value)
    }
}

class DefaultVpnOptionsProviderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun appliedConfigUsesEffectivePortAndTunOptions() = runTest {
        val file = temporaryFolder.newFile("config.json")
        file.writeText("""{"mixed-port":17890,"tun":{"stack":"system","mtu":1400,"congestion-controller":"bbr"}}""")
        val provider = DefaultVpnOptionsProvider(appliedConfigFile = file)
        val options = provider.current()
        assertEquals(17890, options.port)
        assertEquals(true, options.systemProxy)
        assertEquals("system", options.stack)
        assertEquals(1400, options.mtu)
        assertEquals("bbr", options.congestionController)
        // 作者 long：同一暂存路径会在配置切换时被原子替换，不能把上次读取的端口缓存到下一次启动。
        file.writeText("""{"mixed-port":27890}""")
        assertEquals(27890, provider.current().port)
    }

    @Test fun authenticatedOrDisabledMixedPortDoesNotAdvertiseHttpProxy() = runTest {
        val file = temporaryFolder.newFile("config.json")
        val provider = DefaultVpnOptionsProvider(appliedConfigFile = file)
        file.writeText("""{"mixed-port":17890,"authentication":["test:example"]}""")
        assertEquals(false, provider.current().systemProxy)
        file.writeText("""{"mixed-port":0}""")
        assertEquals(false, provider.current().systemProxy)
        file.writeText("""{}""")
        assertEquals(0, provider.current().port)
        assertEquals(false, provider.current().systemProxy)
    }

    @Test fun invalidAppliedConfigCannotSilentlyAdvertiseDefaultPort() = runTest {
        val file = temporaryFolder.newFile("config.json")
        val provider = DefaultVpnOptionsProvider(appliedConfigFile = file)
        file.writeText("""{"mixed-port":65536}""")
        assertEquals(true, runCatching { provider.current() }.isFailure)
        file.writeText("[")
        assertEquals(true, runCatching { provider.current() }.isFailure)
        assertEquals(true, runCatching {
            DefaultVpnOptionsProvider(appliedConfigFile = java.io.File(temporaryFolder.root, "missing")).current()
        }.isFailure)
    }

    @Test fun vpnPreferenceSelectsProxyOnlyOptionsWithoutChangingAccessSettings() = runTest {
        val store = object : VpnSettingsStore {
            var enabled = false
            override fun read() = enabled
            override fun write(enabled: Boolean) { this.enabled = enabled }
        }
        val provider = DefaultVpnOptionsProvider(vpnSettingsStore = store)
        assertEquals(false, provider.current().enable)
        store.write(true)
        assertEquals(true, provider.current().enable)
    }

    @Test
    fun `current options include sanitized access control selection`() = runTest {
        val provider = DefaultVpnOptionsProvider(
            object : AccessControlSettingsProvider {
                override suspend fun current() = AccessControlProps(
                    enable = true,
                    mode = AccessControlMode.ACCEPT_SELECTED,
                    acceptList = listOf("com.z", "", "com.z", "com.a"),
                    rejectList = listOf("com.unused"),
                )

                override suspend fun save(value: AccessControlProps) = Unit
            },
        )

        val options = provider.current()

        assertEquals(true, options.accessControlProps.enable)
        assertEquals(AccessControlMode.ACCEPT_SELECTED, options.accessControlProps.mode)
        assertEquals(listOf("com.a", "com.z"), options.accessControlProps.acceptList)
        assertEquals(listOf("com.unused"), options.accessControlProps.rejectList)
    }
}
