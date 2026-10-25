package com.lonnnnnng.flclash.core

import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 作者 long：把 Go 核心的 JSON method/event 协议收敛成可等待的 Kotlin API，
 * 这样服务层不会在页面或生命周期代码里拼接原始方法名，也不会把错误响应误当成成功。
 */
class CoreBridge {
    suspend fun initialize(homeDir: String, sdkVersion: Int) {
        val response = invoke(
            method = "initClash",
            arguments = JSONObject()
                .put("home-dir", homeDir)
                .put("version", sdkVersion),
        )
        if (!response.optBoolean("result", false)) {
            throw CoreBridgeException("core_init_failed", "Mihomo 核心初始化失败")
        }
    }

    suspend fun validateConfig(path: String) {
        val response = invoke("validateConfig", path)
        val message = response.optString("result", "")
        if (message.isNotBlank()) {
            throw CoreBridgeException("invalid_config", message)
        }
    }

    suspend fun setupConfig(
        selectedMap: Map<String, String> = emptyMap(),
        testUrl: String = DEFAULT_TEST_URL,
        skipCertVerify: Boolean = false,
    ) {
        val selected = JSONObject()
        selectedMap.forEach { (group, proxy) -> selected.put(group, proxy) }
        val response = invoke(
            method = "setupConfig",
            arguments = JSONObject()
                .put("selected-map", selected)
                .put("test-url", testUrl)
                .put("skip-cert-verify", skipCertVerify),
        )
        val message = response.optString("result", "")
        if (message.isNotBlank()) {
            throw CoreBridgeException("apply_config_failed", message)
        }
    }

    fun startListener() = Core.startListener()

    fun stopListener() = Core.stopListener()

    private suspend fun invoke(method: String, arguments: Any): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val request = JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("method", method)
                .put("arguments", arguments)
                .toString()
            runCatching {
                Core.invokeMethod(request) { bytes ->
                    if (!continuation.isActive) return@invokeMethod
                    val response = runCatching {
                        requireNotNull(bytes) { "核心没有返回响应" }
                        JSONObject(bytes.decodeToString())
                    }.getOrElse { error ->
                        continuation.resumeWithException(
                            CoreBridgeException("invalid_core_response", error.message ?: "核心响应无效", error),
                        )
                        return@invokeMethod
                    }
                    val error = response.optJSONObject("error")
                    if (error != null) {
                        continuation.resumeWithException(
                            CoreBridgeException(
                                code = error.optString("code", "core_error"),
                                message = error.optString("message", "核心调用失败"),
                            ),
                        )
                    } else if (!response.has("result")) {
                        continuation.resumeWithException(
                            CoreBridgeException("missing_core_result", "核心响应缺少 result 字段"),
                        )
                    } else {
                        continuation.resume(response)
                    }
                }
            }.onFailure { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
    }
}

class CoreBridgeException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
