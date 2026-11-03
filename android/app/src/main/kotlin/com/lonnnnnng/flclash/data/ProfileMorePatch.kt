package com.lonnnnnng.flclash.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileSnifferOverrides

/** 作者 long：逐项保存只合并本次改变的字段，保留另一分区和编辑窗口打开后新增的覆盖。 */
data class ProfileMorePatch(
    val beforeNtp: ProfileNtpOverrides,
    val ntp: ProfileNtpOverrides,
    val beforeSniffer: ProfileSnifferOverrides,
    val sniffer: ProfileSnifferOverrides,
) {
    fun applyTo(raw: String): String {
        val root = if (raw.isBlank()) JsonObject() else JsonParser.parseString(raw).asJsonObject.deepCopy()
        fun patchSection(name: String, before: JsonObject, after: JsonObject) {
            val current = root.get(name)?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()
            (before.keySet() + after.keySet()).forEach { key ->
                if (before.get(key) == after.get(key)) return@forEach
                if (name == "sniffer" && key == "sniff") {
                    // 作者 long：HTTP/TLS/QUIC各自是一个设置项，修改HTTP不能覆盖并发加入的TLS。
                    val protocols = current.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()
                    val old = before.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                    val next = after.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                    (old.keySet() + next.keySet()).forEach { protocol ->
                        if (old.get(protocol) != next.get(protocol)) {
                            if (!next.has(protocol)) protocols.remove(protocol)
                            else {
                                // 作者 long：端口和独立目标分别保存；仅改端口时不能覆盖最新目标开关或尚不认识的协议参数。
                                val entry = protocols.get(protocol)?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()
                                val previous = old.get(protocol)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                                val changed = next.getAsJsonObject(protocol)
                                (previous.keySet() + changed.keySet()).forEach { field ->
                                    if (previous.get(field) != changed.get(field)) {
                                        if (changed.has(field)) entry.add(field, changed.get(field).deepCopy()) else entry.remove(field)
                                    }
                                }
                                protocols.add(protocol, entry)
                            }
                        }
                    }
                    if (protocols.size() == 0) current.remove(key) else current.add(key, protocols)
                } else if (after.has(key)) current.add(key, after.get(key).deepCopy()) else current.remove(key)
            }
            if (current.size() == 0) root.remove(name) else root.add(name, current)
        }
        patchSection("ntp", beforeNtp.toJson(), ntp.toJson())
        patchSection("sniffer", beforeSniffer.toJson(), sniffer.toJson())
        return root.toString()
    }
}
