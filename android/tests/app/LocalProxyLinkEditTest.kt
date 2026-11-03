package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class LocalProxyLinkEditTest {
    private fun json(text: String) = JsonParser.parseString(text).asJsonObject
    private fun node(id: String, name: String = id, type: String = "ss") = LocalProxy(id,
        """{"name":"$name","type":"$type","server":"node.example","port":443,"password":"fixture","udp":true,"future":{"enabled":true}}""")

    @Test fun unchangedDuplicateLinksConsumeEachOriginalOnceAndOnlyNewLinesArePending() {
        val a = node("a"); val b = node("b")
        val edit = LocalProxyLinkEdit(" same \n\nsame\nsame", listOf(a, b), listOf("same", "same"))
        assertEquals(listOf(a, b, null), edit.lines.map { it.kept })
        assertEquals(listOf(4), edit.pending.map { it.number })
        assertTrue(edit.droppedLinks.isEmpty())
        val result = edit.apply(mapOf(4 to listOf(a.fields)), emptySet(), emptyMap())
        assertEquals(listOf(a, b), result.take(2))
        assertEquals("a-01", result.last().name)
        assertNotEquals(a.id, result.last().id)
    }

    @Test fun unsupportedProtocolsStayAtNearestKeptAnchorWhenOthersMoveOrDisappear() {
        val rows = listOf(node("prefix"), node("a"), node("after-a"), node("b"), node("after-b"), node("c"), node("tail"))
        val edit = LocalProxyLinkEdit("b\nnew\na", rows, listOf("", "a", "", "b", "", "c", ""))
        val result = edit.apply(mapOf(2 to listOf(node("new").fields)), emptySet(), emptyMap())
        assertEquals(listOf("prefix", "b", "after-b", "tail", "new", "a", "after-a"), result.map { it.name })
        assertEquals(mapOf("c" to "c"), edit.droppedLinks)
        assertEquals(rows[0], result[0]); assertEquals(rows[4], result[2]); assertEquals(rows[6], result[3])
    }

    @Test fun sameNameReplacementKeepsIdAndOnlyUnlinkedNestedFields() {
        val old = LocalProxy("stable", """{"name":"A","type":"ss","password":"old","udp":true,"tls":true,"opts":{"path":"/old","private":true},"future":123}""")
        val carried = json("""{"name":"A","type":"ss","password":"old","tls":true,"opts":{"path":"/old"}}""")
        val incoming = json("""{"name":"A","type":"ss","password":"new","opts":{"path":"/new"}}""")
        val before = incoming.toString()
        val edit = LocalProxyLinkEdit("changed", listOf(old), listOf("original"))
        val result = edit.apply(mapOf(1 to listOf(incoming)), emptySet(), mapOf(old.id to carried)).single()
        assertEquals("stable", result.id); assertEquals("new", result.fields["password"].asString)
        assertTrue(result.fields["udp"].asBoolean); assertFalse(result.fields.has("tls"))
        assertTrue(result.fields.getAsJsonObject("opts")["private"].asBoolean)
        assertEquals("/new", result.fields.getAsJsonObject("opts")["path"].asString)
        assertEquals(123, result.fields["future"].asInt)
        assertEquals(before, incoming.toString())
        assertEquals("old", old.fields["password"].asString)
    }

    @Test fun renamedBatchLineIsNewAndConflictsCannotTakeOverDroppedIdentity() {
        val old = node("old", "A")
        val edit = LocalProxyLinkEdit("changed\nreserved\nunnamed", listOf(old), listOf("original"))
        val result = edit.apply(mapOf(1 to listOf(node("x", "B").fields),
            2 to listOf(node("x", "DIRECT").fields), 3 to listOf(node("x", "-01").fields)), setOf("B", "B-01", "node.example"), emptyMap())
        assertEquals(listOf("B-02", "DIRECT-01", "node.example-01"), result.map { it.name })
        assertTrue(result.none { it.id == old.id })
        val conflict = LocalProxyLinkEdit("changed", listOf(old), listOf("original"))
            .apply(mapOf(1 to listOf(old.fields)), setOf("A"), mapOf(old.id to old.fields)).single()
        assertNotEquals(old.id, conflict.id); assertEquals("A-01", conflict.name)
    }

    @Test fun singleRenamePreservesUnlinkedOptionsAvoidsOtherNodesAndKeepsDraftId() {
        val old = node("draft", "Old")
        val next = old.fields.apply { addProperty("name", "Taken"); remove("future"); remove("udp") }
        val linked = old.fields.apply { remove("future"); remove("udp") }
        val result = fillLocalProxyFromLink(next, old, linked, listOf(old, node("other", "Taken")), setOf("Taken-01"))
        assertEquals(old.id, result.id); assertEquals("Taken-02", result.name)
        assertTrue(result.fields["udp"].asBoolean); assertTrue(result.fields.has("future"))
        val unnamed = next.deepCopy().apply { addProperty("name", "") }
        assertEquals("Old", fillLocalProxyFromLink(unnamed, old, linked, listOf(old), emptySet()).name)
    }

    @Test fun noDecodedBaselineOrDifferentProtocolCannotCarryStaleFieldsAndEmptyBulkKeepsUnsupported() {
        val old = node("a")
        val incoming = json("""{"name":"A","type":"ss"}""")
        assertEquals(incoming, withUnlinkedProxyFields(incoming, old.fields, null))
        val different = json("""{"name":"A","type":"http"}""")
        assertEquals(different, withUnlinkedProxyFields(different, old.fields, old.fields))
        val unsupported = node("b", type = "direct")
        val edit = LocalProxyLinkEdit(" \n ", listOf(old, unsupported), listOf("old", ""))
        assertEquals(listOf(unsupported), edit.apply(emptyMap(), emptySet(), emptyMap()))
        assertTrue(runCatching { LocalProxyLinkEdit("", listOf(old), emptyList()) }.isFailure)
        assertTrue(runCatching { LocalProxyLinkEdit("new", listOf(old), listOf("old")).apply(emptyMap(), emptySet(), emptyMap()) }.isFailure)
    }
}
