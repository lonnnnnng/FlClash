package com.lonnnnnng.flclash.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY orderIndex ASC, id ASC")
    abstract fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY orderIndex ASC, id ASC")
    abstract suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecord(profile: ProfileEntity)
    suspend fun insert(profile: ProfileEntity) = auditedDaoWrite("profile.dao.insert", "profiles", profile) { insertRecord(profile) }

    @Update
    protected abstract suspend fun updateRecord(profile: ProfileEntity)
    suspend fun update(profile: ProfileEntity) = auditedDaoWrite("profile.dao.update", "profiles", profile) { updateRecord(profile) }

    @Delete
    protected abstract suspend fun deleteRecord(profile: ProfileEntity)
    suspend fun delete(profile: ProfileEntity) = auditedDaoWrite("profile.dao.delete", "profiles", profile) { deleteRecord(profile) }

    @Query("UPDATE profiles SET orderIndex = :orderIndex WHERE id = :id")
    protected abstract suspend fun updateOrderRecord(id: Long, orderIndex: Int)
    suspend fun updateOrder(id: Long, orderIndex: Int) = auditedDaoWrite("profile.dao.order", "profiles", mapOf("id" to id, "orderIndex" to orderIndex)) { updateOrderRecord(id, orderIndex) }

    @Query("UPDATE profiles SET scriptId = NULL WHERE scriptId = :scriptId")
    protected abstract suspend fun clearScriptBindingRecord(scriptId: Long)
    suspend fun clearScriptBinding(scriptId: Long) = auditedDaoWrite("script.delete.clearBinding", "profiles", mapOf("scriptId" to scriptId)) { clearScriptBindingRecord(scriptId) }

    @Query("DELETE FROM profiles")
    protected abstract suspend fun deleteAllRecords()
    suspend fun deleteAll() = auditedDaoWrite("profile.dao.deleteAll", "profiles", null) { deleteAllRecords() }
}
