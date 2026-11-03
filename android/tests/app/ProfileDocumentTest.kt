package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class ProfileDocumentTest {
    @Test fun strictTextPreservesSpacesAndBomButRejectsMalformedBinaryAndOverBudget() {
        assertEquals(" a \n", profileDocumentText("\uFEFF a \n".toByteArray()))
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), byteArrayOf(0), byteArrayOf(), "  ".toByteArray(), ByteArray(MAX_PROFILE_FILE_BYTES + 1))) {
            assertTrue(runCatching { profileDocumentText(bytes) }.isFailure)
        }
        assertEquals(profileDocumentDigest("a".toByteArray()), profileDocumentDigest("a".toByteArray()))
        assertNotEquals(profileDocumentDigest("a".toByteArray()), profileDocumentDigest("b".toByteArray()))
        val profile = Profile.remote(1, "https://example.test/?key=secret")
        assertFalse(ProfileDocumentSnapshot(profile, "secret", "digest", 0).toString().contains("secret"))
        assertFalse(ProfileDocumentSettings("secret", profile.url, "secret", true, 1).toString().contains("secret"))
    }
}
