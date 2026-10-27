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
