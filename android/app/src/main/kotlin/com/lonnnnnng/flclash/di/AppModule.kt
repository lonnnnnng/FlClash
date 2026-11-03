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
import com.lonnnnnng.flclash.data.RoomAccessControlSettingsStore
import com.lonnnnnng.flclash.data.AndroidClipboardGateway
import com.lonnnnnng.flclash.data.ClipboardGateway
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
import com.lonnnnnng.flclash.service.AccessControlSettingsProvider
import com.lonnnnnng.flclash.service.OutboundModeStore
import com.lonnnnnng.flclash.service.SharedPreferencesOutboundModeStore
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
    @Provides fun provideServiceExitStore(repository: com.lonnnnnng.flclash.data.ServiceExitRepository): com.lonnnnnng.flclash.data.ServiceExitStore = repository
    @Provides fun provideProfileStatisticsRuntime(manager: CoreRuntimeManager): com.lonnnnnng.flclash.service.ProfileStatisticsRuntime = manager
    @Provides fun provideServiceStatusRuntime(manager: CoreRuntimeManager): com.lonnnnnng.flclash.service.ServiceStatusRuntime = manager
    @Provides @Singleton fun provideServiceSettingsStore(store: com.lonnnnnng.flclash.data.AndroidServiceSettingsStore): com.lonnnnnng.flclash.data.ServiceSettingsStore = store

    @Provides @Singleton
    fun provideVpnSettingsStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.service.VpnSettingsStore =
        com.lonnnnnng.flclash.service.SharedPreferencesVpnSettingsStore(context)

    @Provides
    fun provideLocalNetworkSource(source: com.lonnnnnng.flclash.data.AndroidLocalNetworkSource): com.lonnnnnng.flclash.data.LocalNetworkSource = source

    @Provides
    fun provideLanSharingRuntime(manager: CoreRuntimeManager): com.lonnnnnng.flclash.service.LanSharingRuntime = manager

    @Provides
    @Singleton
    fun provideLanSharingStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.service.LanSharingStore =
        com.lonnnnnng.flclash.service.SharedPreferencesLanSharingStore(context)

    @Provides
    fun provideMemoryRuntime(manager: CoreRuntimeManager): com.lonnnnnng.flclash.service.MemoryRuntime = manager

    @Provides
    fun provideRuntimeConfigPreviewProvider(manager: CoreRuntimeManager):
        com.lonnnnnng.flclash.service.RuntimeConfigPreviewProvider = manager

    @Provides
    fun provideOutboundIpRuntime(manager: CoreRuntimeManager): com.lonnnnnng.flclash.service.OutboundIpRuntime = manager

    @Provides
    fun provideIpInformationStore(repository: com.lonnnnnng.flclash.data.IpInformationRepository):
        com.lonnnnnng.flclash.data.IpInformationStore = repository
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KClashDatabase =
        Room.databaseBuilder(context, KClashDatabase::class.java, "kclash.db")
            .addMigrations(KClashDatabase.MIGRATION_1_2)
            .addMigrations(KClashDatabase.MIGRATION_2_3)
            .addMigrations(KClashDatabase.MIGRATION_3_4)
            .addMigrations(KClashDatabase.MIGRATION_4_5)
            .addMigrations(KClashDatabase.MIGRATION_5_6)
            .build()

    @Provides
    @Singleton
    fun provideSubscriptionClient(): SubscriptionClient = HttpSubscriptionClient()

    @Provides
    @Singleton
    fun provideProfileConfigValidator(): ProfileConfigValidator = YamlProfileConfigValidator()

    @Provides
    fun provideProxyGroupFilterValidator(validator: com.lonnnnnng.flclash.data.NativeProxyGroupFilterValidator):
        com.lonnnnnng.flclash.data.ProxyGroupFilterValidator = validator

    @Provides
    @Singleton
    fun provideProfileContentDecoder(
        @ApplicationContext context: Context,
        bridge: CoreBridge,
    ): com.lonnnnnng.flclash.data.ProfileContentDecoder =
        com.lonnnnnng.flclash.data.AgeProfileContentDecoder(context.cacheDir, bridge::decryptConfig)

    @Provides
    fun provideProfileFileDocumentReader(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileFileDocumentReader =
        com.lonnnnnng.flclash.data.AndroidProfileFileDocumentReader(context)

    @Provides
    fun provideRuleResourceCompiler(compiler: com.lonnnnnng.flclash.data.NativeRuleResourceCompiler): com.lonnnnnng.flclash.data.RuleResourceCompiler = compiler

    @Provides
    @Singleton
    fun provideProfileRepository(
        @ApplicationContext context: Context,
        database: KClashDatabase,
        subscriptionClient: SubscriptionClient,
        configValidator: ProfileConfigValidator,
        contentDecoder: com.lonnnnnng.flclash.data.ProfileContentDecoder,
        resources: com.lonnnnnng.flclash.data.RuleResourceRepository,
    ): ProfileRepository = ProfileRepository(context, database, subscriptionClient, configValidator, contentDecoder, resources)

    @Provides
    @Singleton
    fun provideProfileStore(repository: ProfileRepository): ProfileStore = repository

    @Provides
    fun provideProfileExportReader(repository: ProfileRepository): com.lonnnnnng.flclash.data.ProfileExportReader = repository

    @Provides
    fun provideProfileDocumentWriter(writer: com.lonnnnnng.flclash.data.AndroidProfileDocumentWriter):
        com.lonnnnnng.flclash.data.ProfileDocumentWriter = writer

    @Provides
    fun provideProfileQrEncoder(encoder: com.lonnnnnng.flclash.ui.SubscriptionQrEncoder):
        com.lonnnnnng.flclash.ui.ProfileQrEncoder = encoder

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
    fun provideAccessControlSettingsStore(database: KClashDatabase): RoomAccessControlSettingsStore =
        RoomAccessControlSettingsStore(database.appPreferences())

    @Provides
    @Singleton
    fun provideAccessControlSettingsProvider(
        store: RoomAccessControlSettingsStore,
    ): AccessControlSettingsProvider = store

    @Provides
    @Singleton
    fun provideClipboardGateway(
        gateway: AndroidClipboardGateway,
    ): ClipboardGateway = gateway

    @Provides
    @Singleton
    fun provideVpnOptionsProvider(
        accessControlSettingsProvider: AccessControlSettingsProvider,
        vpnSettingsStore: com.lonnnnnng.flclash.service.VpnSettingsStore,
        paths: CoreRuntimePaths,
    ): VpnOptionsProvider = DefaultVpnOptionsProvider(accessControlSettingsProvider, vpnSettingsStore, paths.configFile)

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
    fun provideProxyDelayStore(database: KClashDatabase): com.lonnnnnng.flclash.data.ProxyDelayStore =
        com.lonnnnnng.flclash.data.RoomProxyDelayStore(database.proxyDelayResults())

    @Provides
    @Singleton
    fun provideOutboundModeStore(@ApplicationContext context: Context): OutboundModeStore =
        SharedPreferencesOutboundModeStore(context)

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
        outboundModeStore: OutboundModeStore,
        lanSharingStore: com.lonnnnnng.flclash.service.LanSharingStore,
        vpnSettingsStore: com.lonnnnnng.flclash.service.VpnSettingsStore,
    ): CoreRuntimeManager = CoreRuntimeManager(
        paths = paths,
        profileProvider = profileProvider,
        bridge = bridge,
        serviceController = serviceController,
        vpnOptionsProvider = vpnOptionsProvider,
        runtimeResumeStore = runtimeResumeStore,
        runtimeDiagnosticStore = runtimeDiagnosticStore,
        profileScriptEvaluator = profileScriptEvaluator,
        outboundModeStore = outboundModeStore,
        lanSharingStore = lanSharingStore,
        vpnSettingsStore = vpnSettingsStore,
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
