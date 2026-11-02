package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ProfileExtensionTest {
    @Test fun profileRulesPrecedeGlobalAndSourceWithMatchPlaceholderResolved() {
        val extension = StandardProfileExtension(listOf(
            ExtensionRule("local", "DOMAIN-SUFFIX,example.test,MATCH"),
            ExtensionRule("global", "AND,((NETWORK,UDP),(DST-PORT,443)),REJECT-DROP"),
            ExtensionRule("cidr", "IP-CIDR,10.0.0.0/8,MATCH,no-resolve"),
        ))
        val source = "future: keep\nrules: ['MATCH,DIRECT', 'MATCH,Main']\n"
        val rendered = ProfileExtensionApplier.apply(source, extension)
        val root = JsonParser.parseString(YamlConfigCodec.toJson(rendered)).asJsonObject
        assertEquals(listOf("DOMAIN-SUFFIX,example.test,Main", "AND,((NETWORK,UDP),(DST-PORT,443)),REJECT-DROP",
            "IP-CIDR,10.0.0.0/8,Main,no-resolve", "MATCH,DIRECT", "MATCH,Main"), root["rules"].asJsonArray.map { it.asString })
        assertEquals("keep", root["future"].asString)
    }

    @Test fun explicitTargetOverridesOnlyPlaceholdersAndNeverReplacesOriginalMatch() {
        val value = ProfileExtensionApplier.apply("rules: ['MATCH,DIRECT']\n", StandardProfileExtension(listOf(
            ExtensionRule("a", "DOMAIN,example.test,MATCH"), ExtensionRule("b", "DOMAIN,other.test,REJECT")), "Proxy"))
        assertEquals(listOf("DOMAIN,example.test,Proxy", "DOMAIN,other.test,REJECT", "MATCH,DIRECT"),
            JsonParser.parseString(YamlConfigCodec.toJson(value)).asJsonObject["rules"].asJsonArray.map { it.asString })
        val source = "rules: []\n# keep original formatting\n"
        assertEquals(source, ProfileExtensionApplier.apply(source, null))
        assertEquals(source, ProfileExtensionApplier.apply(source, StandardProfileExtension(emptyList(), "Proxy")))
    }

    @Test fun extensionRoundTripKeepsDnsProviderAndFutureProfileModeMarker() {
        val initial = ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false), proxyProviders = mapOf("source" to ProxyProviderOverride(interval = 60)),
            extension = ProfileExtensionOverrides(true, listOf(ExtensionRule("rule", "GEOIP,CN,DIRECT,no-resolve")), setOf("global")))
        assertEquals(initial, ProfileProviderOverrides.fromJsonString(initial.toJsonString()))
        assertEquals(initial.extension, ProfileProviderOverrides.fromJsonString(initial.copy(dns = ProfileDnsOverrides(ipv6 = true)).toJsonString()).extension)
        assertFalse(ProfileProviderOverrides.fromJsonString("{}").extension.configured)
    }

    @Test fun targetChoicesContainBuiltinThenGroupsAndLocalProxiesWithoutDuplicates() {
        val targets = ProfileExtensionApplier.targets("proxy-groups: [{name: Main, type: select}]\nproxies: [{name: Node, type: direct}, {name: DIRECT, type: direct}]\n")
        assertEquals(listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "PASS-RULE", "COMPATIBLE", "GLOBAL", "Main", "Node"), targets)
    }

    @Test fun ruleValidationRejectsBrokenLogicalRulesDuplicateIdsAndMissingTarget() {
        listOf("DOMAIN", "DOMAIN,example.test", "AND,((NETWORK,UDP),REJECT", "SUB-RULE,foo,DIRECT", "DOMAIN,a,", "DOMAIN,a,DIRECT\nMATCH,DIRECT").forEach { value ->
            assertTrue(value, runCatching { ExtensionRules.validate(listOf(ExtensionRule("a", value))) }.isFailure)
        }
        assertTrue(runCatching { ExtensionRules.validate(listOf(ExtensionRule("a", "MATCH,DIRECT"), ExtensionRule("a", "MATCH,DIRECT"))) }.isFailure)
        assertEquals(listOf("AND", "((NETWORK,UDP),(DST-PORT,443))", "REJECT-DROP"), ExtensionRules.fields("AND,((NETWORK,UDP),(DST-PORT,443)),REJECT-DROP"))
    }
}
