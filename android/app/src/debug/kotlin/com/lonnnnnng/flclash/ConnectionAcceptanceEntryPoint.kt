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
    fun profiles(): ProfileStore
    fun profileRepository(): ProfileRepository
    fun database(): KClashDatabase
    fun paths(): CoreRuntimePaths
    fun runtimeManager(): com.lonnnnnng.flclash.service.CoreRuntimeManager
    fun vpnSettingsStore(): com.lonnnnnng.flclash.service.VpnSettingsStore
    fun lanSharingStore(): com.lonnnnnng.flclash.service.LanSharingStore
    fun serviceSettingsStore(): com.lonnnnnng.flclash.data.ServiceSettingsStore
    fun serviceExitStore(): com.lonnnnnng.flclash.data.ServiceExitStore
}
