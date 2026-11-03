package com.lonnnnnng.flclash.data

import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.YamlConfigCodec
import org.junit.Assert.*
import org.junit.Test

class CustomRuleDraftTest {
    @Test fun yamlKeepsRegexCommaQuotedPunctuationAndDuplicateIdentities() {
        val values = listOf(CustomRuleDraft(value = "DOMAIN-REGEX,^foo,bar: #tag$,DIRECT"), CustomRuleDraft(value = "MATCH,DIRECT"), CustomRuleDraft(value = "MATCH,DIRECT"))
        val decoded = CustomRules.decode(CustomRules.yaml(values), values)
        assertEquals(values, decoded)
        val fields = CustomRules.fields(values.first().value)
        assertEquals("^foo,bar: #tag$", fields.payload); assertEquals("DIRECT", fields.target)
    }
    @Test fun quickEditAcceptsHeaderCommentsAndDropsOnlySubRulesWhenRequested() {
        val rules = CustomRules.decode("# rules\nrules:\n- DOMAIN,example.test,DIRECT # comment\n- SUB-RULE,(NETWORK,TCP),nested\n- MATCH,REJECT", dropSubRules = true)
        assertEquals(listOf("DOMAIN,example.test,DIRECT", "MATCH,REJECT"), rules.map { it.value })
        assertTrue(runCatching { CustomRules.decode("FUTURE,example.test,DIRECT") }.isFailure)
    }
    @Test fun validatesPayloadsAndRetainsSourceIpAndNoResolveParameters() {
        for (value in listOf("NETWORK,tcp,DIRECT", "DSCP,0-63,DIRECT", "DST-PORT,80/443/8000-9000,DIRECT", "IP-CIDR,192.0.2.0/24,DIRECT,src,no-resolve", "AND,((NETWORK,UDP),(DST-PORT,443)),REJECT")) CustomRules.validate(value)
        for (value in listOf("NETWORK,any,DIRECT", "DSCP,64,DIRECT", "DST-PORT,*,DIRECT", "UID,-1,DIRECT", "MATCH,", "DOMAIN,,DIRECT", "MATCH,DIRECT,src", "DOMAIN,example.test,DIRECT,other")) assertTrue(value, runCatching { CustomRules.validate(value) }.isFailure)
        val parsed = CustomRules.fields("IP-CIDR,192.0.2.0/24,DIRECT,src,no-resolve")
        assertTrue(parsed.src); assertTrue(parsed.noResolve); assertEquals("IP-CIDR,192.0.2.0/24,DIRECT,src,no-resolve", parsed.raw())
    }
    @Test fun sourceInlineUnindentedQuotedAndBoundaryCommentsArePreserved() {
        val source = "mixed-port: 7890\n'rules': [\"DOMAIN,old.test,DIRECT\"]\n# DNS retained\ndns: {enable: true}\n"
        assertEquals("DOMAIN,old.test,DIRECT", ProfileRulesEditor.extractRules(source))
        val updated = ProfileRulesEditor.replaceRules(source, CustomRules.scalar("DOMAIN-REGEX,^foo: #bar$,DIRECT"))
        assertTrue(updated.startsWith("mixed-port: 7890\n")); assertTrue(updated.endsWith("# DNS retained\ndns: {enable: true}\n"))
        assertEquals(listOf("DOMAIN-REGEX,^foo: #bar$,DIRECT"), CustomRules.fromSource(updated))
        assertEquals("MATCH,DIRECT", ProfileRulesEditor.extractRules("rules:\n- MATCH,DIRECT\ndns: {}\n"))
    }
    @Test fun replacingFlowAndIndentedRootsDoesNotDuplicateRulesAndEmptyIsAList() {
        for (source in listOf("{rules: [], dns: {enable: true}}", "  rules: []\n  dns: {enable: true}")) {
            val replaced = ProfileRulesEditor.replaceRules(source, "MATCH,DIRECT")
            assertEquals(listOf("MATCH,DIRECT"), CustomRules.fromSource(replaced))
            assertTrue(JsonParser.parseString(YamlConfigCodec.toJson(replaced)).asJsonObject.getAsJsonObject("dns")["enable"].asBoolean)
        }
        assertEquals("rules: []\n", ProfileRulesEditor.replaceRules("", ""))
    }
    @Test fun catalogKeepsMissingReferencesVisibleAndUsesLocalTargets() {
        val catalog = CustomRuleCatalog(groups = listOf("Main"), proxies = listOf("Node"), providers = listOf("Local"))
        assertNull(catalog.issue("RULE-SET,Local,Main")); assertNull(catalog.issue("MATCH,Node"))
        assertTrue(catalog.issue("RULE-SET,Missing,Main")!!.contains("规则资源不存在"))
        assertTrue(catalog.issue("MATCH,Gone")!!.contains("策略目标不存在"))
    }
    @Test fun unchangedSubRulesRemainIntactWhileOtherRulesAreEdited() {
        val subRule = "SUB-RULE,(NETWORK,TCP),nested"
        val source = "rules:\n- $subRule\n- DOMAIN,old.test,DIRECT\n"
        val replaced = ProfileRulesEditor.replaceRules(source, "$subRule\nDOMAIN,new.test,DIRECT")
        assertEquals(listOf(subRule, "DOMAIN,new.test,DIRECT"), CustomRules.fromSource(replaced))
        assertEquals("(NETWORK,TCP)", CustomRules.fields(subRule).payload)
        assertEquals("nested", CustomRules.fields(subRule).target)
        assertTrue(runCatching { ProfileRulesEditor.replaceRules(source, "SUB-RULE,(NETWORK,UDP),other") }.isFailure)
    }
}
