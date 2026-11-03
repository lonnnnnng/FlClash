package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ResourceRepositoriesInstrumentedTest {
    @Test fun iconGallerySyncKeepsRenameAndRejectsDeletedChangedOrInvalidVersions() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val start = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var blocking = false
        var content = """{"name":"远程图库","icons":[{"name":"A","url":"https://example.com/a.png"}]}"""
        val repository = IconSetRepository(database, object : ScriptClient {
            override suspend fun download(url: String): ScriptDownload {
                if (blocking) { start.complete(Unit); finish.await() }
                return ScriptDownload(content.toByteArray(), null)
            }
        })
        try {
            val row = repository.importUrl("https://example.com/icons.json")
            blocking = true
            val job = async(Dispatchers.IO) { repository.sync(row) }
            start.await(); repository.rename(row.id, "我的图库"); finish.complete(Unit)
            val synced = job.await()
            assertEquals("我的图库", synced.name)
            assertEquals(row.id, synced.id)
            content = "{}"
            assertTrue(runCatching { repository.sync(synced) }.isFailure)
            assertEquals(synced, repository.all().single())
            content = """{"icons":[{"url":"https://example.com/b.png"}]}"""
            val changed = repository.options(synced, "改源", "https://example.com/next.json")
            assertTrue(runCatching { repository.sync(synced) }.isFailure)
            assertEquals("https://example.com/next.json", repository.all().single().url)
            repository.delete(row.id)
            assertTrue(runCatching { repository.sync(synced) }.isFailure)
            assertTrue(repository.all().isEmpty())
        } finally { database.close() }
    }

    @Test fun scriptSyncCannotOverwriteConcurrentEditOrResurrectDeletedScript() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "resource-scripts-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        var gate: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
        val repository = ScriptRepository(isolated, database, object : ScriptClient {
            override suspend fun download(url: String): ScriptDownload {
                gate?.let { it.first.complete(Unit); it.second.await() }
                return ScriptDownload("remote".toByteArray(), null)
            }
        })
        try {
            val row = repository.createRemote("脚本", "https://example.com/script.js")
            val first = CompletableDeferred<Unit>() to CompletableDeferred<Unit>(); gate = first
            val download = async(Dispatchers.IO) { runCatching { repository.sync(row.id) } }
            first.first.await(); repository.update(row.id, "编辑后", "edited"); first.second.complete(Unit)
            assertTrue(download.await().isFailure)
            assertEquals("edited", repository.readContent(row.id))
            val second = CompletableDeferred<Unit>() to CompletableDeferred<Unit>(); gate = second
            val deleted = async(Dispatchers.IO) { runCatching { repository.sync(row.id) } }
            second.first.await(); repository.delete(row.id); second.second.complete(Unit)
            assertTrue(deleted.await().isFailure)
            assertFalse(repository.scriptFile(row.id).exists())
            assertNull(database.scripts().getById(row.id))
        } finally { database.close() }
    }
}
