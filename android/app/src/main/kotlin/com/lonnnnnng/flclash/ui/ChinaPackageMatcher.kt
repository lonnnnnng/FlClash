package com.lonnnnnng.flclash.ui

/**
 * 作者 long：智能选择沿用 Flutter 端的包名前缀和排除语义，把国内应用放入黑名单、
 * 其余已加载应用放入白名单；当前 Kotlin 仅展示启动器应用，因此不扫描用户不可见的安装包。
 */
internal object ChinaPackageMatcher {
    fun isChinaPackage(packageName: String): Boolean {
        if (SKIPPED_PREFIXES.any { packageName == it || packageName.startsWith("$it.") }) {
            return false
        }
        return CHINA_PREFIXES.any { packageName.startsWith(it) }
    }

    private val SKIPPED_PREFIXES = listOf(
        "com.google",
        "com.android.chrome",
        "com.android.vending",
        "com.microsoft",
        "com.apple",
        "com.zhiliaoapp.musically",
        "com.mxtech",
        "com.stubhub",
    )

    // 作者 long：这些前缀必须保留无点边界，才能覆盖 com.qihoo360、com.aliyun 等真实包名。
    private val CHINA_PREFIXES = listOf(
        "com.tencent",
        "com.alibaba",
        "com.umeng",
        "com.qihoo",
        "com.ali",
        "com.alipay",
        "com.amap",
        "com.sina",
        "com.weibo",
        "com.vivo",
        "com.xiaomi",
        "com.huawei",
        "com.taobao",
        "com.secneo",
        "s.h.e.l.l",
        "com.stub",
        "com.kiwisec",
        "com.secshell",
        "com.wrapper",
        "cn.securitystack",
        "com.mogosec",
        "com.secoen",
        "com.netease",
        "com.mx",
        "com.qq.e",
        "com.baidu",
        "com.bytedance",
        "com.bugly",
        "com.miui",
        "com.oppo",
        "com.coloros",
        "com.iqoo",
        "com.meizu",
        "com.gionee",
        "cn.nubia",
        "com.oplus",
        "andes.oplus",
        "com.unionpay",
        "cn.wps",
    )
}
