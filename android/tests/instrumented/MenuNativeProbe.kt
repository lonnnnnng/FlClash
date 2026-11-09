package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** 作者 long：仅自有UID调用系统已标记simpleperf二进制，不写全局perf属性，不复制可执行文件或绕过ROM安全限制。 */
internal class MenuNativeProbe {
    private val ins = InstrumentationRegistry.getInstrumentation()
    fun record(run: String, targetPid: Int, action: () -> Unit) {
        require(targetPid > 0)
        val directory = File(ins.targetContext.cacheDir, "native-$run")
        check(!directory.exists() && directory.mkdir())
        val output = File(directory, "close.data")
        val log = File(directory, "stderr.txt")
        val args = listOf("/system/bin/simpleperf", "record", "--in-app", "--stdio-controls-profiling",
            "-p", targetPid.toString(), "-e", "cpu-cycles:u", "-f", "200",
            "--call-graph", "dwarf,8192", "-m", "8", "--user-buffer-size", "4M", "--size-limit", "8M",
            "--duration", "3", "--no-dump-kernel-symbols", "-o", output.absolutePath)
        val report = JSONObject().put("run", run).put("targetPid", targetPid).put("collectorPid", android.os.Process.myPid())
            .put("targetCmdline", runCatching { File("/proc/$targetPid/cmdline").readBytes().takeWhile { it != 0.toByte() }.toByteArray().decodeToString() }.getOrNull())
            .put("args", org.json.JSONArray(args))
        val reader = Executors.newSingleThreadExecutor()
        var process: Process? = null
        try {
            process = ProcessBuilder(args).directory(directory).redirectError(log).start()
            val child = process
            val reply = reader.submit<String> { child.inputStream.bufferedReader().readLine().orEmpty() }
                .get(5, TimeUnit.SECONDS)
            report.put("startReply", reply)
            if (reply == "started") {
                report.put("actionStartNs", System.nanoTime())
                action()
                report.put("actionEndNs", System.nanoTime())
            }
            check(child.waitFor(6, TimeUnit.SECONDS)) { "simpleperf未在预算内退出" }
            report.put("exitCode", child.exitValue()).put("recorded", reply == "started" && child.exitValue() == 0 && output.length() > 0)
        } catch (failure: Exception) {
            report.put("type", failure.javaClass.simpleName).put("message", failure.message.orEmpty())
        } finally {
            if (process?.isAlive == true) { process.destroy(); if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly() }
            reader.shutdownNow()
            report.put("stderr", if (log.exists()) log.readText().take(12_000) else "")
            ins.sendStatus(2, Bundle().apply { putString("kclash_native_probe", report.toString()) })
            if (output.exists()) send(output)
            if (log.exists()) { check(log.delete()) }
            check(directory.listFiles().orEmpty().isEmpty()); check(directory.delete())
        }
    }

    private fun send(file: File) {
        val bytes = file.readBytes()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val chunks = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP).chunked(32 * 1024)
        chunks.forEachIndexed { index, base64 -> ins.sendStatus(2, Bundle().apply {
            putString("kclash_native_chunk", JSONObject().put("name", file.name).put("index", index).put("count", chunks.size)
                .put("bytes", bytes.size).put("sha256", sha).put("base64", base64).toString())
        }) }
        check(file.readBytes().contentEquals(bytes)); check(file.delete())
    }
}
