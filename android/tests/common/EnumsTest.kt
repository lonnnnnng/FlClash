package com.lonnnnnng.flclash.common

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Flutter layer exchanges these enums as JSON, so the serialized spelling is a cross-language
 * contract rather than an implementation detail.
 */
class EnumsTest {
    private val gson = Gson()

    @Test
    fun `access control mode serializes to the Dart spelling`() {
        assertEquals("\"acceptSelected\"", gson.toJson(AccessControlMode.ACCEPT_SELECTED))
        assertEquals("\"rejectSelected\"", gson.toJson(AccessControlMode.REJECT_SELECTED))
    }

    @Test
    fun `access control mode deserializes from the Dart spelling`() {
        assertEquals(
            AccessControlMode.ACCEPT_SELECTED,
            gson.fromJson("\"acceptSelected\"", AccessControlMode::class.java),
        )
        assertEquals(
            AccessControlMode.REJECT_SELECTED,
            gson.fromJson("\"rejectSelected\"", AccessControlMode::class.java),
        )
    }

    @Test
    fun `quick action names back the intent action suffixes`() {
        assertEquals(
            listOf("STOP", "START", "TOGGLE"),
            QuickAction.entries.map { it.name },
        )
    }

    @Test
    fun `broadcast action names back the intent action suffixes`() {
        assertEquals(
            listOf("VPN_START_REQUESTED", "VPN_REVOKED"),
            BroadcastAction.entries.map { it.name },
        )
    }

    @Test
    fun `external action contract accepts only fixed actions without data or extras`() {
        QuickAction.entries.forEach { action ->
            assertEquals(
                true,
                ExternalActionContract.accepts(
                    action = "${Components.PACKAGE_NAME}.action.${action.name}",
                    hasData = false,
                    hasExtras = false,
                ),
            )
        }
        assertEquals(false, ExternalActionContract.accepts("${Components.PACKAGE_NAME}.action.UNKNOWN", false, false))
        assertEquals(false, ExternalActionContract.accepts("${Components.PACKAGE_NAME}.action.START", true, false))
        assertEquals(false, ExternalActionContract.accepts("${Components.PACKAGE_NAME}.action.START", false, true))
        assertEquals(false, ExternalActionContract.accepts(null, false, false))
    }
}
