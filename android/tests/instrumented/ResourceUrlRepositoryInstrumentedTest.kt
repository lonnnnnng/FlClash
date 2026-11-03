package com.lonnnnnng.flclash.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.core.RuleSetInfo
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ResourceUrlRepositoryInstrumentedTest {
    @Test fun concurrentIconImportsDeduplicateAtCommitAndKeepExistingContentOnRepeat() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KClashDatabase::class.java).build()
        val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>(); val count = java.util.concurrent.atomic.AtomicInteger()
        val repository = IconSetRepository(database, object : ScriptClient {
            override suspend fun download(url: String): ScriptDownload {
                if (count.incrementAndGet() == 2) started.complete(Unit)
                gate.await()
                return ScriptDownload("{\"icons\":[{\"name\":\"A\",\"url\":\"https://example.com/a.svg\"}]}".toByteArray(), null)
            }
        })
        try {
            val input = NamedResourceUrl("https://example.com/gallery.json")
            val jobs = (1..2).map { async(Dispatchers.IO) { repository.importUrlIfNew(input) } }
            started.await(); gate.complete(Unit)
            assertEquals(setOf(ResourceImportResult.IMPORTED, ResourceImportResult.SKIPPED), jobs.awaitAll().toSet())
            val row = repository.all().single()
            assertEquals(ResourceImportResult.SKIPPED, repository.importUrlIfNew(input))
            assertEquals(2, count.get()); assertEquals(listOf(row), repository.all())
            repository.importUrlIfNew(NamedResourceUrl("https://example.com/other/gallery.json"))
            assertEquals(listOf("gallery", "gallery (2)"), repository.all().map { it.name })
        } finally { database.close() }
    }
    @Test fun concurrentRuleImportsDiscardOnlyUncommittedStagingAndSkipExistingUrl() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "rule-url-import-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = root }
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>(); val count = java.util.concurrent.atomic.AtomicInteger()
        val repository = RuleResourceRepository(isolated, database, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload {
                if (count.incrementAndGet() == 2) started.complete(Unit)
                gate.await()
                return SubscriptionDownload("payload:\n  - DOMAIN,example.test\n".toByteArray(), null, SubscriptionInfo())
            }
        }, object : RuleResourceCompiler {
            override suspend fun compile(name: String) = RuleSetInfo("classical", "yaml")
            override suspend fun preview(path: String) = ""
        })
        try {
            val input = NamedResourceUrl("https://example.com/rules.yaml")
            val jobs = (1..2).map { async(Dispatchers.IO) { repository.importUrlIfNew(input) } }
            started.await(); gate.complete(Unit)
            assertEquals(setOf(ResourceImportResult.IMPORTED, ResourceImportResult.SKIPPED), jobs.awaitAll().toSet())
            val row = repository.all().single()
            assertTrue(repository.file(row).isFile)
            assertEquals(1, File(root, "mihomo/providers/rules").listFiles()!!.size)
            assertEquals(ResourceImportResult.SKIPPED, repository.importUrlIfNew(input)); assertEquals(2, count.get())
        } finally { database.close(); root.deleteRecursively() }
    }
}
