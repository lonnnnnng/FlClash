package com.lonnnnnng.flclash

import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*

/** 作者 long：语义拒绝前后使用独立应用UID发真实请求，同时将显式VPN绑定权限诊断与流量验收分开。 */
internal suspend fun semanticVpnTrafficAcceptance(entry: ConnectionAcceptanceEntryPoint, phase: String): JSONObject = coroutineScope {
    require(phase in listOf("before", "after"))
    val ins = InstrumentationRegistry.getInstrumentation()
    val token = InstrumentationRegistry.getArguments().getString("kclashSemanticProbeToken").orEmpty()
    require(token.matches(Regex("[a-f0-9]{32}")))
    val basePort = InstrumentationRegistry.getArguments().getString("kclashSemanticProbePort")?.toInt() ?: 47610
    require(basePort in 30000..59998)
    val sourcePort = basePort + if (phase == "before") 0 else 1
    val binding = NetworkSwitchProbeActivity.bindingSnapshot(ins.targetContext)
    assertEquals(ins.targetContext.applicationInfo.uid, binding.getInt("uid"))
    assertEquals(1, binding.getInt("vpnNetworkCount"))
    val input = JSONObject(checkNotNull(stabilityInput(ins.targetContext).read()).decodeToString())
    assertEquals(token, input.getString("token"))
    fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    suspend fun probe(name: String, bindingOnly: Boolean): JSONObject {
        // 作者 long：仅重建独立验收客户端，绝不force-stop当前Instrumentation、VPN提供者或Flutter参考包。
        shell("am force-stop com.lonnnnnng.flclash.networkprobe")
        val launchedAt = SystemClock.elapsedRealtime()
        val launch = shell("am start -W -n com.lonnnnnng.flclash.networkprobe/com.lonnnnnng.flclash.NetworkSwitchProbeActivity --es token $token --es command $name --ei sourcePort $sourcePort --ez bindingOnly $bindingOnly")
        check(!launch.contains("Error:")) { "独立网络验收入口启动失败" }
        return withTimeout(50_000) {
            while (true) {
                val found = shell("logcat -d -v raw -s KClashSwitchProbe:I '*:S'").lineSequence()
                    .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
                    // 作者 long：同一配置token可用于重复验收；只接收本次启动后的回执，避免旧成功掩盖新失败。
                    .lastOrNull { it.optString("token") == token && it.optString("command") == name &&
                        it.optLong("requestStartedElapsedMs", -1) >= launchedAt }
                if (found != null) return@withTimeout found
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE") error("独立网络验收回执未返回")
        }
    }
    val independentBinding = probe("semantic-bind-$phase", true)
    assertTrue(independentBinding.toString(), independentBinding.getBoolean("passed"))
    assertTrue(independentBinding.toString(), independentBinding.getBoolean("diagnosticOnly"))
    val clientBinding = independentBinding.getJSONObject("binding")
    assertNotEquals(Process.myUid(), clientBinding.getInt("uid"))
    assertEquals(binding.getJSONObject("vpnNetwork").getLong("handle"), clientBinding.getJSONObject("vpnNetwork").getLong("handle"))
    // 作者 long：用相同提供者UID和相同VPN再次绑定，区分持续权限限制与Network已可见但路由尚未发布的启动窗口。
    val providerAfterIndependent = NetworkSwitchProbeActivity.bindingSnapshot(ins.targetContext)
    ins.sendStatus(2, Bundle().apply { putString("kclash_semantic_vpn_binding", JSONObject()
        .put("phase", phase).put("provider", binding).put("independent", clientBinding)
        .put("providerAfterIndependent", providerAfterIndependent).toString()) })

    val observation = async(start = CoroutineStart.UNDISPATCHED) {
        networkSwitchAcceptance(ins.targetContext, entry, input, "switch-arm-semantic-$phase", sourcePort)
    }
    val client = try { probe("semantic-traffic-$phase", false) }
        catch (failure: Throwable) { observation.cancelAndJoin(); throw failure }
    val observed = observation.await()
    val result = JSONObject().put("providerBinding", binding).put("independentBinding", clientBinding)
        .put("providerBindingAfterIndependent", providerAfterIndependent)
        .put("client", client).put("observed", observed)
    // 作者 long：先保留诊断回执再断言，失败批次也能区分绑定权限、DNS和业务握手阶段。
    ins.sendStatus(2, Bundle().apply { putString("kclash_semantic_vpn_traffic", JSONObject()
        .put("phase", phase).put("result", result).toString()) })
    // 作者 long：请求成功必须来自不同UID、默认VPN和同源端口非DIRECT双向流量，不能只检查HTTP状态或VPN图标。
    assertNotEquals(Process.myUid(), client.getInt("uid"))
    assertEquals(clientBinding.getInt("uid"), client.getInt("uid"))
    assertTrue(client.toString(), client.getBoolean("passed"))
    assertEquals(200, client.getInt("http"))
    assertTrue(client.getBoolean("wireDnsPassed"))
    assertTrue(client.getJSONObject("networkBefore").getBoolean("vpn"))
    assertFalse(client.getBoolean("explicitVpnSocketBinding"))
    assertFalse(client.getJSONObject("boundProcessNetwork").getBoolean("present"))
    assertEquals(binding.getJSONObject("vpnNetwork").getLong("handle"), client.getJSONObject("networkBefore").getLong("handle"))
    val connections = observed.getJSONArray("connections")
    assertTrue((0 until connections.length()).any { index -> connections.getJSONObject(index).let {
        it.getInt("sourcePort") == sourcePort && it.getBoolean("targetHostMatches") && it.getBoolean("nonDirect") &&
            it.getLong("upload") > 0 && it.getLong("download") > 0
    } })
    result
}
