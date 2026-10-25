package com.lonnnnnng.flclash.di

import android.content.Context
import androidx.room.Room
import com.lonnnnnng.flclash.data.KClashDatabase
import com.lonnnnnng.flclash.data.HttpSubscriptionClient
import com.lonnnnnng.flclash.data.ProfileRepository
import com.lonnnnnng.flclash.data.SubscriptionClient
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.service.ActiveProfileProvider
import com.lonnnnnng.flclash.service.AndroidProxyServiceController
import com.lonnnnnng.flclash.service.CoreRuntimeBridge
import com.lonnnnnng.flclash.service.CoreRuntimePaths
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.NativeCoreRuntimeBridge
import com.lonnnnnng.flclash.service.ProxyServiceController
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
    fun provideCoreBridge(): CoreBridge = CoreBridge()

    @Provides
    @Singleton
    fun provideCoreRuntimeManager(
        paths: CoreRuntimePaths,
        profileProvider: ActiveProfileProvider,
        bridge: CoreRuntimeBridge,
        serviceController: ProxyServiceController,
    ): CoreRuntimeManager = CoreRuntimeManager(paths, profileProvider, bridge, serviceController)
}
