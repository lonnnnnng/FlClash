package com.lonnnnnng.flclash.service

import com.lonnnnnng.flclash.core.*
import kotlinx.coroutines.flow.StateFlow

/** 作者 long：出口变化表示结果已过期，应等待新路由后重试，不能渲染成服务自身不可用。 */
class ServiceRouteChangedException : IllegalStateException("出口已变化，请重新检测")

interface ServiceStatusRuntime {
    val state: StateFlow<CoreRuntimeStatus>
    val runningSinceElapsedMillis: StateFlow<Long?>
    val ipRouteRevision: StateFlow<Long>
    // 作者 long：默认沿用全失效策略，未实现核验的 Runtime 不能自行承诺安全复用。
    val serviceCacheRevision: StateFlow<Long> get() = ipRouteRevision
    suspend fun reuseServiceChecks(results: List<CoreServiceCheck>, expected: CoreRouteStamp, cacheRevision: Long): List<CoreServiceCheck> = emptyList()
    suspend fun ipRouteStamp(): CoreRouteStamp
    suspend fun checkServices(targets: List<ServiceTarget>): List<CoreServiceCheck>
    suspend fun queryServiceExit(proxyName: String, expected: CoreRouteStamp, urls: List<String>): CoreIpResponse =
        error("核心未提供服务出口查询")
}
