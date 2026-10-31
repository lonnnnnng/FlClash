package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry

internal fun recordVisualPng(name: String, png: ByteArray) {
    if (InstrumentationRegistry.getArguments().getString("kclashCapturePng") != "true") return
    // 作者 long：图片仅来自固定夹具；显式采集时复用测试结果通道，避免截图验收依赖 run-as 或设备存储权限。
    InstrumentationRegistry.getInstrumentation().sendStatus(
        2,
        Bundle().apply {
            putString("kclash_png_name", name)
            putString("kclash_png_base64", Base64.encodeToString(png, Base64.NO_WRAP))
        },
    )
}
