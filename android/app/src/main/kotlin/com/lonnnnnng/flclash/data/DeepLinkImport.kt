package com.lonnnnnng.flclash.data

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DeepLinkProfileImport(
    val url: String,
    val label: String?,
)

/**
 * 作者 long：Deep Link 只负责把外部链接转换成现有订阅导入所需的 HTTP(S) 地址，
 * 不在 Activity 中重复下载、写文件或创建 Profile，避免外部入口形成第二套数据链路。
 */
object DeepLinkProfileParser {
    private val supportedSchemes = setOf("clash", "clashmeta", "flclash", "kclash")

    fun parse(raw: String): DeepLinkProfileImport? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in supportedSchemes) return null
        if (uri.host?.lowercase() != INSTALL_CONFIG_HOST) return null

        val query = parseQuery(uri.rawQuery ?: return null)
        val profileUrl = query[URL_PARAMETER]?.trim().orEmpty()
        if (!isHttpUrl(profileUrl)) return null

        return DeepLinkProfileImport(
            url = profileUrl,
            label = query[LABEL_PARAMETER]?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun isSupported(raw: String): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in supportedSchemes
    }

    fun parseProfileUrl(raw: String): String? {
        val text = raw.trim()
        if (isHttpUrl(text)) return text
        return parse(text)?.url
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank()
    }

    private fun parseQuery(rawQuery: String): Map<String, String> =
        rawQuery.split('&')
            .asSequence()
            .filter { it.isNotEmpty() }
            .mapNotNull { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = decode(item.substring(0, separator)) ?: return@mapNotNull null
                val value = decode(item.substring(separator + 1)) ?: return@mapNotNull null
                key to value
            }
            .toMap()

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()

    private const val INSTALL_CONFIG_HOST = "install-config"
    private const val URL_PARAMETER = "url"
    private const val LABEL_PARAMETER = "name"
}

/**
 * 作者 long：相册二维码只读取 QR 格式，并把所有候选文本交给统一链接解析器，
 * 这样二维码、Deep Link 和手工订阅入口最终都走同一个 ProfileRepository 写入边界。
 */
object QrCodeImageImporter {
    suspend fun decode(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return try {
            scanner.process(image).await().mapNotNull { it.rawValue?.trim() }
        } finally {
            scanner.close()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        if (continuation.isActive) continuation.cancel()
    }
}
