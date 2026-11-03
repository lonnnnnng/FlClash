package com.lonnnnnng.flclash.data

import java.net.URI

data class NamedResourceUrl(val url: String, val label: String = "") {
    fun suggestedName(fallback: String): String = label.ifBlank {
        runCatching { URI(url).path.orEmpty().substringAfterLast('/').substringBeforeLast('.') }.getOrDefault("").trim().ifBlank { fallback }
    }.take(128)
}
enum class ResourceImportResult { IMPORTED, SKIPPED }

object NamedResourceUrls {
    fun parse(text: String, label: String = ""): List<NamedResourceUrl> {
        require(text.length <= 1024 * 1024) { "URL 输入过长" }
        val urls = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty).distinct()
        require(urls.isNotEmpty() && urls.size <= 128) { "请输入 1 至 128 个 URL，每行一个" }
        require(label.trim().length <= 128) { "名称最多 128 字符" }
        // 作者 long：整批先校验再下载，避免末尾的无效链接让用户得到意外的部分导入。
        urls.forEach { url ->
            require(IconSetCodec.validUrl(url) && URI(url).fragment == null) { "存在无效 URL，请使用 HTTP/HTTPS 地址" }
        }
        return urls.map { NamedResourceUrl(it, if (urls.size == 1) label.trim() else "") }
    }
}
