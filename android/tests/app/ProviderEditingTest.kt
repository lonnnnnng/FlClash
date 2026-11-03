package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.*
import org.junit.Assert.*
import org.junit.Test

class ProviderEditingTest {
    @Test fun `custom catalog follows semantic references including nested rules but ignores definitions and regex text`() {
        val profile = Profile.custom(1, "引用").copy(overridesJson = ProfileProviderOverrides(extension = ProfileExtensionOverrides(
            rules = listOf(ExtensionRule("extra", "RULE-SET,extension,DIRECT")),
        )).toJsonString())
        val source = """
            proxy-groups:
              - name: Select
                type: select
                use: [cloud, missing, cloud]
            rules:
              - RULE-SET,ads,REJECT
              - AND,((RULE-SET,nested),(OR,((RULE-SET,inner),(DOMAIN,example.org)))),DIRECT
              - DOMAIN-REGEX,RULE-SET,fake,DIRECT
            dns:
              nameserver-policy:
                rule-set:dns: [system]
            sniffer:
              skip-domain: ["rule-set:sniff,other"]
        """.trimIndent()
        val result = ProviderCatalog.from(profile, source, "unused:\n  type: file", "unused:\n  type: file")
        assertEquals(listOf("cloud", "missing"), result.proxies)
        assertEquals(listOf("ads", "nested", "inner", "extension", "dns", "sniff", "other"), result.rules)
    }

    @Test fun `ordinary profile lists all provider definitions`() {
        val result = ProviderCatalog.from(Profile.remote(1, "https://example.com", "订阅"), "rules: []",
            "cloud:\n  type: file\nlocal:\n  type: file", "ads:\n  type: file")
        assertEquals(listOf("cloud", "local"), result.proxies)
        assertEquals(listOf("ads"), result.rules)
    }

    @Test fun `mixed member layouts round trip all permutations and compact natural suffix`() {
        val filter = ProviderFilterKind.FILTER
        val exclude = ProviderFilterKind.EXCLUDE
        val rows = listOf(ProviderFilterMember(filter, "HK"), ProviderFilterMember(filter, "JP"), ProviderFilterMember(exclude, "ads"))
        fun permutations(values: List<ProviderFilterMember>): List<List<ProviderFilterMember>> = if (values.isEmpty()) listOf(emptyList())
            else values.flatMap { row -> permutations(values - row).map { listOf(row) + it } }
        for (order in permutations(rows)) {
            val model = ProviderFilters.replace(ProxyProviderOverride(), order)
            assertEquals(order, ProviderFilters.members(model))
            assertEquals(model, ProxyProviderOverride.fromJson(model.toJson()))
        }
        assertNull(ProviderFilters.replace(ProxyProviderOverride(), rows).memberLayout)
        assertEquals(listOf("excludeFilter"), ProviderFilters.replace(ProxyProviderOverride(), listOf(rows[2], rows[0], rows[1])).memberLayout)
    }

    @Test fun `removing last condition writes explicit clear while untouched fields inherit`() {
        val empty = ProxyProviderOverride()
        assertEquals(empty, ProviderFilters.replace(empty, emptyList()))
        val appended = ProviderFilters.append(empty, ProviderFilterKind.FILTER, "HK`HK`JP")
        assertEquals("HK`JP", appended.filter)
        assertNull(appended.excludeFilter)
        val cleared = ProviderFilters.replace(appended, emptyList())
        assertEquals("", cleared.filter)
        assertNull(cleared.excludeFilter)
        assertEquals(cleared, ProxyProviderOverride.fromJson(cleared.toJson()))
    }

    @Test fun `options add upstream defaults and removal only clears chosen field`() {
        val defaults = ProviderOption.entries.fold(ProxyProviderOverride()) { model, option -> option.apply(model, option.default) }
        assertEquals(300, defaults.interval); assertEquals(5000, defaults.timeout)
        assertEquals(true, defaults.lazy); assertEquals(true, defaults.skipCertVerify)
        assertEquals(true, defaults.udp); assertEquals("dual", defaults.ipVersion)
        val removed = ProviderOption.TIMEOUT.apply(defaults, null)
        assertNull(removed.timeout); assertEquals(defaults.interval, removed.interval)
        assertEquals("", defaults.additionalPrefix)
    }
}
