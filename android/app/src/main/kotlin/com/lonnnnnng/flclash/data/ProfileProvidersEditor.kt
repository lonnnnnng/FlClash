package com.lonnnnnng.flclash.data

/**
 * 作者 long：Provider 分区是顶层 YAML map，先按 proxy-providers/rule-providers 分开编辑，
 * 保存时只替换对应 map，避免用户调整 provider 时重排代理组、规则和其他配置。
 */
object ProfileProvidersEditor {
    data class Sections(
        val proxyProviders: String = "",
        val ruleProviders: String = "",
    )

    private val topLevelKeyPattern = Regex("^[A-Za-z0-9_-]+$")
    // 作者 long：全局资源常用中文名称，YAML 会不加引号输出；同时保留带冒号与转义引号的合法键。
    private val mapEntryPattern = Regex("""^('(?:[^']|'')*'|"(?:[^"\\]|\\.)*"|[^:]+?)\s*:\s*(.*)$""")

    fun extract(yaml: String): Sections = Sections(
        proxyProviders = extractSection(yaml, "proxy-providers"),
        ruleProviders = extractSection(yaml, "rule-providers"),
    )

    /**
     * 作者 long：字段化覆盖只展示配置里真实存在的代理 Provider，避免用户给已经删除的
     * Provider 保存一份永远不会被核心读取的孤立覆盖。
     */
    fun proxyProviderNames(editorText: String): List<String> = runCatching {
        parseEntries(editorText, "proxy-providers").map { normalizeKey(it.first()) }
    }.getOrDefault(emptyList())

    fun replace(yaml: String, sections: Sections): String {
        val root = ProxyGroupDrafts.root(yaml)
        val lines = normalizedLines(yaml)
        val flowRoot = lines.firstOrNull { it.isNotBlank() && !it.trimStart().startsWith('#') }?.trimStart()?.startsWith('{') == true
        // 作者 long：flow 根对象、带引号或整体缩进的分区无法按行定位，语义替换才能避免追加同名分区。
        if (listOf("proxy-providers", "rule-providers").any { key ->
            val line = findSectionStart(lines, key)?.let { lines[it] }
            val value = root.get(key)
            // 作者 long：脚本 codec 会补空代理 Provider map；只有原文分区才需要语义替换，不能把默认值当成现有定义。
            val irregularSection = line == null && (flowRoot || value?.takeIf { it.isJsonObject }?.asJsonObject?.size()?.let { it > 0 } == true ||
                lines.any { it.trimStart().startsWith("\"$key\":") || it.trimStart().startsWith("'$key':") ||
                    (it.firstOrNull()?.isWhitespace() == true && it.trimStart().startsWith("$key:")) })
            root.has(key) && (irregularSection || line?.substringAfter(':')?.trim()?.let { it.isNotEmpty() && it != "{}" && !it.startsWith('#') } == true)
        }) {
            listOf("proxy-providers" to sections.proxyProviders, "rule-providers" to sections.ruleProviders).forEach { (key, value) ->
                if (root.has(key) || value.isNotBlank()) {
                    val providers = ProxyGroupDrafts.root(renderSection(key, parseEntries(value, key)).joinToString("\n")).get(key)
                    require(providers == null || providers.isJsonNull || providers.isJsonObject) { "Provider 分区必须是 map" }
                    root.add(key, providers?.takeIf { it.isJsonObject } ?: com.google.gson.JsonObject())
                }
            }
            return com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(root.toString())
        }
        var updated = yaml
        listOf(
            "proxy-providers" to sections.proxyProviders,
            "rule-providers" to sections.ruleProviders,
        ).forEach { (key, editorText) ->
            val hasSection = findSectionStart(normalizedLines(updated), key) != null
            if (hasSection || editorText.isNotBlank()) {
                updated = replaceSection(updated, key, editorText)
            }
        }
        return updated
    }

    private fun extractSection(yaml: String, key: String): String {
        val lines = normalizedLines(yaml)
        val start = findSectionStart(lines, key) ?: return ProxyGroupDrafts.root(yaml).get(key)?.takeIf { it.isJsonObject && it.asJsonObject.size() > 0 }
            ?.let { com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(it.toString()).trimEnd() }.orEmpty()
        if (lines[start].substringAfter(':').trim() == "{}") return ""
        if (lines[start].substringAfter(':').trim().let { it.isNotEmpty() && !it.startsWith('#') }) {
            return ProxyGroupDrafts.root(yaml).get(key)?.takeIf { it.isJsonObject && it.asJsonObject.size() > 0 }
                ?.let { com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(it.toString()).trimEnd() }.orEmpty()
        }
        val end = findSectionEnd(lines, start)
        val contentEnd = end - trailingBoundaryLines(lines, start, end)
        return lines.subList(start + 1, contentEnd)
            .joinToString("\n") { line ->
                when {
                    line.startsWith("  ") -> line.removePrefix("  ")
                    line.isBlank() -> ""
                    else -> line.trim()
                }
            }
            .trim('\n')
    }

    private fun replaceSection(yaml: String, key: String, editorText: String): String {
        val entries = parseEntries(editorText, key)
        val lines = normalizedLines(yaml)
        val start = findSectionStart(lines, key)
        val rendered = renderSection(key, entries)

        if (start == null) {
            val prefix = lines.joinToString("\n").trimEnd()
            return buildString {
                if (prefix.isNotEmpty()) {
                    append(prefix)
                    append("\n")
                }
                append(rendered.joinToString("\n"))
                append("\n")
            }
        }

        val end = findSectionEnd(lines, start)
        val contentEnd = end - trailingBoundaryLines(lines, start, end)
        val trailing = lines.subList(contentEnd, end)
        return buildList {
            addAll(lines.subList(0, start))
            addAll(rendered)
            addAll(trailing)
            addAll(lines.subList(end, lines.size))
        }.joinToString("\n").trimEnd() + "\n"
    }

