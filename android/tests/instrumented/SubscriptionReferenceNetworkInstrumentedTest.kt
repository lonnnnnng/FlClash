package com.lonnnnnng.flclash.data

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.ProfileSyncStatus
import com.lonnnnnng.flclash.ui.ProfilesViewModel
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：不启动Activity及节点轮询，更新后的首次核心回读不得顺带加载配置而掩盖应用入口失效。 */
class SubscriptionReferenceNetworkInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val entry get() = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
    private val backup get() = EncryptedDraftFile(context, "subscription-reference-acceptance.v1", "com.lonnnnnng.flclash.acceptance.subscription-reference")
    private data class Snapshot(val profiles: List<Profile>, val selected: Long, val vpn: Boolean,
        val sourceId: Long, val customId: Long, val token: String, val files: Map<Long, String>)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun signature(id: Long) = entry.profileRepository().profileFile(id).let { "${it.length()}:${it.lastModified()}:${sha(it.readBytes())}" }
    private fun report(value: JSONObject) = InstrumentationRegistry.getInstrumentation().sendStatus(2,
        Bundle().apply { putString("kclash_subscription_reference", value.toString()) })
    private fun gate() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashSubscriptionReferenceAcceptance"))
        assertPortraitDefaultFontConfiguration()
    }
    private fun authorized(rows: List<Profile>): Profile {
        val expected = InstrumentationRegistry.getArguments().getString("kclashAuthorizedSourceSha256")
        require(expected?.matches(Regex("[0-9a-f]{64}")) == true)
        return rows.single { it.type == ProfileType.URL && sha(it.url.toByteArray()) == expected }
    }

    @Test fun inspectAuthorizedOnlinePayloadWithoutChangingProfiles(): Unit = runBlocking {
        gate()
        val e = entry; val repo = e.profileRepository()
        repo.withAutomaticUpdateBarrier {
            val rows = e.database().profiles().getAll()
            val files = rows.associate { it.id to signature(it.id) }
            val source = authorized(rows.map { it.toDomain() })
            try {
                val downloaded = withContext(Dispatchers.IO) { e.subscriptionClient().download(source.url) }
                val age = isAgeEncryptedProfile(downloaded.bytes)
                report(JSONObject().put("phase", "payload").put("ageArmor", age).put("keyPresent", source.ageSecretKey.isNotBlank())
                    .put("wireBytes", downloaded.bytes.size).put("wireSha256", sha(downloaded.bytes)))
                if (!age || source.ageSecretKey.isNotBlank()) {
                    val plain = e.profileContentDecoder().decode(downloaded.bytes, source.ageSecretKey)
                    assertTrue(ProxyGroupDrafts.proxyNames(plain.decodeToString()).isNotEmpty())
                    report(JSONObject().put("phase", "decoded").put("ageDecryptionExercised", age)
                        .put("plainBytes", plain.size).put("plainSha256", sha(plain)))
                }
            } catch (failure: Exception) {
                throw AssertionError("授权响应检查失败：${failure.javaClass.simpleName}")
            } finally {
                assertTrue("响应检查不得改写正式元数据", rows == e.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(it.id) })
            }
        }
    }

    @Test fun manualSyncRefreshesRunningCustomReference() = verifyReference(automatic = false)
    @Test fun automaticDueUpdateRefreshesRunningCustomReference() = verifyReference(automatic = true)

    private fun verifyReference(automatic: Boolean): Unit = runBlocking {
        gate()
        val e = entry; val repo = e.profileRepository(); val runtime = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        check(backup.read() == null) { "上次引用资源验收尚未恢复" }
        val owners = ViewModelStore()
        var modelJob: Job? = null
        var prepared: Snapshot? = null
        var stage = "prepare"
        try {
            val snapshot = repo.withAutomaticUpdateBarrier {
                val rows = e.database().profiles().getAll()
                val selected = withTimeout(30_000) { repo.currentProfileId.first { id -> rows.any { it.id == id } } }!!
                val source = authorized(rows.map { it.toDomain() })
                check(e.database().appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
                val id = System.nanoTime().and(Long.MAX_VALUE)
                check(rows.none { it.id == id || it.id == id + 1 })
                val saved = Snapshot(rows.map { it.toDomain() }, selected, e.vpnSettingsStore().read(), id, id + 1,
                    "引用在线验收-$id", rows.associate { it.id to signature(it.id) })
                backup.write(Gson().toJson(saved).toByteArray()); prepared = saved
                // 作者 long：先持久化独占ID和原自动更新开关；中断后只恢复本组改动，不用旧整库覆盖用户的新编辑。
                e.database().withTransaction {
                    rows.filter { it.autoUpdate }.forEach { e.database().profiles().update(it.copy(autoUpdate = false)) }
                    e.database().profiles().insert(Profile.remote(id, source.url, saved.token + "-source")
                        .copy(ageSecretKey = source.ageSecretKey, autoUpdate = false, orderIndex = rows.size).toEntity())
                    e.database().profiles().insert(Profile.custom(id + 1, saved.token + "-custom").copy(orderIndex = rows.size + 1).toEntity())
                }
                saved
            }
            val id = snapshot.sourceId; val customId = snapshot.customId
            val bytes = readAuthorizedProxyProfile()
            for (target in listOf(id, customId)) repo.profileFile(target).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            val body = bytes.decodeToString()
            val alias = "在线验收资源"; val group = "在线验收组"
            val extra = ProxyGroupDraft(json = "{\"name\":\"$group\",\"type\":\"select\",\"proxies\":[\"DIRECT\"],\"use\":[\"$alias\"]}")
            repo.saveCustomProxyGroups(customId, ProxyGroupsSave(ProxyGroupDrafts.read(body) + extra, mapOf(alias to id)), body) { true }
            assertTrue(repo.referencesProfile(customId, id))
            val customBody = signature(customId)
            val beforePath = repo.profileById(customId)!!.filePath
            runtime.setVpnEnabled(false).getOrThrow()
            runtime.selectProfile(customId, repo::selectProfile); runtime.startProxy().getOrThrow()
            runtime.changeProxy(customId, group, "DIRECT").getOrThrow()
            val since = checkNotNull(runtime.runningSinceElapsedMillis.value)
            val beforeEpoch = e.coreBridge().ipRouteStamp().epoch
            val beforeProvider = runtime.profileProviders(customId).single { it.name == alias }
            val beforeSha = sha(repo.profileFile(id).readBytes())
            val sourceBefore = checkNotNull(e.database().profiles().getById(id)).toDomain()
            stage = if (automatic) "automatic-due" else "manual-sync"
            if (automatic) {
                repo.updateProfileAutoUpdate(id, true, 900_000)
                // 作者 long：本组验证生产到期处理与引用应用，不冒充WorkManager自然周期；临时新源无更新时间，天然到期。
                val result = repo.updateDueProfiles(System.currentTimeMillis())
                assertEquals(listOf(id), result.downloadedIds)
                assertEquals(listOf(id), result.appliedSourceIds)
                assertTrue(result.failures.isEmpty() && result.applyFailures.isEmpty())
                assertTrue(result.checks.single { it.profileId == id }.due)
                assertTrue(e.database().appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
            } else {
                lateinit var model: ProfilesViewModel
                withContext(Dispatchers.Main) {
                    model = ProfilesViewModel(repo, runtime); owners.put("reference", model)
                    modelJob = model.viewModelScope.coroutineContext[Job]
                    model.update(sourceBefore)
                }
                withTimeout(120_000) { while (model.syncState.value.running) delay(100) }
                val result = model.syncState.value
                assertEquals(1, result.items.size)
                assertEquals(ProfileSyncStatus.SUCCEEDED, result.items.single().status)
                assertNull(result.applyError)
            }
            stage = "readback-without-load"
            // 作者 long：先用只读输入/Provider/epoch断言，再读节点；禁止getProxyGroups自动加载替测试补救。
            assertEquals(customId, repo.currentProfileId.value)
            assertTrue("更新入口必须先完成当前CUSTOM应用", runtime.profileInputsApplied(customId))
            val afterEpoch = e.coreBridge().ipRouteStamp().epoch
            val provider = runtime.profileProviders(customId).single { it.name == alias }
            val updated = checkNotNull(e.database().profiles().getById(id)).toDomain()
            assertTrue(checkNotNull(updated.lastUpdateAt) > (sourceBefore.lastUpdateAt ?: 0))
            assertNull(updated.lastUpdateError)
            assertTrue(sourceBefore.url == updated.url && sourceBefore.ageSecretKey == updated.ageSecretKey)
            val downloaded = repo.profileFile(id).readBytes(); val afterSha = sha(downloaded)
            val expected = ProxyGroupDrafts.proxyNames(downloaded.decodeToString()).toSet()
            assertTrue(expected.isNotEmpty())
            val snapshotFile = File(context.filesDir, "mihomo/providers/profiles/$id-$afterSha.yaml")
            assertTrue(snapshotFile.isFile && snapshotFile.readBytes().contentEquals(downloaded))
            assertEquals(sha(snapshotFile.absolutePath.toByteArray()), provider.fileIdentity)
            assertEquals(expected.size.toLong(), provider.count)
            assertEquals("Proxy", provider.type); assertEquals("File", provider.vehicleType)
            val changed = beforeSha != afterSha
            if (changed) {
                assertNotEquals(beforePath, repo.profileById(customId)!!.filePath)
                assertNotEquals(beforeProvider.fileIdentity, provider.fileIdentity)
                assertTrue("正文变化必须实际应用到核心新代际", afterEpoch > beforeEpoch)
            } else assertEquals(beforeEpoch, afterEpoch)
            val loaded = e.coreBridge().getProxyGroups().groups.single { it.name == group }
            assertEquals(expected + "DIRECT", loaded.all.toSet()); assertEquals("DIRECT", loaded.now)
            assertEquals(customBody, signature(customId))
            assertEquals(since, runtime.runningSinceElapsedMillis.value)
            assertEquals(CoreRuntimeStatus.RUNNING, runtime.state.value)
            report(JSONObject().put("phase", "passed").put("automatic", automatic).put("bodyChanged", changed)
                .put("sourceBeforeSha256", beforeSha).put("sourceAfterSha256", afterSha).put("providerCount", provider.count)
                .put("providerPathSha256", provider.fileIdentity).put("epochBefore", beforeEpoch).put("epochAfter", afterEpoch)
                .put("sessionPreserved", true).put("customBodyPreserved", true).put("selectedPreserved", true))
        } catch (failure: Exception) {
            report(JSONObject().put("phase", "failed").put("stage", stage).put("type", failure.javaClass.simpleName))
            throw AssertionError("引用在线验收失败：$stage/${failure.javaClass.simpleName}")
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { owners.clear() }
                // 作者 long：取消作用域不代表NonCancellable文件提交已退出；等同步完全结束后才能恢复选择和删除独占源。
                modelJob?.join()
                prepared?.let { restore(it) }
            }
        }
    }

    @Test fun restoreInterruptedReferenceAcceptance(): Unit = runBlocking {
        gate()
        val saved = checkNotNull(backup.read()) { "没有待恢复的引用验收" }
        restore(Gson().fromJson(saved.decodeToString(), Snapshot::class.java))
    }

    private suspend fun restore(saved: Snapshot) = entry.profileRepository().withAutomaticUpdateBarrier {
        val e = entry; val repo = e.profileRepository(); val runtime = e.runtimeManager()
        runtime.stop().getOrThrow()
        assertEquals(saved.files, saved.profiles.associate { it.id to signature(it.id) })
        for (original in saved.profiles) {
            val current = checkNotNull(e.database().profiles().getById(original.id))
            assertTrue("正式配置另有编辑，保留恢复记录并停止覆盖", original.copy(autoUpdate = false).toEntity() == current.copy(autoUpdate = false))
        }
        runtime.selectProfile(saved.selected, repo::selectProfile)
        for ((id, suffix) in listOf(saved.customId to "custom", saved.sourceId to "source")) {
            e.database().profiles().getById(id)?.let {
                check(it.label == saved.token + "-" + suffix && saved.profiles.none { row -> row.id == id })
                repo.deleteProfile(id)
            }
            // 作者 long：哈希投影不会随删除源自动回收；只删除本组独占ID的文件，不扫描删除其他历史/正式缓存。
            for (directory in listOf(File(context.filesDir, "resolved-profiles"), File(context.filesDir, "mihomo/providers/profiles"))) {
                directory.listFiles()?.filter { it.name.matches(Regex("$id-[0-9a-f]{64}\\.yaml")) }?.forEach { check(it.delete()) }
                File(directory, ".$id.tmp").takeIf { it.exists() }?.let { check(it.delete()) }
            }
        }
        e.database().withTransaction {
            saved.profiles.forEach { e.database().profiles().update(it.toEntity()) }
            e.database().appPreferences().delete("subscription-pending-apply:${saved.sourceId}")
        }
        runtime.setVpnEnabled(saved.vpn).getOrThrow()
        assertEquals(saved.profiles.map { it.toEntity() }, e.database().profiles().getAll())
        assertEquals(saved.selected, repo.currentProfileId.value)
        backup.write(null)
        check(backup.read() == null)
        report(JSONObject().put("phase", "restored").put("formalCount", saved.profiles.size).put("selected", saved.selected))
    }
}
