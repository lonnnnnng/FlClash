package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.readAuthorizedProxyProfile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.YamlConfigCodec
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** 作者 long：只用授权源和原1.yaml；脚本改变临时配置的运行投影，源正文与正式数据始终保留。 */
class ProxyProviderNetworkInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun authorizedHttpAndFileProxyProvidersKeepSourceAndRunningSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProxyProviderAcceptance"))
        assertPortraitDefaultFontConfiguration()
        val input = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kclash-proxy-provider-authorized.json").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        val url = org.json.JSONObject(input).getString("url")
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = e.profileRepository(); val manager = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val rows = e.database().profiles().getAll()
        if (rows.isNotEmpty()) compose.waitUntil(30_000) { repo.currentProfileId.value in rows.map { it.id } }
        val selected = repo.currentProfileId.value; val vpn = e.vpnSettingsStore().read()
        val files = rows.associate { it.id to signature(repo.profileFile(it.id)) }
        val scripts = e.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to signature(e.scriptRepository().scriptFile(it.id)) }
        val bytes = readAuthorizedProxyProfile()
        val token = "proxy-types-${System.nanoTime()}"
        val local = File(e.paths().homeDir, "$token-file.yaml")
        val remote = File(e.paths().homeDir, "$token-http.yaml")
        check(!local.exists() && !remote.exists())
        var temporary: Profile? = null; var scriptId: Long? = null; var stage = "prepare"
        try {
            local.writeBytes(bytes)
            temporary = repo.addFileProfile(token, bytes)
            val id = temporary.id
            val script = e.scriptRepository().create(token, """
                const main = config => {
                    config['proxy-providers'] = {
                        'acceptance-http': {type: 'http', url: ${Gson().toJson(url)}, path: ${Gson().toJson(remote.path)}, interval: 0, proxy: 'DIRECT',
                            header: {'User-Agent': ['clash-verge/Platform/android']}},
                        'acceptance-file': {type: 'file', path: ${Gson().toJson(local.path)}}
                    };
                    // 作者 long：资源下载显式直连，避免首次下载依赖尚未加载的代理；接入原有选择组并保留原节点和默认选择。
                    const group = config['proxy-groups'].find(value => value.type === 'select');
                    group.use = (group.use || []).concat(['acceptance-http', 'acceptance-file']);
                    return config;
                };
            """.trimIndent())
            scriptId = script.id; repo.updateProfileScript(id, script.id)
            manager.setVpnEnabled(false).getOrThrow()
            stage = "load-real-providers"
            manager.selectProfile(id, repo::selectProfile)
            val sourceSignature = signature(repo.profileFile(id))
            fun verify(requireHttpLoaded: Boolean = true) = runBlocking {
                val providers = manager.profileProviders(id)
                val http = providers.single { it.name == "acceptance-http" }
                val file = providers.single { it.name == "acceptance-file" }
                assertEquals("Proxy", http.type); assertEquals("HTTP", http.vehicleType); assertTrue(http.canUpdate)
                assertEquals("Proxy", file.type); assertEquals("File", file.vehicleType); assertFalse(file.canUpdate)
                instrumentation.sendStatus(2, Bundle().apply { putString("kclash_proxy_provider_counts",
                    "phase=$stage;http=${http.count};file=${file.count};http-cache=${remote.exists()};file-cache=${local.exists()}") })
                if (requireHttpLoaded) assertTrue("HTTP代理资源未加载", http.count > 0)
                assertEquals("本地代理资源条目数", 7L, file.count)
                assertEquals(92, providers.size)
                for (provider in listOf(http, file).filter { it.count > 0 }) {
                    val document = manager.readProfileProvider(id, provider.name).document
                    assertTrue(document.editable)
                    val body = JsonParser.parseString(YamlConfigCodec.toJson(document.content)).asJsonObject
                    assertEquals(provider.count, body.getAsJsonArray("proxies").size().toLong())
                }
                assertTrue(signature(repo.profileFile(id)) == sourceSignature)
                assertArrayEquals(bytes, local.readBytes())
                http
            }
            // 作者 long：首次网络初始化可能失败；先记录零条目，再走正式更新入口，只有真实下载、解析及回读成功才记为通过。
            verify(requireHttpLoaded = false)
            // 作者 long：文件资源没有下载语义，核心拒绝在线更新且页面不显示更新按钮，避免把本地读误记成实网。
            assertTrue(runCatching { manager.updateProfileProvider(id, "acceptance-file") }.isFailure)
            lateinit var model: ProfileStatisticsViewModel
            fun bind() { compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] } }
            bind()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_profile_card"))
            compose.onNodeWithTag("dashboard_profile_card").performClick()
            fun ready() = model.providers.value.let { it.profileId == id && !it.isLoading && it.error == null && it.providers?.size == 92 }
            fun idle() = model.providers.value.let { it.updatingName == null && it.updatingNames.isEmpty() }
            compose.waitUntil(20_000) { ready() }
            for (running in listOf(false, true)) {
                stage = if (running) "running-ui-download" else "stopped-ui-download"
                if (running) manager.startProxy().getOrThrow()
                val since = manager.runningSinceElapsedMillis.value
                compose.waitUntil(20_000) { ready() && idle() }
                val before = verify(requireHttpLoaded = running)
                delay(1_100)
                val index = model.providers.value.providers!!.indexOfFirst { it.name == "acceptance-http" }
                val tag = "profile_provider_update_$index"
                compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performClick()
                compose.waitUntil(45_000) { idle() && model.providers.value.updateMessage != null }
                instrumentation.sendStatus(2, Bundle().apply { putString("kclash_proxy_provider_ui_result",
                    "phase=$stage;failed=${model.providers.value.updateFailed};message=${model.providers.value.updateMessage}") })
                assertFalse(model.providers.value.updateFailed)
                val after = verify()
                assertTrue((after.updatedAtMillis ?: 0) > (before.updatedAtMillis ?: 0))
                assertEquals(since, manager.runningSinceElapsedMillis.value)
                assertEquals(if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED, manager.state.value)
                instrumentation.sendStatus(2, Bundle().apply { putString("kclash_proxy_provider_result",
                    "phase=$stage;type=Proxy;vehicle=HTTP;count=${after.count};before=${before.updatedAtMillis};after=${after.updatedAtMillis};file-count=7;file-update-rejected=true") })
            }
            stage = "recreate-and-poll"
            val since = manager.runningSinceElapsedMillis.value
            compose.activityRule.scenario.recreate(); bind()
            compose.waitUntil(20_000) { ready() && idle() }
            repeat(64) { assertTrue(ready() && idle()); delay(250) }
            verify(); assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertTrue(manager.profileInputsApplied(id))
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_proxy_provider_summary", "http=true;file=true;recreated=true;polling-ms=16000;session=true;source=true") })
        } catch (failure: Throwable) {
            val at = failure.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" }
            throw AssertionError("Proxy Provider阶段=$stage 类型=${failure.javaClass.simpleName} 位置=$at")
        } finally {
            manager.stop().getOrThrow()
            if (selected == null) repo.selectProfile(null) else manager.selectProfile(selected, repo::selectProfile)
            temporary?.let { profile ->
                val row = e.database().profiles().getById(profile.id)
                check(row == null || row.label == token && rows.none { it.id == row.id })
                if (row != null) repo.deleteProfile(profile.id)
            }
            scriptId?.let { id ->
                val row = e.database().scripts().getById(id); check(row == null || row.label == token)
                if (row != null) e.scriptRepository().delete(id)
            }
            listOf(local, remote).forEach { file -> check(file.parentFile == e.paths().homeDir && file.name.startsWith(token)); check(!file.exists() || file.delete()) }
            if (selected == null) repo.selectProfile(null)
            manager.setVpnEnabled(vpn).getOrThrow()
            assertTrue(rows == e.database().profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
            assertTrue(files == rows.associate { it.id to signature(repo.profileFile(it.id)) })
            assertTrue(scripts == e.database().scripts().getAll())
            assertTrue(scriptFiles == scripts.associate { it.id to signature(e.scriptRepository().scriptFile(it.id)) })
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_proxy_provider_preserved", "profiles=${rows.size};files=true;scripts=true;selection=true;vpn=true;stopped=true") })
        }
    }

    @Test fun publicHttpRuleYamlTextAndClassicalUpdateFromUiAndKeepRunningSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashProxyProviderAcceptance"))
        assertPortraitDefaultFontConfiguration()
        val catalog = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kclash-rule-providers-authorized.json").use {
            JsonParser.parseReader(ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader()).asJsonArray
        }
        assertEquals(6, catalog.size())
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = e.profileRepository(); val manager = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val rows = e.database().profiles().getAll()
        if (rows.isNotEmpty()) compose.waitUntil(30_000) { repo.currentProfileId.value in rows.map { it.id } }
        val selected = repo.currentProfileId.value; val vpn = e.vpnSettingsStore().read()
        val files = rows.associate { it.id to signature(repo.profileFile(it.id)) }
        val scripts = e.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to signature(e.scriptRepository().scriptFile(it.id)) }
        val bytes = readAuthorizedProxyProfile(); val token = "rule-types-${System.nanoTime()}"
        val targets = catalog.map { it.asJsonObject }
        val definitions = com.google.gson.JsonObject()
        val caches = targets.map { row ->
            val file = File(e.paths().homeDir, "$token-${row.get("name").asString}.cache")
            check(!file.exists())
            definitions.add(row.get("name").asString, com.google.gson.JsonObject().apply {
                addProperty("type", "http"); addProperty("behavior", row.get("behavior").asString)
                addProperty("format", row.get("format").asString); addProperty("url", row.get("url").asString)
                addProperty("path", file.path); addProperty("interval", 0)
                addProperty("proxy", "DIRECT")
            }); file
        }
        var temporary: Profile? = null; var scriptId: Long? = null; var stage = "prepare"
        try {
            temporary = repo.addFileProfile(token, bytes); val id = temporary.id
            // 作者 long：用户已授权寻找公开规则源；只向临时运行投影追加明确的六个资源，原1.yaml、既有90项及正式Profile不改写。
            val script = e.scriptRepository().create(token, """
                const main = config => {
                    const providers = $definitions;
                    Object.assign(config['rule-providers'], providers);
                    config.rules = Object.keys(providers).map(name => 'RULE-SET,' + name + ',DIRECT').concat(config.rules);
                    return config;
                };
            """.trimIndent())
            scriptId = script.id; repo.updateProfileScript(id, script.id)
            manager.setVpnEnabled(false).getOrThrow(); stage = "initial-load"
            manager.selectProfile(id, repo::selectProfile)
            val original = signature(repo.profileFile(id))
            lateinit var model: ProfileStatisticsViewModel
            fun bind() { compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] } }
            bind()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_profile_card"))
            compose.onNodeWithTag("dashboard_profile_card").performClick()
            fun ready() = model.providers.value.let { it.profileId == id && !it.isLoading && it.error == null && it.providers?.size == 96 }
            fun idle() = model.providers.value.let { it.updatingName == null && it.updatingNames.isEmpty() }
            fun awaitReady() {
                repeat(2) { attempt ->
                    try { compose.waitUntil(20_000) { ready() || model.providers.value.error != null } }
                    finally {
                        val state = model.providers.value
                        instrumentation.sendStatus(2, Bundle().apply { putString("kclash_rule_provider_list",
                            "phase=$stage;attempt=$attempt;profile=${state.profileId == id};count=${state.providers?.size};loading=${state.isLoading};error=${state.error}") })
                    }
                    if (ready()) return
                    check(attempt == 0) { "Providers列表重试后仍失败" }
                    // 作者 long：核心初始化通知可能与首次读取交错；用正式页面的重试恢复，保留失败证据而非直接修改VM状态。
                    compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_providers_retry"))
                    compose.onNodeWithTag("profile_providers_retry").performClick()
                }
            }
            for (running in listOf(false, true)) {
                if (running) manager.startProxy().getOrThrow()
                val since = manager.runningSinceElapsedMillis.value
                for (target in targets) {
                    val name = target.get("name").asString; stage = "$running-$name"
                    awaitReady()
                    compose.waitUntil(20_000) { idle() }
                    stage = "$running-$name-before-list"
                    val before = manager.profileProviders(id).single { it.name == name }
                    assertEquals("Rule", before.type); assertEquals("HTTP", before.vehicleType)
                    val index = model.providers.value.providers!!.indexOfFirst { it.name == name }
                    val tag = "profile_provider_update_$index"
                    compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag(tag))
                    stage = "$running-$name-ui-update"
                    compose.onNodeWithTag(tag).performClick()
                    compose.waitUntil(45_000) { idle() && model.providers.value.updateMessage != null }
                    instrumentation.sendStatus(2, Bundle().apply { putString("kclash_rule_provider_ui_result",
                        "phase=$stage;failed=${model.providers.value.updateFailed};message=${model.providers.value.updateMessage}") })
                    assertFalse("公开规则下载失败：$name", model.providers.value.updateFailed)
                    stage = "$running-$name-after-list"
                    val after = manager.profileProviders(id).single { it.name == name }
                    assertEquals(target.get("entries").asLong, after.count)
                    assertTrue((after.updatedAtMillis ?: 0) > (before.updatedAtMillis ?: 0))
                    stage = "$running-$name-read-body"
                    val body = manager.readProfileProvider(id, name).document
                    assertTrue(body.editable)
                    // 作者 long：公开镜像必须保持原始规则字节；回读哈希能同时证明真实下载、正确资源绑定与正文格式未被转换。
                    val digest = MessageDigest.getInstance("SHA-256").digest(body.content.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    assertEquals(target.get("sha256").asString, digest)
                    if (target.get("format").asString == "yaml") {
                        val parsed = JsonParser.parseString(YamlConfigCodec.toJson(body.content)).asJsonObject
                        assertEquals(target.get("entries").asInt, parsed.getAsJsonArray("payload").size())
                    } else assertFalse(body.content.trimStart().startsWith("payload:"))
                    assertEquals(since, manager.runningSinceElapsedMillis.value)
                    instrumentation.sendStatus(2, Bundle().apply { putString("kclash_rule_provider_result",
                        "running=$running;behavior=${target.get("behavior").asString};format=${target.get("format").asString};count=${after.count};before=${before.updatedAtMillis};after=${after.updatedAtMillis};readback=true") })
                }
            }
            val since = manager.runningSinceElapsedMillis.value; stage = "recreate-and-poll"
            compose.activityRule.scenario.recreate(); bind()
            compose.waitUntil(20_000) { ready() && idle() }
            repeat(64) { assertTrue(ready() && idle()); delay(250) }
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertTrue(manager.profileInputsApplied(id)); assertTrue(original == signature(repo.profileFile(id)))
            assertArrayEquals(bytes, repo.profileFile(id).readBytes())
            val all = manager.profileProviders(id)
            for (behavior in listOf("domain", "ipcidr", "classical")) {
                assertEquals(all.single { it.name == "acceptance-$behavior-yaml" }.count, all.single { it.name == "acceptance-$behavior-text" }.count)
            }
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_rule_provider_summary", "providers=6;updates=12;recreated=true;polling-ms=16000;session=true;source=true") })
        } catch (failure: Throwable) {
            val at = failure.stackTrace.firstOrNull { it.className.startsWith("com.lonnnnnng.") }?.let { "${it.fileName}:${it.lineNumber}" }
            throw AssertionError("公开Rule Provider阶段=$stage 类型=${failure.javaClass.simpleName} 位置=$at")
        } finally {
            manager.stop().getOrThrow()
            if (selected == null) repo.selectProfile(null) else manager.selectProfile(selected, repo::selectProfile)
            temporary?.let { profile ->
                val row = e.database().profiles().getById(profile.id)
                check(row == null || row.label == token && rows.none { it.id == row.id })
                if (row != null) repo.deleteProfile(profile.id)
            }
            scriptId?.let { id -> val row = e.database().scripts().getById(id); check(row == null || row.label == token)
                if (row != null) e.scriptRepository().delete(id) }
            caches.forEach { check(it.parentFile == e.paths().homeDir && it.name.startsWith(token)); check(!it.exists() || it.delete()) }
            if (selected == null) repo.selectProfile(null)
            manager.setVpnEnabled(vpn).getOrThrow()
            assertTrue(rows == e.database().profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
            assertTrue(files == rows.associate { it.id to signature(repo.profileFile(it.id)) })
            assertTrue(scripts == e.database().scripts().getAll()); assertTrue(scriptFiles == scripts.associate { it.id to signature(e.scriptRepository().scriptFile(it.id)) })
            instrumentation.sendStatus(2, Bundle().apply { putString("kclash_rule_provider_preserved", "profiles=${rows.size};files=true;scripts=true;selection=true;vpn=true;stopped=true") })
        }
    }

    private fun signature(file: File) = Triple(file.length(), file.lastModified(), MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList())
}
