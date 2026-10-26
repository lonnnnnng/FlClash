package com.lonnnnnng.flclash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileProvidersEditorTest {
    @Test
    fun `extracts and replaces both provider maps while keeping other yaml`() {
        val source = """
            mixed-port: 7890
            proxy-providers:
              cloud:
                type: http
                url: https://example.com/cloud
                path: ./cloud.yaml
            rule-providers:
              ads:
                type: http
                behavior: domain
                url: https://example.com/ads
                path: ./ads.yaml
            rules:
              - RULE-SET,ads,REJECT
        """.trimIndent()

        val sections = ProfileProvidersEditor.extract(source)
        assertEquals(
            "cloud:\n  type: http\n  url: https://example.com/cloud\n  path: ./cloud.yaml",
            sections.proxyProviders,
        )
        assertEquals(
            "ads:\n  type: http\n  behavior: domain\n  url: https://example.com/ads\n  path: ./ads.yaml",
            sections.ruleProviders,
        )
        assertEquals(
            """
                mixed-port: 7890
                proxy-providers:
                  mobile:
                    type: file
                    path: ./mobile.yaml
                rule-providers: {}
                rules:
                  - RULE-SET,ads,REJECT
            """.trimIndent() + "\n",
            ProfileProvidersEditor.replace(
                source,
                ProfileProvidersEditor.Sections(
                    proxyProviders = "mobile:\n  type: file\n  path: ./mobile.yaml",
                    ruleProviders = "",
                ),
            ),
        )
    }

    @Test
    fun `accepts wrapped maps and appends missing sections`() {
        assertEquals(
            "mixed-port: 7890\nproxy-providers:\n  cloud:\n    type: http\n    url: https://example.com/cloud\n",
            ProfileProvidersEditor.replace(
                "mixed-port: 7890\n",
                ProfileProvidersEditor.Sections(
                    proxyProviders = "proxy-providers:\n  cloud:\n    type: http\n    url: https://example.com/cloud",
                ),
            ),
        )
    }

    @Test
    fun `writes empty maps and preserves boundary comments`() {
        val source = """
            proxy-providers:
              cloud:
                type: http

            # DNS follows provider definitions
            dns:
              enable: true
        """.trimIndent()
        assertEquals(
            "proxy-providers: {}\n\n# DNS follows provider definitions\ndns:\n  enable: true\n",
            ProfileProvidersEditor.replace(source, ProfileProvidersEditor.Sections()),
        )
    }

    @Test
    fun `rejects scalar entries and duplicate provider names`() {
        val scalar = assertThrows(IllegalArgumentException::class.java) {
            ProfileProvidersEditor.replace(
                "",
                ProfileProvidersEditor.Sections(proxyProviders = "cloud: http"),
            )
        }
        assertEquals("第 1 行 Provider 条目必须使用嵌套 map", scalar.message)

        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            ProfileProvidersEditor.replace(
                "",
                ProfileProvidersEditor.Sections(
                    proxyProviders = "cloud:\n  type: http\ncloud:\n  type: file",
                ),
            )
        }
        assertEquals("Provider 名称重复：cloud", duplicate.message)
    }
}
