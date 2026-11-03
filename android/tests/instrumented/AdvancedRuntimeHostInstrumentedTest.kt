package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：运行态验收只使用原1.yaml副本和本轮ID，真实仓库与核心共用生产锁，结束核对原行/正文/时间戳。 */
class AdvancedRuntimeHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun runningBoundScriptSavesApplyRecoverAndDeleteWithoutRestartingSession() = runBlocking {
        fixture { f ->
            val e = f.entry; val repo = e.scriptRepository(); val manager = e.runtimeManager()
            val script = repo.create("advanced-script-${f.ids[0]}", "const main = config => config;")
            f.scripts += script.id
            e.profileRepository().updateProfileScript(f.ids[0], script.id)
            manager.applyCurrentProfile(f.ids[0]); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_advanced"))
            compose.onNodeWithTag("tools_item_advanced").performClick()
            compose.onNodeWithTag("advanced_item_scripts").performClick()
            lateinit var model: ScriptsViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ScriptsViewModel::class.java] }
            fun save(text: String) {
                val latest = runBlocking { repo.readDocument(script.id).script }
                compose.runOnIdle { model.openEditor(latest) }
                compose.waitUntil(15_000) { !model.editorState.value.isLoading }
                compose.runOnIdle { model.save(latest.label, text) }
                compose.waitUntil(30_000) { !model.editorState.value.isOpen }
            }
            save("const main = config => { config.rules.unshift('DOMAIN,script-runtime.invalid,DIRECT'); return config; };")
            assertTrue(manager.profileInputsApplied(f.ids[0]))
            assertTrue(e.paths().configFile.readText().contains("DOMAIN,script-runtime.invalid,DIRECT"))
            val applied = e.paths().configFile.readBytes()
            save("const main = config => { throw new Error('intentional acceptance failure'); };")
            compose.waitUntil(10_000) { model.state.value.applyError != null }
            assertFalse(manager.profileInputsApplied(f.ids[0]))
            assertArrayEquals(applied, e.paths().configFile.readBytes())
            val savedTime = repo.scriptFile(script.id).lastModified()
            compose.onNodeWithTag("scripts_retry_apply").performClick()
            compose.waitUntil(30_000) { !model.state.value.isBusy }
            assertNotNull(model.state.value.applyError)
            assertEquals(savedTime, repo.scriptFile(script.id).lastModified())
            capture("advanced_script_apply_failure")
            save("const main = config => { config.rules.unshift('DOMAIN,script-fixed.invalid,DIRECT'); return config; };")
            compose.waitUntil(15_000) { model.state.value.applyError == null }
            assertTrue(manager.profileInputsApplied(f.ids[0]))
            assertTrue(e.paths().configFile.readText().contains("script-fixed.invalid"))
            val latest = repo.readDocument(script.id).script
            compose.runOnIdle { model.delete(latest) }
            compose.waitUntil(30_000) { !model.state.value.isBusy }
            assertNull(e.database().scripts().getById(script.id))
            assertNull(e.database().profiles().getById(f.ids[0])!!.scriptId)
            assertFalse(e.paths().configFile.readText().contains("script-fixed.invalid"))
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        }
    }

    @Test fun runningGlobalDnsAndRulesApplyAndCustomKeepsOwnDns() = runBlocking {
        fixture { f ->
            val e = f.entry; val manager = e.runtimeManager(); val repo = e.profileRepository()
            manager.startProxy().getOrThrow(); val since = manager.runningSinceElapsedMillis.value
            val original = json(e.paths().configFile).getAsJsonObject("dns").get("ipv6")?.asBoolean ?: false
            val before = e.globalDnsSettingsStore().read()
            manager.updateGlobalDns(before, before.copy(ipv6 = !original)).getOrThrow()
            assertEquals(!original, json(e.paths().configFile).getAsJsonObject("dns").get("ipv6").asBoolean)
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_advanced"))
            compose.onNodeWithTag("tools_item_advanced").performClick()
            compose.onNodeWithTag("advanced_item_rules").performClick()
            lateinit var model: ProfilesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
            compose.waitUntil(15_000) { model.globalRules.state.value.loaded }
            val rules = repo.globalExtensionRules() + ExtensionRule("advanced-${f.ids[0]}", "DOMAIN,global-runtime.invalid,DIRECT")
            compose.runOnIdle { model.globalRules.save(rules) }
            compose.waitUntil(30_000) { !model.globalRules.state.value.saving && !model.globalRules.state.value.applying }
            assertNull(model.globalRules.state.value.error)
            assertTrue(e.paths().configFile.readText().contains("global-runtime.invalid"))
            assertTrue(manager.profileInputsApplied(f.ids[0]))
            capture("advanced_global_rules_running")
            manager.selectProfile(f.ids[1], repo::selectProfile)
            val customDns = json(repo.profileFile(f.ids[1])).getAsJsonObject("dns").get("ipv6")?.asBoolean ?: false
            assertEquals(customDns, json(e.paths().configFile).getAsJsonObject("dns").get("ipv6")?.asBoolean ?: false)
            assertFalse(e.paths().configFile.readText().contains("global-runtime.invalid"))
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        }
    }

    @Test fun runningProviderOptionsMoreAndSideLoadReachCoreWithoutChangingSource() = runBlocking {
        fixture { f ->
            val e = f.entry; val manager = e.runtimeManager(); val repo = e.profileRepository()
            val id = f.ids[1]
            val cache = File(e.paths().homeDir, "advanced-provider-$id.yaml")
            check(!cache.exists()); cache.writeBytes(f.bytes); f.extraFiles += cache
            val script = e.scriptRepository().create("advanced-provider-$id", "const main = config => { config['proxy-providers'] = {'runtime-provider': {type: 'file', path: ${Gson().toJson(cache.path)}}}; return config; };")
            f.scripts += script.id; repo.updateProfileScript(id, script.id)
            manager.selectProfile(id, repo::selectProfile); manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            val sourceTime = repo.profileFile(id).lastModified()
            lateinit var model: ProfilesViewModel
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
            val profile = requireNotNull(repo.extensionProfile(id))
            compose.runOnIdle { model.openProvidersEditor(profile) }
            compose.waitUntil(15_000) { !model.providersEditorState.value.isLoading }
            val state = model.providersEditorState.value
            compose.runOnIdle { model.saveProvidersInPlace(state.proxyProviders, state.ruleProviders,
                mapOf("runtime-provider" to ProxyProviderOverride(interval = 601, filter = ".*"))) }
            compose.waitUntil(30_000) { !model.providersEditorState.value.isSaving }
            assertNull(model.providersEditorState.value.error)
            assertEquals(601, json(e.paths().configFile).getAsJsonObject("proxy-providers").getAsJsonObject("runtime-provider").getAsJsonObject("health-check").get("interval").asInt)
            val updated = requireNotNull(repo.extensionProfile(id))
            compose.runOnIdle { model.closeProvidersEditor(); model.openMoreEditor(updated) }
            val more = model.moreEditorState.value
            compose.runOnIdle { model.saveMoreEditor(more.ntp.copy(enable = false, server = "ntp-runtime.invalid"), more.sniffer) }
            compose.waitUntil(30_000) { !model.moreEditorState.value.isSaving }
            assertNull(model.moreEditorState.value.error)
            assertEquals("ntp-runtime.invalid", json(e.paths().configFile).getAsJsonObject("ntp").get("server").asString)
            val document = manager.readProfileProvider(id, "runtime-provider")
            manager.saveProfileProvider(document, f.bytes.toString(Charsets.UTF_8))
            val readback = manager.readProfileProvider(id, "runtime-provider")
            assertEquals(f.bytes.toString(Charsets.UTF_8), readback.document.content)
            assertTrue(manager.profileInputsApplied(id)); assertEquals(sourceTime, repo.profileFile(id).lastModified())
            assertArrayEquals(f.bytes, repo.profileFile(id).readBytes())
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            manager.selectProfile(f.ids[0], repo::selectProfile)
            assertTrue(runCatching { manager.saveProfileProvider(readback, f.bytes.toString(Charsets.UTF_8)) }.isFailure)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            compose.runOnIdle { model.closeMoreEditor() }
        }
    }

    private class Fixture(val entry: ConnectionAcceptanceEntryPoint, val ids: List<Long>, val bytes: ByteArray) {
        val scripts = mutableListOf<Long>()
        val extraFiles = mutableListOf<File>()
    }

    @Test fun runningAuthorizedProviderBatchUpdatesOnlyExistingResourcesAndKeepsSession() = runBlocking {
        fixture { f ->
            val manager = f.entry.runtimeManager(); val id = f.ids[0]
            manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            // 作者 long：目标仅来自原1.yaml已经交给核心的HTTP资源，不创建测试URL，也不调用全局资源同步。
            val targets = manager.profileProviders(id).filter { it.canUpdate }.take(2)
            assertEquals(2, targets.size)
            val results = manager.updateProfileProviders(id, targets.map { it.name })
            assertEquals(targets.map { it.name }.toSet(), results.map { it.name }.toSet())
            assertTrue("原配置HTTP资源批量更新必须全部成功", results.all { it.code.isEmpty() })
            val after = manager.profileProviders(id).associateBy { it.name }
            targets.forEach { old ->
                val updated = requireNotNull(after[old.name])
                assertNotNull(updated.updatedAtMillis)
                assertTrue(updated.updatedAtMillis!! >= (old.updatedAtMillis ?: 0))
                assertTrue(updated.count > 0)
            }
            assertTrue(manager.profileInputsApplied(id))
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("kclashAdvancedRuntimeAcceptance"))
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = e.runtimeManager(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val rows = e.database().profiles().getAll(); val selected = requireNotNull(repo.currentProfileId.value)
        val originals = rows.associate { it.id to fileSignature(repo.profileFile(it.id)) }
        val scripts = e.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to fileSignature(e.scriptRepository().scriptFile(it.id)) }
        val dns = e.globalDnsSettingsStore().read(); val rules = repo.globalExtensionRules(); val vpn = e.vpnSettingsStore().read()
        val ids = List(2) { System.nanoTime().and(Long.MAX_VALUE) }
        val f = Fixture(e, ids, readAuthorizedProxyProfile())
        try {
            ids.forEachIndexed { index, id ->
                e.database().profiles().insert(Profile.custom(id, "advanced-$id").copy(type = if (index == 0) ProfileType.FILE else ProfileType.CUSTOM, orderIndex = rows.size + index).toEntity())
                repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(f.bytes) }
            }
            manager.setVpnEnabled(false).getOrThrow(); manager.selectProfile(ids[0], repo::selectProfile)
            block(f)
        } finally {
            manager.stop().getOrThrow()
            manager.withExtensionWrite(null) { repo.compareAndSaveGlobalExtensionRules(repo.globalExtensionRules(), rules) }
            manager.updateGlobalDns(e.globalDnsSettingsStore().read(), dns).getOrThrow()
            manager.selectProfile(selected, repo::selectProfile)
            ids.forEach { id ->
                val row = e.database().profiles().getById(id)
                check(row == null || row.label == "advanced-$id")
                if (row != null) repo.deleteProfile(id)
            }
            f.scripts.forEach { id ->
                val row = e.database().scripts().getById(id)
                check(row == null || row.label.startsWith("advanced-"))
                if (row != null) e.scriptRepository().delete(id)
            }
            f.extraFiles.forEach { file -> check(file.parentFile == e.paths().homeDir && file.name.startsWith("advanced-provider-")); check(!file.exists() || file.delete()) }
            manager.setVpnEnabled(vpn).getOrThrow()
            assertEquals(rows, e.database().profiles().getAll()); assertEquals(selected, repo.currentProfileId.value)
            assertEquals(originals, rows.associate { it.id to fileSignature(repo.profileFile(it.id)) })
            assertEquals(scripts, e.database().scripts().getAll())
            assertEquals(scriptFiles, scripts.associate { it.id to fileSignature(e.scriptRepository().scriptFile(it.id)) })
        }
    }

    private fun json(file: File) = JsonParser.parseString(YamlConfigCodec.toJson(file.readText())).asJsonObject
    private fun fileSignature(file: File) = file.lastModified() to java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
