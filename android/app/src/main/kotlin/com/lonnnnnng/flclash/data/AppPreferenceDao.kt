package com.lonnnnnng.flclash.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppPreferenceDao {
    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preference: AppPreferenceEntity)

    @Query("DELETE FROM app_preferences WHERE `key` = :key")
    suspend fun delete(key: String)
}
