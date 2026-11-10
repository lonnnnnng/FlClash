package com.lonnnnnng.flclash.data

import com.google.gson.Gson
import com.lonnnnnng.flclash.common.StateWriteAudit

/** 作者 long：直接DAO旁路也要有来源；受理时保留字段摘要，实际提交仍由SQLite事务观察器判定。 */
internal suspend fun <T> auditedDaoWrite(source: String, table: String, entity: Any?, block: suspend () -> T): T =
    StateWriteAudit.operation(source) {
        if (StateWriteAudit.enabled) {
            val fields = runCatching { entity?.let { Gson().toJsonTree(it) }?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()
                ?.associate { it.key to StateWriteAudit.value(it.value.toString()) }
            }.getOrElse { mapOf("snapshotFailure" to it.javaClass.simpleName) }
            val preferenceKey = (entity as? AppPreferenceEntity)?.key
            val family = preferenceKey?.takeIf { it in setOf("current_profile_id", "runtime_should_resume", "ip_quality_cache_v1", "access_control",
                "global_filter_presets", "global_icon_sets", "global_rule_resources", "global_extension_rules", "local_proxies", "group_icon_history") }
            StateWriteAudit.event("entity_write", mapOf("table" to table, "fields" to fields, "preferenceFamily" to family))
        }
        block()
    }
