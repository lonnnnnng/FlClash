package com.lonnnnnng.flclash.data

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileExportTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `export preserves raw cached bytes including BOM and line endings`() {
        val bytes = byteArrayOf(-17, -69, -65, 0, 10, 13, -1)
        val file = temp.newFile().apply { writeBytes(bytes) }
        val modified = file.lastModified()
        val export = readProfileExport(file, "家庭网络")
        assertArrayEquals(bytes, export.bytes)
        assertEquals("家庭网络.yaml", export.fileName)
        assertEquals(modified, file.lastModified())
    }

    @Test fun `missing empty and oversized caches fail without creating a file`() {
        val missing = File(temp.root, "missing.yaml")
        assertEquals("配置文件不存在", runCatching { readProfileExport(missing, "x") }.exceptionOrNull()?.message)
        assertFalse(missing.exists())
        val empty = temp.newFile()
        assertEquals("配置文件为空", runCatching { readProfileExport(empty, "x") }.exceptionOrNull()?.message)
        val large = temp.newFile().apply { outputStream().use { it.write(ByteArray(8 * 1024 * 1024 + 1)) } }
        assertEquals("配置超过 8 MiB，暂不支持导出", runCatching { readProfileExport(large, "x") }.exceptionOrNull()?.message)
    }

    @Test fun `filename removes separators control characters and traversal labels`() {
        assertEquals("家庭__网络_.yaml", profileExportFileName("家庭/\\网络\n"))
        assertEquals("配置.yaml", profileExportFileName(" .. "))
        assertEquals(85, profileExportFileName("a".repeat(100)).length)
    }
}
