package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class ScriptImportTest {
    @Test fun utf8ImportPreservesWhitespaceAndRejectsMalformedBinaryAndOversize() {
        assertEquals("\nconst main = config => config;\n", decodeScriptBytes("\uFEFF\nconst main = config => config;\n".toByteArray()))
        for (bytes in listOf(byteArrayOf(0xC3.toByte(), 0x28), "text\u0000data".toByteArray(), ByteArray(SCRIPT_MAX_BYTES + 1), byteArrayOf())) {
            assertTrue(runCatching { decodeScriptBytes(bytes) }.isFailure)
        }
        val bytes = ByteArray(SCRIPT_MAX_BYTES + 1) { 65 }
        assertTrue(runCatching { readScriptBytes(bytes.inputStream()) }.isFailure)
        assertArrayEquals("hello".toByteArray(), readScriptBytes("hello".byteInputStream()))
    }
    @Test fun importedNamesUseFirstAvailableSuffix() {
        assertEquals("脚本-02", uniqueScriptLabel("脚本", listOf("脚本", "脚本-01", "脚本-03")))
        assertEquals("脚本", uniqueScriptLabel(" ", emptyList()))
    }
}
