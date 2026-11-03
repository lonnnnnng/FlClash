package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class ProxyGroupIssuesTest {
    private val source = "proxies: [{name: Local, type: direct}]\nproxy-providers: {cloud: {type: file, path: cloud.yaml}}\nproxy-groups: [{name: Main, type: select, proxies: [DIRECT]}, {name: Other, type: fallback, proxies: [Main]}]\nrules: ['DOMAIN,a.test,Main,no-resolve']\n"

    @Test fun missingMembersAreWarningsButBadNamesAndNoSourceBlock() {
        val groups = ProxyGroupDrafts.read(source)
        val missing = groups[0].edit { it.add("proxies", ProxyGroupDrafts.array(listOf("Gone"))); it.add("use", ProxyGroupDrafts.array(listOf("Absent"))) }
        val warnings = ProxyGroupIssues.inspect(missing, listOf(missing, groups[1]), source)
        assertEquals(2, warnings.size); assertTrue(warnings.none { it.blocking })
        val empty = ProxyGroupDraft()
        assertEquals(2, ProxyGroupIssues.inspect(empty, groups + empty, source).count { it.blocking })
        val duplicate = groups[0].edit { it.addProperty("name", "Other") }
        assertTrue(ProxyGroupIssues.inspect(duplicate, listOf(duplicate, groups[1]), source).any { it.message.contains("重复") && it.blocking })
    }

    @Test fun loopsAndInvalidFallbackReportAgainstAffectedGroup() {
        val groups = ProxyGroupDrafts.read(source)
        val main = groups[0].edit { it.add("proxies", ProxyGroupDrafts.array(listOf("Other"))); it.addProperty("empty-fallback", "Other") }
        val issues = ProxyGroupIssues.inspect(main, listOf(main, groups[1]), source)
        assertEquals(2, issues.count { it.blocking })
        assertTrue(issues.any { it.message.contains("Main → Other → Main") })
        val dialerSource = source.replace("type: direct}", "type: direct, dialer-proxy: Main}")
        val local = main.edit { it.add("proxies", ProxyGroupDrafts.array(listOf("Local"))); it.remove("empty-fallback") }
        assertTrue(ProxyGroupIssues.inspect(local, listOf(local, groups[1]), dialerSource).any { it.message.contains("循环引用") })
    }

    @Test fun duplicatesHaveIndependentIdentityAndReserveNamesWithinBatch() {
        val groups = ProxyGroupDrafts.read(source)
        val result = ProxyGroupIssues.duplicate(groups, groups.map { it.key }.toSet(), source)
        assertEquals(listOf("Main", "Main 副本", "Other", "Other 副本"), result.map { it.name })
        assertEquals(result.size, result.map { it.key }.toSet().size)
        assertNull(result[1].originalName); assertNull(result[3].originalName)
        val twice = ProxyGroupIssues.duplicate(result, setOf(groups[0].key), source)
        assertEquals("Main 副本 2", twice[1].name)
        assertEquals(listOf("Main"), twice.last().strings("proxies"))
    }

    @Test fun deletionReportsRulesGroupsAndDialersWithoutSubstringMatches() {
        val groups = ProxyGroupDrafts.read(source)
        assertEquals(listOf("Main"), ProxyGroupIssues.referencedNames(groups, setOf(groups[0].key), source))
        assertTrue(ProxyGroupIssues.referencedNames(groups, setOf(groups[1].key), source).isEmpty())
        val dialer = source.replace("type: direct}", "type: direct, dialer-proxy: Other}")
        assertEquals(listOf("Other"), ProxyGroupIssues.referencedNames(groups, setOf(groups[1].key), dialer))
    }
    @Test fun iconEmbeddingRecognizesFormatsAndRejectsDocumentsAndOversizedImages() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"8\" height=\"8\"/>".toByteArray()
        val encoded = ProfileGroupIconData.encode(svg)
        assertTrue(encoded.startsWith("data:image/svg+xml;base64,"))
        assertArrayEquals(svg, java.util.Base64.getDecoder().decode(encoded.substringAfter(',')))
        assertTrue(runCatching { ProfileGroupIconData.encode("not an image".toByteArray()) }.isFailure)
        assertTrue(runCatching { ProfileGroupIconData.encode(ByteArray(128 * 1024 + 1)) }.isFailure)
    }

}
