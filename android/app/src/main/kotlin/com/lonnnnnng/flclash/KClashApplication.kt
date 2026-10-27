package com.lonnnnnng.flclash

import android.app.Application
import android.content.Context
import android.net.VpnService
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.data.SubscriptionAutoUpdateScheduler
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.RuntimeResumeStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KClashApplication : Application() {
    @Inject
    lateinit var runtimeManager: CoreRuntimeManager

    @Inject
    lateinit var runtimeResumeStore: RuntimeResumeStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 作者 long：服务进程也依赖同一个应用上下文，必须在任何前台服务启动前初始化共享状态。
        GlobalState.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        SubscriptionAutoUpdateScheduler.schedule(this)
        applicationScope.launch {
            val shouldResume = runtimeResumeStore.shouldResume()
            // 作者 long：先恢复上一轮诊断时间线，再决定是否自动重启核心；这样进程重建后的
            // CORE_RESTARTED 会紧接在崩溃前事件之后，而不是从空历史重新开始。
            runtimeManager.restoreDiagnosticHistory(markNextStartAsRestart = shouldResume)
            if (!shouldResume) return@launch
            // 作者 long：没有可交互的 Activity 时不能弹 VPN 授权页；授权失效则保留意图，等用户回到页面后重新授权。
            if (VpnService.prepare(this@KClashApplication) != null) return@launch
            runtimeManager.startProxy()
        }
    }
}
