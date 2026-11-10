package com.lonnnnnng.flclash.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ProxyGroupMembersTest {
    @Test fun addingProxyPreservesFlutterFieldOrderAndExistingLayout() {
        val original = ProxyGroupDraft(json = """{"name":"Main","type":"select","proxies":["DIRECT"],"use":["cloud"],"filter":"HK"}""")
        val next = ProxyGroupMembers.select(original, listOf(GroupMemberEntry(GroupMemberKind.PROXY, "Local")))
        assertFalse(next.fields.has("use-position"))
        assertNull(next.memberLayout)
        assertEquals(listOf("DIRECT", "Local", "cloud", "HK"), ProxyGroupMembers.read(next).map { it.name })
        val ordered = original.copy(memberLayout = listOf("filter"))
        assertEquals(ordered.memberLayout, ProxyGroupMembers.select(ordered,
            listOf(GroupMemberEntry(GroupMemberKind.PROXY, "Local"))).memberLayout)
    }

    @Test fun duplicateKeepsDisplayLayoutButGetsIndependentIdentity() {
        val original = ProxyGroupDraft(originalName = "Main", json = """{"name":"Main","type":"select","proxies":["DIRECT"],"filter":"HK"}""",
            memberLayout = listOf("filter"))
        val copy = ProxyGroupIssues.duplicate(listOf(original), setOf(original.key), "").last()
        assertNotEquals(original.key, copy.key)
        assertNull(copy.originalName)
        assertEquals(original.memberLayout, copy.memberLayout)
        assertEquals(ProxyGroupMembers.read(original), ProxyGroupMembers.read(copy))
    }

    @Test fun overlappingPresetsUndoDoesNotRemoveOriginalOrRemainingParts() {
        val original = ProxyGroupDraft(json = """{"name":"Main","type":"select","proxies":["DIRECT"],"filter":"HK"}""")
        val first = GroupMemberEntry(GroupMemberKind.FILTER, "HK`JP")
        val second = GroupMemberEntry(GroupMemberKind.FILTER, "JP`US")
        assertEquals("HK`JP`US", ProxyGroupMembers.select(original, listOf(first, second)).text("filter"))
        assertEquals("HK`JP`US", ProxyGroupMembers.select(original, listOf(second)).text("filter"))
        assertEquals(original, ProxyGroupMembers.select(original, emptyList()))
    }

    @Test fun mixedRowsRoundTripWithoutChangingCoreFieldOrderOrUnknownFields() {
        val original = ProxyGroupDraft(json = """{"name":"Main","type":"select","proxies":["DIRECT","Local"],"use":["cloud"],"filter":"HK`JP","future":"keep"}""")
        val rows = ProxyGroupMembers.read(original)
        val reordered = listOf(rows[3], rows[2], rows[0], rows[4], rows[1])
        val changed = ProxyGroupMembers.replace(original, reordered)
        assertEquals(reordered, ProxyGroupMembers.read(changed))
        assertEquals(listOf("DIRECT", "Local"), changed.strings("proxies"))
        assertEquals("keep", changed.text("future"))
        val profile = ProxyGroupDrafts.updateProfileReferences(Profile.custom(815, "Round trip"), ProxyGroupsSave(listOf(changed)))
        val persisted = ProxyGroupMembers.restoreLayouts(listOf(changed.copy(memberLayout = null)), profile.overridesJson).single()
        assertEquals(reordered, ProxyGroupMembers.read(persisted))
    }

    private val source = """
        proxies:
          - {name: Local, type: direct}
        proxy-providers:
          cloud: {type: file, path: cloud.yaml}
        proxy-groups:
          - {name: Main, type: select, proxies: [DIRECT, Local], use: [cloud], use-position: {cloud: 1}}
          - {name: Backup, type: fallback, proxies: [DIRECT]}
    """.trimIndent()

    @Test fun sectionsMatchFlutterKindsAndDoNotOfferAlreadySelectedMembers() {
        val main = ProxyGroupDrafts.read(source).first()
        val sections = ProxyGroupMembers.sections(main, ProxyGroupDrafts.read(source), source,
            listOf(FilterPreset("Region", "HK`JP"), FilterPreset("Ads", "ads")))
        assertEquals(listOf("base", "auto", "groups", "filter", "excludeFilter"), sections.map { it.id })
        assertTrue(sections.single { it.id == "base" }.choices.none { it.member.name == "DIRECT" })
        assertTrue(sections.none { it.id == "local" || it.id == "providers" })
        assertEquals("DIRECT", ProxyGroupMembers.read(main).first().name)
    }

    @Test fun selectingAndUndoingEntriesReplaysFromOriginalDraftWithoutPrivateYamlKeys() {
        val main = ProxyGroupDrafts.read(source).first()
        val region = GroupMemberEntry(GroupMemberKind.FILTER, "HK`JP")
        val picked = ProxyGroupMembers.select(main, listOf(
            GroupMemberEntry(GroupMemberKind.PROXY, "Backup"),
            GroupMemberEntry(GroupMemberKind.ALL_PROXIES, "*"),
            region,
        ))
        assertEquals(listOf("DIRECT", "Local", "Backup"), picked.strings("proxies"))
        assertTrue(picked.flag("include-all-proxies"))
        assertEquals("HK`JP", picked.text("filter"))
        assertEquals(1, picked.fields.getAsJsonObject("use-position").get("cloud").asInt)
        assertEquals(main.json, ProxyGroupMembers.select(main, emptyList()).json)
        val yaml = ProxyGroupDrafts.apply(source, ProxyGroupsSave(listOf(picked, ProxyGroupDrafts.read(source).last())))
        assertFalse(yaml.contains("member-layout"))
        assertFalse(yaml.contains("memberLayout"))
        assertTrue(yaml.contains("include-all-proxies"))
        assertEquals("HK`JP", ProxyGroupDrafts.read(yaml).first().text("filter"))
    }

    @Test fun memberDisplayLayoutLivesInProfileOverridesAndSurvivesRenameRestore() {
        val groups = ProxyGroupDrafts.read(source)
        val main = groups.first().copy(memberLayout = listOf("filter", "proxy", "excludeFilter"))
        val profile = ProxyGroupDrafts.updateProfileReferences(Profile.custom(810, "Layouts"), ProxyGroupsSave(listOf(main, groups.last())))
        val restored = ProxyGroupMembers.restoreLayouts(groups, profile.overridesJson)
        assertEquals(main.memberLayout, restored.first().memberLayout)
        val renamed = listOf(main.edit { it.addProperty("name", "Renamed") }, groups.last())
        val afterRename = ProxyGroupDrafts.updateProfileReferences(profile, ProxyGroupsSave(renamed))
        val savedLayouts = JsonParser.parseString(afterRename.overridesJson).asJsonObject.getAsJsonObject("groupMemberLayouts")
        assertFalse(savedLayouts.has("Main"))
        assertEquals(main.memberLayout, ProxyGroupMembers.restoreLayouts(renamed, afterRename.overridesJson).first().memberLayout)
    }
}
