package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileProxiesEditorTest {
    @Test
    fun `top level flow lists keep nodes after opening and saving`() {
        val source = "proxies: [{name: flow, type: http}]\nrules: []\n"
        val extracted = ProfileProxiesEditor.extractProxies(source)
        assertEquals("[{name: flow, type: http}]", extracted)
        val saved = ProfileProxiesEditor.replaceProxies(source, extracted)
        assertEquals(ProxyGroupDrafts.root(source), ProxyGroupDrafts.root(saved))
        val multiline = "proxies: [\n  {name: flow, type: http}\n]\nrules: []\n"
        assertEquals(ProxyGroupDrafts.root(multiline), ProxyGroupDrafts.root(
            ProfileProxiesEditor.replaceProxies(multiline, ProfileProxiesEditor.extractProxies(multiline)),
        ))
    }

    @Test
    fun `commented wrappers retain list indentation and comments`() {
        val saved = ProfileProxiesEditor.replaceProxies(
            "rules: []\n", "# keep leading\nproxies: # keep wrapper\n  - {name: wrapped, type: http}\n",
        )
        assertEquals(1, ProxyGroupDrafts.root(saved).getAsJsonArray("proxies").size())
        org.junit.Assert.assertTrue(saved.contains("# keep leading"))
        org.junit.Assert.assertTrue(saved.contains("# keep wrapper"))
    }

    @Test
    fun `flow maps quoted names nested fields and aliases retain yaml semantics`() {
        val source = """
            defaults: &transport {type: trojan, server: example.test}
            proxies:
              - {name: 'flow:a#b', <<: *transport, password: test-only, headers: {name: nested}}
              - name: block
                type: http
                headers:
                  name: nested
                  type: ignored
            rules: [MATCH,DIRECT]
        """.trimIndent() + "\n"
        val extracted = ProfileProxiesEditor.extractProxies(source)
        assertEquals(source, ProfileProxiesEditor.replaceProxies(source, extracted))
        val edited = extracted + "\n# saved flow-map nodes"
        assertEquals(source.replace("rules: [MATCH,DIRECT]", "  # saved flow-map nodes\nrules: [MATCH,DIRECT]"),
            ProfileProxiesEditor.replaceProxies(source, edited))
    }

    @Test
    fun `semantic duplicate names reject mixed flow and block entries`() {
        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            ProfileProxiesEditor.replaceProxies("rules: []", "- {name: 'same', type: http}\n- name: same\n  type: trojan")
        }
        assertEquals("代理名称重复：same", duplicate.message)
    }

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
