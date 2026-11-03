package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.YamlConfigCodec
import org.junit.Assert.*
import org.junit.Test

class RuleResourcesTest {
    private val resource = RuleResource("resource-id", "规则", "", "resource-file.rules", "domain", "text", 1)
    @Test fun bindingsPreserveAliasAndRefuseAccidentalCollision() {
        val section = "旧名称:\n  type: inline\n  behavior: domain\n  payload: [example.org]\n"
        val replaced = RuleResources.bind(section, resource, "旧名称")
        val definitions = RuleResources.document("rule-providers:\n" + replaced.lines().joinToString("\n") { "  $it" }).getAsJsonObject("rule-providers")
        assertEquals(setOf("旧名称"), definitions.keySet())
        assertEquals(resource.id, RuleResources.idOf(definitions["旧名称"]))
        assertTrue(runCatching { RuleResources.bind(section, resource.copy(label = "旧名称")) }.isFailure)
    }
    @Test fun renamedRuleSetsFollowNestedRulesAndDnsWithoutChangingProxyCredentials() {
        val document = RuleResources.document("""
            rules: ['RULE-SET,old,DIRECT', 'AND,((RULE-SET,old),(DOMAIN,old)),DIRECT']
            dns:
              nameserver-policy:
                'rule-set:old,other': ['https://example.org/RULE-SET,old,other']
            proxies: [{name: old, type: ss, password: 'RULE-SET,old,secret'}]
        """.trimIndent())
        RuleResources.renameRuleReferences(document, mapOf("old" to "new"))
        assertEquals("RULE-SET,new,DIRECT", document.getAsJsonArray("rules")[0].asString)
        assertEquals("AND,((RULE-SET,new),(DOMAIN,old)),DIRECT", document.getAsJsonArray("rules")[1].asString)
        assertEquals("https://example.org/RULE-SET,old,other", document.getAsJsonObject("dns").getAsJsonObject("nameserver-policy").getAsJsonArray("rule-set:new,other")[0].asString)
        assertEquals("RULE-SET,old,secret", document.getAsJsonArray("proxies")[0].asJsonObject["password"].asString)
    }
    @Test fun unicodeAndQuotedProviderKeysSurviveBindingSave() {
        val resourceWithColon = resource.copy(label = "规则: 测试")
        val section = RuleResources.bind("", resourceWithColon)
        val source = ProfileProvidersEditor.replace("rules: []\n", ProfileProvidersEditor.Sections(ruleProviders = section))
        val definitions = RuleResources.document(source).getAsJsonObject("rule-providers")
        assertEquals(resource.id, RuleResources.idOf(definitions.get("规则: 测试")))
        assertEquals(listOf("规则: 测试"), ProfileProvidersEditor.proxyProviderNames(section))
    }

    @Test fun metadataRejectsTraversalDuplicateLabelsAndUnsupportedProtocols() {
        assertEquals(listOf(resource), RuleResources.decode(RuleResources.encode(listOf(resource))))
        for (candidate in listOf(resource.copy(fileName = "../x.rules"), resource.copy(id = "../x"), resource.copy(url = "file:///tmp/secret"), resource.copy(url = "https://user:pass@example.org/x"), resource.copy(behavior = "bad"))) {
            assertTrue(runCatching { RuleResources.encode(listOf(candidate)) }.isFailure)
        }
        assertTrue(runCatching { RuleResources.encode(listOf(resource, resource.copy(id = "second"))) }.isFailure)
        assertEquals("规则 (2)", RuleResources.uniqueLabel("规则", listOf(resource)))
    }
}
