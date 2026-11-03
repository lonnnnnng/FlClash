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

class ApplicationSettingsBackupInstrumentedTest {
    private class General : GeneralSettingsStore {
        var value = GeneralSettings()
        override fun read() = value
        override fun write(value: GeneralSettings) { this.value = value }
    }
    private class Network : NetworkSettingsStore {
        var value = NetworkSettings(); var fail = false
        override fun read() = value
        override fun write(value: NetworkSettings) { this.value = value; if (fail) { fail = false; error("disk after write") } }
    }
    private class Dns : GlobalDnsSettingsStore {
        var value = ProfileDnsOverrides()
        override fun read() = value
        override fun write(value: ProfileDnsOverrides) { this.value = value }
    }
    private fun fixture(action: suspend (KClashDatabase, General, Network, Dns, BackupRepository) -> Unit) = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val root = File(context.cacheDir, "application-backup-${System.nanoTime()}").apply { mkdirs() }
        val general = General(); val network = Network(); val dns = Dns()
        try { action(db, general, network, dns, BackupRepository(db, root, applicationSettingsStore = ApplicationSettingsBackupStore(general, network, dns))) }
        finally { db.close(); root.deleteRecursively() }
    }
    private val savedGeneral = GeneralSettings(mixedPort = 18890, userAgent = "fixture/1", onlyStatisticsProxy = true)
    private val savedNetwork = NetworkSettings(mtu = 1400, routeExcludeAddress = listOf("192.0.2.0/24"))
    private val savedDns = ProfileDnsOverrides(ipv6 = false, listen = "", nameserver = emptyList())

    @Test fun fullRestoreIncludesGlobalSettingsAndProfilesOnlyPreservesThem() = fixture { _, general, network, dns, repository ->
        general.value = savedGeneral; network.value = savedNetwork; dns.value = savedDns
        val full = repository.createArchive()
        assertTrue(entries(full).containsKey("application_settings.json"))
        assertFalse(entries(repository.createArchive(BackupRestoreScope.PROFILES_ONLY)).containsKey("application_settings.json"))
        general.value = GeneralSettings(); network.value = NetworkSettings(); dns.value = ProfileDnsOverrides()
        repository.restore(full, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.OVERRIDE)
        assertEquals(GeneralSettings(), general.value)
        repository.restore(full, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.COMPATIBLE)
        assertEquals(savedGeneral, general.value); assertEquals(savedNetwork, network.value); assertEquals(savedDns, dns.value)
    }

    @Test fun settingsWriteFailureRollsBackRoomAndEveryPersistedSetting() = fixture { db, general, network, dns, repository ->
        general.value = savedGeneral; network.value = savedNetwork; dns.value = savedDns
        val full = repository.createArchive()
        general.value = GeneralSettings(); network.value = NetworkSettings(); dns.value = ProfileDnsOverrides()
        val rule = listOf(ExtensionRule("preserve", "DOMAIN,keep.invalid,DIRECT"))
        db.appPreferences().put(AppPreferenceEntity(ExtensionRules.PREFERENCE_KEY, ExtensionRules.toJson(rule).toString(), 1))
        network.fail = true
        assertTrue(runCatching { repository.restore(full, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE) }.isFailure)
        assertEquals(GeneralSettings(), general.value); assertEquals(NetworkSettings(), network.value); assertEquals(ProfileDnsOverrides(), dns.value)
        assertEquals(rule, ExtensionRules.decode(db.appPreferences().get(ExtensionRules.PREFERENCE_KEY)))
    }

    @Test fun damagedV6IsRejectedAndV5WithoutSettingsPreservesCurrentValues() = fixture { _, general, network, dns, repository ->
        val rows = entries(repository.createArchive())
        rows.remove("application_settings.json")
        assertTrue(runCatching { repository.restore(zip(rows), BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE) }.isFailure)
        rows["manifest.json"] = rows.getValue("manifest.json").toString(Charsets.UTF_8).replace("\"version\":6", "\"version\":5").toByteArray()
        general.value = savedGeneral; network.value = savedNetwork; dns.value = savedDns
        repository.restore(zip(rows), BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)
        assertEquals(savedGeneral, general.value); assertEquals(savedNetwork, network.value); assertEquals(savedDns, dns.value)
    }
    @Test fun ssidArchiveSupportsOldV6ProfilesOnlyAndCompensatesFailureAfterSsidWrite() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val root = File(context.cacheDir, "ssid-backup-${System.nanoTime()}").apply { mkdirs() }
        val ssids = object : OnDemandSettingsStore {
            var value = listOf("Archived", " Spaced ")
            var failNext = false
            override fun read() = value
            override fun write(value: List<String>) {
                this.value = OnDemandSettings.validate(value)
                if (failNext) { failNext = false; error("disk after SSID write") }
            }
        }
        val general = General()
        val repository = BackupRepository(db, root, applicationSettingsStore = ApplicationSettingsBackupStore(general = general, onDemand = ssids))
        try {
            val archive = repository.createArchive()
            ssids.value = listOf("Current")
            repository.restore(archive, BackupRestoreScope.PROFILES_ONLY, BackupRestoreStrategy.OVERRIDE)
            assertEquals(listOf("Current"), ssids.value)
            val legacy = entries(archive)
            val oldSettings = com.google.gson.JsonParser.parseString(legacy.getValue("application_settings.json").toString(Charsets.UTF_8)).asJsonObject
            oldSettings.remove("onDemandSsids")
            legacy["application_settings.json"] = oldSettings.toString().toByteArray()
            repository.restore(zip(legacy), BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)
            assertEquals(listOf("Current"), ssids.value)
            general.value = GeneralSettings(mixedPort = 18891)
            val originalGeneral = general.value
            ssids.failNext = true
            assertTrue(runCatching { repository.restore(archive, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE) }.isFailure)
            assertEquals(listOf("Current"), ssids.value)
            assertEquals(originalGeneral, general.value)
            repository.restore(archive, BackupRestoreScope.ALL_DATA, BackupRestoreStrategy.OVERRIDE)
            assertEquals(listOf("Archived", " Spaced "), ssids.value)
        } finally { db.close(); root.deleteRecursively() }
    }

    private fun entries(bytes: ByteArray): MutableMap<String, ByteArray> = linkedMapOf<String, ByteArray>().also { rows ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { input -> while (true) { val entry = input.nextEntry ?: break; rows[entry.name] = input.readBytes() } }
    }
    private fun zip(rows: Map<String, ByteArray>) = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
        rows.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
    } }.toByteArray()
}
