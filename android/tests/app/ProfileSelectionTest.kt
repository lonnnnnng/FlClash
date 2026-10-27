package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSelectionTest {
    @Test
    fun `selected map json round trip is deterministic`() {
        val json = encodeSelectedMap(
            mapOf(
                "节点选择" to "Tokyo-01",
                "GLOBAL" to "节点选择",
            ),
        )

        assertEquals("{\"GLOBAL\":\"节点选择\",\"节点选择\":\"Tokyo-01\"}", json)
        assertEquals(
            mapOf("节点选择" to "Tokyo-01", "GLOBAL" to "节点选择"),
            decodeSelectedMap(json),
        )
    }

    @Test
    fun `invalid or empty selected map json falls back to empty`() {
        assertTrue(decodeSelectedMap("not-json").isEmpty())
        assertTrue(decodeSelectedMap("{\"GLOBAL\":\"\",\"\":\"node\"}").isEmpty())
    }
}
