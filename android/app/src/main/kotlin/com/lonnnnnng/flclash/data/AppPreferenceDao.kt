package com.lonnnnnng.flclash.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class AppPreferenceDao {
    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    abstract fun observe(key: String): kotlinx.coroutines.flow.Flow<String?>
    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    abstract suspend fun get(key: String): String?

    @Query("SELECT * FROM app_preferences WHERE `key` LIKE :prefix || '%'")
    abstract suspend fun getByPrefix(prefix: String): List<AppPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putRecord(preference: AppPreferenceEntity)
    suspend fun put(preference: AppPreferenceEntity) = auditedDaoWrite("preference.dao.put", "app_preferences", preference) { putRecord(preference) }

    @Query("DELETE FROM app_preferences WHERE `key` = :key")
    protected abstract suspend fun deleteRecord(key: String)
    suspend fun delete(key: String) = auditedDaoWrite("preference.dao.delete", "app_preferences", mapOf("key" to key)) { deleteRecord(key) }
}
