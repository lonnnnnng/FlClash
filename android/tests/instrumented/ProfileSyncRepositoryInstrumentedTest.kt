package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：所有正文只读授权1.yaml原字节，在独占目录测试下载提交；不访问真实订阅、不改正式数据。 */
class ProfileSyncRepositoryInstrumentedTest {
    private fun fixture(action: suspend (ProfileRepository, KClashDatabase, ByteArray, (suspend () -> Unit) -> Unit) -> Unit) = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 作者 long：授权正文会更新，使用当轮原文件的大小/摘要门禁，不能为了旧固定SHA换回旧配置。
        val bytes = readAuthorizedProxyProfile()
        val directory = File(context.cacheDir, "profile-sync-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        var beforeDownload: suspend () -> Unit = {}
        val observationJob = SupervisorJob()
        val repo = ProfileRepository(isolated, db, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload {
                beforeDownload()
                return SubscriptionDownload(bytes, "downloaded", SubscriptionInfo())
            }
        }, observationScope = CoroutineScope(observationJob + Dispatchers.IO))
        try {
            val row = Profile.remote(41, "https://example.test/source", "original").copy(lastUpdateAt = 1)
            db.profiles().insert(row.toEntity())
            repo.profileFile(row.id).apply { parentFile!!.mkdirs(); writeBytes(bytes); setLastModified(1_600_000_000_000) }
            action(repo, db, bytes) { beforeDownload = it }
        } finally {
            observationJob.cancelAndJoin()
            db.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("profile-sync-"))
            directory.deleteRecursively()
        }
    }

    @Test fun delayedDownloadMergesLatestMetadataAndSelectedNodeInsteadOfOverwritingThem() = fixture { repo, db, bytes, setDownload ->
        val arrived = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        setDownload { arrived.complete(Unit); release.await() }
        coroutineScope {
            val update = async { repo.updateRemoteProfile(41) }
            arrived.await()
            listOf(
                async { repo.updateProfileLabel(41, "renamed") },
                async { repo.updateProfileSelectedMap(41, mapOf("Main" to "Selected")) },
                async { repo.updateProfileOverrides(41, "{\"dns\":{\"ipv6\":false}}") },
                async { repo.updateProfileAutoUpdate(41, true, 3_600_000) },
                async { repo.updateProfileGroupViewState(41, "Main", setOf("Main")) },
            ).awaitAll()
            release.complete(Unit)
            val result = update.await()
            assertEquals("renamed", result.label)
            assertEquals(mapOf("Main" to "Selected"), decodeSelectedMap(result.selectedMapJson))
            assertTrue(result.autoUpdate)
            assertEquals("Main", result.currentGroupName)
            assertEquals(setOf("Main"), result.unfoldSet)
            assertTrue(result.overridesJson.contains("false"))
            assertEquals(result.toEntity(), db.profiles().getById(41))
            assertArrayEquals(bytes, repo.profileFile(41).readBytes())
        }
    }

    @Test fun staleSourceDeletedRowAndDatabaseFailurePreserveExistingFileAndLatestSettings() = fixture { repo, db, bytes, setDownload ->
        var original = checkNotNull(db.profiles().getById(41))
        val modified = repo.profileFile(41).lastModified()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_sync BEFORE UPDATE ON profiles WHEN NEW.id = 41 BEGIN SELECT RAISE(ABORT, 'isolated failure'); END")
        assertTrue(runCatching { repo.updateRemoteProfile(41) }.isFailure)
        assertEquals(original, db.profiles().getById(41))
        assertEquals(modified, repo.profileFile(41).lastModified())
        assertArrayEquals(bytes, repo.profileFile(41).readBytes())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_sync")
        setDownload { repo.updateRemoteProfileSettings(41, "new-source", "https://example.test/new", false, 60_000, "") }
        assertTrue(runCatching { repo.updateRemoteProfile(41) }.isFailure)
        original = checkNotNull(db.profiles().getById(41))
        assertEquals("https://example.test/new", original.url)
        assertNull(original.lastUpdateError)
        assertEquals(modified, repo.profileFile(41).lastModified())
        setDownload { repo.deleteProfile(41) }
        assertTrue(runCatching { repo.updateRemoteProfile(41) }.isFailure)
        assertNull(db.profiles().getById(41))
        assertFalse(repo.profileFile(41).exists())
    }

    @Test fun olderSameSourceDownloadCannotReplaceNewerCompletedFile() = fixture { repo, db, bytes, setDownload ->
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        setDownload { if (++calls == 1) { entered.complete(Unit); release.await() } }
        coroutineScope {
            val old = async { runCatching { repo.updateRemoteProfile(41) } }
            entered.await()
            val latest = repo.updateRemoteProfile(41)
            val modified = repo.profileFile(41).lastModified()
            release.complete(Unit)
            assertTrue(old.await().isFailure)
            assertEquals(latest.toEntity(), db.profiles().getById(41))
            assertEquals(modified, repo.profileFile(41).lastModified())
            assertArrayEquals(bytes, repo.profileFile(41).readBytes())
        }
    }
}
