package com.lonnnnnng.flclash.di

import android.content.Context
import androidx.room.Room
import com.lonnnnnng.flclash.data.KClashDatabase
import com.lonnnnnng.flclash.data.HttpSubscriptionClient
import com.lonnnnnng.flclash.data.ProfileRepository
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ProfileConfigValidator
import com.lonnnnnng.flclash.data.BackupRepository
import com.lonnnnnng.flclash.data.RemoteProfileUpdater
import com.lonnnnnng.flclash.data.YamlProfileConfigValidator
import com.lonnnnnng.flclash.data.ScriptRepository
import com.lonnnnnng.flclash.data.ScriptStore
import com.lonnnnnng.flclash.data.HttpScriptClient
import com.lonnnnnng.flclash.data.ScriptClient
import com.lonnnnnng.flclash.data.WebViewProfileScriptEvaluator
import com.lonnnnnng.flclash.data.AndroidWebDavCredentialStore
import com.lonnnnnng.flclash.data.WebDavCredentialStore
import com.lonnnnnng.flclash.data.WebDavBackupRepository
import com.lonnnnnng.flclash.data.WebDavClient
import com.lonnnnnng.flclash.data.HttpWebDavClient
import com.lonnnnnng.flclash.data.RoomRuntimeResumeStore
import com.lonnnnnng.flclash.data.RoomRuntimeDiagnosticStore
import com.lonnnnnng.flclash.data.SubscriptionClient
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.service.ActiveProfileProvider
import com.lonnnnnng.flclash.service.AndroidProxyServiceController
import com.lonnnnnng.flclash.service.CoreRuntimeBridge
import com.lonnnnnng.flclash.service.CoreRuntimePaths
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.ProxyGroupsRuntime
import com.lonnnnnng.flclash.service.NativeCoreRuntimeBridge
import com.lonnnnnng.flclash.service.ProxyServiceController
import com.lonnnnnng.flclash.service.RuntimeResumeStore
import com.lonnnnnng.flclash.service.RuntimeDiagnosticStore
import com.lonnnnnng.flclash.service.DefaultVpnOptionsProvider
import com.lonnnnnng.flclash.service.ProfileScriptEvaluator
import com.lonnnnnng.flclash.service.VpnOptionsProvider
import java.io.File
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KClashDatabase =
        Room.databaseBuilder(context, KClashDatabase::class.java, "kclash.db")
            .addMigrations(KClashDatabase.MIGRATION_1_2)
            .addMigrations(KClashDatabase.MIGRATION_2_3)
            .addMigrations(KClashDatabase.MIGRATION_3_4)
            .addMigrations(KClashDatabase.MIGRATION_4_5)
            .build()

    @Provides
    @Singleton
    fun provideSubscriptionClient(): SubscriptionClient = HttpSubscriptionClient()

    @Provides
    @Singleton
    fun provideProfileConfigValidator(): ProfileConfigValidator = YamlProfileConfigValidator()

    @Provides
    @Singleton
    fun provideProfileRepository(
        @ApplicationContext context: Context,
        database: KClashDatabase,
        subscriptionClient: SubscriptionClient,
        configValidator: ProfileConfigValidator,
    ): ProfileRepository = ProfileRepository(context, database, subscriptionClient, configValidator)

    @Provides
    @Singleton
    fun provideProfileStore(repository: ProfileRepository): ProfileStore = repository

    @Provides
    @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        database: KClashDatabase,
    ): BackupRepository = BackupRepository(database, context.filesDir)

    @Provides
    @Singleton
    fun provideWebDavCredentialStore(@ApplicationContext context: Context): WebDavCredentialStore =
        AndroidWebDavCredentialStore(context)

    @Provides
    @Singleton
    fun provideWebDavClient(): WebDavClient = HttpWebDavClient()

    @Provides
    @Singleton
    fun provideWebDavBackupRepository(
        backupRepository: BackupRepository,
        webDavClient: WebDavClient,
        credentialStore: WebDavCredentialStore,
    ): WebDavBackupRepository = WebDavBackupRepository(
        backupRepository = backupRepository,
        webDavClient = webDavClient,
        credentialStore = credentialStore,
    )

    @Provides
    @Singleton
    fun provideRemoteProfileUpdater(repository: ProfileRepository): RemoteProfileUpdater = repository

    @Provides
    @Singleton
    fun provideScriptRepository(
        @ApplicationContext context: Context,
        database: KClashDatabase,
        scriptClient: ScriptClient,
    ): ScriptRepository = ScriptRepository(context, database, scriptClient)

    @Provides
    @Singleton
    fun provideScriptClient(): ScriptClient = HttpScriptClient()

    @Provides
    @Singleton
    fun provideScriptStore(repository: ScriptRepository): ScriptStore = repository

    @Provides
    @Singleton
    fun provideActiveProfileProvider(repository: ProfileRepository): ActiveProfileProvider = repository

    @Provides
    @Singleton
    fun provideCoreRuntimeBridge(coreBridge: CoreBridge): CoreRuntimeBridge =
        NativeCoreRuntimeBridge(coreBridge)

    @Provides
    @Singleton
    fun provideCoreRuntimePaths(@ApplicationContext context: Context): CoreRuntimePaths =
        CoreRuntimePaths(
            homeDir = File(context.filesDir, "mihomo"),
            prepare = {
                val homeDir = File(context.filesDir, "mihomo").apply { mkdirs() }
                // 作者 long：每个资源先写入同目录临时文件再替换，进程被系统中断时不会留下半个数据库，
                // 已存在的完整资源保持不动，后续启动仍可在后台按核心协议更新。
                CORE_BUNDLED_DATA.forEach { name ->
                    val target = File(homeDir, name)
                    if (target.isFile && target.length() > 0L) return@forEach
                    val temporary = File(homeDir, ".${name}.tmp")
                    context.assets.open(name).use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!temporary.renameTo(target)) {
                        target.outputStream().use { output ->
                            temporary.inputStream().use { input -> input.copyTo(output) }
                        }
                        check(temporary.delete()) { "无法清理核心资源临时文件：$name" }
                    }
                }
            },
        )

    @Provides
    @Singleton
    fun provideProxyServiceController(@ApplicationContext context: Context): ProxyServiceController =
        AndroidProxyServiceController(context)

    @Provides
    @Singleton
    fun provideVpnOptionsProvider(): VpnOptionsProvider = DefaultVpnOptionsProvider()

    @Provides
    @Singleton
    fun provideProfileScriptEvaluator(@ApplicationContext context: Context): ProfileScriptEvaluator =
        WebViewProfileScriptEvaluator(context)

    @Provides
    @Singleton
    fun provideRuntimeResumeStore(database: KClashDatabase): RuntimeResumeStore =
        RoomRuntimeResumeStore(database.appPreferences())

    @Provides
    @Singleton
    fun provideRuntimeDiagnosticStore(database: KClashDatabase): RuntimeDiagnosticStore =
        RoomRuntimeDiagnosticStore(database.runtimeDiagnostics())

    @Provides
    @Singleton
    fun provideCoreBridge(): CoreBridge = CoreBridge()

    @Provides
    @Singleton
    fun provideCoreRuntimeManager(
        paths: CoreRuntimePaths,
        profileProvider: ActiveProfileProvider,
        bridge: CoreRuntimeBridge,
        serviceController: ProxyServiceController,
        vpnOptionsProvider: VpnOptionsProvider,
        runtimeResumeStore: RuntimeResumeStore,
        runtimeDiagnosticStore: RuntimeDiagnosticStore,
        profileScriptEvaluator: ProfileScriptEvaluator,
    ): CoreRuntimeManager = CoreRuntimeManager(
        paths = paths,
        profileProvider = profileProvider,
        bridge = bridge,
        serviceController = serviceController,
        vpnOptionsProvider = vpnOptionsProvider,
        runtimeResumeStore = runtimeResumeStore,
        runtimeDiagnosticStore = runtimeDiagnosticStore,
        profileScriptEvaluator = profileScriptEvaluator,
    )

    @Provides
    @Singleton
    fun provideCoreRuntimeStatusProvider(manager: CoreRuntimeManager): CoreRuntimeStatusProvider = manager

    @Provides
    @Singleton
    fun provideProxyGroupsRuntime(manager: CoreRuntimeManager): ProxyGroupsRuntime = manager

    private val CORE_BUNDLED_DATA = listOf(
        "ASN.mmdb",
        "GEOIP.dat",
        "GEOIP.metadb",
        "GEOSITE.dat",
    )
}
