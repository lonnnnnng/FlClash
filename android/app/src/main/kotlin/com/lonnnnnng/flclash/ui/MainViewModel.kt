package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.core.CoreProbeResult
import com.lonnnnnng.flclash.core.CoreTraffic
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val runtimeManager: CoreRuntimeManager,
) : ViewModel() {
    val runtimeState = runtimeManager.state
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val errors = mutableErrors.asSharedFlow()

    private val mutableStats = MutableStateFlow(RuntimeStatsUiState())
    val stats = mutableStats.asStateFlow()

    private val mutableConnections = MutableStateFlow(ConnectionsUiState())
    val connections = mutableConnections.asStateFlow()

    private val mutableRequests = MutableStateFlow(RequestsUiState())
    val requests = mutableRequests.asStateFlow()

    private val mutableDns = MutableStateFlow(DnsUiState())
    val dns = mutableDns.asStateFlow()

    private val mutableLogs = MutableStateFlow(LogsUiState())
    val logs = mutableLogs.asStateFlow()

    private val mutableProbe = MutableStateFlow(ProbeUiState())
    val probeState = mutableProbe.asStateFlow()

    private val mutableDiagnostic = MutableStateFlow(DiagnosticSessionUiState())
    val diagnosticState = mutableDiagnostic.asStateFlow()
    private var diagnosticSequence = 0L

    init {
        viewModelScope.launch {
            runtimeManager.events.collect { event ->
                mutableRequests.update { current -> current.apply(event) }
                mutableDns.update { current -> current.apply(event) }
                mutableLogs.update { current -> current.apply(event) }
                mutableDiagnostic.update { current -> current.withEvent(event) }
            }
        }
        viewModelScope.launch {
            runtimeState.collectLatest { state ->
                if (!state.isRunning) {
                    mutableStats.value = RuntimeStatsUiState()
                    mutableConnections.value = ConnectionsUiState()
                    mutableRequests.value = RequestsUiState()
                    mutableDns.value = DnsUiState()
                    mutableLogs.value = LogsUiState()
                    mutableProbe.value = ProbeUiState()
                    mutableDiagnostic.value = DiagnosticSessionUiState()
                    return@collectLatest
                }
                while (isActive) {
                    refreshStats()
                    refreshConnections()
                    delay(STATS_REFRESH_MILLIS)
                }
            }
        }
    }

    fun startRuntime() {
        viewModelScope.launch {
            runtimeManager.startProxy().onFailure { error ->
                mutableErrors.emit(error.message ?: "核心启动失败")
            }
        }
    }

    fun stopRuntime() {
        viewModelScope.launch {
            runtimeManager.stop().onFailure { error ->
                mutableErrors.emit(error.message ?: "核心停止失败")
            }
        }
    }

    fun reportError(message: String) {
        mutableErrors.tryEmit(message)
    }

    fun clearDns() {
        mutableDns.value = DnsUiState()
    }

    fun clearLogs() {
        mutableLogs.value = LogsUiState()
    }

    fun probe(url: String) {
        val target = url.trim()
        if (target.isBlank() || mutableProbe.value.isLoading) return
        val sessionId = ++diagnosticSequence
        val startedAt = System.currentTimeMillis()
        mutableDiagnostic.value = DiagnosticSessionUiState(
            status = DiagnosticSessionStatus.RUNNING,
            sessionId = sessionId,
            targetUrl = target,
            startedAtMillis = startedAt,
        )
        viewModelScope.launch {
            mutableProbe.value = ProbeUiState(targetUrl = target, isLoading = true)
            runtimeManager.probe(target)
                .onSuccess { result ->
                    mutableProbe.value = when {
                        result == null -> ProbeUiState(
                            targetUrl = target,
                            error = "核心未返回诊断结果",
                        )

                        result.error.isNotBlank() -> ProbeUiState(
                            targetUrl = target,
                            result = result,
                            error = result.message.ifBlank { result.error },
                        )

                        else -> ProbeUiState(targetUrl = target, result = result)
                    }
                    // 作者 long：核心刚返回 probe 结果时，关联的 request/DNS/log 事件可能仍在事件流中排队，
                    // 留出短暂收敛窗口后再给出会话断言，避免把尚未到达的证据误判为空。
                    delay(DIAGNOSTIC_SETTLE_MILLIS)
                    finishDiagnosticSession(sessionId, result, null)
                }
                .onFailure { error ->
                    mutableProbe.value = ProbeUiState(
                        targetUrl = target,
                        error = error.message ?: "真实请求诊断失败",
                    )
                    finishDiagnosticSession(sessionId, null, error.message ?: "真实请求诊断失败")
                }
        }
    }

    fun toggleRuntime() {
        viewModelScope.launch {
            val result = if (runtimeManager.state.value.isRunning) {
                runtimeManager.stop()
            } else {
                runtimeManager.startProxy()
            }
            result.onFailure { error ->
                mutableErrors.emit(error.message ?: "核心操作失败")
            }
        }
    }

    private suspend fun refreshStats() {
        mutableStats.value = mutableStats.value.copy(isLoading = true, error = null)
        runtimeManager.getStats()
            .onSuccess { value ->
                mutableStats.value = RuntimeStatsUiState(
                    traffic = value.traffic,
                    totalTraffic = value.totalTraffic,
                    connectionCount = value.connectionCount,
                )
            }
            .onFailure { error ->
                // 作者 long：统计轮询失败不能把运行状态改成停止；只在卡片上保留可解释错误，下一轮继续恢复。
                mutableStats.value = mutableStats.value.copy(
                    isLoading = false,
                    error = error.message ?: "读取运行统计失败",
                )
            }
    }

    private suspend fun refreshConnections() {
        mutableConnections.value = mutableConnections.value.copy(isLoading = true, error = null)
        runtimeManager.getConnections()
            .onSuccess { value ->
                mutableConnections.value = ConnectionsUiState(connections = value)
            }
            .onFailure { error ->
                // 作者 long：连接列表读取失败只影响观测卡片，不得把核心运行状态误判为停止。
                mutableConnections.value = mutableConnections.value.copy(
                    isLoading = false,
                    error = error.message ?: "读取活动连接失败",
                )
            }
    }

    private fun finishDiagnosticSession(
        sessionId: Long,
        result: CoreProbeResult?,
        error: String?,
    ) {
        if (mutableDiagnostic.value.sessionId != sessionId) return
        val failure = if (result == null && error == null) {
            "核心未返回诊断结果"
        } else {
            error ?: result?.let(::diagnosticFailureReason)
        }
        mutableDiagnostic.update { current ->
            current.copy(
                status = if (result != null && failure == null) {
                    DiagnosticSessionStatus.PASS
                } else {
                    DiagnosticSessionStatus.FAIL
                },
                result = result,
                error = failure,
                assertion = failure ?: "HTTP 状态、代理链和核心归因均有效",
                finishedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    companion object {
        private const val STATS_REFRESH_MILLIS = 1_000L
        private const val DIAGNOSTIC_SETTLE_MILLIS = 750L
    }
}

data class RuntimeStatsUiState(
    val traffic: CoreTraffic = CoreTraffic(0L, 0L),
    val totalTraffic: CoreTraffic = CoreTraffic(0L, 0L),
    val connectionCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class ConnectionsUiState(
    val connections: List<CoreConnection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class RequestsUiState(
    val requests: List<CoreConnection> = emptyList(),
) {
    /**
     * 作者 long：请求事件按连接 ID 增量更新，保留最近 2000 条历史，
     * 这样流量事件不会重复插入记录，也不会让长时间运行的手机应用无限增长内存。
     */
    internal fun apply(event: CoreEvent): RequestsUiState = when (event) {
        is CoreEvent.Request -> {
            val replaced = requests.indexOfFirst { it.id == event.connection.id }
            val next = if (replaced >= 0) {
                requests.toMutableList().apply { set(replaced, event.connection) }
            } else {
                (requests + event.connection).takeLast(MAX_REQUEST_HISTORY)
            }
            copy(requests = next)
        }

        is CoreEvent.RequestTraffic -> copy(
            requests = requests.map { request ->
                event.updates.firstOrNull { it.id == request.id }?.let { update ->
                    request.copy(upload = update.upload, download = update.download)
                } ?: request
            },
        )

        else -> this
    }
}

/**
 * 作者 long：详情对话框只允许搜索用户可识别的网络字段，避免把 Kotlin 对象的内部格式暴露给 UI，
 * 同时让请求、DNS 和日志使用同一套大小写不敏感的筛选语义。
 */
internal fun filterRequests(
    requests: List<CoreConnection>,
    query: String,
): List<CoreConnection> {
    val needle = query.trim()
    if (needle.isEmpty()) return requests
    return requests.filter { request ->
        listOf(
            request.id,
            request.metadata.network,
            request.metadata.host,
            request.metadata.destinationIp,
            request.metadata.destinationPort,
            request.metadata.process,
            request.metadata.remoteDestination,
            request.chains.joinToString(" "),
            request.rule,
            request.rulePayload,
        ).containsIgnoreCase(needle)
    }
}

data class DnsUiState(
    val queries: List<CoreDnsQuery> = emptyList(),
) {
    /**
     * 作者 long：DNS 查询是高频事件，只保留最近 1000 条，避免长时间运行时把每次解析都永久留在内存中。
     */
    internal fun apply(event: CoreEvent): DnsUiState = when (event) {
        is CoreEvent.Dns -> copy(queries = (queries + event.query).takeLast(MAX_DNS_HISTORY))
        else -> this
    }
}

internal fun filterDnsQueries(
    queries: List<CoreDnsQuery>,
    query: String,
): List<CoreDnsQuery> {
    val needle = query.trim()
    if (needle.isEmpty()) return queries
    return queries.filter { dns ->
        listOf(
            dns.domain,
            dns.type,
            dns.initiator,
            dns.upstream,
            dns.answers.joinToString(" "),
            dns.rcode,
            dns.error,
        ).containsIgnoreCase(needle)
    }
}

data class LogsUiState(
    val entries: List<CoreEvent.Log> = emptyList(),
) {
    /**
     * 作者 long：日志需要保留故障上下文但不能无限增长；清空由用户显式触发，便于围绕一次问题重新收集证据。
     */
    internal fun apply(event: CoreEvent): LogsUiState = when (event) {
        is CoreEvent.Log -> copy(entries = (entries + event).takeLast(MAX_LOG_HISTORY))
        else -> this
    }
}

internal fun filterLogs(
    entries: List<CoreEvent.Log>,
    query: String,
    level: String?,
): List<CoreEvent.Log> {
    val needle = query.trim()
    return entries.filter { entry ->
        val levelMatches = level.isNullOrBlank() || entry.level.equals(level, ignoreCase = true)
        val textMatches = needle.isEmpty() ||
            listOf(entry.level, entry.payload).containsIgnoreCase(needle)
        levelMatches && textMatches
    }
}

/**
 * 作者 long：导出日志用于诊断时必须保留时间、级别和正文上下文，同时先遮蔽 URL 参数与常见凭据字段，
 * 避免用户把订阅 Token 或临时认证信息带出应用数据目录。
 */
internal fun formatLogsForExport(entries: List<CoreEvent.Log>): String = buildString {
    appendLine("KClash core logs")
    appendLine("timestamp_millis\tlevel\tmessage")
    entries.forEach { entry ->
        val safePayload = redactSensitiveLogText(entry.payload).replace('\n', ' ')
        append(entry.timestampMillis)
            .append('\t')
            .append(entry.level.ifBlank { "info" })
            .append('\t')
            .appendLine(safePayload)
    }
}

private fun redactSensitiveLogText(value: String): String = value
    .replace(SENSITIVE_QUERY_PARAMETER, "$1[REDACTED_SECRET]")
    .replace(SENSITIVE_JSON_VALUE, "$1[REDACTED_SECRET]$2")

private val SENSITIVE_QUERY_PARAMETER = Regex(
    "(?i)(^|[?&\\s])((?:token|access_token|api_key|key|secret|password|url)=)[^\\s&]+",
)
private val SENSITIVE_JSON_VALUE = Regex(
    "(?i)(\\\"(?:token|access_token|api_key|key|secret|password|url)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")",
)

data class ProbeUiState(
    val targetUrl: String? = null,
    val isLoading: Boolean = false,
    val result: CoreProbeResult? = null,
    val error: String? = null,
)

enum class DiagnosticSessionStatus {
    IDLE,
    RUNNING,
    PASS,
    FAIL,
}

data class DiagnosticSessionUiState(
    val status: DiagnosticSessionStatus = DiagnosticSessionStatus.IDLE,
    val sessionId: Long = 0L,
    val targetUrl: String? = null,
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val result: CoreProbeResult? = null,
    val error: String? = null,
    val assertion: String? = null,
    val requests: List<CoreConnection> = emptyList(),
    val dns: List<CoreDnsQuery> = emptyList(),
    val logs: List<CoreEvent.Log> = emptyList(),
) {
    /**
     * 作者 long：证据包只接收会话运行窗口内到达的事件，并单独设置较小上限，避免一次诊断把长期观测历史整体复制进内存。
     */
    internal fun withEvent(event: CoreEvent): DiagnosticSessionUiState = when {
        status != DiagnosticSessionStatus.RUNNING -> this
        event is CoreEvent.Request -> {
            val index = requests.indexOfFirst { it.id == event.connection.id }
            val next = if (index >= 0) {
                requests.toMutableList().apply { set(index, event.connection) }
            } else {
                (requests + event.connection).takeLast(MAX_DIAGNOSTIC_ITEMS)
            }
            copy(requests = next)
        }

        event is CoreEvent.RequestTraffic -> copy(
            requests = requests.map { request ->
                event.updates.firstOrNull { it.id == request.id }?.let { update ->
                    request.copy(upload = update.upload, download = update.download)
                } ?: request
            },
        )

        event is CoreEvent.Dns -> copy(dns = (dns + event.query).takeLast(MAX_DIAGNOSTIC_ITEMS))
        event is CoreEvent.Log -> copy(logs = (logs + event).takeLast(MAX_DIAGNOSTIC_ITEMS))
        else -> this
    }
}

internal fun diagnosticFailureReason(result: CoreProbeResult): String? = when {
    result.error.isNotBlank() -> result.message.ifBlank { result.error }
    result.statusCode <= 0 -> "未收到 HTTP 状态"
    result.chains.isEmpty() -> "核心未返回代理链"
    else -> null
}

/**
 * 作者 long：诊断证据包与普通日志导出使用相同脱敏边界，既保留请求归因信息，也不把订阅凭据带出应用。
 */
internal fun formatDiagnosticForExport(session: DiagnosticSessionUiState): String = buildString {
    appendLine("KClash diagnostic session")
    appendLine("session_id\t${session.sessionId}")
    appendLine("status\t${session.status}")
    appendLine("target\t${redactSensitiveLogText(session.targetUrl.orEmpty())}")
    session.result?.let { result ->
        appendLine("probe\tHTTP ${result.statusCode}\t${result.delay} ms")
        appendLine("route\t${redactSensitiveLogText(result.chains.joinToString(" → "))}")
        appendLine("rule\t${redactSensitiveLogText(result.rule)}\t${redactSensitiveLogText(result.rulePayload)}")
        appendLine("probe_error\t${redactSensitiveLogText(result.error)}")
    }
    appendLine("assertion\t${redactSensitiveLogText(session.assertion.orEmpty())}")
    appendLine("[requests]")
    session.requests.forEach { request ->
        appendLine(
            listOf(
                request.metadata.host,
                request.metadata.destinationIp,
                request.metadata.destinationPort,
                request.chains.joinToString(" → "),
                request.rule,
                request.upload.toString(),
                request.download.toString(),
            ).joinToString("\t") { redactSensitiveLogText(it) },
        )
    }
    appendLine("[dns]")
    session.dns.forEach { query ->
        appendLine(
            listOf(query.domain, query.upstream, query.rcode, query.error, query.answers.joinToString(","))
                .joinToString("\t") { redactSensitiveLogText(it) },
        )
    }
    appendLine("[logs]")
    session.logs.forEach { entry ->
        appendLine("${entry.timestampMillis}\t${entry.level}\t${redactSensitiveLogText(entry.payload).replace('\n', ' ')}")
    }
}

private const val MAX_REQUEST_HISTORY = 2_000
private const val MAX_DNS_HISTORY = 1_000
private const val MAX_LOG_HISTORY = 1_000
private const val MAX_DIAGNOSTIC_ITEMS = 100

private fun List<String>.containsIgnoreCase(needle: String): Boolean =
    any { it.contains(needle, ignoreCase = true) }
