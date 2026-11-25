package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.CoreBridge
import org.junit.Assert.*
import org.junit.Test

class GeneralSettingsTest {
    @Test fun portsRejectConflictsRangesAndDisabledMixed() {
        assertThrows(IllegalArgumentException::class.java) { GeneralSettings(mixedPort = 0).validated() }
        assertThrows(IllegalArgumentException::class.java) { GeneralSettings(port = 7890).validated() }
        assertThrows(IllegalArgumentException::class.java) { GeneralSettings(port = 80).validated() }
        GeneralSettings(port = 18890, socksPort = 18891).validated()
    }

    @Test fun generatedCredentialsAndControllerSecretAreUsableAndRedacted() {
        val value = GeneralSettings(authentication = true, externalController = true).prepared()
        assertEquals(8, value.username.length); assertEquals(16, value.password.length); assertEquals(32, value.controllerSecret.length)
        assertFalse(value.toString().contains(value.password)); assertFalse(value.toString().contains(value.controllerSecret))
        assertEquals("alice", value.copy(username = " a:li:ce ").prepared().username)
        assertEquals("", value.copy(externalController = false).prepared().controllerSecret)
    }

    @Test fun coreProjectionDoesNotChangeForBackgroundAndNotificationPreferences() {
        val value = GeneralSettings()
        assertEquals(value.runtimeKey(), value.copy(hidden = true, autoRun = true, minimizeOnExit = false,
            showNotificationStopAction = false, openLogs = true, onlyStatisticsProxy = true).runtimeKey())
        assertEquals(value.runtimeKey(), value.copy(unifiedDelay = false, tcpConcurrent = false, ipv6 = true,
            geodataLoader = "standard").runtimeKey())
        assertEquals(value.runtimeKey(), value.copy(generalOverrideKeys = setOf(GeneralCoreOverrideKey.TEST_URL)).runtimeKey())
        assertNotEquals(value.runtimeKey(), value.copy(delayTestTimeout = 500).runtimeKey())
        assertNotEquals(value.runtimeKey(), value.copy(testUrl = "https://probe.example/").runtimeKey())
        assertEquals(value.configurationReloadKey(), value.copy(delayTestTimeout = 500,
            testUrl = "https://probe.example/").configurationReloadKey())
        assertNotEquals(value.runtimeKey(), value.copy(generalOverrideKeys = setOf(GeneralCoreOverrideKey.IPV6)).runtimeKey())
        assertNotEquals(value.runtimeKey(), value.copy(checkCertificate = false).runtimeKey())
        assertEquals(CoreBridge.DEFAULT_TEST_URL, GeneralSettings().testUrl)
    }

    @Test fun applicationProbeOptionsAreProjectedToLiveCoreUpdates() {
        val before = GeneralSettings()
        val update = before.copy(testUrl = "https://probe.example/", delayTestTimeout = 500).coreUpdateFrom(before)
        assertEquals("https://probe.example/", update.testUrl)
        assertEquals(500L, update.delayTestTimeoutMs)
        assertTrue(update.hasValues())
    }

    @Test fun applierPreservesUnknownsMergesHostsAndAppendsSystemDnsOnce() {
        val source = "dns: {enable: true, nameserver: ['system://', 1.1.1.1]}\nhosts: {source.test: 192.0.2.1}\ncustom-field: {preserve: true}\nauthentication: [old:old]\nskip-auth-prefixes: [127.0.0.1/32]\n"
        val value = GeneralSettings(mixedPort = 18890, authentication = true, username = "user", password = "pass",
            appendSystemDns = true, hosts = mapOf("added.test" to "192.0.2.2, 2001:db8::1"))
        val root = JsonParser.parseString(YamlConfigCodec.toJson(GeneralSettingsApplier.apply(source, value))).asJsonObject
        assertEquals(18890, root.get("mixed-port").asInt)
        assertEquals("user:pass", root.getAsJsonArray("authentication").single().asString)
        assertEquals(0, root.getAsJsonArray("skip-auth-prefixes").size())
        assertTrue(root.getAsJsonObject("custom-field").get("preserve").asBoolean)
        assertTrue(root.getAsJsonObject("hosts").has("source.test"))
        assertEquals(2, root.getAsJsonObject("hosts").getAsJsonArray("added.test").size())
        assertEquals(1, root.getAsJsonObject("dns").getAsJsonArray("nameserver").count { it.asString == "system://" })
        assertEquals(source, GeneralSettingsApplier.apply(source, null))
    }

