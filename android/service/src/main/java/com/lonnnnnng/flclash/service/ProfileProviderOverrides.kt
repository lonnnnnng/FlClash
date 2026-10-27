package com.lonnnnnng.flclash.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 作者 long：这些字段属于应用对代理 Provider 的局部覆盖，不改写用户原始配置文件，
 * 启动核心时才合并到暂存配置，才能让“清除覆盖”继续保留用户手写的 Provider 定义。
 */
data class ProxyProviderOverride(
    val url: String? = null,
    val interval: Int? = null,
    val timeout: Int? = null,
    val lazy: Boolean? = null,
    val expectedStatus: String? = null,
    val additionalPrefix: String? = null,
    val additionalSuffix: String? = null,
    val udp: Boolean? = null,
    val skipCertVerify: Boolean? = null,
    val ipVersion: String? = null,
) {
    val isEmpty: Boolean
        get() = url == null && interval == null && timeout == null && lazy == null &&
            expectedStatus == null && additionalPrefix == null && additionalSuffix == null &&
            udp == null && skipCertVerify == null && ipVersion == null

    fun toJson(): JsonObject = JsonObject().apply {
        url?.let { addProperty("url", it) }
        interval?.let { addProperty("interval", it) }
        timeout?.let { addProperty("timeout", it) }
        lazy?.let { addProperty("lazy", it) }
        expectedStatus?.let { addProperty("expected-status", it) }
        additionalPrefix?.let { addProperty("additional-prefix", it) }
        additionalSuffix?.let { addProperty("additional-suffix", it) }
        udp?.let { addProperty("udp", it) }
        skipCertVerify?.let { addProperty("skip-cert-verify", it) }
        ipVersion?.let { addProperty("ip-version", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): ProxyProviderOverride = ProxyProviderOverride(
            url = json.optStringOrNull("url"),
            interval = json.optPositiveInt("interval"),
            timeout = json.optPositiveInt("timeout"),
            lazy = json.optBooleanOrNull("lazy"),
            expectedStatus = json.optStringOrNull("expected-status"),
            additionalPrefix = json.optStringOrNull("additional-prefix"),
            additionalSuffix = json.optStringOrNull("additional-suffix"),
            udp = json.optBooleanOrNull("udp"),
            skipCertVerify = json.optBooleanOrNull("skip-cert-verify"),
            ipVersion = json.optStringOrNull("ip-version")
                ?.takeIf { it in SUPPORTED_IP_VERSIONS },
        )

        private val SUPPORTED_IP_VERSIONS = setOf("ipv4", "ipv6", "dual")
    }
}

/**
 * 作者 long：DNS 覆盖保存的是用户明确改过的字段，null 代表继续沿用 Profile 原始配置；
 * 这样清除某个覆盖时不会把 Mihomo 的默认值误写进暂存文件。
 */
data class ProfileDnsOverrides(
    val enable: Boolean? = null,
    val listen: String? = null,
    val ipv6: Boolean? = null,
    val respectRules: Boolean? = null,
    val preferH3: Boolean? = null,
    val useHosts: Boolean? = null,
    val useSystemHosts: Boolean? = null,
    val enhancedMode: String? = null,
    val fakeIpRange: String? = null,
    val nameserver: List<String>? = null,
    val fallback: List<String>? = null,
    val defaultNameserver: List<String>? = null,
    val proxyServerNameserver: List<String>? = null,
    val directNameserver: List<String>? = null,
    val fakeIpFilter: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = enable == null && listen == null && ipv6 == null && respectRules == null &&
            preferH3 == null && useHosts == null && useSystemHosts == null &&
            enhancedMode == null && fakeIpRange == null && nameserver == null &&
            fallback == null && defaultNameserver == null && proxyServerNameserver == null &&
            directNameserver == null && fakeIpFilter == null

    fun toJson(): JsonObject = JsonObject().apply {
        enable?.let { addProperty("enable", it) }
        listen?.let { addProperty("listen", it) }
        ipv6?.let { addProperty("ipv6", it) }
        respectRules?.let { addProperty("respect-rules", it) }
        preferH3?.let { addProperty("prefer-h3", it) }
        useHosts?.let { addProperty("use-hosts", it) }
        useSystemHosts?.let { addProperty("use-system-hosts", it) }
        enhancedMode?.let { addProperty("enhanced-mode", it) }
        fakeIpRange?.let { addProperty("fake-ip-range", it) }
        nameserver?.let { addStringArray("nameserver", it) }
        fallback?.let { addStringArray("fallback", it) }
        defaultNameserver?.let { addStringArray("default-nameserver", it) }
        proxyServerNameserver?.let { addStringArray("proxy-server-nameserver", it) }
        directNameserver?.let { addStringArray("direct-nameserver", it) }
        fakeIpFilter?.let { addStringArray("fake-ip-filter", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): ProfileDnsOverrides = ProfileDnsOverrides(
            enable = json.optBooleanOrNull("enable"),
            listen = json.optStringOrNull("listen"),
            ipv6 = json.optBooleanOrNull("ipv6"),
            respectRules = json.optBooleanOrNull("respect-rules"),
            preferH3 = json.optBooleanOrNull("prefer-h3"),
            useHosts = json.optBooleanOrNull("use-hosts"),
            useSystemHosts = json.optBooleanOrNull("use-system-hosts"),
            enhancedMode = json.optStringOrNull("enhanced-mode"),
            fakeIpRange = json.optStringOrNull("fake-ip-range"),
            nameserver = json.optStringListOrNull("nameserver"),
            fallback = json.optStringListOrNull("fallback"),
            defaultNameserver = json.optStringListOrNull("default-nameserver"),
            proxyServerNameserver = json.optStringListOrNull("proxy-server-nameserver"),
            directNameserver = json.optStringListOrNull("direct-nameserver"),
            fakeIpFilter = json.optStringListOrNull("fake-ip-filter"),
        )
    }
}

data class ProfileProviderOverrides(
    val proxyProviders: Map<String, ProxyProviderOverride> = emptyMap(),
    val dns: ProfileDnsOverrides = ProfileDnsOverrides(),
    /** null 表示未设置；空 map 表示用户明确清空全部 Dialers。 */
    val dialers: Map<String, String>? = null,
) {
    fun toJsonString(): String = JsonObject().apply {
        add("proxy-providers", JsonObject().apply {
            proxyProviders.forEach { (name, override) ->
                if (!override.isEmpty) add(name, override.toJson())
            }
        })
        if (!dns.isEmpty) add("dns", dns.toJson())
        dialers?.let { values ->
            add("dialers", JsonObject().apply {
                values.forEach { (proxy, target) ->
                    if (proxy.isNotBlank() && target.isNotBlank()) addProperty(proxy, target)
                }
            })
        }
    }.let(Gson()::toJson)

    companion object {
        fun fromJsonString(raw: String?): ProfileProviderOverrides {
            if (raw.isNullOrBlank()) return ProfileProviderOverrides()
            return runCatching {
                val root = JsonParser.parseString(raw).asJsonObject
                val providers = root.getAsJsonObject("proxy-providers")
                val values = buildMap {
                    providers?.entrySet()?.forEach { (name, element) ->
                        val value = element.takeIf { it.isJsonObject }?.asJsonObject
                            ?: return@forEach
                        val override = ProxyProviderOverride.fromJson(value)
                        if (!override.isEmpty) put(name, override)
                    }
                }
                val dns = root.getAsJsonObject("dns")
                    ?.let(ProfileDnsOverrides::fromJson)
                    ?: ProfileDnsOverrides()
                ProfileProviderOverrides(values, dns, root.optStringMapOrNull("dialers"))
            }.getOrDefault(ProfileProviderOverrides())
        }
    }
}

/**
 * 作者 long：Provider 覆盖只作用于核心暂存文件；原始 Profile 保持可读、可回滚，
 * 并且同一份配置在停止核心后仍能继续被编辑器按原文展示。
 */
object ProfileProviderOverrideApplier {
    fun apply(yaml: String, overrides: ProfileProviderOverrides): String {
        var updated = yaml
        if (overrides.proxyProviders.isNotEmpty()) {
            updated = applyProxyProviders(updated, overrides.proxyProviders)
        }
        updated = ProfileDialerOverrideApplier.apply(updated, overrides.dialers)
        return ProfileDnsOverrideApplier.apply(updated, overrides.dns)
    }

    private fun applyProxyProviders(
        yaml: String,
        proxyProviders: Map<String, ProxyProviderOverride>,
    ): String {
        val trailingNewline = yaml.endsWith('\n')
        val lines = yaml.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .toMutableList()
        if (trailingNewline && lines.lastOrNull().isNullOrEmpty()) lines.removeAt(lines.lastIndex)
        val sectionStart = lines.indexOfFirst { topLevelKey(it) == "proxy-providers" }
        if (sectionStart < 0) return yaml
        val sectionEnd = (sectionStart + 1 until lines.size)
            .firstOrNull { topLevelKey(lines[it]) != null }
            ?: lines.size

        // 从后往前替换，避免前一个 Provider 的行数变化影响后续索引。
        proxyProviders.entries
            .mapNotNull { (name, override) ->
                findProviderEntry(lines, sectionStart + 1, sectionEnd, name)
                    ?.let { it to override }
            }
            .sortedByDescending { it.first.first }
            .forEach { (entry, override) ->
                val updated = applyToEntry(lines.subList(entry.first, entry.second), override)
                lines.subList(entry.first, entry.second).clear()
                lines.addAll(entry.first, updated)
            }
        return lines.joinToString("\n") + if (trailingNewline) "\n" else ""
    }

    private fun applyToEntry(
        entryLines: List<String>,
        override: ProxyProviderOverride,
    ): List<String> {
        val result = entryLines.toMutableList()
        applyNestedBlock(result, "health-check", healthCheckFields(override))
        applyNestedBlock(result, "override", providerOverrideFields(override))
        return result
    }

    private fun healthCheckFields(override: ProxyProviderOverride): LinkedHashMap<String, String> =
        linkedMapOf<String, String>().apply {
            if (override.url != null || override.interval != null || override.timeout != null ||
                override.lazy != null || override.expectedStatus != null
            ) {
                put("enable", "true")
                override.url?.let { put("url", quoteIfNeeded(it)) }
                override.interval?.let { put("interval", it.toString()) }
                override.timeout?.let { put("timeout", it.toString()) }
                override.lazy?.let { put("lazy", it.toString()) }
                override.expectedStatus?.let { put("expected-status", quoteIfNeeded(it)) }
            }
        }

    private fun providerOverrideFields(override: ProxyProviderOverride): LinkedHashMap<String, String> =
        linkedMapOf<String, String>().apply {
            override.additionalPrefix?.let { put("additional-prefix", quoteIfNeeded(it)) }
            override.additionalSuffix?.let { put("additional-suffix", quoteIfNeeded(it)) }
            override.udp?.let { put("udp", it.toString()) }
            override.skipCertVerify?.let { put("skip-cert-verify", it.toString()) }
            override.ipVersion?.let { put("ip-version", it) }
        }

    private fun applyNestedBlock(
        entry: MutableList<String>,
        blockName: String,
        fields: LinkedHashMap<String, String>,
    ) {
        if (fields.isEmpty()) return
        val blockStart = entry.indexOfFirst { nestedKey(it) == blockName }
        if (blockStart < 0) {
            entry.addAll(renderNestedBlock(blockName, fields))
            return
        }
        val blockEnd = (blockStart + 1 until entry.size)
            .firstOrNull { index ->
                val line = entry[index]
                val indent = leadingSpaces(line)
                line.isNotBlank() && indent <= 4 && nestedKey(line) != null
            }
            ?: entry.size
        val existing = entry.subList(blockStart, blockEnd).toMutableList()
        fields.forEach { (key, value) ->
            val fieldIndex = existing.indexOfFirst { nestedFieldKey(it) == key }
            if (fieldIndex >= 0) {
                existing[fieldIndex] = "      $key: $value"
            } else {
                existing.add("      $key: $value")
            }
        }
        entry.subList(blockStart, blockEnd).clear()
        entry.addAll(blockStart, existing)
    }

    private fun renderNestedBlock(
        name: String,
        fields: LinkedHashMap<String, String>,
    ): List<String> = buildList {
        add("    $name:")
        fields.forEach { (key, value) -> add("      $key: $value") }
    }

    private fun findProviderEntry(
        lines: List<String>,
        from: Int,
        to: Int,
        name: String,
    ): Pair<Int, Int>? {
        val start = (from until to).firstOrNull { index ->
            leadingSpaces(lines[index]) == 2 && normalizeKey(lines[index]) == name
        } ?: return null
        val end = (start + 1 until to).firstOrNull { index ->
            leadingSpaces(lines[index]) == 2 && normalizeKey(lines[index]).isNotEmpty()
        } ?: to
        return start to end
    }

    private fun topLevelKey(line: String): String? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        val colon = line.indexOf(':')
        return line.substring(0, colon.coerceAtLeast(0)).trim()
            .takeIf { colon > 0 && it.matches(Regex("[A-Za-z0-9_-]+")) }
    }

    private fun nestedKey(line: String): String? {
        if (leadingSpaces(line) != 4) return null
        val colon = line.indexOf(':')
        return line.substring(0, colon.coerceAtLeast(0)).trim()
            .takeIf { colon > 0 && it.matches(Regex("[A-Za-z0-9_-]+")) }
    }

    private fun nestedFieldKey(line: String): String? {
        if (leadingSpaces(line) != 6) return null
        val colon = line.indexOf(':')
        return line.substring(0, colon.coerceAtLeast(0)).trim()
            .takeIf { colon > 0 && it.matches(Regex("[A-Za-z0-9_-]+")) }
    }

    private fun normalizeKey(line: String): String {
        if (leadingSpaces(line) != 2) return ""
        val colon = line.indexOf(':')
        if (colon <= 2) return ""
        val key = line.substring(2, colon).trim()
        return key.removeSurrounding("'").removeSurrounding("\"")
            .takeIf { it.matches(Regex("[A-Za-z0-9_.-]+")) }
            .orEmpty()
    }

    private fun leadingSpaces(line: String): Int = line.takeWhile { it == ' ' }.length

    private fun quoteIfNeeded(value: String): String {
        if (value.isEmpty()) return "''"
        return if (value.any { it.isWhitespace() || it == '#' }) {
            "'${value.replace("'", "''")}'"
        } else {
            value
        }
    }
}

/**
 * 作者 long：Dialer 覆盖是一份按代理名称索引的完整映射；应用到核心暂存 YAML 时先清掉旧字段，
 * 再写入当前映射，允许用户通过保存空映射明确移除所有 dialer-proxy，而不改写原始 Profile。
 */
object ProfileDialerOverrideApplier {
    fun apply(yaml: String, dialers: Map<String, String>?): String {
        if (dialers == null) return yaml
        val lines = yaml.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n').toMutableList().apply {
                if (lastOrNull().isNullOrEmpty()) removeAt(lastIndex)
            }
        val sectionStart = lines.indexOfFirst { topLevelKey(it) == "proxies" }
        if (sectionStart < 0) {
            require(dialers.isEmpty()) { "配置缺少 proxies，无法应用 Dialers" }
            return yaml
        }
        val sectionEnd = findTopLevelEnd(lines, sectionStart)
        val entries = entryRanges(lines, sectionStart + 1, sectionEnd)
        val names = entries.mapNotNull { entryName(lines, it.first, it.second) }.toSet()
        require(dialers.keys.all { it in names }) {
            "Dialer 代理不存在：${dialers.keys.firstOrNull { it !in names }.orEmpty()}"
        }
        require(dialers.values.all { it.isNotBlank() }) { "Dialer 目标不能为空" }
        val groupNames = topLevelListNames(lines, "proxy-groups")
        val knownTargets = names + groupNames
        require(dialers.values.all { it in knownTargets }) {
            "Dialer 目标不存在：${dialers.values.firstOrNull { it !in knownTargets }.orEmpty()}"
        }
        require(!hasDialerLoop(dialers, groupMembers(lines))) { "Dialer 存在循环依赖" }

        entries.asReversed().forEach { (start, end) ->
            val name = entryName(lines, start, end)
            val kept = lines.subList(start, end).filterNot { nestedKey(it) == "dialer-proxy" }
            lines.subList(start, end).clear()
            lines.addAll(start, kept)
            val target = name?.let(dialers::get) ?: return@forEach
            val insertAt = start + kept.size
            lines.add(insertAt, "    dialer-proxy: ${yamlScalar(target)}")
        }
        return lines.joinToString("\n") + "\n"
    }

    private fun hasDialerLoop(
        dialers: Map<String, String>,
        groups: Map<String, List<String>>,
    ): Boolean {
        fun reaches(current: String, target: String, seen: Set<String>): Boolean {
            if (current == target) return true
            if (current in seen) return false
            val nextSeen = seen + current
            val next = buildList {
                dialers[current]?.let { add(it) }
                addAll(groups[current].orEmpty())
            }
            return next.any { reaches(it, target, nextSeen) }
        }
        return dialers.any { (proxy, target) -> proxy == target || reaches(target, proxy, emptySet()) }
    }

    private fun groupMembers(lines: List<String>): Map<String, List<String>> {
        val start = lines.indexOfFirst { topLevelKey(it) == "proxy-groups" }
        if (start < 0) return emptyMap()
        val end = findTopLevelEnd(lines, start)
        return entryRanges(lines, start + 1, end).mapNotNull { (entryStart, entryEnd) ->
            val name = entryName(lines, entryStart, entryEnd) ?: return@mapNotNull null
            val members = mutableListOf<String>()
            var list = false
            for (index in entryStart until entryEnd) {
                val key = nestedKey(lines[index])
                if (key == "proxies") {
                    list = true
                    val inline = lines[index].substringAfter(':').trim()
                    if (inline.startsWith("[")) {
                        members += inline.removePrefix("[").removeSuffix("]")
                            .split(',')
                            .map { yamlScalarValue(it) }
                            .filter(String::isNotEmpty)
                        list = false
                    }
                } else if (list && lines[index].trimStart().startsWith("-")) {
                    members += yamlScalarValue(lines[index].substringAfter('-').trim())
                } else if (list && lines[index].isNotBlank() && leadingSpaces(lines[index]) <= 4) {
                    list = false
                }
            }
            name to members
        }.toMap()
    }

    private fun topLevelListNames(lines: List<String>, section: String): Set<String> {
        val start = lines.indexOfFirst { topLevelKey(it) == section }
        if (start < 0) return emptySet()
        val end = findTopLevelEnd(lines, start)
        return entryRanges(lines, start + 1, end).mapNotNull { entryName(lines, it.first, it.second) }.toSet()
    }

    private fun entryRanges(lines: List<String>, from: Int, to: Int): List<Pair<Int, Int>> {
        val starts = (from until to).filter { leadingSpaces(lines[it]) == 2 && lines[it].trimStart().startsWith("-") }
        return starts.mapIndexed { index, start -> start to (starts.getOrNull(index + 1) ?: to) }
    }

    private fun entryName(lines: List<String>, start: Int, end: Int): String? {
        for (index in start until end) {
            val line = lines[index]
            val candidate = line.trim().removePrefix("-").trimStart()
            val key = when {
                candidate.startsWith("name:") -> candidate.substringAfter(':').trim()
                leadingSpaces(line) >= 4 && nestedKey(line) == "name" -> line.substringAfter(':').trim()
                else -> null
            }
            if (key != null) return yamlScalarValue(key)
        }
        return null
    }

    private fun findTopLevelEnd(lines: List<String>, start: Int): Int =
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

    private fun leadingSpaces(line: String): Int = line.takeWhile { it == ' ' }.length

    private fun yamlScalarValue(raw: String): String {
        val value = raw.trim()
        return if (value.length >= 2 && value.first() == value.last() &&
            (value.first() == '\'' || value.first() == '"')
        ) {
            value.substring(1, value.length - 1)
        } else value
    }

    private fun yamlScalar(value: String): String =
        if (value.any { it.isWhitespace() || it == '#' || it == ':' }) {
            "'${value.replace("'", "''")}'"
        } else value
}