    private fun parseEntries(editorText: String, key: String): List<List<String>> {
        val sourceLines = normalizedLines(editorText)
        val firstContentIndex = sourceLines.indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (firstContentIndex < 0) return emptyList()

        val firstContent = sourceLines[firstContentIndex].trim()
        if (firstContent == "{}") return emptyList()
        // 作者 long：订阅中的规则资源常用行内 map；按完整 YAML 读取后展开，不能在绑定代理资源时拒绝这些既有定义。
        if (firstContent.startsWith('{') || sourceLines.any { line ->
                mapEntryPattern.matchEntire(line.trimStart())?.groupValues?.get(2)?.trimStart()?.startsWith('{') == true
            }) {
            // 作者 long：放入独立分区，隔离脚本 codec 为完整配置补的默认 proxy-providers，避免把默认空 map 当资源条目。
            val document = "provider-editor:\n" + editorText.lines().joinToString("\n") { "  $it" }
            val parsed = com.google.gson.JsonParser.parseString(com.lonnnnnng.flclash.service.YamlConfigCodec.toJson(document))
                .asJsonObject.get("provider-editor")
            require(parsed.isJsonObject) { "Provider 分区必须是 map" }
            val wrapped = firstContent.startsWith("$key:")
            val providers = if (wrapped) parsed.asJsonObject.get(key) else parsed
            require(providers?.isJsonObject == true) { "Provider 分区必须是 map" }
            return providers.asJsonObject.entrySet().map { (name, value) ->
                require(name.isNotBlank() && value.isJsonObject && value.asJsonObject.size() > 0) { "Provider 条目必须使用非空 map" }
                val entry = com.google.gson.JsonObject().apply { add(name, value) }
                normalizedLines(com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(entry.toString()))
            }
        }
        val body = when {
            firstContent == "$key: {}" -> return emptyList()
            firstContent == "$key:" -> sourceLines.drop(firstContentIndex + 1)
            else -> sourceLines.drop(firstContentIndex)
        }
        val lines = dedent(body)
        val entries = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null

        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()
            if (line.isBlank() || trimmed.startsWith("#")) {
                current?.add(line.trimEnd())
                return@forEachIndexed
            }
            if (line == trimmed) {
                val match = mapEntryPattern.matchEntire(trimmed)
                    ?: throw IllegalArgumentException("第 ${index + 1} 行 Provider map 格式无效")
                require(match.groupValues[2].trim().isEmpty() || match.groupValues[2].trim() == "{}") {
                    "第 ${index + 1} 行 Provider 条目必须使用嵌套 map"
                }
                val entry = mutableListOf(line.trimEnd())
                current = entry
                entries += entry
                return@forEachIndexed
            }
            val entry = requireNotNull(current) {
                "第 ${index + 1} 行必须从 Provider 条目开始"
            }
            require(line.length - trimmed.length >= 2) {
                "第 ${index + 1} 行 Provider 字段必须缩进"
            }
            entry.add(line.trimEnd())
        }

        val names = mutableSetOf<String>()
        entries.forEachIndexed { index, entry ->
            val name = normalizeKey(entry.firstOrNull().orEmpty())
            require(name.isNotEmpty()) { "第 ${index + 1} 个 Provider 名称不能为空" }
            require(names.add(name)) { "Provider 名称重复：$name" }
            require(entry.drop(1).any { it.isNotBlank() && !it.trimStart().startsWith("#") }) {
                "第 ${index + 1} 个 Provider 配置不能为空"
            }
        }
        return entries
    }

    private fun renderSection(key: String, entries: List<List<String>>): List<String> {
        if (entries.isEmpty()) return listOf("$key: {}")
        return buildList {
            add("$key:")
            entries.forEach { entry ->
                entry.forEach { line ->
                    if (line.isBlank()) add("") else add("  $line")
                }
            }
        }
    }

    private fun normalizeKey(line: String): String {
        val key = mapEntryPattern.matchEntire(line)?.groupValues?.get(1)?.trim() ?: return ""
        val json = com.lonnnnnng.flclash.service.YamlConfigCodec.toJson("providers:\n  $key: {}\n")
        return com.google.gson.JsonParser.parseString(json).asJsonObject.getAsJsonObject("providers").keySet().single()
    }

    private fun dedent(lines: List<String>): List<String> {
        val indent = lines
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .minOfOrNull { it.length - it.trimStart().length }
            ?: 0
        if (indent == 0) return lines
        return lines.map { line -> if (line.length >= indent) line.drop(indent) else "" }
    }

    private fun normalizedLines(yaml: String): List<String> =
        yaml.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList().apply {
            if (lastOrNull().isNullOrEmpty()) removeAt(lastIndex)
        }

    private fun findSectionStart(lines: List<String>, key: String): Int? =
        lines.indexOfFirst { topLevelKey(it) == key }.takeIf { it >= 0 }

    private fun findSectionEnd(lines: List<String>, start: Int): Int =
        (start + 1 until lines.size).firstOrNull { topLevelKey(lines[it]) != null } ?: lines.size

    private fun trailingBoundaryLines(lines: List<String>, start: Int, end: Int): Int {
        var count = 0
        for (index in end - 1 downTo start + 1) {
            val line = lines[index]
            if (line.isBlank() || (line.firstOrNull()?.let { !it.isWhitespace() } == true && line.trimStart().startsWith("#"))) {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun topLevelKey(line: String): String? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        return line.substring(0, colon).trim().takeIf { it.matches(topLevelKeyPattern) }
    }
}