    @Test fun defaultUserAgentReplacesProfileValueAndCustomChoiceWins() {
        val source = "global-ua: subscription-agent\n"
        val fallback = "KClash/v0.1.21 clash-verge Platform/android"
        val defaultRoot = JsonParser.parseString(YamlConfigCodec.toJson(
            GeneralSettingsApplier.apply(source, GeneralSettings(), fallback),
        )).asJsonObject
        val customRoot = JsonParser.parseString(YamlConfigCodec.toJson(
            GeneralSettingsApplier.apply(source, GeneralSettings(userAgent = "custom-agent"), fallback),
        )).asJsonObject
        assertEquals(fallback, defaultRoot.get("global-ua").asString)
        assertEquals("custom-agent", customRoot.get("global-ua").asString)
    }

    @Test fun optionalGeneralCoreOverridesKeepSourceUntilSelectedAndAllowExplicitFalse() {
        val source = "unified-delay: false\ntcp-concurrent: false\nipv6: true\ngeodata-loader: standard\n"
        val inherited = JsonParser.parseString(YamlConfigCodec.toJson(
            GeneralSettingsApplier.apply(source, GeneralSettings()),
        )).asJsonObject
        val overridden = JsonParser.parseString(YamlConfigCodec.toJson(GeneralSettingsApplier.apply(source,
            GeneralSettings(generalOverrideKeys = setOf(GeneralCoreOverrideKey.UNIFIED_DELAY,
                GeneralCoreOverrideKey.TCP_CONCURRENT, GeneralCoreOverrideKey.IPV6, GeneralCoreOverrideKey.GEODATA_LOADER),
                unifiedDelay = true, tcpConcurrent = true, ipv6 = false, geodataLoader = "memconservative",
                testUrl = "https://probe.example/")))).asJsonObject
        assertFalse(inherited.get("unified-delay").asBoolean)
        assertFalse(inherited.get("tcp-concurrent").asBoolean)
        assertTrue(inherited.get("ipv6").asBoolean)
        assertEquals("standard", inherited.get("geodata-loader").asString)
        assertTrue(overridden.get("unified-delay").asBoolean)
        assertTrue(overridden.get("tcp-concurrent").asBoolean)
        assertFalse(overridden.get("ipv6").asBoolean)
        assertEquals("memconservative", overridden.get("geodata-loader").asString)
        assertFalse(overridden.has("test-url"))
    }

    @Test fun invalidCoreTestUrlAndGeodataLoaderAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GeneralSettings(testUrl = "file:///etc/passwd").validated() }
        assertThrows(IllegalArgumentException::class.java) { GeneralSettings(geodataLoader = "unknown").validated() }
    }

    @Test fun newCoreSettingsKeepDefaultsWhenOldPreferencesOmitTheirFields() {
        val decoded = com.google.gson.Gson().fromJson("{}", GeneralSettings::class.java).validated()
        assertTrue(decoded.generalOverrideKeys.isEmpty())
        assertEquals(CoreBridge.DEFAULT_TEST_URL, decoded.testUrl)
        assertTrue(decoded.unifiedDelay)
        assertTrue(decoded.tcpConcurrent)
        assertFalse(decoded.ipv6)
        assertEquals("memconservative", decoded.geodataLoader)
    }
}
