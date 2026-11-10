package com.lonnnnnng.flclash.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 作者 long：测速结果与配置正文分开保存，进程重建后只恢复同一 Profile 的短期缓存，避免节点同名时跨配置串结果。
 */
@Entity(
    tableName = "proxy_delay_results",
    primaryKeys = ["profileId", "groupName", "proxyName", "testUrl"],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["updatedAtMillis"]),
    ],
)
data class ProxyDelayEntity(
    val profileId: Long,
    val groupName: String,
    val proxyName: String,
    val testUrl: String,
    val value: Int,
    val updatedAtMillis: Long,
)

@Dao
abstract class ProxyDelayDao {
    @Query(
        "SELECT * FROM proxy_delay_results " +
            "WHERE profileId = :profileId ORDER BY updatedAtMillis DESC",
    )
    abstract suspend fun load(profileId: Long): List<ProxyDelayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertRecord(value: ProxyDelayEntity)
    suspend fun upsert(value: ProxyDelayEntity) = auditedDaoWrite("delay.dao.upsert", "proxy_delay_results", value) { upsertRecord(value) }

    @Query(
        "DELETE FROM proxy_delay_results " +
            "WHERE profileId = :profileId AND groupName = :groupName " +
            "AND proxyName = :proxyName AND testUrl = :testUrl",
    )
    protected abstract suspend fun deleteRecord(profileId: Long, groupName: String, proxyName: String, testUrl: String)
    suspend fun delete(profileId: Long, groupName: String, proxyName: String, testUrl: String) = auditedDaoWrite("delay.dao.delete", "proxy_delay_results",
        mapOf("profileId" to profileId, "groupName" to groupName, "proxyName" to proxyName, "testUrl" to testUrl)) { deleteRecord(profileId, groupName, proxyName, testUrl) }

    @Query(
        "DELETE FROM proxy_delay_results " +
            "WHERE profileId = :profileId AND updatedAtMillis < :cutoffMillis",
    )
    protected abstract suspend fun deleteOlderThanRecord(profileId: Long, cutoffMillis: Long)
    suspend fun deleteOlderThan(profileId: Long, cutoffMillis: Long) = auditedDaoWrite("delay.dao.expiry", "proxy_delay_results", mapOf("profileId" to profileId, "cutoff" to cutoffMillis)) { deleteOlderThanRecord(profileId, cutoffMillis) }
}

interface ProxyDelayStore {
    suspend fun load(profileId: Long): List<ProxyDelayEntity>

    suspend fun upsert(value: ProxyDelayEntity)

    suspend fun delete(profileId: Long, groupName: String, proxyName: String, testUrl: String)

    suspend fun deleteOlderThan(profileId: Long, cutoffMillis: Long)
}

class RoomProxyDelayStore(
    private val dao: ProxyDelayDao,
) : ProxyDelayStore {
    override suspend fun load(profileId: Long): List<ProxyDelayEntity> = dao.load(profileId)

    override suspend fun upsert(value: ProxyDelayEntity) = dao.upsert(value)

    override suspend fun delete(profileId: Long, groupName: String, proxyName: String, testUrl: String) =
        dao.delete(profileId, groupName, proxyName, testUrl)

    override suspend fun deleteOlderThan(profileId: Long, cutoffMillis: Long) =
        dao.deleteOlderThan(profileId, cutoffMillis)
}

/**
 * 作者 long：JVM 面板和旧的直接构造测试不需要 Android SQLite；空实现保留这些调用点，同时让正式 Hilt 图使用 Room Store。
 */
object NoopProxyDelayStore : ProxyDelayStore {
    override suspend fun load(profileId: Long): List<ProxyDelayEntity> = emptyList()

    override suspend fun upsert(value: ProxyDelayEntity) = Unit

    override suspend fun delete(profileId: Long, groupName: String, proxyName: String, testUrl: String) = Unit

    override suspend fun deleteOlderThan(profileId: Long, cutoffMillis: Long) = Unit
}
