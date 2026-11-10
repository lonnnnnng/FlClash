package com.lonnnnnng.flclash.data

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import com.google.gson.Gson
import com.lonnnnnng.flclash.common.StateWriteAudit
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import androidx.sqlite.db.SupportSQLiteOpenHelper

/** 作者 long：审计仅显式motion会话启用，私有随机HMAC密钥跨进程保留，不可用公开摘要枚举低熵凭据。 */
object StateAuditPlatform {
    fun factory(): SupportSQLiteOpenHelper.Factory = AuditedSQLiteFactory()
    private fun config(context: Context) = File(context.noBackupFilesDir, "state-audit-session.json")
    fun initialize(context: Context) {
        if (context.packageName != "com.lonnnnnng.flclash.motion") return
        runCatching {
            val file = config(context)
            if (!file.isFile) return
            val data = JSONObject(file.readText())
            check(data.getString("token").matches(Regex("[a-f0-9]{32}")))
            if (System.currentTimeMillis() > data.getLong("expiresAt")) return
            val key = android.util.Base64.decode(data.getString("key"), android.util.Base64.NO_WRAP)
            check(key.size == 32)
            val directory = File(context.noBackupFilesDir, "state-audit/${data.getString("token")}").apply { mkdirs() }
            StateWriteAudit.install(FileSink(directory, key))
            StateWriteAudit.event("process_started", mapOf("session" to data.getString("token")))
        }
    }
    fun enable(context: Context, token: String) {
        check(context.packageName == "com.lonnnnnng.flclash.motion" && token.matches(Regex("[a-f0-9]{32}")))
        check(!config(context).exists())
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val data = JSONObject().put("token", token).put("expiresAt", System.currentTimeMillis() + 8 * 60 * 60 * 1000)
            .put("key", android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)).toString().toByteArray()
        val atomic = AtomicFile(config(context)); val stream = atomic.startWrite()
        try { stream.write(data); atomic.finishWrite(stream) } catch (failure: Throwable) { atomic.failWrite(stream); throw failure }
        initialize(context)
    }
    fun records(context: Context, token: String, allowIncomplete: Boolean = false): List<File> {
        check(context.packageName == "com.lonnnnnng.flclash.motion" && token.matches(Regex("[a-f0-9]{32}")))
        check(JSONObject(config(context).readText()).getString("token") == token)
        val files = File(context.noBackupFilesDir, "state-audit/$token").listFiles().orEmpty()
        check(allowIncomplete || files.none { it.name.endsWith(".overflow") }) { "审计存在丢失标记，不得记作完整窗口" }
        return files
            .filter { it.name.matches(Regex("[0-9]+-[0-9]+\\.jsonl(?:\\.gz)?")) }.sortedBy { it.name }
    }
    fun disable(context: Context, token: String) {
        check(context.packageName == "com.lonnnnnng.flclash.motion" && token.matches(Regex("[a-f0-9]{32}")))
        check(JSONObject(config(context).readText()).getString("token") == token)
        // 作者 long：丢记录是证据不完整，不能反过来阻止关闭诊断；只核对本会话配置身份，不要求导出成功。
        StateWriteAudit.install(null)
        check(config(context).delete())
    }
    private class FileSink(directory: File, private val key: ByteArray) : StateWriteAudit.Sink {
        private val file = File(directory, "${Process.myPid()}-${SystemClock.elapsedRealtime()}.jsonl.gz")
        private val stackIds = mutableMapOf<List<*>, Int>()
        private val sequence = AtomicLong(); private val dropped = AtomicLong(); private val gson = Gson()
        override fun digest(bytes: ByteArray): String = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            .doFinal(bytes).joinToString("") { "%02x".format(it) }
        override fun dropped() {
            dropped.incrementAndGet()
            runCatching { File(file.parentFile, file.name + ".overflow").writeText("incomplete") }
        }
        override fun status(): Map<String, Any?> = mapOf("pid" to Process.myPid(), "sequence" to sequence.get(), "dropped" to dropped.get(), "bytes" to file.length())
        @Synchronized override fun append(record: Map<String, Any?>) {
            val lines = mutableListOf<Map<String, Any?>>()
            val normalized = record.toMutableMap()
            for (field in listOf("sites", "originSites")) {
                val sites = normalized.remove(field) as? List<*> ?: continue
                val id = stackIds.getOrPut(sites) {
                    val next = stackIds.size + 1
                    lines += mapOf("kind" to "stack_dictionary", "stackId" to next, "sites" to sites)
                    next
                }
                normalized[field + "Id"] = id
            }
            lines += normalized
            val raw = lines.map { row -> gson.toJson(row + mapOf("formatVersion" to 2, "sequence" to sequence.incrementAndGet(), "pid" to Process.myPid(),
                "elapsedMs" to SystemClock.elapsedRealtime(), "epochMs" to System.currentTimeMillis(), "dropped" to dropped.get())).toByteArray() }
            if (raw.any { it.size > 65536 }) { dropped(); return }
            val buffer = java.io.ByteArrayOutputStream()
            // 作者 long：每条记录独立gzip成员，SIGKILL不会让此前记录因缺少整个文件的压缩尾部而无法读取。
            raw.forEach { bytes -> java.util.zip.GZIPOutputStream(buffer).use { zip -> zip.write(bytes); zip.write(10) } }
            val data = buffer.toByteArray()
            if (data.size > 65536) { dropped(); return }
            // 作者 long：同步写入不等于断电持久保证；进程死亡验收读实际残留文件，不能把内存队列当证据。
            java.io.RandomAccessFile(File(file.parentFile, "budget.lock"), "rw").use { budget -> budget.channel.lock().use {
                // 作者 long：新旧进程短暂重叠时也共用配额，不能分别领取相同剩余字节导致总限额失效。
                val total = file.parentFile!!.listFiles().orEmpty().filter { it.extension in setOf("jsonl", "gz") }.sumOf { it.length() }
                if (file.length() + data.size > 128L * 1024 * 1024 || total + data.size > 512L * 1024 * 1024) { dropped(); return }
                java.io.FileOutputStream(file, true).use { stream ->
                    stream.write(data)
                    if (record["kind"] in setOf("db_committed", "db_rolled_back", "file_completed")) stream.fd.sync()
                }
            }
            }
        }
    }
}
