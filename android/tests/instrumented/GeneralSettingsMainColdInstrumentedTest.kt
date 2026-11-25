package com.lonnnnnng.flclash.ui

import android.Manifest
import android.net.VpnService
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.KClashApplication
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.EncryptedDraftFile
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：准备进程必须被外部终止，验证进程只读自动恢复结果，禁止补调用 start 掩盖冷启动失败。 */
class GeneralSettingsMainColdInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mainActivityRestoresSelectedOverridesAfterRealProcessDeath() = runBlocking {
        val ins = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashGeneralMainColdAcceptance") == "true")
        val phase = args.getString("kclashGeneralMainColdPhase").orEmpty()
        val mode = args.getString("kclashGeneralMainColdMode").orEmpty()
        val token = args.getString("kclashGeneralMainColdToken").orEmpty()
        require(phase in setOf("prepare", "verify", "cleanup"))
        require(mode in setOf("stopped", "proxy", "vpn"))
        require(token.matches(Regex("[a-f0-9]{32}")))
        val context = compose.activity.applicationContext
        require(context.packageName == "com.lonnnnnng.flclash")
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        (context as KClashApplication).awaitRuntimeInitialization()
        val manager = entry.runtimeManager()
        val repo = entry.profileRepository()
        repo.profilesLoaded.first { it }
        val snapshot = EncryptedDraftFile(context, "general-main-cold-$token.v1", "com.lonnnnnng.flclash.acceptance.general-main-cold")
        val gson = Gson()
        suspend fun readSnapshot(): JSONObject = JSONObject(checkNotNull(snapshot.read()).decodeToString())
        fun emit(stage: String) = ins.sendStatus(2, Bundle().apply {
            putString("kclash_general_main_cold", "$stage;mode=$mode;pid=${android.os.Process.myPid()}")
        })
        suspend fun cleanup() {
            val saved = readSnapshot()
            manager.stop().getOrThrow()
            saved.optLong("profileId", -1).takeIf { it > 0 }?.let { id ->
                val row = entry.database().profiles().getById(id)
                if (row != null) {
                    check(row.label == "general-cold-$token")
                    assertEquals(saved.getString("sourceSha"), sha(repo.profileFile(id).readBytes()))
                    repo.deleteProfile(id)
                }
            }
            manager.updateGeneralSettings(entry.generalSettingsStore().read(), gson.fromJson(saved.getString("general"), GeneralSettings::class.java)).getOrThrow()
            manager.setVpnEnabled(saved.getBoolean("vpn")).getOrThrow()
            assertTrue(entry.database().profiles().getAll().isEmpty())
            assertNull(repo.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            snapshot.write(null)
            emit("cleaned")
        }

        if (phase == "prepare") {
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
            assertTrue("冷恢复只在本轮全新空库验收", entry.database().profiles().getAll().isEmpty())
            assertNull(repo.currentProfileId.value)
            check(snapshot.read() == null)
            val path = args.getString("kclashGeneralMainColdYaml").orEmpty()
            require(path == "/data/local/tmp/kclash-vpn-settings-$token.yaml")
            val bytes = ins.uiAutomation.executeShellCommand("cat $path").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            val expectedSha = args.getString("kclashLocalYamlSha256").orEmpty()
            assertEquals(expectedSha, sha(bytes))
            val original = entry.generalSettingsStore().read()
            val probePort = ServerSocket(0).use { it.localPort }
            val saved = JSONObject().put("general", gson.toJson(original)).put("vpn", entry.vpnSettingsStore().read())
                .put("sourceSha", expectedSha).put("mode", mode).put("pid", android.os.Process.myPid())
                .put("probePort", probePort)
            snapshot.write(saved.toString().toByteArray(Charsets.UTF_8))
            try {
                val profile = repo.addFileProfile("general-cold-$token", bytes)
                saved.put("profileId", profile.id)
                snapshot.write(saved.toString().toByteArray(Charsets.UTF_8))
                manager.selectProfile(profile.id, repo::selectProfile)
                manager.updateGeneralSettings(original, original.copy(
                    generalOverrideKeys = GeneralCoreOverrideKey.entries.toSet(),
                    unifiedDelay = false, tcpConcurrent = false, ipv6 = false, geodataLoader = "memconservative",
                    testUrl = "http://127.0.0.1:$probePort/generate_204", delayTestTimeout = 2000,
                )).getOrThrow()
                manager.setVpnEnabled(mode == "vpn").getOrThrow()
                if (mode == "vpn") {
                    VpnService.prepare(context)?.let { permission ->
                        compose.activityRule.scenario.onActivity {
                            // 作者 long：系统VPN授权依赖调用方Activity的结果关系；普通startActivity无法授予本包权限。
                            @Suppress("DEPRECATION")
                            it.startActivityForResult(permission, 8151)
                        }
                    }
                    compose.waitUntil(15_000) {
                        val root = ins.uiAutomation.rootInActiveWindow
                        if (root?.packageName?.toString() == "com.android.vpndialogs") root.findAccessibilityNodeInfosByViewId("android:id/button1")
                            .singleOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        VpnService.prepare(context) == null
                    }
                }
                if (mode != "stopped") manager.startProxy().getOrThrow()
                compose.onNodeWithTag("nav_tools").performClick()
                compose.onNodeWithTag("tools_item_general").performScrollTo().performClick()
                compose.onNodeWithTag("page_general").assertExists()
                emit("ready-for-external-death")
                // 作者 long：返回测试会被 runner 清进程，必须在准备完成的同一 PID 仍存活时由主机执行终止。
                kotlinx.coroutines.delay(180_000)
                error("主机未在准备窗口内终止进程")
            } catch (failure: Throwable) {
                try { cleanup() } catch (restore: Throwable) { failure.addSuppressed(restore) }
                throw failure
            }
        } else {
            check(snapshot.read() != null)
            val saved = readSnapshot()
            assertEquals(mode, saved.getString("mode"))
            if (phase == "cleanup") { cleanup(); return@runBlocking }
            try {
                assertNotEquals("必须来自另一真实进程", saved.getInt("pid"), android.os.Process.myPid())
                val original = gson.fromJson(saved.getString("general"), GeneralSettings::class.java)
                val expected = original.copy(generalOverrideKeys = GeneralCoreOverrideKey.entries.toSet(),
                    unifiedDelay = false, tcpConcurrent = false, ipv6 = false, geodataLoader = "memconservative",
                    testUrl = "http://127.0.0.1:${saved.getInt("probePort")}/generate_204", delayTestTimeout = 2000)
                assertEquals(expected, entry.generalSettingsStore().read())
                assertEquals(expected, manager.generalSettings.value)
                assertEquals(saved.getLong("profileId"), repo.currentProfileId.value)
                assertEquals(saved.getString("sourceSha"), sha(repo.profileFile(saved.getLong("profileId")).readBytes()))
                assertEquals(if (mode == "stopped") CoreRuntimeStatus.STOPPED else CoreRuntimeStatus.RUNNING, manager.state.value)
                assertEquals(mode == "vpn", ServiceConfig.vpnAlive.value)
                if (mode != "stopped") {
                    assertTrue(ServiceConfig.proxyAlive.value)
                    val applied = JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject
                    assertFalse(applied.get("unified-delay").asBoolean)
                    assertFalse(applied.get("tcp-concurrent").asBoolean)
                    assertFalse(applied.get("ipv6").asBoolean)
                    assertEquals("memconservative", applied.get("geodata-loader").asString)
                    ServerSocket(saved.getInt("probePort"), 1, InetAddress.getByName("127.0.0.1")).use { server ->
                        server.soTimeout = 6000
                        val responder = Thread {
                            server.accept().use { socket ->
                                socket.soTimeout = 5000
                                val reader = socket.getInputStream().bufferedReader()
                                while (!reader.readLine().isNullOrEmpty()) { /* 作者 long：读完请求头后才返回204。 */ }
                                Thread.sleep(700)
                                socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            }
                        }.apply { isDaemon = true; start() }
                        try {
                            val delay = manager.testDelay("DIRECT", "", 0).getOrThrow()
                            assertNotNull(delay)
                            assertEquals(expected.testUrl, delay!!.url)
                            assertTrue("新进程核心应沿用 2000ms 超时并完成真实回环请求", delay.value > 0)
                        } finally { responder.join(6500) }
                    }
                }
                compose.onNodeWithTag("nav_tools").performClick()
                compose.onNodeWithTag("tools_item_general").performScrollTo().performClick()
                compose.onNodeWithTag("general_timeout").performScrollTo().assertTextContains("2000 ms")
                compose.onNodeWithTag("general_override_unified_delay_enabled").performScrollTo().assertIsOn()
                compose.onNodeWithTag("general_override_unified_delay_value").assertIsOff()
                emit("verified")
            } finally { cleanup() }
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
