package com.lonnnnnng.flclash.service

import org.junit.Assert.*
import org.junit.Test

class OnDemandSettingsTest {
    @Test fun exactNamesRetainWhitespaceCaseAndUnicode() {
        val names = OnDemandSettings.validate(listOf(" Home ", "home", "😀".repeat(32)))
        assertTrue(OnDemandSettings.matches(names, " Home "))
        assertFalse(OnDemandSettings.matches(names, "Home"))
        assertFalse(OnDemandSettings.matches(names, null))
        assertEquals(" Home ", OnDemandSettings.normalizeSsid("\" Home \""))
        assertNull(OnDemandSettings.normalizeSsid("<unknown ssid>"))
        assertNull(OnDemandSettings.normalizeSsid("0x"))
        assertEquals(" ", OnDemandSettings.normalizeSsid("\" \""))
    }

    @Test fun invalidAndDuplicateNamesAreRejectedAndStaleEditorCannotOverwriteRestore() {
        listOf(listOf(""), listOf("A", "A"), listOf("😀".repeat(33)), listOf("a\nb"), listOf("a\u0000b")).forEach {
            assertTrue(runCatching { OnDemandSettings.validate(it) }.isFailure)
        }
        val store = object : OnDemandSettingsStore {
            var value = listOf("old")
            override fun read() = value
            override fun write(value: List<String>) { this.value = OnDemandSettings.validate(value) }
        }
        val expected = store.read()
        store.write(listOf("restored"))
        assertTrue(runCatching { store.compareAndSet(expected, listOf("editor")) }.isFailure)
        assertEquals(listOf("restored"), store.read())
    }
}
