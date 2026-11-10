package com.lonnnnnng.flclash.common

import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class StateWriteAuditTest {
    private class Collector : StateWriteAudit.Sink {
        val records = Collections.synchronizedList(mutableListOf<Map<String, Any?>>())
        override fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        override fun append(record: Map<String, Any?>) { records += record }
        override fun dropped() = Unit
    }
    @After fun stop() { StateWriteAudit.install(null) }
    @Test fun concurrentOriginsSurviveDispatchAndNestedScopeKeepsBusinessSource() = runBlocking {
        val sink = Collector(); StateWriteAudit.install(sink)
        coroutineScope { (1..20).map { n -> async(Dispatchers.Default) {
            StateWriteAudit.operation("source.$n") {
                delay(1)
                withContext(Dispatchers.IO) { StateWriteAudit.operation("dao.fallback") { StateWriteAudit.event("value", mapOf("n" to n)) } }
            }
        } }.awaitAll() }
        val values = sink.records.filter { it["kind"] == "value" }
        assertEquals(20, values.size)
        values.forEach { assertEquals("source.${it["n"]}", it["source"]) }
        assertEquals(20, values.map { it["operation"] }.toSet().size)
        StateWriteAudit.event("outside"); assertNull(sink.records.last()["source"])
    }
    @Test fun failingSinkNeverChangesBusinessResultOrSwallowsCancellation() = runBlocking {
        StateWriteAudit.install(object : StateWriteAudit.Sink {
            override fun digest(bytes: ByteArray): String = error("sink failed")
            override fun append(record: Map<String, Any?>) { error("sink failed") }
            override fun dropped() { error("sink failed") }
        })
        assertEquals(42, StateWriteAudit.operation("source.test") { 42 })
        var propagated = false
        try { StateWriteAudit.operation("source.cancel") { throw CancellationException("private") } }
        catch (_: CancellationException) { propagated = true }
        assertTrue(propagated)
    }
    @Test fun fileFailureRetainsBeforeAndAfterWithoutContent() {
        val sink = Collector(); StateWriteAudit.install(sink)
        val file = Files.createTempFile("state-audit-", ".bin").toFile()
        try {
            file.writeText("private-old")
            try { StateWriteAudit.fileMutation(file, "test.replace") { file.writeText("private-new"); error("private-error") } }
            catch (_: IllegalStateException) { }
            val failure = sink.records.last(); assertEquals("file_failed", failure["kind"])
            assertNotEquals((failure["before"] as Map<*, *>)["content"], (failure["after"] as Map<*, *>)["content"])
            assertFalse(failure.toString().contains("private-"))
        } finally { assertTrue(file.delete()) }
    }
}
