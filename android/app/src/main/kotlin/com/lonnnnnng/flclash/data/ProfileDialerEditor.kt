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
        // 作者 long：全局投影和手写配置都可能使用 flow 对象或无缩进列表，按 YAML 语义读取才能展示实际链式设置。
        val proxies = ProxyGroupDrafts.root(yaml).get("proxies")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyMap()
        return buildMap {
            proxies.forEach { entry ->
                val node = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val name = node.get("name")?.asString
                val target = node.get("dialer-proxy")?.asString
                if (!name.isNullOrBlank() && !target.isNullOrBlank()) put(name, target)
            }
        }
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
}
