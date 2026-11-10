package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
        val ready = CountDownLatch(1)
        val collectorFailure = AtomicReference<Throwable?>()
        var start = 0L
        val period = 50_000_000L
        // 作者 long：同步触点注入会阻塞测试线程，独立采集线程须先取得基线，才能覆盖菜单首开最早的50/100ms。
        val collector = Thread({
            try {
                start = System.nanoTime()
                snapshots.put(snapshot(start))
                ready.countDown()
                var target = 1L
                while (target <= 16L) {
                    val remaining = start + target * period - System.nanoTime()
                    if (remaining > 0) SystemClock.sleep((remaining + 999_999L) / 1_000_000L)
                    val sample = snapshot(start).put("scheduledNs", target * period)
                    snapshots.put(sample)
                    // 作者 long：调度落后时跳过错过的时隙，不能密集补采制造虚假的50ms覆盖；实际边界和缺口供报告核对。
                    target = maxOf(target + 1L, (System.nanoTime() - start) / period + 1L)
                }
            } catch (failure: Throwable) {
                collectorFailure.set(failure)
                ready.countDown()
            }
        }, "menu-counter-sampler")
        collector.start()
        check(ready.await(2, TimeUnit.SECONDS)) { "线程采集基线超时" }
        collectorFailure.get()?.let { throw it }
        val dispatch = System.nanoTime()
        val actionResult = runCatching(action)
        val dispatched = System.nanoTime()
        collector.join(2_000)
        check(!collector.isAlive) { "线程采集未按预算结束" }
        collectorFailure.get()?.let { throw it }
        val report = JSONObject().put("name", name).put("pid", pid).put("ticksPerSecond", ticksPerSecond)
            .put("version", 2).put("sampleStartUptimeNs", start).put("samplingPeriodNs", period)
            .put("samplingMode", "independent-thread").put("actionSucceeded", actionResult.isSuccess)
            .put("dispatchStartNs", dispatch - start).put("dispatchEndNs", dispatched - start)
            .put("renderThreadFound", threads.any { it.second == "RenderThread" }).put("snapshots", snapshots)
            .put("scope", "Own-process cumulative counters sampled independently of input dispatch; actual read boundaries, no native stacks, Popup-only attribution or presentation timestamps.")
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("kclash_thread_cpu", report.toString())
        })
        actionResult.getOrThrow()
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
