package com.lonnnnnng.flclash.service

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 作者 long：脚本边界测试不依赖 Android WebView，先验证 YAML/JSON 契约、文件边界和失败闭环，
 * 确保真正的 JavaScript 引擎只负责执行而不会改变核心配置应用语义。
 */
class ProfileScriptTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test fun yamlErrorLocationUsesUtf16OffsetForEmojiAndCrLfWithoutExposingSource() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            val source = "name: \"😀中文\"${newline}rules: [ok, }"
            val failure = runCatching { YamlConfigCodec.toJson(source) }.exceptionOrNull()!!
            val location = requireNotNull(YamlConfigCodec.errorLocation(source, IllegalArgumentException("wrapper", failure)))
            assertEquals(2, location.line)
            assertEquals(13, location.column)
            assertEquals(source.indexOf('}'), location.offset)
            assertEquals('}', source[location.offset])
            assertFalse(location.toString().contains("中文"))
        }
    }

    @Test fun yamlErrorLocationSupportsEndOfDocumentAndLeavesUnmarkedErrorsUnlocated() {
        val source = "name: 😀\nrules: [bad"
        val failure = runCatching { YamlConfigCodec.toJson(source) }.exceptionOrNull()!!
        val location = requireNotNull(YamlConfigCodec.errorLocation(source, failure))
        assertEquals(source.length, location.offset)
        assertEquals(2, location.line)
        assertEquals(null, YamlConfigCodec.errorLocation("[]", IllegalArgumentException("not a mapping")))
    }

    @Test
    fun editorCanRetainExplicitNullWithoutChangingDefaultScriptInput() {
        val yaml = "settings: {server: null, enable: false}\n"
        val defaults = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject.getAsJsonObject("settings")
        val editor = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(yaml, preserveNulls = true)).asJsonObject.getAsJsonObject("settings")
        assertFalse(defaults.has("server"))
        assertTrue(editor["server"].isJsonNull)
        assertFalse(editor["enable"].asBoolean)
    }

    @Test
    fun yamlCodecAddsProviderMapAndRoundTripsNestedConfig() {
        val json = YamlConfigCodec.toJson(
            """
                mixed-port: 7890
                proxies:
                  - name: direct
                    type: direct
                dns:
                  enable: true
            """.trimIndent(),
        )

        assertTrue(json.contains("\"proxy-providers\":{}"))
        val yaml = YamlConfigCodec.toYaml(
            """
                {"mixed-port":7891,"proxies":[{"name":"direct","type":"direct"}],"enabled":true}
            """.trimIndent(),
        )

        assertTrue(yaml.contains("mixed-port: 7891"))
        assertTrue(yaml.contains("enabled: true"))
        assertTrue(yaml.contains("- name: direct"))
    }

    @Test
    fun yamlCodecPreservesSupplementaryCharactersAtReaderBufferBoundaries() {
        // 作者 long：首轮和后续轮次的满缓冲区都可能截断节点名中的 emoji，必须覆盖原文与序列化后再次解析。
        for (boundary in listOf(1023, 1024, 2046, 2047, 2048, 3071)) {
            val label = "x".repeat(boundary - "name: ".length) + "\uD83C\uDDE8\uD83C\uDDF3"
            val parsed = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson("name: $label\n")).asJsonObject
            assertEquals(label, parsed["name"].asString)
            val roundTrip = com.google.gson.JsonParser.parseString(YamlConfigCodec.toJson(YamlConfigCodec.toYaml(parsed.toString()))).asJsonObject
            assertEquals(label, roundTrip["name"].asString)
        }
    }

    @Test
    fun applierPassesJsonToEvaluatorAndReturnsOnlyEvaluatorConfig() = runBlocking {
        val scriptFile = temporaryFolder.newFile("profile.js").apply {
            writeText("function main(config) { return config }")
        }
        val evaluator = RecordingEvaluator(
            result = "{\"mixed-port\":7891,\"proxy-providers\":{}}",
        )
        val result = ProfileScriptApplier(evaluator).apply(
            yaml = "mixed-port: 7890\n",
            scriptPath = scriptFile.path,
        )

        assertTrue(evaluator.receivedScript.contains("function main"))
        assertTrue(evaluator.receivedConfig.contains("\"proxy-providers\":{}"))
        assertTrue(result.contains("mixed-port: 7891"))
        assertFalse(result.contains("mixed-port: 7890"))
    }

    @Test
    fun missingScriptFileFailsClosed() = runBlocking {
        val evaluator = RecordingEvaluator("{}")

        val error = runCatching {
            ProfileScriptApplier(evaluator).apply("mixed-port: 7890\n", "/missing/script.js")
        }.exceptionOrNull()

        assertEquals("绑定脚本正文不存在", error?.message)
        assertFalse(evaluator.called)
    }

    @Test
    fun nonObjectYamlAndNonObjectScriptResultAreRejected() = runBlocking {
        val rootError = runCatching {
            YamlConfigCodec.toJson("- one\n- two\n")
        }.exceptionOrNull()
        assertEquals("脚本配置必须是 YAML 对象", rootError?.message)

        val scriptFile = temporaryFolder.newFile("invalid.js").apply { writeText("main") }
        val resultError = runCatching {
            ProfileScriptApplier(RecordingEvaluator("[]")).apply(
                "mixed-port: 7890\n",
                scriptFile.path,
            )
        }.exceptionOrNull()
        assertEquals("脚本必须返回配置对象", resultError?.message)
    }

    private class RecordingEvaluator(
        private val result: String,
    ) : ProfileScriptEvaluator {
        var called = false
        var receivedScript = ""
        var receivedConfig = ""

        override suspend fun evaluate(script: String, configJson: String): String {
            called = true
            receivedScript = script
            receivedConfig = configJson
            return result
        }
    }
}
