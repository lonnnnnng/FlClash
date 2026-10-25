package com.lonnnnnng.flclash.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RuntimeDiagnosticDao {
    @Query("SELECT * FROM runtime_diagnostic_events ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<RuntimeDiagnosticEntity>

    @Insert
    suspend fun insert(event: RuntimeDiagnosticEntity)

    /**
     * 作者 long：只清理超出保留窗口的旧自增 ID，避免高频网络回调让数据库无限增长，
     * 同时保留最近事件的插入顺序，便于应用进程重建后按时间线展示。
     */
    @Query(
        "DELETE FROM runtime_diagnostic_events " +
            "WHERE id NOT IN (SELECT id FROM runtime_diagnostic_events " +
            "ORDER BY id DESC LIMIT :limit)",
    )
    suspend fun trim(limit: Int)

    @Transaction
    suspend fun insertAndTrim(event: RuntimeDiagnosticEntity, limit: Int) {
        insert(event)
        trim(limit)
    }

    @Query("DELETE FROM runtime_diagnostic_events")
    suspend fun clear()
}
