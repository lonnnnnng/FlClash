package com.lonnnnnng.flclash.core

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonParser
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 作者 long：把 Go 核心的 JSON method/event 协议收敛成可等待的 Kotlin API，
 * 这样服务层不会在页面或生命周期代码里拼接原始方法名，也不会把错误响应误当成成功。
 */
class CoreBridge {
    /**
     * 作者 long：Go 回调线程不能等待 Compose 或服务层消费事件，因此使用固定容量并显式保持挂起策略，
     * 再通过非阻塞 tryEmit 在缓冲满时丢弃最新事件；这样返回值可以准确统计丢弃数量。
     */
    private val mutableEvents = MutableSharedFlow<CoreEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    private val mutableEventFlowStats = MutableStateFlow(CoreEventFlowStats())

    /**
     * 作者 long：核心事件从 gomobile 回调线程进入共享流，页面只订阅领域事件，
     * 避免把 JSON 解码和回调线程边界泄露到 ViewModel 或 Compose。
     */
    val events: SharedFlow<CoreEvent> = mutableEvents.asSharedFlow()

    val eventFlowStats: StateFlow<CoreEventFlowStats> = mutableEventFlowStats.asStateFlow()

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
        parseMessageResult(response.optString("result", ""), "invalid_config")
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
        parseMessageResult(response.optString("result", ""), "apply_config_failed")
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

