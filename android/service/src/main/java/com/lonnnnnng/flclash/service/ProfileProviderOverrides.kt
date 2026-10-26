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

data class ProfileProviderOverrides(
    val proxyProviders: Map<String, ProxyProviderOverride> = emptyMap(),
) {
    fun toJsonString(): String = JsonObject().apply {
        add("proxy-providers", JsonObject().apply {
            proxyProviders.forEach { (name, override) ->
                if (!override.isEmpty) add(name, override.toJson())
            }
        })
    }.let(Gson()::toJson)

    companion object {
        fun fromJsonString(raw: String?): ProfileProviderOverrides {
            if (raw.isNullOrBlank()) return ProfileProviderOverrides()
            return runCatching {
                val providers = JsonParser.parseString(raw).asJsonObject
                    .getAsJsonObject("proxy-providers")
                    ?: return@runCatching ProfileProviderOverrides()
                val values = buildMap {
                    providers.entrySet().forEach { (name, element) ->
                        val value = element.takeIf { it.isJsonObject }?.asJsonObject
                            ?: return@forEach
                        val override = ProxyProviderOverride.fromJson(value)
                        if (!override.isEmpty) put(name, override)
                    }
                }
                ProfileProviderOverrides(values)
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
        if (overrides.proxyProviders.isEmpty()) return yaml
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
        overrides.proxyProviders.entries
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

private fun JsonObject.optStringOrNull(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.optPositiveInt(name: String): Int? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asInt?.takeIf { it > 0 }

private fun JsonObject.optBooleanOrNull(name: String): Boolean? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asBoolean
