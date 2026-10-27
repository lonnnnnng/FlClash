package com.lonnnnnng.flclash.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionAutoUpdateTest {
    @Test
    fun `never updated remote profile is due`() {
        val profile = Profile.remote(1L, "https://example.test/subscription")

        assertTrue(isAutoUpdateDue(profile, nowMillis = 1_000L))
    }

    @Test
    fun `profile is due only after its interval`() {
        val profile = Profile.remote(2L, "https://example.test/subscription").copy(
            lastUpdateAt = 10_000L,
            autoUpdateIntervalMillis = 15 * 60 * 1_000L,
        )

        assertFalse(isAutoUpdateDue(profile, nowMillis = 909_999L))
        assertTrue(isAutoUpdateDue(profile, nowMillis = 910_000L))
    }

    @Test
    fun `yaml validator accepts object and rejects scalar`() = runBlocking {
        val validator = YamlProfileConfigValidator()
        validator.validate("mixed-port: 7890\n".toByteArray())
        val error = runCatching {
            validator.validate("- invalid\n- root\n".toByteArray())
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
