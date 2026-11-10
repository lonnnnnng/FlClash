package com.lonnnnnng.flclash.common

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** 作者 long：监听器只看到已变化值，不能区分调用方和apply持久化；在原Editor边界记录受理与commit回执。 */
fun auditedPreferences(context: Context, name: String): SharedPreferences {
    val delegate = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    return object : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor = AuditedPreferencesEditor(delegate, delegate.edit(), name)
    }
}

private class AuditedPreferencesEditor(
    private val preferences: SharedPreferences,
    private val delegate: SharedPreferences.Editor,
    private val name: String,
) : SharedPreferences.Editor by delegate {
    private val keys = linkedSetOf<String>()
    private var clears = false
    @Synchronized private fun changed(key: String, block: () -> Unit): SharedPreferences.Editor { keys += key; block(); return this }
    override fun putString(key: String, value: String?) = changed(key) { delegate.putString(key, value) }
    override fun putStringSet(key: String, values: MutableSet<String>?) = changed(key) { delegate.putStringSet(key, values) }
    override fun putInt(key: String, value: Int) = changed(key) { delegate.putInt(key, value) }
    override fun putLong(key: String, value: Long) = changed(key) { delegate.putLong(key, value) }
    override fun putFloat(key: String, value: Float) = changed(key) { delegate.putFloat(key, value) }
    override fun putBoolean(key: String, value: Boolean) = changed(key) { delegate.putBoolean(key, value) }
    override fun remove(key: String) = changed(key) { delegate.remove(key) }
    @Synchronized override fun clear(): SharedPreferences.Editor { clears = true; delegate.clear(); return this }
    private fun snapshot(): Map<String, Any?> = runCatching {
        val all = preferences.all
        (if (clears) all.keys + keys else keys).associate { key ->
            (StateWriteAudit.digest(key.toByteArray()) ?: "unavailable") to StateWriteAudit.value(all[key])
        }
    }.getOrElse { mapOf("snapshotFailure" to it.javaClass.simpleName) }
    @Synchronized private fun <T> write(mode: String, block: () -> T): T {
        if (!StateWriteAudit.enabled) return try { block() } finally { keys.clear(); clears = false }
        val id = UUID.randomUUID().toString()
        val detail = mapOf("write" to id, "store" to name, "mode" to mode, "clear" to clears, "before" to snapshot())
        StateWriteAudit.event("preference_accepted", detail)
        return try {
            block().also { result ->
                // 作者 long：apply返回仅证明内存更新已受理，commit=false也不能认定磁盘提交成功。
                StateWriteAudit.event(if (mode == "apply") "preference_apply_accepted" else if (result == true) "preference_committed" else "preference_commit_failed",
                    detail + mapOf("after" to snapshot()))
            }
        } catch (failure: Throwable) {
            StateWriteAudit.event("preference_failed", detail + mapOf("failureType" to failure.javaClass.simpleName)); throw failure
        } finally { keys.clear(); clears = false }
    }
    override fun commit(): Boolean = write("commit") { delegate.commit() }
    override fun apply() { write("apply") { delegate.apply() } }
}
