package com.lonnnnnng.flclash.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ProxyGroupDraftsTest {
    @Test fun flowRootAndQuotedSectionKeysDoNotProduceDuplicateYamlKeys() {
        for (content in listOf("{proxy-groups: [{name: Old, type: select, proxies: [DIRECT]}], rules: ['MATCH,Old']}",
            "'proxy-groups':\n  - {name: Old, type: select, proxies: [DIRECT]}\nrules: ['MATCH,Old']\n")) {
            val groups = ProxyGroupDrafts.read(content).map { it.edit { fields -> fields.addProperty("name", "New") } }
            val result = ProxyGroupDrafts.root(ProxyGroupDrafts.apply(content, ProxyGroupsSave(groups)))
            assertEquals("New", result.getAsJsonArray("proxy-groups").single().asJsonObject.get("name").asString)
            assertEquals("MATCH,New", result.getAsJsonArray("rules").single().asString)
        }
    }

    private val source = """
        # global settings stay byte for byte
        mixed-port: 7890 # listener
        proxies:
          - {name: node, type: direct}
        proxy-groups:
          - name: Main
            type: select
            proxies: [DIRECT]
            extra: {future: yes}
          - name: Other
            type: fallback
            proxies: [Main]
        rules:
          - DOMAIN,Main,Main,no-resolve
          - MATCH,Other

        # DNS boundary
        dns:
          enable: false # leave this comment
    """.trimIndent() + "\n"

    @Test fun renameRewritesOnlyTargetsAndPreservesUnchangedSectionsAndUnknownFields() {
        val groups = ProxyGroupDrafts.read(source)
        val updated = groups.map { if (it.name == "Main") it.edit { fields -> fields.addProperty("name", "Renamed") } else it }
        val result = ProxyGroupDrafts.apply(source, ProxyGroupsSave(updated))
        val root = ProxyGroupDrafts.root(result)
        assertEquals("Renamed", root.getAsJsonArray("proxy-groups")[1].asJsonObject.getAsJsonArray("proxies")[0].asString)
        assertEquals("DOMAIN,Main,Renamed,no-resolve", root.getAsJsonArray("rules")[0].asString)
        assertTrue(root.getAsJsonArray("proxy-groups")[0].asJsonObject.has("extra"))
        assertTrue(result.startsWith("# global settings stay byte for byte\nmixed-port: 7890 # listener\n"))
        assertTrue(result.endsWith("# DNS boundary\ndns:\n  enable: false # leave this comment\n"))
    }

    @Test fun providerAndProxyInterleavingRoundTripsWithExplicitUsePositions() {
        val group = ProxyGroupDraft().edit { it.addProperty("name", "Mixed") }
        val members = listOf(ProxyGroupMember("first", true), ProxyGroupMember("DIRECT"), ProxyGroupMember("second", true), ProxyGroupMember("REJECT"))
        val updated = ProxyGroupDrafts.withMembers(group, members)
        assertEquals(members, ProxyGroupDrafts.members(updated))
        assertEquals(0, updated.fields.getAsJsonObject("use-position").get("first").asInt)
        assertEquals(1, updated.fields.getAsJsonObject("use-position").get("second").asInt)
        assertFalse(ProxyGroupDrafts.withMembers(updated, members.filterNot { it.provider }).fields.has("use-position"))
    }

    @Test fun emptyReservedDuplicateAndCyclicGroupsAreRejected() {
        val groups = ProxyGroupDrafts.read(source)
        for (name in listOf("", "DIRECT", "Other", "node")) assertTrue(runCatching {
            ProxyGroupDrafts.apply(source, ProxyGroupsSave(groups.map { if (it.name == "Main") it.edit { fields -> fields.addProperty("name", name) } else it }))
        }.isFailure)
        val cycle = groups.map { if (it.name == "Main") it.edit { fields -> fields.add("proxies", ProxyGroupDrafts.array(listOf("Other"))) } else it }
        assertTrue(runCatching { ProxyGroupDrafts.apply(source, ProxyGroupsSave(cycle)) }.isFailure)
        assertTrue(runCatching { ProxyGroupDrafts.validate(listOf(ProxyGroupDraft().edit { it.addProperty("name", "Empty") }), source) }.isFailure)
    }

    @Test fun fallbackCannotPointToGroupAndChainLoopsAreRejected() {
        val groups = ProxyGroupDrafts.read(source)
        assertTrue(runCatching { ProxyGroupDrafts.apply(source, ProxyGroupsSave(groups.map { it.edit { fields -> fields.addProperty("empty-fallback", "Main") } })) }.isFailure)
        val withDialer = source.replace("type: direct}", "type: direct, dialer-proxy: Main}")
        val loop = groups.map { if (it.name == "Main") it.edit { fields -> fields.add("proxies", ProxyGroupDrafts.array(listOf("node"))) } else it }
        assertTrue(runCatching { ProxyGroupDrafts.apply(withDialer, ProxyGroupsSave(loop)) }.isFailure)
    }

    @Test fun quickYamlPreservesUnknownFieldsAndExplicitFalseAndZero() {
        val group = ProxyGroupDrafts.read(source).first()
        val edited = ProxyGroupDrafts.definition("name: Main\ntype: url-test\nproxies: [DIRECT]\nlazy: false\ntolerance: 0\nfuture: [a,b]\n", group)
        assertEquals(group.key, edited.key)
        assertEquals(group.originalName, edited.originalName)
        assertFalse(edited.flag("lazy"))
        assertEquals("0", edited.text("tolerance"))
        assertFalse(edited.fields.has("proxy-providers"))
        assertTrue(ProxyGroupDrafts.apply(source, ProxyGroupsSave(listOf(edited))).contains("future:"))
    }

    @Test fun persistedSelectionsAndOverrideTargetsFollowRenameWithoutClearingOtherSettings() {
        val groups = ProxyGroupDrafts.read(source).map { if (it.name == "Main") it.edit { fields -> fields.addProperty("name", "New") } else it }
        val profile = Profile.custom(1, "Keep").copy(scriptId = 8, selectedMapJson = "{\"Main\":\"DIRECT\",\"Other\":\"Main\",\"GLOBAL\":\"Main\"}",
            currentGroupName = "Main", unfoldSetJson = "[\"Main\"]", matchTarget = "Main",
            overridesJson = "{\"dns\":{\"enable\":true},\"dialers\":{\"node\":\"Main\"},\"ntp\":{\"dialer-proxy\":\"Main\"}}")
        val result = ProxyGroupDrafts.updateProfileReferences(profile, ProxyGroupsSave(groups))
        assertEquals(8L, result.scriptId)
        assertEquals("New", result.currentGroupName)
        assertEquals("New", result.matchTarget)
        assertEquals(setOf("New"), result.unfoldSet)
        assertEquals(mapOf("New" to "DIRECT", "Other" to "New", "GLOBAL" to "New"), decodeSelectedMap(result.selectedMapJson))
        val overrides = JsonParser.parseString(result.overridesJson).asJsonObject
        assertTrue(overrides.getAsJsonObject("dns").get("enable").asBoolean)
        assertEquals("New", overrides.getAsJsonObject("dialers").get("node").asString)
        assertEquals("New", overrides.getAsJsonObject("ntp").get("dialer-proxy").asString)
    }

    @Test fun deletionLeavesExplicitDanglingReferencesForUserRepairAndReorderKeepsIdentity() {
        val groups = ProxyGroupDrafts.read(source)
        val reordered = ProxyGroupDrafts.apply(source, ProxyGroupsSave(groups.reversed()))
        assertEquals(listOf("Other", "Main"), ProxyGroupDrafts.read(reordered).map { it.name })
        val deleted = ProxyGroupDrafts.apply(source, ProxyGroupsSave(groups.drop(1)))
        assertEquals(listOf("Main"), ProxyGroupDrafts.read(deleted).single().strings("proxies"))
        assertTrue(ProxyGroupDrafts.root(deleted).getAsJsonArray("rules")[0].asString.contains(",Main,"))
    }
    @Test fun wholeEditorAcceptsBlockFlowAndRootWithoutLosingUnknownValues() {
        for (text in listOf("- {name: Main, type: select, proxies: [DIRECT], future: false}",
            "proxy-groups:\n- {name: Main, type: select, proxies: [DIRECT], future: false}")) {
            val result = ProxyGroupDrafts.readEditorYaml(text).single()
            assertEquals("Main", result.name)
            assertFalse(result.fields["future"].asBoolean)
        }
        assertTrue(ProxyGroupDrafts.readEditorYaml("[]").isEmpty())
        assertTrue(ProxyGroupDrafts.readEditorYaml("").isEmpty())
        assertTrue(runCatching { ProxyGroupDrafts.readEditorYaml("- {name: Main, type: invalid}") }.isFailure)
        assertTrue(runCatching { ProxyGroupDrafts.readEditorYaml("- {name: Main, type: select}\n- {name: Main, type: select}") }.isFailure)
    }

    @Test fun wholeEditorSyntaxLocationUsesOriginalUtf16AndNewlineCoordinates() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            val text = listOf("# 😀 comment", "- name: Main", "  type: select", "  proxies: [DIRECT").joinToString(newline)
            val error = runCatching { ProxyGroupDrafts.readEditorYaml(text) }.exceptionOrNull()!!
            val location = ProxyGroupDrafts.editorErrorLocation(text, error)!!
            assertEquals(4, location.line)
            assertEquals(text.length, location.offset)
            assertEquals("  proxies: [DIRECT".length + 1, location.column)
        }
    }

}
