package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileRulesEditorTest {
    @Test
    fun `extracts rules and keeps unrelated yaml when replacing`() {
        val source = """
            mixed-port: 7890
            rules:
              - DOMAIN-SUFFIX,example.com,DIRECT
              - MATCH,Proxy
            dns:
              enable: true
        """.trimIndent()

        assertEquals(
            "DOMAIN-SUFFIX,example.com,DIRECT\nMATCH,Proxy",
            ProfileRulesEditor.extractRules(source),
        )
        assertEquals(
            """
            mixed-port: 7890
            rules:
              - DOMAIN,internal.test,DIRECT
            dns:
              enable: true
            """.trimIndent() + "\n",
            ProfileRulesEditor.replaceRules(source, "DOMAIN,internal.test,DIRECT"),
        )
    }

    @Test
    fun `appends a rules section to a custom profile without one`() {
        assertEquals(
            "mixed-port: 7890\nrules:\n  - MATCH,DIRECT\n",
            ProfileRulesEditor.replaceRules("mixed-port: 7890\n", "MATCH,DIRECT"),
        )
    }

    @Test
    fun `rejects sub rule and malformed entries`() {
        val subRuleError = assertThrows(IllegalArgumentException::class.java) {
            ProfileRulesEditor.replaceRules("", "SUB-RULE,local,DIRECT")
        }
        assertEquals("第 1 行不支持 SUB-RULE，请展开为具体规则", subRuleError.message)

        assertThrows(IllegalArgumentException::class.java) {
            ProfileRulesEditor.replaceRules("", "DOMAIN-SUFFIX")
        }
    }
}
