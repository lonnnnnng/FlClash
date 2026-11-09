package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** 作者 long：只读自身线程计数绕开跨进程采样权限；独立诊断不与正式包帧率合计，也不声称得到原生调用栈。 */
internal class MenuThreadCpuProbe {
    private val ticksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK)
    private val pid = android.os.Process.myPid()
    private val threads = File("/proc/self/task").listFiles().orEmpty().mapNotNull { directory ->
        val tid = directory.name.toIntOrNull() ?: return@mapNotNull null
        val name = runCatching { File(directory, "comm").readText().trim() }.getOrNull()
        if (tid == pid || name == "RenderThread") tid to (if (tid == pid) "main" else "RenderThread") else null
    }

    fun sample(name: String, action: () -> Unit) {
        val snapshots = JSONArray()
        val start = System.nanoTime()
        snapshots.put(snapshot(start))
        val dispatch = System.nanoTime()
        action()
        val dispatched = System.nanoTime()
        // 作者 long：计划每100ms只读预先发现的主线程/同名渲染线程；注入可能阻塞，实际时刻和读耗时必须原样保留。
        for (target in 1..8) {
            val remaining = start + target * 100_000_000L - System.nanoTime()
            if (remaining > 0) SystemClock.sleep((remaining + 999_999L) / 1_000_000L)
            snapshots.put(snapshot(start))
        }
        val report = JSONObject().put("name", name).put("pid", pid).put("ticksPerSecond", ticksPerSecond)
            .put("dispatchStartNs", dispatch - start).put("dispatchEndNs", dispatched - start)
            .put("renderThreadFound", threads.any { it.second == "RenderThread" }).put("snapshots", snapshots)
            .put("scope", "Own-process cumulative thread CPU and scheduler counters; 100ms buckets, no native stacks, no Popup-only attribution or actual presentation timestamps.")
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("kclash_thread_cpu", report.toString())
        })
    }

    private fun snapshot(start: Long): JSONObject {
        val before = System.nanoTime()
        val values = JSONArray()
        threads.forEach { (tid, name) ->
            val item = JSONObject().put("tid", tid).put("name", name)
            runCatching {
                val raw = File("/proc/self/task/$tid/stat").readText()
                // 作者 long：comm允许空格和括号，必须从最后一个右括号之后按字段号解析，不能把线程名当作固定词数。
                val fields = raw.substring(raw.lastIndexOf(')') + 2).trim().split(Regex("\\s+"))
                item.put("userTicks", fields[11].toLong()).put("systemTicks", fields[12].toLong())
                    .put("startTicks", fields[19].toLong())
            }.onFailure { item.put("statError", it.javaClass.simpleName) }
            runCatching {
                val fields = File("/proc/self/task/$tid/schedstat").readText().trim().split(Regex("\\s+"))
                item.put("runNs", fields[0].toLong()).put("waitNs", fields[1].toLong()).put("slices", fields[2].toLong())
            }.onFailure { item.put("schedstatError", it.javaClass.simpleName) }
            values.put(item)
        }
        return JSONObject().put("readStartNs", before - start).put("readEndNs", System.nanoTime() - start).put("threads", values)
    }
}
