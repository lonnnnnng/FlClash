package com.lonnnnnng.flclash.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

/** 作者 long：应用行为与核心常规参数共用一次保存，但只有核心投影变化才重新加载配置，避免改通知时重跑脚本。 */
data class GeneralSettings(
    val autoRun: Boolean = false,
    val minimizeOnExit: Boolean = true,
    val hidden: Boolean = false,
    val showNotificationStopAction: Boolean = true,
    val checkCertificate: Boolean = true,
    val userAgent: String? = null,
    val userAgents: List<String> = listOf("clash-verge/v2.4.2", "ClashforWindows/0.19.23"),
    val mixedPort: Int = 7890,
    val port: Int = 0,
    val socksPort: Int = 0,
    val redirPort: Int = 0,
    val tproxyPort: Int = 0,
    val externalController: Boolean = false,
    val controllerSecret: String = "",
    val authentication: Boolean = false,
    val username: String = "",
    val password: String = "",
    val delayTestTimeout: Int = 8000,
    val findProcess: Boolean = false,
    val closeConnections: Boolean = true,
    val onlyStatisticsProxy: Boolean = false,
    val hosts: Map<String, String> = emptyMap(),
    val appendSystemDns: Boolean = false,
    val logLevel: String = "error",
    val openLogs: Boolean = false,
) {
    override fun toString(): String = "GeneralSettings([REDACTED])"

    fun prepared(): GeneralSettings {
        val account = username.trim().replace(":", "")
        // 作者 long：启用认证时补齐可用账号，控制器仅在存在密钥时开放；空账号不能生成无人能访问的入站。
        val generate = authentication && account.isEmpty()
        return copy(username = if (generate) secret(8) else account,
            password = if (generate) secret(16) else password.trim(),
            controllerSecret = controllerSecret.trim().ifEmpty { if (externalController) secret(32) else "" }).validated()
    }

    fun validated(): GeneralSettings {
        val ports = listOf(mixedPort, port, socksPort, redirPort, tproxyPort)
        require(mixedPort in 1024..49151 && ports.drop(1).all { it == 0 || it in 1024..49151 }) { "端口范围为 1024–49151，其他入站可填 0 关闭" }
        require(ports.filter { it > 0 }.distinct().size == ports.count { it > 0 }) { "启用的端口不能重复" }
        require(delayTestTimeout in listOf(500, 1000, 2000, 5000, 8000)) { "请选择有效的测速超时" }
        require(logLevel in listOf("debug", "info", "warning", "error", "silent")) { "请选择有效的日志等级" }
        require(userAgents.size <= 128 && userAgents.distinct().size == userAgents.size) { "User-Agent 最多 128 项且不能重复" }
        require((userAgents + listOfNotNull(userAgent)).all { it.isNotBlank() && it.length <= 1024 && it.none(Char::isISOControl) }) { "User-Agent 必须为非空单行文本，最多 1024 字符" }
        require(listOf(username, password, controllerSecret).all { it.length <= 1024 && it.none(Char::isISOControl) }) { "账号、密码或密钥格式无效" }
        require(':' !in username) { "账号不能包含冒号" }
        require(!authentication || username.isNotEmpty()) { "启用认证时请输入账号" }
        require(!externalController || controllerSecret.isNotEmpty()) { "启用外部控制器时请输入密钥" }
        require(hosts.size <= 1024 && hosts.all { (key, value) -> key.isNotBlank() && key.length <= 253 && key.none { it.isWhitespace() || it.isISOControl() } &&
            value.isNotBlank() && value.length <= 4096 && value.none(Char::isISOControl) }) { "Hosts 每项需要有效域名和非空地址，最多 1024 项" }
        return this
    }

    internal fun runtimeKey(): List<Any?> = listOf(mixedPort, port, socksPort, redirPort, tproxyPort, externalController,
        controllerSecret, authentication, username, password, findProcess, hosts, appendSystemDns, logLevel, userAgent, checkCertificate,
        delayTestTimeout)

    companion object {
        private fun secret(length: Int) = UUID.randomUUID().toString().replace("-", "").take(length)
    }
}

interface GeneralSettingsStore {
    fun read(): GeneralSettings
    fun write(value: GeneralSettings)
}

class SharedPreferencesGeneralSettingsStore(context: Context) : GeneralSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences("general_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    override fun read(): GeneralSettings = preferences.getString("settings", null)?.let {
        gson.fromJson(it, GeneralSettings::class.java).validated()
    } ?: GeneralSettings()
    override fun write(value: GeneralSettings) {
        check(preferences.edit().putString("settings", gson.toJson(value.validated())).commit()) { "无法保存常规设置" }
    }
}

/** 作者 long：只改变运行副本；Hosts逐项合并保留源映射，system://在最终DNS名单末尾去重追加。 */
object GeneralSettingsApplier {
    fun apply(yaml: String, settings: GeneralSettings?): String {
        if (settings == null) return yaml
        settings.validated()
        val root = JsonParser.parseString(YamlConfigCodec.toJson(yaml)).asJsonObject
        root.addProperty("mixed-port", settings.mixedPort)
        root.addProperty("port", settings.port)
        root.addProperty("socks-port", settings.socksPort)
        root.addProperty("redir-port", settings.redirPort)
        root.addProperty("tproxy-port", settings.tproxyPort)
        root.addProperty("find-process-mode", if (settings.findProcess) "always" else "off")
        root.addProperty("log-level", settings.logLevel)
        root.addProperty("external-controller", if (settings.externalController) "127.0.0.1:9090" else "")
        root.addProperty("secret", settings.controllerSecret)
        for (key in listOf("external-controller-tls", "external-controller-unix", "external-controller-pipe")) root.addProperty(key, "")
        root.add("authentication", JsonArray().apply { if (settings.authentication) add("${settings.username}:${settings.password}") })
        root.add("skip-auth-prefixes", JsonArray())
        settings.userAgent?.let { root.addProperty("global-ua", it) }
        val hosts = root.get("hosts")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        settings.hosts.forEach { (name, value) -> hosts.add(name, JsonArray().apply {
            value.split(Regex("[,;\\s]+")).filter(String::isNotEmpty).forEach { add(it) }
        }) }
        root.add("hosts", hosts)
        if (settings.appendSystemDns) {
            val dns = root.get("dns")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            val nameservers = dns.get("nameserver")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            if (nameservers.none { it.isJsonPrimitive && it.asString == "system://" }) nameservers.add("system://")
            dns.add("nameserver", nameservers); root.add("dns", dns)
        }
        return YamlConfigCodec.toYaml(root.toString())
    }
}
