package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryInstrumentedTest {
    private lateinit var database: KClashDatabase
    private lateinit var root: File
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
        repository = BackupRepository(database, root)
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun allDataRoundTripRestoresRoomFilesAndSelection() = runBlocking {
        val profile = profile(1001, scriptId = 2001)
        val script = ScriptEntity(2001, "测试脚本", "", 10L, 0)
        database.profiles().insert(profile)
        database.scripts().insert(script)
        database.appPreferences().put(AppPreferenceEntity("current_profile_id", "1001", 10L))
        write("profiles/1001.yaml", "mixed-port: 7890")
        write("scripts/2001.js", "const main = (config) => config")

        val archive = repository.createArchive()
        database.profiles().deleteAll()
        database.scripts().deleteAll()
        write("profiles/1001.yaml", "broken")

        repository.restore(archive, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)

        assertEquals(listOf(1001L), database.profiles().getAll().map(ProfileEntity::id))
        assertEquals(listOf(2001L), database.scripts().getAll().map(ScriptEntity::id))
        assertEquals("1001", database.appPreferences().get("current_profile_id"))
        assertEquals("mixed-port: 7890", File(root, "profiles/1001.yaml").readText())
        assertEquals("const main = (config) => config", File(root, "scripts/2001.js").readText())
    }

    @Test
    fun compatibleRestoreKeepsUnincludedRowsAndProfilesOnlyClearsScriptBinding() = runBlocking {
        val profile = profile(1002, scriptId = null)
        val extra = profile(1003, scriptId = null)
        database.profiles().insert(profile)
        database.profiles().insert(extra)
        write("profiles/1002.yaml", "a: 1")
        write("profiles/1003.yaml", "b: 2")

        val archive = repository.createArchive(BackupRestoreScope.PROFILES_ONLY)
        database.profiles().delete(extra)
        repository.restore(archive, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.COMPATIBLE)

        assertEquals(setOf(1002L, 1003L), database.profiles().getAll().map(ProfileEntity::id).toSet())
        assertEquals("a: 1", File(root, "profiles/1002.yaml").readText())
    }

    @Test
    fun invalidPathAndUnknownVersionAreRejectedBeforeChangingFiles() = runBlocking {
        write("profiles/1004.yaml", "keep: true")
        val before = File(root, "profiles/1004.yaml").readText()

        val traversal = zip(
            "{\"version\":1,\"createdAtMillis\":1,\"includesScripts\":false,\"includesCurrentProfile\":false}" to "manifest.json",
            "[]" to "profiles.json",
            "bad" to "../profiles/1004.yaml",
        )
        runCatching {
            repository.restore(traversal, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.OVERRIDE)
        }.onSuccess { error("路径穿越备份应该被拒绝") }
        assertEquals(before, File(root, "profiles/1004.yaml").readText())

        val newer = zip(
            "{\"version\":99,\"createdAtMillis\":1,\"includesScripts\":false,\"includesCurrentProfile\":false}" to "manifest.json",
            "[]" to "profiles.json",
        )
        runCatching {
            repository.restore(newer, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.OVERRIDE)
        }.onSuccess { error("高版本备份应该被拒绝") }
        assertTrue(File(root, "profiles/1004.yaml").isFile)
    }

    private fun profile(id: Long, scriptId: Long?) = ProfileEntity(
        id = id,
        type = ProfileType.CUSTOM,
        label = "配置$id",
        url = "",
        ageSecretKey = "",
        lastUpdateAt = 1L,
        lastUpdateError = null,
        autoUpdate = false,
        autoUpdateIntervalMillis = Profile.DEFAULT_UPDATE_INTERVAL_MILLIS,
        uploadBytes = 0,
        downloadBytes = 0,
        totalBytes = 0,
        expireAtSeconds = 0,
        currentGroupName = null,
        selectedMapJson = "{}",
        unfoldSetJson = "[]",
        extendType = "standard",
        scriptId = scriptId,
        matchTarget = null,
        overridesJson = "{}",
        orderIndex = 0,
    )

    private fun write(relative: String, content: String) {
        File(root, relative).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (content, name) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }
}
