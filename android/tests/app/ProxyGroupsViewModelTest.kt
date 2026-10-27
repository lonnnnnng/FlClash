package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.ProxyDelay
import com.lonnnnnng.flclash.core.ProxyGroupInfo
import com.lonnnnnng.flclash.service.ProxyDelayRequest
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
        assertEquals("node-a", computedSelectionTarget(group, "node-a"))
        assertEquals("", computedSelectionTarget(group, "node-a", mapOf("Auto" to "node-a")))
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

    @Test
    fun `proxy search filters members without changing group order`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "Tokyo-01",
            all = listOf("Tokyo-01", "Hong Kong-01", "Tokyo-02"),
            hidden = false,
        )

        assertEquals(listOf("Tokyo-01", "Tokyo-02"), filterProxyMembers(group, "tokyo"))
        assertEquals(group.all, filterProxyMembers(group, "  "))
    }

    @Test
    fun `delay result expires after cache ttl`() {
        val group = ProxyGroupInfo("Auto", "URLTest", "node-a", listOf("node-a"), false, "https://one")
        val state = ProxyGroupsUiState(
            groups = listOf(group),
            delayResults = mapOf(
                ProxyDelayKey("Auto", "node-a", "https://one") to
                    ProxyDelayUiState(value = 188, updatedAtMillis = 1_000L),
            ),
        )

        assertEquals(null, state.delayState(group, "node-a", nowMillis = 1_000L + DELAY_RESULT_TTL_MS + 1L))
        assertEquals(188, state.delayState(group, "node-a", nowMillis = 1_000L + DELAY_RESULT_TTL_MS)?.value)
    }

    @Test
    fun `batch delay targets resolve nested groups and deduplicate leaf requests`() {
        val nested = ProxyGroupInfo(
            name = "Relay",
            type = "Selector",
            now = "node-a",
            all = listOf("node-a", "node-b"),
            hidden = false,
            testUrl = "https://nested",
        )
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "node-a",
            all = listOf("Relay", "Relay"),
            hidden = false,
            testUrl = "https://outer",
        )

        assertEquals(
            listOf(ProxyDelayTarget("Relay", ProxyDelayRequest("node-a", "https://nested"))),
            resolveDelayTargets(group, listOf(group, nested)),
        )
    }

    @Test
    fun `batch delay target cycle is ignored instead of recursing forever`() {
        val first = ProxyGroupInfo("First", "Selector", "Second", listOf("Second"), false)
        val second = ProxyGroupInfo("Second", "Selector", "First", listOf("First"), false)
        val group = ProxyGroupInfo("Auto", "URLTest", null, listOf("First"), false)

        assertTrue(resolveDelayTargets(group, listOf(group, first, second)).isEmpty())
    }

    @Test
    fun `selection resolver follows three nested groups and selected map`() {
        val leaf = ProxyGroupInfo("Leaf", "Selector", "node-c", listOf("node-c"), false)
        val middle = ProxyGroupInfo("Middle", "Selector", "Leaf", listOf("Leaf"), false)
        val outer = ProxyGroupInfo("Outer", "URLTest", "Middle", listOf("Middle"), false)

        val resolved = resolveSelectedProxy(outer, listOf(outer, middle, leaf))

        assertEquals("node-c", resolved.leafName)
        assertEquals(listOf("Outer", "Middle", "Leaf", "node-c"), resolved.path)
        assertTrue(!resolved.cycleDetected)
    }

    @Test
    fun `selection resolver rejects nested cycles`() {
        val first = ProxyGroupInfo("First", "Selector", "Second", listOf("Second"), false)
        val second = ProxyGroupInfo("Second", "Selector", "First", listOf("First"), false)

        val resolved = resolveSelectedProxy(first, listOf(first, second))

        assertEquals(null, resolved.leafName)
        assertTrue(resolved.cycleDetected)
    }

    @Test
    fun `computed selected name prefers explicit pin then fixed then automatic now`() {
        val group = ProxyGroupInfo(
            name = "Auto",
            type = "URLTest",
            now = "auto-node",
            fixed = "fixed-node",
            all = listOf("auto-node", "fixed-node", "pinned-node"),
            hidden = false,
        )

        assertEquals("fixed-node", selectedProxyName(group))
        assertEquals("pinned-node", selectedProxyName(group, mapOf("Auto" to "pinned-node")))
    }
}
