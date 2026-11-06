package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.normalizeSubscriptionUrl

internal data class ProfileUrlImportPreview(
    val urls: List<String> = emptyList(),
    val skippedExisting: Int = 0,
    val invalidCount: Int = 0,
)

/** 作者 long：单条导入也在离开表单前校验，错误只描述格式，避免把订阅凭据带入提示或日志。 */
internal fun profileUrlInputError(value: String): String? {
    val url = value.trim()
    if (url.isEmpty()) return "请输入订阅链接"
    if (url.length > MAX_PROFILE_URL_LENGTH) return "订阅链接不能超过 $MAX_PROFILE_URL_LENGTH 个字符"
    return if (runCatching { normalizeSubscriptionUrl(url) }.isSuccess) null else "请输入有效的 HTTP/HTTPS 订阅链接"
}

/**
 * 作者 long：粘贴配置列表时沿用 Flutter 的行、YAML 列表标记和引号解析规则，
 * 先在界面反馈重复及无效条目，避免用户提交后才发现整批链接不可导入。
 */
internal fun parseProfileUrlImport(text: String, existingUrls: Set<String>): ProfileUrlImportPreview {
    val urls = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var skippedExisting = 0
    var invalidCount = 0

    text.lineSequence().forEach { originalLine ->
        var line = originalLine.trim()
        if (line.startsWith("- ")) line = line.removePrefix("- ").trim()
        if (line.endsWith(',')) line = line.dropLast(1).trim()

        line.split(Regex("\\s+"))
            .map { token ->
                if (token.length >= 2 && token.first() == token.last() && token.first() in setOf('\'', '"')) {
                    token.substring(1, token.lastIndex).trim()
                } else {
                    token
                }
            }
            .filter(String::isNotEmpty)
            .forEach { url ->
                val valid = profileUrlInputError(url) == null
                if (!valid) {
                    invalidCount++
                } else if (seen.add(url)) {
                    if (url in existingUrls) skippedExisting++ else urls += url
                }
            }
    }

    return ProfileUrlImportPreview(urls, skippedExisting, invalidCount)
}

private const val MAX_PROFILE_URL_LENGTH = 2_048
