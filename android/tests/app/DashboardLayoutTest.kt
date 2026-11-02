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
                DashboardSection.CURRENT_OUTBOUND,
                DashboardSection.NETWORK_STATS,
                DashboardSection.OUTBOUND_MODE,
                DashboardSection.PROFILES,
                DashboardSection.PROXY_GROUPS,
            ),
            layout.visibleInOrder(),
        )
        assertEquals(false, DashboardSection.CONNECTION_STATS in layout.visible)
    }

    @Test
    fun storedLayoutDropsUnknownNamesAndAppendsMissingSections() {
        val layout = DashboardLayoutState.fromStored(
            orderNames = listOf("PROXY_GROUPS", "UNKNOWN", "PROFILES"),
            visibleNames = listOf("PROFILES"),
        )

        assertEquals(DashboardSection.CURRENT_OUTBOUND, layout.order[0])
        assertEquals(DashboardSection.PROXY_GROUPS, layout.order[1])
        assertEquals(DashboardSection.PROFILES, layout.order[2])
        assertEquals(DashboardSection.entries.toSet(), layout.order.toSet())
        assertEquals(listOf(DashboardSection.CURRENT_OUTBOUND, DashboardSection.PROFILES), layout.visibleInOrder())
    }

    @Test
    fun newlyAddedHiddenSectionStaysHiddenAfterPreferenceUpgrade() {
        val knownSections = DashboardSection.entries.filter { it.visibleByDefault }
        val knownNames = knownSections.map { it.name }
        val layout = DashboardLayoutState.fromStored(
            orderNames = knownNames,
            visibleNames = knownNames,
            knownSectionNames = knownNames,
        )

        assertEquals(DashboardSection.entries, layout.order)
        assertEquals(knownSections, layout.visibleInOrder())
        assertEquals(false, DashboardSection.CONNECTION_STATS in layout.visible)
    }

    @Test
    fun oldSystemAndDiagnosticCardsDisappearWithoutResettingDashboardPreferences() {
        val previous = listOf(
            "PROFILES", "ACCESS_CONTROL", "CURRENT_OUTBOUND", "PROBE", "NETWORK_STATS",
            "ALWAYS_ON_VPN", "BATTERY_OPTIMIZATION",
        )
        val layout = DashboardLayoutState.fromStored(previous, previous, previous)

        assertEquals(
            listOf(
                DashboardSection.PROFILES, DashboardSection.CURRENT_OUTBOUND, DashboardSection.NETWORK_STATS,
                DashboardSection.OUTBOUND_MODE, DashboardSection.PROXY_GROUPS,
            ),
            layout.visibleInOrder(),
        )
        assertEquals(false, DashboardSection.CONNECTION_STATS in layout.visible)
    }

    @Test
    fun visibilityAndOrderChangesRemainIndependent() {
        val layout = DashboardLayoutState()
            .setVisible(DashboardSection.NETWORK_STATS, false)
            .move(DashboardSection.PROFILES, -1)

        assertEquals(DashboardSection.PROFILES, layout.order[2])
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
                DashboardSection.CURRENT_OUTBOUND,
                DashboardSection.PROFILES,
                DashboardSection.NETWORK_STATS,
                DashboardSection.PROXY_GROUPS,
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
    fun statusCardCanBeReorderedButCannotBeRemoved() {
        val layout = DashboardLayoutState()
            .reorderVisible(DashboardSection.CURRENT_OUTBOUND, DashboardSection.NETWORK_STATS)

        assertEquals(DashboardSection.NETWORK_STATS, layout.visibleInOrder().first())
        assertEquals(DashboardSection.CURRENT_OUTBOUND, layout.visibleInOrder()[1])
        assertEquals(layout, layout.setVisible(DashboardSection.CURRENT_OUTBOUND, false))
    }

    @Test
    fun storedStatusPositionSurvivesReloadEvenWhenVisibilityIsMissing() {
        val layout = DashboardLayoutState.fromStored(
            orderNames = listOf("OUTBOUND_MODE", "CURRENT_OUTBOUND", "NETWORK_STATS"),
            visibleNames = listOf("OUTBOUND_MODE", "NETWORK_STATS"),
        )

        assertEquals(
            listOf(DashboardSection.OUTBOUND_MODE, DashboardSection.CURRENT_OUTBOUND, DashboardSection.NETWORK_STATS),
            layout.visibleInOrder(),
        )
    }

    @Test
    fun olderLayoutKeepsStatusFirstAndPreservesExistingUserOrder() {
        val previousOrder = listOf("PROFILES", "OUTBOUND_MODE", "NETWORK_STATS")
        val layout = DashboardLayoutState.fromStored(
            orderNames = previousOrder,
            visibleNames = previousOrder,
            knownSectionNames = previousOrder,
        )

        assertEquals(DashboardSection.CURRENT_OUTBOUND, layout.order.first())
        assertEquals(previousOrder, layout.order.drop(1).take(previousOrder.size).map { it.name })
        assertEquals(false, DashboardSection.CONNECTION_STATS in layout.visible)
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
