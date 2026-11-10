package com.lonnnnnng.flclash

import android.app.Application
import android.os.Build
import java.io.File

/** 作者 long：隔离校验进程没有主库权限，也不能启动Hilt核心恢复；API24仍按明确进程名识别。 */
internal object ValidationProcess {
    fun isCurrent():Boolean {
        if(Build.VERSION.SDK_INT>=28&&android.os.Process.isIsolated())return true
        val name=if(Build.VERSION.SDK_INT>=28)Application.getProcessName() else runCatching {
            File("/proc/self/cmdline").readText().substringBefore('\u0000')
        }.getOrDefault("")
        return name.endsWith(":config_validation")||name.contains(":config_validation:")
    }
}
