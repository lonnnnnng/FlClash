package com.lonnnnnng.flclash

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.SystemClock
import com.lonnnnnng.flclash.service.ServiceConfig
import com.google.gson.Gson
import com.lonnnnnng.flclash.core.Core
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.NetworkObserver
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** 作者 long：仅独立motion包验收，网络切换过程中只观察原会话，不用restart或清DNS掩盖自动恢复失败。 */
internal suspend fun networkSwitchAcceptance(context: Context, entry: ConnectionAcceptanceEntryPoint,
    input: JSONObject, command: String, sourcePort: Int): JSONObject {
    check(context.packageName == "com.lonnnnnng.flclash.motion")
    val runtime = entry.runtimeManager()
    val repo = entry.profileRepository()
    when {
        command.startsWith("switch-start") -> {
            check(runtime.state.value == CoreRuntimeStatus.STOPPED)
            check(VpnService.prepare(context) == null) { "VPN permission required" }
            val row = entry.database().profiles().getAll().single { it.type == ProfileType.FILE }
            check(stabilitySha(repo.profileFile(row.id).readBytes()) == input.getString("localSha256"))
            runtime.selectProfile(row.id, repo::selectProfile)
            runtime.setVpnEnabled(true).getOrThrow()
            runtime.startProxy().getOrThrow()
        }
        command.startsWith("switch-stop") -> runtime.stop().getOrThrow()
        command == "switch-disable-bypass" -> {
            check(runtime.state.value == CoreRuntimeStatus.STOPPED)
            val old = entry.networkSettingsStore().read()
            input.put("switchOriginalNetworkSettings", Gson().toJson(old))
            stabilityInput(context).write(input.toString().toByteArray())
            runtime.updateNetworkSettings(old, old.copy(allowBypass = false)).getOrThrow()
        }
        command == "switch-restore-settings" -> {
            check(runtime.state.value == CoreRuntimeStatus.STOPPED)
            if (input.has("switchOriginalNetworkSettings")) {
                val old = Gson().fromJson(input.getString("switchOriginalNetworkSettings"), com.lonnnnnng.flclash.service.NetworkSettings::class.java)
                runtime.updateNetworkSettings(entry.networkSettingsStore().read(), old).getOrThrow()
                input.remove("switchOriginalNetworkSettings")
                stabilityInput(context).write(input.toString().toByteArray())
            }
        }
    }
    val result = JSONObject()
    if (command.startsWith("switch-socks-")) {
        val request = acceptedProxyRequest(entry, URI(input.getString("authorizedUrl"))) { _, _ -> }
        result.put("socksRequest", request)
    }
    if (command.startsWith("switch-arm-")) {
        check(runtime.state.value == CoreRuntimeStatus.RUNNING && ServiceConfig.vpnAlive.value)
        require(sourcePort in 30000..60000)
        val host = URI(input.getString("authorizedUrl")).host
        val oldIds = entry.coreBridge().getConnections().map { it.id }.toSet()
        val observed = JSONArray()
        val started = SystemClock.elapsedRealtime()
        Core.beginDialDiagnostic(sourcePort)
        try {
            // 作者 long：独立请求端固定源端口；必须看到新连接的双向正流量和非DIRECT链，不能拿VPN图标代替业务成功。
            while (SystemClock.elapsedRealtime() - started < 35_000) {
                val matches = entry.coreBridge().getConnections().filter {
                    it.id !in oldIds && it.metadata.sourcePort == sourcePort.toString() &&
                        it.metadata.destinationPort == "443" && it.metadata.network.equals("tcp", true) &&
                        it.metadata.host.equals(host, true)
                }
                matches.forEach { connection ->
                    observed.put(JSONObject().put("idHash", stabilitySha(connection.id.toByteArray()))
                        .put("sourcePort", sourcePort).put("upload", connection.upload).put("download", connection.download)
                        .put("hostHash", stabilitySha(connection.metadata.host.toByteArray()))
                        .put("targetHostMatches", connection.metadata.host.equals(host, true))
                        .put("nonDirect", connection.chains.isNotEmpty() && connection.chains.none { it == "DIRECT" })
                        .put("chainHashes", JSONArray(connection.chains.map { stabilitySha(it.toByteArray()) })))
                }
                if (matches.any { it.upload > 0 && it.download > 0 && it.chains.isNotEmpty() && it.chains.none { chain -> chain == "DIRECT" } }) break
                delay(100)
            }
        } finally {
            result.put("sourcePort", sourcePort).put("connections", observed)
                .put("dialDiagnostic", JSONArray(Core.dialDiagnosticSnapshot(sourcePort, true)))
                .put("watchMillis", SystemClock.elapsedRealtime() - started)
        }
    }
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val networks = connectivity.allNetworks.map { network ->
        val caps = connectivity.getNetworkCapabilities(network)
        val links = connectivity.getLinkProperties(network)
        JSONObject().put("handle", network.networkHandle)
            .put("wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
            .put("cellular", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
            .put("vpn", caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
            .put("internet", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            .put("validated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            .put("dnsHashes", JSONArray(links?.dnsServers.orEmpty().map { stabilitySha(it.address) }))
    }
    return result.put("runtime", runtime.state.value.name).put("vpnAlive", ServiceConfig.vpnAlive.value)
        .put("allowBypass", entry.networkSettingsStore().read().allowBypass)
        .put("runningSinceElapsedMs", runtime.runningSinceElapsedMillis.value)
        .put("observer", JSONObject(Gson().toJson(NetworkObserver.snapshot())))
        .put("coreBootstrap", JSONObject(Core.networkBootstrapSnapshot())).put("networks", JSONArray(networks))
}
