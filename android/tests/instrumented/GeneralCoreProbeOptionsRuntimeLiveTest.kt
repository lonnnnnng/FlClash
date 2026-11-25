package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.GeneralSettings
import com.lonnnnnng.flclash.core.GeneralUpdateBoundary
import dagger.hilt.android.EntryPointAccessors
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：用授权本地正文和回环 HTTP 验证运行态测速 URL/超时热更新，不把设备配置切换成联网样本。 */
class GeneralCoreProbeOptionsRuntimeLiveTest {
    @get:Rule(order = 0) val notifications = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun runningCoreUsesUpdatedProbeUrlAndTimeoutWithoutRestarting() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashGeneralProbeOptionsAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val inputPath = args.getString("kclashGeneralProbeOptionsInput").orEmpty()
        require(Regex("/data/local/tmp/kclash-general-probe-[a-f0-9]{32}\\.json").matches(inputPath))
        val input = JSONObject(shellText("cat $inputPath"))
        val bytes = Base64.getDecoder().decode(input.getString("configBase64"))
        assertEquals(input.getString("configSha256"), sha(bytes))

        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        val manager = entry.runtimeManager()
        repository.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val originalRows = entry.database().profiles().getAll()
        val originalFiles = originalRows.associate { it.id to signature(repository.profileFile(it.id)) }
        val originalProfileId = repository.currentProfileId.value
        val originalGeneral = entry.generalSettingsStore().read()
        val originalVpn = entry.vpnSettingsStore().read()
        var fixture: Profile? = null
        var failure: Throwable? = null
        try {
            manager.setVpnEnabled(false).getOrThrow()
            fixture = repository.addFileProfile("测速热更新验收-${System.nanoTime()}", bytes)
            manager.selectProfile(fixture.id, repository::selectProfile)
            val localPort = freeInboundPort()
            val settingsBeforeRun = entry.generalSettingsStore().read()
            manager.updateGeneralSettings(settingsBeforeRun, settingsBeforeRun.copy(mixedPort = localPort)).getOrThrow()
            LocalDelayProbe().use { probe ->
                manager.startProxy().getOrThrow()
                val runningSession = manager.runningSinceElapsedMillis.value
                val before = entry.generalSettingsStore().read()
                val firstUpdate = before.copy(testUrl = probe.url, delayTestTimeout = 500)
                manager.updateGeneralSettings(before, firstUpdate).getOrThrow()
                assertEquals("测速设置热更新不能重建核心会话", runningSession, manager.runningSinceElapsedMillis.value)

                val timedOut = manager.testDelay("DIRECT", "", 0).getOrThrow()
                assertNotNull("空请求 URL 应回退到刚热更新的应用探测 URL", timedOut)
                assertEquals(probe.url, timedOut!!.url)
                assertEquals("短超时应由运行中的核心采纳", -1, timedOut.value)

                val secondUpdate = firstUpdate.copy(delayTestTimeout = 2000)
                manager.updateGeneralSettings(firstUpdate, secondUpdate).getOrThrow()
                assertEquals("超时热更新不能重启代理服务", runningSession, manager.runningSinceElapsedMillis.value)
                val completed = manager.testDelay("DIRECT", "", 0).getOrThrow()
                assertNotNull(completed)
                assertEquals(probe.url, completed!!.url)
                assertTrue("回环探测必须在放宽后的期限内成功", completed.value > 0)
                assertTrue("核心必须实际请求本地探测器", probe.requests.get() >= 2)
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_general_probe_live", "runningCore=true;urlUpdated=true;timeout500=timed-out;timeout2000=success;sessionPreserved=true;vpn=false")
            })
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                manager.stop().getOrThrow()
                val currentSettings = entry.generalSettingsStore().read()
                if (currentSettings != originalGeneral) manager.updateGeneralSettings(currentSettings, originalGeneral).getOrThrow()
                manager.setVpnEnabled(originalVpn).getOrThrow()
                originalProfileId?.let { selected ->
                    if (repository.profiles.value.any { it.id == selected }) manager.selectProfile(selected, repository::selectProfile)
                }
                fixture?.let { row ->
                    val stillOwned = entry.database().profiles().getAll().firstOrNull { it.id == row.id }
                    if (stillOwned?.label == row.label && stillOwned.type == ProfileType.FILE) repository.deleteProfile(row.id)
                }
                assertEquals(originalRows, entry.database().profiles().getAll())
                assertEquals(originalProfileId, repository.currentProfileId.value)
                assertEquals(originalFiles, originalRows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertEquals(originalGeneral, entry.generalSettingsStore().read())
                assertEquals(originalVpn, entry.vpnSettingsStore().read())
                assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    @Test fun liveCoreRejectsGeneralOptionsAndRestoresOriginalSettingsAndSession() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashGeneralProbeOptionsFailureAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val inputPath = args.getString("kclashGeneralProbeOptionsInput").orEmpty()
        require(Regex("/data/local/tmp/kclash-general-probe-[a-f0-9]{32}\\.json").matches(inputPath))
        val input = JSONObject(shellText("cat $inputPath"))
        val bytes = Base64.getDecoder().decode(input.getString("configBase64"))
        assertEquals(input.getString("configSha256"), sha(bytes))

        val context = compose.activity.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        val manager = entry.runtimeManager()
        repository.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val originalRows = entry.database().profiles().getAll()
        val originalFiles = originalRows.associate { it.id to signature(repository.profileFile(it.id)) }
        val originalProfileId = repository.currentProfileId.value
        val originalGeneral = entry.generalSettingsStore().read()
        val originalVpn = entry.vpnSettingsStore().read()
        var fixture: Profile? = null
        var failure: Throwable? = null
        try {
            manager.setVpnEnabled(false).getOrThrow()
            fixture = repository.addFileProfile("常规设置实核回滚-${System.nanoTime()}", bytes)
            manager.selectProfile(fixture.id, repository::selectProfile)
            LocalDelayProbe().use { probe ->
                val prepared = originalGeneral.copy(mixedPort = freeInboundPort(), testUrl = probe.url, delayTestTimeout = 8000)
                manager.updateGeneralSettings(originalGeneral, prepared).getOrThrow()
                manager.startProxy().getOrThrow()
                val runningSession = manager.runningSinceElapsedMillis.value
                val appliedBefore = entry.paths().configFile.readBytes()

                GeneralUpdateBoundary.arm(context.packageName, true)
                val rejected = manager.updateGeneralSettings(
                    prepared,
                    prepared.copy(testUrl = "${probe.url}?rejected=1", delayTestTimeout = 500),
                ).exceptionOrNull()
                assertNotNull("真实 Go updateConfig 必须拒绝无效 CoreOptions 类型", rejected)
                assertEquals("必须是 Go 参数解码失败，不接受无关平台异常代替", "invalid_arguments",
                    (rejected as? com.lonnnnnng.flclash.core.CoreBridgeException)?.code)
                assertEquals("一次故障加一次原值回滚应恰好到达 Bridge 两次", 2, GeneralUpdateBoundary.visits)
                assertEquals(prepared, entry.generalSettingsStore().read())
                assertEquals(prepared, manager.generalSettings.value)
                assertArrayEquals("失败后暂存配置正文必须恢复到本次注入前", appliedBefore, entry.paths().configFile.readBytes())
                assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
                assertEquals("热更新失败不能重启或停止核心", runningSession, manager.runningSinceElapsedMillis.value)

                // 作者 long：绕过已回滚的 Kotlin 设置状态，直接读取 Go 核心自己的默认探测目标。
                val delay = entry.coreBridge().testDelay("DIRECT", "", prepared.delayTestTimeout.toLong())
                assertNotNull(delay)
                assertEquals("核心仍应使用失败前的测速地址", probe.url, delay!!.url)
                assertTrue("原 8000ms 超时应允许回环探测完成", delay.value > 0)
                assertEquals(1, probe.requests.get())
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_general_update_rollback", "passed;goRejectedMalformedOptions=true;settingsAndYamlRestored=true;runningSessionPreserved=true;vpn=false")
            })
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            GeneralUpdateBoundary.reset()
            try {
                manager.stop().getOrThrow()
                val currentGeneral = entry.generalSettingsStore().read()
                if (currentGeneral != originalGeneral) manager.updateGeneralSettings(currentGeneral, originalGeneral).getOrThrow()
                manager.setVpnEnabled(originalVpn).getOrThrow()
                originalProfileId?.let { selected ->
                    if (repository.profiles.value.any { it.id == selected }) manager.selectProfile(selected, repository::selectProfile)
                }
                fixture?.let { row ->
                    val owned = repository.profiles.value.firstOrNull { it.id == row.id }
                    if (owned?.label == row.label && owned.type == ProfileType.FILE) repository.deleteProfile(row.id)
                }
                assertEquals(originalRows, entry.database().profiles().getAll())
                assertEquals(originalProfileId, repository.currentProfileId.value)
                assertEquals(originalFiles, originalRows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertEquals(originalGeneral, entry.generalSettingsStore().read())
                assertEquals(originalVpn, entry.vpnSettingsStore().read())
                assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private class LocalDelayProbe : Closeable {
        private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newCachedThreadPool()
        private val accepting = Thread {
            while (!listener.isClosed) {
                try {
                    val socket = listener.accept()
                    executor.execute {
                        socket.use { accepted ->
                            runCatching {
                                accepted.soTimeout = 3_000
                                val reader = accepted.getInputStream().bufferedReader(Charsets.US_ASCII)
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                                val request = requests.incrementAndGet()
                                // 作者 long：第一次人为超过500ms、第二次在2000ms内完成，用真实核心结果证明超时值已热更新。
                                Thread.sleep(if (request == 1) 1_200 else 100)
                                accepted.getOutputStream().apply {
                                    write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                                    flush()
                                }
                            }
                        }
                    }
                } catch (_: java.net.SocketException) {
                    break
                }
            }
        }.apply { name = "kclash-general-probe"; isDaemon = true; start() }
        val requests = AtomicInteger()
        val url = "http://127.0.0.1:${listener.localPort}/generate_204"

        override fun close() {
            listener.close()
            executor.shutdownNow()
            accepting.join(1_000)
        }
    }

    private fun shellText(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun signature(file: File): Pair<Long, String> = file.lastModified() to sha(file.readBytes())

    private fun freeInboundPort(): Int {
        val ephemeral = ServerSocket(0).use { it.localPort }
        if (ephemeral in 1024..49151) return ephemeral
        return (20000..49151).firstOrNull { port ->
            runCatching { ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", port)) } }.isSuccess
        } ?: error("没有可用于本地验收的应用端口")
    }
}
