package com.lonnnnnng.flclash.data

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.google.gson.Gson
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import dagger.hilt.android.EntryPointAccessors
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：不创建页面、不调时、不直接执行Worker；后台真实20分钟任务必须在真实同步t0后自然到期。 */
class SubscriptionNaturalCycleInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val entry get() = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
    private val backup get() = EncryptedDraftFile(context, "subscription-cycle-acceptance.v1", "com.lonnnnnng.flclash.acceptance.subscription-cycle")
    private data class Snapshot(val profiles: List<Profile>, val selected: Long, val vpn: Boolean, val targetId: Long, val token: String, val files: Map<Long, String>)
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun signature(id: Long) = entry.profileRepository().profileFile(id).let { "${it.length()}:${it.lastModified()}:${digest(it.readBytes())}" }
    private fun status(value: String) = InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("kclash_natural_cycle", value) })
    private fun gate() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashNaturalCycleAcceptance"))
        assertPortraitDefaultFontConfiguration()
    }

    @Test fun duePeriodicWorkerDownloadsAndAppliesInBackgroundWithoutChangingFormalProfiles(): Unit = runBlocking {
        gate()
        val e = entry; val repo = e.profileRepository(); val manager = e.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        check(backup.read() == null) { "上次自然周期验收尚未恢复，请先执行恢复方法" }
        var prepared: Snapshot? = null
        try {
            val snapshot = repo.withAutomaticUpdateBarrier {
                val rows = e.database().profiles().getAll()
                assertTrue(rows.isNotEmpty())
                val selected = withTimeout(30_000) { repo.currentProfileId.first { id -> rows.any { it.id == id } } }!!
                val authorized = checkNotNull(InstrumentationRegistry.getArguments().getString("kclashAuthorizedSourceSha256"))
                val source = rows.first { it.type == ProfileType.URL && digest(it.url.toByteArray()) == authorized }.toDomain()
                val id = System.nanoTime().and(Long.MAX_VALUE)
                val snapshot = Snapshot(rows.map { it.toDomain() }, selected, e.vpnSettingsStore().read(), id,
                    "自然周期验收-$id", rows.associate { it.id to signature(it.id) })
                backup.write(Gson().toJson(snapshot).toByteArray())
                prepared = snapshot
                // 作者 long：与Worker批处理共用屏障，关闭原自动更新前，必须等此前已受理的下载退出。
                e.database().withTransaction {
                    rows.filter { it.autoUpdate }.forEach { e.database().profiles().update(it.copy(autoUpdate = false)) }
                    e.database().profiles().insert(Profile.remote(id, source.url, snapshot.token).copy(ageSecretKey = source.ageSecretKey, autoUpdate = false, orderIndex = rows.size).toEntity())
                }
                snapshot
            }
            val id = snapshot.targetId
            val source = snapshot.profiles.first { it.type == ProfileType.URL && digest(it.url.toByteArray()) == InstrumentationRegistry.getArguments().getString("kclashAuthorizedSourceSha256") }
            val scheduled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SubscriptionAutoUpdateScheduler.WORK_NAME).get(10, TimeUnit.SECONDS).single { !it.state.isFinished }
            // 作者 long：临时副本使用同一授权来源；暂停原配置自动更新，避免等待期间修改正式正文和更新时间。
            repo.profileFile(id).apply { parentFile!!.mkdirs(); writeBytes(repo.profileFile(source.id).readBytes()) }
            val synchronized = repo.updateRemoteProfile(id)
            val t0 = checkNotNull(synchronized.lastUpdateAt)
            val initial = signature(id)
            repo.updateProfileAutoUpdate(id, true, 900_000)
            manager.setVpnEnabled(false).getOrThrow()
            manager.selectProfile(id, repo::selectProfile)
            manager.startProxy().getOrThrow()
            val since = manager.runningSinceElapsedMillis.value
            assertTrue(manager.profileInputsApplied(id))
            status("prepared workId=${scheduled.id} t0=$t0 sourceId=$id interval=900000 pid=${android.os.Process.myPid()}")
            val deadline = SystemClock.elapsedRealtime() + 65 * 60_000L
            var accepted: org.json.JSONObject? = null
            while (SystemClock.elapsedRealtime() < deadline && accepted == null) {
                val audit = SubscriptionAutoUpdateAudit.read(context)
                for (index in 0 until audit.length()) {
                    val run = audit.getJSONObject(index)
                    if (run.getString("workId") != scheduled.id.toString() || run.getLong("checkedAt") < t0) continue
                    val downloads = run.getJSONArray("downloadedIds")
                    if ((0 until downloads.length()).any { downloads.getLong(it) == id }) accepted = run
                }
                if (accepted == null) {
                    assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
                    assertEquals(since, manager.runningSinceElapsedMillis.value)
                    status("waiting elapsedSinceT0=${System.currentTimeMillis() - t0} workId=${scheduled.id} audits=${audit.length()}")
                    delay(30_000)
                }
            }
            val run = checkNotNull(accepted) { "自然周期未在65分钟内完成到期更新" }
            val checks = run.getJSONArray("checks")
            val check = (0 until checks.length()).map { checks.getJSONObject(it) }.single { it.getLong("profileId") == id }
            assertEquals(t0, check.getLong("lastUpdateAt")); assertEquals(900_000L, check.getLong("intervalMillis")); assertTrue(check.getBoolean("due"))
            assertTrue(run.getLong("checkedAt") - t0 >= 900_000)
            assertEquals(0, run.getJSONArray("downloadFailureIds").length()); assertEquals(0, run.getJSONArray("applyFailureIds").length())
            val handled = run.getJSONArray("handledSourceIds")
            assertTrue((0 until handled.length()).any { handled.getLong(it) == id })
            val updated = checkNotNull(e.database().profiles().getById(id))
            assertTrue(checkNotNull(updated.lastUpdateAt) - t0 >= 900_000)
            assertNull(updated.lastUpdateError); assertTrue("订阅来源必须保持", source.url == updated.url); assertTrue("订阅密钥必须保持", source.ageSecretKey == updated.ageSecretKey)
            assertEquals(id, repo.currentProfileId.value); assertTrue(manager.profileInputsApplied(id))
            assertEquals(since, manager.runningSinceElapsedMillis.value); assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
            assertNotEquals(initial, signature(id))
            status("passed t0=$t0 t1=${updated.lastUpdateAt} before=$initial after=${signature(id)} audit=$run")
        } catch (failure: Exception) {
            // 作者 long：网络异常可能携带真实来源，失败只报告安全阶段和异常类型，完整回收仍由finally执行。
            status("failed type=${failure.javaClass.simpleName}")
            throw AssertionError("自然周期验收失败：${failure.javaClass.simpleName}")
        } finally { prepared?.let { snapshot -> withContext(NonCancellable) { restore(snapshot) } } }
    }

    @Test fun restoreInterruptedNaturalCycle(): Unit = runBlocking {
        gate()
        val bytes = backup.read()
        org.junit.Assume.assumeTrue("没有未收尾的自然周期验收", bytes != null)
        checkNotNull(bytes)
        restore(Gson().fromJson(bytes.toString(Charsets.UTF_8), Snapshot::class.java))
    }

    private suspend fun restore(snapshot: Snapshot) = entry.profileRepository().withAutomaticUpdateBarrier {
        val e = entry; val repo = e.profileRepository(); val manager = e.runtimeManager()
        manager.stop().getOrThrow()
        // 作者 long：等待期间若正式文件或用户元数据另有变化，保留密文恢复记录并报错，不覆盖用户的新编辑。
        assertEquals(snapshot.files, snapshot.profiles.associate { it.id to signature(it.id) })
        for (original in snapshot.profiles) {
            val current = checkNotNull(e.database().profiles().getById(original.id))
            assertTrue("正式元数据不能被验收以外的写入覆盖", original.copy(autoUpdate = false).toEntity() == current.copy(autoUpdate = false))
        }
        manager.selectProfile(snapshot.selected, repo::selectProfile)
        e.database().profiles().getById(snapshot.targetId)?.let { target ->
            check(target.label == snapshot.token)
            repo.deleteProfile(target.id)
        }
        e.database().withTransaction {
            snapshot.profiles.forEach { e.database().profiles().update(it.toEntity()) }
            e.database().appPreferences().delete("subscription-pending-apply:${snapshot.targetId}")
        }
        manager.setVpnEnabled(snapshot.vpn).getOrThrow()
        assertTrue("正式配置行必须恢复", snapshot.profiles.map { it.toEntity() } == e.database().profiles().getAll())
        assertEquals(snapshot.selected, repo.currentProfileId.value)
        backup.write(null)
        status("restored formalCount=${snapshot.profiles.size} selected=${snapshot.selected}")
    }
}
