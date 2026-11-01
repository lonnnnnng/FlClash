package com.lonnnnnng.flclash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DashboardLayoutTest {
    @Test
    fun defaultLayoutKeepsFlutterAndroidMainCardSequence() {
        val layout = DashboardLayoutState()

        assertEquals(
            listOf(
                DashboardSection.NETWORK_STATS,
                DashboardSection.OUTBOUND_MODE,
                DashboardSection.PROFILES,
                DashboardSection.PROXY_GROUPS,
                DashboardSection.BATTERY_OPTIMIZATION,
                DashboardSection.ALWAYS_ON_VPN,
                DashboardSection.ACCESS_CONTROL,
                DashboardSection.PROBE,
            ),
            layout.visibleInOrder(),
        )
    }

    @Test
    fun storedLayoutDropsUnknownNamesAndAppendsMissingSections() {
        val layout = DashboardLayoutState.fromStored(
            orderNames = listOf("PROXY_GROUPS", "UNKNOWN", "PROFILES"),
            visibleNames = listOf("PROFILES"),
        )

        assertEquals(DashboardSection.PROXY_GROUPS, layout.order[0])
        assertEquals(DashboardSection.PROFILES, layout.order[1])
        assertEquals(DashboardSection.entries.toSet(), layout.order.toSet())
        assertEquals(listOf(DashboardSection.PROFILES), layout.visibleInOrder())
    }

    @Test
    fun newlyAddedSectionIsVisibleAfterPreferenceUpgrade() {
        val knownSections = DashboardSection.entries.dropLast(1)
        val knownNames = knownSections.map { it.name }
        val layout = DashboardLayoutState.fromStored(
            orderNames = knownNames,
            visibleNames = knownNames,
            knownSectionNames = knownNames,
        )

        assertEquals(DashboardSection.entries, layout.order)
        assertEquals(DashboardSection.entries, layout.visibleInOrder())
    }

    @Test
    fun visibilityAndOrderChangesRemainIndependent() {
        val layout = DashboardLayoutState()
            .setVisible(DashboardSection.NETWORK_STATS, false)
            .move(DashboardSection.PROFILES, -1)

        assertEquals(DashboardSection.PROFILES, layout.order[1])
        assertEquals(false, DashboardSection.NETWORK_STATS in layout.visible)
        assertEquals(true, DashboardSection.PROFILES in layout.visible)
    }

    @Test
    fun visibleSectionsCanBeReorderedWithoutMovingHiddenSectionsIntoView() {
        val layout = DashboardLayoutState()
            .setVisible(DashboardSection.OUTBOUND_MODE, false)
            .reorderVisible(DashboardSection.NETWORK_STATS, DashboardSection.PROFILES)

        assertEquals(
            listOf(
                DashboardSection.PROFILES,
                DashboardSection.NETWORK_STATS,
                DashboardSection.PROXY_GROUPS,
                DashboardSection.BATTERY_OPTIMIZATION,
                DashboardSection.ALWAYS_ON_VPN,
                DashboardSection.ACCESS_CONTROL,
                DashboardSection.PROBE,
            ),
            layout.visibleInOrder(),
        )
        assertEquals(false, DashboardSection.OUTBOUND_MODE in layout.visible)
    }

    @Test
    fun addingHiddenSectionPlacesItAfterCurrentlyVisibleSections() {
        val layout = DashboardLayoutState()
            .setVisible(DashboardSection.NETWORK_STATS, false)
            .setVisible(DashboardSection.NETWORK_STATS, true)

        assertEquals(DashboardSection.NETWORK_STATS, layout.visibleInOrder().last())
    }

    @Test
    fun editSwayUsesSmallPhaseShiftedMotionPerCard() {
        val first = dashboardEditSway(DashboardSection.NETWORK_STATS, 0.12f)
        val later = dashboardEditSway(DashboardSection.NETWORK_STATS, 0.37f)
        val otherCard = dashboardEditSway(DashboardSection.PROFILES, 0.12f)

        assertNotEquals(first, later)
        assertNotEquals(first, otherCard)
        assertEquals(true, first.rotationDegrees in -0.7f..0.7f)
        assertEquals(true, first.translationYDp in -1.25f..1.25f)
    }
}
