package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResourcePreferencesBackupInstrumentedTest {
    private class Store : GeoResourceSettingsStore {
        var value = GeoResourceSettings()
        var failNext = false
        override fun read() = value
        override fun write(settings: GeoResourceSettings) { value = settings; if (failNext) { failNext = false; error("disk failure after write") } }
    }
    private fun fixture(action: suspend (KClashDatabase, File, Store, BackupRepository) -> Unit) = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val root = File(context.cacheDir, "resource-preferences-backup-${System.nanoTime()}").apply { mkdirs() }
        val store = Store()
        try { action(database, root, store, BackupRepository(database, root, geoSettingsStore = store)) }
        finally { database.close(); root.deleteRecursively() }
    }
    private suspend fun write(database: KClashDatabase, settings: ResourcePreferences) {
        database.appPreferences().put(AppPreferenceEntity(ICON_SETS_KEY, IconSetCodec.encode(settings.icons), 1))
        database.appPreferences().put(AppPreferenceEntity(FILTER_PRESETS_KEY, FilterPresets.encode(settings.filters), 1))
    }
    private suspend fun read(database: KClashDatabase, store: Store) = ResourcePreferences(IconSetCodec.decode(database.appPreferences().get(ICON_SETS_KEY)), FilterPresets.decode(database.appPreferences().get(FILTER_PRESETS_KEY)), store.value)
    private val original = ResourcePreferences(listOf(ResourceIconSet("gallery", "图库", icons = listOf(ResourceIcon("节点", "https://example.com/a.svg")), updatedAt = 1)), listOf(FilterPreset("地区", "HK`JP")), GeoResourceSettings(intervalHours = 48))

    @Test fun fullBackupRestoresSettingsAndProfilesOnlyLeavesGlobalPreferencesUntouched() = fixture { database, _, store, repository ->
        write(database, original); store.value = original.geo
        val full = repository.createArchive()
        val changed = ResourcePreferences(emptyList(), listOf(FilterPreset("新预设", "new")), GeoResourceSettings(intervalHours = 72))
        write(database, changed); store.value = changed.geo
        repository.restore(full, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.OVERRIDE)
        assertEquals(changed, read(database, store))
        repository.restore(full, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)
        assertEquals(original, read(database, store))
        val profilesOnly = repository.createArchive(BackupRestoreScope.PROFILES_ONLY)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(profilesOnly)).use { input -> while (true) { val entry = input.nextEntry ?: break; names += entry.name } }
        assertFalse(names.contains("resource_preferences.json"))
    }
    @Test fun failedGeoCommitRollsBackRoomAndCompensatesPersistedSettings() = fixture { database, _, store, repository ->
        write(database, original); store.value = original.geo
        val archive = repository.createArchive()
        val changed = original.copy(filters = listOf(FilterPreset("保留", "keep")), geo = GeoResourceSettings(intervalHours = 96))
        write(database, changed); store.value = changed.geo; store.failNext = true
        assertTrue(runCatching { repository.restore(archive, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE) }.isFailure)
        assertEquals(changed, read(database, store))
    }
    @Test fun malformedPreferencesAreRejectedBeforeAnyWriteAndLegacyArchivePreservesNewSettings() = fixture { database, _, store, repository ->
        write(database, original); store.value = original.geo
        val full = repository.createArchive()
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(full)).use { input -> while (true) { val entry = input.nextEntry ?: break; entries[entry.name] = input.readBytes() } }
        fun zip(): ByteArray = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
        entries["resource_preferences.json"] = "{\"icons\":[],\"filters\":null,\"geo\":{}}".toByteArray()
        assertTrue(runCatching { repository.restore(zip(), BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE) }.isFailure)
        assertEquals(original, read(database, store))
        entries.remove("resource_preferences.json")
        entries["manifest.json"] = "{\"version\":4,\"createdAtMillis\":1,\"includesScripts\":true,\"includesCurrentProfile\":false}".toByteArray()
        repository.restore(zip(), BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)
        assertEquals(original, read(database, store))
    }
    @Test fun coreRejectedArchivedFilterLeavesAllGlobalPreferencesUntouched() = fixture { database, root, store, repository ->
        write(database, original); store.value = original.geo
        val archive = repository.createArchive()
        val guarded = BackupRepository(database, root, geoSettingsStore = store, filterValidator = ProxyGroupFilterValidator { values -> values.map { "invalid regexp2" } })
        val changed = original.copy(filters = listOf(FilterPreset("保留", "keep")))
        write(database, changed)
        assertTrue(runCatching { guarded.restore(archive, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE) }.isFailure)
        assertEquals(changed, read(database, store))
    }
}
