package com.lonnnnnng.flclash.data

import java.net.URI
import java.util.Locale

internal data class GroupIconChoice(val name: String, val url: String)

/** 作者 long：单个策略组可复用用户最近手选的图片，避免依赖独立的全局资源目录。 */
internal object RecentGroupIcons {
    private val nonAlphanumeric = Regex("[^a-z0-9]")

    fun label(name: String): String = name.substringBeforeLast('.', name).replace('_', ' ').ifBlank { "本地图片" }

    fun record(url: String): GroupIconChoice = GroupIconChoice(
        if (url.startsWith("data:")) "" else
            runCatching { URI(url).path.substringAfterLast('/') }.getOrDefault("图标"),
        url,
    )

    fun suggested(recent: List<String>, target: String): List<GroupIconChoice> {
        val records = recent.filter(String::isNotBlank).distinct().map(::record)
        val targetKey = compact(target)
        data class Match(val icon: GroupIconChoice, val score: Int, val length: Int)
        val ranked = if (targetKey.isEmpty()) emptyList() else records.mapNotNull { icon ->
            val key = compact(icon.name.substringBeforeLast('.', icon.name))
            val score = when {
                key.isEmpty() -> 0
                key == targetKey -> 3
                key.length >= 3 && targetKey.contains(key) -> 2
                targetKey.length >= 3 && key.contains(targetKey) -> 1
                else -> 0
            }
            if (score == 0) null else Match(icon, score, key.length)
        }.sortedWith(compareByDescending<Match> { it.score }.thenBy { it.length }).take(24).map { it.icon }
        return (ranked + records).distinctBy { it.url }
    }

    private fun compact(name: String) = name.lowercase(Locale.ROOT).replace(nonAlphanumeric, "")
}
