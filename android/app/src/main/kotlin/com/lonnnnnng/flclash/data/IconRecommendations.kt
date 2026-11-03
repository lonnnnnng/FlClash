package com.lonnnnnng.flclash.data

import java.net.URI
import java.util.Locale

internal data class GroupIconSource(val key: String, val label: String, val icons: List<ResourceIcon>)

/** 作者 long：推荐沿用 Flutter 的英文名称匹配，只在已有图库和最近记录中查找，不发起资源下载。 */
internal object IconRecommendations {
    const val RECOMMENDED = "recommended"
    private val nonAlphanumeric = Regex("[^a-z0-9]")
    fun label(icon: ResourceIcon): String = stem(icon.name).replace('_', ' ').ifBlank { "本地图片" }
    private fun stem(name: String) = name.substringBeforeLast('.', name)
    private fun compact(name: String) = name.lowercase(Locale.ROOT).replace(nonAlphanumeric, "")
    fun record(url: String): ResourceIcon = ResourceIcon(if (url.startsWith("data:")) "" else
        runCatching { URI(url).path.substringAfterLast('/') }.getOrDefault("图标"), url)

    fun sources(sets: List<ResourceIconSet>, recent: List<String>, target: String): List<GroupIconSource> {
        val records = recent.filter(String::isNotBlank).distinct().map(::record)
        val all = sets.flatMap { it.icons } + records
        val name = compact(target)
        data class Match(val icon: ResourceIcon, val score: Int, val length: Int)
        val seen = mutableSetOf<String>()
        val ranked = if (name.isEmpty()) emptyList() else all.mapNotNull { icon ->
            val key = compact(stem(icon.name))
            val score = when {
                key.isEmpty() -> 0
                key == name -> 3
                key.length >= 3 && name.contains(key) -> 2
                name.length >= 3 && key.contains(name) -> 1
                else -> 0
            }
            if (score == 0 || !seen.add(icon.url)) null else Match(icon, score, key.length)
        }.sortedWith(compareByDescending<Match> { it.score }.thenBy { it.length }).take(24).map { it.icon }
        val suggested = (ranked + records).distinctBy { it.url }
        return buildList {
            if (suggested.isNotEmpty()) add(GroupIconSource(RECOMMENDED, "推荐图标", suggested))
            sets.filter { it.icons.isNotEmpty() }.forEach { add(GroupIconSource("set:${it.id}", it.name, it.icons)) }
        }
    }

    fun initialKey(sources: List<GroupIconSource>, value: String): String? =
        sources.firstOrNull { it.key != RECOMMENDED && it.icons.any { icon -> icon.url == value } }?.key ?: sources.firstOrNull()?.key
}
