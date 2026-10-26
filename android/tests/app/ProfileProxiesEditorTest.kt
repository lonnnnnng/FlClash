package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileProxiesEditorTest {
    @Test
    fun `extracts proxy list and keeps unrelated yaml when replacing`() {
        val source = """
            mixed-port: 7890
            proxies:
              - name: old-node
                type: vmess
                server: old.example.com
            rules:
              - MATCH,DIRECT
        """.trimIndent()

        assertEquals(
            "- name: old-node\n  type: vmess\n  server: old.example.com",
            ProfileProxiesEditor.extractProxies(source),
        )
        assertEquals(
            """
            mixed-port: 7890
            proxies:
              - name: new-node
                type: trojan
                server: new.example.com
            rules:
              - MATCH,DIRECT
            """.trimIndent() + "\n",
            ProfileProxiesEditor.replaceProxies(
                source,
                "- name: new-node\n  type: trojan\n  server: new.example.com",
            ),
        )
    }

    @Test
    fun `appends proxy section and accepts a pasted proxies wrapper`() {
        assertEquals(
            "mixed-port: 7890\nproxies:\n  - name: node\n    type: socks5\n",
            ProfileProxiesEditor.replaceProxies(
                "mixed-port: 7890\n",
                "proxies:\n  - name: node\n    type: socks5",
            ),
        )
        assertEquals(
            "mixed-port: 7890\nproxies: []\n",
            ProfileProxiesEditor.replaceProxies("mixed-port: 7890\n", ""),
        )
    }

    @Test
    fun `rejects malformed proxy entries and duplicate names`() {
        val missingType = assertThrows(IllegalArgumentException::class.java) {
            ProfileProxiesEditor.replaceProxies("", "- name: node")
        }
        assertEquals("第 1 个代理缺少 type", missingType.message)

        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            ProfileProxiesEditor.replaceProxies(
                "",
                "- name: node\n  type: vmess\n- name: node\n  type: trojan",
            )
        }
        assertEquals("代理名称重复：node", duplicate.message)

        assertThrows(IllegalArgumentException::class.java) {
            ProfileProxiesEditor.replaceProxies("", "name: node\n  type: vmess")
        }
    }
}
