package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.content.res.Configuration
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest

internal fun assertPortraitDefaultFontConfiguration() {
    // 作者 long：设备自动旋转可能在测试中途改变宽度，每次采集前都核对方向和字号，防止错误画面进入基线。
    val configuration = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
    check(configuration.orientation == Configuration.ORIENTATION_PORTRAIT && configuration.fontScale == 1f) {
        "截图仅验收竖屏默认字号，实际 orientation=${configuration.orientation}, fontScale=${configuration.fontScale}, " +
            "bounds=${configuration.screenWidthDp}x${configuration.screenHeightDp}dp"
    }
}

internal fun prepareTouchVisualCapture() {
    assertPortraitDefaultFontConfiguration()
    // 作者 long：Compose的requestInputMode(Touch)只查询模式，不会退出键盘模式；固定截图须通过平台恢复触摸模式，避免前一焦点测试污染基线。
    InstrumentationRegistry.getInstrumentation().apply { setInTouchMode(true); waitForIdleSync() }
}

internal fun recordVisualPng(name: String, png: ByteArray) {
    if (InstrumentationRegistry.getArguments().getString("kclashCapturePng") != "true") return
    val encoded = Base64.encodeToString(png, Base64.NO_WRAP)
    if (InstrumentationRegistry.getArguments().getString("kclashCapturePngChunks") == "true") {
        // 作者 long：长正文截图可超过Binder事务上限并丢掉后续测试结果；显式分片协议保留原图，接收端必须核验完整索引和摘要。
        val chunks = encoded.chunked(48 * 1024)
        val digest = MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
        chunks.forEachIndexed { index, chunk ->
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_png_name", name)
                putString("kclash_png_chunk", chunk)
                putInt("kclash_png_index", index)
                putInt("kclash_png_count", chunks.size)
                putInt("kclash_png_bytes", png.size)
                putString("kclash_png_sha256", digest)
            })
        }
        return
    }
    // 作者 long：图片仅来自固定夹具；显式采集时复用测试结果通道，避免截图验收依赖 run-as 或设备存储权限。
    InstrumentationRegistry.getInstrumentation().sendStatus(
        2,
        Bundle().apply {
            putString("kclash_png_name", name)
            putString("kclash_png_base64", encoded)
        },
    )
}
