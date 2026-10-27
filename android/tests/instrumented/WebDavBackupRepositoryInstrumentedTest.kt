package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：使用真实 Room、应用文件目录边界和 WebDAV 客户端契约验证远程备份只是在现有
 * ZIP 备份仓库外包了一层传输，不会绕过兼容/覆盖恢复语义，也不会把凭据写入归档。
 */
@RunWith(AndroidJUnit4::class)
class WebDavBackupRepositoryInstrumentedTest {
    private lateinit var database: KClashDatabase
    private lateinit var root: File
    private lateinit var localRepository: BackupRepository
    private lateinit var client: RecordingWebDavClient
    private lateinit var repository: WebDavBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = File(context.cacheDir, "webdav-backup-test-${System.nanoTime()}").apply { mkdirs() }
        localRepository = BackupRepository(database, root)
        client = RecordingWebDavClient()
        repository = WebDavBackupRepository(
            backupRepository = localRepository,
            webDavClient = client,
            credentialStore = NoopWebDavCredentialStore,
        )
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun uploadThenDownloadRestoresUsingExistingConflictStrategy() = runBlocking {
        val credentials = WebDavCredentials(
            baseUrl = "https://dav.example.test/kclash",
            username = "alice",
            password = "secret",
            fileName = "kclash-test.zip",
        )
        database.profiles().insert(profile(501L))
        File(root, "profiles/501.yaml").apply {
            parentFile?.mkdirs()
            writeText("mixed-port: 7890")
        }

        repository.upload(credentials, BackupRestoreScope.PROFILES_ONLY)

        assertEquals(credentials.toEndpoint(), client.lastUploadEndpoint)
        assertNotNull(client.archive)
        val uploaded = client.archive ?: error("远程归档为空")
        database.profiles().deleteAll()
        File(root, "profiles/501.yaml").delete()
        client.archive = uploaded

        repository.downloadAndRestore(
            credentials = credentials,
            scope = BackupRestoreScope.PROFILES_ONLY,
            strategy = BackupRestoreStrategy.OVERRIDE,
        )

        assertEquals(listOf(501L), database.profiles().getAll().map(ProfileEntity::id))
        assertEquals("mixed-port: 7890", File(root, "profiles/501.yaml").readText())
        assertArrayEquals(uploaded, client.archive ?: error("远程归档在下载后丢失"))
    }

    private fun profile(id: Long) = ProfileEntity(
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
        scriptId = null,
        matchTarget = null,
        overridesJson = "{}",
        orderIndex = 0,
    )

    private object NoopWebDavCredentialStore : WebDavCredentialStore {
        override fun read(): WebDavCredentials? = null
        override fun save(credentials: WebDavCredentials) = Unit
        override fun clear() = Unit
    }

    private class RecordingWebDavClient : WebDavClient {
        var archive: ByteArray? = null
        var lastUploadEndpoint: WebDavEndpoint? = null

        override suspend fun probe(endpoint: WebDavEndpoint) = Unit

        override suspend fun upload(endpoint: WebDavEndpoint, archive: ByteArray) {
            lastUploadEndpoint = endpoint
            this.archive = archive.copyOf()
        }

        override suspend fun download(endpoint: WebDavEndpoint): ByteArray =
            archive?.copyOf() ?: error("没有可下载的远程归档")
    }
}
