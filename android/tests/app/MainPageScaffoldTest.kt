package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MainPageScaffoldTest {
    @Test
    fun `flutter mobile navigation hides proxy before usable configuration`() {
        assertEquals(
            listOf(MainDestination.DASHBOARD, MainDestination.PROFILES, MainDestination.TOOLS),
            mainDestinations(hasProxyDestination = false),
        )
    }

    @Test
    fun `flutter mobile navigation uses dashboard proxy profiles tools order when available`() {
        assertEquals(
            listOf(
                MainDestination.DASHBOARD,
                MainDestination.PROXY,
                MainDestination.PROFILES,
                MainDestination.TOOLS,
            ),
            mainDestinations(hasProxyDestination = true),
        )
    }
}
