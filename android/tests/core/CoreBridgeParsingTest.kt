package com.lonnnnnng.flclash.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBridgeParsingTest {
    private val bridge = CoreBridge()

    @Test
    fun `proxy group response preserves core ordering and selector members`() {
        val result = bridge.parseProxyGroupsPayload(
            names = listOf("GLOBAL", "媒体"),
            payloads = mapOf(
                "GLOBAL" to ProxyGroupPayload(
                    name = "GLOBAL",
                    type = "Selector",
                    now = "自动",
                    all = listOf("自动", "直连"),
                    hidden = false,
                    testUrl = "https://example.com",
                ),
                "媒体" to ProxyGroupPayload(
                    name = "媒体",
                    type = "URLTest",
                    now = "香港-01",
                    all = listOf("香港-01"),
                    hidden = false,
                ),
            ),
        )

        assertEquals(listOf("GLOBAL", "媒体"), result.groups.map { it.name })
        assertEquals(listOf("自动", "直连"), result.groups.first().all)
        assertTrue(result.groups.first().isSelector)
        assertEquals("https://example.com", result.groups.first().testUrl)
        assertFalse(result.groups[1].isSelector)
        assertEquals("香港-01", result.groups[1].now)
    }

    @Test
    fun `change proxy response exposes changed flag`() {
        assertTrue(bridge.parseChangeProxyPayload(message = "", changed = true))
        assertFalse(bridge.parseChangeProxyPayload(message = "", changed = false))
    }

    @Test
    fun `change proxy response turns core message into typed failure`() {
        val error = assertThrows(CoreBridgeException::class.java) {
            bridge.parseChangeProxyPayload(message = "节点不存在", changed = false)
        }

        assertEquals("change_proxy_failed", error.code)
        assertEquals("节点不存在", error.message)
    }

    @Test
    fun `delay payload preserves success timeout and failure values`() {
        assertEquals(
            ProxyDelay(url = "https://example.com", name = "香港-01", value = 123),
            bridge.parseDelayPayload(
                ProxyDelayPayload(
                    url = "https://example.com",
                    name = "香港-01",
                    value = 123,
                ),
            ),
        )
        assertEquals(
            -1,
            bridge.parseDelayPayload(ProxyDelayPayload("", "香港-01", -1))?.value,
        )
        assertEquals(
            -2,
            bridge.parseDelayPayload(ProxyDelayPayload("", "香港-01", -2))?.value,
        )
    }

    @Test
    fun `empty delay result means no concurrent slot result`() {
        assertEquals(null, bridge.parseDelayPayload(null))
    }
}
