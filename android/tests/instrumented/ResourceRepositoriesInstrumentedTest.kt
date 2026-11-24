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
