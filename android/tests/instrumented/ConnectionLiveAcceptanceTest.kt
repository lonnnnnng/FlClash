package com.lonnnnnng.flclash

import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.ui.MainViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * 作者 long：真实核心验收必须显式启用，由本机脚本从授权清单和原始 1.yaml 准备临时输入；
 * 普通 connectedAndroidTest 跳过此用例，不暗中联网、替换配置或申请 VPN。
 */
@RunWith(AndroidJUnit4::class)
class ConnectionLiveAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun realConnectionOpensCopiesAndRestoresDetailThenCleansUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("kclashLiveAcceptance") == "true")
        val input = JSONObject(shellText("cat /data/local/tmp/kclash-live-input.json"))
        val bytes = Base64.decode(input.getString("configBase64"), Base64.DEFAULT)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("本地配置副本必须与本机原始文件一致", input.getString("configSha256"), hash)
        val target = URI(input.getString("target"))
        require(target.scheme in listOf("http", "https") && !target.host.isNullOrBlank()) { "授权目标无效" }
        // 作者 long：连接必须使用原配置实际声明的 SOCKS/mixed 端口，不能为验收改写 1.yaml 或猜测默认端口。
        val config = JSONObject(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8)))
        val proxyPort = listOf("socks-port", "mixed-port").firstNotNullOfOrNull { key ->
            config.optInt(key).takeIf { it in 1..65535 }
        } ?: error("原始配置没有可用的 SOCKS/mixed 端口")

        lateinit var runtime: MainViewModel
        compose.activityRule.scenario.onActivity {
            runtime = ViewModelProvider(it)[MainViewModel::class.java]
        }
        compose.waitForIdle()
        assumeTrue("已有运行会话时保留用户现场", runtime.runtimeState.value == CoreRuntimeStatus.STOPPED)
        assertTrue("验收前需在系统中允许 KClash 通知", android.os.Build.VERSION.SDK_INT < 33 ||
            compose.activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val entryPoint = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val profiles = entryPoint.profiles()
        val configFile = entryPoint.paths().configFile
        val originalConfig = configFile.takeIf { it.isFile }?.readBytes()
        val previousId = profiles.currentProfileId.value
        val originalIds = profiles.profiles.value.map { it.id }.toSet()
        val vpnWasAuthorized = VpnService.prepare(compose.activity) == null
        val clipboard = checkNotNull(compose.activity.getSystemService(ClipboardManager::class.java))
        val previousClip = clipboard.primaryClip
        var imported: Profile? = null
        var request: Socket? = null
        var verifiedSnapshot: CoreConnection? = null
        var failure: Throwable? = null
        try {
            // 作者 long：等待 Repository 返回本次唯一 ID；界面轮询超时也不会遗失临时配置的清理归属。
            val acceptanceProfile = runBlocking { profiles.addFileProfile("连接详情验收-${UUID.randomUUID()}", bytes) }
            imported = acceptanceProfile
            runBlocking { profiles.selectProfile(acceptanceProfile.id) }
            compose.waitUntil(5_000) { profiles.currentProfileId.value == acceptanceProfile.id }
            compose.onNodeWithText("启动核心").performClick()
            // 作者 long：仍通过产品的前台授权对话框确认，只允许点击系统 VPN 请求的肯定按钮。
            compose.waitUntil(15_000) {
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                if (root?.packageName?.toString() == "com.android.vpndialogs") {
                    root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                runtime.runtimeState.value in listOf(CoreRuntimeStatus.RUNNING, CoreRuntimeStatus.FAILED)
            }
            assertEquals(CoreRuntimeStatus.RUNNING, runtime.runtimeState.value)
            assertTrue("VPN 授权应已生效", VpnService.prepare(compose.activity) == null)
            request = openAuthorizedRequest(target, proxyPort)
            val sourcePort = request.localPort.toString()
            val destinationPort = (if (target.port > 0) target.port else if (target.scheme == "https") 443 else 80).toString()
            compose.onNode(hasText("连接") and hasClickAction()).performClick()
            var connection: CoreConnection? = null
            compose.waitUntil(15_000) {
                connection = runtime.connections.value.connections.firstOrNull {
                    it.metadata.host == target.host && it.metadata.sourcePort == sourcePort &&
                        it.metadata.destinationPort == destinationPort
                }
                connection != null
            }
            val snapshot = checkNotNull(connection)
            val index = runtime.connections.value.connections.indexOfFirst { it.id == snapshot.id }
            assertTrue("本次连接必须在真实页面展示范围内", index in 0..4)
            compose.onNodeWithTag("connections_row_$index").performClick()
            expandSheet()
            compose.onNodeWithTag("connection_detail_主机").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(target.host, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            compose.onNodeWithText("已复制").assertIsDisplayed()
            compose.activityRule.scenario.onActivity { runtime.stopRuntime() }
            compose.waitUntil(10_000) { runtime.runtimeState.value == CoreRuntimeStatus.STOPPED }
            request.close()
            request = null
            compose.activityRule.scenario.recreate()
            expandSheet()
            compose.onNodeWithTag("connection_detail_主机").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText(target.host) and hasAnyAncestor(hasTestTag("connections_detail_dialog")),
                useUnmergedTree = true).assertIsDisplayed()
            if (snapshot.metadata.dnsMode.isNotBlank()) {
                compose.onNodeWithTag("connection_detail_DNS 模式").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(snapshot.metadata.dnsMode, useUnmergedTree = true).assertIsDisplayed()
            }
            compose.onNodeWithContentDescription("关闭连接详情").performClick()
            compose.onNodeWithTag("connections_detail_sheet").assertDoesNotExist()
            verifiedSnapshot = snapshot
        } catch (error: Throwable) {
            failure = error
        } finally {
            // 作者 long：任一步清理失败仍要尝试后续恢复；只有核心确实停止后才能恢复暂存文件，不能边运行边改配置。
            val cleanupFailures = mutableListOf<String>()
            fun cleanup(step: String, action: () -> Unit) {
                runCatching(action).onFailure { cleanupFailures += "$step:${it.javaClass.simpleName}" }
            }
            cleanup("关闭请求") { request?.close() }
            cleanup("停止核心") {
                compose.activityRule.scenario.onActivity { runtime.stopRuntime() }
                compose.waitUntil(10_000) { runtime.runtimeState.value == CoreRuntimeStatus.STOPPED }
            }
            if (runtime.runtimeState.value == CoreRuntimeStatus.STOPPED) {
                cleanup("删除临时配置") {
                    imported?.let { runBlocking { profiles.deleteProfile(it.id) } }
                    compose.waitUntil(10_000) { profiles.profiles.value.map { it.id }.toSet() == originalIds }
                }
                cleanup("恢复选择") { runBlocking { profiles.selectProfile(previousId) } }
                cleanup("恢复核心暂存") {
                    if (originalConfig != null) configFile.writeBytes(originalConfig)
                    else if (configFile.exists()) check(configFile.delete()) { "无法删除本次暂存配置" }
                }
            }
            cleanup("恢复剪贴板") {
                if (previousClip != null) clipboard.setPrimaryClip(previousClip)
                else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            }
            cleanup("返回首页") {
                val close = compose.onAllNodes(SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
                    listOf("关闭连接详情"),
                ))
                if (close.fetchSemanticsNodes().isNotEmpty()) close[0].performClick()
                compose.onNodeWithText("首页").performClick()
            }
            if (cleanupFailures.isNotEmpty()) {
                val cleanupError = AssertionError("现场恢复未完成：${cleanupFailures.joinToString()}")
                if (failure == null) failure = cleanupError else failure?.addSuppressed(cleanupError)
            }
        }
        failure?.let { throw it }
        val snapshot = checkNotNull(verifiedSnapshot)
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("kclash_live_result", "real-core-detail-copy-recreate-stop-passed")
            putString("kclash_live_dns_mode", snapshot.metadata.dnsMode)
            putInt("kclash_live_geoip_count", snapshot.metadata.destinationGeoIp.size)
            putBoolean("kclash_live_asn_present", snapshot.metadata.destinationIpAsn.isNotBlank())
            putBoolean("kclash_live_new_vpn_authorization", !vpnWasAuthorized)
        })
    }

    private fun expandSheet() {
        val actions = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        if (actions.fetchSemanticsNodes().isNotEmpty()) actions[0].performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.waitForIdle()
    }

    private fun openAuthorizedRequest(target: URI, proxyPort: Int): Socket {
        val port = if (target.port > 0) target.port else if (target.scheme == "https") 443 else 80
        val socket = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort)))
        try {
            socket.connect(InetSocketAddress.createUnresolved(target.host, port), 15_000)
            socket.soTimeout = 15_000
            val stream = if (target.scheme == "https") {
                (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, target.host, port, true)
                    .also { secure ->
                        (secure as SSLSocket).sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                        secure.startHandshake()
                    }
            } else socket
            // 作者 long：保持授权 HTTP 请求的连接直到 UI 采样完成；不保存、不输出订阅响应正文，也不跟随到未授权地址。
            val path = target.rawPath.ifBlank { "/" } + target.rawQuery?.let { "?$it" }.orEmpty()
            stream.getOutputStream().write(
                "GET $path HTTP/1.1\r\nHost: ${target.host}\r\nConnection: keep-alive\r\nUser-Agent: KClash-Acceptance\r\n\r\n".toByteArray(),
            )
            stream.getOutputStream().flush()
            return stream
        } catch (error: Exception) {
            socket.close()
            throw AssertionError("授权请求连接失败：${error.javaClass.simpleName}")
        }
    }

    private fun shellText(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }
}
