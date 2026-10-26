package com.lonnnnnng.flclash.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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

    @Test
    fun migration2To3CreatesDiagnosticTableAndTimestampIndex() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "runtime-diagnostic-migration-test.db"
        context.deleteDatabase(databaseName)

        val versionTwo = createSqliteHelper(context, databaseName, version = 2) {
            // 作者 long：v2 只需要一个可打开的旧 schema，迁移本身只新增诊断表，不依赖旧表数据。
        }
        versionTwo.writableDatabase.close()
        versionTwo.close()

        val versionThree = createSqliteHelper(context, databaseName, version = 3) { database ->
            KClashDatabase.MIGRATION_2_3.migrate(database)
        }
        val upgraded = versionThree.writableDatabase

        assertEquals(1, upgraded.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'runtime_diagnostic_events'",
        ).use { cursor -> if (cursor.moveToFirst()) 1 else 0 })
        assertEquals(1, upgraded.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_runtime_diagnostic_events_timestampMillis'",
        ).use { cursor -> if (cursor.moveToFirst()) 1 else 0 })

        versionThree.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration3To4CreatesScriptTableAndOrderIndex() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "script-migration-test.db"
        context.deleteDatabase(databaseName)

        val versionThree = createSqliteHelper(context, databaseName, version = 3) {
            // 作者 long：v3 只需要一个可打开的旧 schema，迁移本身只新增脚本资源表。
        }
        versionThree.writableDatabase.close()
        versionThree.close()

        val versionFour = createSqliteHelper(context, databaseName, version = 4) { database ->
            KClashDatabase.MIGRATION_3_4.migrate(database)
        }
        val upgraded = versionFour.writableDatabase

        assertEquals(1, upgraded.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'scripts'",
        ).use { cursor -> if (cursor.moveToFirst()) 1 else 0 })
        assertEquals(1, upgraded.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_scripts_orderIndex'",
        ).use { cursor -> if (cursor.moveToFirst()) 1 else 0 })

        versionFour.close()
        context.deleteDatabase(databaseName)
    }

    private fun createSqliteHelper(
        context: android.content.Context,
        name: String,
        version: Int,
        onUpgrade: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    onUpgrade(db)
                }
            })
            .build(),
    )
}
