package com.lonnnnnng.flclash.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：测速缓存依赖复合键和 Android SQLite 的真实约束，必须在 Room/SQLite 层验证，不能只依赖 JVM Fake Store。
 */
@RunWith(AndroidJUnit4::class)
class ProxyDelayDaoInstrumentedTest {
    private lateinit var database: KClashDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KClashDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomStoreIsolatesProfileGroupNodeAndUrlAndUpsertsTheCompositeKey() = runBlocking {
        val store = RoomProxyDelayStore(database.proxyDelayResults())
        store.upsert(ProxyDelayEntity(7L, "Auto", "node-a", "https://one", 120, 100L))
        store.upsert(ProxyDelayEntity(7L, "Auto", "node-a", "https://two", 240, 200L))
        store.upsert(ProxyDelayEntity(7L, "Other", "node-a", "https://one", 360, 300L))
        store.upsert(ProxyDelayEntity(8L, "Auto", "node-a", "https://one", 480, 400L))
        store.upsert(ProxyDelayEntity(7L, "Auto", "node-a", "https://one", 121, 500L))

        val profileSeven = store.load(7L)
        assertEquals(3, profileSeven.size)
        assertEquals(listOf(500L, 300L, 200L), profileSeven.map { it.updatedAtMillis })
        assertEquals(121, profileSeven.first { it.testUrl == "https://one" && it.groupName == "Auto" }.value)
        assertEquals(480, store.load(8L).single().value)
    }

    @Test
    fun roomStoreDeletesOnlyRequestedEntryAndProfileScopedExpiredRows() = runBlocking {
        val store = RoomProxyDelayStore(database.proxyDelayResults())
        store.upsert(ProxyDelayEntity(9L, "Auto", "node-a", "https://one", 100, 100L))
        store.upsert(ProxyDelayEntity(9L, "Auto", "node-a", "https://two", 200, 200L))
        store.upsert(ProxyDelayEntity(9L, "Other", "node-a", "https://one", 300, 300L))
        store.upsert(ProxyDelayEntity(10L, "Auto", "node-a", "https://one", 400, 100L))

        store.delete(9L, "Auto", "node-a", "https://one")
        assertEquals(
            setOf("https://two", "https://one"),
            store.load(9L).map { it.testUrl }.toSet(),
        )

        store.deleteOlderThan(9L, 300L)
        assertEquals(listOf(300L), store.load(9L).map { it.updatedAtMillis })
        assertEquals(listOf(100L), store.load(10L).map { it.updatedAtMillis })
    }

}
