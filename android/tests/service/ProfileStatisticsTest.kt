package com.lonnnnnng.flclash.service

import com.lonnnnnng.flclash.core.CoreProviderCounts
import org.junit.Assert.*
import org.junit.Test

class ProfileStatisticsTest {
    @Test fun countsAppliedListsAndOnlyAddsLoadedProviderNodes() {
        val config = """{"proxy-groups":[{},{}],"proxies":[{}],"rules":["MATCH,DIRECT"],"proxy-providers":{"a":{}},"rule-providers":{"b":{}}}"""
        assertEquals(ProfileStatistics(1, 2, 8, 1, 2), profileStatisticsOf(1, config, CoreProviderCounts(7, 2)))
    }
    @Test fun missingAndNonListSectionsAreZeroButOverflowAndInvalidRootFail() {
        assertEquals(ProfileStatistics(1, 0, 0, 0, 0), profileStatisticsOf(1, "{proxies: {}, rules: null}", CoreProviderCounts(0, 0)))
        assertTrue(runCatching { profileStatisticsOf(1, "[]", CoreProviderCounts(0, 0)) }.isFailure)
        assertTrue(runCatching { profileStatisticsOf(1, "{proxies: [{}]}", CoreProviderCounts(Long.MAX_VALUE, 0)) }.isFailure)
    }
}
