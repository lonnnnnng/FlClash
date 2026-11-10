package com.lonnnnnng.flclash.common

import java.io.File
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** 作者 long：正式运行默认无接收器；显式诊断会话才记录摘要，审计失败不能改变业务写入结果。 */
object StateWriteAudit {
    interface Sink {
        fun digest(bytes: ByteArray): String
        fun append(record: Map<String, Any?>)
        fun dropped()
        fun status(): Map<String, Any?> = emptyMap()
    }
    @Volatile var sink: Sink? = null
        private set
    fun install(value: Sink?) { sink = value }
    val enabled get() = sink != null
    private val current = ThreadLocal<Origin?>()
    data class Origin(val id: String, val source: String, val sites: List<String>)
    private class Context(val origin: Origin) : ThreadContextElement<Origin?>, AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Context>
        override fun updateThreadContext(context: CoroutineContext): Origin? = current.get().also { current.set(origin) }
        override fun restoreThreadContext(context: CoroutineContext, oldState: Origin?) { current.set(oldState) }
    }
    fun digest(bytes: ByteArray): String? = try { sink?.digest(bytes) } catch (_: Exception) {
        runCatching { sink?.dropped() }; null
    }
    fun value(value: Any?): Map<String, Any?> {
        if (!enabled) return emptyMap()
        val bytes = when (value) {
            null -> byteArrayOf()
            is ByteArray -> value
            is Set<*> -> value.map { it.toString() }.sorted().joinToString("\u0000").toByteArray()
            else -> value.toString().toByteArray()
        }
        return mapOf("kind" to (value?.javaClass?.simpleName ?: "null"), "bytes" to bytes.size, "digest" to digest(bytes))
    }
    fun event(kind: String, detail: Map<String, Any?> = emptyMap()) {
        val target = sink ?: return
        try {
            val origin = current.get()
            val sites = callSites()
            target.append(detail + mapOf("kind" to kind, "operation" to origin?.id, "source" to origin?.source,
                "originSites" to origin?.sites, "sites" to sites, "threadId" to Thread.currentThread().id))
        } catch (_: Exception) { runCatching { target.dropped() } }
    }
    private fun callSites() = Throwable().stackTrace.asSequence().filter {
                it.className.startsWith("com.lonnnnnng.flclash.") && !it.className.contains("StateWriteAudit") &&
                    !it.className.contains("AuditedSQLite") && !it.className.contains("AuditedPreferences")
            }.take(6).map { "${it.className}.${it.methodName}:${it.lineNumber}" }.toList()
    suspend fun <T> operation(source: String, block: suspend () -> T): T {
        if (!enabled || current.get() != null) return block()
        require(source.matches(Regex("[a-zA-Z0-9_.-]{1,100}")))
        val origin = Origin(UUID.randomUUID().toString(), source, callSites())
        return withContext(Context(origin)) {
            event("operation_accepted")
            try { block().also { event("operation_completed") } }
            catch (failure: Throwable) {
                event("operation_failed", mapOf("failureType" to failure.javaClass.simpleName)); throw failure
            }
        }
    }
    fun fileState(file: File): Map<String, Any?> = if (!enabled) emptyMap() else runCatching {
        mapOf("path" to digest(file.absolutePath.toByteArray()), "exists" to file.exists(), "bytes" to file.length(),
            "mtime" to file.lastModified(), "content" to if (file.isFile) digest(file.readBytes()) else null)
    }.getOrElse { mapOf("snapshotFailure" to it.javaClass.simpleName) }
    fun <T> fileMutation(file: File, reason: String, block: () -> T): T {
        if (!enabled) return block()
        val id = UUID.randomUUID().toString(); val before = fileState(file)
        event("file_accepted", mapOf("write" to id, "reason" to reason, "before" to before))
        try { return block().also { event("file_completed", mapOf("write" to id, "reason" to reason, "before" to before, "after" to fileState(file))) } }
        catch (failure: Throwable) {
            event("file_failed", mapOf("write" to id, "reason" to reason, "before" to before, "after" to fileState(file), "failureType" to failure.javaClass.simpleName)); throw failure
        }
    }
}

// 作者 long：保留原IO调度和取消语义，只把业务来源随协程传到Room真正执行写入的线程。
suspend fun <T> auditedStateIo(source: String, block: suspend CoroutineScope.() -> T): T =
    StateWriteAudit.operation(source) { withContext(Dispatchers.IO, block) }
