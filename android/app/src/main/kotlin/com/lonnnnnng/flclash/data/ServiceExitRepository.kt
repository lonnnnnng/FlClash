package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.core.CoreRouteStamp
import com.lonnnnnng.flclash.service.ServiceStatusRuntime
import javax.inject.Inject

interface ServiceExitStore {
    suspend fun outbound(node: String, stamp: CoreRouteStamp): OutboundIpInfo
    suspend fun quality(ip: String, force: Boolean): IpQuality
}

class ServiceExitRepository @Inject constructor(
    private val runtime: ServiceStatusRuntime,
    private val information: IpInformationStore,
) : ServiceExitStore {
    override suspend fun outbound(node: String, stamp: CoreRouteStamp): OutboundIpInfo {
        val response = runtime.queryServiceExit(node, stamp, outboundIpSources)
        // 作者 long：既有解析器统一校验公共 IP、国家与来源；任何响应正文都不进入服务行状态或日志。
        check(response.stamp == stamp) { "服务出口已变化" }
        return parseOutboundIp(response)
    }
    override suspend fun quality(ip: String, force: Boolean) = information.quality(ip, force)
}
