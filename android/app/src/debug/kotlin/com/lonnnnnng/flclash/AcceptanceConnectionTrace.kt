package com.lonnnnnng.flclash

import com.lonnnnnng.flclash.core.CoreConnection
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** 作者 long：失败连接也保留身份与单向字节，Tracker是协议适配后的业务流，不能当节点TCP线上的收发包。 */
internal class AcceptanceConnectionTrace {
    private data class Row(val first: Long, var last: Long, var samples: Int, var upload: Long, var download: Long,
        var chainHashes: List<String>, var nonDirect: Boolean)
    private val rows = linkedMapOf<String, Row>()
    private var overflow = 0
    private var readFailures = 0
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @Synchronized fun record(connection: CoreConnection, elapsedMs: Long) {
        val key = hash(connection.id)
        if (key !in rows && rows.size >= 4) { overflow++; return }
        val chains = connection.chains.map(::hash).take(16)
        val row = rows.getOrPut(key) { Row(elapsedMs, elapsedMs, 0, 0, 0, chains, false) }
        row.last = elapsedMs; row.samples++
        row.upload = maxOf(row.upload, connection.upload); row.download = maxOf(row.download, connection.download)
        if (chains.isNotEmpty()) row.chainHashes = chains
        row.nonDirect = row.nonDirect || connection.chains.any { it != "DIRECT" }
    }
    @Synchronized fun readFailed() { readFailures++ }
    @Synchronized fun snapshot() = JSONObject().put("matchedConnections", JSONArray(rows.map { (key, row) ->
        JSONObject().put("connectionIdHash", key).put("firstSeenElapsedMs", row.first).put("lastSeenElapsedMs", row.last)
            .put("samples", row.samples).put("maxUpload", row.upload).put("maxDownload", row.download)
            .put("positiveTraffic", row.upload > 0 && row.download > 0).put("chainHashes", JSONArray(row.chainHashes))
            .put("hasNonDirectChain", row.nonDirect)
    })).put("overflow", overflow).put("readFailures", readFailures).put("wireBytesClaimed", false)
}
