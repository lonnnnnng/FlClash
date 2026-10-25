package com.lonnnnnng.flclash.service

import com.lonnnnnng.flclash.service.models.NotificationParams
import com.lonnnnnng.flclash.service.models.VpnOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnRuntimeState {
    STOPPED,
    STARTING,
    CONNECTED,
    FAILED,
}

object ServiceConfig {
    @Volatile
    private var currentVpnOptions: VpnOptions? = null
    private val mutableNotificationParams = MutableStateFlow(NotificationParams())
    private val mutableVpnState = MutableStateFlow(VpnRuntimeState.STOPPED)
    private val mutableVpnError = MutableStateFlow<String?>(null)

    val vpnOptions: VpnOptions?
        get() = currentVpnOptions

    val notificationParams = mutableNotificationParams.asStateFlow()
    val vpnState = mutableVpnState.asStateFlow()
    val vpnError = mutableVpnError.asStateFlow()

    fun updateVpnOptions(options: VpnOptions) {
        currentVpnOptions = options
    }

    /**
     * 作者 long：服务状态和错误信息必须由 VPN 服务统一发布，管理器只等待这个结果，
     * 避免 UI 根据“已发出启动 Intent”提前显示成已连接。
     */
    fun updateVpnState(state: VpnRuntimeState, errorMessage: String? = null) {
        mutableVpnError.value = errorMessage
        mutableVpnState.value = state
    }

    fun updateNotificationParams(params: NotificationParams) {
        mutableNotificationParams.value = params
    }
}
