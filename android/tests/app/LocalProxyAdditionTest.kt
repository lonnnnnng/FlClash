package com.lonnnnnng.flclash.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class LocalProxyAdditionTest {
    private fun node(name: String = "A", password: String = "fixture") = JsonParser.parseString(
        """{"name":"$name","type":"ss","server":"node.example","port":443,"cipher":"aes-128-gcm","password":"$password","udp":true,"future":{"ports":[1,"2"]}}""").asJsonObject
    @Test fun aliasDialerAndWeakNumberTypesReuseOriginalWithoutChangingIt() {
        val original = LocalProxy(json = node("我的节点").apply { addProperty("dialer-proxy", "OnlyHere") }.toString())
        val incoming = node("订阅别名").apply { addProperty("port", "443"); addProperty("dialer-proxy", "Other")
            getAsJsonObject("future").getAsJsonArray("ports").set(0, com.google.gson.JsonPrimitive("1")) }
        val plan = planLocalProxyAddition(incoming, listOf(original), emptySet())
        assertFalse(plan.added); assertEquals(original, plan.row)
        assertEquals("Other", incoming["dialer-proxy"].asString)
    }
    @Test fun sameNamedAccountKeepsUserOptionsButDifferentCredentialsGetFreeName() {
        val original = LocalProxy(json = node().toString())
        val changedOptions = node().apply { addProperty("udp", false); remove("future") }
        assertFalse(planLocalProxyAddition(changedOptions, listOf(original), emptySet()).added)
        val plan = planLocalProxyAddition(node(password = "other"), listOf(original), setOf("A (2)"), setOf("A (3)"))
        assertTrue(plan.added); assertEquals("A (4)", plan.row.name)
        assertEquals("other", plan.row.fields["password"].asString)
    }
    @Test fun credentiallessProtocolsDoNotReuseDifferentOptionsAndDialerIsNeverSaved() {
        val direct = JsonParser.parseString("""{"name":"D","type":"direct","interface-name":"first"}""").asJsonObject
        val old = LocalProxy(json = direct.toString())
        direct.addProperty("interface-name", "second"); direct.addProperty("dialer-proxy", "X")
        val plan = planLocalProxyAddition(direct, listOf(old), emptySet())
        assertTrue(plan.added); assertEquals("D (2)", plan.row.name); assertFalse(plan.row.fields.has("dialer-proxy"))
        assertEquals("DIRECT (2)", planLocalProxyAddition(node("DIRECT"), emptyList(), emptySet()).row.name)
    }
    @Test fun customImportUsesSameAliasDeduplicationAndRewritesOnlyTargets() {
        val old = LocalProxy(json = node("保留名").toString())
        val source = "proxies: [${node("A")}]\nproxy-groups: [{name: Main, type: select, proxies: [A]}]\nrules: [MATCH,Main]"
        val plan = planLocalProxyImport(source, listOf(old), emptySet())
        assertEquals(listOf(old), plan.after)
        assertEquals(listOf("保留名"), ProxyGroupDrafts.read(plan.source).single().strings("proxies"))
    }
}
