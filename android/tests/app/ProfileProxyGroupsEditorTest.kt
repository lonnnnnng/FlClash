package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileProxyGroupsEditorTest {
    @Test
    fun `extracts proxy groups and keeps unrelated yaml when replacing`() {
        val source = """
            mixed-port: 7890
            proxy-groups:
              - name: Old
                type: select
                proxies:
                  - DIRECT
            rules:
              - MATCH,Old
        """.trimIndent()

        assertEquals(
            "- name: Old\n  type: select\n  proxies:\n    - DIRECT",
            ProfileProxyGroupsEditor.extractGroups(source),
        )
        assertEquals(
            """
            mixed-port: 7890
            proxy-groups:
              - name: Auto
                type: url-test
                proxies:
                  - DIRECT
            rules:
              - MATCH,Old
            """.trimIndent() + "\n",
            ProfileProxyGroupsEditor.replaceGroups(
                source,
                "- name: Auto\n  type: url-test\n  proxies:\n    - DIRECT",
            ),
        )
    }

    @Test
    fun `appends proxy groups and accepts a pasted wrapper`() {
        assertEquals(
            "mixed-port: 7890\nproxy-groups:\n  - name: Select\n    type: select\n    proxies:\n      - node\n",
            ProfileProxyGroupsEditor.replaceGroups(
                "mixed-port: 7890\n",
                "proxy-groups:\n  - name: Select\n    type: select\n    proxies:\n      - node",
            ),
        )
        assertEquals(
            "mixed-port: 7890\nproxy-groups: []\n",
            ProfileProxyGroupsEditor.replaceGroups("mixed-port: 7890\n", ""),
        )
    }

    @Test
    fun `rejects malformed proxy groups and duplicate names`() {
        val missingType = assertThrows(IllegalArgumentException::class.java) {
            ProfileProxyGroupsEditor.replaceGroups("", "- name: Select")
        }
        assertEquals("第 1 个代理组 type 不受支持：", missingType.message)

        val unsupportedType = assertThrows(IllegalArgumentException::class.java) {
            ProfileProxyGroupsEditor.replaceGroups("", "- name: Select\n  type: random")
        }
        assertEquals("第 1 个代理组 type 不受支持：random", unsupportedType.message)

        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            ProfileProxyGroupsEditor.replaceGroups(
                "",
                "- name: Select\n  type: select\n- name: Select\n  type: fallback",
            )
        }
        assertEquals("代理组名称重复：Select", duplicate.message)

        val relay = assertThrows(IllegalArgumentException::class.java) {
            ProfileProxyGroupsEditor.replaceGroups("", "- name: Relay\n  type: relay")
        }
        assertEquals("第 1 个代理组 type 不受支持：relay", relay.message)
    }

    @Test
    fun `normalizes quoted fields and preserves boundary comments`() {
        val source = """
            mixed-port: 7890
            proxy-groups:
              - name: Old
                type: select

            # DNS section follows proxy groups
            dns:
              enable: true
        """.trimIndent()

        assertEquals(
            "- name: Old\n  type: select",
            ProfileProxyGroupsEditor.extractGroups(source),
        )
        assertEquals(
            "mixed-port: 7890\nproxy-groups:\n  - name: \"Auto\"\n    type: select # keep type\n\n# DNS section follows proxy groups\ndns:\n  enable: true\n",
            ProfileProxyGroupsEditor.replaceGroups(
                source,
                "- name: \"Auto\"\n  type: select # keep type",
            ),
        )
    }
}
