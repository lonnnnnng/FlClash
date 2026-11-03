package com.lonnnnnng.flclash.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** 作者 long：快照保留打开时的正文指纹和时间；跨进程只走加密草稿，日志不可展开节点凭据。 */
class ProfileDocumentSnapshot(val profile: Profile, val content: String, val digest: String, val modified: Long) {
    override fun toString() = "ProfileDocumentSnapshot(id=${profile.id}, size=${content.length})"
}

data class ProfileDocumentSettings(val label: String, val url: String, val ageSecretKey: String,
    val autoUpdate: Boolean, val intervalMillis: Long) {
    override fun toString() = "ProfileDocumentSettings([REDACTED])"
}

internal fun profileDocumentText(bytes: ByteArray): String {
    require(bytes.isNotEmpty() && bytes.size <= MAX_PROFILE_FILE_BYTES) { "配置文件为空或超过 8 MiB" }
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    } catch (_: Exception) { throw IllegalArgumentException("配置文件不是有效的 UTF-8 文本") }
    require(text.isNotBlank() && '\u0000' !in text) { "配置文件不是有效的文本" }
    return text
}

internal fun profileDocumentDigest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

internal fun Profile.documentSettings() = ProfileDocumentSettings(label, url, ageSecretKey, autoUpdate, autoUpdateIntervalMillis)
