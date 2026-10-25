package com.lonnnnnng.flclash.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.service.RuntimeDiagnosticEvent
import com.lonnnnnng.flclash.service.RuntimeDiagnosticKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：固定上限和恢复顺序必须在 Android SQLite 上验证，JVM Fake store 无法发现 SQL trim 边界错误。
 */
@RunWith(AndroidJUnit4::class)
class RuntimeDiagnosticDaoInstrumentedTest {
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
    fun roomStoreKeepsExactlyTheNewestBoundedEvents() = runBlocking {
        val store = RoomRuntimeDiagnosticStore(database.runtimeDiagnostics())

        repeat(201) { index ->
            store.append(
                RuntimeDiagnosticEvent(
                    kind = RuntimeDiagnosticKind.DNS_UPDATED,
                    detail = "DNS $index",
                    timestampMillis = index.toLong(),
                ),
                limit = 200,
            )
        }

        val newestFirst = database.runtimeDiagnostics().getRecent(201)

        assertEquals(200, newestFirst.size)
        assertEquals("DNS 200", newestFirst.first().detail)
        assertEquals("DNS 1", newestFirst.last().detail)
    }

    @Test
    fun roomStoreRestoresEventsInInsertionOrderForTimelineSorting() = runBlocking {
        val store = RoomRuntimeDiagnosticStore(database.runtimeDiagnostics())
        store.append(
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.CORE_STARTED, "启动", 20L),
            limit = 200,
        )
        store.append(
            RuntimeDiagnosticEvent(RuntimeDiagnosticKind.NETWORK_LOST, "断网", 10L),
            limit = 200,
        )

        val restored = store.loadRecent(200)

        assertEquals(
            listOf(RuntimeDiagnosticKind.CORE_STARTED, RuntimeDiagnosticKind.NETWORK_LOST),
            restored.map { it.kind },
        )
    }
}
