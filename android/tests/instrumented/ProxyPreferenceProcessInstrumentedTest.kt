package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：优选恢复必须由两个独立instrumentation进程证明，Activity重建不能代替冷进程重新加载核心。 */
class ProxyPreferenceProcessInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "proxy-preference-process.json")
    private fun gate() {
        assertPortraitDefaultFontConfiguration()
        assertEquals("true", androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("kclashProxyProcessAcceptance"))
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("kclash_proxy_process_pid", android.os.Process.myPid().toString())
        })
    }

    @Test fun prepareRealComputedPreferencesForColdProcess(): Unit = runBlocking {
        gate(); assertFalse("不能覆盖未收尾的跨进程夹具", marker().exists())
        val e = entry(); val repo = e.profileRepository(); val runtime = e.runtimeManager()
        val rows = e.database().profiles().getAll()
        val originalIds = rows.map { it.id }.toSet()
        if (originalIds.isNotEmpty()) {
            compose.waitUntil(30_000) { repo.currentProfileId.value?.let(originalIds::contains) == true }
        }
        val selected = repo.currentProfileId.value
        check(selected == null || selected in originalIds)
        // 作者 long：测试 Activity 会遵循应用已有的自动运行偏好；先等待并停止启动中的测试会话，保持夹具只验证优选恢复。
        if (runtime.state.value != CoreRuntimeStatus.STOPPED) runtime.stop().getOrThrow()
        compose.waitUntil(30_000) { runtime.state.value == CoreRuntimeStatus.STOPPED }
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val bytes = readAuthorizedProxyProfile()
        val record = JsonObject().apply {
            addProperty("preparedPid", android.os.Process.myPid())
            addProperty("selected", selected)
            addProperty("rows", hash(Gson().toJson(rows)))
            addProperty("files", files(rows.map { it.id }))
            addProperty("scripts", scripts())
            addProperty("nodes", hash(Gson().toJson(repo.localProxies())))
            addProperty("vpn", e.vpnSettingsStore().read())
            addProperty("mode", runtime.outboundMode.value.name)
            addProperty("options", Gson().toJson(ProxyPageOptionsStore.load(compose.activity)))
        }
        val temporary = repo.addFileProfile("proxy-process-${System.nanoTime()}", bytes)
        record.addProperty("id", temporary.id); record.addProperty("label", temporary.label)
        var stage = "准备本地 Profile"
        try {
            marker().writeText(record.toString())
            ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
            runtime.setVpnEnabled(false).getOrThrow()
            runtime.setOutboundMode(CoreOutboundMode.RULE).getOrThrow()
            runtime.selectProfile(temporary.id, repo::selectProfile)
            stage = "启动真实核心"
            if (runtime.state.value == CoreRuntimeStatus.STOPPED) runtime.startProxy().getOrThrow()
            stage = "等待核心运行"
            compose.waitUntil(30_000) { runtime.state.value == CoreRuntimeStatus.RUNNING }
            val since = runtime.runningSinceElapsedMillis.value
            stage = "重建 Activity"
            compose.activityRule.scenario.recreate()
            stage = "打开代理页"
            compose.onNodeWithTag("nav_proxy").performClick()
            val model = model()
            stage = "等待代理组加载"
            compose.waitUntil(30_000) { model.state.value.profileId == temporary.id && !model.state.value.isLoading && model.state.value.groups.isNotEmpty() }
            val targets = com.google.gson.JsonArray()
            for (type in listOf("URLTest")) {
                stage = "${type}真实测速"
                val snapshot = runtime.getProxyGroups(temporary.id).getOrThrow()
                val group = snapshot.groups.first { it.type.equals(type, true) && !it.hidden }
                val names = snapshot.groups.map { it.name }.toSet()
                val url = group.testUrl?.takeIf { it.isNotBlank() } ?: CoreBridge.DEFAULT_TEST_URL
                // 作者 long：只探测原文件已有叶子及原组测速地址，真实正延迟才进入优选恢复验收；无可用节点必须失败，不能用假结果代替。
                var healthy: String? = null
                for (candidate in group.all.filter { it !in names && it !in LocalProxies.reserved }.take(4)) {
                    if ((runtime.testDelay(temporary.id, candidate, url, 4_000).getOrNull()?.value ?: -1) > 0) { healthy = candidate; break }
                }
                val target = checkNotNull(healthy) { "${type}没有可用叶子节点" }
                stage = "${type}菜单优选"
                compose.onNodeWithTag("proxy_group_tab_${group.name}").performScrollTo().performClick()
                val tag = "proxy_node_${group.name}_$target"
                compose.onNodeWithTag("proxy_group_members_${group.name}").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performTouchInput { longClick() }
                compose.onNodeWithTag("${tag}_menu_select").performClick()
                compose.waitUntil(30_000) { model.state.value.switchingGroup == null && model.state.value.selectedMap[group.name] == target }
                val core = runtime.getProxyGroups(temporary.id).getOrThrow().groups.single { it.name == group.name }
                assertEquals(target, core.fixed); assertEquals(target, core.now)
                targets.add(JsonObject().apply { addProperty("group", group.name); addProperty("node", target); addProperty("url", url); addProperty("type", type) })
                assertEquals(since, runtime.runningSinceElapsedMillis.value)
            }
            record.add("targets", targets); marker().writeText(record.toString())
            assertEquals(1, targets.size())
            assertEquals(record.get("rows").asString, hash(Gson().toJson(e.database().profiles().getAll().filter { it.id != temporary.id })))
            assertEquals(record.get("files").asString, files(rows.map { it.id }))
        } catch (failure: Throwable) {
            try { restore(record) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            // 作者 long：核心错误可能含真实节点和凭据，失败日志只暴露类型，详细夹具留在应用私有目录。
            val location = failure.stackTrace.filter { it.className.startsWith("com.lonnnnnng") }
                .take(4).joinToString("|") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            val cleanup = failure.suppressed.joinToString(",") { it.javaClass.simpleName }.ifBlank { "none" }
            throw AssertionError("跨进程优选准备失败：$stage/${failure.javaClass.simpleName}/$location/cleanup=$cleanup")
        } finally { runtime.stop().getOrThrow() }
    }

    @Test fun coldProcessRestoresComputedPreferencesAndRealNetworkThenClearsThem(): Unit = runBlocking {
        gate()
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertNotEquals("必须先由外部驱动结束旧应用进程", record.get("preparedPid").asInt, android.os.Process.myPid())
        val e = entry(); val repo = e.profileRepository(); val runtime = e.runtimeManager(); val id = record.get("id").asLong
        try {
            assertEquals(id, repo.currentProfileId.value)
            assertArrayEquals(readAuthorizedProxyProfile(), repo.profileFile(id).readBytes())
            val targets = record.getAsJsonArray("targets"); assertEquals(1, targets.size())
            runtime.startProxy().getOrThrow()
            val since = runtime.runningSinceElapsedMillis.value
            compose.onNodeWithTag("nav_proxy").performClick(); val model = model()
            compose.waitUntil(30_000) { model.state.value.profileId == id && !model.state.value.isLoading && model.state.value.groups.isNotEmpty() }
            for (value in targets) {
                val target = value.asJsonObject; val name = target.get("group").asString; val node = target.get("node").asString
                assertEquals(node, decodeSelectedMap(e.database().profiles().getById(id)!!.selectedMapJson)[name])
                val restored = runtime.getProxyGroups(id).getOrThrow().groups.single { it.name == name }
                assertEquals(node, restored.fixed); assertEquals(node, restored.now)
                assertTrue("新进程恢复后必须仍有真实正延迟", (runtime.testDelay(id, node, target.get("url").asString, 8_000).getOrNull()?.value ?: -1) > 0)
                compose.onNodeWithTag("proxy_group_tab_$name").performScrollTo().performClick()
                val tag = "proxy_node_${name}_$node"
                compose.onNodeWithTag("proxy_group_members_$name").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).assertProxyInUse(compose).performTouchInput { longClick() }
                compose.onNodeWithTag("${tag}_menu_select").performClick()
                compose.waitUntil(30_000) { model.state.value.switchingGroup == null && model.state.value.selectedMap[name] == null }
                assertNull(decodeSelectedMap(e.database().profiles().getById(id)!!.selectedMapJson)[name])
                assertTrue(runtime.getProxyGroups(id).getOrThrow().groups.single { it.name == name }.fixed.isNullOrBlank())
                assertEquals(since, runtime.runningSinceElapsedMillis.value)
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putString("kclash_proxy_process_result", "computed-groups=1 restored-core-fixed=true real-positive-delays=true clear-fixed=true session-preserved=true")
            })
        } finally { restore(record) }
    }

    private suspend fun restore(record: JsonObject) {
        val e = entry(); val repo = e.profileRepository(); val runtime = e.runtimeManager(); val id = record.get("id").asLong
        // 作者 long：页面交互失败不能留下临时配置；恢复仍逐项回读正式数据，失败时保留marker供定点修复。
        runCatching { compose.onNodeWithTag("nav_dashboard").performClick(); compose.waitForIdle() }
        runtime.stop().getOrThrow()
        val selected = record.get("selected")?.takeUnless { it.isJsonNull }?.asLong
        if (selected == null) repo.selectProfile(null) else runtime.selectProfile(selected, repo::selectProfile)
        val owned = e.database().profiles().getById(id)
        if (owned != null) {
            check(owned.label == record.get("label").asString && owned.label.startsWith("proxy-process-"))
            assertArrayEquals(readAuthorizedProxyProfile(), repo.profileFile(id).readBytes())
            repo.deleteProfile(id)
        }
        if (selected == null) repo.selectProfile(null)
        runtime.setOutboundMode(CoreOutboundMode.valueOf(record.get("mode").asString)).getOrThrow()
        runtime.setVpnEnabled(record.get("vpn").asBoolean).getOrThrow()
        ProxyPageOptionsStore.save(compose.activity, Gson().fromJson(record.get("options").asString, ProxyPageOptions::class.java))
        val rows = e.database().profiles().getAll()
        assertEquals(record.get("rows").asString, hash(Gson().toJson(rows)))
        assertEquals(record.get("files").asString, files(rows.map { it.id }))
        assertEquals(record.get("scripts").asString, scripts())
        assertEquals(record.get("nodes").asString, hash(Gson().toJson(repo.localProxies())))
        assertEquals(selected, repo.currentProfileId.value)
        check(marker().delete())
    }

    private fun model(): ProxyGroupsViewModel {
        lateinit var value: ProxyGroupsViewModel
        compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProxyGroupsViewModel::class.java] }; return value
    }
    private fun hash(text: String) = hash(text.toByteArray())
    private fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun files(ids: List<Long>) = ids.joinToString("|") { id -> entry().profileRepository().profileFile(id).let { "$id:${it.length()}:${it.lastModified()}:${hash(it.readBytes())}" } }
    private suspend fun scripts() = entry().database().scripts().getAll().let { rows -> hash(Gson().toJson(rows) + rows.joinToString("|") { entry().scriptRepository().scriptFile(it.id).let { file -> "${file.length()}:${file.lastModified()}:${hash(file.readBytes())}" } }) }
}
