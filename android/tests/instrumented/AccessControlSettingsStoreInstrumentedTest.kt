package com.lonnnnnng.flclash.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.service.models.AccessControlProps
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：按应用代理列表必须与订阅 YAML 分离，并能在真实 Android SQLite 上恢复，
 * 这样进程重建后启动 VPN 才会使用用户最后一次保存的包名选择。
 */
@RunWith(AndroidJUnit4::class)
class AccessControlSettingsStoreInstrumentedTest {
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
    fun savesAndRestoresSanitizedPackageSelection() = runBlocking {
        val store = RoomAccessControlSettingsStore(database.appPreferences())
        store.save(
            AccessControlProps(
                enable = true,
                mode = AccessControlMode.ACCEPT_SELECTED,
                acceptList = listOf("com.z", "", "com.z", "com.a"),
                rejectList = listOf("com.unused"),
            ),
        )

        val restored = store.current()

        assertEquals(true, restored.enable)
        assertEquals(AccessControlMode.ACCEPT_SELECTED, restored.mode)
        assertEquals(listOf("com.a", "com.z"), restored.acceptList)
        assertEquals(listOf("com.unused"), restored.rejectList)
    }
}
