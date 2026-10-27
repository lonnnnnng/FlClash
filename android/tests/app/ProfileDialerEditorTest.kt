package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDialerEditorTest {
    @Test
    fun `parse and render keep mappings and ignore comments`() {
        val mappings = ProfileDialerEditor.parseDialers(
            """
            # 通过代理名选择出站目标
            node-a -> "Relay Group" # 行尾说明
            node-b = node-a
            """.trimIndent(),
        )

        assertEquals(
            mapOf("node-a" to "Relay Group", "node-b" to "node-a"),
            mappings,
        )
        assertEquals("node-a -> Relay Group\nnode-b -> node-a", ProfileDialerEditor.renderDialers(mappings))
    }

    @Test
    fun `extract reads quoted dialer fields without changing source`() {
        val source = """
            proxies:
              - name: node-a
                type: vmess
                dialer-proxy: "Relay Group" # preserve source elsewhere
              - name: node-b
                type: trojan
            rules:
              - MATCH,DIRECT
        """.trimIndent()

        assertEquals(
            mapOf("node-a" to "Relay Group"),
            ProfileDialerEditor.extractDialers(source),
        )
        assertTrue(source.contains("dialer-proxy: \"Relay Group\""))
    }

    @Test
    fun `parse rejects malformed and duplicate mappings`() {
        val malformed = runCatching { ProfileDialerEditor.parseDialers("node-a") }.exceptionOrNull()
        val duplicate = runCatching {
            ProfileDialerEditor.parseDialers("node-a -> first\nnode-a -> second")
        }.exceptionOrNull()

        assertTrue(malformed is IllegalArgumentException)
        assertTrue(duplicate is IllegalArgumentException)
    }
}