    suspend fun probe(
        url: String,
        proxyName: String = "",
        timeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
    ): CoreProbeResult? {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "真实请求诊断只支持 HTTP/HTTPS 地址"
        }
        val response = invoke(
            method = "probe",
            arguments = JSONObject()
                .put("url", url)
                .put("proxy-name", proxyName)
                .put("headers", JSONObject())
                .put("timeout", timeoutMs)
                // 作者 long：诊断只需要状态、延迟和路由归因，不读取订阅响应正文，避免把配置内容带入 UI 或日志。
                .put("max-body", 0),
        )
        return parseProbeResponse(response.optJSONObject("result"))
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
        resetEventFlowStats()
        Core.updateEventListener { bytes ->
            dispatchEvents(bytes)
        }
    }

    fun stopEventListener() = Core.updateEventListener(null)

    /**
     * 作者 long：事件流健康统计按一次核心运行会话重新计数，避免上一次运行的丢弃量污染当前诊断证据。
     */
    internal fun resetEventFlowStats() {
        mutableEventFlowStats.value = CoreEventFlowStats()
    }

    /**
     * 作者 long：将单个 Go 回调批次限制在固定大小内，并逐条使用 tryEmit；任何解析成功但未进入
     * 有界共享流的事件都计为丢弃，调用方不会因高频日志或请求事件阻塞核心线程。
     */
    internal fun dispatchEvents(bytes: ByteArray) {
        val batch = parseEventBatch(bytes) ?: return
        val now = System.currentTimeMillis()
        mutableEventFlowStats.update { current ->
            current.copy(
                parsedBatches = current.parsedBatches + 1,
                parsedEvents = current.parsedEvents + batch.events.size,
                truncatedEvents = current.truncatedEvents + batch.truncatedEvents,
                lastEventAtMillis = if (batch.events.isEmpty()) current.lastEventAtMillis else now,
            )
        }
        batch.events.forEach { event ->
            val accepted = mutableEvents.subscriptionCount.value > 0 && mutableEvents.tryEmit(event)
            mutableEventFlowStats.update { current ->
                if (accepted) {
                    current.copy(emittedEvents = current.emittedEvents + 1)
                } else {
                    current.copy(droppedEvents = current.droppedEvents + 1)
                }
            }
        }
    }

    /**
     * 作者 long：Go 核心的日志泵必须显式启动；仅注册事件回调不会订阅 Mihomo 日志，
     * 否则日志卡片会一直保持空态，无法解释真实请求失败原因。
     */
    suspend fun startLog() {
        invoke("startLog", JSONObject())
    }

    suspend fun stopLog() {
        invoke("stopLog", JSONObject())
    }

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

    /**
     * 作者 long：Go 的 withMessage 协议用空字符串表示成功，非空字符串承载 Mihomo 原始错误；
     * 统一在 Bridge 边界转换，确保配置校验和应用不会把具体行号、字段名等诊断信息丢掉。
     */
    internal fun parseMessageResult(message: String?, code: String) {
        val message = message.orEmpty()
        if (message.isNotBlank()) {
            throw CoreBridgeException(code, message)
        }
    }

    /**
     * 作者 long：旧版或异常 Go bridge 可能把 error 编码成字符串而不是对象，
     * 这里保留任意 JSON 错误的文本，避免随后被误报成“缺少 result”而遮住核心原因。
     */
    internal fun parseErrorResponse(response: JSONObject): CoreBridgeException? {
        if (!response.has("error") || response.isNull("error")) return null
        val rawError = response.get("error")
        val payload = when (rawError) {
            is JSONObject -> JsonParser.parseString(rawError.toString())
            is String -> JsonPrimitive(rawError)
            else -> JsonParser.parseString(rawError.toString())
        }
        return parseErrorPayload(payload)
    }

    internal fun parseErrorPayload(error: com.google.gson.JsonElement): CoreBridgeException {
        if (error.isJsonObject) {
            val payload = error.asJsonObject
            return CoreBridgeException(
                code = payload.get("code")?.takeUnless { it.isJsonNull }?.asString ?: "core_error",
                message = payload.get("message")?.takeUnless { it.isJsonNull }?.asString ?: "核心调用失败",
            )
        }
        return CoreBridgeException(
            code = "core_error",
            message = error.toString().removeSurrounding("\"").ifBlank { "核心调用失败" },
        )
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

    internal fun parseProbeResponse(result: JSONObject?): CoreProbeResult? {
        if (result == null) return null
        return parseProbePayload(
            CoreProbePayload(
                statusCode = result.optInt("status-code", 0),
                delay = result.optLong("delay", 0L),
                url = result.optString("url"),
                chains = result.optJSONArray("chains").toStringList(),
                rule = result.optString("rule"),
                rulePayload = result.optString("rule-payload"),
                error = result.optString("error"),
                message = result.optString("message"),
                coreEpoch = result.optLong("core-epoch", 0L),
                routeVersion = result.optLong("route-version", 0L),
            ),
        )
    }

    internal fun parseProbePayload(payload: CoreProbePayload): CoreProbeResult = CoreProbeResult(
        statusCode = payload.statusCode.coerceAtLeast(0),
        delay = payload.delay.coerceAtLeast(0L),
        url = payload.url,
        chains = payload.chains,
        rule = payload.rule,
        rulePayload = payload.rulePayload,
        error = payload.error,
        message = payload.message,
        coreEpoch = payload.coreEpoch.coerceAtLeast(0L),
        routeVersion = payload.routeVersion.coerceAtLeast(0L),
    )

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
        return parseEventBatch(bytes)?.events.orEmpty()
    }

    private fun parseEventBatch(bytes: ByteArray): ParsedEventBatch? {
        val envelope = runCatching {
            JsonParser.parseString(bytes.decodeToString()).asJsonObject
        }.getOrNull() ?: return null
        if (envelope.string("method") != "message") return null
        val arguments = envelope.array("arguments") ?: return null
        val limit = minOf(arguments.size(), MAX_EVENTS_PER_BATCH)
        var nestedTruncatedEvents = 0
        val events = buildList(limit) {
            for (index in 0 until limit) {
                val element = arguments[index]
                if (!element.isJsonObject) continue
                val raw = element.asJsonObject
                if (raw.string("type") == "requestTraffic") {
                    val updates = raw.array("data")
                    nestedTruncatedEvents += (updates?.size()?.minus(MAX_TRAFFIC_UPDATES_PER_EVENT) ?: 0)
                        .coerceAtLeast(0)
                }
                parseEvent(raw)?.let(::add)
            }
        }
        return ParsedEventBatch(
            events = events,
            truncatedEvents = (arguments.size() - limit).coerceAtLeast(0) + nestedTruncatedEvents,
        )
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
                    val limit = minOf(data.size(), MAX_TRAFFIC_UPDATES_PER_EVENT)
                    CoreEvent.RequestTraffic(
                        buildList(limit) {
                            for (index in 0 until limit) {
                                val element = data[index]
                                if (!element.isJsonObject) continue
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
                "delay" -> {
                    val data = raw.objectValue("data") ?: return@runCatching null
                    CoreEvent.Delay(
                        ProxyDelay(
                            url = data.string("url"),
                            name = data.string("name"),
                            value = data.long("value")
                                .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                                .toInt(),
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
                    val error = parseErrorResponse(response)
                    if (error != null) {
                        continuation.resumeWithException(error)
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
        internal const val EVENT_BUFFER_SIZE = 256
        internal const val MAX_EVENTS_PER_BATCH = 512
        private const val MAX_TRAFFIC_UPDATES_PER_EVENT = 256
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
        const val DEFAULT_DELAY_TIMEOUT_MS = 8_000L
        const val DEFAULT_PROBE_TIMEOUT_MS = 20_000L
        const val DELAY_TIMED_OUT = -1
        const val DELAY_FAILED = -2
    }
}

private data class ParsedEventBatch(
    val events: List<CoreEvent>,
    val truncatedEvents: Int,
)

data class CoreEventFlowStats(
    val parsedBatches: Long = 0L,
    val parsedEvents: Long = 0L,
    val emittedEvents: Long = 0L,
    val droppedEvents: Long = 0L,
    val truncatedEvents: Long = 0L,
    val lastEventAtMillis: Long = 0L,
)

sealed interface CoreEvent {
    data class Request(val connection: CoreConnection) : CoreEvent

    data class RequestTraffic(val updates: List<CoreTrafficUpdate>) : CoreEvent

    data class Log(
        val level: String,
        val payload: String,
        val timestampMillis: Long,
    ) : CoreEvent

    data class Dns(val query: CoreDnsQuery) : CoreEvent

    data class Delay(val result: ProxyDelay) : CoreEvent
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

    /**
     * 作者 long：URLTest/Fallback 由 Mihomo 自动计算当前节点，但 Flutter 仍允许用户临时指定或清除该选择；
     * 把两类 computed 组单独建模，避免 UI 把它们误当成只能手动切换的 Selector。
     */
    val isComputed: Boolean
        get() = type.equals("URLTest", ignoreCase = true) ||
            type.equals("Fallback", ignoreCase = true)

    val isSelectable: Boolean
        get() = isSelector || isComputed
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

data class CoreProbePayload(
    val statusCode: Int,
    val delay: Long,
    val url: String,
    val chains: List<String>,
    val rule: String,
    val rulePayload: String,
    val error: String,
    val message: String,
    val coreEpoch: Long,
    val routeVersion: Long,
)

data class CoreProbeResult(
    val statusCode: Int,
    val delay: Long,
    val url: String,
    val chains: List<String>,
    val rule: String,
    val rulePayload: String,
    val error: String,
    val message: String,
    val coreEpoch: Long,
    val routeVersion: Long,
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
