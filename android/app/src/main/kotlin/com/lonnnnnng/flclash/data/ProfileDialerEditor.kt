package com.lonnnnnng.flclash.data

/**
 * 作者 long：Dialers 编辑使用“代理名 -> 目标名”的稳定文本格式，数据库只保存映射，
 * 原始 Profile YAML 仍由文件编辑器负责；核心启动时再把映射合并到暂存文件。
 */
object ProfileDialerEditor {
    fun parseDialers(text: String): Map<String, String> = buildMap {
        text.replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .forEachIndexed { index, line ->
                val separator = line.indexOf("->").takeIf { it >= 0 } ?: line.indexOf('=')
                require(separator > 0) { "第 ${index + 1} 行应使用“代理名 -> 目标名”格式" }
                val width = if (line.substring(separator).startsWith("->")) 2 else 1
                val proxy = parseScalar(line.substring(0, separator), index + 1, "代理")
                val target = parseScalar(line.substring(separator + width), index + 1, "目标")
                require(proxy.isNotEmpty() && target.isNotEmpty()) { "第 ${index + 1} 行代理和目标不能为空" }
                require(!containsKey(proxy)) { "Dialer 代理重复：$proxy" }
                put(proxy, target)
            }
    }

    fun renderDialers(dialers: Map<String, String>): String =
        dialers.entries.joinToString("\n") { (proxy, target) -> "$proxy -> $target" }

    /** 从原始 YAML 读取已有 dialer-proxy，用于第一次打开编辑器时显示有效值。 */
    fun extractDialers(yaml: String): Map<String, String> {
        val lines = normalizedLines(yaml)
        val start = lines.indexOfFirst { topLevelKey(it) == "proxies" }
        if (start < 0) return emptyMap()
        val end = sectionEnd(lines, start)
        val entryStarts = (start + 1 until end)
            .filter { leadingSpaces(lines[it]) == 2 && lines[it].trimStart().startsWith("-") }
        return buildMap {
            entryStarts.forEachIndexed { index, entryStart ->
                val entryEnd = entryStarts.getOrNull(index + 1) ?: end
                var name: String? = null
                var target: String? = null
                for (lineIndex in entryStart until entryEnd) {
                    val line = lines[lineIndex]
                    val candidate = line.trim().removePrefix("-").trimStart()
                    when {
                        candidate.startsWith("name:") -> name = scalar(candidate.substringAfter(':'))
                        leadingSpaces(line) >= 4 && nestedKey(line) == "name" -> name = scalar(line.substringAfter(':'))
                        leadingSpaces(line) >= 4 && nestedKey(line) == "dialer-proxy" -> {
                            target = scalar(line.substringAfter(':'))
                        }
                    }
                }
                if (!name.isNullOrBlank() && !target.isNullOrBlank()) put(name!!, target!!)
            }
        }
    }

    private fun normalizedLines(yaml: String): List<String> =
        yaml.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    private fun sectionEnd(lines: List<String>, start: Int): Int =
        (start + 1 until lines.size).firstOrNull { topLevelKey(lines[it]) != null } ?: lines.size

    private fun topLevelKey(line: String): String? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        val colon = line.indexOf(':')
        return line.substring(0, colon.coerceAtLeast(0)).trim()
            .takeIf { colon > 0 && it.matches(Regex("[A-Za-z0-9_-]+")) }
    }

    private fun nestedKey(line: String): String? {
        if (leadingSpaces(line) < 4) return null
        val colon = line.indexOf(':')
        return line.substring(0, colon.coerceAtLeast(0)).trim()
            .takeIf { colon > 0 && it.matches(Regex("[A-Za-z0-9_-]+")) }
    }

    private fun scalar(raw: String): String {
        val value = stripInlineComment(raw.trim())
        return if (value.length >= 2 && value.first() == value.last() &&
            (value.first() == '\'' || value.first() == '"')
        ) value.substring(1, value.length - 1) else value
    }

    private fun parseScalar(raw: String, line: Int, label: String): String {
        val value = scalar(raw)
        require(value.isNotEmpty()) { "第 $line 行${label}不能为空" }
        return value
    }

    private fun stripInlineComment(value: String): String {
        var quote: Char? = null
        value.forEachIndexed { index, char ->
            when {
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote != null && char == quote && (index == 0 || value[index - 1] != '\\') -> quote = null
                quote == null && char == '#' && (index == 0 || value[index - 1].isWhitespace()) ->
                    return value.substring(0, index).trim()
            }
        }
        return value
    }

    private fun leadingSpaces(line: String): Int = line.takeWhile { it == ' ' }.length
}
