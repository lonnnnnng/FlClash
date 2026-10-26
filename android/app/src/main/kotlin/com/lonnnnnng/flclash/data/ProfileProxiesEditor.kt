package com.lonnnnnng.flclash.data

/**
 * 作者 long：代理分区编辑只改写自定义 Profile 的顶层 `proxies` 列表，
 * 保留端口、DNS、规则和其他未编辑字段，避免局部编辑重排整份配置。
 */
object ProfileProxiesEditor {
    private val fieldPattern = Regex("^([A-Za-z0-9_-]+)\\s*:\\s*(.*)$")
    private val topLevelKeyPattern = Regex("^[A-Za-z0-9_-]+$")

    fun extractProxies(yaml: String): String {
        val lines = normalizedLines(yaml)
        val start = findProxiesStart(lines) ?: return ""
        if (lines[start].substringAfter(':').trim() == "[]") return ""
        val end = findSectionEnd(lines, start)
        return lines
            .subList(start + 1, end)
            .joinToString("\n") { line ->
                when {
                    line.startsWith("  ") -> line.removePrefix("  ")
                    line.isBlank() -> ""
                    else -> line.trim()
                }
            }
            .trim('\n')
    }

    fun replaceProxies(yaml: String, editorText: String): String {
        val entries = parseEntries(editorText)
        val lines = normalizedLines(yaml)
        val start = findProxiesStart(lines)
        val rendered = renderSection(entries)

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
        return buildList {
            addAll(lines.subList(0, start))
            addAll(rendered)
            addAll(lines.subList(end, lines.size))
        }.joinToString("\n").trimEnd() + "\n"
    }

    private fun parseEntries(editorText: String): List<List<String>> {
        val sourceLines = normalizedLines(editorText)
        val firstContentIndex = sourceLines.indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (firstContentIndex < 0) return emptyList()

        val firstContent = sourceLines[firstContentIndex].trim()
        val body = when {
            firstContent == "proxies: []" -> return emptyList()
            firstContent == "proxies:" -> sourceLines.drop(firstContentIndex + 1)
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
            if (line == trimmed && trimmed.startsWith("-")) {
                require(trimmed == "-" || trimmed.startsWith("- ")) {
                    "第 ${index + 1} 行代理条目格式无效"
                }
                current = mutableListOf(line.trimEnd())
                entries += current
                return@forEachIndexed
            }
            val entry = requireNotNull(current) {
                "第 ${index + 1} 行必须从列表条目开始"
            }
            require(line.length - trimmed.length >= 2) {
                "第 ${index + 1} 行代理字段必须缩进"
            }
            entry.add(line.trimEnd())
        }

        val names = mutableSetOf<String>()
        entries.forEachIndexed { index, entry ->
            val fields = entryFields(entry)
            val name = fields["name"].orEmpty().trim()
            val type = fields["type"].orEmpty().trim()
            require(name.isNotEmpty()) {
                "第 ${index + 1} 个代理缺少 name"
            }
            require(type.isNotEmpty()) {
                "第 ${index + 1} 个代理缺少 type"
            }
            require(names.add(name)) {
                "代理名称重复：$name"
            }
        }
        return entries
    }

    private fun entryFields(entry: List<String>): Map<String, String> = buildMap {
        entry.forEachIndexed { index, rawLine ->
            val candidate = if (index == 0) {
                rawLine.trim().removePrefix("-").trimStart()
            } else {
                rawLine.trim()
            }
            val match = fieldPattern.matchEntire(candidate) ?: return@forEachIndexed
            put(match.groupValues[1], match.groupValues[2])
        }
    }

    private fun renderSection(entries: List<List<String>>): List<String> {
        if (entries.isEmpty()) return listOf("proxies: []")
        return buildList {
            add("proxies:")
            entries.forEach { entry ->
                entry.forEach { line ->
                    if (line.isBlank()) {
                        add("")
                    } else {
                        add("  $line")
                    }
                }
            }
        }
    }

    private fun dedent(lines: List<String>): List<String> {
        val indent = lines
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .minOfOrNull { it.length - it.trimStart().length }
            ?: 0
        if (indent == 0) return lines
        return lines.map { line ->
            if (line.length >= indent) line.drop(indent) else ""
        }
    }

    private fun normalizedLines(yaml: String): List<String> =
        yaml.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList().apply {
            if (lastOrNull().isNullOrEmpty()) removeAt(lastIndex)
        }

    private fun findProxiesStart(lines: List<String>): Int? =
        lines.indexOfFirst { topLevelKey(it) == "proxies" }.takeIf { it >= 0 }

    private fun findSectionEnd(lines: List<String>, start: Int): Int =
        (start + 1 until lines.size).firstOrNull { topLevelKey(lines[it]) != null } ?: lines.size

    private fun topLevelKey(line: String): String? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        return line.substring(0, colon).trim().takeIf { it.matches(topLevelKeyPattern) }
    }
}
