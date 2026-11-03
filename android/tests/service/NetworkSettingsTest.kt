package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NetworkSettingsTest {
    private class Store(var settings: NetworkSettings) : NetworkSettingsStore {
        override fun read() = settings
        override fun write(value: NetworkSettings) { settings = value }
    }

    @Test fun excludesSubnetsAndNormalizesHostBitsWithoutLosingOtherAddressFamilies() {
        val settings = NetworkSettings(bypassPrivateRoute = false,
            routeAddress = listOf("192.0.2.99/24", "2001:db8::23/120"),
            routeExcludeAddress = listOf("192.0.2.0/26", "2001:db8::80/121"))
        val routes = settings.validated().vpnRoutes().map(::parseNetworkCidr)
        assertEquals(listOf(parseNetworkCidr("192.0.2.64/26"), parseNetworkCidr("192.0.2.128/25"), parseNetworkCidr("2001:db8::/121")), routes)
        assertEquals(0, settings.copy(routeExcludeAddress = settings.routeAddress).vpnRoutes().size)
    }

    @Test fun wholeInternetMinusPrivateNetworksContainsPublicAndDnsAddressesOnly() {
        val routes = NetworkSettings().vpnRoutes().map(::parseNetworkCidr)
        fun contains(ip: String): Boolean {
            val address = parseNetworkCidr(ip)
            return routes.any { it.bits == address.bits && address.start >= it.start && address.end <= it.end }
        }
        assertTrue(contains("198.18.0.2/32"))
        assertTrue(contains("8.8.8.8/32"))
        assertTrue(contains("2001:4860:4860::8888/128"))
        assertFalse(contains("192.168.1.1/32"))
        assertFalse(contains("fe80::1/128"))
        assertFalse(contains("fc00::1/128"))
        assertTrue(NetworkSettings(bypassPrivateRoute = false).vpnRoutes().isEmpty())
    }

    @Test fun rejectHostnamesInvalidPrefixesAndOutOfRangeSettingsBeforeSaving() {
        for (cidr in listOf("example.com/24", "1.2.3.4/33", "::/129", "1.2.3/24", "256.0.0.1/8", "fe80::1%en0/64")) {
            assertTrue(cidr, runCatching { NetworkSettings(routeAddress = listOf(cidr)).validated() }.isFailure)
        }
        assertTrue(runCatching { NetworkSettings(mtu = 1279).validated() }.isFailure)
        assertTrue(runCatching { NetworkSettings(stack = "invalid").validated() }.isFailure)
        assertTrue(runCatching { NetworkSettings(bypassDomain = listOf("https://example.com")).validated() }.isFailure)
    }

    @Test fun networkProjectionKeepsDnsBaselineAuthenticationAndUnknownFields() {
        val original = "mixed-port: 7890\nauthentication: [test:fixture]\ncustom-field: keep\ntun: {dns-hijack: [any:53], udp-timeout: 123}\n"
        val projected = patchNetworkSettings(ProfileDnsBaselineApplier.apply(original), NetworkSettings(stack = "system", mtu = 1500))
        val root = JsonParser.parseString(YamlConfigCodec.toJson(projected)).asJsonObject
        assertTrue(root["dns"].asJsonObject["enable"].asBoolean)
        assertEquals("keep", root["custom-field"].asString)
        assertEquals("test:fixture", root["authentication"].asJsonArray[0].asString)
        val tun = root["tun"].asJsonObject
        assertEquals("any:53", tun["dns-hijack"].asJsonArray[0].asString)
        assertEquals(123, tun["udp-timeout"].asInt)
        assertEquals("system", tun["stack"].asString)
        assertEquals(1500, tun["mtu"].asInt)
    }

    @Test fun vpnOptionsUseSavedSettingsAndDistinguishAllRoutesFromNoRoutes() = runTest {
        val store = Store(NetworkSettings(ipv6 = true, dnsHijacking = true, allowBypass = false, systemProxy = false,
            bypassPrivateRoute = false, routeAddress = listOf("0.0.0.0/0", "::/0"), routeExcludeAddress = listOf("0.0.0.0/0", "::/0")))
        val provider = DefaultVpnOptionsProvider(networkSettingsStore = store)
        val excluded = provider.current()
        assertTrue(excluded.ipv6)
        assertTrue(excluded.dnsHijacking)
        assertFalse(excluded.allowBypass)
        assertFalse(excluded.systemProxy)
        assertTrue(excluded.routeAddress.isEmpty())
        assertFalse(excluded.routeAll)
        store.settings = NetworkSettings(bypassPrivateRoute = false)
        assertTrue(provider.current().routeAll)
        assertTrue(provider.current().systemProxy)
    }
}
