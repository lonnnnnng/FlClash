package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.GeoResourceType
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：回环服务器只返回现有 Geo 原字节或无效二进制，验收真实完成事件且不下载外部数据、不修改 Profile。 */
class GeoResourcesHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun nativeGeoCompletionRefreshesMetadataAndRejectsBadDatabaseWithoutVpn() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val runtime = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        val profilesBefore = entry.database().profiles().getAll()
        var overview: ResourcesViewModel? = null
        compose.activityRule.scenario.onActivity { overview = androidx.lifecycle.ViewModelProvider(it)[ResourcesViewModel::class.java] }
        val state = checkNotNull(overview).state
        runtime.getProxyGroups().getOrThrow()
        val file = File(entry.paths().homeDir, GeoResourceType.MMDB.fileName)
        val original = file.readBytes()
        val modified = file.lastModified()
        val settings = runtime.geoSettings.value
        val response = AtomicReference(original)
        val socket = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        val server = thread(isDaemon = true, name = "geo-resource-acceptance") {
            try {
                while (!socket.isClosed) socket.accept().use { connection ->
                    connection.soTimeout = 5000
                    val reader = connection.getInputStream().bufferedReader()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
                    val bytes = response.get()
                    connection.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            } catch (_: java.net.SocketException) { /* 作者 long：关闭测试监听用于结束等待，不代表下载失败。 */ }
        }
        try {
            val link = "http://127.0.0.1:${socket.localPort}/geo.metadb"
            runtime.setGeoSettings(settings.copy(autoUpdate = false, urls = settings.urls + (GeoResourceType.MMDB to link)))
            runtime.requestGeoUpdate(GeoResourceType.MMDB)
            val success = withTimeout(30_000) { runtime.geoUpdates.first { it["MMDB"]?.updating == false }.getValue("MMDB") }
            assertTrue("核心应报告相同 Geo 数据已完成", success.error.isEmpty())
            assertTrue(success.skipped)
            assertTrue(file.readBytes().contentEquals(original))
            val expectedTime = file.lastModified()
            withTimeout(5000) { state.first { snapshot -> snapshot.rows.any {
                it.kind == com.lonnnnnng.flclash.data.ResourceKind.GEO && it.id == "MMDB" &&
                    it.updatedAt == expectedTime && it.size == file.length()
            } } }
            response.set("invalid-database".toByteArray())
            runtime.requestGeoUpdate(GeoResourceType.MMDB)
            val failure = withTimeout(30_000) { runtime.geoUpdates.first { it["MMDB"]?.updating == false }.getValue("MMDB") }
            assertTrue("无效数据库应由核心完成事件报告失败", failure.error.isNotEmpty())
            assertTrue(file.readBytes().contentEquals(original))
            assertEquals(profilesBefore, entry.database().profiles().getAll())
            assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
            val work = androidx.work.WorkManager.getInstance(compose.activity.application)
                .getWorkInfosForUniqueWork(com.lonnnnnng.flclash.data.ResourceAutoUpdateScheduler.WORK_NAME)
                .get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue("资源自动更新应已注册", work.any { it.state != androidx.work.WorkInfo.State.CANCELLED })
        } finally {
            socket.close(); server.join(1000)
            runtime.setGeoSettings(settings)
            check(file.setLastModified(modified))
        }
    }
}
