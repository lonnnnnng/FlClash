package com.lonnnnnng.flclash

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.core.CoreOutboundMode
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.*
import java.net.InetAddress
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** 作者 long：固定原节点的解析地址只用于motion因果对照，原server/WS身份与授权请求不改；原设置先加密保存才能触碰Hosts。 */
internal suspend fun endpointControl(context: Context, entry: ConnectionAcceptanceEntryPoint, input: JSONObject, command: String): JSONObject {
    check(context.packageName == "com.lonnnnnng.flclash.motion")
    val runtime = entry.runtimeManager(); val repo = entry.profileRepository(); val gson = Gson()
    if (command == "endpoint-lock") {
        check(runtime.state.value == CoreRuntimeStatus.RUNNING && !ServiceConfig.vpnAlive.value)
        val state = input.getJSONObject("endpointControl")
        check(runtime.outboundMode.value == CoreOutboundMode.GLOBAL)
        // 作者 long：只固定域名不固定叶节点时自动组仍会换到别的server；GLOBAL仅用于独立因果对照，不冒充原RULE路由验收。
        val groups = runtime.changeProxy("GLOBAL", state.getString("node")).getOrThrow()
        check(groups.groups.single { it.name == "GLOBAL" }.now == state.getString("node"))
        return JSONObject().put("leafFixed", true).put("nodeHash", stabilitySha(state.getString("node").toByteArray()))
            .put("diagnosticMode", "GLOBAL")
    }
    check(runtime.state.value == CoreRuntimeStatus.STOPPED && !ServiceConfig.proxyAlive.value && !ServiceConfig.vpnAlive.value)
    val localId = input.optLong("localProfileId", -1L)
    val local = entry.database().profiles().getAll().single {
        it.type == ProfileType.FILE && if (localId > 0) it.id == localId
        else stabilitySha(repo.profileFile(it.id).readBytes()) == input.getString("localSha256")
    }
    val result = JSONObject()
    val root = JsonParser.parseString(YamlConfigCodec.toJson(repo.profileFile(local.id).readText())).asJsonObject
    val nodes = root.getAsJsonArray("proxies")
    val protocolCounts = JSONObject().apply {
        nodes.map { it.asJsonObject.get("type").asString }.groupingBy { it }.eachCount().forEach { (type, count) -> put(type, count) }
    }
    suspend fun targetState(nodeIndex: Int): JSONObject {
        require(nodeIndex in 0 until nodes.size())
        val node = nodes[nodeIndex].asJsonObject
        check(node.get("type").asString in setOf("vless", "vmess"))
        val host = node.get("server").asString
        val addresses = withTimeout(15_000) {
            kotlinx.coroutines.runInterruptible {
                InetAddress.getAllByName(host).map { checkNotNull(it.hostAddress) }.distinct().sorted().take(64)
            }
        }
        check(addresses.isNotEmpty())
        return JSONObject().put("nodeIndex", nodeIndex).put("host", host).put("port", node.get("port").asInt)
            .put("node", node.get("name").asString).put("addresses", JSONArray(addresses))
            .put("nodeType", node.get("type").asString).put("nodeNetwork", node.get("network")?.asString ?: "tcp")
    }
    fun targetReport(target: JSONObject): JSONObject {
        val host = target.getString("host")
        val port = target.getInt("port")
        val addresses = target.getJSONArray("addresses")
        return JSONObject().put("nodeIndex", target.getInt("nodeIndex"))
            .put("nodeHostHash", stabilitySha(host.toByteArray())).put("candidateCount", addresses.length())
            .put("nodeHash", stabilitySha(target.getString("node").toByteArray()))
            .put("nodeType", target.getString("nodeType")).put("nodeNetwork", target.getString("nodeNetwork"))
            .put("candidateEndpointHashes", JSONArray((0 until addresses.length()).map {
                stabilitySha("${addresses.getString(it)}:$port".toByteArray())
            }))
    }
    when {
        command == "endpoint-prepare" || Regex("endpoint-prepare-[0-9]{1,2}").matches(command) -> {
            check(!input.has("endpointControl"))
            val nodeIndex = command.removePrefix("endpoint-prepare").removePrefix("-").toIntOrNull() ?: 0
            val target = targetState(nodeIndex)
            val config = entry.paths().configFile
            val state = JSONObject().put("general", gson.toJson(entry.generalSettingsStore().read()))
                .put("selected", checkNotNull(repo.currentProfileId.value))
                .put("vpn", entry.vpnSettingsStore().read()).put("mode", runtime.outboundMode.value.name)
                .put("configPresent", config.isFile).put("configMtime", config.lastModified())
                .put("sourceSha256", input.getString("localSha256")).put("proxyCount", nodes.size())
                .put("protocolCounts", protocolCounts)
            target.keys().forEach { state.put(it, target.get(it)) }
            if (config.isFile) state.put("configBase64", Base64.encodeToString(config.readBytes(), Base64.NO_WRAP))
            // 作者 long：备份含节点域名/IP，继续使用既有Keystore密文输入，外部回执只输出单向摘要。
            input.put("endpointControl", state)
            stabilityInput(context).write(input.toString().toByteArray())
            result.put("proxyCount", nodes.size()).put("protocolCounts", protocolCounts)
                .put("target", targetReport(target)).put("resolver", "android-system").put("backupPersisted", true)
        }
        command.startsWith("endpoint-select-") -> {
            val match = checkNotNull(Regex("endpoint-select-([0-9]{1,2})").matchEntire(command))
            val state = input.getJSONObject("endpointControl")
            check(state.getString("sourceSha256") == input.getString("localSha256"))
            val target = targetState(match.groupValues[1].toInt())
            // 作者 long：节点切换只替换本轮目标字段，首次备份始终不变，任何失败都能回到同一原始状态。
            target.keys().forEach { state.put(it, target.get(it)) }
            stabilityInput(context).write(input.toString().toByteArray())
            result.put("target", targetReport(target)).put("proxyCount", state.getInt("proxyCount"))
                .put("protocolCounts", state.getJSONObject("protocolCounts")).put("backupPreserved", true)
        }
        command == "endpoint-restore" -> {
            val state = input.getJSONObject("endpointControl")
            check(state.getString("sourceSha256") == input.getString("localSha256"))
            val original = gson.fromJson(state.getString("general"), GeneralSettings::class.java)
            runtime.updateGeneralSettings(entry.generalSettingsStore().read(), original).getOrThrow()
            val selected = state.getLong("selected")
            runtime.selectProfile(selected, repo::selectProfile)
            runtime.setVpnEnabled(state.getBoolean("vpn")).getOrThrow()
            runtime.setOutboundMode(CoreOutboundMode.valueOf(state.getString("mode"))).getOrThrow()
            check(entry.generalSettingsStore().read() == original && repo.currentProfileId.value == selected)
            val config = entry.paths().configFile
            if (state.getBoolean("configPresent")) {
                config.writeBytes(Base64.decode(state.getString("configBase64"), Base64.NO_WRAP))
                check(config.setLastModified(state.getLong("configMtime")))
            } else if (config.exists()) check(config.delete())
            val temporaryId = input.optLong("localProfileId", -1L)
            if (temporaryId > 0) {
                check(repo.currentProfileId.value != temporaryId)
                val baselineIds = input.getJSONArray("baselineProfileIds").let { ids -> (0 until ids.length()).map { ids.getLong(it) }.toSet() }
                repo.deleteProfile(temporaryId)
                check(entry.database().profiles().getAll().map { it.id }.toSet() == baselineIds)
            }
            if (input.has("previousInputBase64")) {
                val previous = Base64.decode(input.getString("previousInputBase64"), Base64.NO_WRAP)
                check(stabilitySha(previous) == input.getString("previousInputSha256"))
                stabilityInput(context).write(previous)
            } else {
                input.remove("endpointControl")
                stabilityInput(context).write(input.toString().toByteArray())
            }
            result.put("settingsRestored", true).put("selectionRestored", true).put("configRestored", true)
                .put("temporaryProfileDeleted", temporaryId > 0).put("backupCleared", true)
        }
        else -> {
            val state = input.getJSONObject("endpointControl")
            val nodeMatch = Regex("endpoint-apply-node-([0-9]{1,2})-([0-9]{1,2})").matchEntire(command)
            val match = nodeMatch ?: checkNotNull(Regex("endpoint-apply-([0-9]{1,2})").matchEntire(command))
            val nodeIndex = nodeMatch?.groupValues?.get(1)?.toInt()
            if (nodeIndex != null) check(state.getInt("nodeIndex") == nodeIndex)
            val index = match.groupValues.last().toInt()
            val addresses = state.getJSONArray("addresses"); require(index in 0 until addresses.length())
            val host = state.getString("host"); val address = addresses.getString(index)
            val original = gson.fromJson(state.getString("general"), GeneralSettings::class.java)
            runtime.setVpnEnabled(false).getOrThrow()
            runtime.selectProfile(local.id, repo::selectProfile)
            val literal = runCatching { InetAddress.getByName(host).hostAddress == host }.getOrDefault(false)
            val next = if (literal) original else original.copy(hosts = original.hosts + (host to address))
            runtime.updateGeneralSettings(entry.generalSettingsStore().read(), next).getOrThrow()
            runtime.setOutboundMode(CoreOutboundMode.GLOBAL).getOrThrow()
            val applied = JsonParser.parseString(YamlConfigCodec.toJson(entry.paths().configFile.readText())).asJsonObject
            if (literal) check(applied.getAsJsonArray("proxies") == JsonParser.parseString(YamlConfigCodec.toJson(repo.profileFile(local.id).readText())).asJsonObject.getAsJsonArray("proxies"))
            else check(applied.getAsJsonObject("hosts").getAsJsonArray(host).single().asString == address)
            val before = JsonParser.parseString(YamlConfigCodec.toJson(repo.profileFile(local.id).readText())).asJsonObject
            check(applied.getAsJsonArray("proxies") == before.getAsJsonArray("proxies"))
            result.put("index", index).put("nodeIndex", state.getInt("nodeIndex"))
                .put("expectedEndpointHash", stabilitySha("$address:${state.getInt("port")}".toByteArray()))
                .put("fixedMechanism", if (literal) "literal-ip" else "hosts")
                .put("hostsApplied", !literal).put("nodeDefinitionsUnchanged", true).put("localSourceSha256", input.getString("localSha256"))
        }
    }
    return result
}
