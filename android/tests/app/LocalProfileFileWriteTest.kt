package com.lonnnnnng.flclash.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalProfileFileWriteTest {
    @get:Rule val folder = TemporaryFolder()
    private fun file() = File(folder.root, "profile.yaml").apply { writeText("original"); setLastModified(1_600_000_000_000L) }
    private fun noTemporaryFiles() = assertTrue(folder.root.listFiles()!!.all { it.name == "profile.yaml" })

    @Test fun `document reader rejects empty and oversized streams and preserves exact bytes`() {
        assertArrayEquals(byteArrayOf(0, 13, 10, -1), readProfileFileBytes(ByteArrayInputStream(byteArrayOf(0, 13, 10, -1))))
        assertTrue(runCatching { readProfileFileBytes(ByteArrayInputStream(byteArrayOf())) }.isFailure)
        assertEquals(MAX_PROFILE_FILE_BYTES, readProfileFileBytes(ByteArrayInputStream(ByteArray(MAX_PROFILE_FILE_BYTES))).size)
        assertTrue(runCatching { readProfileFileBytes(ByteArrayInputStream(ByteArray(MAX_PROFILE_FILE_BYTES + 1))) }.isFailure)
    }

    @Test fun `image budget accepts exact limit and stops reading at first excess byte`() {
        val limit = 128 * 1024
        val exact = ByteArray(limit) { (it % 251).toByte() }
        assertArrayEquals(exact, readProfileFileBytes(ByteArrayInputStream(exact), maxBytes = limit))
        val oversized = ByteArrayInputStream(ByteArray(limit * 3))
        assertEquals("文件超过允许大小", runCatching { readProfileFileBytes(oversized, maxBytes = limit) }.exceptionOrNull()?.message)
        // 作者 long：只读到上限后一字节即可判超限，不能为了报错先把云端整份图片读入内存。
        assertEquals(limit * 2 - 1, oversized.available())
    }

    @Test fun `staging does not modify original until persistence commits the file`() = runBlocking {
        val target = file()
        val result = replaceLocalProfileFile(target, "replacement".toByteArray(), { commit ->
            assertEquals("original", target.readText())
            commit()
            "persisted"
        })
        assertEquals("persisted", result)
        assertEquals("replacement", target.readText())
        noTemporaryFiles()
    }

    @Test fun `database failure before rename leaves original and timestamp intact`() = runBlocking {
        val target = file()
        val modified = target.lastModified()
        assertTrue(runCatching { replaceLocalProfileFile(target, "replacement".toByteArray(), { _: () -> Unit -> throw IOException("database") }) }.isFailure)
        assertEquals("original", target.readText())
        assertEquals(modified, target.lastModified())
        noTemporaryFiles()
    }

    @Test fun `database failure after rename restores original bytes and timestamp`() = runBlocking {
        val target = file()
        val modified = target.lastModified()
        assertTrue(runCatching { replaceLocalProfileFile(target, "replacement".toByteArray(), { commit -> commit(); throw IOException("commit") }) }.isFailure)
        assertEquals("original", target.readText())
        assertEquals(modified, target.lastModified())
        noTemporaryFiles()
    }

    @Test fun `rename failure never falls back to truncating original`() = runBlocking {
        val target = file()
        assertTrue(runCatching { replaceLocalProfileFile(target, "replacement".toByteArray(), { commit -> commit() }, rename = { _, _ -> false }) }.isFailure)
        assertEquals("original", target.readText())
        noTemporaryFiles()
    }

    @Test fun `backup timestamp failure aborts before database persistence or replacement`() = runBlocking {
        val target = file()
        val modified = target.lastModified()
        var persisted = false
        assertTrue(runCatching {
            replaceLocalProfileFile(target, "replacement".toByteArray(), { commit ->
                persisted = true
                commit()
            }, setModified = { _, _ -> false })
        }.isFailure)
        assertFalse(persisted)
        assertEquals("original", target.readText())
        assertEquals(modified, target.lastModified())
        noTemporaryFiles()
    }

    @Test fun `same size same timestamp external replacement rejects stale commit without reverting it`() = runBlocking {
        val target = file()
        val modified = target.lastModified()
        assertTrue(runCatching { replaceLocalProfileFile(target, "replacement".toByteArray(), { commit ->
            target.writeText("external")
            target.setLastModified(modified)
            commit()
        }) }.isFailure)
        assertEquals("external", target.readText())
        noTemporaryFiles()
    }

    @Test fun `missing and empty original can be repaired and cancellation rolls new file back`() = runBlocking {
        val target = File(folder.root, "profile.yaml")
        val failure = runCatching { replaceLocalProfileFile(target, "replacement".toByteArray(), { commit -> commit(); throw CancellationException("cancel") }) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertFalse(target.exists())
        target.createNewFile()
        replaceLocalProfileFile(target, "replacement".toByteArray(), { commit -> commit() })
        assertEquals("replacement", target.readText())
        noTemporaryFiles()
    }

    @Test fun `failed rollback preserves exclusive original backup for recovery`() = runBlocking {
        val target = file()
        assertTrue(runCatching { replaceLocalProfileFile(target, "replacement".toByteArray(), { commit -> commit(); throw IOException("commit") },
            rename = { from, to -> if (from.name.startsWith(".profile-old-")) false else from.renameTo(to) }) }.isFailure)
        val backup = folder.root.listFiles()!!.single { it.name.startsWith(".profile-old-") }
        assertEquals("original", backup.readText())
    }
}
