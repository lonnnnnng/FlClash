package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 作者 long：走正式常规设置和真实核心验证控制器交接，避免只在macOS Go测试通过而漏掉Android绑定链路。 */
class ControllerRuntimeLiveTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun controllerSecretRotationAndDisableReleaseTheRealPortWithoutRewritingProfiles() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashControllerAcceptance") == "true")
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val original = entry.generalSettingsStore().read()
        assertFalse("本场景要求原控制器关闭，不能接管既有控制器会话", original.externalController)
        val originalVpn = entry.vpnSettingsStore().read()
        val repository = entry.profileRepository()
        val before = entry.database().profiles().getAll()
        val selected = repository.currentProfileId.value
        val profile = before.single { it.id == selected }
        assertEquals(args.getString("kclashAuthorizedSourceSha256"), sha(profile.url.toByteArray()))
        fun files() = before.associate { row -> row.id to repository.profileFile(row.id).let { file ->
            Triple(file.length(), file.lastModified(), sha(file.readBytes()))
        } }
        val originalFiles = files()
        assertPortFree()
        try {
            manager.setVpnEnabled(false).getOrThrow()
            manager.startProxy().getOrThrow()
            val runningSince = manager.runningSinceElapsedMillis.value
            repeat(6) {
                val enabled = entry.generalSettingsStore().read().copy(externalController = true, controllerSecret = UUID.randomUUID().toString())
                manager.updateGeneralSettings(entry.generalSettingsStore().read(), enabled).getOrThrow()
                assertEquals(200, request(enabled.controllerSecret))
                val rotated = enabled.copy(controllerSecret = UUID.randomUUID().toString())
                manager.updateGeneralSettings(enabled, rotated).getOrThrow()
                assertEquals(200, request(rotated.controllerSecret))
                assertEquals(401, request(enabled.controllerSecret))
                assertEquals(401, request("invalid-local-test-secret"))
                // 作者 long：Android嵌入核心仍禁止外部改配置；控制器生命周期修正不能打开第二个配置写入口。
                assertEquals(405, request(rotated.controllerSecret, "PUT", "/configs/"))
                val disabled = rotated.copy(externalController = false)
                manager.updateGeneralSettings(rotated, disabled).getOrThrow()
                assertPortFree()
                assertEquals(CoreRuntimeStatus.RUNNING, manager.state.value)
                assertEquals(runningSince, manager.runningSinceElapsedMillis.value)
            }
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(originalFiles, files())
            instrumentation.sendStatus(2, Bundle().apply {
                putString("kclash_controller", JSONObject().put("rounds", 6).put("authenticated", 12)
                    .put("rejected", 12).put("disabledPortReleased", 6).put("embeddedMutationBlocked", 6)
                    .put("sameRuntimeSession", true).put("formalRowsAndFilesPreserved", true).toString())
            })
        } finally {
            manager.stop().getOrThrow()
            manager.updateGeneralSettings(entry.generalSettingsStore().read(), original).getOrThrow()
            manager.setVpnEnabled(originalVpn).getOrThrow()
            assertEquals(original, entry.generalSettingsStore().read())
            assertPortFree()
        }
    }

    private suspend fun request(secret: String, method: String = "GET", path: String = "/"): Int = withContext(Dispatchers.IO) {
        // 作者 long：控制器是明确的回环HTTP协议，直接走受超时约束的socket验收，避免为测试放宽应用的明文网络策略。
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 9090), 3_000)
            socket.soTimeout = 3_000
            val body = if (method == "PUT") "{" else ""
            val request = "$method $path HTTP/1.1\r\nHost: 127.0.0.1:9090\r\nAuthorization: Bearer $secret\r\n" +
                "Connection: close\r\nContent-Length: ${body.length}\r\n\r\n$body"
            socket.getOutputStream().apply { write(request.toByteArray(Charsets.US_ASCII)); flush() }
            val status = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine().orEmpty()
            check(status.matches(Regex("HTTP/1\\.[01] [0-9]{3} .*"))) { "控制器未返回有效HTTP状态行" }
            status.split(' ', limit = 3)[1].toInt()
        }
    }

    private suspend fun assertPortFree() = withContext(Dispatchers.IO) {
        // 作者 long：不等待、不轮询重试，设置保存返回时旧监听就必须已释放；否则不能把迟到关闭记为即时交接通过。
        ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", 9090)) }
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
