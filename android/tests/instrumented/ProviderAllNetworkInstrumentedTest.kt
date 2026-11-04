package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.CoreExternalProvider
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** 作者 long：全部目标来自授权原1.yaml；通过正式UI提交，逐项回读真实核心更新时间，失败结果也留存且只重试失败项。 */
class ProviderAllNetworkInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allOriginalYamlHttpProvidersUpdateReadBackAndKeepRunningSession() = verifyProviders(remainingOnly = false)

    @Test fun remainingOriginalYamlProvidersUpdateIndividuallyAndKeepRunningSession() = verifyProviders(remainingOnly = true)

    private fun verifyProviders(remainingOnly: Boolean) = runBlocking {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashAllProvidersAcceptance"))
        assertPortraitDefaultFontConfiguration()
        val e = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = e.profileRepository(); val manager = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val rows = e.database().profiles().getAll()
        if (rows.isNotEmpty()) compose.waitUntil(30_000) { repo.currentProfileId.value in rows.map { it.id } }
        val selected = repo.currentProfileId.value
        val files = rows.associate { it.id to signature(repo.profileFile(it.id)) }
        val vpn = e.vpnSettingsStore().read()
        val bytes = readAuthorizedProxyProfile()
        val definitions = JsonParser.parseString(YamlConfigCodec.toJson(bytes.decodeToString())).asJsonObject.getAsJsonObject("rule-providers")
        val token = "provider-all-${System.nanoTime()}"
        var temporary: Profile? = null
        var stage = "prepare"
        try {
            temporary = repo.addFileProfile(token, bytes)
            val id = temporary.id
            manager.setVpnEnabled(false).getOrThrow()
            manager.selectProfile(id, repo::selectProfile)
            manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            lateinit var model: ProfileStatisticsViewModel
            fun model() { compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] } }
            model()
            compose.onNodeWithTag("page_dashboard").performScrollToNode(hasTestTag("dashboard_profile_card"))
            compose.onNodeWithTag("dashboard_profile_card").performClick()
            fun ready() = model.providers.value.let { it.profileId == id && !it.isLoading && it.error == null && it.providers != null }
            fun idle() = model.providers.value.let { it.updatingName == null && it.updatingNames.isEmpty() }
            compose.waitUntil(20_000) { ready() }
            val before = model.providers.value.providers!!
            assertEquals(90, before.size)
            assertTrue("每个目标必须为原配置已有的HTTP规则资源", before.all { it.canUpdate && it.type == "Rule" && definitions.has(it.name) })
            assertTrue("核心资源集合必须与原配置一致", definitions.keySet() == before.map { it.name }.toSet())
            val targets = if (remainingOnly) {
                // 作者 long：补验只接收前轮失败资源的摘要，仍从原配置解析名称；不重新下载已成功的87项，也不接受新URL。
                val hashes = InstrumentationRegistry.getArguments().getString("kclashProviderRetryHashes").orEmpty().split(',').toSet()
                before.filter { sha(it.name.toByteArray()) in hashes }.also {
                    check(it.isNotEmpty() && it.size == hashes.size) { "补验摘要必须全部属于原配置" }
                }
            } else before
            val individualFailures = mutableSetOf<String>()
            if (remainingOnly) {
                stage = "update-remaining"
                for (target in targets) {
                    val index = model.providers.value.providers!!.indexOfFirst { it.name == target.name }
                    val tag = "profile_provider_update_$index"
                    compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag(tag))
                    compose.onNodeWithTag(tag).performClick()
                    compose.waitUntil(45_000) { idle() && model.providers.value.updateMessage != null }
                    if (model.providers.value.updateFailed) individualFailures += target.name
                    compose.waitUntil(20_000) { ready() }
                }
            } else {
                stage = "update-all"
                compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_providers_update_all"))
                compose.onNodeWithTag("profile_providers_update_all").performClick()
                compose.waitUntil(5_000) { !idle() || model.providers.value.updateMessage != null }
                compose.waitUntil(300_000) { idle() && model.providers.value.updateMessage != null }
            }
            var failed = if (remainingOnly) individualFailures.toSet() else model.providers.value.failedNames
            var after = manager.profileProviders(id).associateBy { it.name }
            fun report(phase: String, targets: List<CoreExternalProvider>, failures: Set<String>) {
                targets.forEachIndexed { index, old ->
                    val new = after[old.name]
                    val definition = definitions.getAsJsonObject(old.name)
                    val result = org.json.JSONObject().put("phase", phase).put("index", index)
                        .put("nameSha256", sha(old.name.toByteArray())).put("type", old.type).put("vehicle", old.vehicleType)
                        .put("behavior", definition.get("behavior").asString).put("format", definition.get("format").asString)
                        .put("before", old.updatedAtMillis ?: 0).put("after", new?.updatedAtMillis ?: 0)
                        .put("count", new?.count ?: -1).put("failed", old.name in failures)
                    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("kclash_provider_result", result.toString()) })
                }
            }
            report(if (remainingOnly) "remaining" else "all", targets, failed)
            if (failed.isNotEmpty() && !remainingOnly) {
                stage = "retry-failed"
                val retry = before.filter { it.name in failed }
                compose.onNodeWithTag("dashboard_profile_detail_sheet").performScrollToNode(hasTestTag("profile_providers_retry_failed"))
                compose.onNodeWithTag("profile_providers_retry_failed").performClick()
                compose.waitUntil(300_000) { idle() && model.providers.value.updateMessage != null }
                failed = model.providers.value.failedNames
                after = manager.profileProviders(id).associateBy { it.name }
                report("retry", retry, failed)
            }
            assertTrue("仍有 ${failed.size} 个原配置HTTP资源更新失败", failed.isEmpty())
            targets.forEachIndexed { index, old ->
                val updated = requireNotNull(after[old.name])
                assertTrue("第 $index 项更新时间必须严格前进", (updated.updatedAtMillis ?: 0) > (old.updatedAtMillis ?: 0))
                assertTrue("第 $index 项必须返回有效条目数", updated.count >= 0)
            }
            stage = "poll-and-recreate"
            compose.waitUntil(20_000) { ready() }
            val deadline = android.os.SystemClock.elapsedRealtime() + 16_000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                assertTrue(ready() && idle()); assertEquals(90, model.providers.value.providers!!.size); delay(250)
            }
            compose.activityRule.scenario.recreate(); model()
            compose.waitUntil(20_000) { ready() && idle() }
            assertEquals(90, model.providers.value.providers!!.size)
            assertEquals(since, manager.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertTrue(manager.profileInputsApplied(id))
            assertArrayEquals(bytes, repo.profileFile(id).readBytes())
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_provider_all_summary", "count=${targets.size};scope=${if (remainingOnly) "remaining" else "all"};all-updated=true;polling-ms=16000;recreated=true;session-preserved=true")
            })
        } catch (failure: Throwable) {
            // 作者 long：真实网络异常可能包含URL，只保留阶段和异常类型，逐项失败通过上面的脱敏协议记录。
            throw IllegalStateException("Provider全量验收阶段=$stage 类型=${failure.javaClass.simpleName}")
        } finally {
            runCatching { compose.onNodeWithTag("nav_dashboard").performClick() }
            manager.stop().getOrThrow()
            if (selected == null) repo.selectProfile(null) else manager.selectProfile(selected, repo::selectProfile)
            temporary?.let { profile ->
                val row = e.database().profiles().getById(profile.id)
                check(row == null || row.label == token && rows.none { it.id == row.id })
                if (row != null) repo.deleteProfile(profile.id)
            }
            if (selected == null) repo.selectProfile(null)
            manager.setVpnEnabled(vpn).getOrThrow()
            assertTrue("正式配置行保持", rows == e.database().profiles().getAll())
            assertEquals(selected, repo.currentProfileId.value)
            assertEquals(files, rows.associate { it.id to signature(repo.profileFile(it.id)) })
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_provider_preserved", "profiles=${rows.size};files=true;selection=true;vpn=true;stopped=true")
            })
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), sha(file.readBytes()))
}
