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
    @Provides @Singleton
    fun provideOnDemandSettingsStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.service.OnDemandSettingsStore =
        com.lonnnnnng.flclash.service.SharedPreferencesOnDemandSettingsStore(context)
    @Provides fun provideServiceExitStore(repository: com.lonnnnnng.flclash.data.ServiceExitRepository): com.lonnnnnng.flclash.data.ServiceExitStore = repository
    @Provides fun provideProfileStatisticsRuntime(manager: CoreRuntimeManager): com.lonnnnnng.flclash.service.ProfileStatisticsRuntime = manager
    @Provides fun provideServiceStatusRuntime(manager: CoreRuntimeManager): com.lonnnnnng.flclash.service.ServiceStatusRuntime = manager
    @Provides @Singleton fun provideServiceSettingsStore(store: com.lonnnnnng.flclash.data.AndroidServiceSettingsStore): com.lonnnnnng.flclash.data.ServiceSettingsStore = store

    @Provides @Singleton
    fun provideVpnSettingsStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.service.VpnSettingsStore =
        com.lonnnnnng.flclash.service.SharedPreferencesVpnSettingsStore(context)

    @Provides @Singleton
    fun provideNetworkSettingsStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.service.NetworkSettingsStore =
        com.lonnnnnng.flclash.service.SharedPreferencesNetworkSettingsStore(context)

    @Provides @Singleton
    fun provideGlobalDnsSettingsStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.service.GlobalDnsSettingsStore =
        com.lonnnnnng.flclash.service.SharedPreferencesGlobalDnsSettingsStore(context)

    @Provides @Singleton
    fun provideGeneralSettingsStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.service.GeneralSettingsStore =
        com.lonnnnnng.flclash.service.SharedPreferencesGeneralSettingsStore(context)

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
            .openHelperFactory(com.lonnnnnng.flclash.data.StateAuditPlatform.factory())
            .addMigrations(KClashDatabase.MIGRATION_1_2)
            .addMigrations(KClashDatabase.MIGRATION_2_3)
            .addMigrations(KClashDatabase.MIGRATION_3_4)
            .addMigrations(KClashDatabase.MIGRATION_4_5)
            .addMigrations(KClashDatabase.MIGRATION_5_6)
            .build()

    @Provides
    @Singleton
    fun provideSubscriptionClient(settings: com.lonnnnnng.flclash.service.GeneralSettingsStore): SubscriptionClient = HttpSubscriptionClient(settings)

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
    fun provideProfileDraftStore(store: com.lonnnnnng.flclash.data.AndroidProfileDraftStore): com.lonnnnnng.flclash.data.ProfileDraftStore = store

    @Provides
    @Singleton
    @javax.inject.Named("script-picker")
    fun provideScriptPickerStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "script-picker.v1", "com.lonnnnnng.flclash.script.picker.v1")

    @Provides
    @Singleton
    @javax.inject.Named("provider-picker")
    fun provideProviderPickerStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "provider-picker.v1", "com.lonnnnnng.flclash.provider.picker.v1")

    @Provides
    @Singleton
    @javax.inject.Named("provider-document")
    fun provideProviderDocumentStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "provider-document.v1", "com.lonnnnnng.flclash.provider.document.v1")

    @Provides
    @Singleton
    @javax.inject.Named("local-proxy-draft")
    fun provideLocalProxyDraftStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "local-proxy-draft.v1", "com.lonnnnnng.flclash.local.proxy.draft.v1")

    @Provides
    @Singleton
    @javax.inject.Named("profile-management")
    fun provideProfileManagementStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "profile-management.v1", "com.lonnnnnng.flclash.profile.management.v1")

    @Provides
    @Singleton
    @javax.inject.Named("profile-extension")
    fun provideProfileExtensionStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "profile-extension.v1", "com.lonnnnnng.flclash.profile.extension.v1")

    @Provides
    fun provideRuleResourceCompiler(compiler: com.lonnnnnng.flclash.data.NativeRuleResourceCompiler): com.lonnnnnng.flclash.data.RuleResourceCompiler = compiler

    @Provides @Singleton @javax.inject.Named("rule-file-picker")
    fun provideRuleFilePicker(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "rule-file-picker.v1", "com.lonnnnnng.flclash.rule.picker.v1")

    @Provides @Singleton @javax.inject.Named("rule-resource-draft")
    fun provideRuleResourceDraft(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "rule-resource-draft.v1", "com.lonnnnnng.flclash.rule.draft.v1")

    @Provides @Singleton @javax.inject.Named("icon-file-picker")
    fun provideIconFilePicker(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "icon-file-picker.v1", "com.lonnnnnng.flclash.icon.picker.v1")

    @Provides @Singleton @javax.inject.Named("group-image-picker")
    fun provideGroupImagePicker(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "group-image-picker.v1", "com.lonnnnnng.flclash.group.image.picker.v1")

    @Provides
    @Singleton
    @javax.inject.Named("global-rules")
    fun provideGlobalRulesStore(@ApplicationContext context: Context): com.lonnnnnng.flclash.data.ProfileDraftStore =
        com.lonnnnnng.flclash.data.EncryptedDraftFile(context, "global-rules.v1", "com.lonnnnnng.flclash.global.rules.v1")

    @Provides
    @Singleton
    fun provideProfileRepository(
        @ApplicationContext context: Context,
        database: KClashDatabase,
        subscriptionClient: SubscriptionClient,
        configValidator: ProfileConfigValidator,
        contentDecoder: com.lonnnnnng.flclash.data.ProfileContentDecoder,
        resources: com.lonnnnnng.flclash.data.RuleResourceRepository,
        runtime: javax.inject.Provider<CoreRuntimeManager>,
    ): ProfileRepository = ProfileRepository(context, database, subscriptionClient, configValidator, contentDecoder, resources,
        automaticCommitter = com.lonnnnnng.flclash.data.RemoteProfileCommitter { id, write ->
            // 作者 long：仓库也是核心的输入提供者，延迟取得管理器可避免Hilt环依赖；网络阶段已在此回调之前完成。
            runtime.get().withProfileSourceWrite(id, write)
        })

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
        geoSettingsStore: com.lonnnnnng.flclash.data.AndroidGeoResourceSettingsStore,
        filterValidator: com.lonnnnnng.flclash.data.ProxyGroupFilterValidator,
        general: com.lonnnnnng.flclash.service.GeneralSettingsStore,
        network: com.lonnnnnng.flclash.service.NetworkSettingsStore,
        dns: com.lonnnnnng.flclash.service.GlobalDnsSettingsStore,
        vpn: com.lonnnnnng.flclash.service.VpnSettingsStore,
        lan: com.lonnnnnng.flclash.service.LanSharingStore,
        runtimeManager: CoreRuntimeManager,
        backupUi: com.lonnnnnng.flclash.data.BackupUiPreferences,
        onDemand: com.lonnnnnng.flclash.service.OnDemandSettingsStore,
    ): BackupRepository = BackupRepository(database, context.filesDir, geoSettingsStore = geoSettingsStore, filterValidator = filterValidator,
        applicationSettingsStore = com.lonnnnnng.flclash.data.ApplicationSettingsBackupStore(general, network, dns, vpn, lan, backupUi, onDemand), runtimeManager = runtimeManager)

    @Provides
    @Singleton
    fun provideWebDavCredentialStore(@ApplicationContext context: Context): WebDavCredentialStore =
        AndroidWebDavCredentialStore(context)

    @Provides
    @Singleton
    fun provideWebDavClient(settings: com.lonnnnnng.flclash.service.GeneralSettingsStore): WebDavClient = HttpWebDavClient(generalSettingsStore = settings)

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
        runtime: javax.inject.Provider<CoreRuntimeManager>,
    ): ScriptRepository = ScriptRepository(context, database, scriptClient, runtime = { runtime.get() })

    @Provides
    @Singleton
    fun provideScriptClient(settings: com.lonnnnnng.flclash.service.GeneralSettingsStore): ScriptClient = HttpScriptClient(settings)

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
        networkSettingsStore: com.lonnnnnng.flclash.service.NetworkSettingsStore,
    ): VpnOptionsProvider = DefaultVpnOptionsProvider(accessControlSettingsProvider, vpnSettingsStore, paths.configFile, networkSettingsStore)

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
        geoSettingsStore: com.lonnnnnng.flclash.data.AndroidGeoResourceSettingsStore,
        networkSettingsStore: com.lonnnnnng.flclash.service.NetworkSettingsStore,
        globalDnsSettingsStore: com.lonnnnnng.flclash.service.GlobalDnsSettingsStore,
        generalSettingsStore: com.lonnnnnng.flclash.service.GeneralSettingsStore,
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
        geoSettingsStore = geoSettingsStore,
        networkSettingsStore = networkSettingsStore,
        globalDnsSettingsStore = globalDnsSettingsStore,
        generalSettingsStore = generalSettingsStore,
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
