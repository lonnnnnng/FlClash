package com.lonnnnnng.flclash.data

import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.*
import org.junit.Assert.*
import org.junit.Test

class ApplicationSettingsBackupTest {
    @Test fun ssidBackupDistinguishesLegacyMissingFromExplicitClearAndRejectsMalformedNames() {
        val legacy = ApplicationSettingsBackupCodec.encode(ApplicationSettingsBackup())
        assertNull(ApplicationSettingsBackupCodec.decode(legacy).onDemandSsids)
        for (names in listOf(emptyList(), listOf(" Home ", "home", "😀"))) {
            val value = ApplicationSettingsBackup(onDemandSsids = names)
            assertEquals(value, ApplicationSettingsBackupCodec.decode(ApplicationSettingsBackupCodec.encode(value)))
        }
        for (raw in listOf("null", "true", "[1]", "[\"\"]", "[\"A\",\"A\"]")) {
            val root = JsonParser.parseString(legacy).asJsonObject
            root.add("onDemandSsids", JsonParser.parseString(raw))
            assertTrue(runCatching { ApplicationSettingsBackupCodec.decode(root.toString()) }.isFailure)
        }
        val store = object : OnDemandSettingsStore {
            var current = listOf("preserved")
            override fun read() = current
            override fun write(value: List<String>) { current = value }
        }
        val aggregate = ApplicationSettingsBackupStore(onDemand = store)
        aggregate.write(ApplicationSettingsBackupCodec.decode(legacy))
        assertEquals(listOf("preserved"), store.current)
        aggregate.write(ApplicationSettingsBackup(onDemandSsids = emptyList()))
        assertTrue(store.current.isEmpty())
        aggregate.compensate(ApplicationSettingsBackup(onDemandSsids = listOf("preserved")))
        assertEquals(listOf("preserved"), aggregate.read().onDemandSsids)
    }
    @Test fun roundTripRetainsExplicitDnsEmptyListsFalseAndNullLan() {
        val value = ApplicationSettingsBackup(GeneralSettings(authentication = true, username = "alice", password = "private",
            externalController = true, controllerSecret = "secret", hosts = mapOf("test.invalid" to "192.0.2.1")),
            NetworkSettings(systemProxy = false, routeAddress = listOf("192.0.2.0/24")),
            ProfileDnsOverrides(listen = "", ipv6 = false, nameserver = emptyList()), false, null)
        assertEquals(value, ApplicationSettingsBackupCodec.decode(ApplicationSettingsBackupCodec.encode(value)))
        assertFalse(value.toString().contains("private")); assertFalse(value.toString().contains("secret"))
    }

    @Test fun malformedTypesMissingKeysAndInvalidPortsAreRejected() {
        val original = ApplicationSettingsBackupCodec.encode(ApplicationSettingsBackup())
        val mutations: List<(com.google.gson.JsonObject) -> Unit> = listOf(
            { it.getAsJsonObject("general").addProperty("mixedPort", "7890") },
            { it.getAsJsonObject("general").remove("checkCertificate") },
            { it.getAsJsonObject("general").addProperty("port", 7890) },
            { it.getAsJsonObject("network").addProperty("systemProxy", "true") },
            { it.getAsJsonObject("dns").addProperty("ipv6", "false") },
            { it.getAsJsonObject("dns").addProperty("enable", true) },
            { it.addProperty("allowLan", "false") },
        )
        mutations.forEach { mutate ->
            val json = JsonParser.parseString(original).asJsonObject; mutate(json)
            assertTrue(runCatching { ApplicationSettingsBackupCodec.decode(json.toString()) }.isFailure)
        }
    }
}
