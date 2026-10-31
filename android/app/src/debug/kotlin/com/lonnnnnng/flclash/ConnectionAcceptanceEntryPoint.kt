package com.lonnnnnng.flclash

import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimePaths
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 作者 long：真机详情验收只在 Debug 取得可等待的导入结果和暂存路径，便于精确恢复本次测试现场。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ConnectionAcceptanceEntryPoint {
    fun profiles(): ProfileStore
    fun paths(): CoreRuntimePaths
}
