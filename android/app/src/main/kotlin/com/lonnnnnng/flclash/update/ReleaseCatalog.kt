package com.lonnnnnng.flclash.update

import com.google.gson.JsonParser

internal const val UPDATE_REPOSITORY = "lonnnnnng/FlClash"
data class AppRelease(val version: String, val versionCode: Long, val notes: String, val releaseUrl: String)

/** 作者 long：fork仍保留Flutter发布，只选择本仓库带Kotlin arm64安装包的稳定版，避免提示用户下载错应用。 */
internal object ReleaseCatalog {
    fun versionCode(version: String): Long {
        require(version.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))
        val parts = version.split('.').map { it.toLong() }
        require(parts[0] <= 2147 && parts[1] < 1000 && parts[2] < 1000)
        return (parts[0] * 1_000_000 + parts[1] * 1000 + parts[2]).also { require(it in 1..Int.MAX_VALUE.toLong()) }
    }

    fun parse(json: String): List<AppRelease> {
        require(json.toByteArray().size <= 2 * 1024 * 1024)
        return JsonParser.parseString(json).asJsonArray.mapNotNull { value ->
            val row = value.asJsonObject
            val tag = row["tag_name"]?.asString ?: return@mapNotNull null
            if (!tag.startsWith("kclash-v") || row["draft"]?.asBoolean != false || row["prerelease"]?.asBoolean != false) return@mapNotNull null
            val version = tag.removePrefix("kclash-v")
            val code = runCatching { versionCode(version) }.getOrNull() ?: return@mapNotNull null
            val name = "KClash-$version-android-arm64-v8a.apk"
            val expected = "https://github.com/$UPDATE_REPOSITORY/releases/download/$tag/$name"
            if (row.getAsJsonArray("assets")?.any { asset ->
                asset.asJsonObject["name"]?.asString == name && asset.asJsonObject["browser_download_url"]?.asString == expected
            } != true) return@mapNotNull null
            AppRelease(version, code, row["body"]?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(32768),
                "https://github.com/$UPDATE_REPOSITORY/releases/tag/$tag")
        }.sortedByDescending { it.versionCode }.distinctBy { it.versionCode }
    }

    fun check(json: String, currentVersionCode: Long): UpdateState {
        val latest = parse(json).firstOrNull() ?: error("暂无KClash正式版本")
        return UpdateState(if (latest.versionCode > currentVersionCode) UpdatePhase.AVAILABLE else UpdatePhase.LATEST, latest)
    }
}
