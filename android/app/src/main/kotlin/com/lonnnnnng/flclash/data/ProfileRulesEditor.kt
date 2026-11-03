package com.lonnnnnng.flclash.data

/**
 * 作者 long：规则分区只处理自定义 Profile 的顶层 `rules`，先复用原始 YAML 其余内容，
 * 避免高级编辑器为了保存几条规则而重建订阅配置并破坏用户未编辑的字段。
 */
object ProfileRulesEditor {
    fun extractRules(yaml: String): String = CustomRules.fromSource(yaml).joinToString("\n", transform = CustomRules::scalar)

    fun replaceRules(yaml: String, editorText: String): String {
        val rules = CustomRules.decode(editorText, validate = false)
        CustomRules.validateAll(rules, unchangedValues = CustomRules.fromSource(yaml).toSet())
        val lines = normalizedLines(yaml)
        val start = findRulesStart(lines)
        val rendered = buildList {
            if (rules.isEmpty()) add("rules: []") else {
                add("rules:")
                rules.forEach { add("  - ${CustomRules.scalar(it.value)}") }
            }
        }

        // 作者 long：flow 根对象或整体缩进无法安全截取分区时，按语义替换，不能追加第二份 rules 破坏配置。
        val first = lines.firstOrNull { it.isNotBlank() && !it.trimStart().startsWith('#') && it.trim() != "---" }
        if (yaml.isNotBlank() && ((start == null && ProxyGroupDrafts.root(yaml).has("rules")) ||
                first?.startsWith(' ') == true || first?.startsWith('{') == true)) {
            val root = ProxyGroupDrafts.root(yaml)
            root.add("rules", com.google.gson.JsonArray().apply { rules.forEach { add(it.value) } })
            return com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(root.toString())
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

        var end = findSectionEnd(lines, start)
        // 作者 long：分区之间的空行和顶层说明属于后续内容，不能随规则删除而丢失。
        while (end > start + 1 && (lines[end - 1].isBlank() || lines[end - 1].startsWith('#'))) end--
        return buildList {
            addAll(lines.subList(0, start))
            addAll(rendered)
            addAll(lines.subList(end, lines.size))
        }.joinToString("\n").trimEnd() + "\n"
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
        if (line.isBlank() || line.first().isWhitespace() || line.startsWith('#') || line.startsWith('-') || line.startsWith('{')) return null
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        return line.substring(0, colon).trim().trim('\'', '"').takeIf { it.matches(Regex("^[A-Za-z0-9_-]+$")) }
    }
}
