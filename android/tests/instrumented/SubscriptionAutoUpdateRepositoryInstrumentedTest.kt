package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：隔离仓库只下载原1.yaml字节，验证提交/应用分离；不把模拟到期或故障注入计为真实周期更新。 */
class SubscriptionAutoUpdateRepositoryInstrumentedTest {
    private fun fixture(action: suspend (KClashDatabase, ByteArray, (RemoteProfileCommitter) -> ProfileRepository) -> Unit) = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "automatic-sync-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        val bytes = readAuthorizedProxyProfile()
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val row = Profile.remote(41, "https://example.test/source", "isolated").copy(autoUpdate = true)
        db.profiles().insert(row.toEntity())
        val observerJobs = mutableListOf<Job>()
        try {
            action(db, bytes) { committer ->
                val job = SupervisorJob().also { observerJobs += it }
                ProfileRepository(isolated, db, object : SubscriptionClient {
                override suspend fun download(url: String) = SubscriptionDownload(bytes, null, SubscriptionInfo())
            }, automaticCommitter = committer, observationScope = CoroutineScope(job + Dispatchers.IO)) }
        } finally {
            observerJobs.forEach { it.cancelAndJoin() }
            db.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("automatic-sync-"))
            directory.deleteRecursively()
        }
    }

    @Test fun committedApplyFailureSurvivesRepositoryRecreationAndRetriesWithoutDownloading() = fixture { db, bytes, factory ->
        var commits = 0
        val repository = factory(RemoteProfileCommitter { _, write ->
            commits++
            write()
            throw ProfileApplyAfterSaveException(41)
        })
        val first = repository.updateDueProfiles()
        assertEquals(1, first.attempted); assertEquals(1, first.succeeded)
        assertTrue(first.failures.isEmpty()); assertEquals(1, first.applyFailures.size)
        val saved = checkNotNull(db.profiles().getById(41))
        assertNotNull(saved.lastUpdateAt); assertNull(saved.lastUpdateError)
        assertArrayEquals(bytes, repository.profileFile(41).readBytes())
        val mtime = repository.profileFile(41).lastModified()
        assertEquals(1, db.appPreferences().getByPrefix("subscription-pending-apply:").size)
        val recreated = factory(RemoteProfileCommitter { _, write -> commits++; write() })
        val retry = recreated.updateDueProfiles()
        assertEquals(0, retry.attempted); assertEquals(0, retry.succeeded); assertEquals(0, retry.failed)
        assertEquals(listOf(41L), retry.appliedSourceIds)
        assertEquals(2, commits)
        assertEquals(saved, db.profiles().getById(41))
        assertEquals(mtime, recreated.profileFile(41).lastModified())
        assertTrue(db.appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
        assertEquals(0, recreated.updateDueProfiles().attempted)
    }

    @Test fun databaseFailureRollsBackBodyAndPendingMarkerAndCancellationIsNotReportedAsDownloadFailure() = fixture { db, bytes, factory ->
        val repository = factory(RemoteProfileCommitter { _, write -> write() })
        repository.profileFile(41).apply { parentFile!!.mkdirs(); writeBytes(bytes); setLastModified(1_600_000_000_000) }
        val saved = db.profiles().getById(41)
        val mtime = repository.profileFile(41).lastModified()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_automatic_apply BEFORE INSERT ON app_preferences WHEN NEW.`key` LIKE 'subscription-pending-apply:%' BEGIN SELECT RAISE(ABORT, 'isolated failure'); END")
        val failed = repository.updateDueProfiles()
        assertEquals(0, failed.succeeded); assertEquals(1, failed.failures.size)
        assertEquals(saved?.lastUpdateAt, db.profiles().getById(41)?.lastUpdateAt)
        assertArrayEquals(bytes, repository.profileFile(41).readBytes()); assertEquals(mtime, repository.profileFile(41).lastModified())
        assertTrue(db.appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_automatic_apply")
        val cancelled = factory(RemoteProfileCommitter { _, _ -> throw CancellationException("isolated lifecycle") })
        assertTrue(runCatching { cancelled.updateDueProfiles() }.exceptionOrNull() is CancellationException)
        assertEquals(saved?.lastUpdateAt, db.profiles().getById(41)?.lastUpdateAt)
    }

    @Test fun changedSourceInvalidatesOldPendingApplyAndAllowsNewDueDownload() = fixture { db, _, factory ->
        val failed = factory(RemoteProfileCommitter { _, write -> write(); throw ProfileApplyAfterSaveException(41) })
        assertEquals(1, failed.updateDueProfiles().applyFailures.size)
        val t0 = checkNotNull(db.profiles().getById(41)?.lastUpdateAt)
        failed.updateRemoteProfileSettings(41, "fixed", "https://example.test/fixed", true, 900_000, "")
        var calls = 0
        val fixed = factory(RemoteProfileCommitter { _, write -> calls++; write() })
        val result = fixed.updateDueProfiles(t0 + 900_000)
        assertEquals(1, result.attempted); assertEquals(1, result.succeeded); assertEquals(0, result.failed)
        assertEquals(1, calls)
        assertTrue(db.appPreferences().getByPrefix("subscription-pending-apply:").isEmpty())
    }
}
