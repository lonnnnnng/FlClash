package com.lonnnnnng.flclash.di

import android.content.Context
import androidx.room.Room
import com.lonnnnnng.flclash.data.KClashDatabase
import com.lonnnnnng.flclash.data.HttpSubscriptionClient
import com.lonnnnnng.flclash.data.ProfileRepository
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ScriptRepository
import com.lonnnnnng.flclash.data.ScriptStore
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
import com.lonnnnnng.flclash.service.NativeCoreRuntimeBridge
import com.lonnnnnng.flclash.service.ProxyServiceController
import com.lonnnnnng.flclash.service.RuntimeResumeStore
import com.lonnnnnng.flclash.service.RuntimeDiagnosticStore
import com.lonnnnnng.flclash.service.DefaultVpnOptionsProvider
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
            .build()

    @Provides
    @Singleton
    fun provideSubscriptionClient(): SubscriptionClient = HttpSubscriptionClient()

    @Provides
    @Singleton
    fun provideProfileRepository(
        @ApplicationContext context: Context,
        database: KClashDatabase,
        subscriptionClient: SubscriptionClient,
    ): ProfileRepository = ProfileRepository(context, database, subscriptionClient)

    @Provides
    @Singleton
    fun provideProfileStore(repository: ProfileRepository): ProfileStore = repository

    @Provides
    @Singleton
    fun provideScriptRepository(
        @ApplicationContext context: Context,
        database: KClashDatabase,
    ): ScriptRepository = ScriptRepository(context, database)

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
        CoreRuntimePaths(File(context.filesDir, "mihomo"))

    @Provides
    @Singleton
    fun provideProxyServiceController(@ApplicationContext context: Context): ProxyServiceController =
        AndroidProxyServiceController(context)

    @Provides
    @Singleton
    fun provideVpnOptionsProvider(): VpnOptionsProvider = DefaultVpnOptionsProvider()

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
    ): CoreRuntimeManager = CoreRuntimeManager(
        paths = paths,
        profileProvider = profileProvider,
        bridge = bridge,
        serviceController = serviceController,
        vpnOptionsProvider = vpnOptionsProvider,
        runtimeResumeStore = runtimeResumeStore,
        runtimeDiagnosticStore = runtimeDiagnosticStore,
    )

    @Provides
    @Singleton
    fun provideCoreRuntimeStatusProvider(manager: CoreRuntimeManager): CoreRuntimeStatusProvider = manager
}
