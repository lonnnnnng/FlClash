package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：正文验收只使用授权原1.yaml原字节，在隔离目录内提交；不访问订阅服务器或写正式Profile。 */
class ProfileDocumentRepositoryInstrumentedTest {
    private fun fixture(action: suspend (ProfileRepository, KClashDatabase, ByteArray) -> Unit) = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = readAuthorizedProxyProfile()
        val directory = File(context.cacheDir, "profile-document-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repo = ProfileRepository(isolated, db, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("本地副本保存不得下载订阅")
        }, contentDecoder = ProfileContentDecoder { content, key -> if (key == "bad") error("decode failed") else content })
        try {
            for (row in listOf(Profile.remote(51, "https://example.test/sub", "remote").copy(autoUpdate = true, lastUpdateAt = 1),
                Profile.custom(52, "file").copy(type = ProfileType.FILE, lastUpdateAt = 1))) {
                db.profiles().insert(row.toEntity())
                repo.profileFile(row.id).apply { parentFile!!.mkdirs(); writeBytes(bytes); setLastModified(1_600_000_000_000) }
            }
            action(repo, db, bytes)
        } finally {
            db.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("profile-document-"))
            directory.deleteRecursively()
        }
    }

    @Test fun remoteAndFileDocumentSavesMergeLiveSelectionAndOnlyCommitAtOuterSave() = fixture { repo, db, bytes ->
        for (id in listOf(51L, 52L)) {
            val document = repo.readEditableProfileDocument(id)
            val fileTime = repo.profileFile(id).lastModified()
            repo.updateProfileSelectedMap(id, mapOf("Main" to "Picked"))
            repo.updateProfileGroupViewState(id, "Main", setOf("Main"))
            repo.updateProfileOverrides(id, "{\"dns\":{\"ipv6\":false}}")
            assertEquals(fileTime, repo.profileFile(id).lastModified())
            val saved = repo.saveProfileDocument(document, document.profile.documentSettings().copy(label = "saved", autoUpdate = false), bytes) { true }
            assertEquals("saved", saved.label)
            assertEquals(document.profile.type, saved.type)
            assertEquals(document.profile.url, saved.url)
            assertEquals(mapOf("Main" to "Picked"), decodeSelectedMap(saved.selectedMapJson))
            assertEquals("Main", saved.currentGroupName)
            assertEquals(setOf("Main"), saved.unfoldSet)
            assertTrue(saved.overridesJson.contains("false"))
            assertFalse(saved.autoUpdate)
            assertArrayEquals(bytes, repo.profileFile(id).readBytes())
            assertEquals(saved.toEntity(), db.profiles().getById(id))
            assertTrue(runCatching { repo.saveProfileDocument(document, document.profile.documentSettings(), bytes) { true } }.isFailure)
        }
    }

    @Test fun invalidDecodeDatabaseFailureAndLateGuardKeepOriginalBodyMetadataAndTimestamp() = fixture { repo, db, bytes ->
        val document = repo.readEditableProfileDocument(51)
        val before = db.profiles().getById(51)
        val settings = document.profile.documentSettings().copy(label = "cannot-half-save")
        suspend fun preserved() {
            assertEquals(before, db.profiles().getById(51))
            assertArrayEquals(bytes, repo.profileFile(51).readBytes())
            assertEquals(document.modified, repo.profileFile(51).lastModified())
        }
        assertTrue(runCatching { repo.saveProfileDocument(document, settings.copy(ageSecretKey = "bad"), bytes) { true } }.isFailure)
        preserved()
        assertTrue(runCatching { repo.saveProfileDocument(document, settings, byteArrayOf(0xc3.toByte(), 0x28)) { true } }.isFailure)
        preserved()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_document BEFORE UPDATE ON profiles WHEN NEW.id = 51 BEGIN SELECT RAISE(ABORT, 'isolated failure'); END")
        assertTrue(runCatching { repo.saveProfileDocument(document, settings, bytes) { true } }.isFailure)
        preserved()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_document")
        var guards = 0
        assertTrue(runCatching { repo.saveProfileDocument(document, settings, bytes) { ++guards < 2 } }.isFailure)
        preserved()
        repo.updateRemoteProfileSettings(51, "external", "https://example.test/new-source", true, 3_600_000, "")
        val changed = db.profiles().getById(51)
        assertTrue(runCatching { repo.saveProfileDocument(document, settings, bytes) { true } }.isFailure)
        assertEquals(changed, db.profiles().getById(51))
        assertEquals(document.modified, repo.profileFile(51).lastModified())
    }

    @Test fun metadataOnlyDoesNotRewriteBodyAndChangedFileCannotBeSavedFromOldEditor() = fixture { repo, db, bytes ->
        val document = repo.readEditableProfileDocument(51)
        repo.saveProfileDocument(document, document.profile.documentSettings().copy(label = "metadata"), null) { true }
        assertEquals(document.modified, repo.profileFile(51).lastModified())
        assertArrayEquals(bytes, repo.profileFile(51).readBytes())
        val next = repo.readEditableProfileDocument(51)
        repo.profileFile(51).setLastModified(next.modified + 1_000)
        assertTrue(runCatching { repo.saveProfileDocument(next, next.profile.documentSettings(), bytes) { true } }.isFailure)
        assertEquals("metadata", db.profiles().getById(51)?.label)
        assertArrayEquals(bytes, repo.profileFile(51).readBytes())
    }
}
