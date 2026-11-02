package com.lonnnnnng.flclash.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MAX_PROFILE_FILE_BYTES = 8 * 1024 * 1024

class ProfileFileDocument(val name: String, val bytes: ByteArray) {
    override fun toString(): String = "ProfileFileDocument(size=${bytes.size})"
}

fun interface ProfileFileDocumentReader {
    suspend fun read(uri: String): ProfileFileDocument
}

class AndroidProfileFileDocumentReader(private val context: Context) : ProfileFileDocumentReader {
    override suspend fun read(uri: String): ProfileFileDocument = withContext(Dispatchers.IO) {
        val source = Uri.parse(uri)
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(source)?.use { readProfileFileBytes(it) }
            ?: error("无法读取所选配置文件")
        val name = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().ifBlank { "本地配置.yaml" }
        ProfileFileDocument(name, bytes)
    }
}

internal fun readProfileFileBytes(input: InputStream, allowEmpty: Boolean = false): ByteArray {
    // 作者 long：SAF 的大小元数据不可靠，按实际读取预算截断，避免云端文件令编辑页无界分配内存。
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, MAX_PROFILE_FILE_BYTES + 1 - output.size()))
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
        require(output.size() <= MAX_PROFILE_FILE_BYTES) { "配置文件超过 8 MiB" }
    }
    val bytes = output.toByteArray()
    require(allowEmpty || bytes.isNotEmpty()) { "配置文件为空" }
    return bytes
}
