package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class IpQualityCacheInstrumentedTest {
    @Test fun roomTransactionsKeepConcurrentIpsAndRejectOlderLateAnswers(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        try {
            val cache = RoomIpQualityCache(database)
            coroutineScope {
                (1..20).map { index -> async(Dispatchers.IO) {
                    cache.put(IpQualityRecord(IpQuality("8.8.8.$index", IpQualitySource.IP_QUERY, IpType.HOSTING), 2_000), 2_000)
                } }.awaitAll()
            }
            assertEquals(20, IpQualityCacheCodec.decode(database.appPreferences().get(IP_QUALITY_CACHE_KEY), 2_000).size)
            cache.put(IpQualityRecord(IpQuality("8.8.8.1", IpQualitySource.IDENT_ME, IpType.RESIDENTIAL), 1_000), 2_000)
            assertEquals(IpType.HOSTING, cache.fresh("8.8.8.1", 2_000)!!.quality.type)
            assertNull(cache.fresh("8.8.8.1", 2_000 + IP_QUALITY_MAX_AGE))
            assertNull(cache.fresh("8.8.8.1", 1_999))
            database.appPreferences().put(AppPreferenceEntity(IP_QUALITY_CACHE_KEY, "{truncated", 2_000))
            assertNull(cache.fresh("8.8.8.1", 2_000))
            cache.put(IpQualityRecord(IpQuality("8.8.8.1", IpQualitySource.IP_QUERY, IpType.MOBILE), 3_000), 3_000)
            assertEquals(IpType.MOBILE, cache.fresh("8.8.8.1", 3_000)!!.quality.type)
        } finally { database.close() }
    }
}
