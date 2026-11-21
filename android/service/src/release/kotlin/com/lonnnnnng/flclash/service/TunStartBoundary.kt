package com.lonnnnnng.flclash.service

import android.net.VpnService
import com.lonnnnnng.flclash.service.models.VpnOptions

/** 作者 long：发布包只透传真实系统/核心调用，不包含故障计划、参数替换或FD诊断接口。 */
object TunStartBoundary {
    @Suppress("UNUSED_PARAMETER")
    fun begin(packageName: String, options: VpnOptions) = Attempt

    object Attempt {
        fun establish(builder: VpnService.Builder) = builder.establish()
        @Suppress("UNUSED_PARAMETER")
        inline fun beforeCoreProtect(coreStartCallActive: Boolean, protect: (java.net.DatagramSocket) -> Boolean) = Unit
        @Suppress("UNUSED_PARAMETER")
        inline fun startCore(fd: Int, stack: String, options: String, call: (String, String) -> Boolean) = call(stack, options)
    }
}
