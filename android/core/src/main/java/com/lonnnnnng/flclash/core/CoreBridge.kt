package com.lonnnnnng.flclash.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 作者 long：把 Go 核心的 JSON method/event 协议收敛成可等待的 Kotlin API，
 * 这样服务层不会在页面或生命周期代码里拼接原始方法名，也不会把错误响应误当成成功。
 */
class CoreBridge {
    private val mutableEvents = MutableSharedFlow<CoreEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)

    /**
     * 作者 long：核心事件从 gomobile 回调线程进入共享流，页面只订阅领域事件，
     * 避免把 JSON 解码和回调线程边界泄露到 ViewModel 或 Compose。
     */
    val events: SharedFlow<CoreEvent> = mutableEvents.asSharedFlow()

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

    suspend fun getProxyGroups(): ProxyGroupsSnapshot {
        val response = invoke("getProxies", JSONObject())
        return parseProxyGroupsResponse(response.optJSONObject("result"))
    }

    suspend fun changeProxy(groupName: String, proxyName: String): Boolean {
        val response = invoke(
            method = "changeProxy",
            arguments = JSONObject()
                .put("group-name", groupName)
                .put("proxy-name", proxyName),
        )
        return parseChangeProxyResponse(response.optJSONObject("result"))
    }

    /**
     * 作者 long：测速使用核心当前已应用的代理名称和代理组测试地址，
     * 返回空值时表示核心没有拿到并发槽位，不能把它伪装成节点失败。
     */
    suspend fun testDelay(
        proxyName: String,
        testUrl: String = DEFAULT_TEST_URL,
        timeoutMs: Long = DEFAULT_DELAY_TIMEOUT_MS,
    ): ProxyDelay? {
        val response = invoke(
            method = "asyncTestDelay",
            arguments = JSONObject()
                .put("proxy-name", proxyName)
                .put("test-url", testUrl)
                .put("timeout", timeoutMs),
        )
        return parseDelayResponse(response.optJSONObject("result"))
    }

    suspend fun getTraffic(): CoreTraffic =
        parseTrafficResponse(invoke("getTraffic", false).optJSONObject("result"))

    suspend fun getTotalTraffic(): CoreTraffic =
        parseTrafficResponse(invoke("getTotalTraffic", false).optJSONObject("result"))

    suspend fun getConnectionCount(): Int =
        invoke("getConnectionCount", JSONObject()).optInt("result", 0).coerceAtLeast(0)

    suspend fun getConnections(): List<CoreConnection> =
        parseConnectionsResponse(invoke("getConnections", JSONObject()).optJSONObject("result"))

    fun startEventListener() {
        Core.updateEventListener { bytes ->
            parseEvents(bytes).forEach { mutableEvents.tryEmit(it) }
        }
    }

    fun stopEventListener() = Core.updateEventListener(null)

    fun startListener() = Core.startListener()

    fun stopListener() = Core.stopListener()

    internal fun parseProxyGroupsResponse(result: JSONObject?): ProxyGroupsSnapshot {
        requireNotNull(result) { "核心代理组响应缺少 result" }
        val proxies = result.optJSONObject("proxies") ?: JSONObject()
        val names = result.optJSONArray("all").toStringList()
        val payloads = buildMap {
            names.forEach { groupName ->
                val raw = proxies.optJSONObject(groupName) ?: return@forEach
                put(
                    groupName,
                    ProxyGroupPayload(
                        name = raw.optString("name", groupName),
                        type = raw.optString("type", "Unknown"),
                        now = raw.optNullableString("now"),
                        all = raw.optJSONArray("all").toStringList(),
                        hidden = raw.optBoolean("hidden", false),
                        testUrl = raw.optNullableString("testUrl"),
                    ),
                )
            }
        }
        return parseProxyGroupsPayload(names, payloads)
    }

    internal fun parseProxyGroupsPayload(
        names: List<String>,
        payloads: Map<String, ProxyGroupPayload>,
    ): ProxyGroupsSnapshot {
        val groups = names.mapNotNull { groupName ->
            val payload = payloads[groupName] ?: return@mapNotNull null
            ProxyGroupInfo(
                name = payload.name,
                type = payload.type,
                now = payload.now,
                all = payload.all,
                hidden = payload.hidden,
                testUrl = payload.testUrl,
            )
        }
        return ProxyGroupsSnapshot(groups)
    }

    internal fun parseChangeProxyResponse(result: JSONObject?): Boolean {
        requireNotNull(result) { "核心代理切换响应缺少 result" }
        return parseChangeProxyPayload(
            message = result.optString("message", ""),
            changed = result.optBoolean("changed", false),
        )
    }

    internal fun parseChangeProxyPayload(message: String?, changed: Boolean): Boolean {
        val message = message.orEmpty()
        if (message.isNotBlank()) {
            throw CoreBridgeException("change_proxy_failed", message)
        }
        return changed
    }

    internal fun parseDelayResponse(result: JSONObject?): ProxyDelay? {
        if (result == null) return null
        return parseDelayPayload(
            ProxyDelayPayload(
                url = result.optNullableString("url"),
                name = result.optNullableString("name"),
                value = result.optIntOrNull("value"),
            ),
        )
    }

    internal fun parseDelayPayload(payload: ProxyDelayPayload?): ProxyDelay? {
        if (payload == null) return null
        return ProxyDelay(
            url = payload.url.orEmpty(),
            name = payload.name.orEmpty(),
            value = payload.value ?: DELAY_FAILED,
        )
    }

    internal fun parseTrafficResponse(result: JSONObject?): CoreTraffic {
        requireNotNull(result) { "核心流量响应缺少 result" }
        return parseTrafficPayload(
            up = result.optLong("up", 0L),
            down = result.optLong("down", 0L),
        )
    }

    internal fun parseTrafficPayload(up: Long, down: Long): CoreTraffic = CoreTraffic(
        up = up.coerceAtLeast(0L),
        down = down.coerceAtLeast(0L),
    )

    internal fun parseConnectionsResponse(result: JSONObject?): List<CoreConnection> {
        requireNotNull(result) { "核心连接响应缺少 result" }
        val connections = result.optJSONArray("connections") ?: return emptyList()
        return buildList(connections.length()) {
            for (index in 0 until connections.length()) {
                val raw = connections.optJSONObject(index) ?: continue
                add(parseConnectionPayload(raw))
            }
        }
    }

    internal fun parseConnectionPayload(raw: JSONObject): CoreConnection {
        val metadata = raw.optJSONObject("metadata") ?: JSONObject()
        return parseConnectionPayload(
            CoreConnectionPayload(
                id = raw.optString("id"),
                upload = raw.optLong("upload", 0L),
                download = raw.optLong("download", 0L),
                start = raw.optString("start"),
                metadata = CoreConnectionMetadata(
                network = metadata.optString("network"),
                sourceIp = metadata.optString("sourceIP"),
                sourcePort = metadata.optPort("sourcePort"),
                destinationIp = metadata.optString("destinationIP"),
                destinationPort = metadata.optPort("destinationPort"),
                host = metadata.optString("host"),
                process = metadata.optString("process"),
                processPath = metadata.optString("processPath"),
                remoteDestination = metadata.optString("remoteDestination"),
                uid = metadata.optLong("uid", 0L).coerceAtLeast(0L),
                specialProxy = metadata.optString("specialProxy"),
                specialRules = metadata.optString("specialRules"),
                ),
                chains = raw.optJSONArray("chains").toStringList(),
                rule = raw.optString("rule"),
                rulePayload = raw.optString("rulePayload"),
            ),
        )
    }

    internal fun parseConnectionPayload(payload: CoreConnectionPayload): CoreConnection = CoreConnection(
        id = payload.id,
        upload = payload.upload.coerceAtLeast(0L),
        download = payload.download.coerceAtLeast(0L),
        start = payload.start,
        metadata = payload.metadata,
        chains = payload.chains,
        rule = payload.rule,
        rulePayload = payload.rulePayload,
    )

    private fun parseConnectionJson(raw: JsonObject): CoreConnection {
        val metadata = raw.objectValue("metadata") ?: JsonObject()
        return parseConnectionPayload(
            CoreConnectionPayload(
                id = raw.string("id"),
                upload = raw.long("upload"),
                download = raw.long("download"),
                start = raw.string("start"),
                metadata = CoreConnectionMetadata(
                    network = metadata.string("network"),
                    sourceIp = metadata.string("sourceIP"),
                    sourcePort = metadata.string("sourcePort"),
                    destinationIp = metadata.string("destinationIP"),
                    destinationPort = metadata.string("destinationPort"),
                    host = metadata.string("host"),
                    process = metadata.string("process"),
                    processPath = metadata.string("processPath"),
                    remoteDestination = metadata.string("remoteDestination"),
                    uid = metadata.long("uid").coerceAtLeast(0L),
                    specialProxy = metadata.string("specialProxy"),
                    specialRules = metadata.string("specialRules"),
                ),
                chains = raw.array("chains").stringList(),
                rule = raw.string("rule"),
                rulePayload = raw.string("rulePayload"),
            ),
        )
    }

    /**
     * 作者 long：Go 核心把多个事件放进 `message.arguments` 批次；未知或损坏的事件只丢弃
     * 当前条目，不能让一次异常事件阻断后续请求和流量事件。
     */
    internal fun parseEvents(bytes: ByteArray): List<CoreEvent> {
        val envelope = runCatching {
            JsonParser.parseString(bytes.decodeToString()).asJsonObject
        }.getOrNull() ?: return emptyList()
        if (envelope.string("method") != "message") return emptyList()
        val arguments = envelope.array("arguments") ?: return emptyList()
        return buildList(arguments.size()) {
            arguments.forEach { element ->
                if (!element.isJsonObject) return@forEach
                parseEvent(element.asJsonObject)?.let(::add)
            }
        }
    }

    private fun parseEvent(raw: JsonObject): CoreEvent? {
        val type = raw.string("type")
        return runCatching {
            when (type) {
                "request" -> {
                    val data = raw.objectValue("data") ?: return@runCatching null
                    CoreEvent.Request(parseConnectionJson(data))
                }
                "requestTraffic" -> {
                    val data = raw.array("data") ?: return@runCatching null
                    CoreEvent.RequestTraffic(
                        buildList(data.size()) {
                            data.forEach { element ->
                                if (!element.isJsonObject) return@forEach
                                val item = element.asJsonObject
                                add(
                                    CoreTrafficUpdate(
                                        id = item.string("id"),
                                        upload = item.long("upload").coerceAtLeast(0L),
                                        download = item.long("download").coerceAtLeast(0L),
                                    ),
                                )
                            }
                        },
                    )
                }
                "log" -> {
                    val data = raw.objectValue("data") ?: return@runCatching null
                    CoreEvent.Log(
                        level = parseLogLevel(data["LogLevel"]),
                        payload = data.string("Payload"),
                        timestampMillis = System.currentTimeMillis(),
                    )
                }
                "dns" -> {
                    val data = raw.objectValue("data") ?: return@runCatching null
                    CoreEvent.Dns(
                        CoreDnsQuery(
                            domain = data.string("domain"),
                            type = data.string("type"),
                            initiator = data.string("initiator"),
                            upstream = data.string("upstream"),
                            cached = data.bool("cached"),
                            answers = data.array("answers").stringList(),
                            rcode = data.string("rcode"),
                            error = data.string("error"),
                            delay = data.long("delay").coerceAtLeast(0L),
                            time = data.string("time"),
                        ),
                    )
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun parseLogLevel(value: com.google.gson.JsonElement?): String = when {
        value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber -> when (value.asInt) {
            0 -> "debug"
            1 -> "info"
            2 -> "warning"
            3 -> "error"
            4 -> "silent"
            else -> "unknown"
        }
        else -> value?.takeUnless { it.isJsonNull }?.asString?.lowercase().orEmpty().ifBlank { "info" }
    }

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
        private const val EVENT_BUFFER_SIZE = 256
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
        const val DEFAULT_DELAY_TIMEOUT_MS = 8_000L
        const val DELAY_TIMED_OUT = -1
        const val DELAY_FAILED = -2
    }
}

sealed interface CoreEvent {
    data class Request(val connection: CoreConnection) : CoreEvent

    data class RequestTraffic(val updates: List<CoreTrafficUpdate>) : CoreEvent

    data class Log(
        val level: String,
        val payload: String,
        val timestampMillis: Long,
    ) : CoreEvent

    data class Dns(val query: CoreDnsQuery) : CoreEvent
}

data class CoreTrafficUpdate(
    val id: String,
    val upload: Long,
    val download: Long,
)

data class CoreDnsQuery(
    val domain: String,
    val type: String,
    val initiator: String,
    val upstream: String,
    val cached: Boolean,
    val answers: List<String>,
    val rcode: String,
    val error: String,
    val delay: Long,
    val time: String,
)

data class ProxyGroupsSnapshot(
    val groups: List<ProxyGroupInfo>,
)

data class ProxyGroupInfo(
    val name: String,
    val type: String,
    val now: String?,
    val all: List<String>,
    val hidden: Boolean,
    val testUrl: String? = null,
) {
    val isSelector: Boolean
        get() = type.equals("Selector", ignoreCase = true)
}

data class ProxyGroupPayload(
    val name: String,
    val type: String,
    val now: String?,
    val all: List<String>,
    val hidden: Boolean,
    val testUrl: String? = null,
)

data class ProxyDelay(
    val url: String,
    val name: String,
    val value: Int,
)

data class ProxyDelayPayload(
    val url: String?,
    val name: String?,
    val value: Int?,
)

data class CoreTraffic(
    val up: Long,
    val down: Long,
)

data class CoreConnection(
    val id: String,
    val upload: Long,
    val download: Long,
    val start: String,
    val metadata: CoreConnectionMetadata,
    val chains: List<String>,
    val rule: String,
    val rulePayload: String,
)

data class CoreConnectionPayload(
    val id: String,
    val upload: Long,
    val download: Long,
    val start: String,
    val metadata: CoreConnectionMetadata,
    val chains: List<String>,
    val rule: String,
    val rulePayload: String,
)

data class CoreConnectionMetadata(
    val network: String,
    val sourceIp: String,
    val sourcePort: String,
    val destinationIp: String,
    val destinationPort: String,
    val host: String,
    val process: String,
    val processPath: String,
    val remoteDestination: String,
    val uid: Long,
    val specialProxy: String,
    val specialRules: String,
)

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun JsonObject.string(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.let { element ->
        runCatching { element.asString }.getOrNull()
    }.orEmpty()

private fun JsonObject.long(name: String): Long =
    get(name)?.takeUnless { it.isJsonNull }?.let { element ->
        runCatching { element.asLong }.getOrNull()
    } ?: 0L

private fun JsonObject.bool(name: String): Boolean =
    get(name)?.takeUnless { it.isJsonNull }?.let { element ->
        runCatching { element.asBoolean }.getOrNull()
    } ?: false

private fun JsonObject.objectValue(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.array(name: String): com.google.gson.JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun com.google.gson.JsonArray?.stringList(): List<String> =
    this?.mapNotNull { element ->
        element.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }
    }.orEmpty()

private fun JSONObject.optNullableString(name: String): String? {
    if (isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}

private fun JSONObject.optIntOrNull(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name)
}

private fun JSONObject.optPort(name: String): String {
    if (!has(name) || isNull(name)) return ""
    return optString(name).takeIf { it.isNotBlank() } ?: optInt(name).toString()
}

class CoreBridgeException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
