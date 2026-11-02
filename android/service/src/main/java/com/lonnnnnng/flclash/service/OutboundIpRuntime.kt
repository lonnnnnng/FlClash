package com.lonnnnnng.flclash.service

import com.lonnnnnng.flclash.core.CoreIpResponse
import com.lonnnnnng.flclash.core.CoreRouteStamp
import kotlinx.coroutines.flow.StateFlow

interface OutboundIpRuntime {
    fun networkChanged() {}
    val state: StateFlow<CoreRuntimeStatus>
    val runningSinceElapsedMillis: StateFlow<Long?>
    val ipRouteRevision: StateFlow<Long>
    suspend fun ipRouteStamp(): CoreRouteStamp
    suspend fun queryOutboundIp(urls: List<String>): CoreIpResponse
    suspend fun queryIpMetadata(url: String): CoreIpResponse
}
