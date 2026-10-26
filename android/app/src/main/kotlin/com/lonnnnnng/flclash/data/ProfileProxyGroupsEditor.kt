package com.lonnnnnng.flclash.data

/**
 * 作者 long：代理组分区编辑只改写自定义 Profile 的顶层 `proxy-groups` 列表，
 * 保留代理节点、规则、DNS 和其他未编辑字段，避免局部调整破坏整份配置。
 */
object ProfileProxyGroupsEditor {
    private val fieldPattern = Regex("^([A-Za-z0-9_-]+)\\s*:\\s*(.*)$")
    private val topLevelKeyPattern = Regex("^[A-Za-z0-9_-]+$")
    // 作者 long：Flutter 自定义编辑器与 Mihomo 可用的代理组类型保持一致，relay 虽可被核心解析但不能作为可编辑选项。
    private val supportedTypes = setOf("select", "url-test", "fallback", "load-balance")

    fun extractGroups(yaml: String): String {
        val lines = normalizedLines(yaml)
        val start = findGroupsStart(lines) ?: return ""
        if (lines[start].substringAfter(':').trim() == "[]") return ""
        val end = findSectionEnd(lines, start)
        val contentEnd = end - trailingBoundaryLines(lines, start, end)
        return lines
            .subList(start + 1, contentEnd)
            .joinToString("\n") { line ->
                when {
                    line.startsWith("  ") -> line.removePrefix("  ")
                    line.isBlank() -> ""
                    else -> line.trim()
                }
            }
            .trim('\n')
    }

    fun replaceGroups(yaml: String, editorText: String): String {
        val entries = parseEntries(editorText)
        val lines = normalizedLines(yaml)
        val start = findGroupsStart(lines)
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
        val contentEnd = end - trailingBoundaryLines(lines, start, end)
        val trailing = lines.subList(contentEnd, end)
        return buildList {
            addAll(lines.subList(0, start))
            addAll(rendered)
            addAll(trailing)
            addAll(lines.subList(end, lines.size))
        }.joinToString("\n").trimEnd() + "\n"
    }

    private fun parseEntries(editorText: String): List<List<String>> {
        val sourceLines = normalizedLines(editorText)
        val firstContentIndex = sourceLines.indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (firstContentIndex < 0) return emptyList()

        val firstContent = sourceLines[firstContentIndex].trim()
        val body = when {
            firstContent == "proxy-groups: []" -> return emptyList()
            firstContent == "proxy-groups:" -> sourceLines.drop(firstContentIndex + 1)
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
                    "第 ${index + 1} 行代理组条目格式无效"
                }
                val entry = mutableListOf(line.trimEnd())
                current = entry
                entries += entry
                return@forEachIndexed
            }
            val entry = requireNotNull(current) {
                "第 ${index + 1} 行必须从代理组条目开始"
            }
            require(line.length - trimmed.length >= 2) {
                "第 ${index + 1} 行代理组字段必须缩进"
            }
            entry.add(line.trimEnd())
        }

        val names = mutableSetOf<String>()
        entries.forEachIndexed { index, entry ->
            val fields = entryFields(entry)
            val name = scalarValue(fields["name"].orEmpty())
            val type = scalarValue(fields["type"].orEmpty()).lowercase()
            require(name.isNotEmpty()) {
                "第 ${index + 1} 个代理组缺少 name"
            }
            require(type in supportedTypes) {
                "第 ${index + 1} 个代理组 type 不受支持：${scalarValue(fields["type"].orEmpty())}"
            }
            require(names.add(name)) {
                "代理组名称重复：$name"
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

    private fun scalarValue(raw: String): String {
        val value = stripInlineComment(raw).trim()
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }

    private fun stripInlineComment(raw: String): String {
        var quote: Char? = null
        raw.forEachIndexed { index, char ->
            when {
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote != null && char == quote && (index == 0 || raw[index - 1] != '\\') -> quote = null
                quote == null && char == '#' && (index == 0 || raw[index - 1].isWhitespace()) ->
                    return raw.substring(0, index)
            }
        }
        return raw
    }

    private fun renderSection(entries: List<List<String>>): List<String> {
        if (entries.isEmpty()) return listOf("proxy-groups: []")
        return buildList {
            add("proxy-groups:")
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

    private fun findGroupsStart(lines: List<String>): Int? =
        lines.indexOfFirst { topLevelKey(it) == "proxy-groups" }.takeIf { it >= 0 }

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