/**
 * 作者 long：DNS 覆盖只修改暂存 YAML 的顶层 dns 区块，未知字段和原始配置文件保持不变；
 * 列表使用块序列写回，避免服务器 URL 中的冒号被误解析成 map。
 */
object ProfileDnsOverrideApplier {
    fun apply(yaml: String, overrides: ProfileDnsOverrides): String {
        if (overrides.isEmpty) return yaml
        val trailingNewline = yaml.endsWith('\n')
        val lines = normalizedLines(yaml)
        val fields = overrides.renderedFields()
        val sectionStart = lines.indexOfFirst { topLevelKey(it) == "dns" }
        if (sectionStart < 0) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += "dns:"
            fields.forEach { field -> lines.addAll(field.lines) }
            return lines.joinToString("\n") + "\n"
        }

        val sectionEnd = findTopLevelEnd(lines, sectionStart)
        if (lines[sectionStart].substringAfter(':').trim() == "{}") {
            val replacement = buildList {
                add("dns:")
                fields.forEach { addAll(it.lines) }
            }
            lines.subList(sectionStart, sectionEnd).clear()
            lines.addAll(sectionStart, replacement)
        } else {
            fields.forEach { field ->
                replaceField(lines, sectionStart, findTopLevelEnd(lines, sectionStart), field.key, field.lines)
            }
        }
        val result = lines.joinToString("\n")
        return result + if (trailingNewline || !result.endsWith('\n')) "\n" else ""
    }

    private data class RenderedField(val key: String, val lines: List<String>)

    private fun ProfileDnsOverrides.renderedFields(): List<RenderedField> = buildList {
        enable?.let { add(RenderedField("enable", listOf("  enable: $it"))) }
        listen?.let { add(RenderedField("listen", listOf("  listen: ${yamlScalar(it)}"))) }
        ipv6?.let { add(RenderedField("ipv6", listOf("  ipv6: $it"))) }
        respectRules?.let { add(RenderedField("respect-rules", listOf("  respect-rules: $it"))) }
        preferH3?.let { add(RenderedField("prefer-h3", listOf("  prefer-h3: $it"))) }
        useHosts?.let { add(RenderedField("use-hosts", listOf("  use-hosts: $it"))) }
        useSystemHosts?.let { add(RenderedField("use-system-hosts", listOf("  use-system-hosts: $it"))) }
        enhancedMode?.let { add(RenderedField("enhanced-mode", listOf("  enhanced-mode: ${yamlScalar(it)}"))) }
        fakeIpRange?.let { add(RenderedField("fake-ip-range", listOf("  fake-ip-range: ${yamlScalar(it)}"))) }
        nameserver?.let { add(RenderedField("nameserver", renderList("nameserver", it))) }
        fallback?.let { add(RenderedField("fallback", renderList("fallback", it))) }
        defaultNameserver?.let { add(RenderedField("default-nameserver", renderList("default-nameserver", it))) }
        proxyServerNameserver?.let { add(RenderedField("proxy-server-nameserver", renderList("proxy-server-nameserver", it))) }
        directNameserver?.let { add(RenderedField("direct-nameserver", renderList("direct-nameserver", it))) }
        fakeIpFilter?.let { add(RenderedField("fake-ip-filter", renderList("fake-ip-filter", it))) }
    }

    private fun renderList(key: String, values: List<String>): List<String> = buildList {
        if (values.isEmpty()) {
            add("  $key: []")
        } else {
            add("  $key:")
            values.forEach { add("    - ${yamlScalar(it)}") }
        }
    }

    private fun replaceField(
        lines: MutableList<String>,
        sectionStart: Int,
        sectionEnd: Int,
        key: String,
        replacement: List<String>,
    ) {
        val fieldStart = (sectionStart + 1 until sectionEnd).firstOrNull { index ->
            leadingSpaces(lines[index]) == 2 && nestedKey(lines[index]) == key
        }
        if (fieldStart == null) {
            lines.addAll(sectionEnd, replacement)
            return
        }
        val fieldEnd = (fieldStart + 1 until sectionEnd).firstOrNull { index ->
            leadingSpaces(lines[index]) == 2 && nestedKey(lines[index]) != null
        } ?: sectionEnd
        lines.subList(fieldStart, fieldEnd).clear()
        lines.addAll(fieldStart, replacement)
    }

    private fun normalizedLines(yaml: String): MutableList<String> =
        yaml.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList().apply {
            if (lastOrNull().isNullOrEmpty()) removeAt(lastIndex)
        }

    private fun findTopLevelEnd(lines: List<String>, start: Int): Int =
        (start + 1 until lines.size).firstOrNull { topLevelKey(lines[it]) != null } ?: lines.size

    private fun topLevelKey(line: String): String? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        val colon = line.indexOf(':')
        return line.substring(0, colon.coerceAtLeast(0)).trim()
            .takeIf { colon > 0 && it.matches(Regex("[A-Za-z0-9_-]+")) }
    }

    private fun nestedKey(line: String): String? {
        if (leadingSpaces(line) != 2) return null
        val colon = line.indexOf(':')
        return line.substring(0, colon.coerceAtLeast(0)).trim()
            .takeIf { colon > 0 && it.matches(Regex("[A-Za-z0-9_-]+")) }
    }

    private fun leadingSpaces(line: String): Int = line.takeWhile { it == ' ' }.length

    private fun yamlScalar(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "''"
        // 作者 long：DNS 服务器 URL 中的冒号是合法内容，保持与现有 Provider YAML 写法一致；
        // 只有空白或注释标记可能改变 YAML scalar 语义时才加引号。
        return if (trimmed.any { it.isWhitespace() || it == '#' }) {
            "'${trimmed.replace("'", "''")}'"
        } else {
            trimmed
        }
    }
}

private fun JsonObject.optStringOrNull(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.optPositiveInt(name: String): Int? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asInt?.takeIf { it > 0 }

private fun JsonObject.optBooleanOrNull(name: String): Boolean? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asBoolean

private fun JsonObject.optStringListOrNull(name: String): List<String>? =
    get(name)?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { it.takeIf { item -> item.isJsonPrimitive }?.asString?.trim() }
        ?.takeIf { it.isNotEmpty() }

private fun JsonObject.addStringArray(name: String, values: List<String>) {
    add(name, com.google.gson.JsonArray().apply {
        values.forEach { add(it) }
    })
}

private fun JsonObject.optStringMapOrNull(name: String): Map<String, String>? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.mapNotNull { (key, value) ->
        value.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }?.let { key to it }
    }?.toMap()
