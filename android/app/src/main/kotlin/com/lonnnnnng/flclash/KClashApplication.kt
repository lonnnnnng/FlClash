package com.lonnnnnng.flclash

import android.app.Application
import android.content.Context
import com.lonnnnnng.flclash.common.ensureNotificationChannel
import android.net.VpnService
import com.lonnnnnng.flclash.common.GlobalState
import com.lonnnnnng.flclash.data.SubscriptionAutoUpdateScheduler
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import com.lonnnnnng.flclash.service.RuntimeResumeStore
import com.lonnnnnng.flclash.service.NetworkObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@HiltAndroidApp
class KClashApplication : Application() {
    @Inject
    lateinit var runtimeManager: CoreRuntimeManager

    @Inject
    lateinit var runtimeResumeStore: RuntimeResumeStore

    @Inject lateinit var appearanceSettings: com.lonnnnnng.flclash.data.AppearanceSettingsStore
    @Inject lateinit var onDemandCoordinator: com.lonnnnnng.flclash.data.OnDemandCoordinator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var runtimeInitialization: Job

    // 作者 long：系统VPN回调和Application恢复可同时到达；先等诊断/旧运行意图恢复，避免第二次初始化覆盖新会话。
    suspend fun awaitRuntimeInitialization() = runtimeInitialization.join()

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 作者 long：恢复进程的Room/Hilt初始化也可能写状态，显式诊断必须先挂接；Release入口为空。
        if(!ValidationProcess.isCurrent())com.lonnnnnng.flclash.data.StateAuditPlatform.initialize(this)
        // 作者 long：服务进程也依赖同一个应用上下文，必须在任何前台服务启动前初始化共享状态。
        GlobalState.init(this)
        // 作者 long：隔离语义预检UID不能读取主应用偏好，语言初始化仅发生在正常应用进程。
        if (ValidationProcess.isCurrent()) return
        com.lonnnnnng.flclash.common.AppText.initialize(this)
        val appearance = getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val language = appearance.getString("language", "SYSTEM")
        com.lonnnnnng.flclash.common.AppText.useEnglish = language == "ENGLISH" || language == "SYSTEM" && resources.configuration.locales[0].language != "zh"
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!ValidationProcess.isCurrent()) applyAppearanceLanguage(appearanceSettings.state.value.language)
    }

    private fun applyAppearanceLanguage(language: com.lonnnnnng.flclash.data.AppLanguage) {
        val systemLanguage = if (android.os.Build.VERSION.SDK_INT >= 33)
            getSystemService(android.app.LocaleManager::class.java).systemLocales[0].language else resources.configuration.locales[0].language
        val english = language.english(systemLanguage)
        com.lonnnnnng.flclash.common.AppText.useEnglish = english
        com.lonnnnnng.flclash.ui.UiLanguage.english = english
        // 作者 long：Android 13起由平台同步快捷方式和应用资源；旧系统由Compose局部资源上下文呈现相同语种。
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val locales = android.os.LocaleList.forLanguageTags(when (language) {
                com.lonnnnnng.flclash.data.AppLanguage.SYSTEM -> ""
                com.lonnnnnng.flclash.data.AppLanguage.CHINESE -> "zh-CN"
                com.lonnnnnng.flclash.data.AppLanguage.ENGLISH -> "en"
            })
            val manager = getSystemService(android.app.LocaleManager::class.java)
            if (manager.applicationLocales != locales) manager.applicationLocales = locales
        }
        ensureNotificationChannel()
        android.service.quicksettings.TileService.requestListeningState(this, android.content.ComponentName(this, KClashTileService::class.java))
    }

    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onCreate() {
        // 作者 long：Hilt生成的super.onCreate先构建数据库和核心；隔离校验必须在这一步之前分流，避免无权限进程执行恢复。
        if(ValidationProcess.isCurrent())return
        super.onCreate()
        applicationScope.launch(Dispatchers.Main) {
            appearanceSettings.state.collect { applyAppearanceLanguage(it.language) }
        }
        // 作者 long：与Flutter初始化顺序一致，停止态的Provider/DNS和进程恢复加载也需要底层DNS。
        // 观察器属于应用进程，不能等前台服务创建后才注册，更不能由任一服务销毁时清空。
        NetworkObserver.start()
        onDemandCoordinator.start(applicationScope)
        SubscriptionAutoUpdateScheduler.schedule(this)
        com.lonnnnnng.flclash.data.ResourceAutoUpdateScheduler.schedule(this)
        runtimeInitialization = applicationScope.launch {
            val shouldResume = runtimeResumeStore.shouldResume()
            // 作者 long：先恢复上一轮诊断时间线，再决定是否自动重启核心；这样进程重建后的
            // CORE_RESTARTED 会紧接在崩溃前事件之后，而不是从空历史重新开始。
            runtimeManager.restoreDiagnosticHistory(markNextStartAsRestart = shouldResume)
            if (!shouldResume) return@launch
            // 作者 long：没有可交互的 Activity 时不能弹 VPN 授权页；授权失效则保留意图，等用户回到页面后重新授权。
            if (runtimeManager.vpnSettings.value.enabled &&
                VpnService.prepare(this@KClashApplication) != null) return@launch
            runtimeManager.startProxy()
        }
    }
}
