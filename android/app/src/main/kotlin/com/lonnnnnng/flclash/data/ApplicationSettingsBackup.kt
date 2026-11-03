package com.lonnnnnng.flclash.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.*

data class ApplicationSettingsBackup(
    val general: GeneralSettings = GeneralSettings(),
    val network: NetworkSettings = NetworkSettings(),
    val dns: ProfileDnsOverrides = ProfileDnsOverrides(),
    val vpnEnabled: Boolean = true,
    val allowLan: Boolean? = null,
    val restoreStrategy: BackupRestoreStrategy = BackupRestoreStrategy.COMPATIBLE,
    val onDemandSsids: List<String>? = null,
) {
    override fun toString() = "ApplicationSettingsBackup([REDACTED])"
}

/** 作者 long：归档显式保留null和空列表，恢复不能把DNS清空覆盖误当继承，也不能为缺失认证生成新凭据。 */
object ApplicationSettingsBackupCodec {
    private val gson = GsonBuilder().serializeNulls().create()
    fun encode(value: ApplicationSettingsBackup): String {
        value.general.validated(); value.network.validated(); GlobalDnsSettings.validate(value.dns)
        value.onDemandSsids?.let(OnDemandSettings::validate)
        return JsonObject().apply {
            add("general", gson.toJsonTree(value.general)); add("network", gson.toJsonTree(value.network))
            add("dns", value.dns.toJson()); addProperty("vpnEnabled", value.vpnEnabled); add("allowLan", gson.toJsonTree(value.allowLan))
            addProperty("restoreStrategy", value.restoreStrategy.name)
            value.onDemandSsids?.let { add("onDemandSsids", gson.toJsonTree(it)) }
        }.toString()
    }

    fun decode(raw: String): ApplicationSettingsBackup {
        require(raw.toByteArray().size <= 8 * 1024 * 1024) { "应用设置超过容量限制" }
        val root = JsonParser.parseString(raw).asJsonObject
        val required = setOf("general", "network", "dns", "vpnEnabled", "allowLan", "restoreStrategy")
        require(root.keySet().containsAll(required) && root.keySet().all { it in required || it == "onDemandSsids" }) { "应用设置字段不完整" }
        // 作者 long：旧v6缺项保留当前SSID规则，显式空数组才表示清空，错误类型不能退化成缺项。
        val ssids = root["onDemandSsids"]?.let {
            require(it.isJsonArray && it.asJsonArray.all(::isString)) { "SSID 列表类型无效" }
            OnDemandSettings.validate(it.asJsonArray.map(JsonElement::getAsString))
        }
        val general = root.getAsJsonObject("general")
        val network = root.getAsJsonObject("network")
        // 作者 long：Gson会把字符串转成布尔/数字，归档先检查实际JSON类型，避免损坏备份被静默纠正。
        validateShape(general, gson.toJsonTree(GeneralSettings()).asJsonObject, setOf("hosts", "userAgent"))
        require(general["userAgent"].isJsonNull || isString(general["userAgent"])) { "User-Agent 类型无效" }
        require(general["hosts"].isJsonObject && general.getAsJsonObject("hosts").entrySet().all { isString(it.value) }) { "Hosts 类型无效" }
        validateShape(network, gson.toJsonTree(NetworkSettings()).asJsonObject)
        val dns = root.getAsJsonObject("dns")
        require(dns.keySet().all { it in GlobalDnsSettings.keys }) { "DNS字段无效" }
        dns.entrySet().forEach { (key, value) ->
            require(when {
                key in GlobalDnsSettings.listKeys -> value.isJsonArray && value.asJsonArray.all(::isString)
                key == "ipv6" -> isBoolean(value)
                else -> isString(value)
            }) { "DNS字段类型无效" }
        }
        require(isBoolean(root["vpnEnabled"]) && (root["allowLan"].isJsonNull || isBoolean(root["allowLan"]))) { "网络开关类型无效" }
        require(isString(root["restoreStrategy"])) { "恢复策略类型无效" }
        val strategy = BackupRestoreStrategy.entries.firstOrNull { it.name == root["restoreStrategy"].asString } ?: error("恢复策略无效")
        return ApplicationSettingsBackup(gson.fromJson(general, GeneralSettings::class.java).validated(),
            gson.fromJson(network, NetworkSettings::class.java).validated(),
            GlobalDnsSettings.validate(ProfileDnsOverrides.fromJson(dns)), root["vpnEnabled"].asBoolean,
            root["allowLan"].takeUnless { it.isJsonNull }?.asBoolean, strategy, ssids)
    }

    private fun isString(value: JsonElement) = value.isJsonPrimitive && value.asJsonPrimitive.isString
    private fun isBoolean(value: JsonElement) = value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
    private fun validateShape(actual: JsonObject, expected: JsonObject, skip: Set<String> = emptySet()) {
        require(actual.keySet() == expected.keySet()) { "应用设置字段不完整" }
        expected.entrySet().filterNot { it.key in skip }.forEach { (key, sample) ->
            val value = actual[key]
            require(when {
                sample.isJsonArray -> value.isJsonArray && value.asJsonArray.all(::isString)
                sample.isJsonPrimitive && sample.asJsonPrimitive.isBoolean -> isBoolean(value)
                sample.isJsonPrimitive && sample.asJsonPrimitive.isNumber -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asString.toIntOrNull() != null
                else -> isString(value)
            }) { "应用设置字段类型无效" }
        }
    }
}

/** 作者 long：多个SharedPreferences不能与Room组成原子事务；任意一步失败时逐项补偿，不能因一个补偿失败跳过其余偏好。 */
class ApplicationSettingsBackupStore(
    private val general: GeneralSettingsStore? = null,
    private val network: NetworkSettingsStore? = null,
    private val dns: GlobalDnsSettingsStore? = null,
    private val vpn: VpnSettingsStore? = null,
    private val lan: LanSharingStore? = null,
    private val backupUi: BackupUiPreferences? = null,
    private val onDemand: OnDemandSettingsStore? = null,
) {
    fun read() = ApplicationSettingsBackup(general?.read() ?: GeneralSettings(), network?.read() ?: NetworkSettings(),
        dns?.read() ?: ProfileDnsOverrides(), vpn?.read() ?: true, lan?.read(), backupUi?.read() ?: BackupRestoreStrategy.COMPATIBLE, onDemand?.read())

    fun write(value: ApplicationSettingsBackup) {
        general?.write(value.general); network?.write(value.network); dns?.write(value.dns)
        vpn?.write(value.vpnEnabled); lan?.write(value.allowLan)
        backupUi?.write(value.restoreStrategy)
        value.onDemandSsids?.let { onDemand?.write(it) }
    }

    fun compensate(value: ApplicationSettingsBackup) {
        var failure: Throwable? = null
        listOf<() -> Unit>({ general?.write(value.general) }, { network?.write(value.network) }, { dns?.write(value.dns) },
            { vpn?.write(value.vpnEnabled) }, { lan?.write(value.allowLan) }, { backupUi?.write(value.restoreStrategy) },
            { value.onDemandSsids?.let { onDemand?.write(it) } }).forEach { restore ->
            try { restore() } catch (error: Exception) { if (failure == null) failure = error else failure!!.addSuppressed(error) }
        }
        failure?.let { throw it }
    }
}
