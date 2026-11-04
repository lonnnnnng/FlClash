package com.lonnnnnng.flclash.data

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** 作者 long：只保留最近16次系统任务的ID、到期输入和结果，不保存地址、标签、正文或异常原文。 */
object SubscriptionAutoUpdateAudit {
    private const val STORE = "subscription-auto-update-audit"

    fun read(context: Context): JSONArray = runCatching {
        JSONArray(context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("runs", "[]"))
    }.getOrElse { JSONArray() }

    @Synchronized
    fun record(context: Context, workId: String, attempt: Int, checkedAt: Long, startedElapsed: Long, result: AutoUpdateResult) {
        val entry = JSONObject().apply {
            put("workId", workId); put("attempt", attempt)
            put("checkedAt", checkedAt); put("startedElapsed", startedElapsed)
            put("finishedAt", System.currentTimeMillis()); put("finishedElapsed", SystemClock.elapsedRealtime())
            put("attempted", result.attempted); put("downloadedIds", JSONArray(result.downloadedIds))
            put("handledSourceIds", JSONArray(result.appliedSourceIds))
            put("downloadFailureIds", JSONArray(result.failures.map { it.profileId }))
            put("applyFailureIds", JSONArray(result.applyFailures.map { it.profileId }))
            put("checks", JSONArray().apply { result.checks.forEach { check -> put(JSONObject().apply {
                put("profileId", check.profileId); put("lastUpdateAt", check.lastUpdateAt ?: JSONObject.NULL)
                put("intervalMillis", check.intervalMillis); put("due", check.due)
            }) } })
        }
        val old = read(context)
        val runs = JSONArray().apply {
            for (index in maxOf(0, old.length() - 15) until old.length()) put(old.getJSONObject(index))
            put(entry)
        }
        // 作者 long：在Worker返回前落盘，避免进程结束后只剩WorkInfo成功却无法归因本次下载与应用。
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString("runs", runs.toString()).commit()
    }
}
