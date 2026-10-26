package com.lonnnnnng.flclash.data

/**
 * 作者 long：规则分区只处理自定义 Profile 的顶层 `rules`，先复用原始 YAML 其余内容，
 * 避免高级编辑器为了保存几条规则而重建订阅配置并破坏用户未编辑的字段。
 */
object ProfileRulesEditor {
    private val ruleTypePattern = Regex("^[A-Z][A-Z0-9_-]*$")

    fun extractRules(yaml: String): String {
        val lines = normalizedLines(yaml)
        val start = findRulesStart(lines) ?: return ""
        val end = findSectionEnd(lines, start)
        return lines
            .subList(start + 1, end)
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    null
                } else {
                    trimmed.removePrefix("-").trimStart().takeIf { it.isNotEmpty() }
                }
            }
            .joinToString("\n")
    }

    fun replaceRules(yaml: String, editorText: String): String {
        val rules = parseRules(editorText)
        val lines = normalizedLines(yaml)
        val start = findRulesStart(lines)
        val rendered = buildList {
            add("rules:")
            rules.forEach { add("  - $it") }
        }

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

    private fun parseRules(editorText: String): List<String> {
        return editorText.lineSequence().mapIndexedNotNull { index, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapIndexedNotNull null
            val rule = trimmed.removePrefix("-").trimStart()
            require(!rule.startsWith("SUB-RULE,", ignoreCase = true)) {
                "第 ${index + 1} 行不支持 SUB-RULE，请展开为具体规则"
            }
            val fields = rule.split(',')
            require(fields.size >= 2 && fields[0].trim().matches(ruleTypePattern)) {
                "第 ${index + 1} 行规则格式无效，应包含匹配类型和动作"
            }
            require(fields.drop(1).any { it.trim().isNotEmpty() }) {
                "第 ${index + 1} 行缺少规则动作"
            }
            rule
        }.toList()
    }

    private fun normalizedLines(yaml: String): MutableList<String> =
        yaml.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList().apply {
            if (lastOrNull().isNullOrEmpty()) removeAt(lastIndex)
        }

    private fun findRulesStart(lines: List<String>): Int? =
        lines.indexOfFirst { topLevelKey(it) == "rules" }.takeIf { it >= 0 }

    private fun findSectionEnd(lines: List<String>, start: Int): Int =
        (start + 1 until lines.size).firstOrNull { topLevelKey(lines[it]) != null } ?: lines.size

    private fun topLevelKey(line: String): String? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        return line.substring(0, colon).trim().takeIf { it.matches(Regex("^[A-Za-z0-9_-]+$")) }
    }
}
