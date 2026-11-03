package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.ResourceImportResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ResourceUrlImportControllerTest {
    @Test fun fourConcurrentImportsKeepSuccessSkipDuplicatesAndRetryOnlyFailures() = runTest {
        val active = AtomicInteger(); val max = AtomicInteger(); val started = AtomicInteger()
        val calls = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val firstWave = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val controller = ResourceUrlImportController(this) { input ->
            val count = calls.computeIfAbsent(input.url) { AtomicInteger() }.incrementAndGet()
            val concurrency = active.incrementAndGet(); max.updateAndGet { maxOf(it, concurrency) }
            if (started.incrementAndGet() == 4) firstWave.complete(Unit)
            try {
                release.await()
                if (input.url.endsWith("/2") && count == 1) error("network details must stay private")
                if (input.url.endsWith("/1")) ResourceImportResult.SKIPPED else ResourceImportResult.IMPORTED
            } finally { active.decrementAndGet() }
        }
        controller.start((1..8).joinToString("\n") { "https://example.com/$it" }, "")
        firstWave.await(); assertEquals(4, active.get()); release.complete(Unit)
        controller.state.first { !it.running }
        assertEquals(4, max.get())
        assertEquals(6, controller.state.value.rows.count { it.status == UrlImportStatus.IMPORTED })
        assertEquals(1, controller.state.value.rows.count { it.status == UrlImportStatus.SKIPPED })
        controller.retry(); controller.state.first { !it.running }
        assertEquals(7, controller.state.value.rows.count { it.status == UrlImportStatus.IMPORTED })
        assertEquals(2, calls.getValue("https://example.com/2").get())
        assertEquals(1, calls.getValue("https://example.com/3").get())
    }
    @Test fun cancelMarksQueuedRowsAndRetryPreservesAlreadyCommittedResources() = runTest {
        val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        val calls = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        var blocked = true
        val controller = ResourceUrlImportController(this) { input ->
            calls.computeIfAbsent(input.url) { AtomicInteger() }.incrementAndGet()
            if (input.url.endsWith("/1")) ResourceImportResult.IMPORTED else {
                started.complete(Unit); if (blocked) gate.await(); ResourceImportResult.IMPORTED
            }
        }
        controller.start((1..9).joinToString("\n") { "https://example.com/$it" }, "")
        started.await(); controller.state.first { it.rows.first().status == UrlImportStatus.IMPORTED }
        controller.cancel(); controller.state.first { !it.running }
        assertEquals(8, controller.state.value.rows.count { it.status == UrlImportStatus.CANCELLED })
        blocked = false; controller.retry(); controller.state.first { !it.running }
        assertTrue(controller.state.value.rows.all { it.status == UrlImportStatus.IMPORTED })
        assertEquals(1, calls.getValue("https://example.com/1").get())
    }
    @Test fun invalidBatchDoesNotCallDownloader() = runTest {
        var calls = 0
        val controller = ResourceUrlImportController(this) { calls++; ResourceImportResult.IMPORTED }
        controller.start("https://example.com/valid\nfile:///invalid", "")
        assertEquals(0, calls); assertNotNull(controller.state.value.error)
        assertTrue(controller.state.value.rows.isEmpty())
    }
}
