package com.lonnnnnng.flclash

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.data.ProxyGroupDrafts
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.VisualMotionTestActivity
import dagger.hilt.android.EntryPointAccessors
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

class SubscriptionProtocolTrafficInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun report(stage: String, vararg fields: Pair<String, Any>) {
        val data = JSONObject().put("stage", stage)
        fields.forEach { (key, value) -> data.put(key, value) }
        instrumentation.sendStatus(2, Bundle().apply { putString("protocolAcceptance", data.toString()) })
    }

    @Test fun importedSubscriptionRoutesThroughEachRequestedProtocol(): Unit = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashProtocolAcceptance") == "true")
        val app = instrumentation.targetContext.applicationContext as KClashApplication
        check(app.packageName == "com.lonnnnnng.flclash.motion")
        val path = checkNotNull(args.getString("privateInput"))
        require(path.matches(Regex("/data/local/tmp/kclash-protocol-[a-f0-9]{32}\\.json")))
        val bytes = instrumentation.uiAutomation.executeShellCommand("cat -- $path").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        check(digest(bytes) == args.getString("privateInputSha"))
        val input = JSONObject(bytes.decodeToString())
        val url = input.getString("subscription")
        val expected = input.getJSONArray("protocols").let { types -> (0 until types.length()).map(types::getString) }
        require(expected.isNotEmpty() && expected.all { it in setOf("trojan", "hysteria2", "vless") })
        require(URI(url).scheme == "https")
        // 作者 long：探测目标使用公开静态文件。订阅地址仅供下载配置，任何节点回执都不携带令牌或节点名称。
        val target = URI("https://www.google.com/robots.txt")
        val entry = EntryPointAccessors.fromApplication(app, ConnectionAcceptanceEntryPoint::class.java)
        app.awaitRuntimeInitialization()
        val repo = entry.profileRepository()
        val database = entry.database()
        val runtime = entry.runtimeManager()
        check(runtime.state.value == CoreRuntimeStatus.STOPPED && database.profiles().getAll().isEmpty())
        val oldMode = runtime.outboundMode.value
        val oldVpn = runtime.vpnSettings.value.enabled
        var phase = "import"
        ActivityScenario.launch(VisualMotionTestActivity::class.java).use {
            try {
                val profile = repo.addRemoteProfile(url, "协议验收")
                check(profile.type == ProfileType.URL && repo.currentProfileId.value == profile.id)
                val file = repo.profileFile(profile.id)
                val original = file.readBytes()
                val root = ProxyGroupDrafts.root(original.decodeToString())
                val nodes = root.getAsJsonArray("proxies").map { it.asJsonObject }
                val byProtocol = nodes.groupBy { it["type"].asString.lowercase() }
                report("import", "profiles" to database.profiles().getAll().size, "sourceBytes" to original.size,
                    "nodeCount" to nodes.size, "counts" to JSONObject(expected.associateWith { byProtocol[it].orEmpty().size }))
                phase = "start"
                runtime.setVpnEnabled(false).getOrThrow()
                runtime.startProxy().getOrThrow()
                check(runtime.state.value == CoreRuntimeStatus.RUNNING)
                runtime.setOutboundMode(CoreOutboundMode.GLOBAL).getOrThrow()
                phase = "traffic"
                var allPassed = true
                for (protocol in expected) {
                    val endpoints = byProtocol[protocol].orEmpty().distinctBy { it["server"].asString to it["port"].asInt }
                    var successes = 0
                    val wanted = minOf(2, endpoints.size)
                    // 作者 long：同端口的别名不重复计数；先按原序抽样，两处成功即结束，最多核对四个不同端点。
                    for ((index, node) in endpoints.take(4).withIndex()) {
                        val name = node["name"].asString
                        val selection = runtime.changeProxy("GLOBAL", name).getOrThrow()
                        check(selection.groups.single { it.name == "GLOBAL" }.now == name)
                        val result = acceptedProxyRequest(entry, target) { _, _ -> }
                        val chains = result.optJSONArray("chainHashes")
                        val matched = chains != null && (0 until chains.length()).any { chains.getString(it) == digest(name.toByteArray()) }
                        val passed = result.optBoolean("passed") && result.optInt("http") == 200 && matched &&
                            result.optBoolean("attributedSocket") && result.optBoolean("positiveTraffic")
                        report("probe", "protocol" to protocol, "endpointOrdinal" to index + 1, "passed" to passed,
                            "http" to result.optInt("http"), "leafMatched" to matched, "sameSocket" to result.optBoolean("attributedSocket"),
                            "positiveTraffic" to result.optBoolean("positiveTraffic"), "failedStep" to result.optString("failedStep"))
                        if (passed && ++successes == wanted) break
                    }
                    allPassed = allPassed && wanted > 0 && successes == wanted
                }
                check(original.contentEquals(file.readBytes()))
                check(allPassed) { "部分协议未达实流验收条件" }
            } catch (failure: Exception) {
                report("failure", "phase" to phase, "category" to failure.javaClass.simpleName)
                throw AssertionError("验收失败于 $phase（${failure.javaClass.simpleName}）")
            } finally {
                withContext(NonCancellable) {
                    runtime.stop().getOrThrow()
                    runtime.setOutboundMode(oldMode).getOrThrow()
                    runtime.setVpnEnabled(oldVpn).getOrThrow()
                    database.profiles().getAll().singleOrNull { it.url == url }?.let { row ->
                        repo.deleteProfile(row.id)
                        check(!repo.profileFile(row.id).exists())
                    }
                    check(database.profiles().getAll().isEmpty() && repo.currentProfileId.value == null && runtime.state.value == CoreRuntimeStatus.STOPPED)
                    report("cleanup", "empty" to true, "stopped" to true)
                }
            }
        }
    }
}
