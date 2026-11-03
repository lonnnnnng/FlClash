package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class OnDemandSsidTrackerTest {
    @Test fun sameWifiRefreshWaitsForLocationCallbackWithoutCellularFalseResume() {
        val tracker = OnDemandSsidTracker<Int>()
        tracker.changed(1, true, "Home")
        tracker.refresh(setOf(1))
        tracker.changed(2, false, null)
        assertEquals("Home", tracker.current)
        tracker.changed(1, true, "Office")
        tracker.expireInitialRead()
        assertEquals("Office", tracker.current)
    }

    @Test fun allNetworksLostAndReplacedWifiCannotReuseOldSsid() {
        val tracker = OnDemandSsidTracker<Int>()
        tracker.changed(1, true, "Home")
        tracker.refresh(emptySet())
        assertNull(tracker.current)
        tracker.changed(1, true, "Home")
        tracker.refresh(setOf(3))
        assertNull(tracker.current)
        tracker.changed(3, true, "New")
        tracker.changed(3, false, null)
        assertNull(tracker.current)
    }

    @Test fun expiredOrRedactedLocationCannotKeepExcludedNetworkForever() {
        val tracker = OnDemandSsidTracker<Int>()
        tracker.changed(1, true, "Home")
        tracker.refresh(setOf(1))
        tracker.expireInitialRead()
        assertNull(tracker.current)
        tracker.changed(1, true, "Home")
        tracker.changed(1, true, null)
        assertNull(tracker.current)
    }
}
