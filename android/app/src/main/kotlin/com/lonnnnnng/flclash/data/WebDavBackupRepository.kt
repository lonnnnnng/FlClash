package com.lonnnnnng.flclash.data

/**
 * 作者 long：WebDAV 只提供用户主动触发的远程备份动作；本地 ZIP 的生成和恢复仍由
 * [BackupRepository] 负责，避免远程协议绕过既有的文件校验、Room 事务和回滚边界。
 */
class WebDavBackupRepository(
    private val backupRepository: BackupRepository,
    private val webDavClient: WebDavClient,
    private val credentialStore: WebDavCredentialStore,
) {
    fun readCredentials(): WebDavCredentials? = credentialStore.read()

    fun saveCredentials(credentials: WebDavCredentials) {
        credentialStore.save(credentials)
    }

    fun clearCredentials() {
        credentialStore.clear()
    }

    suspend fun probe(credentials: WebDavCredentials) {
        webDavClient.probe(credentials.toEndpoint())
    }

    suspend fun upload(
        credentials: WebDavCredentials,
        scope: BackupRestoreScope = BackupRestoreScope.ALL_DATA,
    ) {
        val archive = backupRepository.createArchive(scope)
        // 作者 long：PUT 明确覆盖同名远程归档，调用方必须在 UI 中展示覆盖语义并由用户主动确认。
        webDavClient.upload(credentials.toEndpoint(), archive)
    }

    suspend fun downloadAndRestore(
        credentials: WebDavCredentials,
        scope: BackupRestoreScope,
        strategy: BackupRestoreStrategy,
    ) {
        // 作者 long：先完整下载到内存并通过 BackupRepository 校验，校验失败时不改变本地数据。
        val archive = webDavClient.download(credentials.toEndpoint())
        backupRepository.restore(archive, scope, strategy)
    }
}
