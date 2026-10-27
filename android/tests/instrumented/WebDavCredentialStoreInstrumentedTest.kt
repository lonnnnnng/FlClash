package com.lonnnnnng.flclash.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：使用设备真实 Android Keystore 验证 WebDAV 凭据只以密文落盘，
 * 避免 JVM 假实现掩盖 Keystore、SharedPreferences 和 Android 生命周期之间的差异。
 */
@RunWith(AndroidJUnit4::class)
class WebDavCredentialStoreInstrumentedTest {
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var store: AndroidWebDavCredentialStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = context.getSharedPreferences("kclash_webdav_credentials", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = AndroidWebDavCredentialStore(context)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun roundTripUsesEncryptedPreferencesAndClearRemovesCredentials() {
        val credentials = WebDavCredentials(
            baseUrl = "https://dav.example.test/backup",
            username = "test-user",
            password = "超密密码-仅测试",
        )

        store.save(credentials)

        assertEquals(credentials, store.read())
        val persistedValues = preferences.all.values.joinToString("|")
        assertFalse(persistedValues.contains(credentials.password))
        assertFalse(persistedValues.contains(credentials.baseUrl))
        assertFalse(persistedValues.contains(credentials.username))

        store.clear()

        assertNull(store.read())
        assertEquals(0, preferences.all.size)
    }
}
