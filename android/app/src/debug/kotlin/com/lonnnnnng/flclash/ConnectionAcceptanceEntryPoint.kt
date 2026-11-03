package com.lonnnnnng.flclash

import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ProfileRepository
import com.lonnnnnng.flclash.data.KClashDatabase
import com.lonnnnnng.flclash.service.CoreRuntimePaths
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 作者 long：Debug 验收使用正式数据实例核实配置和导入结果，避免初始空状态流被误判为用户数据缺失。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ConnectionAcceptanceEntryPoint {
    fun onDemandSettingsStore(): com.lonnnnnng.flclash.service.OnDemandSettingsStore
    fun onDemandCoordinator(): com.lonnnnnng.flclash.data.OnDemandCoordinator
    fun scriptRepository(): com.lonnnnnng.flclash.data.ScriptRepository
    fun profiles(): ProfileStore
    fun profileRepository(): ProfileRepository
    fun database(): KClashDatabase
    fun paths(): CoreRuntimePaths
    fun runtimeManager(): com.lonnnnnng.flclash.service.CoreRuntimeManager
    fun vpnSettingsStore(): com.lonnnnnng.flclash.service.VpnSettingsStore
    fun generalSettingsStore(): com.lonnnnnng.flclash.service.GeneralSettingsStore
    fun globalDnsSettingsStore(): com.lonnnnnng.flclash.service.GlobalDnsSettingsStore
    fun networkSettingsStore(): com.lonnnnnng.flclash.service.NetworkSettingsStore
    fun lanSharingStore(): com.lonnnnnng.flclash.service.LanSharingStore
    fun backupRepository(): com.lonnnnnng.flclash.data.BackupRepository
    fun backupUiPreferences(): com.lonnnnnng.flclash.data.BackupUiPreferences
    fun serviceSettingsStore(): com.lonnnnnng.flclash.data.ServiceSettingsStore
    fun serviceExitStore(): com.lonnnnnng.flclash.data.ServiceExitStore
    // 作者 long：资源整页验收必须回读与页面相同的仓库实例，避免新建仓库绕开正式并发锁与更新流。
    fun icons(): com.lonnnnnng.flclash.data.IconSetRepository
    fun rules(): com.lonnnnnng.flclash.data.RuleResourceRepository
}
