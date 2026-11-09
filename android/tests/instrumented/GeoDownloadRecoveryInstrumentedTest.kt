package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.data.readAuthorizedProxyProfile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.GeoResourceType
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：故障注入只允许独立空motion库和用户原1.yaml，正式包及其配置不参与回环改源。 */
class GeoDownloadRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun originalGeoUrlsDownloadFullBodiesAcrossAutomaticAndFixedLeaves() = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation()
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashGeoLive"))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = entry.profileRepository()
        val runtime = entry.runtimeManager()
        val bridge = entry.coreBridge()
        repo.profilesLoaded.first { it }
        assertTrue(entry.database().profiles().getAll().isEmpty())
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val oldSettings = runtime.geoSettings.value
        val originalInput = readAuthorizedProxyProfile()
        var imported: com.lonnnnnng.flclash.data.Profile? = null
        val observations = CopyOnWriteArrayList<CoreEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bridge.events.collect { event ->
                if (event is CoreEvent.Log && event.payload.startsWith("[GEO] resource=") ||
                    event is CoreEvent.GeoUpdate || event is CoreEvent.Request &&
                    event.connection.metadata.host in setOf("github.com", "release-assets.githubusercontent.com")) observations += event
            }
        }
        val runs = JSONArray()
        var automaticPassed = true
        var stagedOriginal: ByteArray? = null
        var testedGroup: String? = null
        var originalSelection = ""
        try {
            runtime.setGeoSettings(com.lonnnnnng.flclash.service.GeoResourceSettings(autoUpdate = false))
            val profile = repo.addFileProfile("geo-live-authorized", originalInput)
            imported = profile
            runtime.selectProfile(profile.id, repo::selectProfile)
            stagedOriginal = entry.paths().configFile.readBytes()
            // 作者 long：只关闭独立核心暂存输入的条件缓存，强制验正文；授权源文件与正式配置不改写。
            val staged = com.google.gson.JsonParser.parseString(
                com.lonnnnnng.flclash.service.YamlConfigCodec.toJson(stagedOriginal.decodeToString())
            ).asJsonObject
            staged.addProperty("etag-support", false)
            entry.paths().configFile.writeText(com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(staged.toString()))
            bridge.setupConfig()
            bridge.updateLogLevel("info")
            bridge.startLog()
            val group = bridge.getProxyGroups().groups.single { it.isSelector && it.name.contains("代码托管") }
            testedGroup = group.name
            val originalTarget = checkNotNull(group.now)
            originalSelection = originalTarget
            assertTrue(group.all.containsAll(listOf("CF-VLESS-WARP-80", "CF-VLESS-WARP-8880")))
            for ((index, target) in listOf(originalTarget, "CF-VLESS-WARP-80", "CF-VLESS-WARP-8880", originalTarget).withIndex()) {
                bridge.changeProxy(group.name, target)
                val selected = bridge.getProxyGroups().groups.single { it.name == group.name }
                assertEquals("本轮测试节点必须确实生效", target, selected.now)
                observations.clear()
                val started = android.os.SystemClock.elapsedRealtime()
                val completions = coroutineScope {
                    GeoResourceType.entries.map { type -> async {
                        runtime.requestGeoUpdate(type)
                        withTimeout(180_000) { runtime.geoUpdates.first { it[type.name]?.updating == false }.getValue(type.name) }
                    } }.awaitAll()
                }
                delay(250)
                val attemptLogs = observations.filterIsInstance<CoreEvent.Log>().map { it.payload }
                val chains = observations.filterIsInstance<CoreEvent.Request>().map { it.connection }.map {
                    JSONObject().put("host", it.metadata.host).put("chains", JSONArray(it.chains))
                        .put("rule", it.rule).put("rulePayload", it.rulePayload)
                }
                val files = JSONArray()
                var fullBodies = 0
                for (type in GeoResourceType.entries) {
                    val file = File(entry.paths().homeDir, type.fileName)
                    val bytes = file.readBytes()
                    val completion = completions.single { it.type == type.name }
                    val full = attemptLogs.any { line ->
                        "resource=${type.name} " in line && "status=200 " in line &&
                            Regex("bytes=(\\d+)").find(line)?.groupValues?.get(1)?.toLong() == file.length() &&
                            "outcome=downloaded" in line
                    }
                    if (full && completion.error.isEmpty()) fullBodies++
                    files.put(JSONObject().put("type", type.name).put("success", completion.error.isEmpty())
                        .put("fullBody", full).put("bytes", bytes.size).put("mtime", file.lastModified())
                        .put("sha256", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                        .put("skipped", completion.skipped).put("error", completion.error))
                }
                val run = JSONObject().put("cycle", index + 1).put("target", target)
                    .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started).put("files", files)
                    .put("fullBodies", fullBodies).put("attemptLogs", JSONArray(attemptLogs)).put("connections", JSONArray(chains))
                runs.put(run)
                // 作者 long：设备状态行有长度上限，分片并校验SHA后回传，避免截断JSON被当作完整证据。
                val raw = run.toString().toByteArray()
                val chunks = android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP).chunked(2400)
                val sha = java.security.MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
                chunks.forEachIndexed { part, chunk ->
                    ins.sendStatus(2, Bundle().apply { putString("kclash_geo_live_chunk", JSONObject()
                        .put("cycle", index + 1).put("index", part).put("count", chunks.size)
                        .put("bytes", raw.size).put("sha256", sha).put("base64", chunk).toString()) })
                }
                if (index == 0 || index == 3) automaticPassed = automaticPassed && fullBodies == 4
                assertArrayEquals(originalInput, repo.profileFile(profile.id).readBytes())
            }
            ins.sendStatus(2, Bundle().apply { putString("kclash_geo_live_summary", JSONObject().put("cycles", runs.length())
                .put("automaticPassed", automaticPassed).put("sourceUnchanged", true).toString()) })
            assertTrue("自动路径完整正文验收失败，逐次证据保留", automaticPassed)
        } finally {
            withContext(NonCancellable) {
                val failures = mutableListOf<String>()
                suspend fun cleanup(name: String, block: suspend () -> Unit) {
                    try { block() } catch (_: Exception) { failures += name }
                }
                cleanup("terminal") { withTimeout(180_000) { runtime.geoUpdates.first { events -> events.values.none { it.updating } } } }
                cleanup("log") { bridge.stopLog(); bridge.updateLogLevel("error") }
                cleanup("collector") { collector.cancelAndJoin() }
                cleanup("settings") { runtime.setGeoSettings(oldSettings) }
                cleanup("selection") { testedGroup?.let { bridge.changeProxy(it, originalSelection) } }
                cleanup("staged") { stagedOriginal?.let { entry.paths().configFile.writeBytes(it) } }
                cleanup("profile") { imported?.let { p -> runtime.removeProfile(p.id, { repo.profiles.value.map { it.id } }, repo::selectProfile) { repo.deleteProfile(p.id) } } }
                check(failures.isEmpty()) { "实网验收清理失败：${failures.joinToString()}" }
            }
        }
    }

    @Test fun geoRetriesKeepBusyProtectOldFileAndAllowManualRecovery() = runBlocking(Dispatchers.IO) {
        val ins = InstrumentationRegistry.getInstrumentation()
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashGeoRecovery"))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = entry.profileRepository()
        val runtime = entry.runtimeManager()
        repo.profilesLoaded.first { it }
        assertTrue(entry.database().profiles().getAll().isEmpty())
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val oldSettings = runtime.geoSettings.value
        var vm: ResourcesViewModel? = null
        compose.activityRule.scenario.onActivity { vm = ViewModelProvider(it)[ResourcesViewModel::class.java] }
        val overview = checkNotNull(vm)
        val requests = AtomicInteger()
        val mode = AtomicInteger()
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val completions = CopyOnWriteArrayList<CoreEvent.GeoUpdate>()
        val events = launch(start = CoroutineStart.UNDISPATCHED) {
            entry.coreBridge().events.filterIsInstance<CoreEvent.GeoUpdate>().filter { it.type == "MMDB" }.collect { completions += it }
        }
        var server: Thread? = null
        var original: ByteArray? = null
        var originalTime = 0L
        val file = File(entry.paths().homeDir, GeoResourceType.MMDB.fileName)
        val report = JSONArray()
        var imported: com.lonnnnnng.flclash.data.Profile? = null
        try {
            runtime.setGeoSettings(oldSettings.copy(autoUpdate = false))
            val profile = repo.addFileProfile("geo-recovery-authorized", readAuthorizedProxyProfile())
            imported = profile
            runtime.selectProfile(profile.id, repo::selectProfile)
            original = file.readBytes()
            originalTime = file.lastModified()
            val bytes = checkNotNull(original)
            val profilesBefore = entry.database().profiles().getAll()
            val profileBytes = repo.profileFile(profile.id).readBytes()
            server = thread(isDaemon = true, name = "geo-retry-fixture") {
                try {
                    while (!socket.isClosed) socket.accept().use { connection ->
                        connection.soTimeout = 5000
                        val reader = connection.getInputStream().bufferedReader()
                        while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
                        val attempt = requests.incrementAndGet()
                        val status = when (mode.get()) {
                            0 -> if (attempt == 1) 504 else 200
                            1 -> 504
                            2 -> 403
                            else -> 200
                        }
                        val body = if (mode.get() == 4) "invalid-database".toByteArray() else bytes
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 $status Result\r\nContent-Length: ${if (status == 200) body.size else 0}\r\nConnection: close\r\n\r\n".toByteArray())
                            if (status == 200) {
                                // 作者 long：第二次仅发送前缀，验证截断仍处于同一操作内，第三次重新下载全部旧库。
                                if (mode.get() == 0 && attempt == 2) write(body, 0, 32) else write(body)
                            }
                            flush()
                        }
                    }
                } catch (_: java.net.SocketException) { /* 作者 long：关闭独占监听结束夹具。 */ }
            }
            val link = "http://127.0.0.1:${socket.localPort}/database?sig=fixture-secret"
            runtime.setGeoSettings(oldSettings.copy(autoUpdate = false, urls = oldSettings.urls + (GeoResourceType.MMDB to link)))
            withTimeout(10_000) { overview.state.first { it.rows.any { row -> row.id == "MMDB" && row.url == link } } }
            suspend fun runCase(label: String, scenario: Int, expectedAttempts: Int, success: Boolean) {
                mode.set(scenario); requests.set(0); completions.clear()
                val beforeTime = file.lastModified()
                withContext(Dispatchers.Main) {
                    overview.clearFeedback()
                    overview.sync(overview.state.value.rows.first { it.id == "MMDB" })
                }
                withTimeout(5000) { while (requests.get() == 0) delay(10) }
                if (scenario == 0) {
                    assertTrue("重试期间必须保持忙态", "GEO:MMDB" in overview.state.value.updating)
                    assertNull("临时失败不能提前展示成功", overview.state.value.message)
                    assertTrue(completions.none { !it.updating })
                    assertArrayEquals(bytes, file.readBytes())
                    assertEquals(beforeTime, file.lastModified())
                }
                val final = withTimeout(35_000) { overview.state.first { it.updating.isEmpty() && (it.message != null || it.failures.isNotEmpty()) } }
                withTimeout(5000) { while (completions.count { !it.updating } != 1) delay(10) }
                assertEquals(expectedAttempts, requests.get())
                assertEquals(success, final.failures.isEmpty())
                assertEquals(1, completions.count { it.updating })
                assertEquals(1, completions.count { !it.updating })
                assertArrayEquals(bytes, file.readBytes())
                if (!success) assertEquals(beforeTime, file.lastModified())
                assertFalse(completions.any { "fixture-secret" in it.error })
                report.put(JSONObject().put("case", label).put("attempts", requests.get()).put("success", success)
                    .put("terminalEvents", completions.count { !it.updating }).put("oldBytesPreserved", true))
            }
            runCase("gateway-truncated-recovered", 0, 3, true)
            runCase("persistent-gateway-failure", 1, 3, false)
            runCase("manual-recovery", 3, 1, true)
            runCase("forbidden-no-retry", 2, 1, false)
            runCase("invalid-database-no-retry", 4, 1, false)
            assertEquals(profilesBefore, entry.database().profiles().getAll())
            assertArrayEquals(profileBytes, repo.profileFile(profile.id).readBytes())
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
            ins.sendStatus(2, Bundle().apply { putString("kclash_geo_recovery", report.toString()) })
        } finally {
            // 作者 long：测试等待可能比核心预算短，先等终态再恢复；某一步失败也不能跳过其余独占数据清理。
            withContext(NonCancellable) {
                val failures = mutableListOf<String>()
                suspend fun cleanup(name: String, block: suspend () -> Unit) {
                    try { block() } catch (_: Exception) { failures += name }
                }
                cleanup("server") { socket.close(); server?.join(1000) }
                cleanup("download") { withTimeout(180_000) { runtime.geoUpdates.first { it["MMDB"]?.updating != true } } }
                cleanup("events") { events.cancelAndJoin() }
                cleanup("settings") { runtime.setGeoSettings(oldSettings) }
                cleanup("bytes") { original?.let { check(file.readBytes().contentEquals(it)) } }
                cleanup("mtime") { if (original != null) check(file.setLastModified(originalTime)) }
                cleanup("profile") {
                    imported?.let { profile ->
                        runtime.removeProfile(profile.id, { repo.profiles.value.map { it.id } }, repo::selectProfile) { repo.deleteProfile(profile.id) }
                    }
                }
                check(failures.isEmpty()) { "验收清理失败：${failures.joinToString()}" }
            }
        }
    }
}
