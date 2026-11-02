package com.lonnnnnng.flclash.service

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.File
import java.io.StringReader
import java.math.BigDecimal
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * 作者 long：Profile 脚本接收的是 Flutter 侧同样的 JSON 配置对象，执行结果再回到 YAML，
 * 这样脚本只修改暂存配置，不改写用户保存的原始 Profile 文件。
 */
class ProfileScriptApplier(
    private val evaluator: ProfileScriptEvaluator,
) {
    suspend fun apply(yaml: String, scriptPath: String?): String {
        if (scriptPath == null) return yaml
        val scriptFile = File(scriptPath)
        require(scriptFile.isFile) { "绑定脚本正文不存在" }
        val scriptBytes = scriptFile.readBytes()
        require(scriptBytes.isNotEmpty()) { "脚本内容不能为空" }
        require(scriptBytes.size <= MAX_SCRIPT_BYTES) { "脚本文件超过 2 MiB 限制" }
        return applyContent(yaml, scriptBytes.toString(Charsets.UTF_8))
    }

    internal suspend fun applyContent(yaml: String, script: String): String {
        // 作者 long：预览执行冻结的脚本正文，避免文件在读取与执行之间被替换而混入另一版本。
        require(script.isNotEmpty()) { "脚本内容不能为空" }
        require(script.toByteArray(Charsets.UTF_8).size <= MAX_SCRIPT_BYTES) { "脚本文件超过 2 MiB 限制" }
        val configJson = YamlConfigCodec.toJson(yaml)
        val resultJson = evaluator.evaluate(script, configJson)
        return YamlConfigCodec.toYaml(resultJson)
    }

    private companion object {
        const val MAX_SCRIPT_BYTES = 2 * 1024 * 1024
    }
}

/**
 * 作者 long：SnakeYAML 只使用安全构造器，拒绝通过 YAML 标签实例化任意 Java 类型；
 * 配置根节点必须是 map，才能与 Flutter 侧脚本契约一致。
 */
object YamlConfigCodec {
    private const val MAX_CONFIG_BYTES = 8 * 1024 * 1024
    private val gson = Gson()
    // 作者 long：预览可以与启动并行解析，SnakeYAML 实例不能在不同协程间共享可变解析状态。
    private fun loader() = Yaml(SafeConstructor(LoaderOptions()))
    private fun dumper() = Yaml(
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            width = 120
        },
    )

    fun toJson(yaml: String): String {
        require(yaml.toByteArray(Charsets.UTF_8).size <= MAX_CONFIG_BYTES) {
            "配置文件超过 8 MiB 限制"
        }
        // 作者 long：SnakeYAML 2.3 遇到缓冲区末尾的高代理项会再补读一个字符；
        // 每次常规读取预留一格，避免含 emoji 的节点名在扩展/脚本重排后碰到 1024 字符边界而越界。
        val parsed = loader().load<Any?>(object : StringReader(yaml) {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, if (offset == 0 && length == buffer.size && length > 1) length - 1 else length)
        })
        val json = gson.toJsonTree(parsed)
        require(json.isJsonObject) { "脚本配置必须是 YAML 对象" }
        val objectConfig = json.asJsonObject
        // 作者 long：Flutter 在执行脚本前保证 proxy-providers 存在，脚本可以直接写入该 map。
        if (!objectConfig.has("proxy-providers") ||
            objectConfig.get("proxy-providers").isJsonNull
        ) {
            objectConfig.add("proxy-providers", JsonObject())
        }
        return objectConfig.toString()
    }

    fun toYaml(json: String): String {
        val parsed = runCatching { JsonParser.parseString(json) }
            .getOrElse { throw IllegalArgumentException("脚本返回值不是有效 JSON", it) }
        require(parsed.isJsonObject) { "脚本必须返回配置对象" }
        val dumped = dumper().dump(jsonToJava(parsed.asJsonObject))
        return if (dumped.endsWith('\n')) dumped else "$dumped\n"
    }

    private fun jsonToJava(element: JsonElement): Any? = when {
        element.isJsonNull -> null
        element.isJsonObject -> linkedMapOf<String, Any?>().apply {
            element.asJsonObject.entrySet().forEach { (key, value) -> put(key, jsonToJava(value)) }
        }
        element.isJsonArray -> element.asJsonArray.map(::jsonToJava)
        element.isJsonPrimitive -> primitiveToJava(element.asJsonPrimitive)
        else -> error("脚本返回值包含无法序列化的 JSON 节点")
    }

    private fun primitiveToJava(primitive: JsonPrimitive): Any = when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isString -> primitive.asString
        primitive.isNumber -> primitive.asString.toLongOrNull()
            ?: primitive.asString.toDoubleOrNull()
            ?: BigDecimal.ZERO
        else -> primitive.asString
    }
}
