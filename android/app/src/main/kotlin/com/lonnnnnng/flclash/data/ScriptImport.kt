package com.lonnnnnng.flclash.data

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal const val SCRIPT_MAX_BYTES = 2 * 1024 * 1024

/** 作者 long：本地文件和远程下载共用严格UTF-8边界，避免二进制被替换字符静默保存成可执行脚本。 */
internal fun decodeScriptBytes(bytes: ByteArray): String {
    require(bytes.size <= SCRIPT_MAX_BYTES) { "脚本文件超过 2 MiB 限制" }
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    } catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("脚本必须是 UTF-8 文本") }
    require(text.isNotBlank() && '\u0000' !in text) { "脚本内容必须是非空文本" }
    return text
}

internal fun readScriptBytes(input: InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return output.toByteArray()
        require(output.size() + count <= SCRIPT_MAX_BYTES) { "脚本文件超过 2 MiB 限制" }
        output.write(buffer, 0, count)
    }
}

internal fun uniqueScriptLabel(candidate: String, existing: List<String>): String {
    val stem = candidate.trim().ifBlank { "脚本" }
    var label = stem
    var index = 1
    while (label in existing) { label = "$stem-${index.toString().padStart(2, '0')}"; index++ }
    return label
}
