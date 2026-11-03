package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.Room
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.EntryPointAccessors
import java.io.*
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupSettingsHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualBackupEntryPersistsStrategyAcrossRecreationAndKeepsProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val before = entry.database().profiles().getAll()
        val files = before.associate { it.id to entry.profileRepository().profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } }
        val original = entry.backupUiPreferences().read()
        try {
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("tools_item_backup").performClick()
            compose.onNodeWithTag("tools_backup_page").performScrollToNode(hasTestTag("backup_strategy"))
            compose.onNodeWithTag("backup_strategy").performClick()
            val next = if (original == BackupRestoreStrategy.COMPATIBLE) BackupRestoreStrategy.OVERRIDE else BackupRestoreStrategy.COMPATIBLE
            compose.onNodeWithTag("backup_strategy_${next.name}").performClick()
            compose.waitUntil(10_000) { entry.backupUiPreferences().read() == next }
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("tools_backup_page").assertExists()
            compose.onNodeWithTag("tools_backup_page").performScrollToNode(hasTestTag("backup_strategy"))
            compose.onNodeWithTag("backup_strategy").assertTextContains(if (next == BackupRestoreStrategy.COMPATIBLE) "兼容" else "覆盖", substring = true)
            val archive = entry.backupRepository().createArchive()
            var settings: ApplicationSettingsBackup? = null
            ZipInputStream(ByteArrayInputStream(archive)).use { input -> while (true) {
                val row = input.nextEntry ?: break
                if (row.name == "application_settings.json") settings = ApplicationSettingsBackupCodec.decode(input.readBytes().toString(Charsets.UTF_8))
            } }
            assertEquals(next, settings?.restoreStrategy)
            assertEquals(entry.generalSettingsStore().read(), settings?.general)
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(files, before.associate { it.id to entry.profileRepository().profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } })
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally { entry.backupUiPreferences().write(original) }
    }

    @Test fun isolatedArchiveRestoresProductionPreferencesAndManagerFlowsWithoutTouchingProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = compose.activity.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val before = entry.database().profiles().getAll(); val selected = entry.profileRepository().currentProfileId.value
        val files = before.associate { it.id to entry.profileRepository().profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } }
        val store = ApplicationSettingsBackupStore(entry.generalSettingsStore(), entry.networkSettingsStore(), entry.globalDnsSettingsStore(), entry.vpnSettingsStore(), entry.lanSharingStore(), entry.backupUiPreferences())
        val original = store.read()
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val root = File(context.cacheDir, "backup-host-${System.nanoTime()}").apply { mkdirs() }
        val repository = BackupRepository(db, root, applicationSettingsStore = store, runtimeManager = manager)
        try {
            val next = original.copy(general = original.general.copy(mixedPort = if (original.general.mixedPort == 18890) 18891 else 18890),
                network = original.network.copy(mtu = 1400), dns = ProfileDnsOverrides(ipv6 = false, listen = "", nameserver = emptyList()), vpnEnabled = false, allowLan = true)
            manager.withStoppedDataRestore { store.write(next) }
            val archive = repository.createArchive()
            manager.withStoppedDataRestore { store.write(original) }
            repository.restore(archive, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)
            assertEquals(next, store.read()); assertEquals(next.general, manager.generalSettings.value)
            assertEquals(next.network, manager.networkSettings.value); assertEquals(next.dns, manager.globalDns.value)
            assertFalse(manager.vpnSettings.value.enabled); assertTrue(manager.lanSharing.value.config.allowLan)
            assertEquals(before, entry.database().profiles().getAll()); assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, before.associate { it.id to entry.profileRepository().profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } })
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        } finally {
            manager.withStoppedDataRestore { store.compensate(original) }
            assertEquals(original, store.read()); db.close(); root.deleteRecursively()
        }
    }
}
