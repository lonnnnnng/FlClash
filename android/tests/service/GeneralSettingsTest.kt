package com.lonnnnnng.flclash.service

import com.google.gson.JsonParser
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
    }

    @Test fun coreProjectionDoesNotChangeForBackgroundAndNotificationPreferences() {
        val value = GeneralSettings()
        assertEquals(value.runtimeKey(), value.copy(hidden = true, autoRun = true, minimizeOnExit = false,
            showNotificationStopAction = false, openLogs = true, onlyStatisticsProxy = true, delayTestTimeout = 500).runtimeKey())
        assertNotEquals(value.runtimeKey(), value.copy(checkCertificate = false).runtimeKey())
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
}
