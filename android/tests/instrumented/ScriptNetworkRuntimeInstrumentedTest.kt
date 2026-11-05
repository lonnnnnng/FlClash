package com.lonnnnnng.flclash.ui

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.YamlConfigCodec
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：不创建Activity，先直接回读核心代际和组，避免页面轮询顺带加载而掩盖脚本更新未应用。 */
class ScriptNetworkRuntimeInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val entry get() = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
    private val backup get() = EncryptedDraftFile(context, "resource-script-acceptance.v1", "com.lonnnnnng.flclash.acceptance.resource-script")
    private data class Snapshot(val profileId: Long, val token: String, val selected: Long, val vpn: Boolean,
        val profileIds: List<Long>, val profileDigest: String, val profileFiles: Map<Long, String>,
        val scriptIds: List<Long>, val scriptDigest: String, val scriptFiles: Map<Long, String>)

    @Test fun publicRemoteScriptImportsChangesSourceSyncsAndAppliesToRunningCore(): Unit = runBlocking {
        val manifest = ResourceAcceptanceInputs.manifest()
        val e = entry; val profiles = e.profileRepository(); val scripts = e.scriptRepository(); val runtime = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        assertNull("先恢复上次中断验收", backup.read())
        val owners = ViewModelStore(); var modelJob: Job? = null; var collector: Job? = null
        var saved: Snapshot? = null
        // 作者 long：整个验收挡住后台订阅提交，正式行不需要临时关闭自动更新，也不会在保护快照期间被到期任务改写。
        profiles.withAutomaticUpdateBarrier {
            try {
                val before = e.database().profiles().getAll(); val beforeScripts = e.database().scripts().getAll()
                val selected = withTimeout(20_000) { profiles.currentProfileId.first { id -> before.any { it.id == id } } }!!
                val id = System.nanoTime().and(Long.MAX_VALUE)
                val snapshot = Snapshot(id, manifest.getString("token") + "-network", selected, e.vpnSettingsStore().read(),
                    before.map { it.id }, digest(before), before.associate { it.id to ResourceAcceptanceInputs.signature(profiles.profileFile(it.id)) },
                    beforeScripts.map { it.id }, digest(beforeScripts), beforeScripts.associate { it.id to ResourceAcceptanceInputs.signature(scripts.scriptFile(it.id)) })
                backup.write(Gson().toJson(snapshot).toByteArray()); saved = snapshot
                lateinit var model: ScriptsViewModel
                withContext(Dispatchers.Main) {
                    model = ScriptsViewModel(scripts, runtime); owners.put("network", model)
                    modelJob = model.viewModelScope.coroutineContext[Job]
                }
                collector = launch(start = CoroutineStart.UNDISPATCHED) { model.state.collect() }
                val sources = manifest.getJSONArray("scriptSources")
                val source0 = sources.getJSONObject(0); val source1 = sources.getJSONObject(1)
                check(source0.getString("url") != source1.getString("url"))
                suspend fun action(block: () -> Unit) {
                    val completed = model.state.value.completed
                    withContext(Dispatchers.Main) { block() }
                    withTimeout(120_000) { model.state.first { !it.isBusy && (it.completed > completed || it.error != null || it.applyError != null) } }
                    val result = model.state.value
                    assertNull("下载/保存必须成功", result.error); assertNull("保存完成不能代替应用成功", result.applyError)
                    assertEquals(completed + 1, result.completed)
                }
                action { model.openRemote(); model.submitRemote(snapshot.token, source0.getString("url")) }
                var script = e.database().scripts().getAll().single { it.id !in snapshot.scriptIds }.toDomain()
                assertEquals(snapshot.token, script.label); assertEquals(source0.getString("url"), script.url)
                val file = scripts.scriptFile(script.id)
                assertEquals(source0.getString("sha256"), ResourceAcceptanceInputs.sha(file.readBytes()))
                assertEquals(source0.getInt("bytes").toLong(), file.length())
                val profile = Profile.custom(id, snapshot.token).copy(type = ProfileType.FILE, orderIndex = before.size)
                e.database().profiles().insert(profile.toEntity())
                profiles.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(readAuthorizedProxyProfile()) }
                val sourceSignature = ResourceAcceptanceInputs.signature(profiles.profileFile(id))
                profiles.updateProfileScript(id, script.id)
                runtime.setVpnEnabled(false).getOrThrow()
                runtime.selectProfile(id, profiles::selectProfile); runtime.startProxy().getOrThrow()
                val selectionGroup = withTimeout(10_000) { e.coreBridge().getProxyGroups() }.groups.first { it.isSelector && "DIRECT" in it.all }.name
                runtime.changeProxy(id, selectionGroup, "DIRECT").getOrThrow()
                val since = requireNotNull(runtime.runningSinceElapsedMillis.value)
                fun staged() = JsonParser.parseString(YamlConfigCodec.toJson(e.paths().configFile.readText())).asJsonObject
                suspend fun readCore(phase: String, previousEpoch: Long?): Long {
                    assertTrue(runtime.profileInputsApplied(id))
                    val epoch = withTimeout(10_000) { e.coreBridge().ipRouteStamp() }.epoch
                    if (previousEpoch != null) assertTrue("脚本文件版本前进必须触发真实核心重载", epoch > previousEpoch)
                    val actual = withTimeout(10_000) { e.coreBridge().getProxyGroups() }
                    val groups = actual.groups
                    assertTrue("新增脚本节点必须进入真实核心", actual.proxyTypes.containsKey("CustomProxy"))
                    assertEquals("DIRECT", groups.single { it.name == selectionGroup }.now)
                    val loaded = staged()
                    assertEquals("DOMAIN,custom.example.com,CustomProxy", loaded.getAsJsonArray("rules").first().asString)
                    assertEquals(1, loaded.getAsJsonArray("proxies").count { it.asJsonObject.get("name").asString == "CustomProxy" })
                    assertEquals(sourceSignature, ResourceAcceptanceInputs.signature(profiles.profileFile(id)))
                    assertEquals(id, profiles.currentProfileId.value); assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value)
                    assertEquals(since, runtime.runningSinceElapsedMillis.value)
                    ResourceAcceptanceInputs.report(JSONObject().put("case", "script-network").put("phase", phase)
                        .put("epoch", epoch).put("previousEpoch", previousEpoch).put("groupCount", groups.size)
                        .put("scriptSha256", ResourceAcceptanceInputs.sha(file.readBytes())).put("scriptMtime", file.lastModified())
                        .put("sessionPreserved", true).put("sourceBodyPreserved", true).put("selectionPreserved", true))
                    return epoch
                }
                var epoch = readCore("download-bind-start", null)
                var modified = file.lastModified(); val first = script
                action { model.openRemote(script); model.submitRemote("不得改名", source1.getString("url")) }
                script = e.database().scripts().getById(script.id)!!.toDomain()
                assertEquals(first.id, script.id); assertEquals(first.label, script.label); assertEquals(first.orderIndex, script.orderIndex)
                assertEquals(source1.getString("url"), script.url); assertTrue(script.lastUpdateAt!! > first.lastUpdateAt!!)
                assertTrue(file.lastModified() > modified); assertEquals(source1.getString("sha256"), ResourceAcceptanceInputs.sha(file.readBytes()))
                epoch = readCore("change-source", epoch)
                modified = file.lastModified(); val beforeSync = script
                action { model.sync(script) }
                script = e.database().scripts().getById(script.id)!!.toDomain()
                assertEquals(beforeSync.copy(lastUpdateAt = script.lastUpdateAt), script)
                assertTrue(script.lastUpdateAt!! > beforeSync.lastUpdateAt!!); assertTrue(file.lastModified() > modified)
                assertEquals(source1.getString("sha256"), ResourceAcceptanceInputs.sha(file.readBytes()))
                readCore("sync", epoch)
                ResourceAcceptanceInputs.report(JSONObject().put("case", "script-network").put("phase", "passed")
                    .put("downloadedBytes", file.length()).put("sameRemoteBody", source0.getString("sha256") == source1.getString("sha256")))
            } finally {
                withContext(NonCancellable) {
                    collector?.cancelAndJoin()
                    withContext(Dispatchers.Main) { owners.clear() }
                    // 作者 long：任务仍可能在不可取消的文件提交中；超时保留加密恢复记录，不能抢先删除仍被写入的脚本。
                    withTimeout(45_000) { modelJob?.join() }
                    saved?.let { restore(it) }
                }
            }
        }
    }

    @Test fun restoreInterruptedScriptNetworkAcceptance(): Unit = runBlocking {
        ResourceAcceptanceInputs.manifest()
        val saved = checkNotNull(backup.read()) { "没有待恢复脚本验收" }
        entry.profileRepository().withAutomaticUpdateBarrier { restore(Gson().fromJson(saved.decodeToString(), Snapshot::class.java)) }
    }
    private fun digest(value: Any) = ResourceAcceptanceInputs.sha(Gson().toJson(value).toByteArray())
    private suspend fun restore(saved: Snapshot) {
        val e = entry; val profiles = e.profileRepository(); val scripts = e.scriptRepository(); val runtime = e.runtimeManager()
        runtime.stop().getOrThrow(); runtime.selectProfile(saved.selected, profiles::selectProfile)
        e.database().profiles().getById(saved.profileId)?.let { check(it.label == saved.token && it.id !in saved.profileIds); profiles.deleteProfile(it.id) }
        for (row in e.database().scripts().getAll().filter { it.id !in saved.scriptIds }) {
            check(row.label == saved.token); scripts.delete(row.id)
        }
        runtime.setVpnEnabled(saved.vpn).getOrThrow()
        val rows = e.database().profiles().getAll(); val scriptRows = e.database().scripts().getAll()
        assertEquals(saved.profileIds, rows.map { it.id }); assertEquals(saved.profileDigest, digest(rows))
        assertEquals(saved.profileFiles, rows.associate { it.id to ResourceAcceptanceInputs.signature(profiles.profileFile(it.id)) })
        assertEquals(saved.scriptIds, scriptRows.map { it.id }); assertEquals(saved.scriptDigest, digest(scriptRows))
        assertEquals(saved.scriptFiles, scriptRows.associate { it.id to ResourceAcceptanceInputs.signature(scripts.scriptFile(it.id)) })
        assertEquals(saved.selected, profiles.currentProfileId.value)
        backup.write(null); assertNull(backup.read())
        ResourceAcceptanceInputs.report(JSONObject().put("case", "script-network").put("phase", "restored").put("formalProfiles", rows.size).put("formalScripts", scriptRows.size))
    }
}
