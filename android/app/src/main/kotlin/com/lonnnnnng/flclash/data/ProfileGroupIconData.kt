package com.lonnnnnng.flclash.data

import okio.ByteString.Companion.toByteString

/** 作者 long：本地图片嵌入配置，限制正文大小并从文件签名识别格式，不把任意文档当成图片持久化。 */
object ProfileGroupIconData {
    fun encode(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 128 * 1024) { "请选择不超过 128 KiB 的图片" }
        val header = bytes.take(16).toByteArray().toString(Charsets.ISO_8859_1)
        val mime = when {
            header.startsWith("\u0089PNG\r\n\u001a\n") -> "image/png"
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
            header.startsWith("GIF87a") || header.startsWith("GIF89a") -> "image/gif"
            header.length >= 12 && header.startsWith("RIFF") && header.substring(8).startsWith("WEBP") -> "image/webp"
            bytes.take(1024).toByteArray().toString(Charsets.UTF_8).contains("<svg") -> "image/svg+xml"
            else -> error("无法识别图片，请选择 PNG、JPEG、GIF、WebP 或 SVG")
        }
        return "data:$mime;base64," + bytes.toByteString().base64()
    }
}
