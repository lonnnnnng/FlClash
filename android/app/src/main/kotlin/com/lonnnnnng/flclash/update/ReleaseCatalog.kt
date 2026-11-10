package com.lonnnnnng.flclash.update

import com.google.gson.JsonParser
import java.net.URI

internal const val UPDATE_REPOSITORY = "lonnnnnng/FlClash"
internal const val UPDATE_PACKAGE = "com.lonnnnnng.flclash"
internal const val UPDATE_CERTIFICATE = "3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49"
internal const val MAX_APK_BYTES = 512L * 1024 * 1024

data class AppRelease(val version: String, val versionCode: Long, val apkName: String, val apkUrl: String,
    val apkBytes: Long, val checksumsUrl: String, val notes: String, val releaseUrl: String, val assetDigest: String?)

/** 作者 long：同一fork可能保留Flutter发布历史，只识别Kotlin标签和本仓库arm64资产，不能按最新任意tag升级。 */
internal object ReleaseCatalog {
    fun versionCode(version: String): Long {
        require(version.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) { "版本格式无效" }
        val parts = version.split('.').map { it.toLong() }
        require(parts[0] <= 2147 && parts[1] < 1000 && parts[2] < 1000)
        return (parts[0] * 1_000_000 + parts[1] * 1000 + parts[2]).also { require(it in 1..Int.MAX_VALUE.toLong()) }
    }
    fun parse(json: String): List<AppRelease> {
        require(json.toByteArray().size <= 2 * 1024 * 1024) { "发布列表过大" }
        val rows = JsonParser.parseString(json).asJsonArray
        require(rows.size() <= 100)
        return rows.mapNotNull { value ->
            val row = value.asJsonObject
            val tag = row["tag_name"]?.asString ?: return@mapNotNull null
            if (!tag.startsWith("kclash-v") || row["draft"]?.asBoolean != false || row["prerelease"]?.asBoolean != false) return@mapNotNull null
            val version = tag.removePrefix("kclash-v")
            val code = runCatching { versionCode(version) }.getOrNull() ?: return@mapNotNull null
            val filename = "KClash-$version-android-arm64-v8a.apk"
            val assets = row.getAsJsonArray("assets")?.map { it.asJsonObject } ?: return@mapNotNull null
            val apk = assets.singleOrNull { it["name"]?.asString == filename } ?: return@mapNotNull null
            val sums = assets.singleOrNull { it["name"]?.asString == "SHA256SUMS" } ?: return@mapNotNull null
            val size = apk["size"]?.asLong ?: return@mapNotNull null
            if (size !in 1..MAX_APK_BYTES) return@mapNotNull null
            val prefix = "https://github.com/$UPDATE_REPOSITORY/releases/download/$tag/"
            val apkUrl = apk["browser_download_url"]?.asString ?: return@mapNotNull null
            val sumsUrl = sums["browser_download_url"]?.asString ?: return@mapNotNull null
            if (apkUrl != prefix + filename || sumsUrl != prefix + "SHA256SUMS") return@mapNotNull null
            val releaseUrl = "https://github.com/$UPDATE_REPOSITORY/releases/tag/$tag"
            val digest = apk["digest"]?.takeUnless { it.isJsonNull }?.asString?.removePrefix("sha256:")
            if (digest != null && !digest.matches(Regex("[0-9a-f]{64}"))) return@mapNotNull null
            AppRelease(version, code, filename, apkUrl, size, sumsUrl, row["body"]?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(32768), releaseUrl, digest)
        }.sortedByDescending { it.versionCode }.distinctBy { it.versionCode }
    }
    fun checksum(content: String, release: AppRelease): String {
        require(content.toByteArray().size <= 64 * 1024) { "校验清单过大" }
        val matches = content.lineSequence().mapNotNull { line ->
            Regex("^([0-9a-fA-F]{64})[ \\t]+\\*?([^/\\\\]+)$").matchEntire(line.trim())
                ?.takeIf { it.groupValues[2] == release.apkName }?.groupValues?.get(1)?.lowercase()
        }.toList()
        require(matches.size == 1) { "发布资产缺少唯一的SHA256校验值" }
        return matches.single().also { require(release.assetDigest == null || release.assetDigest == it) { "发布资产校验信息不一致" } }
    }
    fun allowedDownloadUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            uri.host?.lowercase() in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)
}
