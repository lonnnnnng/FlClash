package com.lonnnnnng.flclash.update

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val UPDATE_REPOSITORY = "lonnnnnng/FlClash"
data class AppRelease(val version: String, val versionCode: Long, val releaseUrl: String)

/** 作者 long：fork仍保留Flutter发布，只接受自己仓库的正式Kotlin标签，避免提示用户下载错应用。 */
internal object ReleaseCatalog {
    fun versionCode(version: String): Long {
        require(version.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))
        val parts = version.split('.').map { it.toLong() }
        require(parts[0] <= 2147 && parts[1] < 1000 && parts[2] < 1000)
        return (parts[0] * 1_000_000 + parts[1] * 1000 + parts[2]).also { require(it in 1..Int.MAX_VALUE.toLong()) }
    }

    fun check(latest: AppRelease, currentVersionCode: Long): UpdateState {
        return UpdateState(if (latest.versionCode > currentVersionCode) UpdatePhase.AVAILABLE else UpdatePhase.LATEST, latest)
    }

    fun fromLatestRedirect(location: String?): AppRelease {
        val url = requireNotNull(location?.toHttpUrlOrNull()) { "发布页未返回有效版本" }
        val prefix = "/$UPDATE_REPOSITORY/releases/tag/kclash-v"
        // 作者 long：只信任自己仓库的正式Kotlin标签，拒绝其他站点、Flutter标签和预发布。
        require(url.scheme == "https" && url.host == "github.com" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
            url.encodedPath.startsWith(prefix)) { "发布页未返回KClash正式版本" }
        val version = url.encodedPath.removePrefix(prefix)
        return AppRelease(version, versionCode(version), "https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v$version")
    }
}
