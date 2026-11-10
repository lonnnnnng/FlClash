package com.lonnnnnng.flclash.data

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class LocalProxiesTest {
    private fun node(name: String, password: String = "secret") = LocalProxy(json = """{"name":"$name","type":"ss","server":"node.example","port":443,"cipher":"aes-128-gcm","password":"$password","future":{"keep":true}}""")
    private val source = """
        proxies: []
        proxy-groups:
          - {name: Main, type: select, proxies: [A, DIRECT], default-selected: A}
        rules: ["DOMAIN,A,A,no-resolve", "AND,((DOMAIN,A),(NETWORK,TCP)),A", "MATCH,Main"]
    """.trimIndent()
    @Test fun catalogRoundTripsAndDoesNotExposeCredentialsInToString() {
        val row = node("A")
        assertEquals(listOf(row), LocalProxies.decode(LocalProxies.encode(listOf(row))))
        assertFalse(row.toString().contains("secret"))
        assertTrue(LocalProxies.catalog.size > 80)
        assertTrue(LocalProxies.catalog.map { it.id }.distinct().size == LocalProxies.catalog.size)
    }
    @Test fun projectionOnlyIncludesNamedNodesAndKeepsSourceAndUnknownOptions() {
        val a = node("A"); val b = node("B")
        val projected = ProxyGroupDrafts.root(LocalProxies.project(source, listOf(a, b)))
        assertEquals(listOf("A"), projected.getAsJsonArray("proxies").map { it.asJsonObject["name"].asString })
        assertTrue(projected.getAsJsonArray("proxies").single().asJsonObject["future"].asJsonObject["keep"].asBoolean)
        assertTrue(ProxyGroupDrafts.root(source).getAsJsonArray("proxies").isEmpty)
        assertEquals(source, LocalProxies.project(source, listOf(b)))
        val inline = source.replace("proxies: []", "proxies: [{name: A, type: direct}]")
        val keptInline = ProxyGroupDrafts.root(LocalProxies.project(inline, listOf(a))).getAsJsonArray("proxies")
        assertEquals(1, keptInline.size())
        assertEquals("direct", keptInline.single().asJsonObject["type"].asString)
    }
    @Test fun nestedFieldEditsKeepHostCaseAndPruneEmptyMaps() {
        val a = node("A").copy(json = node("A").fields.apply { add("ws-opts", JsonObject().apply { add("headers", JsonObject().apply { addProperty("host", "a.example"); addProperty("X-Test", "keep") }) }) }.toString())
        val edited = LocalProxies.withValue(a, "ws-opts.headers.Host", JsonPrimitive("b.example"))
        assertEquals("b.example", LocalProxies.value(edited.fields, "ws-opts.headers.HOST")!!.asString)
        assertFalse(edited.fields["ws-opts"].asJsonObject["headers"].asJsonObject.has("Host"))
        assertEquals("keep", LocalProxies.value(edited.fields, "ws-opts.headers.X-Test")!!.asString)
        val empty = LocalProxies.withValue(LocalProxies.withValue(edited, "ws-opts.headers.Host", null), "ws-opts.headers.X-Test", null)
        assertFalse(empty.fields.has("ws-opts"))
    }
    @Test fun importKeepsRuleOnlyTargetsAndDialerDependenciesInRuntimeProjection() {
        val a = node("A"); val b = node("B", "other"); val unused = node("unused")
        val source = "proxies: [${a.json},${b.json}]\nrules: ['MATCH,A']\n"
        val plan = planLocalProxyImport(source, emptyList(), emptySet())
        val projected = ProxyGroupDrafts.root(LocalProxies.project(plan.source, plan.after + unused, mapOf("A" to "B")))
        assertEquals(setOf("A", "B"), projected.getAsJsonArray("proxies").map { it.asJsonObject["name"].asString }.toSet())
        assertEquals("MATCH,A", projected.getAsJsonArray("rules").single().asString)
        assertFalse(ProxyGroupDrafts.root(plan.source).has("proxies"))
    }
    @Test fun protocolChangesPruneUnavailableOptionsAndTransportChildren() {
        val row = node("A").copy(json = """{"name":"A","type":"vless","uuid":"id","network":"ws","ws-opts":{"path":"/path"},"future":9}""")
        val network = LocalProxies.catalog.first { it.id == "network" }
        val tcp = LocalProxyFields.change(row, network, JsonPrimitive("tcp"))
        assertFalse(tcp.fields.has("ws-opts"))
        val ss = LocalProxyFields.changeType(row, "ss")
        assertFalse(ss.fields.has("uuid")); assertFalse(ss.fields.has("network")); assertFalse(ss.fields.has("ws-opts"))
        assertEquals(9, ss.fields["future"].asInt)
        val vmess = LocalProxyFields.changeType(ss, "vmess")
        assertEquals("auto", vmess.fields["cipher"].asString); assertEquals(0, vmess.fields["alterId"].asInt)
    }
    @Test fun namesRejectReservedDuplicatesAndEveryCustomGroupCollision() {
        for (rows in listOf(listOf(node("DIRECT")), listOf(node("A"), node("A")), listOf(node("GLOBAL")), listOf(node("a,b"))))
            assertTrue(runCatching { LocalProxies.validate(rows) }.isFailure)
        assertTrue(runCatching { LocalProxies.validate(listOf(node("A")), setOf("A")) }.isFailure)
    }
    @Test fun renameTouchesTargetsAndSelectionsButKeepsPayloadAndSameNameGroups() {
        val updated = ProxyGroupDrafts.root(LocalProxyReferences.rename(source, "A", "B"))
        assertEquals("B", updated.getAsJsonArray("proxy-groups")[0].asJsonObject.getAsJsonArray("proxies")[0].asString)
        assertEquals("DOMAIN,A,B,no-resolve", updated.getAsJsonArray("rules")[0].asString)
        assertEquals("AND,((DOMAIN,A),(NETWORK,TCP)),B", updated.getAsJsonArray("rules")[1].asString)
        assertEquals(source, LocalProxyReferences.rename(source, "Main", "B"))
        val profile = Profile.custom(1, "test").copy(selectedMapJson = "{\"Main\":\"A\"}", matchTarget = "A", overridesJson = "{\"dns\":{\"enable\":false},\"ntp\":{\"dialer-proxy\":\"A\"}}")
        val meta = LocalProxyReferences.metadata(profile, "A", "B")
        assertEquals("B", decodeSelectedMap(meta.selectedMapJson)["Main"])
        assertEquals("B", meta.matchTarget)
        assertFalse(JsonParser.parseString(meta.overridesJson).asJsonObject["dns"].asJsonObject["enable"].asBoolean)
    }
    @Test fun importReusesDefinitionsAndMapsConflictingNamesWithDialerTargets() {
        val imported = source.replace("proxies: []", "proxies: [${node("A").json}]")
        val reused = planLocalProxyImport(imported, listOf(node("A")), emptySet())
        assertEquals(1, reused.after.size)
        assertFalse(ProxyGroupDrafts.root(reused.source).has("proxies"))
        val mapped = planLocalProxyImport(imported, listOf(node("A", "other")), emptySet())
        assertEquals(listOf("A", "A (2)"), mapped.after.map { it.name })
        assertEquals(listOf("A (2)", "DIRECT"), ProxyGroupDrafts.read(mapped.source).first().strings("proxies"))
    }
    @Test fun globalFallbackCanSaveWithoutMaterializingGlobalNodesInSource() {
        val group = ProxyGroupDrafts.read(source).single().edit { it.addProperty("empty-fallback", "A") }
        val saved = ProxyGroupDrafts.apply(source, ProxyGroupsSave(listOf(group)), listOf("A"))
        assertEquals("A", ProxyGroupDrafts.read(saved).single().text("empty-fallback"))
        assertTrue(ProxyGroupDrafts.root(saved).getAsJsonArray("proxies").isEmpty)
    }
}
