package com.lonnnnnng.flclash.service

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.ErrnoException
import android.system.OsConstants
import com.lonnnnnng.flclash.service.models.VpnOptions
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** 作者 long：仅独立motion验收显式布置；修改原生调用的参数副本，系统异常和Core返回值必须来自真实实现。 */
object TunStartBoundary {
    enum class Fault { ESTABLISH_MTU, CORE_CONGESTION }
    private val lock = Any()
    private var plan: List<Fault>? = null
    private var visits = 0
    private val records = mutableListOf<JSONObject>()

    fun arm(packageName: String, explicitlyEnabled: Boolean, faults: List<Fault>) = synchronized(lock) {
        check(packageName == "com.lonnnnnng.flclash.motion" && explicitlyEnabled)
        check(plan == null && faults.size in 1..2)
        plan = faults.toList(); visits = 0; records.clear()
    }

    fun reset() = synchronized(lock) { plan = null; visits = 0; records.clear() }
    fun snapshot(): JSONObject = synchronized(lock) {
        JSONObject().put("visits", visits).put("planned", plan?.size ?: 0)
            .put("attempts", JSONArray(records.map { JSONObject(it.toString()) }))
    }

    fun begin(packageName: String, options: VpnOptions): Attempt = synchronized(lock) {
        val faults = plan
        if (faults == null) return Attempt(null, null)
        check(packageName == "com.lonnnnnng.flclash.motion")
        val fault = faults.getOrNull(visits)
        val record = JSONObject().put("attempt", ++visits).put("fault", fault?.name ?: "NONE")
            .put("at", SystemClock.elapsedRealtime()).put("mtu", options.mtu).put("stack", options.stack)
        check(records.size < 16) { "TUN验收启动次数超出预算" }
        records += record
        Attempt(fault, record)
    }

    private fun fdTarget(fd: Int): String = try {
        val target = Os.readlink("/proc/self/fd/$fd")
        if (target.contains("tun")) "TUN" else "OTHER"
    } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) "ABSENT" else "UNREADABLE"
    }

    fun tunFds(): JSONObject {
        val entries = checkNotNull(File("/proc/self/fd").list()) { "FD目录不可读" }
        val states = entries.mapNotNull(String::toIntOrNull).associateWith(::fdTarget)
        return JSONObject().put("tunCount", states.values.count { it == "TUN" })
            .put("unreadableCount", states.values.count { it == "UNREADABLE" })
    }

    class Attempt internal constructor(private val fault: Fault?, private val record: JSONObject?) {
        private fun put(key: String, value: Any) = synchronized(lock) { record?.put(key, value); Unit }

        fun establish(builder: VpnService.Builder): ParcelFileDescriptor? {
            if (record == null) return builder.establish()
            put("beforeEstablishFds", tunFds())
            // 作者 long：绕开验收副本的MTU钳制，真实Binder/system_server负责拒绝；不在测试侧伪抛系统错误。
            if (fault == Fault.ESTABLISH_MTU) builder.setMtu(65536)
            put("establishCalled", true)
            return try {
                builder.establish().also { descriptor ->
                    put("establishReturnedFd", descriptor != null)
                    if (descriptor != null) put("establishedFdKind", fdTarget(descriptor.fd))
                }
            } catch (failure: Exception) {
                put("establishException", failure.javaClass.simpleName)
                put("establishExceptionFrames", JSONArray(failure.stackTrace.take(12).map { "${it.className}.${it.methodName}" }))
                put("afterEstablishFailureFds", tunFds())
                throw failure
            }
        }

        fun startCore(fd: Int, stack: String, options: String, call: (String, String) -> Boolean): Boolean {
            if (record == null) return call(stack, options)
            put("coreFdKindBefore", fdTarget(fd)); put("beforeCoreFds", tunFds())
            val effectiveOptions = if (fault == Fault.CORE_CONGESTION) JSONObject(options)
                .put("congestion-controller", "kclash-tun-invalid-congestion").toString() else options
            val effectiveStack = if (fault == Fault.CORE_CONGESTION) "mips" else stack
            put("coreCalled", true)
            // 作者 long：Core接收后负责消费原FD和内部副本；这里只读检查，不再adopt/close，避免FD编号复用时误关其他资源。
            return call(effectiveStack, effectiveOptions).also { result ->
                put("coreAccepted", result); put("coreFdKindAfter", fdTarget(fd)); put("afterCoreFds", tunFds())
            }
        }
    }
}
