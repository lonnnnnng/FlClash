package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.YamlConfigCodec
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：只在原1.yaml临时副本的正式脚本投影中隔离规则；下载、更新、TCP匹配与核心回读均走真实实现。 */
class ProviderRecoveryHitsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val entry get() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private val backup get() = EncryptedDraftFile(compose.activity, "provider-hits-acceptance.v1", "com.lonnnnnng.flclash.acceptance.provider-hits")
    private data class Snapshot(val id: Long, val token: String, val selected: Long, val vpn: Boolean,
        val profileIds: List<Long>, val profileDigest: String, val profileFiles: Map<Long, String>,
        val scriptIds: List<Long>, val scriptDigest: String, val scriptFiles: Map<Long, String>, val caches: List<String>)
    private lateinit var manifest: JSONObject
    private lateinit var saved: Snapshot
    private lateinit var model: ProfileStatisticsViewModel
    private var scriptId = 0L
    private var since = 0L
    private lateinit var sourceSignature: String
    private var proveInlineProxy = false
    private val sources get() = manifest.getJSONArray("sources").let { array -> (0 until array.length()).map(array::getJSONObject) }
    private fun sha(bytes: ByteArray) = ResourceAcceptanceInputs.sha(bytes)
    private fun signature(file: File) = ResourceAcceptanceInputs.signature(file)
    private fun digest(value: Any) = sha(Gson().toJson(value).toByteArray())
    private fun report(value: JSONObject) = instrumentation.sendStatus(2, Bundle().apply { putString("kclash_provider_hits", value.toString()) })
    private fun cache(name: String) = File(entry.paths().homeDir, "${saved.token}-$name.cache")

    @Test fun realBatchPartialFailureKeepsOldCacheAndRetriesOnlyRemaining(): Unit = fixture {
        val source = sources.first { it.getString("name") == "domain-yaml" }
        val bytes = input(source)
        FaultServer(bytes).use { faults ->
            val definitions = JsonObject().apply {
                // 作者 long：成功对照与两个故障项共用受控HTTP服务，公网可用性由独立八源用例验证，避免网络波动改变故障数量。
                add("control-success", definition(source, "control-success").apply { addProperty("url", faults.url("control-success")) })
                for (name in listOf("fault-status", "fault-content")) {
                    cache(name).writeBytes(bytes)
                    add(name, definition(source, name).apply { addProperty("url", faults.url(name)) })
                }
            }
            start(definitions, "control-success")
            ready(3)
            val before = entry.runtimeManager().profileProviders(saved.id).associateBy { it.name }
            val oldCaches = listOf("fault-status", "fault-content").associateWith { signature(cache(it)) }
            faults.modes["fault-status"] = 503; faults.modes["fault-content"] = 422
            click("profile_providers_update_all"); awaitUpdate()
            report(JSONObject().put("case", "batch-failure-state").put("failed", org.json.JSONArray(model.providers.value.failedNames.toList()))
                .put("reasons", JSONObject(model.providers.value.failedMessages)).put("requests", JSONObject(faults.counts)))
            assertEquals(setOf("fault-status", "fault-content"), model.providers.value.failedNames)
            assertTrue(model.providers.value.updatingNames.isEmpty())
            assertEquals(oldCaches, oldCaches.keys.associateWith { signature(cache(it)) })
            val after = entry.runtimeManager().profileProviders(saved.id).associateBy { it.name }
            assertTrue(after.getValue("control-success").updatedAtMillis!! > before.getValue("control-success").updatedAtMillis!!)
            for (name in oldCaches.keys) assertEquals(before.getValue(name).count, after.getValue(name).count)
            capture("provider_batch_partial_failure")
            val counts = faults.counts.toMap()
            faults.modes["fault-status"] = 200
            clickUpdate("fault-status"); awaitUpdate()
            assertEquals(setOf("fault-content"), model.providers.value.failedNames)
            assertEquals(counts.getValue("fault-content"), faults.counts["fault-content"])
            val singleAt = entry.runtimeManager().profileProviders(saved.id).single { it.name == "fault-status" }.updatedAtMillis
            val controlAt = after.getValue("control-success").updatedAtMillis
            // 作者 long：前后台变化只能暂停列表读取，不能丢掉同一详情中尚未恢复的失败项。
            compose.runOnUiThread { model.setForeground(false) }
            compose.runOnUiThread { model.setForeground(true) }; ready(3)
            assertEquals(setOf("fault-content"), model.providers.value.failedNames)
            capture("provider_batch_single_recovered")
            faults.modes["fault-content"] = 200
            click("profile_providers_retry_failed"); awaitUpdate()
            assertTrue(model.providers.value.failedNames.isEmpty()); assertTrue(model.providers.value.failedMessages.isEmpty())
            compose.onNodeWithTag("profile_providers_retry_failed").assertDoesNotExist()
            val restored = entry.runtimeManager().profileProviders(saved.id).associateBy { it.name }
            assertEquals(controlAt, restored.getValue("control-success").updatedAtMillis)
            assertEquals(singleAt, restored.getValue("fault-status").updatedAtMillis)
            assertEquals(counts.getValue("fault-status") + 1, faults.counts["fault-status"])
            assertEquals(counts.getValue("fault-content") + 1, faults.counts["fault-content"])
            assertArrayEquals(bytes, cache("fault-status").readBytes()); assertArrayEquals(bytes, cache("fault-content").readBytes())
            assertSession(); capture("provider_batch_all_recovered")
            report(JSONObject().put("case", "partial-failure").put("batchSuccess", 1).put("batchFailures", 2)
                .put("controlledHttpServer", true)
                .put("failedCachesPreserved", true).put("singleRetryPreservesOtherFailure", true)
                .put("retryOnlyRemaining", true).put("unretriedTimestampsPreserved", true).put("faultRequests", JSONObject(faults.counts)))
        }
    }

    @Test fun httpFileAndInlineRulesMatchPublicHttpsTraffic(): Unit = fixture {
        val definitions = JsonObject()
        sources.forEach { definitions.add(it.getString("name"), definition(it, it.getString("name"))) }
        start(definitions, sources.first().getString("name")); ready(8)
        val before = entry.runtimeManager().profileProviders(saved.id).associateBy { it.name }
        val updates = entry.runtimeManager().updateProfileProviders(saved.id, sources.map { it.getString("name") })
        assertEquals(sources.map { it.getString("name") }, updates.map { it.name })
        assertTrue("公开HTTP资源全部真实下载后才开始匹配", updates.all { it.code.isEmpty() })
        val after = entry.runtimeManager().profileProviders(saved.id).associateBy { it.name }
        sources.forEach {
            val name = it.getString("name")
            assertEquals(it.getString("sha256"), sha(cache(name).readBytes()))
            assertTrue(after.getValue(name).count > 0)
            assertTrue("逐项更新时间必须前进", after.getValue(name).updatedAtMillis!! > before.getValue(name).updatedAtMillis!!)
            report(JSONObject().put("case", "http-download").put("provider", name).put("before", before.getValue(name).updatedAtMillis)
                .put("after", after.getValue(name).updatedAtMillis).put("count", after.getValue(name).count).put("sha256", it.getString("sha256")))
        }
        for (vehicle in listOf("http", "file")) for (source in sources) {
            val name = source.getString("name")
            val active = definitions.deepCopy()
            if (vehicle == "file") for ((_, value) in active.entrySet()) value.asJsonObject.apply {
                addProperty("type", "file"); remove("url"); remove("proxy"); remove("interval")
            }
            apply(active, name)
            verifyPublicHit(name, source.getString("behavior"), source.getString("format"), vehicle)
        }
        for (behavior in listOf("domain", "ipcidr", "classical")) {
            val source = sources.single { it.getString("behavior") == behavior && it.getString("format") == "yaml" }
            val name = "inline-$behavior"
            val payload = JsonParser.parseString(YamlConfigCodec.toJson(input(source).decodeToString())).asJsonObject.getAsJsonArray("payload")
            val active = JsonObject().apply { add(name, JsonObject().apply {
                addProperty("type", "inline"); addProperty("behavior", behavior); add("payload", payload)
            }) }
            apply(active, name)
            verifyPublicHit(name, behavior, "inline", "inline")
        }
        assertSession()
        report(JSONObject().put("case", "rule-matches").put("http", 8).put("file", 8).put("inline", 3)
            .put("transport", "real-WiFi-public-HTTPS").put("internetResourceDownloads", 8)
            .put("everyRequest", "hostname-verified-TLS-HTTP200-body-RuleSet-exactProvider-upload-download"))
    }

    @Test fun originalDomainMrsMatchesPublicHttpsResponse(): Unit = fixture {
        val root = JsonParser.parseString(YamlConfigCodec.toJson(readAuthorizedProxyProfile().decodeToString())).asJsonObject
        val source = root.getAsJsonObject("rule-providers").getAsJsonObject("geolocation-cn").deepCopy()
        val name = "original-domain"
        source.addProperty("url", "https://gh-proxy.com/" + source["url"].asString)
        source.addProperty("path", cache(name).path); source.addProperty("interval", 0); source.addProperty("proxy", "DIRECT")
        start(JsonObject().apply { add(name, source) }, name); ready(1)
        val runtime = entry.runtimeManager()
        // 作者 long：公开加速服务可暂时拒绝请求；保留每次失败记录，仅允许三次真实更新，耗尽仍判失败。
        for (attempt in 1..3) {
            try {
                runtime.updateProfileProvider(saved.id, name)
                report(JSONObject().put("case", "original-mrs-download").put("attempt", attempt).put("success", true))
                break
            } catch (error: com.lonnnnnng.flclash.service.ProviderOperationException) {
                report(JSONObject().put("case", "original-mrs-download").put("attempt", attempt).put("success", false).put("type", error.javaClass.simpleName))
                if (attempt == 3) throw error
                delay(attempt * 1_000L)
            }
        }
        assertTrue(runtime.profileProviders(saved.id).single().count > 0)
        withContext(Dispatchers.IO) {
            val host = "www.baidu.com"
            val proxyPort = effectiveSocksPort()
            Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort))).use { socket ->
                socket.connect(InetSocketAddress.createUnresolved(host, 443), 20_000); socket.soTimeout = 20_000
                // 作者 long：在TLS握手前订阅本次连接，服务器即使快速关闭，也不能拿另一条连接的规则归因顶替。
                val observing = async {
                    withTimeout(20_000) {
                        while (true) {
                            val found = entry.coreBridge().getConnections().singleOrNull {
                                it.metadata.sourcePort == socket.localPort.toString() && it.metadata.destinationPort == "443" && it.metadata.host == host }
                            if (found != null && found.upload > 0 && found.download > 0) return@withTimeout found
                            delay(50)
                        }
                        @Suppress("UNREACHABLE_CODE") error("公网连接不可见")
                    }
                }
                (javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory)
                    .createSocket(socket, host, 443, true).use { secure ->
                        secure as javax.net.ssl.SSLSocket
                        secure.sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                        secure.startHandshake()
                        secure.getOutputStream().apply { write("GET / HTTP/1.1\r\nHost: $host\r\nConnection: keep-alive\r\n\r\n".toByteArray()); flush() }
                        val input = secure.getInputStream().buffered()
                        fun line(): String = buildString { while (true) { val value = input.read(); check(value >= 0); if (value == 10) break; if (value != 13) append(value.toChar()) } }
                        val status = line(); assertTrue(status.startsWith("HTTP/1.1 200 ") || status.startsWith("HTTP/1.0 200 "))
                        while (line().isNotEmpty()) Unit
                        assertTrue("公网响应必须有正文", input.read() >= 0)
                        val connection = observing.await()
                        assertEquals("RuleSet", connection.rule); assertEquals(name, connection.rulePayload)
                        assertTrue(connection.chains.contains("DIRECT"))
                        report(JSONObject().put("case", "public-internet-hit").put("source", "original-geolocation-cn-mrs")
                            .put("status", 200).put("hostnameVerifiedTls", true).put("responseBodyPresent", true)
                            .put("rule", connection.rule).put("provider", connection.rulePayload).put("connectionId", connection.id)
                            .put("sourcePort", socket.localPort).put("targetSha256", sha(host.toByteArray()))
                            .put("upload", connection.upload).put("download", connection.download).put("mrsSha256", sha(cache(name).readBytes())))
                    }
            }
        }
        assertSession()
    }

    @Test fun inlineProxyProviderExpandsOriginalNodesInRunningCore(): Unit = fixture {
        proveInlineProxy = true
        val source = sources.first(); val name = source.getString("name")
        start(JsonObject().apply { add(name, definition(source, name)) }, name); ready(1)
        val expected = JsonParser.parseString(YamlConfigCodec.toJson(readAuthorizedProxyProfile().decodeToString())).asJsonObject
            .getAsJsonArray("proxies").take(2).map { it.asJsonObject["name"].asString }
        val loaded = entry.coreBridge().getProxyGroups()
        assertTrue("inline资源应由真实核心展开原配置节点", loaded.groups.single { it.name == "provider-inline-proof" }.all == expected)
        assertTrue(expected.all { loaded.proxyTypes.containsKey(it) })
        assertFalse(entry.runtimeManager().profileProviders(saved.id).any { it.name == "provider-inline" })
        assertSession()
        report(JSONObject().put("case", "proxy-inline").put("source", "original-1.yaml-proxies")
            .put("count", expected.size).put("realCoreGroupExpanded", true).put("intentionallyAbsentFromExternalList", true)
            .put("protocolTrafficTested", false))
    }

    private fun fixture(block: suspend () -> Unit): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashProviderHitsAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val connectivity = compose.activity.getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        assertTrue("本批流量验收要求当前默认网络为Wi-Fi", capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
        val path = checkNotNull(args.getString("kclashProviderHitsManifest"))
        check(path.matches(Regex("/sdcard/Download/provider-hits-[0-9]+-manifest\\.json")))
        val data = ResourceAcceptanceInputs.shell("cat $path")
        assertEquals(args.getString("kclashProviderHitsManifestSha256"), sha(data))
        manifest = JSONObject(data.decodeToString()); check(sources.size == 8)
        val runtime = entry.runtimeManager(); val repo = entry.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value); assertNull("先恢复上次验收", backup.read())
        repo.withAutomaticUpdateBarrier {
            val rows = entry.database().profiles().getAll(); val scripts = entry.database().scripts().getAll()
            val selected = withTimeout(20_000) { repo.currentProfileId.first { id -> rows.any { it.id == id } } }!!
            val token = manifest.getString("token") + "-" + System.nanoTime()
            val id = System.nanoTime().and(Long.MAX_VALUE)
            saved = Snapshot(id, token, selected, entry.vpnSettingsStore().read(), rows.map { it.id }, digest(rows),
                rows.associate { it.id to signature(repo.profileFile(it.id)) }, scripts.map { it.id }, digest(scripts),
                scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) },
                (sources.map { it.getString("name") } + listOf("control-success", "fault-status", "fault-content", "original-domain")).map { "$token-$it.cache" })
            backup.write(Gson().toJson(saved).toByteArray())
            try {
                sources.forEach { cache(it.getString("name")).apply { check(!exists()); writeBytes(input(it)) } }
                block()
            } catch (error: Throwable) {
                report(JSONObject().put("case", "failed").put("type", error.javaClass.simpleName)
                    .put("at", error.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" }))
                throw error
            } finally { withContext(NonCancellable) { restore(saved) } }
        }
    }

    private fun input(source: JSONObject): ByteArray {
        val name = source.getString("file")
        check(name.matches(Regex("${Regex.escape(manifest.getString("token"))}-[a-z-]+\\.[a-z]+")))
        return ResourceAcceptanceInputs.shell("cat /sdcard/Download/$name").also {
            assertEquals(source.getInt("bytes"), it.size); assertEquals(source.getString("sha256"), sha(it))
        }
    }

    private fun definition(source: JSONObject, name: String) = JsonObject().apply {
        if (!cache(name).exists()) cache(name).writeBytes(input(source))
        addProperty("type", "http"); addProperty("url", source.getString("url")); addProperty("path", cache(name).path)
        addProperty("behavior", source.getString("behavior")); addProperty("format", source.getString("format"))
        addProperty("interval", 0); addProperty("proxy", "DIRECT")
    }

    private fun script(definitions: JsonObject, first: String) = """
        const main = config => {
            // 作者 long：测试通过正式脚本隔离本批资源，源1.yaml字节保持；只交换第一条RULE-SET以排除前项遮蔽。
            config['rule-providers'] = $definitions;
            config.rules = ['RULE-SET,${first},DIRECT', 'MATCH,DIRECT'];
            config.mode = 'rule';
            // 作者 long：原配置会嗅探TLS后清空字面IP再解析；隔离Provider验收时保留本次系统DNS地址，避免把重解析差异当资源故障。
            config.sniffer = {...config.sniffer, enable: false};
            ${if (proveInlineProxy) """
            // 作者 long：inline代理直接复用原1.yaml前两个节点，不构造示例凭据，也不向该节点发送业务连接。
            config['proxy-providers'] = {'provider-inline': {type: 'inline', payload: config.proxies.slice(0, 2)}};
            config['proxy-groups'].push({name: 'provider-inline-proof', type: 'select', use: ['provider-inline']});
            """.trimIndent() else ""}
            return config;
        };
    """.trimIndent()

    private suspend fun start(definitions: JsonObject, first: String) {
        val repo = entry.profileRepository(); val runtime = entry.runtimeManager()
        val row = Profile.custom(saved.id, saved.token).copy(type = ProfileType.FILE, orderIndex = saved.profileIds.size)
        entry.database().profiles().insert(row.toEntity())
        repo.profileFile(saved.id).apply { parentFile!!.mkdirs(); writeBytes(readAuthorizedProxyProfile()) }
        sourceSignature = signature(repo.profileFile(saved.id))
        scriptId = entry.scriptRepository().create(saved.token, script(definitions, first)).id
        repo.updateProfileScript(saved.id, scriptId)
        runtime.setVpnEnabled(false).getOrThrow(); runtime.selectProfile(saved.id, repo::selectProfile)
        runtime.startProxy().getOrThrow(); since = checkNotNull(runtime.runningSinceElapsedMillis.value)
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] }
        compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_profile_card"))
        compose.onNodeWithTag("dashboard_profile_card").performClick()
    }

    private suspend fun apply(definitions: JsonObject, first: String) {
        val before = entry.coreBridge().ipRouteStamp().epoch
        entry.scriptRepository().update(scriptId, saved.token, script(definitions, first))
        assertTrue(entry.runtimeManager().profileInputsApplied(saved.id))
        assertTrue(entry.coreBridge().ipRouteStamp().epoch > before)
        assertSession()
    }

    private suspend fun verifyPublicHit(name: String, behavior: String, format: String, vehicle: String) = withContext(Dispatchers.IO) {
        val host = manifest.getString("trafficTarget")
        check(host == "www.baidu.com")
        if (vehicle == "inline") assertFalse(entry.coreBridge().externalProviders().any { it.name == name })
        else {
            val provider = withTimeout(20_000) {
                while (true) {
                    entry.coreBridge().externalProviders().singleOrNull { it.name == name && it.count > 0 }?.let { return@withTimeout it }
                    delay(50)
                }
                @Suppress("UNREACHABLE_CODE") error("规则资源尚未加载")
            }
            assertEquals(if (vehicle == "http") "HTTP" else "File", provider.vehicleType)
        }
        val applied = JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject
        assertFalse(applied.getAsJsonObject("sniffer")["enable"].asBoolean)
        val proxyPort = effectiveSocksPort()
        // 作者 long：IP规则使用现场系统DNS得到的字面IPv4，独立于核心域名解析；TLS仍严格校验原站主机名。
        val destination = if (behavior == "ipcidr") InetSocketAddress(InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().first(), 443)
            else InetSocketAddress.createUnresolved(host, 443)
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort))).use { socket ->
            socket.connect(destination, 20_000); socket.soTimeout = 20_000
            // 作者 long：同一SOCKS监听器中的源端口和目标端口绑定本次socket；核心可省略目标IP，不能用可空元数据丢弃真实连接。
            // TLS仍校验原站主机名，后续必须同时满足HTTP正文、精确RULE-SET及Provider归因，避免把仅建连当成规则命中。
            val observing = async { withTimeout(20_000) {
                while (true) {
                    val found = entry.coreBridge().getConnections().singleOrNull { it.metadata.sourcePort == socket.localPort.toString() &&
                        it.metadata.destinationPort == "443" }
                    if (found != null && found.upload > 0 && found.download > 0) return@withTimeout found
                    delay(50)
                }
                @Suppress("UNREACHABLE_CODE") error("连接不可见")
            } }
            (javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory).createSocket(socket, host, 443, true).use { secure ->
                secure as javax.net.ssl.SSLSocket
                secure.sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                secure.startHandshake()
                secure.getOutputStream().apply { write("GET / HTTP/1.1\r\nHost: $host\r\nConnection: keep-alive\r\n\r\n".toByteArray()); flush() }
                val input = secure.getInputStream().buffered()
                fun line(): String = buildString { while (true) { val ch = input.read(); check(ch >= 0); if (ch == 10) break; if (ch != 13) append(ch.toChar()) } }
                val status = line(); assertTrue(status.startsWith("HTTP/1.1 200 ") || status.startsWith("HTTP/1.0 200 "))
                while (line().isNotEmpty()) Unit
                assertTrue("HTTP响应必须有正文", input.read() >= 0)
                val observed = observing.await()
                val connection = entry.coreBridge().getConnections().singleOrNull { it.id == observed.id } ?: observed
                report(JSONObject().put("case", "rule-observation").put("provider", name).put("vehicle", vehicle)
                    .put("rule", connection.rule).put("payload", connection.rulePayload)
                    .put("destinationIpSha256", sha(connection.metadata.destinationIp.toByteArray())))
                assertEquals("RuleSet", connection.rule); assertEquals(name, connection.rulePayload)
                assertTrue(connection.chains.contains("DIRECT"))
                report(JSONObject().put("case", "rule-hit").put("vehicle", vehicle).put("behavior", behavior).put("format", format)
                    .put("provider", name).put("rule", connection.rule).put("rulePayload", connection.rulePayload)
                    .put("connectionId", connection.id).put("sourcePort", socket.localPort).put("destinationPort", 443)
                    .put("targetSha256", sha(host.toByteArray())).put("status", 200).put("responseBodyPresent", true).put("hostnameVerifiedTls", true)
                    .put("snifferDisabledInTestProjection", true)
                    .put("destinationForm", if (behavior == "ipcidr") "system-DNS-literal-IPv4" else "unresolved-domain")
                    .put("upload", connection.upload).put("download", connection.download).put("epoch", entry.coreBridge().ipRouteStamp().epoch))
            }
        }
    }

    private fun effectiveSocksPort(): Int {
        // 作者 long：通用设置会覆盖源配置端口，必须读取已应用暂存配置；关闭的独立SOCKS端口不能遮蔽有效mixed端口。
        val root = JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject
        return listOf("socks-port", "mixed-port").mapNotNull { root[it]?.asInt?.takeIf { port -> port in 1..65535 } }.first()
    }

    private fun ready(count: Int) = compose.waitUntil(20_000) { model.providers.value.let {
        it.profileId == saved.id && !it.isLoading && it.error == null && it.providers?.size == count } }
    private fun click(tag: String) {
        compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun clickUpdate(name: String) = click("profile_provider_update_" + model.providers.value.providers!!.indexOfFirst { it.name == name })
    private fun awaitUpdate() = compose.waitUntil(120_000) { model.providers.value.let {
        it.updatingName == null && it.updatingNames.isEmpty() && it.updateMessage != null } }
    private fun assertSession() {
        assertEquals(CoreRuntimeStatus.RUNNING, entry.runtimeManager().state.value)
        assertEquals(since, entry.runtimeManager().runningSinceElapsedMillis.value)
        assertEquals(saved.id, entry.profileRepository().currentProfileId.value)
        assertEquals(sourceSignature, signature(entry.profileRepository().profileFile(saved.id)))
        assertEquals("bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898", sha(entry.profileRepository().profileFile(saved.id).readBytes()))
    }
    private fun capture(name: String) {
        prepareTouchVisualCapture(); compose.waitForIdle()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }

    private suspend fun restore(snapshot: Snapshot) {
        val runtime = entry.runtimeManager(); val repo = entry.profileRepository()
        val failures = mutableListOf<String>()
        suspend fun step(name: String, action: suspend () -> Unit) {
            try { action() } catch (error: Exception) { failures += "$name:${error.javaClass.simpleName}" }
        }
        // 作者 long：某一步恢复失败仍尝试其他独立状态；核心未停止时禁止回收仍可能被读取的配置和缓存。
        step("stop") { runtime.stop().getOrThrow() }
        if (runtime.state.value == CoreRuntimeStatus.STOPPED) {
            step("selection") { runtime.selectProfile(snapshot.selected, repo::selectProfile) }
            if (repo.currentProfileId.value == snapshot.selected) {
                step("profile") {
                    check(snapshot.id !in snapshot.profileIds)
                    entry.database().profiles().getById(snapshot.id)?.let {
                        check(it.label == snapshot.token); repo.deleteProfile(it.id)
                    }
                    // 作者 long：Room删除成功不代表正文已删除；仅回收本次独占ID且仍为原1.yaml的文件，失败则保留恢复备份。
                    repo.profileFile(snapshot.id).let { file ->
                        check(!file.exists() || sha(file.readBytes()) == "bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898" && file.delete())
                        check(!file.exists())
                    }
                }
                step("scripts") { entry.database().scripts().getAll().filter { it.id !in snapshot.scriptIds && it.label == snapshot.token }.forEach {
                    entry.scriptRepository().delete(it.id) } }
                step("caches") { snapshot.caches.forEach { name ->
                    check(name.startsWith(snapshot.token) && '/' !in name)
                    File(entry.paths().homeDir, name).let { check(!it.exists() || it.delete()) }
                } }
            }
        }
        step("vpn") { runtime.setVpnEnabled(snapshot.vpn).getOrThrow() }
        check(failures.isEmpty()) { "验收恢复未完成：${failures.joinToString()}" }
        val profiles = entry.database().profiles().getAll(); val scripts = entry.database().scripts().getAll()
        assertEquals(snapshot.profileIds, profiles.map { it.id }); assertEquals(snapshot.profileDigest, digest(profiles))
        assertEquals(snapshot.profileFiles, profiles.associate { it.id to signature(repo.profileFile(it.id)) })
        assertEquals(snapshot.scriptIds, scripts.map { it.id }); assertEquals(snapshot.scriptDigest, digest(scripts))
        assertEquals(snapshot.scriptFiles, scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) })
        assertEquals(snapshot.selected, repo.currentProfileId.value)
        backup.write(null); assertNull(backup.read())
        report(JSONObject().put("case", "restored").put("formalProfiles", profiles.size).put("formalScripts", scripts.size))
    }

    @Test fun restoreInterruptedProviderHitAcceptance(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("kclashProviderHitsAcceptance") == "true")
        val bytes = backup.read(); assumeTrue(bytes != null)
        val snapshot = Gson().fromJson(checkNotNull(bytes).decodeToString(), Snapshot::class.java)
        entry.profileRepository().withAutomaticUpdateBarrier { restore(snapshot) }
    }
}

/** 作者 long：仅注入HTTP服务端故障，Mihomo下载/解析/缓存和正式VM仍真实运行；恢复时返回存档的公开原字节。 */
private class FaultServer(private val body: ByteArray) : AutoCloseable {
    val modes = ConcurrentHashMap<String, Int>()
    val counts = ConcurrentHashMap<String, Int>()
    private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool()
    init {
        pool.submit { while (!listener.isClosed) {
            val socket = try { listener.accept() } catch (_: Exception) { break }
            pool.submit { socket.use {
                it.soTimeout = 5_000
                val reader = it.getInputStream().bufferedReader(); val request = reader.readLine().orEmpty()
                val name = request.substringAfter("GET /").substringBefore(' ')
                while (!reader.readLine().isNullOrEmpty()) Unit
                counts.merge(name, 1, Int::plus)
                val mode = modes[name] ?: 200
                val bytes = if (mode == 422) "missing-yaml-payload".toByteArray() else body
                val status = if (mode == 503) "503 Service Unavailable" else "200 OK"
                it.getOutputStream().apply { write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() }
            } }
        } }
    }
    fun url(name: String) = "http://127.0.0.1:${listener.localPort}/$name"
    override fun close() { listener.close(); pool.shutdownNow() }
}
