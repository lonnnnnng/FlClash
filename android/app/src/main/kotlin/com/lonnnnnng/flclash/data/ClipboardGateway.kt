package com.lonnnnnng.flclash.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 作者 long：把系统剪贴板隔离在应用层边界之外，ViewModel 只处理列表和订阅分享等业务状态，
 * 这样单元测试可以使用内存 fake，同时避免把 Android Context 带进纯状态转换逻辑。
 */
interface ClipboardGateway {
    fun readText(): String?

    fun writeText(text: String)

    fun writeSensitiveText(text: String) = writeText(text)
}

class AndroidClipboardGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClipboardGateway {
    private val clipboard = checkNotNull(
        context.getSystemService(ClipboardManager::class.java),
    ) { "系统剪贴板服务不可用" }

    override fun readText(): String? = clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()

    override fun writeText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("KClash", text))
    }

    override fun writeSensitiveText(text: String) {
        // 作者 long：订阅链接可能携带凭据，只隐藏系统剪贴板预览，实际粘贴值仍保留原链接。
        val clip = ClipData.newPlainText("KClash", text)
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
    }
}
