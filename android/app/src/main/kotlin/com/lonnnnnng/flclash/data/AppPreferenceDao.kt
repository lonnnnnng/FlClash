package com.lonnnnnng.flclash.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppPreferenceDao {
    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun observe(key: String): kotlinx.coroutines.flow.Flow<String?>
    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_preferences WHERE `key` LIKE :prefix || '%'")
    suspend fun getByPrefix(prefix: String): List<AppPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preference: AppPreferenceEntity)

    @Query("DELETE FROM app_preferences WHERE `key` = :key")
    suspend fun delete(key: String)
}
