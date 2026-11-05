package com.lonnnnnng.flclash.data

import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal const val IP_QUALITY_MAX_AGE = 3 * 24 * 3_600_000L
internal const val IP_QUALITY_CACHE_KEY = "ip_quality_cache_v1"
internal data class IpQualityRecord(val quality: IpQuality, val checkedAt: Long)

internal interface IpQualityCache {
    suspend fun fresh(ip: String, now: Long): IpQualityRecord?
    suspend fun put(record: IpQualityRecord, now: Long)
}

internal object NoopIpQualityCache : IpQualityCache {
    override suspend fun fresh(ip: String, now: Long): IpQualityRecord? = null
    override suspend fun put(record: IpQualityRecord, now: Long) = Unit
}

/** 作者 long：质量按IP复用，不能把节点/路由状态跨进程保存；仅保存成功解析的字段，不保存源响应或失败。 */
internal class RoomIpQualityCache(private val database: KClashDatabase) : IpQualityCache {
    override suspend fun fresh(ip: String, now: Long): IpQualityRecord? =
        IpQualityCacheCodec.decode(database.appPreferences().get(IP_QUALITY_CACHE_KEY), now)[ip]

    override suspend fun put(record: IpQualityRecord, now: Long) = database.withTransaction {
        val records = IpQualityCacheCodec.decode(database.appPreferences().get(IP_QUALITY_CACHE_KEY), now).toMutableMap()
        // 作者 long：旧查询迟到时不能覆盖同IP较新的结果；事务同时保留其他IP刚提交的记录。
        if (record.checkedAt >= (records[record.quality.ip]?.checkedAt ?: Long.MIN_VALUE)) records[record.quality.ip] = record
        database.appPreferences().put(AppPreferenceEntity(IP_QUALITY_CACHE_KEY, IpQualityCacheCodec.encode(records.values, now), now))
    }
}

internal object IpQualityCacheCodec {
    private const val MAX_CHARS = 128 * 1024
    private const val MAX_ENTRIES = 64

    fun fresh(at: Long, now: Long) = at >= 0 && at <= now && now - at < IP_QUALITY_MAX_AGE

    fun decode(content: String?, now: Long): Map<String, IpQualityRecord> {
        if (content == null || content.length > MAX_CHARS) return emptyMap()
        return runCatching {
            val root = JsonParser.parseString(content).asJsonObject
            require(root["version"].asInt == 1)
            val entries = root.getAsJsonArray("entries"); require(entries.size() <= MAX_ENTRIES)
            entries.mapNotNull { value -> runCatching {
                val row = value.asJsonObject
                val at = row["checkedAt"].asLong; require(fresh(at, now))
                val q = row.getAsJsonObject("quality")
                val ip = q["ip"].asString; require(isPublicIpLiteral(ip))
                val source = IpQualitySource.valueOf(q["source"].asString)
                val type = IpType.valueOf(q["type"].asString)
                fun flag(name: String): Boolean {
                    val field = q[name]; require(field.isJsonPrimitive && field.asJsonPrimitive.isBoolean)
                    return field.asBoolean
                }
                val organization = q["organization"]?.takeUnless { it.isJsonNull }?.asString
                require(organization == null || organization.length <= 256)
                val asn = q["asn"]?.takeUnless { it.isJsonNull }?.asLong
                require(asn == null || asn > 0)
                IpQualityRecord(IpQuality(ip, source, type, organization, asn,
                    flag("isProxy"), flag("isVpn"), flag("isTor"), flag("isAbuser")), at)
            }.getOrNull() }.sortedBy { it.checkedAt }.associateBy { it.quality.ip }
        }.getOrDefault(emptyMap())
    }

    fun encode(records: Collection<IpQualityRecord>, now: Long): String {
        // 作者 long：按原查询时间清理过期/未来记录并限制64个IP；命中或重启不延长三天有效期。
        val entries = records.filter { fresh(it.checkedAt, now) }.sortedByDescending { it.checkedAt }.take(MAX_ENTRIES)
        // 作者 long：显式写协议字段，Release混淆不能改变磁盘格式，也不必为缓存保留整棵领域模型。
        val rows = JsonArray()
        entries.forEach { record ->
            val quality = record.quality
            val fields = JsonObject().apply {
                addProperty("ip", quality.ip)
                addProperty("source", quality.source.name)
                addProperty("type", quality.type.name)
                addProperty("organization", quality.organization)
                addProperty("asn", quality.asn)
                addProperty("isProxy", quality.isProxy)
                addProperty("isVpn", quality.isVpn)
                addProperty("isTor", quality.isTor)
                addProperty("isAbuser", quality.isAbuser)
            }
            rows.add(JsonObject().apply {
                addProperty("checkedAt", record.checkedAt)
                add("quality", fields)
            })
        }
        return JsonObject().apply {
            addProperty("version", 1)
            add("entries", rows)
        }.toString().also { require(it.length <= MAX_CHARS) }
    }
}
