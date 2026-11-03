package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FilterPresetRepositoryInstrumentedTest {
    @Test fun nativeRegexp2ValidationRenameReorderAndStaleEditsPreserveCatalog() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KClashDatabase::class.java).build()
        try {
            val repository = FilterPresetRepository(database, NativeProxyGroupFilterValidator(CoreBridge()))
            repository.save(null, " 地区 ", " HK`JP ")
            val first = repository.all().single()
            assertEquals(FilterPreset("地区", "HK`JP"), first)
            assertTrue(runCatching { repository.save(first, "地区", "[") }.isFailure)
            assertEquals(listOf(first), repository.all())
            repository.save(null, "过滤", "(?<=foo)bar")
            assertTrue(runCatching { repository.save(null, "地区", "US") }.isFailure)
            repository.reorder(listOf("过滤", "地区"))
            assertEquals(listOf("过滤", "地区"), repository.all().map { it.label })
            repository.save(first, "新名称", "HK")
            assertTrue(runCatching { repository.save(first, "旧快照", "US") }.isFailure)
            repository.delete(listOf(first))
            assertTrue(repository.all().any { it.label == "新名称" })
            repository.delete(repository.all())
            assertTrue(repository.all().isEmpty())
        } finally { database.close() }
    }
}
