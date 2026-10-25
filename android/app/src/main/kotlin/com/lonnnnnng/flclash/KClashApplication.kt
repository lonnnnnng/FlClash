package com.lonnnnnng.flclash

import android.app.Application
import android.content.Context
import com.lonnnnnng.flclash.common.GlobalState
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KClashApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 作者 long：服务进程也依赖同一个应用上下文，必须在任何前台服务启动前初始化共享状态。
        GlobalState.init(this)
    }
}
