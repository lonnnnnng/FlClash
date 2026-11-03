package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ProfileProxyResourcesTest {
    private val file = Profile.remote(12, "", "同名资源").copy(type = ProfileType.FILE)

    @Test fun candidatesExcludeCustomAndDisambiguateDuplicateLabels() {
        val choices = ProfileProxyResources.candidates("", listOf(file, file.copy(id = 13), Profile.custom(14, "自定义")))
        assertEquals(listOf("同名资源", "同名资源 (2)"), choices.map { it.alias })
        assertEquals(listOf(12L, 13L), choices.map { it.profile.id })
    }

    @Test fun repairKeepsAliasOptionsAndStableIdAfterRename() {
        val section = "旧别名:\n  type: file\n  path: kclash-profile:99\n  filter: HK\n  health-check:\n    enable: true\n"
        val bound = ProfileProxyResources.bind(section, file.copy(label = "已改名"), "旧别名")
        val definitions = ProfileProxyResources.definitions(bound)
        assertEquals(setOf("旧别名"), definitions.keySet())
        assertEquals(12L, ProfileProxyResources.idOf(definitions["旧别名"]))
        assertEquals("HK", definitions.getAsJsonObject("旧别名")["filter"].asString)
        assertTrue(definitions.getAsJsonObject("旧别名").getAsJsonObject("health-check")["enable"].asBoolean)
        assertTrue(runCatching { ProfileProxyResources.bind("", Profile.custom(15, "custom")) }.isFailure)
        assertNull(ProfileProxyResources.idOf(JsonObject().apply { addProperty("path", "kclash-profile:../1") }))
    }

    @Test fun importRenamesOnlyResourceUsesAndPositions() {
        val document = JsonParser.parseString("""{"proxy-groups":[{"name":"old","proxies":["old"],"use":["old","other"],"use-position":{"old":0}}],"proxies":[{"name":"old","password":"old"}]}""").asJsonObject
        ProfileProxyResources.renameUses(document, mapOf("old" to "mapped"))
        val group = document.getAsJsonArray("proxy-groups").single().asJsonObject
        assertEquals("old", group["name"].asString)
        assertEquals("old", group.getAsJsonArray("proxies").single().asString)
        assertEquals("mapped", group.getAsJsonArray("use")[0].asString)
        assertEquals(0, group.getAsJsonObject("use-position")["mapped"].asInt)
        assertEquals("old", document.getAsJsonArray("proxies").single().asJsonObject["password"].asString)
    }
}
