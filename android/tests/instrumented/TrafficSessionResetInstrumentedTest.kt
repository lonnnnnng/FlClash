package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.KClashApplication
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.readAuthorizedProxyProfile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 作者 long：只在独立motion空库中使用用户原1.yaml；回环流量走真实SOCKS监听，不靠伪造统计证明清零。 */
class TrafficSessionResetInstrumentedTest {
    @Test fun realCoreStopResetsTotalsAndSuspensionKeepsCurrentSession(): Unit = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("kclashTrafficResetAcceptance") == "true")
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        assertPortraitDefaultFontConfiguration()
        (ins.targetContext.applicationContext as KClashApplication).awaitRuntimeInitialization()
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use {
            val entry = EntryPointAccessors.fromApplication(ins.targetContext.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
            val repo = entry.profileRepository()
            val runtime = entry.runtimeManager()
            val bridge = entry.coreBridge()
            repo.profilesLoaded.first { it }
            assertTrue("只允许独立空库", entry.database().profiles().getAll().isEmpty())
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
            val original = readAuthorizedProxyProfile()
            val oldVpn = runtime.vpnSettings.value.enabled
            val oldMode = runtime.outboundMode.value
            var profile: Profile? = null
            fun record(stage: String, all: CoreTraffic, proxy: CoreTraffic) {
                ins.sendStatus(2, Bundle().apply { putString("kclash_traffic_session", JSONObject()
                    .put("stage", stage).put("pid", android.os.Process.myPid())
                    .put("up", all.up).put("down", all.down).put("proxyUp", proxy.up).put("proxyDown", proxy.down)
                    .put("state", runtime.state.value.name).toString()) })
            }
            suspend fun snapshot(stage: String): CoreTraffic {
                val all = bridge.getTotalTraffic(false)
                record(stage, all, bridge.getTotalTraffic(true))
                return all
            }
            suspend fun assertStopped(stage: String) {
                assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
                assertEquals("停止后全部累计归零", CoreTraffic(0, 0), snapshot(stage))
                assertEquals("代理口径也归零", CoreTraffic(0, 0), bridge.getTotalTraffic(true))
                assertEquals("旧速度采样也归零", CoreTraffic(0, 0), bridge.getTraffic(false))
            }
            suspend fun assertRetained(stage: String, previous: CoreTraffic): CoreTraffic {
                // 作者 long：原配置的健康检查可并发产生新流量，暂停/恢复必须保留累计下界，不能要求全局计数冻结。
                val current = snapshot(stage)
                assertTrue("暂停不能清除已有上传", current.up >= previous.up)
                assertTrue("暂停不能清除已有下载", current.down >= previous.down)
                return current
            }
            try {
                runtime.setVpnEnabled(false).getOrThrow()
                val imported = repo.addFileProfile("traffic-session-authorized", original)
                profile = imported
                runtime.selectProfile(imported.id, repo::selectProfile)
                runtime.setOutboundMode(CoreOutboundMode.DIRECT).getOrThrow()
                runtime.startProxy().getOrThrow()
                val beforeFirstTransfer = snapshot("first-start")
                val port = bridge.getLanConfig().mixedPort
                exchange(port, 64 * 1024, 128 * 1024)
                val first = snapshot("first-transfer")
                assertTrue(first.up >= beforeFirstTransfer.up + 64 * 1024)
                assertTrue(first.down >= beforeFirstTransfer.down + 128 * 1024)
                runtime.setOnDemandExcluded(true).getOrThrow()
                val suspended = assertRetained("suspended", first)
                runtime.setOnDemandExcluded(false).getOrThrow()
                val resumed = assertRetained("resumed", suspended)
                runtime.stop().getOrThrow()
                assertStopped("first-stop")
                runtime.startProxy().getOrThrow()
                val beforeSecondTransfer = snapshot("second-start")
                // 作者 long：新会话可立即发生真实探测；停止处严格验零，重启处要求旧载荷没有被恢复。
                assertTrue(beforeSecondTransfer.up < resumed.up && beforeSecondTransfer.down < resumed.down)
                exchange(port, 4 * 1024, 8 * 1024)
                val second = snapshot("second-transfer")
                assertTrue(second.up >= beforeSecondTransfer.up + 4 * 1024 && second.up < first.up)
                assertTrue(second.down >= beforeSecondTransfer.down + 8 * 1024 && second.down < first.down)
                runtime.setOnDemandExcluded(true).getOrThrow()
                assertRetained("second-suspended", second)
                runtime.stop().getOrThrow()
                assertStopped("stop-while-suspended")
                runtime.setOnDemandExcluded(false).getOrThrow()
                runtime.stop().getOrThrow()
                assertStopped("repeated-stop")
                assertArrayEquals(original, repo.profileFile(profile.id).readBytes())
            } finally {
                withContext(NonCancellable) {
                    runtime.stop().getOrThrow()
                    runtime.setOnDemandExcluded(false).getOrThrow()
                    runtime.setOutboundMode(oldMode).getOrThrow()
                    runtime.setVpnEnabled(oldVpn).getOrThrow()
                    profile?.let { current -> runtime.removeProfile(current.id, { emptyList() }, repo::selectProfile) { repo.deleteProfile(current.id) } }
                    assertTrue(entry.database().profiles().getAll().isEmpty())
                }
            }
        }
    }

    private suspend fun exchange(port: Int, uploadBytes: Int, downloadBytes: Int): Unit = coroutineScope {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 8_000
            val responder = async(Dispatchers.IO) {
                server.accept().use { peer ->
                    peer.soTimeout = 8_000
                    val input = peer.getInputStream()
                    repeat(uploadBytes) { check(input.read() == 0x5a) }
                    peer.getOutputStream().apply { write(ByteArray(downloadBytes) { 0x33 }); flush() }
                }
            }
            // 作者 long：请求必须经过实际mixed/SOCKS端口，直接连接本地server不会进入Mihomo统计。
            Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).use { client ->
                client.soTimeout = 8_000
                client.connect(InetSocketAddress("127.0.0.1", server.localPort), 8_000)
                client.getOutputStream().apply { write(ByteArray(uploadBytes) { 0x5a }); flush() }
                val received = client.getInputStream().readBytes()
                assertEquals(downloadBytes, received.size)
                assertTrue(received.all { it == 0x33.toByte() })
            }
            responder.await()
        }
    }
}
