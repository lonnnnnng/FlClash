package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 作者 long：导出正文仅驻留内存，不生成包含配置内容的 toString，也不放入 SavedState 或临时共享目录。
class ProfileExport(val fileName: String, val bytes: ByteArray)

fun interface ProfileExportReader {
    suspend fun readExport(profileId: Long): ProfileExport
}

fun interface ProfileDocumentWriter {
    suspend fun write(destination: String, bytes: ByteArray)
}

class AndroidProfileDocumentWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProfileDocumentWriter {
    override suspend fun write(destination: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination.toUri(), "wt")?.use { it.write(bytes) }
            ?: error("无法打开导出文件")
    }
}

internal fun readProfileExport(file: File, label: String): ProfileExport {
    check(file.isFile) { "配置文件不存在" }
    // 作者 long：只导出原缓存字节，不解析或重跑脚本；限定读取预算，损坏或超大缓存不能耗尽页面内存。
    val bytes = file.inputStream().use { it.readNBytesCompat(MAX_EXPORT_BYTES + 1) }
    check(bytes.isNotEmpty()) { "配置文件为空" }
    check(bytes.size <= MAX_EXPORT_BYTES) { "配置超过 8 MiB，暂不支持导出" }
    return ProfileExport(profileExportFileName(label), bytes)
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val result = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (result.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - result.size()))
        if (count < 0) break
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}

internal fun profileExportFileName(label: String): String {
    val stem = label.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_")
        .trim().trim('.').take(80).ifBlank { "配置" }
    return "$stem.yaml"
}

private const val MAX_EXPORT_BYTES = 8 * 1024 * 1024
