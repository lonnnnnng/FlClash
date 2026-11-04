package com.lonnnnnng.flclash.data

/**
 * 作者 long：代理分区编辑只改写自定义 Profile 的顶层 `proxies` 列表，
 * 保留端口、DNS、规则和其他未编辑字段，避免局部编辑重排整份配置。
 */
object ProfileProxiesEditor {
    private val topLevelKeyPattern = Regex("^[A-Za-z0-9_-]+$")

    fun extractProxies(yaml: String): String {
        val lines = normalizedLines(yaml)
        val start = findProxiesStart(lines) ?: return ""
        if (lines[start].substringAfter(':').trim() == "[]") return ""
        val end = findSectionEnd(lines, start)
        // 作者 long：顶层flow列表的首行也含节点；漏掉冒号后的值会让未修改保存误清空节点。
        val inline = lines[start].substringAfter(':').trimStart()
        val body = dedent(lines.subList(start + 1, end))
        return ((if (inline.isNotEmpty()) listOf(inline) else emptyList()) + body).joinToString("\n").trim('\n')
    }

    fun replaceProxies(yaml: String, editorText: String): String {
        val entries = editorLines(editorText)
        val lines = normalizedLines(yaml)
        val start = findProxiesStart(lines)
        val rendered = renderSection(entries)

        val replaced = if (start == null) {
            val prefix = lines.joinToString("\n").trimEnd()
            buildString {
                if (prefix.isNotEmpty()) {
                    append(prefix)
                    append("\n")
                }
                append(rendered.joinToString("\n"))
                append("\n")
            }
        } else {
            val end = findSectionEnd(lines, start)
            buildList {
                addAll(lines.subList(0, start))
                addAll(rendered)
                addAll(lines.subList(end, lines.size))
            }.joinToString("\n").trimEnd() + "\n"
        }
        validateProxies(replaced)
        return replaced
    }

    private fun editorLines(editorText: String): List<String> {
        val sourceLines = normalizedLines(editorText)
        val firstContentIndex = sourceLines.indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (firstContentIndex < 0) return emptyList()
        val firstContent = sourceLines[firstContentIndex].trim()
        val body = if (firstContent.startsWith("proxies:")) {
            val inline = firstContent.substringAfter(':').trimStart()
            // 作者 long：包装行注释不参与列表缩进计算，否则粘贴带注释的proxies包装会破坏节点结构。
            dedent(sourceLines.take(firstContentIndex)) +
                (if (inline.isNotEmpty()) listOf(inline) else emptyList()) +
                dedent(sourceLines.drop(firstContentIndex + 1))
        } else sourceLines
        val lines = dedent(body)
        val content = lines.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        return if (content.isEmpty() || content == listOf("[]")) emptyList() else lines
    }

    private fun validateProxies(yaml: String) {
        // 作者 long：Flutter按解析后的节点定义读取name/type；文本行扫描会误拒绝行内映射，也会把嵌套headers字段当成节点字段。
        // 在完整替换结果中校验，才能解析节点引用的其他分区锚点，同时保留用户分区原文、注释和未编辑配置。
        val root = try { ProxyGroupDrafts.root(yaml) }
        catch (error: Exception) { throw IllegalArgumentException("代理 YAML 格式无效", error) }
        val values = root.get("proxies")
        require(values != null && values.isJsonArray) { "代理分区必须是列表" }
        val names = mutableSetOf<String>()
        values.asJsonArray.forEachIndexed { index, value ->
            require(value.isJsonObject) { "第 ${index + 1} 个代理必须是对象" }
            val fields = value.asJsonObject
            val name = fields.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()
            val type = fields.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()
            require(name.isNotEmpty()) { "第 ${index + 1} 个代理缺少 name" }
            require(type.isNotEmpty()) { "第 ${index + 1} 个代理缺少 type" }
            require(names.add(name)) { "代理名称重复：$name" }
        }
    }

    private fun renderSection(lines: List<String>): List<String> =
        if (lines.isEmpty()) listOf("proxies: []")
        else listOf("proxies:") + lines.map { if (it.isBlank()) "" else "  $it" }

    private fun dedent(lines: List<String>): List<String> {
        val indent = lines
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .minOfOrNull { it.length - it.trimStart().length }
            ?: 0
        if (indent == 0) return lines
        return lines.map { line ->
            line.drop(minOf(indent, line.length - line.trimStart().length))
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
