package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyGroupsViewModelTest {
    @Test
    fun `computed selection toggles current node back to automatic mode`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "node-a",
            all = listOf("node-a", "node-b"),
            hidden = false,
        )

        assertEquals("node-b", computedSelectionTarget(group, "node-b"))
        assertEquals("", computedSelectionTarget(group, "node-a"))
    }

    @Test
    fun `non selectable groups reject selection targets`() {
        val group = ProxyGroupInfo(
            name = "Relay",
            type = "LoadBalance",
            now = "node-a",
            all = listOf("node-a"),
            hidden = false,
        )

        assertNull(computedSelectionTarget(group, "node-a"))
    }

    @Test
    fun `automatic delay event updates only groups with matching url and node`() {
        val state = ProxyGroupsUiState(
            groups = listOf(
                ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a"), false, "https://one"),
                ProxyGroupInfo("Fallback", "Fallback", "node-a", listOf("node-a"), false, "https://two"),
            ),
        )

        val updated = state.applyDelayEvent(
            CoreEvent.Delay(ProxyDelay("https://one", "node-a", 188)),
        )

        assertEquals(188, updated.delayResults.values.single().value)
        assertTrue(updated.delayResults.keys.single().groupName == "Auto")
    }
}
