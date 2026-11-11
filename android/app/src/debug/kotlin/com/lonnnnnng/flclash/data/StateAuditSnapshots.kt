package com.lonnnnnng.flclash.data

import android.content.Context
import android.os.SystemClock
import com.google.gson.Gson
import com.lonnnnnng.flclash.common.StateWriteAudit
import org.json.JSONObject

/** 作者 long：快照与写入审计共用摘要协议，逐键匹配只能证明值来源，读取时间窗交叠时不得宣称唯一写者。 */
object StateAuditSnapshots {
    private val gson = Gson()

    fun entityFields(entity: Any): JSONObject = JSONObject(gson.toJsonTree(entity).asJsonObject.entrySet()
        .associate { it.key to StateWriteAudit.value(it.value.toString()) })

    suspend fun delays(database: KClashDatabase, profileIds: List<Long>): JSONObject {
        if (!StateWriteAudit.enabled) return JSONObject().put("enabled", false)
        val started = SystemClock.elapsedRealtime()
        val profiles = JSONObject()
        for (id in profileIds) {
            val readStarted = SystemClock.elapsedRealtime()
            val rows = database.proxyDelayResults().load(id)
            val readFinished = SystemClock.elapsedRealtime()
            val values = JSONObject()
            rows.forEach { row ->
                // 作者 long：四列主键共同标识测速，节点同名或测试URL不同不能合并；字段仍与DAO的JSON标量摘要完全一致。
                val key = gson.toJson(listOf(row.profileId, row.groupName, row.proxyName, row.testUrl))
                val digest = checkNotNull(StateWriteAudit.digest(key.toByteArray()))
                check(!values.has(digest))
                values.put(digest, entityFields(row))
            }
            profiles.put(id.toString(), JSONObject().put("startedElapsedMs", readStarted)
                .put("finishedElapsedMs", readFinished).put("rows", values))
        }
        return JSONObject().put("enabled", true).put("startedElapsedMs", started)
            .put("sessionDigest", StateWriteAudit.digest("state-audit-key-join-v1".toByteArray()))
            .put("finishedElapsedMs", SystemClock.elapsedRealtime()).put("profiles", profiles)
    }

    fun preferences(context: Context, stores: Collection<String>): JSONObject {
        if (!StateWriteAudit.enabled) return JSONObject().put("enabled", false)
        val started = SystemClock.elapsedRealtime()
        val values = JSONObject()
        for (store in stores.sorted()) {
            val readStarted = SystemClock.elapsedRealtime()
            val all = context.getSharedPreferences(store, Context.MODE_PRIVATE).all
            val readFinished = SystemClock.elapsedRealtime()
            // 作者 long：store沿用审计已有的应用内部文件名；键和值只保留会话HMAC，字符串不能额外JSON加引号。
            val fields = all.entries.associate { (key, value) ->
                checkNotNull(StateWriteAudit.digest(key.toByteArray())) to StateWriteAudit.value(value)
            }
            values.put(store, JSONObject().put("startedElapsedMs", readStarted)
                .put("finishedElapsedMs", readFinished).put("values", JSONObject(fields)))
        }
        return JSONObject().put("enabled", true).put("startedElapsedMs", started)
            .put("sessionDigest", StateWriteAudit.digest("state-audit-key-join-v1".toByteArray()))
            .put("finishedElapsedMs", SystemClock.elapsedRealtime()).put("stores", values)
    }
}
