package com.lonnnnnng.flclash.data

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileContentDecoderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val armored = "-----BEGIN AGE ENCRYPTED FILE-----\nunit fixture".toByteArray()

    @Test fun `only exact armor prefix triggers decryption and plaintext ignores invalid key`() = runBlocking {
        var calls = 0
        val decoder = AgeProfileContentDecoder(temporary.root) { _, _ -> calls++; error("unexpected") }
        for (bytes in listOf("mode: rule\n".toByteArray(), byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + armored, " ".toByteArray() + armored)) {
            assertArrayEquals(bytes, decoder.decode(bytes, "invalid fixture identity"))
        }
        assertEquals(0, calls)
    }

    @Test fun `missing key does not create temporary file or invoke bridge`() = runBlocking {
        val decoder = AgeProfileContentDecoder(temporary.root) { _, _ -> error("unexpected") }
        val failure = runCatching { decoder.decode(armored, " \n ") }.exceptionOrNull()
        assertTrue(failure is AgeSecretKeyRequiredException)
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `decoder trims key passes exact ciphertext and returns only plaintext`() = runBlocking {
        var input: File? = null
        val decoder = AgeProfileContentDecoder(temporary.root) { path, key ->
            input = File(path)
            assertArrayEquals(armored, input!!.readBytes())
            assertTrue(key == "unit-identity")
            "mode: rule\n"
        }
        assertArrayEquals("mode: rule\n".toByteArray(), decoder.decode(armored, " unit-identity\n"))
        assertFalse(input!!.exists())
    }

    @Test fun `bridge errors cannot leak key or cause and temporary file is removed`() = runBlocking {
        val secret = "private-unit-identity"
        val decoder = AgeProfileContentDecoder(temporary.root) { _, key -> error("parse identity $key") }
        val failure = runCatching { decoder.decode(armored, secret) }.exceptionOrNull()!!
        assertEquals("age 解密失败，请检查密钥与配置文件", failure.message)
        // 作者 long：协程栈恢复可以复制安全异常为 cause；逐层确认不包含原始 identity，而非约束 cause 的实现形式。
        assertTrue(generateSequence(failure) { it.cause }.all { !it.toString().contains(secret) })
        assertFalse(failure.toString().contains(secret))
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `cancellation propagates and removes temporary ciphertext`() = runBlocking {
        val decoder = AgeProfileContentDecoder(temporary.root) { _, _ -> throw CancellationException("cancel") }
        assertTrue(runCatching { decoder.decode(armored, "fixture") }.exceptionOrNull() is CancellationException)
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `oversized input and empty or oversized decrypted output are rejected`() = runBlocking {
        val decoder = AgeProfileContentDecoder(temporary.root) { _, _ -> "" }
        assertTrue(runCatching { decoder.decode(ByteArray(8 * 1024 * 1024 + 1), "") }.isFailure)
        assertTrue(runCatching { decoder.decode(armored, "fixture") }.isFailure)
        val oversized = AgeProfileContentDecoder(temporary.root) { _, _ -> "x".repeat(8 * 1024 * 1024 + 1) }
        assertTrue(runCatching { oversized.decode(armored, "fixture") }.isFailure)
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `profile string representations do not disclose subscription or age key`() {
        val profile = Profile.remote(1, "https://example.test/private-query").copy(ageSecretKey = "private-unit-identity")
        for (text in listOf(profile.toString(), profile.toEntity().toString())) {
            assertFalse(text.contains(profile.ageSecretKey))
            assertFalse(text.contains(profile.url))
        }
    }
}
